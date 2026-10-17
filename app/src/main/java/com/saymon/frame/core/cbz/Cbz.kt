package com.saymon.frame.core.cbz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.zip.ZipFile

private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/**
 * CBZ через SAF: глава копируется в кэш один раз, дальше — ZipFile.
 * Страницы декодируются по мере листания с ужиманием под экран.
 */
class ChapterSession private constructor(
    private val zip: ZipFile,
    val entries: List<String>,
    val cacheFile: File,
) {
    fun close() {
        try {
            zip.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Глава — обычный файл (библиотека в корне устройства). */
        fun openFile(path: String): ChapterSession {
            val file = File(path)
            val zip = ZipFile(file)
            val entries = zipEntries(zip)
            if (entries.isEmpty()) {
                try {
                    zip.close()
                } catch (_: Exception) {
                }
                throw IllegalStateException("В CBZ нет картинок")
            }
            return ChapterSession(zip, entries, file)
        }

        private fun zipEntries(zip: ZipFile): List<String> {
            return zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { n ->
                    IMG.contains(n.substringAfterLast('.', "").lowercase())
                }
                .sortedWith { a, b ->
                    // по имени файла внутри архива
                    val an = a.substringAfterLast('/')
                    val bn = b.substringAfterLast('/')
                    com.saymon.frame.data.NaturalSort.compare(an, bn)
                }
                .toList()
        }
    }
}

/** Декодирует страницу, ужимая до [maxPx] по длинной стороне. */
fun decodePage(
    session: ChapterSession,
    index: Int,
    maxPx: Int = 2200,
): Bitmap? {
    if (index !in session.entries.indices) return null
    return try {
        val entry = session.entries[index]
        // ZipFile не потокобезопасен — синхронизируемся на сессии
        synchronized(session) {
            val stream = sessionZip(session, entry) ?: return null
            stream.use { inp ->
                val bounds = BitmapFactory.Options()
                    .apply { inJustDecodeBounds = true }
                val bytes = inp.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var s = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / s > maxPx) s *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = s })
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun sessionZip(session: ChapterSession, entry: String) =
    try {
        java.util.zip.ZipFile(session.cacheFile).let { z ->
            val s = z.getInputStream(z.getEntry(entry))
            // оборачиваем чтобы закрыть zip вместе с потоком
            object : java.io.FilterInputStream(s) {
                override fun close() {
                    super.close()
                    try {
                        z.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    }

/** Обложка из обычного файла (папка в корне устройства). */
fun decodeFile(f: File, maxPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var s = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / s > maxPx) s *= 2
        BitmapFactory.decodeFile(f.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Exception) {
        null
    }
}

data class ChapterInfo(
    val titleId: String,
    val uri: String,
    val name: String,
)

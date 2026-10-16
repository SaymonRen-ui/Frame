package com.saymon.frame.core.cbz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        suspend fun open(
            context: Context,
            chapterUri: String,
            titleId: String,
        ): ChapterSession = withContext(Dispatchers.IO) {
            val uri = Uri.parse(chapterUri)
            val dir = File(context.cacheDir,
                "cbz/" + titleId.hashCode().toString(16)).also { it.mkdirs() }
            val name = DocumentFile.fromSingleUri(context, uri)?.name
                ?: "chapter.cbz"
            val cache = File(dir, name.replace("/", "_"))
            if (!cache.exists()) {
                context.contentResolver.openInputStream(uri)?.use { inp ->
                    cache.outputStream().use { inp.copyTo(it) }
                } ?: throw IllegalStateException("Не открылся CBZ")
            }
            val zip = ZipFile(cache)
            val entries = zip.entries().asSequence()
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
            if (entries.isEmpty()) {
                try {
                    zip.close()
                } catch (_: Exception) {
                }
                throw IllegalStateException("В CBZ нет картинок")
            }
            ChapterSession(zip, entries, cache)
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

/** Обложка произведения: cover.jpg в папке, иначе первая страница первой главы. */
suspend fun loadCover(
    context: Context,
    titleDir: DocumentFile,
    chapters: List<ChapterInfo>,
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val cover = titleDir.listFiles().firstOrNull {
            val n = (it.name ?: "").lowercase()
            (n == "cover.jpg" || n == "cover.png" || n == "cover.jpeg") && it.isFile
        }
        if (cover != null) {
            return@withContext decodeUri(context, cover.uri, 512)
        }
        val first = chapters.firstOrNull() ?: return@withContext null
        val session = try {
            ChapterSession.open(context, first.uri, first.titleId)
        } catch (_: Exception) {
            return@withContext null
        }
        try {
            val bmp = withContext(Dispatchers.IO) {
                decodePage(session, 0, 512)
            }
            session.close()
            bmp
        } catch (_: Exception) {
            try {
                session.close()
            } catch (_: Exception) {
            }
            null
        }
    } catch (_: Exception) {
        null
    }
}

data class ChapterInfo(
    val titleId: String,
    val uri: String,
    val name: String,
)

fun decodeUri(context: Context, uri: Uri, maxPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var s = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / s > maxPx) s *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = s })
        }
    } catch (_: Exception) {
        null
    }
}

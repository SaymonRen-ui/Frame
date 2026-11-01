package com.saymon.frame.core.cbz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/** Верхний предел стороны растра для PDF: выше — лишняя память и лаги. */
private const val PDF_RENDER_MAX = 2400

/** Общий кэш растров глав: помогает при листании назад и через границу. */
private const val IMG_CACHE_BYTES = 64 * 1024 * 1024

private val IMG_CACHE = object : LruCache<String, Bitmap>(IMG_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** Сбрасывает кэш декодированных страниц (память освободит GC). */
fun clearImageCache() {
    IMG_CACHE.evictAll()
}

/**
 * Глава: CBZ (ZipFile) или PDF (PdfRenderer).
 * Страницы декодируются по мере листания с ужиманием под экран.
 */
class ChapterSession private constructor(
    private val zip: ZipFile?,
    private val pdf: PdfRenderer?,
    private val pdfFd: ParcelFileDescriptor?,
    val entries: List<String>,
    val cacheFile: File,
) {
    val isPdf: Boolean get() = pdf != null

    private fun pageKey(index: Int, maxPx: Int, cropBorders: Boolean): String =
        "${cacheFile.absolutePath}|${cacheFile.length()}|${cacheFile.lastModified()}" +
            "|$index|$maxPx|$cropBorders"

    fun cachedPage(index: Int, maxPx: Int, cropBorders: Boolean): Bitmap? {
        val bmp = IMG_CACHE.get(pageKey(index, maxPx, cropBorders)) ?: return null
        if (bmp.isRecycled) {
            IMG_CACHE.remove(pageKey(index, maxPx, cropBorders))
            return null
        }
        return bmp
    }

    fun storePage(index: Int, maxPx: Int, cropBorders: Boolean, bmp: Bitmap) {
        IMG_CACHE.put(pageKey(index, maxPx, cropBorders), bmp)
    }

    fun close() {
        try {
            pdf?.close()
        } catch (_: Exception) {
        }
        try {
            pdfFd?.close()
        } catch (_: Exception) {
        }
        try {
            zip?.close()
        } catch (_: Exception) {
        }
    }

    /** Поток записи внутри архива. ZipFile не потокобезопасен — вызывать под synchronized. */
    fun open(entry: String): java.io.InputStream? {
        val z = zip ?: return null
        return try {
            val e = z.getEntry(entry) ?: return null
            z.getInputStream(e)
        } catch (_: Exception) {
            null
        }
    }

    /** Рендер страницы PDF с кэшем (PdfRenderer не потокобезопасен). */
    fun renderPdf(index: Int, maxPx: Int, cropBorders: Boolean): Bitmap? {
        val renderer = pdf ?: return null
        if (index !in entries.indices) return null
        val limit = maxPx.coerceIn(256, PDF_RENDER_MAX)
        cachedPage(index, limit, cropBorders)?.let { return it }
        return try {
            synchronized(this) {
                renderer.openPage(index).use { page ->
                    val w = page.width
                    val h = page.height
                    if (w <= 0 || h <= 0) return null
                    val scale = min(1f, limit.toFloat() / max(w, h).toFloat())
                    val bw = (w * scale).roundToInt().coerceAtLeast(1)
                    val bh = (h * scale).roundToInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = if (cropBorders) bmp.cropBorders() else bmp
                    storePage(index, limit, cropBorders, out)
                    out
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun pdfBounds(index: Int): IntArray? {
        val renderer = pdf ?: return null
        if (index !in entries.indices) return null
        return try {
            synchronized(this) {
                renderer.openPage(index).use { p ->
                    if (p.width <= 0 || p.height <= 0) null
                    else intArrayOf(p.width, p.height)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Глава — обычный файл (библиотека в корне устройства). */
        fun openFile(file: File): ChapterSession {
            if (file.name.lowercase().endsWith(".pdf")) return openPdf(file)
            val zip = ZipFile(file)
            val entries = zipEntries(zip)
            if (entries.isEmpty()) {
                try {
                    zip.close()
                } catch (_: Exception) {
                }
                throw IllegalStateException("В CBZ нет картинок")
            }
            return ChapterSession(zip, null, null, entries, file)
        }

        private fun openPdf(file: File): ChapterSession {
            var fd: ParcelFileDescriptor? = null
            try {
                fd = ParcelFileDescriptor.open(
                    file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val count = renderer.pageCount
                if (count <= 0) {
                    renderer.close()
                    throw IllegalStateException("В PDF нет страниц")
                }
                val names = List(count) { "page-${it + 1}.pdf" }
                return ChapterSession(null, renderer, fd, names, file)
            } catch (e: Exception) {
                try {
                    fd?.close()
                } catch (_: Exception) {
                }
                throw e
            }
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
    cropBorders: Boolean = false,
): Bitmap? {
    if (index !in session.entries.indices) return null
    if (session.isPdf) return session.renderPdf(index, maxPx, cropBorders)
    session.cachedPage(index, maxPx, cropBorders)?.let { return it }
    return try {
        val entry = session.entries[index]
        // ZipFile не потокобезопасен — синхронизируемся на сессии
        synchronized(session) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            session.open(entry)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx) sample *= 2
            val bmp = session.open(entry)?.use {
                BitmapFactory.decodeStream(
                    it, null,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
            val out = if (bmp != null && cropBorders) bmp.cropBorders() else bmp
            if (out != null) session.storePage(index, maxPx, cropBorders, out)
            out
        }
    } catch (_: Exception) {
        null
    }
}

/** Размер страницы без полного декодирования (для разделения широких разворотов). */
fun pageBounds(session: ChapterSession, index: Int): IntArray? {
    if (index !in session.entries.indices) return null
    if (session.isPdf) return session.pdfBounds(index)
    return try {
        synchronized(session) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            session.open(session.entries[index])?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
            else intArrayOf(bounds.outWidth, bounds.outHeight)
        }
    } catch (_: Exception) {
        null
    }
}

/** Обрезает почти белые поля по краям скана. */
private fun Bitmap.cropBorders(): Bitmap {
    val w = width
    val h = height
    if (w < 8 || h < 8) return this
    val row = IntArray(w)
    val col = IntArray(h)

    fun isWhite(p: Int): Boolean {
        val a = (p ushr 24) and 0xFF
        if (a < 16) return true
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return r >= 240 && g >= 240 && b >= 240
    }

    fun rowWhite(y: Int): Boolean {
        getPixels(row, 0, w, 0, y, w, 1)
        var n = 0
        for (p in row) if (isWhite(p)) n++
        return n >= (w * 0.98f).toInt()
    }

    fun colWhite(x: Int): Boolean {
        getPixels(col, 0, 1, x, 0, 1, h)
        var n = 0
        for (p in col) if (isWhite(p)) n++
        return n >= (h * 0.98f).toInt()
    }

    var top = 0
    while (top < h / 3 && rowWhite(top)) top++
    var bottom = h - 1
    while (bottom > h * 2 / 3 && rowWhite(bottom)) bottom--
    var left = 0
    while (left < w / 3 && colWhite(left)) left++
    var right = w - 1
    while (right > w * 2 / 3 && colWhite(right)) right--
    if (top == 0 && left == 0 && bottom == h - 1 && right == w - 1) return this
    val nw = right - left + 1
    val nh = bottom - top + 1
    if (nw < 16 || nh < 16) return this
    val out = Bitmap.createBitmap(this, left, top, nw, nh)
    if (out !== this) recycle()
    return out
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

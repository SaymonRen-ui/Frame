package com.saymon.frame.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Всё состояние — в SharedPreferences + JSON, аккаунтов нет.
 * Вкладки, прогресс глав, история, настройки чтения.
 */
class Store(context: Context) {
    private val app = context.applicationContext
    private fun prefs() = app.getSharedPreferences("frame", Context.MODE_PRIVATE)

    // --- Библиотека ---
    fun libraryUri(): String =
        prefs().getString("library_uri", "").orEmpty()

    fun setLibraryUri(uri: String) {
        prefs().edit { putString("library_uri", uri) }
    }

    // --- Вкладки: список + привязка произведений ---
    fun tabs(): List<String> {
        val raw = prefs().getString("tabs", "").orEmpty()
        if (raw.isBlank()) {
            return listOf("Читаю", "В планах", "Закончил", "Брошено", "Избранное").also {
                prefs().edit { putString("tabs", JSONArray(it).toString()) }
            }
        }
        return try {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        } catch (_: Exception) {
            listOf("Читаю")
        }
    }

    fun setTabs(tabs: List<String>) {
        prefs().edit { putString("tabs", JSONArray(tabs).toString()) }
    }

    /** titleId -> вкладки. */
    fun titleTabs(): Map<String, Set<String>> {
        return try {
            val o = JSONObject(prefs().getString("title_tabs", "{}").orEmpty())
            buildMap {
                for (k in o.keys()) {
                    val a = o.getJSONArray(k)
                    put(k, List(a.length()) { a.getString(it) }.toSet())
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setTitleTabs(titleId: String, tabs: Set<String>) {
        try {
            val o = JSONObject(prefs().getString("title_tabs", "{}").orEmpty())
            o.put(titleId, JSONArray(tabs.toList()))
            prefs().edit { putString("title_tabs", o.toString()) }
        } catch (_: Exception) {
        }
    }

    // --- Метаданные произведения (описание, рейтинг, отзыв) ---
    fun titleMeta(titleId: String): TitleMeta {
        return try {
            val o = JSONObject(prefs().getString("meta|$titleId", "{}").orEmpty())
            TitleMeta(
                description = o.optString("description", ""),
                rating = o.optDouble("rating", 0.0),
                review = o.optString("review", ""),
                author = o.optString("author", "")
            )
        } catch (_: Exception) {
            TitleMeta()
        }
    }

    fun setTitleMeta(titleId: String, meta: TitleMeta) {
        try {
            val o = JSONObject()
            o.put("description", meta.description)
            o.put("rating", meta.rating)
            o.put("review", meta.review)
            o.put("author", meta.author)
            prefs().edit { putString("meta|$titleId", o.toString()) }
        } catch (_: Exception) {
        }
    }

    // --- Прогресс: "titleId|chapterUri" -> страница ---
    fun progress(titleId: String, chapterUri: String): Int =
        prefs().getInt("p|$titleId|$chapterUri", 0)

    fun setProgress(p: Progress) {
        prefs().edit {
            putInt("p|${p.titleId}|${p.chapterUri}", p.page)
        }
        pushHistory(p)
    }

    // --- История: последние 50 ---
    fun history(): List<Progress> {
        return try {
            val a = JSONArray(prefs().getString("history", "[]").orEmpty())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Progress(
                    o.getString("titleId"), o.getString("titleName"),
                    o.getString("chapterUri"), o.getString("chapterName"),
                    o.getInt("page"), o.getInt("pages"), o.getLong("time"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun pushHistory(p: Progress) {
        try {
            val cur = history()
                .filterNot { it.titleId == p.titleId && it.chapterUri == p.chapterUri }
            val all = (listOf(p) + cur).take(50)
            val a = JSONArray()
            for (h in all) {
                a.put(JSONObject()
                    .put("titleId", h.titleId)
                    .put("titleName", h.titleName)
                    .put("chapterUri", h.chapterUri)
                    .put("chapterName", h.chapterName)
                    .put("page", h.page)
                    .put("pages", h.pages)
                    .put("time", h.time))
            }
            prefs().edit { putString("history", a.toString()) }
        } catch (_: Exception) {
        }
    }

    fun clearHistory() {
        prefs().edit { putString("history", "[]") }
    }

    // --- Настройки чтения ---
    fun theme(): Int = prefs().getInt("theme", 0)
    fun setTheme(v: Int) = prefs().edit { putInt("theme", v) }

    fun readMode(): Int = prefs().getInt("read_mode", 0)
    fun setReadMode(v: Int) = prefs().edit { putInt("read_mode", v) }

    fun orientation(): Int = prefs().getInt("orientation", 0)
    fun setOrientation(v: Int) = prefs().edit { putInt("orientation", v) }

    fun tapZones(): Int = prefs().getInt("tap_zones", 0)
    fun setTapZones(v: Int) = prefs().edit { putInt("tap_zones", v) }

    fun doubleTapZoom(): Boolean = prefs().getBoolean("dt_zoom", true)
    fun setDoubleTapZoom(v: Boolean) = prefs().edit { putBoolean("dt_zoom", v) }

    fun pageNumber(): Boolean = prefs().getBoolean("page_number", true)
    fun setPageNumber(v: Boolean) = prefs().edit { putBoolean("page_number", v) }

    fun bgColor(): Int = prefs().getInt("bg_color", 0)
    fun setBgColor(v: Int) = prefs().edit { putInt("bg_color", v) }

    fun keepScreen(): Boolean = prefs().getBoolean("keep_screen", true)
    fun setKeepScreen(v: Boolean) = prefs().edit { putBoolean("keep_screen", v) }

    fun fullscreen(): Boolean = prefs().getBoolean("fullscreen", false)
    fun setFullscreen(v: Boolean) = prefs().edit { putBoolean("fullscreen", v) }

    // --- Универсальные хелперы для расширенных настроек читалки ---
    fun i(key: String, def: Int = 0): Int = prefs().getInt(key, def)
    fun si(key: String, v: Int) = prefs().edit { putInt(key, v) }
    fun b(key: String, def: Boolean = false): Boolean = prefs().getBoolean(key, def)
    fun sb(key: String, v: Boolean) = prefs().edit { putBoolean(key, v) }

    /** Размер кэша распакованных глав (байт) — для кнопки очистки. */
    fun cacheSize(): Long {
        return try {
            app.cacheDir.walkTopDown()
                .filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0
        }
    }

    fun clearCache(): Boolean {
        return try {
            app.cacheDir.deleteRecursively()
            app.cacheDir.mkdirs()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Режимы чтения (как у Mihon, набор v1). */
object ReadMode {
    const val STRIP = 0 // длинные страницы
    const val STRIP_GAPS = 1 // длинные с зазорами
    const val PAGED_LTR = 2 // постранично слева направо
    const val PAGED_RTL = 3 // постранично справа налево
    const val PAGED_VERTICAL = 4 // постранично вертикально
}

/** Ориентация: 0 системная, 1 портрет, 2 альбом. */
object Orient {
    const val SYSTEM = 0
    const val PORTRAIT = 1
    const val LANDSCAPE = 2
}

/** Метаданные произведения: описание, рейтинг, отзыв, автор. */
data class TitleMeta(
    val description: String = "",
    val rating: Double = 0.0,
    val review: String = "",
    val author: String = ""
)

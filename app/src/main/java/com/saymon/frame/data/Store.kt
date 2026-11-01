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

    init {
        migrateLegacySettings()
    }

    /**
     * Старые версии хранили режимы по другой нумерации. Новые ключи
     * (`nav_modes`, `crop_borders`, ...) показывают, что миграция уже не нужна.
     */
    private fun migrateLegacySettings() {
        val p = prefs()
        if (p.getInt("settings_schema", 0) >= 1) return
        val newFormat = p.contains("nav_modes") || p.contains("crop_borders") ||
            p.contains("sidebar_pad") || p.contains("chapter_transition")
        val e = p.edit()
        if (!newFormat) {
            if (p.contains("read_mode")) {
                e.putInt("read_mode", when (p.getInt("read_mode", 0)) {
                    0 -> ReadMode.STRIP
                    1 -> ReadMode.STRIP_GAPS
                    2 -> ReadMode.PAGED_LTR
                    3 -> ReadMode.PAGED_RTL
                    4 -> ReadMode.PAGED_VERTICAL
                    else -> p.getInt("read_mode", 0)
                })
            }
            if (p.contains("orientation")) {
                e.putInt("orientation", when (p.getInt("orientation", 0)) {
                    1 -> Orient.PORTRAIT
                    2 -> Orient.LANDSCAPE
                    else -> p.getInt("orientation", 0)
                })
            }
            if (p.contains("tap_zones") && p.getInt("tap_zones", 0) == 1) {
                e.putInt("tap_zones", TapZone.DISABLED)
            }
        }
        e.putInt("settings_schema", 1).apply()
    }

    // --- Библиотека ---
    fun libraryUri(): String =
        prefs().getString("library_uri", "").orEmpty()

    fun setLibraryUri(uri: String) {
        prefs().edit { putString("library_uri", uri) }
    }

    fun compactLibrary(): Boolean = prefs().getBoolean("compact_library", false)
    fun setCompactLibrary(value: Boolean) = prefs().edit { putBoolean("compact_library", value) }

    /** Экспортирует настройки, прогресс и историю в переносимый JSON-файл. */
    fun exportBackup(): String {
        val out = JSONObject().put("version", 1)
        val values = JSONObject()
        for ((key, value) in prefs().all) {
            // Путь/URI библиотеки привязан к устройству — в бэкап не попадает.
            if (key == "library_uri") continue
            val item = JSONObject()
            when (value) {
                is Boolean -> item.put("type", "boolean").put("value", value)
                is Int -> item.put("type", "int").put("value", value)
                is Long -> item.put("type", "long").put("value", value)
                is Float -> item.put("type", "float").put("value", value.toDouble())
                is String -> item.put("type", "string").put("value", value)
                is Set<*> -> item.put("type", "strings")
                    .put("value", JSONArray(value.filterIsInstance<String>()))
                else -> continue
            }
            values.put(key, item)
        }
        return out.put("prefs", values).toString(2)
    }

    /** Восстанавливает только данные, созданные приложением. */
    fun importBackup(raw: String): Boolean {
        return try {
            val root = JSONObject(raw)
            if (root.optInt("version", 0) != 1) return false
            val values = root.optJSONObject("prefs") ?: return false
            // Доступ к папке библиотеки выдан этому устройству — не затираем его.
            val library = prefs().getString("library_uri", "").orEmpty()
            val edit = prefs().edit().clear()
            if (library.isNotEmpty()) edit.putString("library_uri", library)
            val keys = values.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "library_uri") continue
                val item = values.optJSONObject(key) ?: continue
                when (item.optString("type")) {
                    "boolean" -> edit.putBoolean(key, item.optBoolean("value"))
                    "int" -> edit.putInt(key, item.optInt("value"))
                    "long" -> edit.putLong(key, item.optLong("value"))
                    "float" -> edit.putFloat(key, item.optDouble("value").toFloat())
                    "string" -> edit.putString(key, item.optString("value"))
                    "strings" -> {
                        val a = item.optJSONArray("value") ?: JSONArray()
                        edit.putStringSet(key, buildSet {
                            for (i in 0 until a.length()) add(a.optString(i))
                        })
                    }
                }
            }
            edit.apply()
            migrateLegacySettings()
            true
        } catch (_: Exception) {
            false
        }
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

    fun removeTitleTabs(titleId: String) {
        try {
            val o = JSONObject(prefs().getString("title_tabs", "{}").orEmpty())
            o.remove(titleId)
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
            writeHistory((listOf(p) + cur).take(50))
        } catch (_: Exception) {
        }
    }

    private fun writeHistory(all: List<Progress>) {
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
    }

    /** Удалить одну запись из истории. */
    fun removeHistory(titleId: String, chapterUri: String) {
        try {
            val cur = history().filterNot {
                it.titleId == titleId && it.chapterUri == chapterUri
            }
            writeHistory(cur)
        } catch (_: Exception) {
        }
    }

    fun clearHistory() {
        prefs().edit { putString("history", "[]") }
    }

    // --- Наборы строк (избранные главы, отметки о прочтении) ---
    fun strSet(key: String): Set<String> {
        return try {
            val a = JSONArray(prefs().getString(key, "[]").orEmpty())
            List(a.length()) { a.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setStrSet(key: String, v: Set<String>) {
        prefs().edit { putString(key, JSONArray(v.toList()).toString()) }
    }

    /** Убирает все следы главы: прогресс, прочитано, избранное, историю. */
    fun removeChapterData(titleId: String, chapterUri: String) {
        prefs().edit { remove("p|$titleId|$chapterUri") }
        setStrSet("read|$titleId", strSet("read|$titleId") - chapterUri)
        setStrSet("fav|$titleId", strSet("fav|$titleId") - chapterUri)
        removeHistory(titleId, chapterUri)
    }

    /** Убирает все данные произведения: главы, метаданные, вкладки. */
    fun removeTitleData(titleId: String) {
        try {
            prefs().edit {
                val prefix = "p|$titleId|"
                for (k in prefs().all.keys) {
                    if (k.startsWith(prefix)) remove(k)
                }
                remove("read|$titleId")
                remove("fav|$titleId")
                remove("meta|$titleId")
            }
            removeTitleTabs(titleId)
            writeHistory(history().filterNot { it.titleId == titleId })
        } catch (_: Exception) {
        }
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

    fun autoDeleteRead(): Boolean = prefs().getBoolean("auto_delete_read", false)
    fun setAutoDeleteRead(v: Boolean) = prefs().edit { putBoolean("auto_delete_read", v) }

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

    /** Очищает кэш и возвращает освобождённые байты, либо -1 при ошибке. */
    fun clearCache(): Long {
        return try {
            val before = cacheSize()
            app.cacheDir.deleteRecursively()
            app.cacheDir.mkdirs()
            before
        } catch (_: Exception) {
            -1
        }
    }
}

/** Режимы чтения — нумерация как в Mihon (порядок пунктов меню). */
object ReadMode {
    const val DEFAULT = 0 // по умолчанию (слева направо)
    const val PAGED_LTR = 1 // постранично слева направо
    const val PAGED_RTL = 2 // постранично справа налево
    const val PAGED_VERTICAL = 3 // постранично вертикально
    const val STRIP = 4 // длинные страницы
    const val STRIP_GAPS = 5 // длинные с зазорами

    val labels = listOf(
        "По умолчанию",
        "Постранично (слева направо)",
        "Постранично (справа налево)",
        "Постранично (вертикально)",
        "Длинные страницы",
        "Длинные страницы с зазорами",
    )
}

/** Ориентация — как ReaderOrientation в Mihon. */
object Orient {
    const val DEFAULT = 0 // системная по умолчанию
    const val FREE = 1 // свободная (по датчику)
    const val PORTRAIT = 2 // портретная (датчик)
    const val LANDSCAPE = 3 // альбомная (датчик)
    const val LOCKED_PORTRAIT = 4
    const val LOCKED_LANDSCAPE = 5
    const val REVERSE_PORTRAIT = 6

    val labels = listOf(
        "По умолчанию",
        "Свободная",
        "Портретная",
        "Альбомная",
        "Заблокированная портретная",
        "Заблокированная альбомная",
        "Портретная наоборот",
    )
}

/** Зоны касания — как ViewerNavigation в Mihon. */
object TapZone {
    const val DEFAULT = 0
    const val L = 1
    const val KINDLE = 2
    const val EDGE = 3
    const val RIGHT_LEFT = 4
    const val DISABLED = 5

    val labels = listOf(
        "По умолчанию",
        "L-образная",
        "Kindle-подобная",
        "По краям",
        "Справа и слева",
        "Отключено",
    )
}

/** Метаданные произведения: описание, рейтинг, отзыв, автор. */
data class TitleMeta(
    val description: String = "",
    val rating: Double = 0.0,
    val review: String = "",
    val author: String = ""
)

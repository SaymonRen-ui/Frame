package com.saymon.frame.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymon.frame.core.cbz.ChapterInfo
import com.saymon.frame.core.cbz.ChapterSession
import com.saymon.frame.core.cbz.decodePage
import com.saymon.frame.data.NaturalSort
import com.saymon.frame.data.Progress
import com.saymon.frame.data.Store
import com.saymon.frame.data.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Library : Screen
    data class Title(val titleId: String) : Screen
    data class Reader(val titleId: String, val chapterUri: String) : Screen
    data object History : Screen
    data object Settings : Screen
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val ctx get() = getApplication<Application>()

    var screen by mutableStateOf<Screen>(Screen.Library)
        private set
    private val stack = ArrayDeque<Screen>()
    var backDepth by mutableStateOf(0)
        private set
    val canGoBack: Boolean get() = backDepth > 0

    fun go(s: Screen) {
        if (screen == s) return
        stack.addLast(screen)
        screen = s
        backDepth = stack.size
    }

    fun back(): Boolean {
        val p = stack.removeLastOrNull() ?: return false
        screen = p
        backDepth = stack.size
        return true
    }

    fun goTab(tab: Int) {
        bottomTab = tab
        go(Screen.Library)
    }

    var bottomTab by mutableStateOf(0)
        private set

    var theme by mutableStateOf(store.theme())
        private set

    fun chooseTheme(v: Int) {
        theme = v
        store.setTheme(v)
    }

    // --- Библиотека ---
    var titles by mutableStateOf<List<Title>>(emptyList())
        private set
    var covers by mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap?>>(emptyMap())
        private set
    var search by mutableStateOf("")
    var sortMode by mutableStateOf(0) // 0 — А-Я, 1 — недавно читал, 2 — недавно добавлено
    var activeTab by mutableStateOf("Все")
        private set
    var libTick by mutableStateOf(0)
        private set

    fun setTab(t: String) {
        activeTab = t
    }

    /** Корень библиотеки: /sdcard/Frame. */
    fun workDir(): java.io.File =
        java.io.File(
            android.os.Environment.getExternalStorageDirectory(),
            "Frame")

    fun hasFullAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 ||
            android.os.Environment.isExternalStorageManager()

    fun refresh() {
        libTick++
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    val root = workDir()
                    if (!root.isDirectory) return@withContext emptyList<Title>()
                    root.listFiles()?.toList()
                        ?.filter { it.isDirectory }
                        ?.map { Title(it.absolutePath, it.name) }
                        ?.sortedWith { a, b ->
                            NaturalSort.compare(a.name, b.name)
                        }.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            titles = list
            // Обложки — лениво, по мере списка
            for (t in list) {
                if (covers.containsKey(t.id)) continue
                val bmp = withContext(Dispatchers.IO) { coverOf(t) }
                covers = covers + (t.id to bmp)
            }
        }
    }

    private fun coverOf(t: Title):
        androidx.compose.ui.graphics.ImageBitmap? {
        return try {
            val dir = java.io.File(t.id)
            val cover = dir.listFiles()?.firstOrNull {
                val n = it.name.lowercase()
                (n == "cover.jpg" || n == "cover.png" ||
                    n == "cover.jpeg") && it.isFile
            }
            if (cover != null) {
                decodeCoverFile(cover)
            } else {
                val chaps = chapters(t.id, t.name)
                if (chaps.isEmpty()) return null
                val session = try {
                    ChapterSession.openFile(chaps.first().uri)
                } catch (_: Exception) {
                    return null
                }
                try {
                    val bmp = decodePage(session, 0, 512)?.asImageBitmap()
                    session.close()
                    bmp
                } catch (_: Exception) {
                    try {
                        session.close()
                    } catch (_: Exception) {
                    }
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeCoverFile(f: java.io.File):
        androidx.compose.ui.graphics.ImageBitmap? {
        return try {
            com.saymon.frame.core.cbz.decodeFile(f, 512)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun chapters(titleId: String, titleName: String): List<ChapterInfo> {
        return try {
            java.io.File(titleId).listFiles()?.toList()
                ?.filter {
                    it.isFile && it.name.lowercase().endsWith(".cbz")
                }
                ?.map { ChapterInfo(titleId, it.absolutePath, it.name) }
                ?.sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun titleName(titleId: String): String =
        titles.firstOrNull { it.id == titleId }?.name ?: "…"

    fun titleTabs(titleId: String): Set<String> =
        store.titleTabs()[titleId].orEmpty()

    fun toggleTitleTab(titleId: String, tab: String) {
        val cur = titleTabs(titleId).toMutableSet()
        if (tab in cur) cur.remove(tab) else cur.add(tab)
        store.setTitleTabs(titleId, cur)
    }

    fun progress(titleId: String, chapterUri: String): Int =
        store.progress(titleId, chapterUri)

    fun saveProgress(p: Progress) = store.setProgress(p)

    fun history(): List<Progress> = store.history()
    fun clearHistory() = store.clearHistory()
}

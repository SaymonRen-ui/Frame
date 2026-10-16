package com.saymon.frame.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymon.frame.core.cbz.ChapterInfo
import com.saymon.frame.core.cbz.loadCover
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

    fun setLibraryUri(uri: String) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        store.setLibraryUri(uri)
        refresh()
    }

    fun refresh() {
        libTick++
        viewModelScope.launch {
            val uri = store.libraryUri()
            if (uri.isEmpty()) {
                titles = emptyList()
                return@launch
            }
            val list = withContext(Dispatchers.IO) {
                try {
                    val root = DocumentFile.fromTreeUri(ctx, Uri.parse(uri))
                        ?: return@withContext emptyList<Title>()
                    (root.listFiles().toList())
                        .filter { it.isDirectory }
                        .map { Title(it.uri.toString(), it.name ?: "…") }
                        .sortedWith { a, b ->
                            NaturalSort.compare(a.name, b.name)
                        }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            titles = list
            // Обложки — лениво, по мере списка
            for (t in list) {
                if (covers.containsKey(t.id)) continue
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(t.id))
                            ?: return@withContext null
                        val chaps = chaptersOf(dir, t.id, t.name)
                        loadCover(ctx, dir, chaps)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                covers = covers + (t.id to bmp)
            }
        }
    }

    fun chapters(titleId: String, titleName: String): List<ChapterInfo> {
        return try {
            val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(titleId))
                ?: return emptyList()
            chaptersOf(dir, titleId, titleName)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun chaptersOf(
        dir: DocumentFile,
        titleId: String,
        @Suppress("UNUSED_PARAMETER") titleName: String,
    ): List<ChapterInfo> {
        return try {
            dir.listFiles().toList()
                .filter {
                    it.isFile && (it.name ?: "").lowercase().endsWith(".cbz")
                }
                .map { ChapterInfo(titleId, it.uri.toString(), it.name ?: "…") }
                .sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }
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

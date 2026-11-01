package com.saymon.frame.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saymon.frame.core.cbz.ChapterInfo
import com.saymon.frame.core.cbz.ChapterSession
import com.saymon.frame.core.cbz.decodePage
import com.saymon.frame.data.NaturalSort
import com.saymon.frame.data.Progress
import com.saymon.frame.data.Store
import com.saymon.frame.data.Title
import com.saymon.frame.data.TitleMeta
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

    /** Запомнить текущую главу читалки, чтобы поворот экрана её не сбрасывал. */
    fun setReaderChapter(titleId: String, chapterUri: String) {
        val cur = screen
        if (cur is Screen.Reader && cur.chapterUri != chapterUri) {
            screen = Screen.Reader(titleId, chapterUri)
        }
    }

    fun goTab(tab: Int) {
        bottomTab = tab
        if (screen != Screen.Library) {
            screen = Screen.Library
            stack.clear()
            backDepth = 0
        }
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

    /** Перерисовать списки без повторного чтения папок. */
    fun touch() {
        libTick++
    }

    fun setTab(t: String) {
        activeTab = t
    }

    fun clearCovers() {
        covers = emptyMap()
    }

    fun libraryUsesSaf(): Boolean = store.libraryUri().startsWith("content://")

    private fun rootDocument(): DocumentFile? {
        val raw = store.libraryUri()
        if (!raw.startsWith("content://")) return null
        return try { DocumentFile.fromTreeUri(ctx, Uri.parse(raw)) } catch (_: Exception) { null }
    }

    private fun document(uri: String): DocumentFile? {
        if (!uri.startsWith("content://")) return null
        return try {
            val parsed = Uri.parse(uri)
            if (DocumentsContract.isTreeUri(parsed)) {
                DocumentFile.fromTreeUri(ctx, parsed)
            } else {
                val single = DocumentFile.fromSingleUri(ctx, parsed) ?: return null
                // SingleDocumentFile не умеет listFiles(): для папок собираем tree-URI.
                if (single.isDirectory) {
                    val docId = DocumentsContract.getDocumentId(parsed)
                    val tree = DocumentsContract.buildTreeDocumentUri(parsed.authority, docId)
                    DocumentFile.fromTreeUri(ctx, tree) ?: single
                } else {
                    single
                }
            }
        } catch (_: Exception) { null }
    }

    /** Удаляет файл или папку независимо от способа доступа. */
    private fun deleteDocument(uri: String, recursive: Boolean = false): Boolean {
        return try {
            if (uri.startsWith("content://")) {
                val doc = document(uri) ?: return false
                doc.delete() || !doc.exists()
            } else {
                val file = java.io.File(uri)
                val ok = if (recursive) file.deleteRecursively() else file.delete()
                ok || !file.exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun libraryLocation(): String = if (libraryUsesSaf()) {
        rootDocument()?.name?.let { "Папка «$it»" } ?: "Папка недоступна"
    } else {
        if (hasLegacyAccess() && workDir().isDirectory) "Папка Frame в корне устройства"
        else "Папка не выбрана"
    }

    fun setLibraryTree(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
            val root = DocumentFile.fromTreeUri(ctx, uri)
            if (root?.exists() != true || root.canRead() != true || root.canWrite() != true) {
                runCatching { ctx.contentResolver.releasePersistableUriPermission(uri, flags) }
                return false
            }
            val old = store.libraryUri()
            store.setLibraryUri(uri.toString())
            if (old.startsWith("content://") && old != uri.toString()) {
                runCatching {
                    ctx.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags)
                }
            }
            covers = emptyMap()
            refresh()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearLibraryTree() {
        store.setLibraryUri("")
        covers = emptyMap()
        refresh()
    }

    /** Корень библиотеки: /sdcard/Frame. */
    fun workDir(): java.io.File =
        java.io.File(Environment.getExternalStorageDirectory(), "Frame")

    /** Прямой доступ к /sdcard/Frame возможен только до Android 11. */
    fun hasLegacyAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return false
        val read = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val write = Build.VERSION.SDK_INT > 29 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    fun hasFullAccess(): Boolean {
        if (libraryUsesSaf()) {
            val root = rootDocument() ?: return false
            return root.canRead() && root.canWrite()
        }
        return hasLegacyAccess()
    }

    /** Нужно выбрать папку библиотеки (первый запуск или доступ отозван). */
    fun needsLibrarySetup(): Boolean = !hasFullAccess()

    fun refresh() {
        libTick++
        viewModelScope.launch {
            // Без SAF-папки и без legacy-доступа список не читаем: система может
            // отдать имена папок, но файлы внутри будут недоступны.
            if (!libraryUsesSaf() && !hasLegacyAccess()) {
                titles = emptyList()
                covers = emptyMap()
                return@launch
            }
            val list = withContext(Dispatchers.IO) {
                try {
                    if (libraryUsesSaf()) {
                        rootDocument()?.listFiles()?.asSequence()
                            ?.filter { it.isDirectory }
                            ?.map { Title(it.uri.toString(), it.name ?: "Без названия") }
                            ?.sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }
                            ?.toList().orEmpty()
                    } else {
                        val root = workDir()
                        if (!root.isDirectory) return@withContext emptyList<Title>()
                        root.listFiles()?.toList()
                            ?.filter { it.isDirectory }
                            ?.map { Title(it.absolutePath, it.name) }
                            ?.sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }
                            .orEmpty()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            titles = list
            val ids = list.mapTo(HashSet()) { it.id }
            val pruned = covers.filterKeys { it in ids }
            if (pruned.size != covers.size) covers = pruned
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
            val coverUri = if (t.id.startsWith("content://")) {
                document(t.id)?.listFiles()?.firstOrNull { f ->
                    val n = (f.name ?: "").lowercase()
                    (n == "cover.jpg" || n == "cover.png" || n == "cover.jpeg") && f.isFile
                }?.uri?.toString()
            } else {
                java.io.File(t.id).listFiles()?.firstOrNull { f ->
                    val n = f.name.lowercase()
                    (n == "cover.jpg" || n == "cover.png" || n == "cover.jpeg") && f.isFile
                }?.absolutePath
            }
            if (coverUri != null) {
                decodeCoverFile(materializeChapter(coverUri))
            } else {
                val chaps = chapters(t.id, t.name)
                if (chaps.isEmpty()) return null
                val session = try { ChapterSession.openFile(materializeChapter(chaps.first().uri)) }
                catch (_: Exception) { return null }
                try {
                    val bmp = decodePage(session, 0, 512)?.asImageBitmap()
                    session.close(); bmp
                } catch (_: Exception) { runCatching { session.close() }; null }
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
        val list = try {
            if (titleId.startsWith("content://")) {
                document(titleId)?.listFiles()?.toList()
                    ?.filter { it.isFile && ((it.name ?: "").lowercase().endsWith(".cbz") ||
                        (it.name ?: "").lowercase().endsWith(".pdf")) }
                    ?.map { ChapterInfo(titleId, it.uri.toString(), it.name ?: "Глава") }
                    .orEmpty()
            } else java.io.File(titleId).listFiles()?.toList()
                ?.filter {
                    if (!it.isFile) return@filter false
                    val n = it.name.lowercase()
                    n.endsWith(".cbz") || n.endsWith(".pdf")
                }
                ?.map { ChapterInfo(titleId, it.absolutePath, it.name) }
                ?.distinctBy { it.uri }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        return list.sortedWith { a, b -> NaturalSort.compare(a.name, b.name) }
    }

    /** Время изменения произведения — для сортировки «недавно добавлено». */
    fun titleModified(titleId: String): Long {
        return try {
            if (titleId.startsWith("content://")) {
                val dir = document(titleId) ?: return 0L
                dir.listFiles().maxOfOrNull { it.lastModified() } ?: dir.lastModified()
            } else {
                java.io.File(titleId).lastModified()
            }
        } catch (_: Exception) {
            0L
        }
    }

    /** Инвалидировать кэш обложки произведения. */
    fun invalidateCover(titleId: String) {
        covers = covers - titleId
    }

    data class ImportResult(val imported: Int, val skipped: Int, val failed: Int)

    /** Новая папка произведения в библиотеке. */
    fun addTitle(name: String): Boolean {
        return try {
            val clean = name.trim().replace("/", "_").take(80)
            if (clean.isEmpty()) return false
            if (libraryUsesSaf()) {
                val root = rootDocument() ?: return false
                if (root.listFiles().any { it.isDirectory && it.name == clean }) return false
                if (root.createDirectory(clean) == null) return false
            } else {
                val dir = java.io.File(workDir(), clean)
                if (dir.exists()) return false
                if (!dir.mkdirs() && !dir.isDirectory) return false
            }
            refresh()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Удалить произведение целиком: папку с главами и обложкой. */
    fun deleteTitle(titleId: String) {
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) { deleteDocument(titleId, recursive = true) }
            if (gone) {
                store.removeTitleData(titleId)
                covers = covers - titleId
            }
            refresh()
        }
    }

    /** Удалить только главы (CBZ) произведения, оставив папку и обложку. */
    fun deleteChapters(titleId: String) {
        val uris = chapters(titleId, titleName(titleId)).map { it.uri }
        deleteChapterUris(titleId, uris)
    }

    /** Удалить конкретные главы и вычистить их прогресс/историю. */
    fun deleteChapterUris(titleId: String, uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val gone = deleteDocument(uri)
                    if (gone) store.removeChapterData(titleId, uri)
                }
            }
            invalidateCover(titleId)
            refresh()
        }
    }

    /** Скопировать CBZ в папку произведения. */
    fun importChapters(
        titleId: String,
        uris: List<Uri>,
        onComplete: (ImportResult) -> Unit = {},
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var imported = 0
                var skipped = 0
                var failed = 0
                try {
                    val dir = if (titleId.startsWith("content://")) document(titleId) else null
                    if (titleId.startsWith("content://") && dir?.isDirectory != true) {
                        return@withContext ImportResult(0, 0, uris.size)
                    }
                    if (!titleId.startsWith("content://")) {
                        val legacy = java.io.File(titleId)
                        if (!legacy.isDirectory && !legacy.mkdirs()) return@withContext ImportResult(0, 0, uris.size)
                    }
                    for (uri in uris) {
                        val raw = (displayName(uri) ?: "глава").replace("/", "_")
                        val dot = raw.lastIndexOf('.')
                        val base = (if (dot > 0) raw.substring(0, dot) else raw)
                            .trim().ifBlank { "глава" }.take(80)
                        val ext = if (dot > 0) raw.substring(dot).lowercase() else ""
                        val keepExt = when (ext) {
                            ".pdf" -> ".pdf"
                            ".cbz", ".zip" -> ".cbz"
                            else -> null
                        }
                        if (keepExt == null) {
                            skipped++
                            continue
                        }
                        val safe = base + keepExt
                        val srcSize = fileSize(uri)
                        val existing = if (dir != null) {
                            dir.listFiles().firstOrNull { it.name == safe }
                        } else null
                        if (srcSize != null && ((existing?.isFile == true && existing.length() == srcSize) ||
                                (existing == null && java.io.File(titleId, safe).isFile && java.io.File(titleId, safe).length() == srcSize))) {
                            // Такой файл уже импортирован — не плодим дубликаты.
                            skipped++
                            continue
                        }
                        var i = 1
                        var destName = safe
                        fun occupied(name: String): Boolean = if (dir != null) {
                            dir.listFiles().any { it.name == name }
                        } else java.io.File(titleId, name).exists()
                        while (occupied(destName)) {
                            val d = safe.lastIndexOf('.')
                            val b = if (d > 0) safe.substring(0, d) else safe
                            val e = if (d > 0) safe.substring(d) else ""
                            destName = "$b ($i)$e"
                            i++
                        }
                        val tmp = java.io.File(ctx.cacheDir, "import-${System.currentTimeMillis()}-$i$keepExt")
                        val ok = try {
                            ctx.contentResolver.openInputStream(uri)?.use { inp ->
                                tmp.outputStream().use { inp.copyTo(it) }
                                true
                            } ?: false
                        } catch (_: Exception) {
                            false
                        }
                        if (!ok) {
                            tmp.delete()
                            failed++
                            continue
                        }
                        val valid = try {
                            val s = ChapterSession.openFile(tmp)
                            val good = s.entries.isNotEmpty()
                            s.close(); good
                        } catch (_: Exception) { false }
                        if (!valid) {
                            tmp.delete(); failed++; continue
                        }
                        if (dir != null) {
                            val mime = if (keepExt == ".pdf") "application/pdf" else "application/zip"
                            val out = dir.createFile(mime, destName)
                            val copied = try {
                                out?.uri?.let { target ->
                                    ctx.contentResolver.openOutputStream(target)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                                    true
                                } ?: false
                            } catch (_: Exception) { false }
                            if (!copied) { out?.delete(); tmp.delete(); failed++; continue }
                            tmp.delete()
                        } else {
                            val dest = java.io.File(titleId, destName)
                            try { tmp.copyTo(dest, overwrite = false); tmp.delete() }
                            catch (_: Exception) { tmp.delete(); failed++; continue }
                        }
                        imported++
                    }
                } catch (_: Exception) {
                    failed++
                }
                ImportResult(imported, skipped, failed)
            }
            if (result.imported > 0) invalidateCover(titleId)
            refresh()
            onComplete(result)
        }
    }

    private fun displayName(uri: Uri): String? {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fileSize(uri: Uri): Long? {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(
                        android.provider.OpenableColumns.SIZE)
                    if (i >= 0 && !c.isNull(i)) c.getLong(i) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Возвращает локальный файл для движка PdfRenderer/ZipFile. SAF-документы кэшируются. */
    fun materializeChapter(uri: String): java.io.File {
        if (!uri.startsWith("content://")) return java.io.File(uri)
        val name = (displayName(Uri.parse(uri)) ?: "chapter.cbz")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = java.io.File(ctx.cacheDir, "chapters").apply { mkdirs() }
        val stamp = try { document(uri)?.lastModified() ?: 0L } catch (_: Exception) { 0L }
        val key = Integer.toHexString(uri.hashCode()) + "-" + stamp + "-" + name
        val out = java.io.File(dir, key)
        val expected = fileSize(Uri.parse(uri))
        if (out.isFile && (expected == null || out.length() == expected)) return out
        synchronized(this) {
            if (out.isFile && (expected == null || out.length() == expected)) return out
            val temp = java.io.File(dir, ".$key.tmp")
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Документ недоступен")
            if (!temp.renameTo(out)) {
                temp.copyTo(out, overwrite = true)
                temp.delete()
            }
            return out
        }
    }

    fun exists(uri: String): Boolean = if (uri.startsWith("content://")) {
        document(uri)?.isFile == true
    } else java.io.File(uri).isFile

    fun lastModified(uri: String): Long = if (uri.startsWith("content://")) {
        document(uri)?.lastModified() ?: 0L
    } else java.io.File(uri).lastModified()

    fun importCover(titleId: String, source: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (titleId.startsWith("content://")) {
                        val dir = document(titleId) ?: return@withContext
                        dir.listFiles().filter {
                            val n = (it.name ?: "").lowercase()
                            it.isFile && (n == "cover.jpg" || n == "cover.png" || n == "cover.jpeg")
                        }.forEach { it.delete() }
                        val target = dir.createFile("image/jpeg", "cover.jpg") ?: return@withContext
                        val ok = ctx.contentResolver.openInputStream(source)?.use { input ->
                            ctx.contentResolver.openOutputStream(target.uri)?.use { output -> input.copyTo(output) }
                            true
                        } ?: false
                        if (!ok) target.delete()
                    } else {
                        val dir = java.io.File(titleId).apply { mkdirs() }
                        dir.listFiles()?.filter {
                            val n = it.name.lowercase()
                            it.isFile && (n == "cover.jpg" || n == "cover.png" || n == "cover.jpeg")
                        }?.forEach { it.delete() }
                        val target = java.io.File(dir, "cover.jpg")
                        ctx.contentResolver.openInputStream(source)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@withContext
                    }
                } catch (_: Exception) { }
            }
            invalidateCover(titleId)
            refresh()
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
    fun removeHistoryEntry(titleId: String, chapterUri: String) =
        store.removeHistory(titleId, chapterUri)

    // --- Главы: прочитано / избранное / удаление ---
    fun chapterReadSet(titleId: String): Set<String> = store.strSet("read|$titleId")

    /** Глава отмечена прочитанной, когда дочитана до конца. */
    fun isChapterRead(titleId: String, chapterUri: String): Boolean =
        chapterUri in chapterReadSet(titleId)

    fun setChapterRead(titleId: String, chapterUri: String, read: Boolean) {
        val s = chapterReadSet(titleId).toMutableSet()
        if (read) s.add(chapterUri) else s.remove(chapterUri)
        store.setStrSet("read|$titleId", s)
    }

    fun chapterFavSet(titleId: String): Set<String> = store.strSet("fav|$titleId")

    /** Вернёт true, если глава теперь в избранном. */
    fun toggleChapterFav(titleId: String, chapterUri: String): Boolean {
        val s = chapterFavSet(titleId).toMutableSet()
        val nowFav = if (chapterUri in s) { s.remove(chapterUri); false }
        else { s.add(chapterUri); true }
        store.setStrSet("fav|$titleId", s)
        return nowFav
    }

    fun deleteChapter(titleId: String, chapterUri: String) {
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) { deleteDocument(chapterUri) }
            if (gone) store.removeChapterData(titleId, chapterUri)
            if (gone) invalidateCover(titleId)
            refresh()
        }
    }

    /** Проверяет все главы библиотеки на целостность CBZ/PDF. Возвращает список повреждённых. */
    fun checkCorruptFiles(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val corrupt = withContext(Dispatchers.IO) {
                val result = mutableListOf<String>()
                for (title in titles) {
                    for (ch in chapters(title.id, title.name)) {
                        try {
                            val file = materializeChapter(ch.uri)
                            val session = ChapterSession.openFile(file)
                            if (session.entries.isEmpty()) {
                                result.add("${title.name} / ${ch.name}")
                            }
                            session.close()
                        } catch (_: Exception) {
                            result.add("${title.name} / ${ch.name}")
                        }
                    }
                }
                result
            }
            onResult(corrupt)
        }
    }

    fun autoDeleteRead(): Boolean = store.autoDeleteRead()
    fun setAutoDeleteRead(value: Boolean) = store.setAutoDeleteRead(value)

    // --- Метаданные ---
    fun titleMeta(titleId: String): TitleMeta = store.titleMeta(titleId)
    fun setTitleMeta(titleId: String, meta: TitleMeta) = store.setTitleMeta(titleId, meta)
}

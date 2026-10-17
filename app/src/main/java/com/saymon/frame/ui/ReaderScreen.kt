package com.saymon.frame.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.saymon.frame.core.cbz.ChapterSession
import com.saymon.frame.core.cbz.decodePage
import com.saymon.frame.data.Orient
import com.saymon.frame.data.Progress
import com.saymon.frame.data.ReadMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    vm: LibraryViewModel,
    titleId: String,
    chapterUri: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val chapters = remember { vm.chapters(titleId, vm.titleName(titleId)) }
    var idx by remember(chapterUri) {
        mutableIntStateOf(chapters.indexOfFirst { it.uri == chapterUri }.coerceAtLeast(0))
    }
    val chapter = chapters.getOrNull(idx)
    val mode = vm.store.readMode()
    val zonesOff = vm.store.tapZones() == 1
    val dtZoom = vm.store.doubleTapZoom()
    val showNum = vm.store.pageNumber()
    val bg = when (vm.store.bgColor()) {
        1 -> Color(0xFF2A2A2E)
        2 -> Color.White
        else -> Color.Black
    }

    LaunchedEffect(Unit) {
        (context as? Activity)?.requestedOrientation = when (vm.store.orientation()) {
            Orient.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orient.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val win = (context as? Activity)?.window
        if (vm.store.fullscreen()) {
            win?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        } else {
            win?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    DisposableEffect(Unit) {
        val v = view
        val old = v.keepScreenOn
        v.keepScreenOn = vm.store.keepScreen()
        onDispose {
            v.keepScreenOn = old
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            v.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    var session by remember { mutableStateOf<ChapterSession?>(null) }
    var pages by remember { mutableIntStateOf(0) }
    var loadErr by remember { mutableStateOf("") }
    LaunchedEffect(chapter?.uri) {
        session?.close()
        session = null
        pages = 0
        loadErr = ""
        val ch = chapter ?: return@LaunchedEffect
        try {
            val s = withContext(Dispatchers.IO) { ChapterSession.openFile(ch.uri) }
            session = s
            pages = s.entries.size
        } catch (e: Exception) {
            loadErr = e.message ?: "Не открылась глава"
        }
    }
    DisposableEffect(Unit) { onDispose { session?.close() } }

    var page by remember(chapter?.uri) {
        mutableIntStateOf(vm.progress(titleId, chapter?.uri.orEmpty()))
    }
    var menuOpen by remember { mutableStateOf(false) }
    var menuTab by remember { mutableStateOf(0) }

    fun save() {
        val ch = chapter ?: return
        if (pages <= 0) return
        vm.saveProgress(Progress(titleId, vm.titleName(titleId),
            ch.uri, ch.name, page.coerceIn(0, pages - 1), pages, System.currentTimeMillis()))
    }

    if (chapter == null || loadErr.isNotEmpty()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("Глава") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } }
                )
            }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(if (chapter == null) "Глава не найдена" else loadErr)
            }
        }
        return
    }

    val hPager = rememberPagerState(initialPage = page.coerceIn(0, maxOf(pages - 1, 0))) { maxOf(pages, 1) }
    val vPager = rememberPagerState(initialPage = page.coerceIn(0, maxOf(pages - 1, 0))) { maxOf(pages, 1) }
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = page.coerceIn(0, maxOf(pages - 1, 0)))

    LaunchedEffect(hPager.currentPage, vPager.currentPage) {
        val cur = when (mode) {
            ReadMode.PAGED_LTR, ReadMode.PAGED_RTL -> hPager.currentPage
            ReadMode.PAGED_VERTICAL -> vPager.currentPage
            else -> page
        }
        if (cur != page && cur in 0 until pages) { page = cur; save() }
    }
    LaunchedEffect(stripState.firstVisibleItemIndex) {
        if (mode == ReadMode.STRIP || mode == ReadMode.STRIP_GAPS) {
            val cur = stripState.firstVisibleItemIndex
            if (cur != page && cur in 0 until pages) { page = cur; save() }
        }
    }

    fun goNext() = scope.launch {
        when (mode) {
            ReadMode.PAGED_LTR -> hPager.animateScrollToPage((page + 1).coerceAtMost(pages - 1))
            ReadMode.PAGED_RTL -> hPager.animateScrollToPage((page - 1).coerceAtLeast(0))
            ReadMode.PAGED_VERTICAL -> vPager.animateScrollToPage((page + 1).coerceAtMost(pages - 1))
            else -> stripState.animateScrollToItem((page + 1).coerceAtMost(pages - 1))
        }
    }
    fun goPrev() = scope.launch {
        when (mode) {
            ReadMode.PAGED_LTR -> hPager.animateScrollToPage((page - 1).coerceAtLeast(0))
            ReadMode.PAGED_RTL -> hPager.animateScrollToPage((page + 1).coerceAtMost(pages - 1))
            ReadMode.PAGED_VERTICAL -> vPager.animateScrollToPage((page - 1).coerceAtLeast(0))
            else -> stripState.animateScrollToItem((page - 1).coerceAtLeast(0))
        }
    }
    fun prevChapter() { save(); if (idx > 0) { idx--; page = 0 } }
    fun nextChapter() { save(); if (idx < chapters.size - 1) { idx++; page = 0 } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = bg,
        topBar = {
            if (menuOpen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(vm.titleName(titleId), style = MaterialTheme.typography.titleSmall)
                            Text(chapter.name, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { save(); onBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    })
            }
        },
        bottomBar = {
            if (menuOpen) {
                Column(Modifier.background(bg).padding(12.dp)) {
                    if (showNum && pages > 0) {
                        Text("${page + 1} / $pages", color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Slider(value = page.toFloat(), onValueChange = { page = it.toInt() }, valueRange = 0f..maxOf(pages - 1, 0).toFloat(),
                            onValueChangeFinished = { scope.launch {
                                when (mode) {
                                    ReadMode.PAGED_LTR, ReadMode.PAGED_RTL -> hPager.scrollToPage(page)
                                    ReadMode.PAGED_VERTICAL -> vPager.scrollToPage(page)
                                    else -> stripState.scrollToItem(page)
                                }
                                save()
                            }})
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { prevChapter() }, enabled = idx > 0, modifier = Modifier.fillMaxWidth(0.5f)) { Text("← Глава") }
                        OutlinedButton(onClick = { nextChapter() }, enabled = idx < chapters.size - 1, modifier = Modifier.fillMaxWidth(0.5f)) { Text("Глава →") }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(bg)) {
            if (pages == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Готовлю страницы…", color = Color.White) }
            } else when (mode) {
                ReadMode.PAGED_LTR -> HorizontalPager(state = hPager, modifier = Modifier.fillMaxSize()) { i -> PageView(session, i, dtZoom) }
                ReadMode.PAGED_RTL -> HorizontalPager(state = hPager, reverseLayout = true, modifier = Modifier.fillMaxSize()) { i -> PageView(session, i, dtZoom) }
                ReadMode.PAGED_VERTICAL -> VerticalPager(state = vPager, modifier = Modifier.fillMaxSize()) { i -> PageView(session, i, dtZoom) }
                ReadMode.STRIP -> LazyColumn(state = stripState, modifier = Modifier.fillMaxSize()) { items(pages) { i -> PageView(session, i, dtZoom) } }
                else -> LazyColumn(state = stripState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(pages) { i -> PageView(session, i, dtZoom) } }
            }
            // Меню настроек поверх всего
            if (menuOpen) {
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .clickable { menuOpen = false }
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(top = 56.dp, bottom = 16.dp)
                    ) {
                        ReaderMenu(vm, titleId, mode, { menuOpen = false })
                    }
                }
            }
            // Зоны касания: лево/право — листать, центр — меню по долгому нажатию
            if (!zonesOff && pages > 0 && !menuOpen) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth(0.33f).clickable { goPrev() })
                    Box(Modifier.fillMaxWidth(0.34f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { menuOpen = true }
                            )
                        })
                    Box(Modifier.fillMaxWidth(0.33f).clickable { goNext() })
                }
            }
        }
    }
}

/** Меню настроек читалки (как в Mihon). */
@Composable
private fun ReaderMenu(
    vm: LibraryViewModel,
    titleId: String,
    currentMode: Int,
    onClose: () -> Unit,
) {
    val readModeNames = listOf(
        "По умолчанию", "Постранично (Слева направо)", "Постранично (Справа налево)",
        "Постранично (Вертикально)", "Длинные страницы", "Длинные страницы с зазорами")
    val orientNames = listOf("По умолчанию", "Системная", "Портретная", "Альбомная")
    val zonesNames = listOf("По умолчанию", "L-образная", "Kindle-подобная", "По краям", "Справа и слева", "Отключено")
    val navPosNames = listOf("Нет", "Горизонтально", "Вертикально", "Обеоси")

    var menuTab by remember { mutableStateOf(0) }

    val surface = Color(0xFF1E1E1E) // тёмный фон меню как в Mihon
    val surfaceVariant = Color(0xFF2A2A2E)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = Color.White
    val onSurfaceVariant = Color(0xFFB0B0B0)
    val outline = Color(0xFF3A3A3A)

    Column(
        Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(16.dp))
    ) {
        // Вкладки в верхней части
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TabButton("Режим чтения", menuTab == 0) { menuTab = 0 }
            TabButton("Основные", menuTab == 1) { menuTab = 1 }
            TabButton("Пользователь...", menuTab == 2) { menuTab = 2 }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(outline))

        // Прокручиваемое содержимое
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            when (menuTab) {
                0 -> {
                    SectionHeader("Для этой серии")
                    SubHeader("Режим чтения")
                    readModeNames.forEachIndexed { i, name ->
                        ModernOptionButton(title = name, selected = currentMode == i, onClick = { vm.store.setReadMode(i) })
                    }
                    SectionHeader("Ориентация")
                    orientNames.forEachIndexed { i, name ->
                        ModernOptionButton(title = name, selected = vm.store.orientation() == i, onClick = { vm.store.setOrientation(i) })
                    }
                }
                1 -> {
                    SectionHeader("Длинные страницы")
                    zonesNames.forEachIndexed { i, name ->
                        ModernOptionButton(title = name, selected = vm.store.tapZones() == i, onClick = { vm.store.setTapZones(i) })
                    }
                    SectionHeader("Зоны касания наоборот")
                    navPosNames.forEachIndexed { i, name ->
                        ModernOptionButton(title = name, selected = false, onClick = { /* подробные настройки зон */ })
                    }
                    SectionHeader("Боковой отступ")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(value = 0f, onValueChange = {}, valueRange = 0f..100f)
                        Text("0 %", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant)
                    }
                }
                2 -> {
                    SectionHeader("Цвет фона")
                    listOf("Чёрный" to 0, "Серый" to 1, "Белый" to 2, "Автоматический" to 3).forEach { (name, v) ->
                        ModernOptionButton(title = name, selected = vm.store.bgColor() == v, onClick = { vm.store.setBgColor(v) })
                    }
                    ModernCheckboxRow(title = "Номер страницы", checked = vm.store.pageNumber(), onChange = { vm.store.setPageNumber(it) })
                    ModernCheckboxRow(title = "Использовать вертикальную навигацию глав в длинных страницах", checked = false, onChange = {})
                    ModernCheckboxRow(title = "Полноэкранный режим", checked = vm.store.fullscreen(), onChange = { vm.store.setFullscreen(it) })
                    ModernCheckboxRow(title = "Показывать содержание в вырезанной области", checked = false, onChange = {})
                    ModernCheckboxRow(title = "Не выключать экран", checked = vm.store.keepScreen(), onChange = { vm.store.setKeepScreen(it) })
                    ModernCheckboxRow(title = "Показать действия при долгом нажатии", checked = true, onChange = {})
                    ModernCheckboxRow(title = "Переход между главами", checked = true, onChange = {})
                    ModernCheckboxRow(title = "Анимированные переходы страниц", checked = true, onChange = {})
                    ModernCheckboxRow(title = "Мигать при смене страницы", checked = false, onChange = {})
                }
            }
            Box(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(outline))
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onClose) { Text("Закрыть") }
        }
    }
}

@Composable
private fun TabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = Color(0xFFB0B0B0)
    Column(
        Modifier
            .padding(horizontal = 4.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) primary else onSurfaceVariant)
        if (selected) {
            Box(Modifier.width(24.dp).height(3.dp).background(primary))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFFB0B0B0))
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFFB0B0B0),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun ModernOptionButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() }
            .background(
                if (selected) primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .border(
                if (selected) 1.dp else 0.dp,
                primary,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (selected) primary else onSurface, modifier = Modifier.fillMaxWidth())
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = primary)
        }
    }
}

@Composable
private fun ModernCheckboxRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = Color.White
    val surfaceVariant = Color(0xFFB0B0B0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .border(
                    2.dp,
                    if (checked) primary else surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                )
                .background(if (checked) primary else Color.Transparent)
                .clickable { onChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.fillMaxWidth()) { Text(title, style = MaterialTheme.typography.bodyMedium, color = onSurface, modifier = Modifier.padding(start = 12.dp)) }
    }
}

@Composable
private fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)) }

@Composable
private fun PageView(session: ChapterSession?, index: Int, dtZoom: Boolean) {
    var bmp by remember(session, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(session, index) {
        bmp = withContext(Dispatchers.IO) { val b = session?.let { decodePage(it, index) }; b?.asImageBitmap() }
    }
    var zoom by remember(session, index) { mutableFloatStateOf(1f) }
    var offX by remember(session, index) { mutableFloatStateOf(0f) }
    var offY by remember(session, index) { mutableFloatStateOf(0f) }
    val b = bmp
    if (b == null) {
        Box(Modifier.fillMaxSize().aspectRatio(3f / 4f), contentAlignment = Alignment.Center) { Text("…", color = Color.Gray) }
    } else {
        var mod: Modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offX, translationY = offY)
        if (dtZoom) {
            mod = mod.pointerInput(session, index) { detectTapGestures(onDoubleTap = { if (zoom > 1f) { zoom = 1f; offX = 0f; offY = 0f } else { zoom = 2.2f } }) }
        }
        if (zoom > 1f) {
            mod = mod.pointerInput(session, index) { detectDragGestures { change, drag -> change.consume(); offX += drag.x; offY += drag.y } }
        }
        Image(b, contentDescription = "Стр. ${index + 1}", contentScale = ContentScale.FillWidth, modifier = mod)
    }
}

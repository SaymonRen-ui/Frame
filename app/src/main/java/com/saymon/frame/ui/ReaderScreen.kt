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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
    var settingsTick by remember { mutableStateOf(0) }
    val mode = remember(settingsTick) { vm.store.readMode() }
    val zonesOff = remember(settingsTick) { vm.store.tapZones() == 1 || vm.store.tapZones() == 5 }
    val dtZoom = remember(settingsTick) { vm.store.doubleTapZoom() }
    val showNum = remember(settingsTick) { vm.store.pageNumber() }
    val bg = remember(settingsTick) {
        when (vm.store.bgColor()) {
            1 -> Color(0xFF2A2A2E)
            2 -> Color.White
            else -> Color.Black
        }
    }

    LaunchedEffect(settingsTick) {
        (context as? Activity)?.requestedOrientation = when (vm.store.orientation()) {
            2, 4, 6 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            3, 5 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        view.keepScreenOn = vm.store.keepScreen()
    }
    DisposableEffect(Unit) {
        val v = view
        onDispose {
            v.keepScreenOn = false
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
    var autoDeleted by remember(chapter?.uri) { mutableStateOf(false) }

    fun save() {
        val ch = chapter ?: return
        if (pages <= 0) return
        val last = page.coerceIn(0, pages - 1)
        vm.saveProgress(Progress(titleId, vm.titleName(titleId),
            ch.uri, ch.name, last, pages, System.currentTimeMillis()))
        // Дошли до последней страницы — отмечаем главу прочитанной
        if (pages > 1 && last >= pages - 1) {
            vm.setChapterRead(titleId, ch.uri, true)
            if (vm.autoDeleteRead() && !autoDeleted) {
                autoDeleted = true
                vm.deleteChapter(ch.uri)
            }
        }
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

    // Короткий тап по краям — перелистывание, по центру — меню; долгое нажатие — меню.
    val onPageTap: (Float) -> Unit = { frac ->
        if (!zonesOff) {
            val rtl = mode == ReadMode.PAGED_RTL
            when {
                frac < 0.33f -> if (rtl) goNext() else goPrev()
                frac > 0.66f -> if (rtl) goPrev() else goNext()
                else -> menuOpen = true
            }
        }
    }
    val onPageLong: () -> Unit = { menuOpen = true }

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
            // Навигация по главам убрана из-под меню — меню настроек идёт до самого низа.
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(bg)) {
            if (pages == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Готовлю страницы…", color = Color.White) }
            } else {
                when (mode) {
                    ReadMode.PAGED_LTR -> HorizontalPager(state = hPager, modifier = Modifier.fillMaxSize()) { i -> PageView(session, i, dtZoom, onPageTap, onPageLong) }
                    ReadMode.PAGED_RTL -> HorizontalPager(state = hPager, reverseLayout = true, modifier = Modifier.fillMaxSize()) { i -> PageView(session, i, dtZoom, onPageTap, onPageLong) }
                    ReadMode.PAGED_VERTICAL -> VerticalPager(state = vPager, modifier = Modifier.fillMaxSize()) { i -> PageView(session, i, dtZoom, onPageTap, onPageLong) }
                    ReadMode.STRIP -> LazyColumn(state = stripState, modifier = Modifier.fillMaxSize()) { items(pages) { i -> PageView(session, i, dtZoom, onPageTap, onPageLong) } }
                    else -> LazyColumn(state = stripState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(pages) { i -> PageView(session, i, dtZoom, onPageTap, onPageLong) } }
                }
            }
            // Меню настроек поверх всего — нижняя панель как в Mihon
            if (menuOpen) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { menuOpen = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .pointerInput(Unit) { detectTapGestures { } }
                    ) {
                        ReaderMenu(
                            vm, titleId, mode, page, pages, settingsTick,
                            onPageChange = { page = it.coerceIn(0, maxOf(pages - 1, 0)) },
                            onPageCommit = {
                                scope.launch {
                                    when (mode) {
                                        ReadMode.PAGED_LTR, ReadMode.PAGED_RTL -> hPager.scrollToPage(page)
                                        ReadMode.PAGED_VERTICAL -> vPager.scrollToPage(page)
                                        else -> stripState.scrollToItem(page)
                                    }
                                    save()
                                }
                            },
                            onClose = { menuOpen = false },
                            onChanged = { settingsTick++ }
                        )
                    }
                }
            }
        }
    }
}

/** Палитра меню читалки — строится из цветов текущей темы (тёмная/светлая). */
private data class MenuPalette(
    val bg: Color,
    val onBg: Color,
    val onBgVariant: Color,
    val header: Color,
    val accent: Color,
    val border: Color,
    val selectedBg: Color,
    val selectedText: Color,
)

@Composable
private fun readerMenuPalette(): MenuPalette {
    val cs = MaterialTheme.colorScheme
    return MenuPalette(
        bg = cs.surfaceContainerHigh,
        onBg = cs.onSurface,
        onBgVariant = cs.onSurfaceVariant,
        header = cs.primary,
        accent = cs.primary,
        border = cs.outlineVariant,
        selectedBg = cs.primary,
        selectedText = cs.onPrimary,
    )
}

/** Меню настроек читалки (как в Mihon). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderMenu(
    vm: LibraryViewModel,
    titleId: String,
    currentMode: Int,
    page: Int,
    pages: Int,
    tick: Int,
    onPageChange: (Int) -> Unit,
    onPageCommit: () -> Unit,
    onClose: () -> Unit,
    onChanged: () -> Unit,
) {
    val readModes = listOf(
        "По умолчанию", "Постранично (Слева направо)", "Постранично (Справа налево)",
        "Постранично (Вертикально)", "Длинные страницы", "Длинные страницы с зазорами")
    val orientations = listOf(
        "По умолчанию", "Системная", "Портретная", "Альбомная",
        "Заблокированная портретная", "Заблокированная альбомная", "Портретная наоборот")
    val zones = listOf(
        "По умолчанию", "L-образная", "Kindle-подобная", "По краям", "Справа и слева", "Отключено")
    val navPos = listOf("Нет", "Горизонтально", "Вертикально", "Обе оси")
    val bgColors = listOf("Чёрный", "Серый", "Белый", "Автоматически")

    var menuTab by remember { mutableStateOf(0) }
    val c = readerMenuPalette()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        if (pages > 0) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Страница", color = c.onBgVariant, style = MaterialTheme.typography.bodySmall)
                    Text("${page + 1} / $pages", color = c.onBg, style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = page.toFloat(),
                    onValueChange = { onPageChange(it.toInt()) },
                    onValueChangeFinished = { onPageCommit() },
                    valueRange = 0f..maxOf(pages - 1, 0).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.border
                    )
                )
            }
        }
        MenuTabs(c, menuTab) { menuTab = it }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            when (menuTab) {
                0 -> {
                    MenuHeader(c, "Для этой серии")
                    MenuHeader(c, "Режим чтения")
                    readModes.forEachIndexed { i, name ->
                        MenuOption(c, name, currentMode == i, true) { vm.store.setReadMode(i); onChanged() }
                    }
                    MenuHeader(c, "Ориентация")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orientations.forEachIndexed { i, name ->
                            MenuOption(c, name, vm.store.orientation() == i, false) { vm.store.setOrientation(i); onChanged() }
                        }
                    }
                    MenuHeader(c, "Длинные страницы")
                    MenuHeader(c, "Зоны касания")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        zones.forEachIndexed { i, name ->
                            MenuOption(c, name, vm.store.tapZones() == i, false) { vm.store.setTapZones(i); onChanged() }
                        }
                    }
                    MenuHeader(c, "Зоны касания наоборот")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        navPos.forEachIndexed { i, name ->
                            MenuOption(c, name, vm.store.i("nav_pos") == i, false) { vm.store.si("nav_pos", i); onChanged() }
                        }
                    }
                    MenuSlider(c, "Боковой отступ", vm.store.i("sidebar_pad")) { vm.store.si("sidebar_pad", it); onChanged() }
                    MenuCheckbox(c, "Обрезать поля", vm.store.b("crop_borders")) { vm.store.sb("crop_borders", it); onChanged() }
                    MenuCheckbox(c, "Разделение широких страниц", vm.store.b("split_wide")) { vm.store.sb("split_wide", it); onChanged() }
                    MenuCheckbox(c, "Развернуть широкие страницы под размер экрана", vm.store.b("stretch_wide")) { vm.store.sb("stretch_wide", it); onChanged() }
                    MenuCheckbox(c, "Двойное нажатие для увеличения", vm.store.doubleTapZoom()) { vm.store.setDoubleTapZoom(it); onChanged() }
                    MenuCheckbox(c, "Отключить уменьшение масштаба", vm.store.b("no_scale_down")) { vm.store.sb("no_scale_down", it); onChanged() }
                }
                1 -> {
                    MenuHeader(c, "Цвет фона")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bgColors.forEachIndexed { i, name ->
                            MenuOption(c, name, vm.store.bgColor() == i, false) { vm.store.setBgColor(i); onChanged() }
                        }
                    }
                    MenuCheckbox(c, "Номер страницы", vm.store.pageNumber()) { vm.store.setPageNumber(it); onChanged() }
                    MenuHeader(c, "Использовать вертикальную навигацию главы в")
                    readModes.forEachIndexed { i, name ->
                        MenuOption(c, name, vm.store.i("nav_mode") == i, true) { vm.store.si("nav_mode", i); onChanged() }
                    }
                    MenuCheckbox(c, "Разместить вертикальную навигацию с левой стороны", vm.store.b("nav_left")) { vm.store.sb("nav_left", it); onChanged() }
                    MenuSlider(c, "Высота вертикальной навигации", vm.store.i("nav_height", 65)) { vm.store.si("nav_height", it); onChanged() }
                    MenuCheckbox(c, "Полноэкранный режим", vm.store.fullscreen()) { vm.store.setFullscreen(it); onChanged() }
                    MenuCheckbox(c, "Показывать содержимое в вырезанной области", vm.store.b("cutout_content", true)) { vm.store.sb("cutout_content", it); onChanged() }
                    MenuCheckbox(c, "Не выключать экран", vm.store.keepScreen()) { vm.store.setKeepScreen(it); onChanged() }
                    MenuCheckbox(c, "Показать действия при долгом нажатии", vm.store.b("long_tap_actions", true)) { vm.store.sb("long_tap_actions", it); onChanged() }
                    MenuCheckbox(c, "Переход между главами", vm.store.b("chapter_transition", true)) { vm.store.sb("chapter_transition", it); onChanged() }
                    MenuCheckbox(c, "Анимированные переходы страниц", vm.store.b("anim_transitions", true)) { vm.store.sb("anim_transitions", it); onChanged() }
                    MenuCheckbox(c, "Мигать при смене страницы", vm.store.b("flash_page")) { vm.store.sb("flash_page", it); onChanged() }
                }
                else -> {
                    MenuCheckbox(c, "Пользовательская яркость", vm.store.b("user_brightness")) { vm.store.sb("user_brightness", it); onChanged() }
                    MenuCheckbox(c, "Пользовательский светофильтр", vm.store.b("user_filter")) { vm.store.sb("user_filter", it); onChanged() }
                    MenuCheckbox(c, "Чёрно-белый фильтр", vm.store.b("grayscale")) { vm.store.sb("grayscale", it); onChanged() }
                    MenuCheckbox(c, "Цвета наоборот", vm.store.b("invert")) { vm.store.sb("invert", it); onChanged() }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().clickable { onClose() }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.width(48.dp).height(4.dp)
                    .background(c.onBgVariant.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun MenuTabs(c: MenuPalette, selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf("Режим чтения", "Основные", "Пользователь...")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            titles.forEachIndexed { i, t ->
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelect(i) }.padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        t,
                        color = if (i == selected) c.accent else c.onBg,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(
                        Modifier.fillMaxWidth(0.65f).height(3.dp)
                            .background(if (i == selected) c.accent else Color.Transparent, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun MenuHeader(c: MenuPalette, text: String) {
    Text(
        text,
        color = c.header,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun MenuOption(c: MenuPalette, title: String, selected: Boolean, full: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .then(if (full) Modifier.fillMaxWidth() else Modifier)
            .then(if (full) Modifier.padding(horizontal = 16.dp, vertical = 4.dp) else Modifier)
            .background(if (selected) c.selectedBg else Color.Transparent, shape)
            .border(1.dp, if (selected) c.selectedBg else c.border, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(
            title,
            color = if (selected) c.selectedText else c.onBg,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MenuCheckbox(c: MenuPalette, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(if (checked) c.accent else Color.Transparent, RoundedCornerShape(4.dp))
                .border(2.dp, if (checked) c.accent else c.onBgVariant, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = c.selectedText, modifier = Modifier.size(16.dp))
        }
        Text(
            title,
            color = c.onBg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun MenuSlider(c: MenuPalette, label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.onBg, style = MaterialTheme.typography.bodyMedium)
            Text("$value", color = c.onBgVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.border
            )
        )
    }
}

@Composable
private fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)) }

@Composable
private fun PageView(
    session: ChapterSession?,
    index: Int,
    dtZoom: Boolean,
    onSingleTap: (Float) -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    var bmp by remember(session, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(session, index) {
        bmp = withContext(Dispatchers.IO) { val b = session?.let { decodePage(it, index) }; b?.asImageBitmap() }
    }
    var zoom by remember(session, index) { mutableFloatStateOf(1f) }
    var offX by remember(session, index) { mutableFloatStateOf(0f) }
    var offY by remember(session, index) { mutableFloatStateOf(0f) }
    val cbTap by rememberUpdatedState(onSingleTap)
    val cbLong by rememberUpdatedState(onLongPress)
    val zoomNow by rememberUpdatedState(zoom)
    val b = bmp
    if (b == null) {
        Box(Modifier.fillMaxSize().aspectRatio(3f / 4f), contentAlignment = Alignment.Center) { Text("…", color = Color.Gray) }
    } else {
        var mod: Modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offX, translationY = offY)
        mod = mod.pointerInput(session, index, dtZoom) {
            detectTapGestures(
                onTap = { off -> if (zoomNow <= 1.01f) cbTap(off.x / size.width) },
                onLongPress = { cbLong() },
                onDoubleTap = if (dtZoom) {
                    { if (zoomNow > 1f) { zoom = 1f; offX = 0f; offY = 0f } else zoom = 2.2f }
                } else null
            )
        }
        if (zoom > 1f) {
            mod = mod.pointerInput(session, index) { detectDragGestures { change, drag -> change.consume(); offX += drag.x; offY += drag.y } }
        }
        Image(b, contentDescription = "Стр. ${index + 1}", contentScale = ContentScale.FillWidth, modifier = mod)
    }
}

package com.saymon.frame.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

/**
 * Читалка главы: лента / постранично LTR-RTL-вертикально,
 * зоны касания, двойной тап-зум, номер страницы, фон.
 */
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
        mutableIntStateOf(
            chapters.indexOfFirst { it.uri == chapterUri }.coerceAtLeast(0))
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

    // Ориентация + не гасить экран
    LaunchedEffect(Unit) {
        (context as? Activity)?.requestedOrientation = when (vm.store.orientation()) {
            Orient.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orient.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        val v = view
        val old = v.keepScreenOn
        v.keepScreenOn = vm.store.keepScreen()
        onDispose {
            v.keepScreenOn = old
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Сессия главы: кэш-копия + ZipFile
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
            val s = ChapterSession.open(context, ch.uri, titleId)
            session = s
            pages = s.entries.size
        } catch (e: Exception) {
            loadErr = e.message ?: "Не открылась глава"
        }
    }
    DisposableEffect(Unit) {
        onDispose { session?.close() }
    }

    var page by remember(chapter?.uri) {
        mutableIntStateOf(vm.progress(titleId, chapter?.uri.orEmpty()))
    }
    var menu by remember { mutableStateOf(false) }

    fun save() {
        val ch = chapter ?: return
        if (pages <= 0) return
        vm.saveProgress(Progress(titleId, vm.titleName(titleId),
            ch.uri, ch.name, page.coerceIn(0, pages - 1), pages,
            System.currentTimeMillis()))
    }

    if (chapter == null || loadErr.isNotEmpty()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("Глава") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack,
                                contentDescription = "Назад")
                        }
                    })
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

    val hPager = rememberPagerState(
        initialPage = page.coerceIn(0, maxOf(pages - 1, 0))) {
        maxOf(pages, 1)
    }
    val vPager = rememberPagerState(
        initialPage = page.coerceIn(0, maxOf(pages - 1, 0))) {
        maxOf(pages, 1)
    }
    val stripState = rememberLazyListState(
        initialFirstVisibleItemIndex = page.coerceIn(0, maxOf(pages - 1, 0)))

    // Синхронизация текущей страницы из активного вьювера
    LaunchedEffect(hPager.currentPage, vPager.currentPage) {
        val cur = when (mode) {
            ReadMode.PAGED_LTR, ReadMode.PAGED_RTL -> hPager.currentPage
            ReadMode.PAGED_VERTICAL -> vPager.currentPage
            else -> page
        }
        if (cur != page && cur in 0 until pages) {
            page = cur
            save()
        }
    }
    // Лента: следим за видимым
    LaunchedEffect(stripState.firstVisibleItemIndex) {
        if (mode == ReadMode.STRIP || mode == ReadMode.STRIP_GAPS) {
            val cur = stripState.firstVisibleItemIndex
            if (cur != page && cur in 0 until pages) {
                page = cur
                save()
            }
        }
    }

    fun goNext() {
        scope.launch {
            when (mode) {
                ReadMode.PAGED_LTR ->
                    hPager.animateScrollToPage((page + 1).coerceAtMost(pages - 1))
                ReadMode.PAGED_RTL ->
                    hPager.animateScrollToPage((page - 1).coerceAtLeast(0))
                ReadMode.PAGED_VERTICAL ->
                    vPager.animateScrollToPage((page + 1).coerceAtMost(pages - 1))
                else -> stripState.animateScrollToItem(
                    (page + 1).coerceAtMost(pages - 1))
            }
        }
    }

    fun goPrev() {
        scope.launch {
            when (mode) {
                ReadMode.PAGED_LTR ->
                    hPager.animateScrollToPage((page - 1).coerceAtLeast(0))
                ReadMode.PAGED_RTL ->
                    hPager.animateScrollToPage((page + 1).coerceAtMost(pages - 1))
                ReadMode.PAGED_VERTICAL ->
                    vPager.animateScrollToPage((page - 1).coerceAtLeast(0))
                else -> stripState.animateScrollToItem(
                    (page - 1).coerceAtLeast(0))
            }
        }
    }

    fun prevChapter() {
        save()
        if (idx > 0) {
            idx--
            page = 0
        }
    }

    fun nextChapter() {
        save()
        if (idx < chapters.size - 1) {
            idx++
            page = 0
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = bg,
        topBar = {
            if (menu) {
                TopAppBar(
                    title = {
                        Column {
                            Text(vm.titleName(titleId),
                                style = MaterialTheme.typography.titleSmall)
                            Text(chapter.name,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            save()
                            onBack()
                        }) {
                            Icon(Icons.Default.ArrowBack,
                                contentDescription = "Назад")
                        }
                    })
            }
        },
        bottomBar = {
            if (menu) {
                Column(Modifier.background(bg).padding(12.dp)) {
                    if (showNum && pages > 0) {
                        Text("${page + 1} / $pages",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                        Slider(
                            value = page.toFloat(),
                            onValueChange = { page = it.toInt() },
                            valueRange = 0f..maxOf(pages - 1, 0).toFloat(),
                            onValueChangeFinished = {
                                scope.launch {
                                    when (mode) {
                                        ReadMode.PAGED_LTR, ReadMode.PAGED_RTL ->
                                            hPager.scrollToPage(page)
                                        ReadMode.PAGED_VERTICAL ->
                                            vPager.scrollToPage(page)
                                        else -> stripState.scrollToItem(page)
                                    }
                                    save()
                                }
                            })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { prevChapter() },
                            enabled = idx > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("← Глава") }
                        OutlinedButton(
                            onClick = { nextChapter() },
                            enabled = idx < chapters.size - 1,
                            modifier = Modifier.weight(1f)
                        ) { Text("Глава →") }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(bg)) {
            if (pages == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Готовлю страницы…", color = Color.White)
                }
            } else when (mode) {
                ReadMode.PAGED_LTR -> {
                    HorizontalPager(state = hPager,
                        modifier = Modifier.fillMaxSize()) { i ->
                        PageView(session, i, dtZoom)
                    }
                }
                ReadMode.PAGED_RTL -> {
                    HorizontalPager(state = hPager, reverseLayout = true,
                        modifier = Modifier.fillMaxSize()) { i ->
                        PageView(session, i, dtZoom)
                    }
                }
                ReadMode.PAGED_VERTICAL -> {
                    VerticalPager(state = vPager,
                        modifier = Modifier.fillMaxSize()) { i ->
                        PageView(session, i, dtZoom)
                    }
                }
                ReadMode.STRIP -> {
                    LazyColumn(state = stripState,
                        modifier = Modifier.fillMaxSize()) {
                        items(pages) { i ->
                            PageView(session, i, dtZoom)
                        }
                    }
                }
                else -> {
                    LazyColumn(state = stripState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(pages) { i ->
                            PageView(session, i, dtZoom)
                        }
                    }
                }
            }
            // Зоны касания: лево/право — листать, центр — меню
            if (!zonesOff && pages > 0) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ) { goPrev() })
                    Box(Modifier.weight(1f).fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ) { menu = !menu })
                    Box(Modifier.weight(1f).fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ) { goNext() })
                }
            }
        }
    }
}

/** Одна страница: декодирование по требованию + двойной тап-зум. */
@Composable
private fun PageView(
    session: ChapterSession?,
    index: Int,
    dtZoom: Boolean,
) {
    var bmp by remember(session, index) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(session, index) {
        bmp = withContext(Dispatchers.IO) {
            val b = session?.let { decodePage(it, index) }
            b?.asImageBitmap()
        }
    }
    var zoom by remember(session, index) { mutableFloatStateOf(1f) }
    var offX by remember(session, index) { mutableFloatStateOf(0f) }
    var offY by remember(session, index) { mutableFloatStateOf(0f) }
    val b = bmp
    if (b == null) {
        Box(Modifier.fillMaxSize().aspectRatio(3f / 4f),
            contentAlignment = Alignment.Center) {
            Text("…", color = Color.Gray)
        }
    } else {
        var mod: Modifier = Modifier.fillMaxSize()
            .graphicsLayer(
                scaleX = zoom, scaleY = zoom,
                translationX = offX, translationY = offY)
        if (dtZoom) {
            mod = mod.pointerInput(session, index) {
                detectTapGestures(onDoubleTap = {
                    if (zoom > 1f) {
                        zoom = 1f
                        offX = 0f
                        offY = 0f
                    } else {
                        zoom = 2.2f
                    }
                })
            }
        }
        if (zoom > 1f) {
            mod = mod.pointerInput(session, index) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offX += drag.x
                    offY += drag.y
                }
            }
        }
        Image(b, contentDescription = "Стр. ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = mod)
    }
}

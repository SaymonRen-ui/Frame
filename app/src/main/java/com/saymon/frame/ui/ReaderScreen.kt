package com.saymon.frame.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.saymon.frame.core.cbz.ChapterInfo
import com.saymon.frame.core.cbz.ChapterSession
import com.saymon.frame.core.cbz.decodePage
import com.saymon.frame.core.cbz.pageBounds
import com.saymon.frame.data.Orient
import com.saymon.frame.data.Progress
import com.saymon.frame.data.ReadMode
import com.saymon.frame.data.TapZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Снимок настроек читалки: читается один раз на изменение, а не в каждом кадре. */
private data class ReaderSettings(
    val mode: Int = ReadMode.DEFAULT,
    val tapZones: Int = 0,
    val invert: Int = 0,
    val dtZoom: Boolean = true,
    val showNum: Boolean = true,
    val bg: Color = Color.Black,
    val orient: Int = 0,
    val fullscreen: Boolean = false,
    val cutout: Boolean = true,
    val keep: Boolean = true,
    val chapterTransition: Boolean = true,
    val anim: Boolean = true,
    val flash: Boolean = false,
    val navModes: Set<Int> = emptySet(),
    val navLeft: Boolean = false,
    val navHeight: Int = 65,
    val brightness: Boolean = false,
    val brightnessVal: Int = 0,
    val filter: Boolean = false,
    val filterColor: Color = Color(0x33FFB74D),
    val gray: Boolean = false,
    val invertColors: Boolean = false,
    val sidebar: Int = 0,
    val crop: Boolean = false,
    val split: Boolean = false,
    val stretch: Boolean = false,
    val noScale: Boolean = false,
    val numSize: Int = 13,
    val numAlpha: Int = 55,
)

private enum class ZAction { MENU, PREV, NEXT, LEFT, RIGHT }

private data class Zone(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val action: ZAction,
)

private data class Part(val index: Int, val half: Int)

private class StripItem(
    val chapIdx: Int,
    val pageIdx: Int,
    val session: ChapterSession,
) {
    val isBoundary: Boolean get() = pageIdx < 0
}

private class Holder(var value: Offset = Offset.Zero)

private fun buildStripItems(
    lo: Int,
    hi: Int,
    sessions: Map<Int, ChapterSession>,
): List<StripItem> {
    val out = ArrayList<StripItem>()
    for (ci in lo..hi) {
        val ss = sessions[ci] ?: continue
        if (ci > lo || lo > 0) out += StripItem(ci, -1, ss)
        for (p in ss.entries.indices) out += StripItem(ci, p, ss)
    }
    return out
}

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
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val chapters = remember { vm.chapters(titleId, vm.titleName(titleId)) }
    var idx by remember(chapterUri) {
        mutableIntStateOf(chapters.indexOfFirst { it.uri == chapterUri }.coerceAtLeast(0))
    }
    val chapter = chapters.getOrNull(idx)
    LaunchedEffect(chapter?.uri) {
        chapter?.let { vm.setReaderChapter(titleId, it.uri) }
    }
    var settingsTick by remember { mutableIntStateOf(0) }
    val appDark = when (vm.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val s = remember(settingsTick, appDark) {
        ReaderSettings(
            mode = vm.store.readMode(),
            tapZones = vm.store.tapZones(),
            invert = vm.store.i("nav_pos"),
            dtZoom = vm.store.doubleTapZoom(),
            showNum = vm.store.pageNumber(),
            bg = when (vm.store.bgColor()) {
                1 -> Color(0xFF2A2A2E)
                2 -> Color.White
                3 -> if (appDark) Color.Black else Color.White
                else -> Color.Black
            },
            orient = vm.store.orientation(),
            fullscreen = vm.store.fullscreen(),
            cutout = vm.store.b("cutout_content", true),
            keep = vm.store.keepScreen(),
            chapterTransition = vm.store.b("chapter_transition", true),
            anim = vm.store.b("anim_transitions", true),
            flash = vm.store.b("flash_page"),
            navModes = vm.store.strSet("nav_modes").mapNotNull { it.toIntOrNull() }.toSet(),
            navLeft = vm.store.b("nav_left"),
            navHeight = vm.store.i("nav_height", 65),
            brightness = vm.store.b("user_brightness"),
            brightnessVal = vm.store.i("user_brightness_value", 0),
            filter = vm.store.b("user_filter"),
            filterColor = Color(vm.store.i("user_filter_color", 0x33FFB74D)),
            gray = vm.store.b("grayscale"),
            invertColors = vm.store.b("invert"),
            sidebar = vm.store.i("sidebar_pad"),
            crop = vm.store.b("crop_borders"),
            split = vm.store.b("split_wide"),
            stretch = vm.store.b("stretch_wide"),
            noScale = vm.store.b("no_scale_down"),
            numSize = vm.store.i("page_num_size", 13),
            numAlpha = vm.store.i("page_num_alpha", 55),
        )
    }
    val mode = s.mode
    val stripSeamless = (mode == ReadMode.STRIP || mode == ReadMode.STRIP_GAPS) &&
        s.chapterTransition && !vm.autoDeleteRead()
    val stripSessions = remember(chapters) { mutableStateMapOf<Int, ChapterSession>() }
    var stripVersion by remember { mutableIntStateOf(0) }
    var stripReady by remember { mutableStateOf(false) }
    var winLo by remember(chapters) { mutableIntStateOf(idx) }
    var winHi by remember(chapters) { mutableIntStateOf(idx) }
    var stripTarget by remember { mutableStateOf<Int?>(null) }
    var stripAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var stripPrepending by remember { mutableStateOf(false) }
    var stripAppendWanted by remember { mutableStateOf(false) }
    var stripPrependReq by remember { mutableIntStateOf(0) }

    LaunchedEffect(stripSeamless, chapters) {
        if (!stripSeamless) {
            if (stripSessions.isNotEmpty() || stripReady || stripTarget != null) {
                stripSessions.values.forEach { runCatching { it.close() } }
                stripSessions.clear()
                stripVersion++
                stripReady = false
                stripTarget = null
            }
            return@LaunchedEffect
        }
        val lo = (idx - 1).coerceAtLeast(0)
        val hi = (idx + 1).coerceAtMost(chapters.lastIndex)
        val opened = withContext(Dispatchers.IO) {
            (lo..hi).mapNotNull { ci ->
                val ss = stripSessions[ci] ?: runCatching {
                    ChapterSession.openFile(vm.materializeChapter(chapters[ci].uri))
                }.getOrNull()
                ss?.let { ci to it }
            }
        }
        opened.forEach { (ci, ss) -> if (stripSessions[ci] == null) stripSessions[ci] = ss }
        stripSessions.keys.filter { it !in lo..hi }.forEach { ci ->
            runCatching { stripSessions.remove(ci)?.close() }
        }
        stripVersion++
        winLo = lo
        winHi = hi
        val active = stripSessions[idx]
        stripReady = active != null
        if (active != null) {
            val p = vm.progress(titleId, chapters[idx].uri)
                .coerceIn(0, active.entries.size - 1)
            val built = buildStripItems(lo, hi, stripSessions)
            stripTarget = built.indexOfFirst { it.chapIdx == idx && it.pageIdx == p }
                .takeIf { it >= 0 } ?: 0
        }
    }

    LaunchedEffect(s.orient) {
        (context as? Activity)?.requestedOrientation = when (s.orient) {
            Orient.FREE -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            Orient.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            Orient.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Orient.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orient.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Orient.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(s.fullscreen, s.cutout) {
        val act = context as? Activity ?: return@LaunchedEffect
        val win = act.window
        if (s.fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val ctl = WindowCompat.getInsetsController(win, view)
            ctl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(win, true)
            WindowCompat.getInsetsController(win, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            win.attributes = win.attributes.apply {
                layoutInDisplayCutoutMode = if (s.cutout) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }
        }
    }
    LaunchedEffect(s.keep) { view.keepScreenOn = s.keep }
    LaunchedEffect(s.brightness, s.brightnessVal) {
        val win = (context as? Activity)?.window ?: return@LaunchedEffect
        win.attributes = win.attributes.apply {
            screenBrightness = if (s.brightness && s.brightnessVal > 0) {
                s.brightnessVal / 100f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    DisposableEffect(Unit) {
        val v = view
        val win = (context as? Activity)?.window
        onDispose {
            v.keepScreenOn = false
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (win != null) {
                WindowCompat.setDecorFitsSystemWindows(win, true)
                WindowCompat.getInsetsController(win, v)
                    .show(WindowInsetsCompat.Type.systemBars())
                win.attributes = win.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }

    var session by remember { mutableStateOf<ChapterSession?>(null) }
    var pages by remember { mutableIntStateOf(0) }
    var loadErr by remember { mutableStateOf("") }
    LaunchedEffect(chapter?.uri, stripSeamless, stripReady, stripVersion) {
        if (stripSeamless) {
            val ss = stripSessions[idx]
            if (ss != null) {
                session = ss
                pages = ss.entries.size
                loadErr = ""
            }
            return@LaunchedEffect
        }
        session?.close()
        session = null
        pages = 0
        loadErr = ""
        val ch = chapter ?: return@LaunchedEffect
        try {
            val opened = withContext(Dispatchers.IO) {
                ChapterSession.openFile(vm.materializeChapter(ch.uri))
            }
            session = opened
            pages = opened.entries.size
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            loadErr = e.message ?: "Не открылась глава"
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            session?.close()
            stripSessions.values.forEach { runCatching { it.close() } }
        }
    }

    var page by remember(if (stripSeamless) titleId else chapter?.uri) {
        mutableIntStateOf(vm.progress(titleId, chapter?.uri.orEmpty()))
    }
    var menuOpen by remember { mutableStateOf(false) }
    var autoDeleted by remember(chapter?.uri) { mutableStateOf(false) }

    fun save(atEnd: Boolean = false) {
        if (autoDeleted) return
        val ch = chapter ?: return
        if (pages <= 0) return
        val last = page.coerceIn(0, pages - 1)
        vm.saveProgress(
            Progress(
                titleId, vm.titleName(titleId),
                ch.uri, ch.name, last, pages, System.currentTimeMillis()
            )
        )
        if (atEnd && pages > 0) {
            vm.setChapterRead(titleId, ch.uri, true)
            if (vm.autoDeleteRead() && !autoDeleted) {
                autoDeleted = true
                vm.deleteChapter(titleId, ch.uri)
            }
        }
    }

    if (chapter == null || loadErr.isNotEmpty()) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("Глава") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    })
            }
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (chapter == null) "Глава не найдена" else loadErr)
            }
        }
        return
    }

    val splitOn = s.split && (mode == ReadMode.DEFAULT || mode == ReadMode.PAGED_LTR ||
        mode == ReadMode.PAGED_RTL || mode == ReadMode.PAGED_VERTICAL)
    var parts by remember { mutableStateOf<List<Part>?>(null) }
    LaunchedEffect(session, splitOn, pages) {
        val sn = session
        parts = if (splitOn && sn != null && pages > 0) {
            withContext(Dispatchers.IO) { buildParts(sn) }
        } else {
            null
        }
    }
    val partList = parts
    val stripItems = remember(stripSeamless, stripVersion, winLo, winHi, chapters) {
        if (!stripSeamless) emptyList()
        else buildStripItems(winLo, winHi, stripSessions)
    }
    val count = if (stripSeamless) stripItems.size else partList?.size ?: pages
    val startPos = remember(partList, pages, page, chapter?.uri) {
        val byPage = partList?.indexOfFirst { it.index == page }
        (byPage ?: page).coerceIn(0, max(count - 1, 0))
    }

    val hPager = key(chapter?.uri, partList) {
        rememberPagerState(initialPage = startPos) { max(count, 1) }
    }
    val vPager = key(chapter?.uri, partList) {
        rememberPagerState(initialPage = startPos) { max(count, 1) }
    }
    val stripState = if (stripSeamless) {
        key(titleId) { rememberLazyListState() }
    } else {
        key(chapter?.uri, partList) {
            rememberLazyListState(initialFirstVisibleItemIndex = startPos)
        }
    }
    val stripPosition by remember(stripState) {
        derivedStateOf { stripState.firstVisibleItemIndex }
    }

    fun currentPos(): Int = when (mode) {
        ReadMode.DEFAULT, ReadMode.PAGED_LTR, ReadMode.PAGED_RTL -> hPager.currentPage
        ReadMode.PAGED_VERTICAL -> vPager.currentPage
        else -> stripState.firstVisibleItemIndex
    }

    fun sourcePage(pos: Int): Int =
        partList?.getOrNull(pos)?.index ?: pos

    fun posOfPage(src: Int): Int =
        partList?.indexOfFirst { it.index == src } ?: src

    fun scrollTo(pos: Int, animate: Boolean) {
        val p = pos.coerceIn(0, max(count - 1, 0))
        scope.launch {
            when (mode) {
                ReadMode.DEFAULT, ReadMode.PAGED_LTR, ReadMode.PAGED_RTL ->
                    if (animate) hPager.animateScrollToPage(p) else hPager.scrollToPage(p)
                ReadMode.PAGED_VERTICAL ->
                    if (animate) vPager.animateScrollToPage(p) else vPager.scrollToPage(p)
                else ->
                    if (animate) stripState.animateScrollToItem(p) else stripState.scrollToItem(p)
            }
        }
    }

    var flashKey by remember { mutableIntStateOf(0) }
    val flashAlpha = remember { Animatable(0f) }
    fun flashNow() {
        if (s.flash) flashKey++
    }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(0f, tween(140))
        }
    }

    var lastPos by remember { mutableIntStateOf(-1) }
    var suppressInitialReadMarkFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mode, stripSeamless, hPager, vPager, stripState) {
        if (stripSeamless) return@LaunchedEffect
        snapshotFlow { currentPos() }.distinctUntilChanged().collect { cur ->
            if (lastPos >= 0 && cur != lastPos) flashNow()
            lastPos = cur
            val src = sourcePage(cur)
            if (src != page && src in 0 until pages) {
                page = src
            }
        }
    }

    // Постраничные режимы: прогресс пишем после остановки листания,
    // а не на каждый промежуточный кадр/страницу.
    LaunchedEffect(chapter?.uri, session, page, pages, stripSeamless) {
        if (stripSeamless || pages <= 0) return@LaunchedEffect
        if (chapter?.uri == suppressInitialReadMarkFor) {
            if (session != null) suppressInitialReadMarkFor = null
            return@LaunchedEffect
        }
        delay(350)
        save(atEnd = page >= pages - 1)
    }

    // Пейджеры создаются раньше, чем глава дочитана: ставим их на сохранённую страницу,
    // когда число страниц (и разбивка разворотов) уже известно.
    var pendingEnd by remember { mutableStateOf(false) }
    LaunchedEffect(mode, pages, partList, chapter?.uri, stripSeamless) {
        if (pages <= 0) return@LaunchedEffect
        if (stripSeamless) return@LaunchedEffect
        val toEnd = pendingEnd
        pendingEnd = false
        val target = if (toEnd) count - 1 else posOfPage(page)
        val safeTarget = target.coerceIn(0, max(count - 1, 0))
        page = sourcePage(safeTarget)
        when (mode) {
            ReadMode.DEFAULT, ReadMode.PAGED_LTR, ReadMode.PAGED_RTL ->
                hPager.scrollToPage(safeTarget)
            ReadMode.PAGED_VERTICAL -> vPager.scrollToPage(safeTarget)
            else -> stripState.scrollToItem(safeTarget)
        }
        if (toEnd) save(atEnd = false)
    }

    fun saveChapterAt(ci: Int, p: Int, atEnd: Boolean = false) {
        val ch = chapters.getOrNull(ci) ?: return
        val pg = stripSessions[ci]?.entries?.size ?: return
        if (pg <= 0) return
        val lp = p.coerceIn(0, pg - 1)
        vm.saveProgress(
            Progress(titleId, vm.titleName(titleId), ch.uri, ch.name, lp, pg, System.currentTimeMillis())
        )
        if (atEnd) vm.setChapterRead(titleId, ch.uri, true)
    }

    // Бесшовная лента: ставим список на сохранённую страницу активной главы.
    LaunchedEffect(stripSeamless, stripTarget, stripItems.size) {
        if (!stripSeamless) return@LaunchedEffect
        val t = stripTarget ?: return@LaunchedEffect
        if (stripItems.isEmpty()) return@LaunchedEffect
        stripState.scrollToItem(t.coerceIn(0, stripItems.lastIndex))
        stripTarget = null
    }

    // Границы глав: обновляем активную главу/страницу при перелистывании через стык.
    LaunchedEffect(
        stripSeamless, stripReady, stripItems.size,
        stripPrepending, stripAnchor, stripTarget,
    ) {
        if (!stripSeamless || !stripReady || stripPrepending || stripAnchor != null ||
            stripTarget != null
        ) return@LaunchedEffect
        snapshotFlow { stripState.firstVisibleItemIndex }.distinctUntilChanged().collect { first ->
            val item = stripItems.getOrNull(first) ?: return@collect
            val targetPage = if (item.isBoundary) 0 else item.pageIdx
            if (item.chapIdx != idx) {
                val oldIdx = idx
                val oldCh = chapters.getOrNull(oldIdx) ?: return@collect
                val oldPages = stripSessions[oldIdx]?.entries?.size ?: 0
                val oldPage = page.coerceIn(0, (oldPages - 1).coerceAtLeast(0))
                if (oldPages > 0) saveChapterAt(oldIdx, oldPage)
                if (item.chapIdx > oldIdx && oldPages > 0 && oldPage >= oldPages - 1) {
                    vm.setChapterRead(titleId, oldCh.uri, true)
                }
                idx = item.chapIdx
                page = targetPage
                saveChapterAt(idx, page)
            } else if (!item.isBoundary && item.pageIdx != page) {
                // Сохраняем не на каждом кадре прокрутки, а после остановки.
                page = item.pageIdx
            }
        }
    }

    // Прогресс в бесшовной ленте пишем с задержкой: быстрый скролл
    // не должен дёргать SharedPreferences и JSON истории на каждый кадр.
    LaunchedEffect(stripSeamless, idx, page) {
        if (!stripSeamless || !stripReady) return@LaunchedEffect
        delay(450)
        saveChapterAt(idx, page)
    }

    // Догружаем соседние главы при подходе к краю списка.
    LaunchedEffect(
        stripSeamless, stripReady, stripItems.size,
        winLo, winHi, stripPrepending, stripAnchor, stripTarget,
    ) {
        if (!stripSeamless || !stripReady || stripPrepending || stripAnchor != null ||
            stripTarget != null
        ) return@LaunchedEffect
        if (stripItems.isEmpty()) return@LaunchedEffect
        snapshotFlow { stripState.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            if (i <= 1 && winLo > 0) stripPrependReq++
            if (i >= stripItems.size - 3 && winHi < chapters.lastIndex) stripAppendWanted = true
        }
    }

    LaunchedEffect(stripSeamless, stripAppendWanted, winHi) {
        if (!stripSeamless || !stripAppendWanted) return@LaunchedEffect
        stripAppendWanted = false
        val ni = winHi + 1
        if (ni > chapters.lastIndex) return@LaunchedEffect
        val ss = stripSessions[ni] ?: withContext(Dispatchers.IO) {
            runCatching { ChapterSession.openFile(vm.materializeChapter(chapters[ni].uri)) }.getOrNull()
        }
        if (ss != null) {
            if (stripSessions[ni] == null) stripSessions[ni] = ss
            winHi = ni
            stripSessions.keys.filter { it !in winLo..winHi }.forEach { ci ->
                runCatching { stripSessions.remove(ci)?.close() }
            }
            stripVersion++
        }
    }

    LaunchedEffect(stripSeamless, stripPrependReq) {
        if (!stripSeamless || stripPrependReq == 0 || winLo <= 0) return@LaunchedEffect
        stripPrepending = true
        val first = stripState.firstVisibleItemIndex
        val off = stripState.firstVisibleItemScrollOffset
        val prev = winLo - 1
        val ss = stripSessions[prev] ?: withContext(Dispatchers.IO) {
            runCatching { ChapterSession.openFile(vm.materializeChapter(chapters[prev].uri)) }.getOrNull()
        }
        if (ss != null) {
            if (stripSessions[prev] == null) stripSessions[prev] = ss
            val added = ss.entries.size + if (prev > 0) 1 else 0
            winLo = prev
            stripSessions.keys.filter { it !in winLo..winHi }.forEach { ci ->
                runCatching { stripSessions.remove(ci)?.close() }
            }
            stripVersion++
            stripAnchor = (first + added) to off
        } else {
            stripPrepending = false
        }
    }

    LaunchedEffect(stripAnchor, stripItems.size) {
        val a = stripAnchor ?: return@LaunchedEffect
        if (stripItems.isEmpty()) return@LaunchedEffect
        stripState.scrollToItem(a.first.coerceIn(0, stripItems.lastIndex), a.second)
        stripAnchor = null
        stripPrepending = false
    }

    fun goNextChapter() {
        if (!s.chapterTransition) return
        if (idx >= chapters.size - 1) return
        save(atEnd = true)
        idx++
        page = 0
    }

    fun goNext() {
        val pos = currentPos()
        if (pos < count - 1) {
            flashNow()
            scrollTo(pos + 1, s.anim)
        } else if (stripSeamless) {
            stripAppendWanted = true
        } else {
            goNextChapter()
        }
    }

    fun goPrev() {
        val pos = currentPos()
        if (pos > 0) {
            flashNow()
            scrollTo(pos - 1, s.anim)
        } else if (stripSeamless) {
            if (winLo > 0) stripPrependReq++
        } else if (s.chapterTransition && idx > 0) {
            save()
            suppressInitialReadMarkFor = chapters.getOrNull(idx - 1)?.uri
            pendingEnd = true
            idx--
        }
    }

    // В ленте конец списка не даёт скроллить дальше: ловим «перелистывание» за край
    // и переходим к следующей главе, как в Mihon.
    val stripTop = mode == ReadMode.STRIP || mode == ReadMode.STRIP_GAPS
    val advanceAcc = remember { mutableFloatStateOf(0f) }
    val advanceThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val nextChapterCb = rememberUpdatedState({ goNextChapter() })
    val stripScroll = remember(stripState, advanceThreshold, stripSeamless) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (stripSeamless) return Offset.Zero
                if (source == NestedScrollSource.UserInput && !stripState.canScrollForward) {
                    if (available.y < 0f) {
                        advanceAcc.value += -available.y
                        if (advanceAcc.value >= advanceThreshold) {
                            advanceAcc.value = 0f
                            nextChapterCb.value()
                        }
                    } else {
                        advanceAcc.value = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(chapter?.uri) { advanceAcc.value = 0f }

    // Короткий тап — зоны навигации из Mihon, по центру — ничего; меню только по долгому нажатию.
    fun openMenu() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        menuOpen = true
    }
    val zones = remember(mode, s.tapZones, s.invert) {
        zoneRects(mode, s.tapZones).map { it.inverted(s.invert) }
    }
    val onPageTap: (Float, Float) -> Unit = { x, y ->
        val rtl = mode == ReadMode.PAGED_RTL
        val action = zones.firstOrNull { x in it.l..it.r && y in it.t..it.b }?.action
            ?: ZAction.MENU
        when (action) {
            ZAction.MENU -> Unit
            ZAction.NEXT -> goNext()
            ZAction.PREV -> goPrev()
            ZAction.LEFT -> if (rtl) goNext() else goPrev()
            ZAction.RIGHT -> if (rtl) goPrev() else goNext()
        }
    }
    val onPageLong: () -> Unit = { openMenu() }

    BackHandler(enabled = menuOpen) { menuOpen = false }
    BackHandler(enabled = !menuOpen) { save(); onBack() }

    val insets = when {
        s.fullscreen -> WindowInsets(0, 0, 0, 0)
        s.cutout -> WindowInsets.safeDrawing.exclude(WindowInsets.displayCutout)
        else -> WindowInsets.safeDrawing
    }

    Scaffold(
        contentWindowInsets = insets,
        containerColor = s.bg,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(s.bg)) {
            if (pages == 0 || (stripSeamless && (!stripReady || stripItems.isEmpty()))) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Готовлю страницы…", color = Color.White)
                }
            } else {
                val innerPad = Modifier.padding(horizontal = (s.sidebar / 2).dp)
                when (mode) {
                    ReadMode.DEFAULT, ReadMode.PAGED_LTR, ReadMode.PAGED_RTL -> {
                        HorizontalPager(
                            state = hPager,
                            reverseLayout = mode == ReadMode.PAGED_RTL,
                            modifier = Modifier.fillMaxSize().then(innerPad)
                        ) { i ->
                            val part = partList?.getOrNull(i)
                            PageView(
                                session, part?.index ?: i, part?.half ?: -1, s,
                                onPageTap, onPageLong
                            )
                        }
                    }
                    ReadMode.PAGED_VERTICAL -> {
                        VerticalPager(state = vPager, modifier = Modifier.fillMaxSize().then(innerPad)) { i ->
                            val part = partList?.getOrNull(i)
                            PageView(
                                session, part?.index ?: i, part?.half ?: -1, s,
                                onPageTap, onPageLong
                            )
                        }
                    }
                    ReadMode.STRIP -> {
                        if (stripSeamless) {
                            LazyColumn(
                                state = stripState,
                                modifier = Modifier.fillMaxSize().then(innerPad)
                            ) {
                                items(count, key = { i -> "${stripItems[i].chapIdx}-${stripItems[i].pageIdx}" }, contentType = { i -> if (stripItems[i].isBoundary) "boundary" else "page" }) { i ->
                                    val it = stripItems[i]
                                    if (it.isBoundary) {
                                        ChapterTransitionPage(
                                            prev = chapters.getOrNull(it.chapIdx - 1),
                                            current = chapters.getOrNull(it.chapIdx),
                                            bg = s.bg,
                                            onTap = onPageTap,
                                            onLong = onPageLong,
                                            modifier = Modifier.fillParentMaxHeight(1f),
                                        )
                                    } else {
                                        PageView(
                                            it.session, it.pageIdx, -1, s,
                                            onPageTap, onPageLong
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = stripState,
                                modifier = Modifier.fillMaxSize().then(innerPad).nestedScroll(stripScroll)
                            ) {
                                items(count) { i ->
                                    val part = partList?.getOrNull(i)
                                    PageView(
                                        session, part?.index ?: i, part?.half ?: -1, s,
                                        onPageTap, onPageLong
                                    )
                                }
                                if (s.chapterTransition && idx < chapters.size - 1) {
                                    item(key = "next-chapter") {
                                        ChapterTransitionFooter(
                                            prev = chapters.getOrNull(idx - 1),
                                            current = chapter,
                                            next = chapters.getOrNull(idx + 1),
                                            modifier = Modifier.fillParentMaxHeight(0.9f),
                                            onNext = { goNextChapter() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (stripSeamless) {
                            LazyColumn(
                                state = stripState,
                                modifier = Modifier.fillMaxSize().then(innerPad),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(count, key = { i -> "${stripItems[i].chapIdx}-${stripItems[i].pageIdx}" }, contentType = { i -> if (stripItems[i].isBoundary) "boundary" else "page" }) { i ->
                                    val it = stripItems[i]
                                    if (it.isBoundary) {
                                        ChapterTransitionPage(
                                            prev = chapters.getOrNull(it.chapIdx - 1),
                                            current = chapters.getOrNull(it.chapIdx),
                                            bg = s.bg,
                                            onTap = onPageTap,
                                            onLong = onPageLong,
                                            modifier = Modifier.fillParentMaxHeight(1f),
                                        )
                                    } else {
                                        PageView(
                                            it.session, it.pageIdx, -1, s,
                                            onPageTap, onPageLong
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = stripState,
                                modifier = Modifier.fillMaxSize().then(innerPad).nestedScroll(stripScroll),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(count) { i ->
                                    val part = partList?.getOrNull(i)
                                    PageView(
                                        session, part?.index ?: i, part?.half ?: -1, s,
                                        onPageTap, onPageLong
                                    )
                                }
                                if (s.chapterTransition && idx < chapters.size - 1) {
                                    item(key = "next-chapter") {
                                        ChapterTransitionFooter(
                                            prev = chapters.getOrNull(idx - 1),
                                            current = chapter,
                                            next = chapters.getOrNull(idx + 1),
                                            modifier = Modifier.fillParentMaxHeight(0.9f),
                                            onNext = { goNextChapter() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (s.brightness && s.brightnessVal < 0) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = (abs(s.brightnessVal) / 100f).coerceIn(0f, 1f)))
                )
            }
            if (s.filter && s.filterColor.alpha > 0f) {
                Canvas(Modifier.fillMaxSize()) { drawRect(s.filterColor) }
            }
            if (!menuOpen && s.showNum && pages > 0) {
                Box(Modifier.fillMaxSize().padding(bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.background(
                            Color.Black.copy(alpha = (s.numAlpha / 100f).coerceIn(0f, 1f)),
                            RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${page + 1} / $pages",
                            color = Color.White,
                            fontSize = s.numSize.sp,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            if (count > 1 && mode in s.navModes) {
                VerticalChapterNav(
                    page = if (stripSeamless) stripPosition
                    else posOfPage(page).coerceIn(0, max(count - 1, 0)),
                    count = count,
                    left = s.navLeft,
                    heightPct = s.navHeight,
                    onSeek = { p ->
                        if (stripSeamless) {
                            scrollTo(p, false)
                        } else {
                            val src = sourcePage(p)
                            if (src != page) {
                                page = src
                                scrollTo(p, false)
                            }
                        }
                    },
                    onCommit = { save() },
                )
            }
            if (flashAlpha.value > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flashAlpha.value)))
            }

            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                            .clickable { menuOpen = false }
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.96f)
                    ) {
                        ReaderMenu(
                            vm, titleId, s, page, pages,
                            title = vm.titleName(titleId),
                            chapterName = chapter.name,
                            onBack = { save(); onBack() },
                            onPageChange = { page = it.coerceIn(0, max(pages - 1, 0)) },
                            onPageCommit = {
                                if (stripSeamless) {
                                    val p = stripItems.indexOfFirst {
                                        it.chapIdx == idx && it.pageIdx == page
                                    }
                                    if (p >= 0) scrollTo(p, false)
                                } else {
                                    scrollTo(posOfPage(page), false)
                                }
                                save(atEnd = page >= pages - 1)
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

/** Экран-переход между главами внутри бесшовной ленты. */
@Composable
private fun ChapterTransitionPage(
    prev: ChapterInfo?,
    current: ChapterInfo?,
    bg: Color,
    onTap: (Float, Float) -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (bg.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFEDEDED)
    val dim = fg.copy(alpha = 0.60f)
    val cbTap by rememberUpdatedState(onTap)
    val cbLong by rememberUpdatedState(onLong)
    Column(
        modifier
            .fillMaxWidth()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { off ->
                        cbTap(
                            (off.x / size.width).coerceIn(0f, 1f),
                            (off.y / size.height).coerceIn(0f, 1f),
                        )
                    },
                    onLongPress = { cbLong() },
                )
            }
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (prev != null) {
            Text("Предыдущая:", color = dim, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                prev.name,
                color = fg,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(36.dp))
        }
        Text("Текущая:", color = dim, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            current?.name.orEmpty(),
            color = fg,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Вставка между главами в режиме длинных страниц (как переход в Mihon). */
@Composable
private fun ChapterTransitionFooter(
    prev: ChapterInfo?,
    current: ChapterInfo,
    next: ChapterInfo?,
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
) {
    val bg = Color(0xFF0E0E12)
    val fg = Color(0xFFEDEDED)
    val dim = Color(0xFF9A9AA5)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Переход к следующей главе", color = dim, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(24.dp))
        if (prev != null) {
            Text("Предыдущая:", color = dim, style = MaterialTheme.typography.labelMedium)
            Text(prev.name, color = fg, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
        }
        Text("Текущая:", color = dim, style = MaterialTheme.typography.labelMedium)
        Text(current.name, color = fg, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (next != null) {
            Text("Следующая:", color = dim, style = MaterialTheme.typography.labelMedium)
            Text(next.name, color = accent, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(accent, RoundedCornerShape(12.dp))
                    .clickable { onNext() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Следующая глава",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Потяните вверх, чтобы продолжить",
                color = dim,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun zoneRects(mode: Int, zones: Int): List<Zone> {
    val l = listOf(
        Zone(0f, 0.33f, 0.33f, 0.66f, ZAction.PREV),
        Zone(0f, 0f, 1f, 0.33f, ZAction.PREV),
        Zone(0.66f, 0.33f, 1f, 0.66f, ZAction.NEXT),
        Zone(0f, 0.66f, 1f, 1f, ZAction.NEXT),
    )
    val kindle = listOf(
        Zone(0.33f, 0.33f, 1f, 1f, ZAction.NEXT),
        Zone(0f, 0.33f, 0.33f, 1f, ZAction.PREV),
    )
    val edge = listOf(
        Zone(0f, 0f, 0.33f, 1f, ZAction.NEXT),
        Zone(0.33f, 0.66f, 0.66f, 1f, ZAction.PREV),
        Zone(0.66f, 0f, 1f, 1f, ZAction.NEXT),
    )
    val rightLeft = listOf(
        Zone(0f, 0f, 0.33f, 1f, ZAction.LEFT),
        Zone(0.66f, 0f, 1f, 1f, ZAction.RIGHT),
    )
    return when (zones) {
        1 -> l
        2 -> kindle
        3 -> edge
        4 -> rightLeft
        5 -> emptyList()
        else -> if (mode == ReadMode.PAGED_VERTICAL ||
            mode == ReadMode.STRIP || mode == ReadMode.STRIP_GAPS
        ) l else rightLeft
    }
}

private fun Zone.inverted(mode: Int): Zone {
    val h = mode == 1 || mode == 3
    val v = mode == 2 || mode == 3
    var nl = l
    var nt = t
    var nr = r
    var nb = b
    if (h) {
        nl = 1f - r
        nr = 1f - l
    }
    if (v) {
        nt = 1f - b
        nb = 1f - t
    }
    return copy(l = nl, t = nt, r = nr, b = nb)
}

private fun buildParts(session: ChapterSession): List<Part>? {
    val out = ArrayList<Part>(session.entries.size)
    var anyWide = false
    for (i in session.entries.indices) {
        val b = pageBounds(session, i)
        val wide = b != null && b[0] > b[1] * 1.2f
        if (wide) {
            anyWide = true
            out += Part(i, 0)
            out += Part(i, 1)
        } else {
            out += Part(i, -1)
        }
    }
    return if (anyWide) out else null
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
    s: ReaderSettings,
    page: Int,
    pages: Int,
    title: String,
    chapterName: String,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onPageCommit: () -> Unit,
    onClose: () -> Unit,
    onChanged: () -> Unit,
) {
    val readModes = ReadMode.labels
    val orientations = Orient.labels
    val zones = TapZone.labels
    val navPos = listOf("Нет", "Горизонтально", "Вертикально", "Обе оси")
    val bgColors = listOf("Чёрный", "Серый", "Белый", "Автоматически")
    val navModeSet = s.navModes.toMutableSet()

    var menuTab by remember { mutableStateOf(0) }
    val c = readerMenuPalette()

    var dragOffset by remember { mutableStateOf(0f) }
    val animOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val closeThreshold = screenHeight * 0.7f

    // Сброс при открытии меню
    LaunchedEffect(Unit) {
        dragOffset = 0f
        animOffset.snapTo(0f)
    }

    LaunchedEffect(dragOffset) {
        animOffset.snapTo(dragOffset)
    }

    Column(
        Modifier
            .fillMaxSize()
            .offset(y = animOffset.value.dp)
            .background(c.bg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {},
                    onDragEnd = {
                        scope.launch {
                            if (animOffset.value > closeThreshold) {
                                onClose()
                            }
                            // Иначе остаётся на месте (не возвращается)
                        }
                    },
                    onDrag = { _, dragAmount ->
                        dragOffset += dragAmount.y / density.density
                        if (dragOffset < 0f) dragOffset = 0f
                    }
                )
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Закрыть меню", tint = c.onBg)
            }
            Column {
                Text(title, color = c.onBg, style = MaterialTheme.typography.titleSmall)
                Text(chapterName, color = c.onBgVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Handle bar для перетаскивания (drag-to-close)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.width(48.dp).height(4.dp)
                    .background(c.onBgVariant.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
        }
        if (pages > 1) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${page + 1} / $pages",
                    color = c.onBgVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = page.toFloat(),
                    onValueChange = { onPageChange(it.toInt()) },
                    onValueChangeFinished = { onPageCommit() },
                    valueRange = 0f..maxOf(pages - 1, 0).toFloat(),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
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
                    MenuHeader(c, "Режим чтения")
                    readModes.forEachIndexed { i, name ->
                        MenuOption(c, name, s.mode == i, true) { vm.store.setReadMode(i); onChanged() }
                    }
                    MenuHeader(c, "Ориентация")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orientations.forEachIndexed { i, name ->
                            MenuOption(c, name, s.orient == i, false) {
                                vm.store.setOrientation(i); onChanged()
                            }
                        }
                    }
                    MenuHeader(c, "Зоны касания")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        zones.forEachIndexed { i, name ->
                            MenuOption(c, name, s.tapZones == i, false) {
                                vm.store.setTapZones(i); onChanged()
                            }
                        }
                    }
                    MenuHeader(c, "Зоны касания наоборот")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        navPos.forEachIndexed { i, name ->
                            MenuOption(c, name, s.invert == i, false) {
                                vm.store.si("nav_pos", i); onChanged()
                            }
                        }
                    }
                    MenuSlider(c, "Боковой отступ", s.sidebar, 0f..100f) {
                        vm.store.si("sidebar_pad", it); onChanged()
                    }
                    MenuCheckbox(c, "Обрезать поля", s.crop) { vm.store.sb("crop_borders", it); onChanged() }
                    MenuCheckbox(c, "Разделение широких страниц", s.split) {
                        vm.store.sb("split_wide", it); onChanged()
                    }
                    MenuCheckbox(c, "Развернуть широкие страницы под размер экрана", s.stretch) {
                        vm.store.sb("stretch_wide", it); onChanged()
                    }
                    MenuCheckbox(c, "Двойное нажатие для увеличения", s.dtZoom) {
                        vm.store.setDoubleTapZoom(it); onChanged()
                    }
                    MenuCheckbox(c, "Отключить уменьшение масштаба", s.noScale) {
                        vm.store.sb("no_scale_down", it); onChanged()
                    }
                }
                1 -> {
                    MenuHeader(c, "Цвет фона")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bgColors.forEachIndexed { i, name ->
                            MenuOption(c, name, vm.store.bgColor() == i, false) {
                                vm.store.setBgColor(i); onChanged()
                            }
                        }
                    }
                    MenuCheckbox(c, "Номер страницы", s.showNum) {
                        vm.store.setPageNumber(it); onChanged()
                    }
                    if (s.showNum) {
                        MenuSlider(c, "Размер номера страницы", s.numSize, 10f..24f) {
                            vm.store.si("page_num_size", it); onChanged()
                        }
                        MenuSlider(c, "Прозрачность подложки", s.numAlpha, 0f..100f) {
                            vm.store.si("page_num_alpha", it); onChanged()
                        }
                    }
                    MenuHeader(c, "Вертикальная навигация главы")
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        readModes.forEachIndexed { i, name ->
                            MenuOption(c, name, i in navModeSet, false) {
                                if (i in navModeSet) navModeSet.remove(i) else navModeSet.add(i)
                                vm.store.setStrSet(
                                    "nav_modes",
                                    navModeSet.map { it.toString() }.toSet()
                                )
                                onChanged()
                            }
                        }
                    }
                    MenuCheckbox(c, "Разместить вертикальную навигацию с левой стороны", s.navLeft) {
                        vm.store.sb("nav_left", it); onChanged()
                    }
                    MenuSlider(c, "Высота вертикальной навигации", s.navHeight, 20f..100f) {
                        vm.store.si("nav_height", it); onChanged()
                    }
                    MenuCheckbox(c, "Полноэкранный режим", s.fullscreen) {
                        vm.store.setFullscreen(it); onChanged()
                    }
                    MenuCheckbox(c, "Показывать содержимое в вырезанной области", s.cutout) {
                        vm.store.sb("cutout_content", it); onChanged()
                    }
                    MenuCheckbox(c, "Не выключать экран", s.keep) {
                        vm.store.setKeepScreen(it); onChanged()
                    }
                    MenuCheckbox(c, "Переход между главами", s.chapterTransition) {
                        vm.store.sb("chapter_transition", it); onChanged()
                    }
                    MenuCheckbox(c, "Анимированные переходы страниц", s.anim) {
                        vm.store.sb("anim_transitions", it); onChanged()
                    }
                    MenuCheckbox(c, "Мигать при смене страницы", s.flash) {
                        vm.store.sb("flash_page", it); onChanged()
                    }
                }
                else -> {
                    MenuCheckbox(c, "Пользовательская яркость", s.brightness) {
                        vm.store.sb("user_brightness", it); onChanged()
                    }
                    MenuSlider(c, "Яркость", s.brightnessVal, -75f..100f) {
                        vm.store.si("user_brightness_value", it); onChanged()
                    }
                    MenuCheckbox(c, "Пользовательский светофильтр", s.filter) {
                        vm.store.sb("user_filter", it); onChanged()
                    }
                    val fc = s.filterColor
                    val fr = (fc.red * 255).roundToInt()
                    val fg = (fc.green * 255).roundToInt()
                    val fb = (fc.blue * 255).roundToInt()
                    val fa = (fc.alpha * 255).roundToInt()
                    fun putColor(r: Int, g: Int, b: Int, a: Int) {
                        vm.store.si("user_filter_color", AColor.argb(a, r, g, b))
                        onChanged()
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(28.dp)
                                .background(fc, RoundedCornerShape(6.dp))
                                .border(1.dp, c.border, RoundedCornerShape(6.dp))
                        )
                        Text("Цвет и прозрачность", color = c.onBgVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    MenuSlider(c, "Красный", fr, 0f..255f) { putColor(it, fg, fb, fa) }
                    MenuSlider(c, "Зелёный", fg, 0f..255f) { putColor(fr, it, fb, fa) }
                    MenuSlider(c, "Синий", fb, 0f..255f) { putColor(fr, fg, it, fa) }
                    MenuSlider(c, "Прозрачность", fa, 0f..255f) { putColor(fr, fg, fb, it) }
                    MenuCheckbox(c, "Чёрно-белый фильтр", s.gray) {
                        vm.store.sb("grayscale", it); onChanged()
                    }
                    MenuCheckbox(c, "Цвета наоборот", s.invertColors) {
                        vm.store.sb("invert", it); onChanged()
                    }
                }
            }
        }
    }  // end of Column (menu content)
    }  // end of ReaderMenu


@Composable
private fun MenuTabs(c: MenuPalette, selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf("Режим чтения", "Основные", "Фильтры")
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
                            .background(
                                if (i == selected) c.accent else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
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
            if (checked) {
                Icon(
                    Icons.Default.Check, contentDescription = null,
                    tint = c.selectedText, modifier = Modifier.size(16.dp)
                )
            }
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
private fun MenuSlider(
    c: MenuPalette,
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    onChange: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.onBg, style = MaterialTheme.typography.bodyMedium)
            Text("${local.roundToInt()}", color = c.onBgVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = local.coerceIn(range.start, range.endInclusive),
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.border
            )
        )
    }
}

/** Вертикальный ползунок главы (как verticalNavigator в Mihon). */
@Composable
private fun VerticalChapterNav(
    page: Int,
    count: Int,
    left: Boolean,
    heightPct: Int,
    onSeek: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    val frac = if (count > 1) page.toFloat() / (count - 1) else 0f
    Box(
        Modifier.fillMaxSize().padding(horizontal = 4.dp),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxHeight((heightPct.coerceIn(20, 100)) / 100f)
                .width(40.dp)
                .padding(vertical = 12.dp)
        ) {
            val h = maxHeight
            Box(
                Modifier
                    .width(14.dp)
                    .fillMaxHeight()
                    .pointerInput(count) {
                        fun set(y: Float) {
                            val f = (y / size.height).coerceIn(0f, 1f)
                            onSeek((f * (count - 1)).roundToInt().coerceIn(0, count - 1))
                        }
                        detectTapGestures { off -> set(off.y); onCommit() }
                    }
                    .pointerInput(count) {
                        fun set(y: Float) {
                            val f = (y / size.height).coerceIn(0f, 1f)
                            onSeek((f * (count - 1)).roundToInt().coerceIn(0, count - 1))
                        }
                        detectDragGestures(
                            onDragStart = { off -> set(off.y) },
                            onDragEnd = { onCommit() },
                            onDragCancel = { onCommit() },
                        ) { change, _ ->
                            change.consume()
                            set(change.position.y)
                        }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier.width(4.dp).fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                )
                val thumbY = (h - 14.dp) * frac
                Box(
                    Modifier
                        .offset(y = if (thumbY.value.isNaN()) 0.dp else thumbY)
                        .size(width = 14.dp, height = 14.dp)
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(7.dp))
                )
            }
        }
    }
}

@Composable
private fun PageView(
    session: ChapterSession?,
    index: Int,
    half: Int,
    s: ReaderSettings,
    onSingleTap: (Float, Float) -> Unit,
    onLongPress: () -> Unit,
) {
    val stripMode = s.mode == ReadMode.STRIP || s.mode == ReadMode.STRIP_GAPS
    var bmp by remember(session, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(session, index, s.crop, stripMode) {
        bmp = withContext(Dispatchers.IO) {
            val b = session?.let {
                decodePage(
                    it, index,
                    maxPx = if (stripMode) 3000 else 2200,
                    cropBorders = s.crop,
                )
            }
            b?.asImageBitmap()
        }
    }
    var zoom by remember(session, index, half) { mutableStateOf(1f) }
    var offX by remember(session, index, half) { mutableStateOf(0f) }
    var offY by remember(session, index, half) { mutableStateOf(0f) }
    val cbTap by rememberUpdatedState(onSingleTap)
    val cbLong by rememberUpdatedState(onLongPress)
    val zoomNow by rememberUpdatedState(zoom)
    val winSize = LocalWindowInfo.current.containerSize
    val pageOrigin = remember { Holder() }

    val cf = remember(s.gray, s.invertColors) {
        var m: ColorMatrix? = null
        if (s.invertColors) {
            m = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        if (s.gray) {
            val g = ColorMatrix().apply { setToSaturation(0f) }
            m = if (m == null) g else m.also { it.timesAssign(g) }
        }
        m?.let { ColorFilter.colorMatrix(it) }
    }

    val b = bmp
    if (b == null) {
        val bounds = remember(session, index, half) {
            session?.let { pageBounds(it, index) }
        }
        val ratio = bounds?.let {
            ((if (half >= 0) it[0] / 2f else it[0].toFloat()) / it[1]).coerceAtLeast(0.05f)
        } ?: 0.6f
        Box(
            if (stripMode) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("…", color = Color.Gray)
        }
    } else {
        val partW = if (half >= 0) b.width / 2f else b.width.toFloat()
        var mod = Modifier
            .then(
                if (stripMode) Modifier.fillMaxWidth()
                    .aspectRatio((partW / b.height).coerceAtLeast(0.05f))
                else Modifier.fillMaxSize()
            )
            .onGloballyPositioned { pageOrigin.value = it.positionInRoot() }
            .pointerInput(session, index, half, s.dtZoom) {
                detectTapGestures(
                    onTap = { off ->
                        if (zoomNow <= 1.01f) {
                            val gx = pageOrigin.value.x + off.x
                            val gy = pageOrigin.value.y + off.y
                            cbTap(
                                (gx / winSize.width).coerceIn(0f, 1f),
                                (gy / winSize.height).coerceIn(0f, 1f),
                            )
                        }
                    },
                    onLongPress = { cbLong() },
                    onDoubleTap = if (s.dtZoom) {
                        {
                            if (zoomNow > 1f) {
                                zoom = 1f; offX = 0f; offY = 0f
                            } else {
                                zoom = 2.2f
                            }
                        }
                    } else null
                )
            }
        if (zoom > 1.01f || s.noScale) {
            mod = mod.pointerInput(session, index, half) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offX += drag.x
                    offY += drag.y
                }
            }
        }
        Canvas(mod) {
            val paged = s.mode == ReadMode.DEFAULT || s.mode == ReadMode.PAGED_LTR ||
                s.mode == ReadMode.PAGED_RTL || s.mode == ReadMode.PAGED_VERTICAL
            val sw = if (half >= 0) b.width / 2f else b.width.toFloat()
            val sh = b.height.toFloat()
            val sx = if (half == 1) b.width / 2f else 0f
            val wide = b.width > b.height * 1.2f
            if (s.stretch && wide && half < 0 && paged) {
                drawImage(
                    image = b,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    colorFilter = cf,
                    filterQuality = if (zoom > 1.01f) FilterQuality.Medium else FilterQuality.Low,
                )
                return@Canvas
            }
            val fit = min(size.width / sw, size.height / sh)
            val fitW = size.width / sw
            val base = when {
                s.noScale -> max(if (paged) fit else fitW, 1f)
                paged -> fit
                else -> fitW
            }
            val total = base * zoom
            val dw = sw * total
            val dh = sh * total
            val maxX = ((dw - size.width) / 2f).coerceAtLeast(0f)
            val maxY = ((dh - size.height) / 2f).coerceAtLeast(0f)
            val dx = (size.width - dw) / 2f + offX.coerceIn(-maxX, maxX)
            val dy = (size.height - dh) / 2f + offY.coerceIn(-maxY, maxY)
            drawImage(
                image = b,
                srcOffset = IntOffset(sx.roundToInt(), 0),
                srcSize = IntSize(sw.roundToInt(), sh.roundToInt()),
                dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                colorFilter = cf,
                filterQuality = if (total > 1.01f || zoom > 1.01f) FilterQuality.Medium else FilterQuality.Low,
            )
        }
    }
}

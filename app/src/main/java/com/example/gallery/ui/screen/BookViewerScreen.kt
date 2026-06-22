package com.example.gallery.ui.screen

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

private enum class BookPageLayout { AUTO, SINGLE, DOUBLE }
private enum class BookReadingDirection { RIGHT_TO_LEFT, LEFT_TO_RIGHT }
private enum class BookFitMode { SCREEN, WIDTH, HEIGHT }
private enum class BookBackground { BLACK, GRAY, WHITE }
private enum class BookRenderQuality { STANDARD, HIGH }

private data class BookViewerSettings(
    val pageLayout: BookPageLayout = BookPageLayout.AUTO,
    val readingDirection: BookReadingDirection = BookReadingDirection.RIGHT_TO_LEFT,
    val fitMode: BookFitMode = BookFitMode.SCREEN,
    val background: BookBackground = BookBackground.BLACK,
    val renderQuality: BookRenderQuality = BookRenderQuality.STANDARD,
    val pageGapDp: Int = 0,
    val preloadPages: Int = 1,
    val tapNavigation: Boolean = true,
    val keepScreenOn: Boolean = true
)

private const val BOOK_VIEWER_PREFS = "book_viewer_settings"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookViewerScreen(
    book: BookData,
    repository: BookRepository,
    onClose: () -> Unit,
    onPreviousBook: (() -> Unit)? = null,
    onNextBook: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val preferences = remember {
        context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)
    }
    var viewerSettings by remember { mutableStateOf(loadBookViewerSettings(preferences)) }
    var showSettings by remember { mutableStateOf(false) }

    fun updateSettings(updated: BookViewerSettings) {
        viewerSettings = updated
        saveBookViewerSettings(preferences, updated)
    }
    
    var screenOrientation by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    val isLandscape = when (screenOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> true
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> false
        else -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
    val isTwoPageMode = when (viewerSettings.pageLayout) {
        BookPageLayout.AUTO -> isLandscape
        BookPageLayout.SINGLE -> false
        BookPageLayout.DOUBLE -> true
    }
    val isRightToLeft = viewerSettings.readingDirection == BookReadingDirection.RIGHT_TO_LEFT
    val pageContentScale = when (viewerSettings.fitMode) {
        BookFitMode.SCREEN -> ContentScale.Fit
        BookFitMode.WIDTH -> ContentScale.FillWidth
        BookFitMode.HEIGHT -> ContentScale.FillHeight
    }
    val viewerBackground = when (viewerSettings.background) {
        BookBackground.BLACK -> Color.Black
        BookBackground.GRAY -> Color(0xFF303030)
        BookBackground.WHITE -> Color.White
    }

    val spreadCount = if (isTwoPageMode) (book.pageCount + 1) / 2 else book.pageCount
    var currentAbsolutePage by rememberSaveable { mutableIntStateOf(0) }
    
    val pagerState = rememberPagerState(
        initialPage = if (isTwoPageMode) currentAbsolutePage / 2 else currentAbsolutePage,
        pageCount = { spreadCount }
    )
    
    LaunchedEffect(isTwoPageMode) {
        val targetSpread = if (isTwoPageMode) currentAbsolutePage / 2 else currentAbsolutePage
        if (pagerState.currentPage != targetSpread) {
            pagerState.scrollToPage(targetSpread)
        }
    }

    LaunchedEffect(pagerState.currentPage, isTwoPageMode) {
        currentAbsolutePage = if (isTwoPageMode) pagerState.currentPage * 2 else pagerState.currentPage
    }
    
    var isUiVisible by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(isUiVisible, insetsController, screenOrientation) {
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!isUiVisible) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    SideEffect {
        if (screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            (context as? Activity)?.requestedOrientation = screenOrientation
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val originalKeepScreenOn = remember(view) { view.keepScreenOn }
    SideEffect {
        view.keepScreenOn = viewerSettings.keepScreenOn
    }
    DisposableEffect(view) {
        onDispose {
            view.keepScreenOn = originalKeepScreenOn
        }
    }

    BackHandler { onClose() }

    val pageCache = remember { mutableStateMapOf<Int, Bitmap?>() }
    val pageLoadMutex = remember { Mutex() }
    var activeLoadingPage by remember { mutableStateOf<Int?>(null) }
    var pageLoadStartedAtMs by remember { mutableLongStateOf(0L) }
    var pageLoadElapsedMs by remember { mutableLongStateOf(0L) }
    var averagePageLoadMs by remember { mutableLongStateOf(0L) }

    suspend fun loadPage(pageIndex: Int): Bitmap? = pageLoadMutex.withLock {
        if (pageCache.containsKey(pageIndex)) return@withLock pageCache[pageIndex]
        val startedAt = SystemClock.elapsedRealtime()
        activeLoadingPage = pageIndex
        pageLoadStartedAtMs = startedAt
        pageLoadElapsedMs = 0L
        val maxLongSide = when (viewerSettings.renderQuality) {
            BookRenderQuality.STANDARD -> 1800
            BookRenderQuality.HIGH -> 2800
        }
        try {
            if (book.type == BookType.ZIP) {
                repository.getZipPage(book.path, pageIndex, maxLongSide)
            } else {
                repository.getPdfPage(book.path, pageIndex, maxLongSide)
            }
        } finally {
            val duration = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
            averagePageLoadMs = if (averagePageLoadMs == 0L) {
                duration
            } else {
                (averagePageLoadMs * 3L + duration) / 4L
            }
            if (activeLoadingPage == pageIndex) {
                pageLoadElapsedMs = duration
                activeLoadingPage = null
            }
        }
    }

    LaunchedEffect(activeLoadingPage, pageLoadStartedAtMs) {
        while (activeLoadingPage != null) {
            pageLoadElapsedMs = SystemClock.elapsedRealtime() - pageLoadStartedAtMs
            delay(100)
        }
    }

    DisposableEffect(book.id) {
        onDispose {
            pageCache.values.filterNotNull().distinct().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            pageCache.clear()
        }
    }

    LaunchedEffect(currentAbsolutePage, viewerSettings.preloadPages, isTwoPageMode) {
        val keepRadius = (viewerSettings.preloadPages + 2) * if (isTwoPageMode) 2 else 1
        val keysToRemove = pageCache.keys.filter { kotlin.math.abs(it - currentAbsolutePage) > keepRadius }
        keysToRemove.forEach { key ->
            pageCache.remove(key)?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    LaunchedEffect(viewerSettings.renderQuality) {
        pageCache.values.filterNotNull().distinct().forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        pageCache.clear()
    }

    fun pageToSeekValue(page: Int): Float = if (isRightToLeft) {
        (book.pageCount - 1 - page).toFloat()
    } else {
        page.toFloat()
    }

    fun seekValueToPage(value: Float): Int = if (isRightToLeft) {
        (book.pageCount - 1) - value.toInt()
    } else {
        value.toInt()
    }

    LaunchedEffect(currentAbsolutePage, isRightToLeft) {
        if (!isSeeking) {
            seekValue = pageToSeekValue(currentAbsolutePage)
        }
    }

    LaunchedEffect(isSeeking, seekValue, isRightToLeft) {
        if (!isSeeking) {
            previewBitmap?.let { if (!it.isRecycled) it.recycle() }
            previewBitmap = null
            return@LaunchedEffect
        }
        delay(120)
        val page = seekValueToPage(seekValue)
        val loaded = if (book.type == BookType.ZIP) {
            repository.getZipPage(book.path, page, 640)
        } else {
            repository.getPdfPage(book.path, page, 640)
        }
        previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        previewBitmap = loaded
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(viewerBackground)
        .onGloballyPositioned { viewSize = it.size }
    ) {
        LaunchedEffect(pagerState.isScrollInProgress) {
            if (pagerState.isScrollInProgress) {
                isUiVisible = false
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset += pan
                        } else {
                            offset = Offset.Zero
                        }
                    }
                },
            beyondViewportPageCount = viewerSettings.preloadPages,
            reverseLayout = isRightToLeft,
            userScrollEnabled = scale <= 1.01f
        ) { spreadIndex ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (isTwoPageMode) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val firstPageIndex = spreadIndex * 2
                        val secondPageIndex = firstPageIndex + 1
                        val leftPaneIndex = if (isRightToLeft) secondPageIndex else firstPageIndex
                        val rightPaneIndex = if (isRightToLeft) firstPageIndex else secondPageIndex

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (leftPaneIndex < book.pageCount) {
                                val bmp = pageCache[leftPaneIndex]
                                LaunchedEffect(leftPaneIndex) {
                                    if (pageCache[leftPaneIndex] == null) {
                                        Log.d("BookViewerScreen", "Loading left pane: $leftPaneIndex")
                                        val loaded = loadPage(leftPaneIndex)
                                        pageCache[leftPaneIndex] = loaded
                                    }
                                }
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = pageContentScale, alignment = Alignment.CenterEnd)
                                } else {
                                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) Color.DarkGray else Color.White)
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (rightPaneIndex < book.pageCount) {
                                val bmp = pageCache[rightPaneIndex]
                                LaunchedEffect(rightPaneIndex) {
                                    if (pageCache[rightPaneIndex] == null) {
                                        Log.d("BookViewerScreen", "Loading right pane: $rightPaneIndex")
                                        val loaded = loadPage(rightPaneIndex)
                                        pageCache[rightPaneIndex] = loaded
                                    }
                                }
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = pageContentScale, alignment = Alignment.CenterStart)
                                } else {
                                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) Color.DarkGray else Color.White)
                                }
                            }
                        }
                    }
                } else {
                    val bmp = pageCache[spreadIndex]
                    LaunchedEffect(spreadIndex) {
                        if (pageCache[spreadIndex] == null) {
                            Log.d("BookViewerScreen", "Loading single page: $spreadIndex")
                            val loaded = loadPage(spreadIndex)
                            pageCache[spreadIndex] = loaded
                        }
                    }
                    if (bmp != null) {
                        Image(
                            bmp.asImageBitmap(),
                            null,
                            Modifier.fillMaxSize().padding(viewerSettings.pageGapDp.dp),
                            contentScale = pageContentScale
                        )
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) Color.DarkGray else Color.White)
                    }
                }

                // Tap Areas (Inside Page to not block swiping)
                if (scale <= 1.01f && viewerSettings.tapNavigation) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxHeight().weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Color.White.copy(alpha = 0.2f))) {
                            val target = if (isRightToLeft) pagerState.currentPage + 1 else pagerState.currentPage - 1
                            if (target in 0 until pagerState.pageCount) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        })
                        Box(Modifier.fillMaxHeight().weight(1f).pointerInput(Unit) { detectTapGestures { isUiVisible = !isUiVisible } })
                        Box(Modifier.fillMaxHeight().weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Color.White.copy(alpha = 0.2f))) {
                            val target = if (isRightToLeft) pagerState.currentPage - 1 else pagerState.currentPage + 1
                            if (target in 0 until pagerState.pageCount) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        })
                    }
                } else if (scale <= 1.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { isUiVisible = !isUiVisible } }
                    )
                }
            }
        }

        if (isUiVisible) {
            Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).height(60.dp).padding(horizontal = 8.dp)) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    Text(
                        book.title,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 56.dp, end = 192.dp),
                        maxLines = 1,
                        fontSize = 14.sp
                    )
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { openBookTitleSearch(context, book.title) }) {
                            Icon(Icons.Default.Search, contentDescription = "タイトルで検索", tint = Color.White)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "表示設定", tint = Color.White)
                        }
                        IconButton(onClick = {
                            val bitmap = captureViewToBitmap(context, isTwoPageMode, book.pageCount, pagerState.currentPage, pageCache, viewSize)
                            if (bitmap != null) saveBitmapToGallery(context, bitmap) else Toast.makeText(context, "保存失敗", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Screenshot, null, tint = Color.White) }
                        IconButton(onClick = {
                            screenOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        }) { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) }
                    }
                }
            }
            Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentAbsolutePage + 1} / ${book.pageCount}", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = seekValue,
                            onValueChange = { 
                                seekValue = it
                                isSeeking = true
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                val target = seekValueToPage(seekValue)
                                scope.launch { pagerState.scrollToPage(if (isTwoPageMode) target / 2 else target) }
                            },
                            valueRange = 0f..(book.pageCount - 1).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(activeTrackColor = Color.White.copy(alpha = 0.3f), inactiveTrackColor = Color.White.copy(alpha = 0.3f), thumbColor = Color.White)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onPreviousBook?.invoke() }, enabled = onPreviousBook != null) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("前の本")
                        }
                        TextButton(onClick = { onNextBook?.invoke() }, enabled = onNextBook != null) {
                            Text("次の本")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                        }
                    }
                }
            }
            if (isSeeking && previewBitmap != null) {
                Card(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 168.dp).size(160.dp, 240.dp), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                    Image(previewBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }

        val loadingPage = activeLoadingPage
        val isVisiblePageLoading = loadingPage != null && (
            loadingPage == currentAbsolutePage ||
                (isTwoPageMode && loadingPage == currentAbsolutePage + 1)
            )
        if (isVisiblePageLoading) {
            val estimateMs = averagePageLoadMs.takeIf { it > 0L }
            val progress = estimateMs?.let {
                (pageLoadElapsedMs.toFloat() / it.toFloat()).coerceIn(0f, 0.95f)
            }
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = if (isUiVisible) 176.dp else 28.dp
                    )
                    .fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = if (estimateMs == null) {
                            "ページを読み込み中  ${formatBookLoadTime(pageLoadElapsedMs)}経過"
                        } else {
                            val remainingMs = (estimateMs - pageLoadElapsedMs).coerceAtLeast(0L)
                            "ページを読み込み中  残り約${formatBookLoadTime(remainingMs)}"
                        },
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = Color(0xFF1D1A18),
            contentColor = Color.White
        ) {
            BookViewerSettingsPanel(
                settings = viewerSettings,
                onSettingsChange = ::updateSettings
            )
        }
    }
}

@Composable
private fun BookViewerSettingsPanel(
    settings: BookViewerSettings,
    onSettingsChange: (BookViewerSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("本ビュワー設定", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

        BookChoiceSetting(
            title = "ページ表示",
            options = listOf("自動" to BookPageLayout.AUTO, "1ページ" to BookPageLayout.SINGLE, "見開き" to BookPageLayout.DOUBLE),
            selected = settings.pageLayout,
            onSelected = { onSettingsChange(settings.copy(pageLayout = it)) }
        )
        BookChoiceSetting(
            title = "読む方向",
            options = listOf("右から左" to BookReadingDirection.RIGHT_TO_LEFT, "左から右" to BookReadingDirection.LEFT_TO_RIGHT),
            selected = settings.readingDirection,
            onSelected = { onSettingsChange(settings.copy(readingDirection = it)) }
        )
        BookChoiceSetting(
            title = "ページの合わせ方",
            options = listOf("画面内" to BookFitMode.SCREEN, "幅" to BookFitMode.WIDTH, "高さ" to BookFitMode.HEIGHT),
            selected = settings.fitMode,
            onSelected = { onSettingsChange(settings.copy(fitMode = it)) }
        )
        BookChoiceSetting(
            title = "背景",
            options = listOf("黒" to BookBackground.BLACK, "グレー" to BookBackground.GRAY, "白" to BookBackground.WHITE),
            selected = settings.background,
            onSelected = { onSettingsChange(settings.copy(background = it)) }
        )
        BookChoiceSetting(
            title = "表示画質",
            options = listOf("標準" to BookRenderQuality.STANDARD, "高画質" to BookRenderQuality.HIGH),
            selected = settings.renderQuality,
            onSelected = { onSettingsChange(settings.copy(renderQuality = it)) }
        )

        Text("ページ間隔 ${settings.pageGapDp}dp", color = Color.LightGray)
        Slider(
            value = settings.pageGapDp.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(pageGapDp = it.toInt())) },
            valueRange = 0f..24f,
            steps = 5
        )

        Text("先読み ${settings.preloadPages}ページ", color = Color.LightGray)
        Slider(
            value = settings.preloadPages.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(preloadPages = it.toInt())) },
            valueRange = 0f..2f,
            steps = 1
        )

        BookSwitchSetting(
            title = "左右タップでページ送り",
            checked = settings.tapNavigation,
            onCheckedChange = { onSettingsChange(settings.copy(tapNavigation = it)) }
        )
        BookSwitchSetting(
            title = "閲覧中は画面を消灯しない",
            checked = settings.keepScreenOn,
            onCheckedChange = { onSettingsChange(settings.copy(keepScreenOn = it)) }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun <T> BookChoiceSetting(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.LightGray, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) }
                )
            }
        }

    }
}

@Composable
private fun BookSwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color.LightGray, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openBookTitleSearch(context: Context, title: String) {
    val query = title.substringBeforeLast('.').ifBlank { title }
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
    )
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show() }
}

private fun loadBookViewerSettings(preferences: android.content.SharedPreferences): BookViewerSettings {
    fun <T : Enum<T>> enumValue(key: String, default: T, values: Array<T>): T {
        val stored = preferences.getString(key, default.name)
        return values.firstOrNull { it.name == stored } ?: default
    }
    return BookViewerSettings(
        pageLayout = enumValue("pageLayout", BookPageLayout.AUTO, BookPageLayout.entries.toTypedArray()),
        readingDirection = enumValue("readingDirection", BookReadingDirection.RIGHT_TO_LEFT, BookReadingDirection.entries.toTypedArray()),
        fitMode = enumValue("fitMode", BookFitMode.SCREEN, BookFitMode.entries.toTypedArray()),
        background = enumValue("background", BookBackground.BLACK, BookBackground.entries.toTypedArray()),
        renderQuality = enumValue("renderQuality", BookRenderQuality.STANDARD, BookRenderQuality.entries.toTypedArray()),
        pageGapDp = preferences.getInt("pageGapDp", 0).coerceIn(0, 24),
        preloadPages = preferences.getInt("preloadPages", 1).coerceIn(0, 2),
        tapNavigation = preferences.getBoolean("tapNavigation", true),
        keepScreenOn = preferences.getBoolean("keepScreenOn", true)
    )
}

private fun saveBookViewerSettings(
    preferences: android.content.SharedPreferences,
    settings: BookViewerSettings
) {
    preferences.edit()
        .putString("pageLayout", settings.pageLayout.name)
        .putString("readingDirection", settings.readingDirection.name)
        .putString("fitMode", settings.fitMode.name)
        .putString("background", settings.background.name)
        .putString("renderQuality", settings.renderQuality.name)
        .putInt("pageGapDp", settings.pageGapDp)
        .putInt("preloadPages", settings.preloadPages)
        .putBoolean("tapNavigation", settings.tapNavigation)
        .putBoolean("keepScreenOn", settings.keepScreenOn)
        .apply()
}

private fun formatBookLoadTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000f
    return if (seconds < 10f) "%.1f秒".format(Locale.JAPAN, seconds) else "${seconds.toInt()}秒"
}

private fun captureViewToBitmap(context: Context, isTwoPageMode: Boolean, pageCount: Int, spreadIdx: Int, cache: Map<Int, Bitmap?>, viewSize: IntSize): Bitmap? {
    if (viewSize.width <= 0 || viewSize.height <= 0) return null
    val rects = mutableListOf<RectF>()
    if (isTwoPageMode) {
        val rIdx = spreadIdx * 2; val lIdx = rIdx + 1; val halfW = viewSize.width / 2f
        cache[rIdx]?.let { rects.add(getFitRect(it, halfW, viewSize.height.toFloat(), halfW, 0f)) }
        if (lIdx < pageCount) cache[lIdx]?.let { rects.add(getFitRect(it, halfW, viewSize.height.toFloat(), 0f, 0f)) }
    } else { cache[spreadIdx]?.let { rects.add(getFitRect(it, viewSize.width.toFloat(), viewSize.height.toFloat(), 0f, 0f)) } }
    if (rects.isEmpty()) return null
    val union = RectF(rects[0]); rects.forEach { union.union(it) }
    val result = Bitmap.createBitmap(union.width().toInt().coerceAtLeast(1), union.height().toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result); canvas.drawColor(android.graphics.Color.BLACK)
    canvas.translate(-union.left, -union.top)
    if (isTwoPageMode) {
        val rIdx = spreadIdx * 2; val lIdx = rIdx + 1; val halfW = viewSize.width / 2f
        cache[rIdx]?.let { drawFit(canvas, it, halfW, viewSize.height.toFloat(), halfW, 0f) }
        if (lIdx < pageCount) cache[lIdx]?.let { drawFit(canvas, it, halfW, viewSize.height.toFloat(), 0f, 0f) }
    } else { cache[spreadIdx]?.let { drawFit(canvas, it, viewSize.width.toFloat(), viewSize.height.toFloat(), 0f, 0f) } }
    return result
}

private fun getFitRect(bmp: Bitmap, tw: Float, th: Float, x: Float, y: Float): RectF {
    val s = Math.min(tw / bmp.width, th / bmp.height); val dw = bmp.width * s; val dh = bmp.height * s
    val dx = x + (tw - dw) / 2f; val dy = y + (th - dh) / 2f
    return RectF(dx, dy, dx + dw, dy + dh)
}

private fun drawFit(canvas: Canvas, bmp: Bitmap, tw: Float, th: Float, x: Float, y: Float) {
    val s = Math.min(tw / bmp.width, th / bmp.height); val dw = bmp.width * s; val dh = bmp.height * s
    val dx = x + (tw - dw) / 2f; val dy = y + (th - dh) / 2f
    canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "BookScreenshot_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    try {
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear(); contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) { Toast.makeText(context, "失敗", Toast.LENGTH_SHORT).show() }
}

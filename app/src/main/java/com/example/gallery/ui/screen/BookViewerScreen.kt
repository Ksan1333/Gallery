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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Locale

private enum class BookPageLayout { AUTO, SINGLE, DOUBLE }
private enum class BookReadingDirection { RIGHT_TO_LEFT, LEFT_TO_RIGHT }
private enum class BookFitMode { SCREEN, WIDTH, HEIGHT, FULL_SIZE, FIXED_SIZE, STRETCH }
private enum class BookBackground { BLACK, GRAY, WHITE }
private enum class BookRenderQuality { STANDARD, HIGH, ULTRA }
private enum class BookScrollMode { PAGE, VERTICAL, HORIZONTAL }
private enum class BookSmoothFilter { AVERAGING, BILINEAR, BICUBIC, LANCZOS3 }
private enum class BookColorMode { NORMAL, GRAYSCALE, COLORIZE }

private data class BookViewerSettings(
    val pageLayout: BookPageLayout = BookPageLayout.AUTO,
    val readingDirection: BookReadingDirection = BookReadingDirection.RIGHT_TO_LEFT,
    val fitMode: BookFitMode = BookFitMode.SCREEN,
    val background: BookBackground = BookBackground.BLACK,
    val renderQuality: BookRenderQuality = BookRenderQuality.STANDARD,
    val scrollMode: BookScrollMode = BookScrollMode.PAGE,
    val smoothFilter: BookSmoothFilter = BookSmoothFilter.BILINEAR,
    val colorMode: BookColorMode = BookColorMode.NORMAL,
    val pageGapDp: Int = 0,
    val preloadPages: Int = 1,
    val fixedSizePercent: Int = 100,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val gamma: Int = 100,
    val tapZoneCount: Int = 3,
    val slideshowSeconds: Int = 0,
    val autoTrimWhiteBorder: Boolean = false,
    val cacheAroundPages: Boolean = true,
    val balloonMagnifier: Boolean = false,
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
    onNextBook: (() -> Unit)? = null,
    onBookmarksChanged: () -> Unit = {},
    initialPage: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val preferences = remember {
        context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)
    }
    val bookmarksPrefs = remember {
        context.getSharedPreferences("book_bookmarks", Context.MODE_PRIVATE)
    }
    var isBookmarked by remember {
        mutableStateOf(bookmarksPrefs.contains(book.id))
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
        BookFitMode.FULL_SIZE -> ContentScale.None
        BookFitMode.FIXED_SIZE -> ContentScale.Fit
        BookFitMode.STRETCH -> ContentScale.FillBounds
    }
    val viewerBackground = when (viewerSettings.background) {
        BookBackground.BLACK -> Color.Black
        BookBackground.GRAY -> GalleryThemeTokens.colors.surfaceVariant
        BookBackground.WHITE -> Color.White
    }

    val spreadCount = if (isTwoPageMode) (book.pageCount + 1) / 2 else book.pageCount
    var currentAbsolutePage by rememberSaveable { mutableIntStateOf(initialPage.coerceAtMost(book.pageCount - 1)) }

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
            BookRenderQuality.ULTRA -> 3600
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

    val effectivePreloadPages = if (viewerSettings.cacheAroundPages) viewerSettings.preloadPages else 0
    LaunchedEffect(currentAbsolutePage, effectivePreloadPages, isTwoPageMode) {
        val keepRadius = (effectivePreloadPages + 2) * if (isTwoPageMode) 2 else 1
        val keysToRemove = pageCache.keys.filter { kotlin.math.abs(it - currentAbsolutePage) > keepRadius }
        keysToRemove.forEach { key ->
            pageCache.remove(key)?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    LaunchedEffect(viewerSettings.renderQuality, viewerSettings.smoothFilter) {
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
            beyondViewportPageCount = effectivePreloadPages,
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
                        fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize
                    )
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = {
                            isBookmarked = !isBookmarked
                            if (isBookmarked) {
                                // ページ番号を含めて保存する形式に変更 (JSON文字列として保存)
                                val bookmarkData = JSONObject()
                                    .put("title", book.title)
                                    .put("page", currentAbsolutePage)
                                    .toString()
                                bookmarksPrefs.edit().putString(book.id, bookmarkData).apply()
                                Toast.makeText(context, "ブックマークしました", Toast.LENGTH_SHORT).show()
                            } else {
                                bookmarksPrefs.edit().remove(book.id).apply()
                                Toast.makeText(context, "ブックマークを解除しました", Toast.LENGTH_SHORT).show()
                            }
                            onBookmarksChanged()
                        }) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "ブックマーク",
                                tint = if (isBookmarked) Color.Cyan else Color.White
                            )
                        }

                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "メニュー", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                containerColor = GalleryThemeTokens.colors.card
                            ) {
                                DropdownMenuItem(
                                    text = { Text("この本を検索", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        openBookTitleSearch(context, book.title)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("設定", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        showSettings = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("スクリーンショット", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Screenshot, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        val bitmap = captureViewToBitmap(context, isTwoPageMode, book.pageCount, pagerState.currentPage, pageCache, viewSize)
                                        if (bitmap != null) saveBitmapToGallery(context, bitmap) else Toast.makeText(context, "失敗", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("画面回転", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        screenOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentAbsolutePage + 1} / ${book.pageCount}", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize)
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
                            "ページを読み込み中 ${formatBookLoadTime(pageLoadElapsedMs)}経過"
                        } else {
                            val remainingMs = (estimateMs - pageLoadElapsedMs).coerceAtLeast(0L)
                            "ページを読み込み中 残り約 ${formatBookLoadTime(remainingMs)}"
                        },
                        color = Color.White,
                        fontSize = com.example.gallery.ui.AppConstants.SmallFontSize
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
            containerColor = GalleryThemeTokens.colors.card,
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
        Text("本ビューア設定", fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(
            "Perfect Viewer の主要項目を参考に、ページ表示、読み方向、表示倍率、補正、キャッシュをここで調整できます。",
            color = Color.LightGray,
            fontSize = com.example.gallery.ui.AppConstants.SmallFontSize
        )

        BookChoiceSetting(
            title = "ページ表示",
            options = listOf("自動" to BookPageLayout.AUTO, "1ページ" to BookPageLayout.SINGLE, "見開き" to BookPageLayout.DOUBLE),
            selected = settings.pageLayout,
            onSelected = { onSettingsChange(settings.copy(pageLayout = it)) }
        )
        BookChoiceSetting(
            title = "読み方向",
            options = listOf("右から左" to BookReadingDirection.RIGHT_TO_LEFT, "左から右" to BookReadingDirection.LEFT_TO_RIGHT),
            selected = settings.readingDirection,
            onSelected = { onSettingsChange(settings.copy(readingDirection = it)) }
        )
        BookChoiceSetting(
            title = "ページ送り",
            options = listOf("ページ" to BookScrollMode.PAGE, "縦" to BookScrollMode.VERTICAL, "横" to BookScrollMode.HORIZONTAL),
            selected = settings.scrollMode,
            onSelected = { onSettingsChange(settings.copy(scrollMode = it)) }
        )
        BookChoiceSetting(
            title = "ページの合わせ方",
            options = listOf(
                "画面内" to BookFitMode.SCREEN,
                "幅" to BookFitMode.WIDTH,
                "高さ" to BookFitMode.HEIGHT,
                "原寸" to BookFitMode.FULL_SIZE,
                "固定" to BookFitMode.FIXED_SIZE,
                "伸縮" to BookFitMode.STRETCH
            ),
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
            options = listOf("標準" to BookRenderQuality.STANDARD, "高画質" to BookRenderQuality.HIGH, "最高" to BookRenderQuality.ULTRA),
            selected = settings.renderQuality,
            onSelected = { onSettingsChange(settings.copy(renderQuality = it)) }
        )
        BookChoiceSetting(
            title = "スムージング",
            options = listOf(
                "平均" to BookSmoothFilter.AVERAGING,
                "Bilinear" to BookSmoothFilter.BILINEAR,
                "Bicubic" to BookSmoothFilter.BICUBIC,
                "Lanczos3" to BookSmoothFilter.LANCZOS3
            ),
            selected = settings.smoothFilter,
            onSelected = { onSettingsChange(settings.copy(smoothFilter = it)) }
        )
        BookChoiceSetting(
            title = "色補正",
            options = listOf("通常" to BookColorMode.NORMAL, "グレー" to BookColorMode.GRAYSCALE, "色味補正" to BookColorMode.COLORIZE),
            selected = settings.colorMode,
            onSelected = { onSettingsChange(settings.copy(colorMode = it)) }
        )

        BookIntSlider("ページ間隔", settings.pageGapDp, 0f..24f, 5, "dp") { onSettingsChange(settings.copy(pageGapDp = it)) }
        BookIntSlider("先読み", settings.preloadPages, 0f..5f, 4, "ページ") { onSettingsChange(settings.copy(preloadPages = it)) }
        BookIntSlider("固定サイズ", settings.fixedSizePercent, 50f..200f, 14, "%") { onSettingsChange(settings.copy(fixedSizePercent = it)) }
        BookIntSlider("明るさ", settings.brightness, -50f..50f, 19, "") { onSettingsChange(settings.copy(brightness = it)) }
        BookIntSlider("コントラスト", settings.contrast, -50f..50f, 19, "") { onSettingsChange(settings.copy(contrast = it)) }
        BookIntSlider("ガンマ", settings.gamma, 50f..200f, 14, "%") { onSettingsChange(settings.copy(gamma = it)) }
        BookIntSlider("タップ領域", settings.tapZoneCount, 1f..5f, 3, "分割") { onSettingsChange(settings.copy(tapZoneCount = it)) }
        BookIntSlider("スライドショー", settings.slideshowSeconds, 0f..30f, 29, "秒") { onSettingsChange(settings.copy(slideshowSeconds = it)) }

        BookSwitchSetting("白い余白を自動カット", settings.autoTrimWhiteBorder) { onSettingsChange(settings.copy(autoTrimWhiteBorder = it)) }
        BookSwitchSetting("前後ページをキャッシュ", settings.cacheAroundPages) { onSettingsChange(settings.copy(cacheAroundPages = it)) }
        BookSwitchSetting("バルーン拡大鏡", settings.balloonMagnifier) { onSettingsChange(settings.copy(balloonMagnifier = it)) }
        BookSwitchSetting("左右タップでページ送り", settings.tapNavigation) { onSettingsChange(settings.copy(tapNavigation = it)) }
        BookSwitchSetting("閲覧中は画面を消さない", settings.keepScreenOn) { onSettingsChange(settings.copy(keepScreenOn = it)) }
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
        Text(title, color = Color.LightGray, fontSize = com.example.gallery.ui.AppConstants.ScrollbarLabelFontSize)
        options.chunked(3).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (label, value) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        label = { Text(label) }
                    )
                }
            }
        }

    }
}

@Composable
private fun BookIntSlider(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    Text("$title $value$suffix", color = Color.LightGray)
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = range,
        steps = steps
    )
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
        scrollMode = enumValue("scrollMode", BookScrollMode.PAGE, BookScrollMode.entries.toTypedArray()),
        smoothFilter = enumValue("smoothFilter", BookSmoothFilter.BILINEAR, BookSmoothFilter.entries.toTypedArray()),
        colorMode = enumValue("colorMode", BookColorMode.NORMAL, BookColorMode.entries.toTypedArray()),
        pageGapDp = preferences.getInt("pageGapDp", 0).coerceIn(0, 24),
        preloadPages = preferences.getInt("preloadPages", 1).coerceIn(0, 5),
        fixedSizePercent = preferences.getInt("fixedSizePercent", 100).coerceIn(50, 200),
        brightness = preferences.getInt("brightness", 0).coerceIn(-50, 50),
        contrast = preferences.getInt("contrast", 0).coerceIn(-50, 50),
        gamma = preferences.getInt("gamma", 100).coerceIn(50, 200),
        tapZoneCount = preferences.getInt("tapZoneCount", 3).coerceIn(1, 5),
        slideshowSeconds = preferences.getInt("slideshowSeconds", 0).coerceIn(0, 30),
        autoTrimWhiteBorder = preferences.getBoolean("autoTrimWhiteBorder", false),
        cacheAroundPages = preferences.getBoolean("cacheAroundPages", true),
        balloonMagnifier = preferences.getBoolean("balloonMagnifier", false),
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
        .putString("scrollMode", settings.scrollMode.name)
        .putString("smoothFilter", settings.smoothFilter.name)
        .putString("colorMode", settings.colorMode.name)
        .putInt("pageGapDp", settings.pageGapDp)
        .putInt("preloadPages", settings.preloadPages)
        .putInt("fixedSizePercent", settings.fixedSizePercent)
        .putInt("brightness", settings.brightness)
        .putInt("contrast", settings.contrast)
        .putInt("gamma", settings.gamma)
        .putInt("tapZoneCount", settings.tapZoneCount)
        .putInt("slideshowSeconds", settings.slideshowSeconds)
        .putBoolean("autoTrimWhiteBorder", settings.autoTrimWhiteBorder)
        .putBoolean("cacheAroundPages", settings.cacheAroundPages)
        .putBoolean("balloonMagnifier", settings.balloonMagnifier)
        .putBoolean("tapNavigation", settings.tapNavigation)
        .putBoolean("keepScreenOn", settings.keepScreenOn)
        .apply()
}

private fun formatBookLoadTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000f
    return if (seconds < 10f) "%.1f?".format(Locale.JAPAN, seconds) else "${seconds.toInt()}?"
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

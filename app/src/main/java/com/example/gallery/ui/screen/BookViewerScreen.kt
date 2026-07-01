package com.example.gallery.ui.screen

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.BatteryManager
import android.os.SystemClock
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import com.example.gallery.ui.component.OperationProgressIndicator
import com.example.gallery.ui.component.TapZoneGuideOverlay
import com.example.gallery.ui.component.tapZoneCountForLayout
import com.example.gallery.ui.component.tapZoneIndexAt
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private enum class BookPageLayout { AUTO, SINGLE, DOUBLE }
private enum class BookReadingDirection { RIGHT_TO_LEFT, LEFT_TO_RIGHT }
private enum class BookFitMode { SCREEN, WIDTH, HEIGHT, FULL_SIZE, FIXED_SIZE, STRETCH }
private enum class BookBackground { BLACK, GRAY, WHITE }
private enum class BookRenderQuality { STANDARD, HIGH, ULTRA }
private enum class BookScrollMode { PAGE, VERTICAL, HORIZONTAL }
private enum class BookSmoothFilter { NEAREST, AVERAGING, BILINEAR, BICUBIC, LANCZOS3 }
private enum class BookColorMode { NORMAL, GRAYSCALE, COLORIZE }
private enum class BookFullscreenMode { DISABLED, FULLSCREEN, HIDE_STATUS_BAR, HIDE_NAV_BAR }
private enum class BookOrientationMode { AUTO, PORTRAIT, LANDSCAPE, REVERSE_PORTRAIT, REVERSE_LANDSCAPE }
private enum class BookTransitionEffect { NONE, PAGE_CURL, SLIDE_HORIZONTAL, SLIDE_VERTICAL, COVER, FADE }

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
    val keepScreenOn: Boolean = true,
    val fullscreenMode: BookFullscreenMode = BookFullscreenMode.FULLSCREEN,
    val orientationMode: BookOrientationMode = BookOrientationMode.AUTO,
    val transitionEffect: BookTransitionEffect = BookTransitionEffect.SLIDE_HORIZONTAL,
    val transitionSpeedMs: Int = 250,
    val slideshowSpeedMs: Int = 250,
    val menuOpacityPercent: Int = 0,
    val longPressMagnifier: Boolean = false,
    val doubleTapFastZoom: Boolean = true,
    val magnifierScalePercent: Int = 200,
    val readMark: String = "NONE",
    val touchIndicator: Boolean = false,
    val showClockBattery: Boolean = false
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
    onOpenBookSettings: () -> Unit = {},
    initialPage: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val preferences = remember {
        context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)
    }
    val globalSettingsPrefs = remember {
        context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
    }
    val progressDisplayMode = globalSettingsPrefs.getString(
        "progressDisplayMode",
        preferences.getString("progressDisplayMode", "MAX")
    ) ?: "MAX"
    val progressMiniStyle = globalSettingsPrefs.getString(
        "progressMiniStyle",
        preferences.getString("progressMiniStyle", "BAR")
    ) ?: "BAR"
    val bookmarksPrefs = remember {
        context.getSharedPreferences("book_bookmarks", Context.MODE_PRIVATE)
    }
    var isBookmarked by remember {
        mutableStateOf(bookmarksPrefs.contains(book.id))
    }
    var viewerSettings by remember { mutableStateOf(loadBookViewerSettings(preferences)) }
    val imageFilterQuality = when (viewerSettings.smoothFilter) {
        BookSmoothFilter.NEAREST -> FilterQuality.None
        BookSmoothFilter.AVERAGING -> FilterQuality.Low
        BookSmoothFilter.BILINEAR -> FilterQuality.Medium
        BookSmoothFilter.BICUBIC,
        BookSmoothFilter.LANCZOS3 -> FilterQuality.High
    }

    fun updateSettings(updated: BookViewerSettings) {
        viewerSettings = updated
        saveBookViewerSettings(preferences, updated)
    }

    var screenOrientationOverride by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    val configuredScreenOrientation = when (viewerSettings.orientationMode) {
        BookOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        BookOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        BookOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        BookOrientationMode.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        BookOrientationMode.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }
    val effectiveScreenOrientation =
        if (screenOrientationOverride != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            screenOrientationOverride
        } else {
            configuredScreenOrientation
        }
    val isLandscape = when (effectiveScreenOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> true
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> true
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> false
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> false
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
    val verticalListState = rememberLazyListState(initialFirstVisibleItemIndex = currentAbsolutePage)

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
    var isSlideshowRunning by rememberSaveable { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isBookMagnifierActive by remember { mutableStateOf(false) }
    var bookMagnifierBaseScale by remember { mutableFloatStateOf(1f) }
    var bookMagnifierBaseOffset by remember { mutableStateOf(Offset.Zero) }
    var bookMagnifierStartPosition by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var touchIndicatorPoint by remember { mutableStateOf<Offset?>(null) }
    var touchIndicatorToken by remember { mutableIntStateOf(0) }
    var suppressTapAfterMagnifier by remember { mutableStateOf(false) }

    fun showBookTouchIndicator(position: Offset) {
        if (!viewerSettings.touchIndicator) return
        touchIndicatorPoint = position
        touchIndicatorToken++
    }

    LaunchedEffect(touchIndicatorToken) {
        if (touchIndicatorPoint != null) {
            delay(450)
            touchIndicatorPoint = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
        isBookMagnifierActive = false
    }

    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(insetsController, viewerSettings.fullscreenMode, isUiVisible, effectiveScreenOrientation) {
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val hiddenTypes = when (viewerSettings.fullscreenMode) {
            BookFullscreenMode.DISABLED -> 0
            BookFullscreenMode.FULLSCREEN -> WindowInsetsCompat.Type.systemBars()
            BookFullscreenMode.HIDE_STATUS_BAR -> WindowInsetsCompat.Type.statusBars()
            BookFullscreenMode.HIDE_NAV_BAR -> WindowInsetsCompat.Type.navigationBars()
        }
        if (hiddenTypes == 0) {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.hide(hiddenTypes)
            if (isUiVisible) {
                insetsController?.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    SideEffect {
        (context as? Activity)?.requestedOrientation = effectiveScreenOrientation
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val originalKeepScreenOn = remember(view) { view.keepScreenOn }
    SideEffect {
        view.keepScreenOn = viewerSettings.keepScreenOn
        (context as? Activity)?.window?.let { window ->
            if (viewerSettings.fullscreenMode == BookFullscreenMode.DISABLED) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
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
        preferences.edit()
            .putString("lastReadBookId:${book.folderPath.hashCode()}", book.id)
            .putInt("lastReadPage:${book.id}", currentAbsolutePage)
            .apply()
    }

    LaunchedEffect(isSlideshowRunning, viewerSettings.slideshowSeconds, viewerSettings.slideshowSpeedMs, pagerState.currentPage, scale) {
        if (!isSlideshowRunning || viewerSettings.slideshowSeconds <= 0 || scale > 1.01f) return@LaunchedEffect
        delay(viewerSettings.slideshowSeconds * 1000L)
        val target = (pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)
        if (target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target, animationSpec = tween(viewerSettings.slideshowSpeedMs))
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

    fun moveSpread(forward: Boolean) {
        val delta = if (forward) 1 else -1
        val target = if (isRightToLeft) pagerState.currentPage - delta else pagerState.currentPage + delta
        if (target in 0 until pagerState.pageCount) {
            scope.launch { pagerState.animateScrollToPage(target, animationSpec = tween(viewerSettings.transitionSpeedMs)) }
        }
    }

    fun saveCurrentScreenshot() {
        val spreadIndex = if (viewerSettings.scrollMode == BookScrollMode.VERTICAL) currentAbsolutePage else pagerState.currentPage
        val bitmap = captureViewToBitmap(context, isTwoPageMode, book.pageCount, spreadIndex, pageCache, viewSize)
        if (bitmap != null) {
            saveBitmapToGallery(context, bitmap)
        } else {
            Toast.makeText(context, "保存に失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleZoom() {
        scale = if (scale > 1.01f) 1f else 2.5f
        offset = Offset.Zero
    }

    fun handleTapZone(index: Int, zoneCount: Int) {
        when (zoneCount) {
            11 -> when (index) {
                0 -> onPreviousBook?.invoke() ?: moveSpread(false)
                1, 2 -> moveSpread(false)
                3 -> toggleZoom()
                4 -> saveCurrentScreenshot()
                5 -> isUiVisible = !isUiVisible
                6 -> onOpenBookSettings()
                7 -> if (viewerSettings.slideshowSeconds > 0) {
                    isSlideshowRunning = !isSlideshowRunning
                    isUiVisible = !isSlideshowRunning
                }
                8 -> toggleZoom()
                9 -> moveSpread(true)
                10 -> onNextBook?.invoke() ?: moveSpread(true)
                else -> isUiVisible = !isUiVisible
            }
            7 -> when (index) {
                0 -> onPreviousBook?.invoke() ?: moveSpread(false)
                1 -> onNextBook?.invoke() ?: moveSpread(true)
                2 -> moveSpread(false)
                3 -> isUiVisible = !isUiVisible
                4 -> moveSpread(true)
                5 -> toggleZoom()
                6 -> if (viewerSettings.slideshowSeconds > 0) {
                    isSlideshowRunning = !isSlideshowRunning
                    isUiVisible = !isSlideshowRunning
                } else {
                    saveCurrentScreenshot()
                }
                else -> isUiVisible = !isUiVisible
            }
            5 -> when (index) {
                0 -> moveSpread(false)
                1 -> onPreviousBook?.invoke() ?: moveSpread(false)
                2 -> isUiVisible = !isUiVisible
                3 -> onNextBook?.invoke() ?: moveSpread(true)
                4 -> moveSpread(true)
                else -> isUiVisible = !isUiVisible
            }
            4 -> when (index) {
                0 -> moveSpread(false)
                1 -> toggleZoom()
                2 -> onOpenBookSettings()
                else -> moveSpread(true)
            }
            else -> when {
                index < zoneCount / 3 -> moveSpread(false)
                index >= zoneCount - zoneCount / 3 -> moveSpread(true)
                else -> isUiVisible = !isUiVisible
            }
        }
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

        if (viewerSettings.scrollMode == BookScrollMode.VERTICAL) {
            LaunchedEffect(verticalListState.firstVisibleItemIndex) {
                currentAbsolutePage = verticalListState.firstVisibleItemIndex.coerceIn(0, book.pageCount - 1)
            }
            LazyColumn(
                state = verticalListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { position ->
                            showBookTouchIndicator(position)
                            val zoneCount = viewerSettings.tapZoneCount.coerceIn(3, 11)
                            val zoneIndex = tapZoneIndexAt(
                                zoneCount = zoneCount,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                x = position.x,
                                y = position.y
                            )
                            handleTapZone(zoneIndex, zoneCount)
                        })
                    },
                userScrollEnabled = scale <= 1.01f
            ) {
                items((0 until book.pageCount).toList()) { pageIndex ->
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        val bmp = pageCache[pageIndex]
                        LaunchedEffect(pageIndex) {
                            if (pageCache[pageIndex] == null) {
                                pageCache[pageIndex] = loadPage(pageIndex)
                            }
                        }
                        if (bmp != null) {
                            val pageAspectRatio = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.2f, 5f)
                            Image(
                                bmp.asImageBitmap(),
                                null,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(pageAspectRatio)
                                    .padding(viewerSettings.pageGapDp.dp),
                                contentScale = pageContentScale,
                                filterQuality = imageFilterQuality
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = if (viewerBackground == Color.White) Color.DarkGray else Color.White)
                            }
                        }
                    }
                }
            }
        } else {
        val pageTransitionDensity = LocalDensity.current.density
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
            val pageOffset = ((pagerState.currentPage - spreadIndex) + pagerState.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (viewerSettings.transitionEffect) {
                            BookTransitionEffect.NONE,
                            BookTransitionEffect.SLIDE_HORIZONTAL -> Unit
                            BookTransitionEffect.FADE -> {
                                alpha = 1f - pageOffset * 0.55f
                            }
                            BookTransitionEffect.COVER -> {
                                alpha = 1f - pageOffset * 0.25f
                                val nextScale = 1f - pageOffset * 0.08f
                                scaleX = nextScale
                                scaleY = nextScale
                            }
                            BookTransitionEffect.SLIDE_VERTICAL -> {
                                translationY = size.height * 0.18f * pageOffset
                                alpha = 1f - pageOffset * 0.2f
                            }
                            BookTransitionEffect.PAGE_CURL -> {
                                rotationY = pageOffset * if (isRightToLeft) -34f else 34f
                                cameraDistance = 18f * pageTransitionDensity
                                alpha = 1f - pageOffset * 0.18f
                            }
                        }
                    }
            ) {
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
                                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = pageContentScale, alignment = Alignment.CenterEnd, filterQuality = imageFilterQuality)
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
                                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = pageContentScale, alignment = Alignment.CenterStart, filterQuality = imageFilterQuality)
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
                            contentScale = pageContentScale,
                            filterQuality = imageFilterQuality
                        )
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) Color.DarkGray else Color.White)
                    }
                }

                if ((scale <= 1.01f || isBookMagnifierActive) && viewerSettings.tapNavigation) {
                    val zoneCount = viewerSettings.tapZoneCount.coerceIn(3, 11)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(isBookMagnifierActive, viewerSettings.magnifierScalePercent) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (isBookMagnifierActive) {
                                            val change = event.changes.firstOrNull()
                                            if (change != null && change.pressed) {
                                                val drag = change.position - bookMagnifierStartPosition
                                                val targetScale = (bookMagnifierBaseScale * (viewerSettings.magnifierScalePercent / 100f)).coerceIn(1f, 3f)
                                                scale = targetScale
                                                offset = bookMagnifierBaseOffset + drag * targetScale
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(zoneCount, viewerSettings.doubleTapFastZoom, viewerSettings.longPressMagnifier, viewerSettings.magnifierScalePercent) {
                                detectTapGestures(
                                    onPress = {
                                        showBookTouchIndicator(it)
                                        var magnifierShown = false
                                        bookMagnifierBaseScale = scale
                                        bookMagnifierBaseOffset = offset
                                        bookMagnifierStartPosition = it
                                        val job = scope.launch {
                                            delay(450)
                                            if (viewerSettings.longPressMagnifier) {
                                                magnifierShown = true
                                                suppressTapAfterMagnifier = true
                                                isBookMagnifierActive = true
                                                scale = (bookMagnifierBaseScale * (viewerSettings.magnifierScalePercent / 100f)).coerceIn(1f, 3f)
                                            }
                                        }
                                        tryAwaitRelease()
                                        job.cancel()
                                        if (magnifierShown) {
                                            isBookMagnifierActive = false
                                            scale = bookMagnifierBaseScale
                                            offset = bookMagnifierBaseOffset
                                        }
                                    },
                                    onTap = { position ->
                                        showBookTouchIndicator(position)
                                        if (suppressTapAfterMagnifier) {
                                            suppressTapAfterMagnifier = false
                                        } else {
                                            val zoneIndex = tapZoneIndexAt(
                                                zoneCount = zoneCount,
                                                width = size.width.toFloat(),
                                                height = size.height.toFloat(),
                                                x = position.x,
                                                y = position.y
                                            )
                                            handleTapZone(zoneIndex, zoneCount)
                                        }
                                    },
                                    onDoubleTap = {
                                        showBookTouchIndicator(it)
                                        if (viewerSettings.doubleTapFastZoom) {
                                            scale = if (scale > 1.01f) 1f else 2.5f
                                            offset = Offset.Zero
                                        }
                                    }
                                )
                            }
                    )
                } else if (scale <= 1.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { showBookTouchIndicator(it); isUiVisible = !isUiVisible } }
                    )
                }
            }
        }
        }

        if (viewerSettings.touchIndicator) {
            val zoneCount = viewerSettings.tapZoneCount.coerceIn(3, 11)
            TapZoneGuideOverlay(
                labels = bookTapZoneGuideLabels(zoneCount),
                vertical = viewerSettings.scrollMode == BookScrollMode.VERTICAL,
                modifier = Modifier.matchParentSize()
            )
        }

        if (isUiVisible) {
            val menuAlpha = (1f - viewerSettings.menuOpacityPercent / 100f).coerceIn(0f, 1f)
            Surface(color = Color.Black, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().graphicsLayer { alpha = menuAlpha }) {
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).height(60.dp).padding(horizontal = 8.dp)) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    Text(
                        book.title,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 56.dp, end = 240.dp),
                        maxLines = 1,
                        fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize
                    )
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = {
                            isBookmarked = !isBookmarked
                            if (isBookmarked) {
                                // ページ番号を含めて保存する形式に変更
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

                        IconButton(onClick = onOpenBookSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White)
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
                                    text = { Text(if (isSlideshowRunning) "スライドショー停止" else "スライドショー開始", color = if (viewerSettings.slideshowSeconds > 0) Color.White else Color.Gray) },
                                    leadingIcon = { Icon(if (isSlideshowRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if (viewerSettings.slideshowSeconds > 0) Color.White else Color.Gray) },
                                    enabled = viewerSettings.slideshowSeconds > 0,
                                    onClick = {
                                        showMoreMenu = false
                                        isSlideshowRunning = !isSlideshowRunning
                                        isUiVisible = !isSlideshowRunning
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_search_this), color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        openBookTitleSearch(context, book.title)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_screenshot), color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Screenshot, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        saveCurrentScreenshot()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.book_rotate), color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) },
                                    onClick = {
                                        showMoreMenu = false
                                        screenOrientationOverride = if (isLandscape) {
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
            Surface(color = Color.Black, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha = menuAlpha }) {
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
                            Text(stringResource(R.string.book_prev))
                        }
                        TextButton(onClick = { onNextBook?.invoke() }, enabled = onNextBook != null) {
                            Text(stringResource(R.string.book_next))
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
            val progressLabel = if (estimateMs == null) {
                "ページを読み込み中 ${formatBookLoadTime(pageLoadElapsedMs)}経過"
            } else {
                val remainingMs = (estimateMs - pageLoadElapsedMs).coerceAtLeast(0L)
                "ページを読み込み中 残り約${formatBookLoadTime(remainingMs)}"
            }
            OperationProgressIndicator(
                label = progressLabel,
                progress = progress,
                displayMode = progressDisplayMode,
                minimumStyle = progressMiniStyle,
                modifier = Modifier
                    .align(if (progressDisplayMode == "MIN" && progressMiniStyle == "CIRCLE") Alignment.BottomEnd else Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = if (isUiVisible) 176.dp else 28.dp
                    )
            )
        }

        val readProgress = ((currentAbsolutePage + 1).toFloat() / book.pageCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        if (!isUiVisible && viewerSettings.readMark != "NONE") {
            when (viewerSettings.readMark) {
                "ICON" -> Text(
                    text = "既読",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    fontSize = com.example.gallery.ui.AppConstants.TinyFontSize
                )
                "PAGE" -> Text(
                    text = "${currentAbsolutePage + 1}/${book.pageCount}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    fontSize = com.example.gallery.ui.AppConstants.TinyFontSize
                )
                "PERCENT" -> Text(
                    text = "${(readProgress * 100).toInt()}%",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    fontSize = com.example.gallery.ui.AppConstants.TinyFontSize
                )
                "PROGRESS_BAR" -> LinearProgressIndicator(
                    progress = { readProgress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }

        if (viewerSettings.touchIndicator && scale > 1.01f) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(top = if (isUiVisible) 96.dp else 16.dp, start = 16.dp)
            ) {
                Text(
                    text = "x${"%.1f".format(Locale.US, scale)}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = com.example.gallery.ui.AppConstants.TinyFontSize
                )
            }
        }

        if (viewerSettings.showClockBattery) {
            var clockText by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                while (true) {
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scaleValue = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val battery = if (level >= 0 && scaleValue > 0) "${(level * 100 / scaleValue)}%" else "--%"
                    clockText = "${formatter.format(Date())}  $battery"
                    delay(30_000)
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (isUiVisible) 96.dp else 16.dp, end = 16.dp)
            ) {
                Text(
                    text = clockText,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = com.example.gallery.ui.AppConstants.TinyFontSize
                )
            }
        }

        touchIndicatorPoint?.let { point ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((point.x - 22f).roundToInt(), (point.y - 22f).roundToInt()) }
                    .size(44.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            )
        }
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

private fun bookTapZoneLabels(zoneCount: Int): List<String> {
    return when (zoneCount) {
        11 -> listOf(
            "前の本",
            "前ページ",
            "前ページ",
            "ズーム",
            "スクショ",
            "メニュー",
            "設定",
            "スライド",
            "ズーム",
            "次ページ",
            "次の本"
        )
        4 -> listOf("前ページ", "ズーム", "設定", "次ページ")
        else -> listOf("前ページ", "メニュー", "次ページ")
    }
}

private fun bookTapZoneGuideLabels(zoneCount: Int): List<String> {
    return when (zoneCount) {
        11 -> listOf(
            "前の本",
            "前ページ",
            "前ページ",
            "ズーム",
            "スクリーンショット",
            "メニュー",
            "設定",
            "スライドショー",
            "ズーム",
            "次ページ",
            "次の本"
        )
        7 -> listOf("前の本", "次の本", "前ページ", "メニュー", "次ページ", "ズーム", "スライドショー")
        5 -> listOf("前ページ", "前の本", "メニュー", "次の本", "次ページ")
        4 -> listOf("前ページ", "ズーム", "設定", "次ページ")
        else -> listOf("前ページ", "メニュー", "次ページ")
    }
}

private fun loadBookViewerSettings(preferences: android.content.SharedPreferences): BookViewerSettings {
    fun <T : Enum<T>> enumValue(key: String, default: T, values: Array<T>): T {
        val stored = preferences.getString(key, default.name)
        return values.firstOrNull { it.name == stored } ?: default
    }
    fun scrollModeValue(): BookScrollMode {
        val stored = preferences.getString("viewerMode", preferences.getString("scrollMode", BookScrollMode.PAGE.name))
        return when (stored) {
            "VERTICAL_SCROLL" -> BookScrollMode.VERTICAL
            "HORIZONTAL_SCROLL" -> BookScrollMode.HORIZONTAL
            else -> BookScrollMode.entries.firstOrNull { it.name == stored } ?: BookScrollMode.PAGE
        }
    }
    fun slideshowSecondsValue(): Int {
        val intervalMs = preferences.getInt("slideshowIntervalMs", -1)
        return if (intervalMs > 0) (intervalMs / 1000).coerceIn(1, 30) else preferences.getInt("slideshowSeconds", 0).coerceIn(0, 30)
    }
    val bindingDirection = preferences.getString("bindingDirection", null)
    val readingDirection = when (bindingDirection) {
        "LEFT" -> BookReadingDirection.LEFT_TO_RIGHT
        "RIGHT" -> BookReadingDirection.RIGHT_TO_LEFT
        else -> enumValue("readingDirection", BookReadingDirection.RIGHT_TO_LEFT, BookReadingDirection.entries.toTypedArray())
    }
    return BookViewerSettings(
        pageLayout = enumValue("pageLayout", BookPageLayout.AUTO, BookPageLayout.entries.toTypedArray()),
        readingDirection = readingDirection,
        fitMode = enumValue("fitMode", BookFitMode.SCREEN, BookFitMode.entries.toTypedArray()),
        background = enumValue("background", BookBackground.BLACK, BookBackground.entries.toTypedArray()),
        renderQuality = enumValue("renderQuality", BookRenderQuality.STANDARD, BookRenderQuality.entries.toTypedArray()),
        scrollMode = scrollModeValue(),
        smoothFilter = enumValue("smoothFilter", BookSmoothFilter.BILINEAR, BookSmoothFilter.entries.toTypedArray()),
        colorMode = enumValue("colorMode", BookColorMode.NORMAL, BookColorMode.entries.toTypedArray()),
        pageGapDp = preferences.getInt("pageGapDp", 0).coerceIn(0, 24),
        preloadPages = preferences.getInt("preloadPages", 1).coerceIn(0, 5),
        fixedSizePercent = preferences.getInt("fixedSizePercent", 100).coerceIn(50, 200),
        brightness = preferences.getInt("brightness", 0).coerceIn(-50, 50),
        contrast = preferences.getInt("contrast", 0).coerceIn(-50, 50),
        gamma = preferences.getInt("gamma", 100).coerceIn(50, 200),
        tapZoneCount = tapZoneCountForLayout(
            preferences.getString("tapZoneLayout", ""),
            preferences.getInt("tapZoneCount", 3)
        ),
        slideshowSeconds = slideshowSecondsValue(),
        autoTrimWhiteBorder = preferences.getBoolean("autoTrimWhiteBorder", false),
        cacheAroundPages = preferences.getBoolean("imageCache", preferences.getBoolean("cacheAroundPages", true)),
        balloonMagnifier = preferences.getBoolean("longPressMagnifier", preferences.getBoolean("balloonMagnifier", false)),
        tapNavigation = preferences.getBoolean("tapNavigation", true),
        keepScreenOn = preferences.getBoolean("keepScreenOn", true),
        fullscreenMode = enumValue("fullscreenMode", BookFullscreenMode.FULLSCREEN, BookFullscreenMode.entries.toTypedArray()),
        orientationMode = enumValue("orientation", BookOrientationMode.AUTO, BookOrientationMode.entries.toTypedArray()),
        transitionEffect = enumValue("transitionEffect", BookTransitionEffect.SLIDE_HORIZONTAL, BookTransitionEffect.entries.toTypedArray()),
        transitionSpeedMs = preferences.getInt("transitionSpeedMs", 250).coerceIn(0, 2000),
        slideshowSpeedMs = preferences.getInt("slideshowSpeedMs", 250).coerceIn(0, 2000),
        menuOpacityPercent = preferences.getInt("menuOpacityPercent", 0).coerceIn(0, 100),
        longPressMagnifier = preferences.getBoolean("longPressMagnifier", false),
        doubleTapFastZoom = preferences.getBoolean("doubleTapFastZoom", true),
        magnifierScalePercent = preferences.getInt("magnifierScalePercent", 200).coerceIn(100, 300),
        readMark = preferences.getString("readMark", "NONE") ?: "NONE",
        touchIndicator = preferences.getBoolean("touchIndicator", false),
        showClockBattery = preferences.getBoolean("showClockBattery", false)
    )
}

private fun saveBookViewerSettings(
    preferences: android.content.SharedPreferences,
    settings: BookViewerSettings
) {
    preferences.edit()
        .putString("pageLayout", settings.pageLayout.name)
        .putString("readingDirection", settings.readingDirection.name)
        .putString("bindingDirection", if (settings.readingDirection == BookReadingDirection.LEFT_TO_RIGHT) "LEFT" else "RIGHT")
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
        .putString("fullscreenMode", settings.fullscreenMode.name)
        .putString("orientation", settings.orientationMode.name)
        .putString("transitionEffect", settings.transitionEffect.name)
        .putInt("transitionSpeedMs", settings.transitionSpeedMs)
        .putInt("slideshowSpeedMs", settings.slideshowSpeedMs)
        .putInt("menuOpacityPercent", settings.menuOpacityPercent)
        .putBoolean("longPressMagnifier", settings.longPressMagnifier)
        .putBoolean("doubleTapFastZoom", settings.doubleTapFastZoom)
        .putInt("magnifierScalePercent", settings.magnifierScalePercent)
        .putString("readMark", settings.readMark)
        .putBoolean("touchIndicator", settings.touchIndicator)
        .putBoolean("showClockBattery", settings.showClockBattery)
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
    } catch (e: Exception) { Toast.makeText(context, "保存に失敗しました", Toast.LENGTH_SHORT).show() }
}

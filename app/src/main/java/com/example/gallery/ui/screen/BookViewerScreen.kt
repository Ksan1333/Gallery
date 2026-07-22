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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import com.example.gallery.ui.component.OperationProgressIndicator
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryVideoSeekBar
import com.example.gallery.ui.component.TapZoneGuideOverlay
import com.example.gallery.ui.component.isViewerOverflowActionName
import com.example.gallery.ui.component.tapZoneCountForLayout
import com.example.gallery.ui.component.tapZoneIndexAt
import com.example.gallery.ui.component.tapZoneSpecs
import com.example.gallery.ui.component.resolveViewerAction
import com.example.gallery.ui.component.resolveViewerActionLabel
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.ui.theme.GalleryAlphaTokens
import com.example.gallery.util.BookPageCacheManager
import androidx.lifecycle.lifecycleScope
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

private fun handleBookViewerAction(
    function: String,
    context: Context,
    book: BookData,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isTwoPageMode: Boolean,
    currentAbsolutePage: Int,
    isSlideshowRunning: Boolean,
    onToggleSlideshow: () -> Unit,
    onClose: () -> Unit,
    onRotate: () -> Unit,
    onSaveScreenshot: () -> Unit,
    onOpenBookSettings: () -> Unit,
    onBookmarksChanged: () -> Unit
) {
    val scope = (context as? androidx.activity.ComponentActivity)?.lifecycleScope ?: kotlinx.coroutines.MainScope()
    val bookmarksPrefs = context.getSharedPreferences("book_bookmarks", Context.MODE_PRIVATE)

    val actionClose = context.getString(R.string.label_action_close)
    val actionSettings = context.getString(R.string.label_action_settings)
    val actionRotate = context.getString(R.string.label_action_rotate)
    val actionScreenshot = context.getString(R.string.label_action_screenshot)
    val actionPrev = context.getString(R.string.label_action_prev)
    val actionNext = context.getString(R.string.label_action_next)
    val actionBookmark = context.getString(R.string.label_action_bookmark)
    val actionSlideshow = context.getString(R.string.label_action_slideshow)
    val actionSearch = context.getString(R.string.label_action_search)

    when (function) {
        actionClose -> onClose()
        actionSettings -> onOpenBookSettings()
        actionRotate -> onRotate()
        actionScreenshot -> onSaveScreenshot()
        actionPrev -> scope.launch {
            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
        }
        actionNext -> scope.launch {
            pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1))
        }
        actionBookmark -> {
            val exists = bookmarksPrefs.contains(book.id)
            if (!exists) {
                val data = JSONObject().put("title", book.title).put("page", currentAbsolutePage).toString()
                bookmarksPrefs.edit().putString(book.id, data).apply()
                Toast.makeText(context, context.getString(R.string.book_bookmark_added), Toast.LENGTH_SHORT).show()
            } else {
                bookmarksPrefs.edit().remove(book.id).apply()
                Toast.makeText(context, context.getString(R.string.book_bookmark_removed), Toast.LENGTH_SHORT).show()
            }
            onBookmarksChanged()
        }
        actionSlideshow -> onToggleSlideshow()
        actionSearch -> openBookTitleSearch(context, book.title)
    }
}

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
    val seekAnchorsEnabled: Boolean = true,
    val maxSeekAnchors: Int = 3,
    val touchIndicator: Boolean = false,
    val showClockBattery: Boolean = false
)

private const val BOOK_VIEWER_PREFS = "book_viewer_settings"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookViewerScreen(
    book: BookData,
    repository: BookRepository,
    onClose: () -> Unit,
    onPreviousBook: (() -> Unit)? = null,
    onNextBook: (() -> Unit)? = null,
    onBookmarksChanged: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onOpenBookSettings: () -> Unit = {},
    initialPage: Int = 0
) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
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
    val bookFavoritesPrefs = remember {
        context.getSharedPreferences("book_favorites", Context.MODE_PRIVATE)
    }
    var isBookmarked by remember {
        mutableStateOf(bookmarksPrefs.contains(book.id))
    }
    var isFavoriteBook by remember {
        mutableStateOf(bookFavoritesPrefs.getBoolean(book.id, false))
    }
    var showBookTopMenu by remember { mutableStateOf(false) }
    var viewerSettings by remember { mutableStateOf(loadBookViewerSettings(context, preferences)) }
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
        BookBackground.GRAY -> Color.DarkGray
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
    val seekAnchors = remember { mutableStateListOf<Int>() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isBookMagnifierActive by remember { mutableStateOf(false) }
    var bookMagnifierBaseScale by remember { mutableFloatStateOf(1f) }
    var bookMagnifierBaseOffset by remember { mutableStateOf(Offset.Zero) }
    var bookMagnifierFocusOffset by remember { mutableStateOf(Offset.Zero) }
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
    LaunchedEffect(book.id) {
        BookPageCacheManager.prepareCache(book, repository)
    }

    DisposableEffect(view) {
        onDispose {
            view.keepScreenOn = originalKeepScreenOn
            BookPageCacheManager.clearCache()
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

    fun pageToSeekValue(page: Int): Float = page.toFloat()

    fun seekValueToPage(value: Float): Int = value.toInt()

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

    LaunchedEffect(isSeeking, seekValue) {
        if (!isSeeking) {
            previewBitmap = null
            return@LaunchedEffect
        }
        val page = seekValueToPage(seekValue)
        previewBitmap = BookPageCacheManager.getPage(page)
    }

    fun moveSpread(forward: Boolean) {
        // forward=true means "visual right", forward=false means "visual left"
        // In LTR: visual right = next (index+1), visual left = prev (index-1)
        // In RTL: visual right = prev (index-1), visual left = next (index+1)
        val delta = if (isRightToLeft) {
            if (forward) -1 else 1
        } else {
            if (forward) 1 else -1
        }
        val target = pagerState.currentPage + delta
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
            Toast.makeText(context, context.getString(R.string.msg_save_failed, ""), Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleZoom() {
        scale = if (scale > 1.01f) 1f else 2.5f
        offset = Offset.Zero
    }

    val bookTouchAssignments = remember(preferences, viewerSettings.tapZoneCount) {
        val defaults = defaultBookTouchAssignments(viewerSettings.tapZoneCount)
        tapZoneSpecs(viewerSettings.tapZoneCount).map { slot ->
            preferences.getString("book_touch.${slot.id}", defaults[slot.id] ?: "なし") ?: "なし"
        }
    }

    fun handleTapZone(index: Int, zoneCount: Int) {
        when (bookTouchAssignments.getOrElse(index) { "なし" }) {
            AppConstants.ACTION_PREV_BOOK -> onPreviousBook?.invoke() ?: moveSpread(false)
            AppConstants.ACTION_NEXT_BOOK -> onNextBook?.invoke() ?: moveSpread(true)
            AppConstants.ACTION_PREV_PAGE -> moveSpread(false)
            AppConstants.ACTION_NEXT_PAGE -> moveSpread(true)
            AppConstants.ACTION_ZOOM -> toggleZoom()
            AppConstants.ACTION_SETTINGS -> onOpenBookSettings()
            AppConstants.ACTION_TOGGLE_UI -> isUiVisible = !isUiVisible
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
            Box(modifier = Modifier.fillMaxSize()) {
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
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(R.dimen.spacing_tiny))) {
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
                                Box(Modifier.fillMaxWidth().height(dimensionResource(R.dimen.grid_placeholder_height)), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = if (viewerBackground == Color.White) colors.mutedText else colors.primaryText)
                                }
                            }
                        }
                    }
                }
            }
        } else {
        val pageTransitionDensity = LocalDensity.current.density
        Box(modifier = Modifier.fillMaxSize()) {
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
                                    alpha = 1f - pageOffset * GalleryAlphaTokens.TransitionFade
                                }
                                BookTransitionEffect.COVER -> {
                                    alpha = 1f - pageOffset * GalleryAlphaTokens.TransitionCoverAlpha
                                    val nextScale = 1f - pageOffset * GalleryAlphaTokens.TransitionCoverScale
                                    scaleX = nextScale
                                    scaleY = nextScale
                                }
                                BookTransitionEffect.SLIDE_VERTICAL -> {
                                    translationY = size.height * GalleryAlphaTokens.TransitionSlideVertical * pageOffset
                                    alpha = 1f - pageOffset * GalleryAlphaTokens.TransitionSlideVerticalAlpha
                                }
                                BookTransitionEffect.PAGE_CURL -> {
                                    rotationY = pageOffset * if (isRightToLeft) -34f else 34f
                                    cameraDistance = 18f * pageTransitionDensity
                                    alpha = 1f - pageOffset * GalleryAlphaTokens.TransitionPageCurlAlpha
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
                                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) colors.mutedText else colors.primaryText)
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
                                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) colors.mutedText else colors.primaryText)
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
                            CircularProgressIndicator(Modifier.align(Alignment.Center), color = if (viewerBackground == Color.White) colors.mutedText else colors.primaryText)
                        }
                    }

                    if ((scale <= 1.01f || isBookMagnifierActive) && viewerSettings.tapNavigation) {
                        val zoneCount = viewerSettings.tapZoneCount.coerceIn(3, 11)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(viewerSettings.longPressMagnifier, viewerSettings.magnifierScalePercent, viewSize) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (!viewerSettings.longPressMagnifier) return@awaitEachGesture

                                        val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.count { it.pressed } > 1) {
                                                    return@withTimeoutOrNull false
                                                }

                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: return@withTimeoutOrNull false
                                                if (!change.pressed) {
                                                    return@withTimeoutOrNull false
                                                }

                                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                                    return@withTimeoutOrNull false
                                                }
                                            }
                                        } == null

                                        if (!longPressReached) return@awaitEachGesture

                                        showBookTouchIndicator(down.position)
                                        bookMagnifierBaseScale = scale
                                        bookMagnifierBaseOffset = offset
                                        bookMagnifierStartPosition = down.position
                                        suppressTapAfterMagnifier = true
                                        isBookMagnifierActive = true

                                        val targetScale = (bookMagnifierBaseScale * (viewerSettings.magnifierScalePercent / 100f)).coerceIn(1f, 3f)
                                        fun focusBookMagnifierAt(position: Offset) {
                                            val scaleRatio = targetScale / bookMagnifierBaseScale.coerceAtLeast(0.01f)
                                            val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
                                            val touchFromCenter = position - center
                                            bookMagnifierFocusOffset = Offset(
                                                x = touchFromCenter.x * (1f - scaleRatio) + bookMagnifierBaseOffset.x * scaleRatio,
                                                y = touchFromCenter.y * (1f - scaleRatio) + bookMagnifierBaseOffset.y * scaleRatio
                                            )
                                            scale = targetScale
                                            offset = bookMagnifierFocusOffset
                                        }

                                        try {
                                            focusBookMagnifierAt(down.position)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                                if (!change.pressed) break
                                                focusBookMagnifierAt(change.position)
                                                change.consume()
                                            }
                                        } finally {
                                            isBookMagnifierActive = false
                                            scale = bookMagnifierBaseScale
                                            offset = bookMagnifierBaseOffset
                                        }
                                    }
                                }
                                .pointerInput(zoneCount, viewerSettings.doubleTapFastZoom, viewerSettings.longPressMagnifier, viewerSettings.magnifierScalePercent) {
                                    detectTapGestures(
                                        onPress = {
                                            showBookTouchIndicator(it)
                                            tryAwaitRelease()
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
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(scale) {
                                    detectTapGestures(onTap = {
                                        showBookTouchIndicator(it)
                                        scale = 1f
                                        offset = Offset.Zero
                                        isBookMagnifierActive = false
                                    })
                                }
                        )
                    }
                }
            }
        }
        }

        if (viewerSettings.touchIndicator) {
            val zoneCount = viewerSettings.tapZoneCount.coerceIn(3, 11)
            TapZoneGuideOverlay(
                labels = bookTapZoneGuideLabels(bookTouchAssignments),
                vertical = viewerSettings.scrollMode == BookScrollMode.VERTICAL,
                modifier = Modifier.matchParentSize()
            )
        }

        if (isUiVisible) {
            val menuAlpha = (1f - viewerSettings.menuOpacityPercent / 100f).coerceIn(0f, 1f)
            Surface(color = Color.Black, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().graphicsLayer { alpha = menuAlpha }) {
                Box(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .height(dimensionResource(R.dimen.viewer_top_bar_height))
                        .padding(horizontal = dimensionResource(R.dimen.spacing_small))
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    Text(
                        book.title,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = dimensionResource(R.dimen.book_viewer_title_padding_start), end = dimensionResource(R.dimen.book_viewer_title_padding_end)),
                        maxLines = 1,
                        fontSize = textSizes.subtitle
                    )
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { showBookTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.book_menu), tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showBookTopMenu,
                            onDismissRequest = { showBookTopMenu = false },
                            containerColor = GalleryThemeTokens.colors.card
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_action_favorite), color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        if (isFavoriteBook) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        null,
                                        tint = if (isFavoriteBook) GalleryThemeTokens.colors.danger else Color.White
                                    )
                                },
                                onClick = {
                                    isFavoriteBook = !isFavoriteBook
                                    bookFavoritesPrefs.edit().putBoolean(book.id, isFavoriteBook).apply()
                                    showBookTopMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_action_settings), color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Settings, null, tint = Color.White) },
                                onClick = { showBookTopMenu = false; onOpenBookSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_action_rotate), color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) },
                                onClick = {
                                    screenOrientationOverride =
                                        if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    showBookTopMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    val label = if (isSlideshowRunning) stringResource(R.string.viewer_slideshow_stop) else stringResource(R.string.viewer_slideshow_start)
                                    Text(label, color = Color.White)
                                },
                                leadingIcon = { Icon(if (isSlideshowRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White) },
                                onClick = {
                                    isSlideshowRunning = !isSlideshowRunning
                                    isUiVisible = !isSlideshowRunning
                                    showBookTopMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_action_prev_book), color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) },
                                enabled = onPreviousBook != null,
                                onClick = { showBookTopMenu = false; onPreviousBook?.invoke() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_action_next_book), color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.SkipNext, null, tint = Color.White) },
                                enabled = onNextBook != null,
                                onClick = { showBookTopMenu = false; onNextBook?.invoke() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isBookmarked) stringResource(R.string.book_bookmark_removed) else stringResource(R.string.label_action_bookmark), color = Color.White) },
                                leadingIcon = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, tint = Color.White) },
                                onClick = {
                                    if (isBookmarked) {
                                        bookmarksPrefs.edit().remove(book.id).apply()
                                        isBookmarked = false
                                        Toast.makeText(context, context.getString(R.string.book_bookmark_removed), Toast.LENGTH_SHORT).show()
                                    } else {
                                        val data = JSONObject().put("title", book.title).put("page", currentAbsolutePage).toString()
                                        bookmarksPrefs.edit().putString(book.id, data).apply()
                                        isBookmarked = true
                                        Toast.makeText(context, context.getString(R.string.book_bookmark_added), Toast.LENGTH_SHORT).show()
                                    }
                                    onBookmarksChanged()
                                    showBookTopMenu = false
                                }
                            )
                        }
                    }
                }
            }
            Surface(color = Color.Black, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha = menuAlpha }) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = dimensionResource(R.dimen.spacing_medium), vertical = dimensionResource(R.dimen.spacing_small))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentAbsolutePage + 1} / ${book.pageCount}", color = Color.White, fontSize = textSizes.small)
                        GalleryVideoSeekBar(
                            positionMs = seekValue.roundToInt().toLong(),
                            durationMs = (book.pageCount - 1).coerceAtLeast(1).toLong(),
                            onSeekStart = { 
                                isSeeking = true 
                                Log.d("BookViewer", "Seek started at page $currentAbsolutePage")
                                if (viewerSettings.seekAnchorsEnabled) {
                                    val startPage = currentAbsolutePage
                                    if (startPage !in seekAnchors) {
                                        Log.d("BookViewer", "Adding anchor at $startPage")
                                        seekAnchors.add(0, startPage)
                                        while (seekAnchors.size > viewerSettings.maxSeekAnchors) {
                                            seekAnchors.removeAt(seekAnchors.size - 1)
                                        }
                                    }
                                }
                            },
                            onSeek = {
                                Log.d("BookViewer", "Seeking to $it")
                                seekValue = it.toFloat().coerceIn(0f, (book.pageCount - 1).toFloat())
                            },
                            onSeekEnd = {
                                Log.d("BookViewer", "Seek ended at ${seekValueToPage(seekValue)}")
                                isSeeking = false
                                val target = seekValueToPage(seekValue)
                                scope.launch { pagerState.scrollToPage(if (isTwoPageMode) target / 2 else target) }
                            },
                            modifier = Modifier.weight(1f).height(dimensionResource(R.dimen.viewer_seek_bar_height)).padding(horizontal = dimensionResource(R.dimen.spacing_base)),
                            trackColor = Color.White.copy(alpha = 0.10f),
                            progressColor = Color.White.copy(alpha = 0.24f),
                            thumbColor = Color.White.copy(alpha = 0.72f),
                            trackHeight = 1.dp,
                            thumbSize = dimensionResource(R.dimen.spacing_small),
                            reverseLayout = isRightToLeft,
                            anchors = if (viewerSettings.seekAnchorsEnabled) seekAnchors.map { it.toLong() } else emptyList()
                        )
                    }

                    val barSlots = listOf("ボトム左", "ボトム中央左", "ボトム中央", "ボトム中央右", "ボトム右")
                    val legacyBarSlots = listOf("位置 1", "位置 2", "位置 3", "位置 4", "位置 5")
                    val actionClose = stringResource(R.string.label_action_close)
                    val actionSettings = stringResource(R.string.label_action_settings)
                    val actionRotate = stringResource(R.string.label_action_rotate)
                    val actionScreenshot = stringResource(R.string.label_action_screenshot)
                    val actionBookmark = stringResource(R.string.label_action_bookmark)
                    val actionPrev = stringResource(R.string.label_action_prev)
                    val actionNext = stringResource(R.string.label_action_next)
                    val labelNone = stringResource(R.string.label_action_none)
                    val action3dot = stringResource(R.string.label_3dot_menu)

                    val bookActionCatalog = remember(actionClose, actionSettings, actionRotate, actionScreenshot, actionBookmark, actionPrev, actionNext) {
                        listOf(actionClose, actionSettings, actionBookmark, actionRotate, actionScreenshot, actionPrev, actionNext)
                    }
                    val defaultBarAssignments = remember(actionClose, actionPrev, actionBookmark, actionNext, action3dot) {
                        listOf(actionClose, actionPrev, actionBookmark, actionNext, action3dot)
                    }
                    val barAssignments = remember(preferences) {
                        val saved = barSlots.mapIndexed { index, slot ->
                            preferences.getString("book_bar.$slot", null)
                                ?: preferences.getString("book_bar.${legacyBarSlots[index]}", null)
                                ?: labelNone
                        }.filter { it != labelNone }
                        saved.ifEmpty { defaultBarAssignments }
                    }
                    val menuAssignments = remember(barAssignments) {
                        bookActionCatalog.filterNot { action -> barAssignments.contains(action) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.viewer_bottom_bar_height)),
                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_extra_large), Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        barAssignments.forEach { function ->
                            if (isViewerOverflowActionName(function) || function !in bookActionCatalog) {
                                return@forEach
                            }
                            val action = resolveViewerAction(function, isBookmarked = isBookmarked, isSlideshowRunning = isSlideshowRunning)
                            if (action != null) {
                                IconButton(onClick = {
                                    handleBookViewerAction(
                                        function = function,
                                        context = context,
                                        book = book,
                                        pagerState = pagerState,
                                        isTwoPageMode = isTwoPageMode,
                                        currentAbsolutePage = currentAbsolutePage,
                                        isSlideshowRunning = isSlideshowRunning,
                                        onToggleSlideshow = { isSlideshowRunning = !isSlideshowRunning; isUiVisible = !isSlideshowRunning },
                                        onClose = onClose,
                                        onRotate = { screenOrientationOverride = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE },
                                        onSaveScreenshot = { saveCurrentScreenshot() },
                                        onOpenBookSettings = onOpenBookSettings,
                                        onBookmarksChanged = { isBookmarked = bookmarksPrefs.contains(book.id); onBookmarksChanged() }
                                    )
                                }) {
                                    Icon(action.icon, contentDescription = action.label, tint = action.color ?: Color.White)
                                }
                            }
                        }

                        if (menuAssignments.isNotEmpty() && barAssignments.any(::isViewerOverflowActionName)) {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.book_menu), tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    containerColor = GalleryThemeTokens.colors.card
                                ) {
                                    menuAssignments.forEach { function ->
                                        val action = resolveViewerAction(function, isBookmarked = isBookmarked, isSlideshowRunning = isSlideshowRunning)
                                        if (action != null) {
                                            DropdownMenuItem(
                                                text = { Text(action.label, color = Color.White) },
                                                leadingIcon = { Icon(action.icon, null, tint = action.color ?: Color.White) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    handleBookViewerAction(
                                                        function = function,
                                                        context = context,
                                                        book = book,
                                                        pagerState = pagerState,
                                                        isTwoPageMode = isTwoPageMode,
                                                        currentAbsolutePage = currentAbsolutePage,
                                                        isSlideshowRunning = isSlideshowRunning,
                                                        onToggleSlideshow = { isSlideshowRunning = !isSlideshowRunning; isUiVisible = !isSlideshowRunning },
                                                        onClose = onClose,
                                                        onRotate = { screenOrientationOverride = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE },
                                                        onSaveScreenshot = { saveCurrentScreenshot() },
                                                        onOpenBookSettings = onOpenBookSettings,
                                                        onBookmarksChanged = { isBookmarked = bookmarksPrefs.contains(book.id); onBookmarksChanged() }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isSeeking && previewBitmap != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 160.dp) // Move higher to avoid finger and seek bar overlap
                        .size(dimensionResource(R.dimen.viewer_preview_card_width), dimensionResource(R.dimen.viewer_preview_card_height)),
                    shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium)),
                    elevation = CardDefaults.cardElevation(dimensionResource(R.dimen.spacing_small))
                ) {
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
                stringResource(R.string.book_loading_page, formatBookLoadTime(pageLoadElapsedMs))
            } else {
                val remainingMs = (estimateMs - pageLoadElapsedMs).coerceAtLeast(0L)
                stringResource(R.string.book_loading_page_remaining, formatBookLoadTime(remainingMs))
            }
            OperationProgressIndicator(
                label = progressLabel,
                progress = progress,
                displayMode = progressDisplayMode,
                minimumStyle = progressMiniStyle,
                modifier = Modifier
                    .align(if (progressDisplayMode == "MIN" && progressMiniStyle == "CIRCLE") Alignment.BottomEnd else Alignment.BottomCenter)
                    .padding(
                        start = dimensionResource(R.dimen.spacing_large),
                        end = dimensionResource(R.dimen.spacing_large),
                        bottom = if (isUiVisible) dimensionResource(R.dimen.viewer_bottom_padding_ui) else dimensionResource(R.dimen.viewer_bottom_padding_no_ui)
                    )
            )
        }

        val readProgress = ((currentAbsolutePage + 1).toFloat() / book.pageCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        if (!isUiVisible && viewerSettings.readMark != "NONE") {
            when (viewerSettings.readMark) {
                "ICON" -> Text(
                    text = stringResource(R.string.book_read_mark),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(dimensionResource(R.dimen.spacing_medium)),
                    fontSize = textSizes.tiny
                )
                "PAGE" -> Text(
                    text = "${currentAbsolutePage + 1}/${book.pageCount}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(dimensionResource(R.dimen.spacing_medium)),
                    fontSize = textSizes.tiny
                )
                "PERCENT" -> Text(
                    text = "${(readProgress * 100).toInt()}%",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(dimensionResource(R.dimen.spacing_medium)),
                    fontSize = textSizes.tiny
                )
                "PROGRESS_BAR" -> LinearProgressIndicator(
                    progress = { readProgress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.spacing_tiny))
                )
            }
        }

        if (viewerSettings.touchIndicator && scale > 1.01f) {
            Surface(
                color = Color.Black.copy(alpha = GalleryAlphaTokens.Clock),
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_full)),
                modifier = Modifier.align(Alignment.TopStart).padding(top = if (isUiVisible) dimensionResource(R.dimen.viewer_zoom_indicator_padding_top) else dimensionResource(R.dimen.spacing_medium), start = dimensionResource(R.dimen.spacing_medium))
            ) {
                Text(
                    text = "x${"%.1f".format(Locale.US, scale)}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.popup_padding_h), vertical = dimensionResource(R.dimen.spacing_tiny)),
                    fontSize = textSizes.tiny
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
                color = Color.Black.copy(alpha = GalleryAlphaTokens.Clock),
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_full)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (isUiVisible) dimensionResource(R.dimen.viewer_clock_battery_padding_top) else dimensionResource(R.dimen.spacing_medium), end = dimensionResource(R.dimen.spacing_medium))
            ) {
                Text(
                    text = clockText,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.popup_padding_h), vertical = dimensionResource(R.dimen.spacing_tiny)),
                    fontSize = textSizes.tiny
                )
            }
        }

        // Removed touch circle indicator as per request
    }

}


private fun openBookTitleSearch(context: Context, title: String) {
    val query = title.substringBeforeLast('.').ifBlank { title }
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
    )
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, context.getString(R.string.book_error_browser), Toast.LENGTH_SHORT).show() }
}

private fun defaultBookTouchAssignments(zoneCount: Int): Map<String, String> = when (zoneCount) {
    11 -> mapOf(
        "top_start" to AppConstants.ACTION_PREV_BOOK,
        "top_center" to AppConstants.ACTION_TOGGLE_UI,
        "top_end" to AppConstants.ACTION_NEXT_BOOK,
        "left_upper" to AppConstants.ACTION_PREV_PAGE,
        "left_lower" to AppConstants.ACTION_PREV_PAGE,
        "center" to AppConstants.ACTION_ZOOM,
        "right_upper" to AppConstants.ACTION_NEXT_PAGE,
        "right_lower" to AppConstants.ACTION_NEXT_PAGE,
        "bottom_start" to AppConstants.ACTION_SETTINGS,
        "bottom_center" to AppConstants.ACTION_TOGGLE_UI,
        "bottom_end" to AppConstants.ACTION_NEXT_BOOK
    )
    7 -> mapOf(
        "top_start" to AppConstants.ACTION_PREV_BOOK,
        "top_end" to AppConstants.ACTION_NEXT_BOOK,
        "left" to AppConstants.ACTION_PREV_PAGE,
        "center" to AppConstants.ACTION_TOGGLE_UI,
        "right" to AppConstants.ACTION_NEXT_PAGE,
        "bottom_start" to AppConstants.ACTION_ZOOM,
        "bottom_end" to AppConstants.ACTION_SETTINGS
    )
    5 -> mapOf(
        "top" to AppConstants.ACTION_PREV_PAGE,
        "left" to AppConstants.ACTION_PREV_BOOK,
        "center" to AppConstants.ACTION_TOGGLE_UI,
        "right" to AppConstants.ACTION_NEXT_BOOK,
        "bottom" to AppConstants.ACTION_NEXT_PAGE
    )
    4 -> mapOf(
        "left" to AppConstants.ACTION_PREV_PAGE,
        "center" to AppConstants.ACTION_ZOOM,
        "bottom" to AppConstants.ACTION_SETTINGS,
        "right" to AppConstants.ACTION_NEXT_PAGE
    )
    else -> mapOf(
        "left" to AppConstants.ACTION_PREV_PAGE,
        "center" to AppConstants.ACTION_TOGGLE_UI,
        "right" to AppConstants.ACTION_NEXT_PAGE
    )
}

@Composable
private fun bookTapZoneGuideLabels(assignments: List<String>): List<String> =
    assignments.map { resolveViewerActionLabel(it) }

private fun loadBookViewerSettings(context: Context, preferences: android.content.SharedPreferences): BookViewerSettings {
    val globalPrefs = context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)

    fun <T : Enum<T>> enumValue(key: String, default: T, values: Array<T>, prefs: android.content.SharedPreferences = preferences): T {
        val stored = prefs.getString(key, default.name)
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
        val intervalMs = globalPrefs.getInt("slideshowIntervalMs", preferences.getInt("slideshowIntervalMs", -1))
        return if (intervalMs > 0) (intervalMs / 1000).coerceIn(1, 30) else preferences.getInt("slideshowSeconds", 5).coerceIn(1, 30)
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
        smoothFilter = enumValue("smoothing", BookSmoothFilter.BILINEAR, BookSmoothFilter.entries.toTypedArray(), globalPrefs),
        colorMode = enumValue("colorMode", BookColorMode.NORMAL, BookColorMode.entries.toTypedArray()),
        pageGapDp = preferences.getInt("pageGapDp", 0).coerceIn(0, 24),
        preloadPages = preferences.getInt("preloadPages", 1).coerceIn(0, 5),
        fixedSizePercent = preferences.getInt("fixedSizePercent", 100).coerceIn(50, 200),
        brightness = preferences.getInt("brightness", 0).coerceIn(-50, 50),
        contrast = preferences.getInt("contrast", 0).coerceIn(-50, 50),
        gamma = preferences.getInt("gamma", 100).coerceIn(50, 200),
        tapZoneCount = tapZoneCountForLayout(
            preferences.getString("tapZoneLayout", globalPrefs.getString("tapZoneLayout", "THREE")),
            preferences.getInt("tapZoneCount", 3)
        ),
        slideshowSeconds = slideshowSecondsValue(),
        autoTrimWhiteBorder = preferences.getBoolean("autoTrimWhiteBorder", false),
        cacheAroundPages = preferences.getBoolean("imageCache", preferences.getBoolean("cacheAroundPages", true)),
        balloonMagnifier = globalPrefs.getBoolean("longPressMagnifier", preferences.getBoolean("balloonMagnifier", false)),
        tapNavigation = preferences.getBoolean("tapNavigation", true),
        keepScreenOn = preferences.getBoolean("keepScreenOn", true),
        fullscreenMode = enumValue("fullscreenMode", BookFullscreenMode.FULLSCREEN, BookFullscreenMode.entries.toTypedArray(), globalPrefs),
        orientationMode = enumValue("orientation", BookOrientationMode.AUTO, BookOrientationMode.entries.toTypedArray(), globalPrefs),
        transitionEffect = enumValue("transitionEffect", BookTransitionEffect.SLIDE_HORIZONTAL, BookTransitionEffect.entries.toTypedArray()),
        transitionSpeedMs = globalPrefs.getInt("transitionSpeedMs", preferences.getInt("transitionSpeedMs", 250)).coerceIn(0, 2000),
        slideshowSpeedMs = globalPrefs.getInt("slideshowSpeedMs", preferences.getInt("slideshowSpeedMs", 250)).coerceIn(0, 2000),
        menuOpacityPercent = globalPrefs.getInt("menuOpacityPercent", preferences.getInt("menuOpacityPercent", 0)).coerceIn(0, 100),
        longPressMagnifier = globalPrefs.getBoolean("longPressMagnifier", false),
        doubleTapFastZoom = globalPrefs.getBoolean("doubleTapFastZoom", true),
        magnifierScalePercent = globalPrefs.getInt("magnifierScalePercent", preferences.getInt("magnifierScalePercent", 200)).coerceIn(100, 300),
        readMark = preferences.getString("readMark", "NONE") ?: "NONE",
        seekAnchorsEnabled = preferences.getBoolean("seekAnchorsEnabled", true),
        maxSeekAnchors = preferences.getInt("maxSeekAnchors", 3).coerceIn(1, 5),
        touchIndicator = preferences.getBoolean("touchIndicator", globalPrefs.getBoolean("touchIndicator", false)),
        showClockBattery = globalPrefs.getBoolean("showClockBattery", preferences.getBoolean("showClockBattery", false))
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
        .putBoolean("seekAnchorsEnabled", settings.seekAnchorsEnabled)
        .putInt("maxSeekAnchors", settings.maxSeekAnchors)
        .putBoolean("touchIndicator", settings.touchIndicator)
        .putBoolean("showClockBattery", settings.showClockBattery)
        .apply()
}

@Composable
private fun formatBookLoadTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000f
    return if (seconds < 10f) stringResource(R.string.unit_seconds_float, seconds) else stringResource(R.string.unit_seconds, seconds.toInt())
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
            Toast.makeText(context, context.getString(R.string.book_saved), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) { Toast.makeText(context, context.getString(R.string.msg_save_failed), Toast.LENGTH_SHORT).show() }
}

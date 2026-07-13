package com.example.gallery.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.window.Popup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.imageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.provider.MediaStore
import android.content.ContentValues
import android.app.WallpaperManager
import android.media.MediaMetadataRetriever
import android.widget.Toast
import android.content.Context
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.AppRoutes
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.AgeRatingFilter
import com.example.gallery.service.TagTranslationService
import com.example.gallery.ui.component.GalleryFloatingActionButton
import com.example.gallery.ui.component.GalleryVideoSeekBar
import com.example.gallery.ui.component.OperationProgressIndicator
import com.example.gallery.ui.component.ViewerControlBar
import com.example.gallery.ui.component.resolveViewerAction
import com.example.gallery.ui.component.TapZoneGuideOverlay
import com.example.gallery.ui.component.tapZoneCountForLayout
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.ui.theme.GalleryThemeTokens

private fun handleViewerAction(
    function: String,
    context: Context,
    currentMediaItem: MediaData,
    galleryState: GalleryState?,
    scope: kotlinx.coroutines.CoroutineScope,
    videoPosition: Long,
    isFrameSteppingVisible: Boolean,
    gifFrames: List<Bitmap>,
    onClickedClose: () -> Unit,
    onRotate: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleGifStepping: () -> Unit,
    onShowTagDialog: () -> Unit,
    onSearchAscii2d: () -> Unit
) {
    when (function) {
        context.getString(R.string.label_action_trash) -> galleryState?.let { state ->
            scope.launch {
                state.repository.moveToTrash(listOf(currentMediaItem.uri))
                Toast.makeText(context, context.getString(R.string.msg_deleted_count, 1), Toast.LENGTH_SHORT).show()
                onClickedClose()
            }
        }
        context.getString(R.string.label_action_close) -> onClickedClose()
        context.getString(R.string.label_action_settings) -> galleryState?.navController?.navigate(AppRoutes.MEDIA_VIEWER_SETTINGS)
        context.getString(R.string.label_action_rotate) -> onRotate()
        context.getString(R.string.label_action_screenshot) -> {
            if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) saveBitmapToScreenshots(context, gifFrames[0])
            else if (currentMediaItem.isVideo) captureVideoFrame(context, currentMediaItem.uri, videoPosition) { bitmap -> if (bitmap != null) saveBitmapToScreenshots(context, bitmap) }
            else if (currentMediaItem.isGif) Toast.makeText(context, context.getString(R.string.msg_gif_use_stepping), Toast.LENGTH_SHORT).show()
        }
        context.getString(R.string.label_action_favorite) -> galleryState?.let { scope.launch { it.repository.toggleFavorite(currentMediaItem.uri) } }
        context.getString(R.string.label_action_slideshow) -> onToggleSlideshow()
        context.getString(R.string.label_action_gif) -> onToggleGifStepping()
        context.getString(R.string.label_action_ascii2d) -> onSearchAscii2d()
        context.getString(R.string.label_action_wallpaper) -> {
            if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) setBitmapAsWallpaper(context, gifFrames[0])
            else setAsWallpaper(context, currentMediaItem.uri)
        }
        context.getString(R.string.label_action_folder_thumbnail) -> scope.launch {
            galleryState?.repository?.updateFolderThumbnail(currentMediaItem.folderName, currentMediaItem.uri)
            Toast.makeText(context, context.getString(R.string.msg_set_folder_thumbnail, currentMediaItem.folderName), Toast.LENGTH_SHORT).show()
        }
        context.getString(R.string.label_action_tag) -> onShowTagDialog()
    }
}

private fun isViewerOverflowAction(function: String, context: Context): Boolean {
    return function == context.getString(R.string.label_3dot_menu) || function.contains("3")
}

private fun isMediaViewerActionAvailable(function: String, mediaItem: MediaData, context: Context): Boolean {
    return when (function) {
        context.getString(R.string.label_action_gif) -> mediaItem.isGif
        context.getString(R.string.label_action_ascii2d) -> !mediaItem.isVideo
        else -> true
    }
}

private const val VIDEO_VIEWER_TRACE = "GALLERY_VIDEO_VIEWER_TRACE"

private fun logVideoViewerTrace(message: String) {
    Log.d(VIDEO_VIEWER_TRACE, "$VIDEO_VIEWER_TRACE $message")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    imageList: List<MediaData>,
    initialPage: Int,
    onClickedClose: () -> Unit,
    modifier: Modifier = Modifier,
    galleryState: GalleryState? = null,
    onNavigateToMedia: ((String) -> Unit)? = null,
    onNavigateToTag: ((String) -> Unit)? = null,
    onPageSelected: ((Int) -> Unit)? = null,
    showDeleteButton: Boolean = true,
    isTrashMode: Boolean = false,
    keepNavigationBarsHidden: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { imageList.size })
    val globalSettingsPrefs = remember { context.getSharedPreferences("global_settings", android.content.Context.MODE_PRIVATE) }
    val doubleTapFastZoom = globalSettingsPrefs.getBoolean("doubleTapFastZoom", true)
    val slideshowIntervalMs = remember { globalSettingsPrefs.getInt("slideshowIntervalMs", 5000) }
    val slideshowSpeedMs = remember { globalSettingsPrefs.getInt("slideshowSpeedMs", 250).coerceIn(0, 2000) }
    val randomPlayback = remember { globalSettingsPrefs.getBoolean("randomPlayback", false) }
    val continuousPlayback = remember { globalSettingsPrefs.getBoolean("continuousPlayback", true) }
    val menuAlpha = remember { (1f - globalSettingsPrefs.getInt("menuOpacityPercent", 0) / 100f).coerceIn(0f, 1f) }
    val progressDisplayMode = globalSettingsPrefs.getString("progressDisplayMode", "MAX") ?: "MAX"
    val progressMiniStyle = globalSettingsPrefs.getString("progressMiniStyle", "BAR") ?: "BAR"
    val longPressMagnifier = globalSettingsPrefs.getBoolean("longPressMagnifier", false)
    val magnifierScale = (globalSettingsPrefs.getInt("magnifierScalePercent", 200) / 100f).coerceIn(1f, 3f)
    val touchIndicatorEnabled = globalSettingsPrefs.getBoolean("touchIndicator", false)
    val tapZoneLayout = globalSettingsPrefs.getString("tapZoneLayout", "THREE") ?: "THREE"
    val tapZoneCount = tapZoneCountForLayout(tapZoneLayout)
    val showClockBattery = globalSettingsPrefs.getBoolean("showClockBattery", false)
    val fullscreenMode = remember { globalSettingsPrefs.getString("fullscreenMode", "HIDE_STATUS_BAR") ?: "HIDE_STATUS_BAR" }
    val orientationMode = remember { globalSettingsPrefs.getString("orientation", "AUTO") ?: "AUTO" }
    val imageFilterQuality = remember {
        when (globalSettingsPrefs.getString("smoothing", "BILINEAR")) {
            "NEAREST" -> FilterQuality.None
            "AVERAGING" -> FilterQuality.Low
            "BICUBIC", "LANCZOS3" -> FilterQuality.High
            else -> FilterQuality.Medium
        }
    }

    val mediaViewerPrefs = remember { context.getSharedPreferences("media_viewer_settings", android.content.Context.MODE_PRIVATE) }
    val showInfoOverlayEnabled = mediaViewerPrefs.getBoolean("showInfoOverlay", false)
    val loopGif = mediaViewerPrefs.getBoolean("loopGif", true)
    val showFrameBar = mediaViewerPrefs.getBoolean("showFrameBar", false)
    val showRandomRecs = mediaViewerPrefs.getBoolean("showRandomRecs", true)
    val showSimilarRecs = mediaViewerPrefs.getBoolean("showSimilarRecs", true)
    val swipeUpRecs = mediaViewerPrefs.getBoolean("swipeUpRecs", true)
    val swipeDownClose = mediaViewerPrefs.getBoolean("swipeDownClose", true)
    val doubleTapZoomEnabled = mediaViewerPrefs.getBoolean("doubleTapZoom", true)
    val showSystemBarsPref = mediaViewerPrefs.getBoolean("showSystemBars", false)
    val videoSeekIntervalMediaPref = remember { mediaViewerPrefs.getString("seekInterval", "10")?.toIntOrNull() ?: 10 }

    val videoPrefs = remember { context.getSharedPreferences("video_viewer_settings", android.content.Context.MODE_PRIVATE) }
    val videoAutoPlay = remember { videoPrefs.getBoolean("autoPlay", true) }
    val videoLoopPlayback = remember { videoPrefs.getBoolean("loopPlayback", true) }
    val videoDefaultMute = remember { videoPrefs.getBoolean("defaultMute", false) }
    val videoSeekIntervalPref = remember { videoPrefs.getString("seekInterval", "10")?.toIntOrNull() ?: videoSeekIntervalMediaPref }
    val videoResizeMode = remember { videoPrefs.getString("resizeMode", "FIT") ?: "FIT" }

    val thumbnailListState = rememberLazyListState()
    val thumbnailOriginalIndices = remember(imageList) { imageList.indices.toList() }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    var lastInitialPage by rememberSaveable { mutableIntStateOf(initialPage) }
    LaunchedEffect(initialPage) {
        if (initialPage != lastInitialPage && initialPage in 0 until imageList.size) {
            pagerState.scrollToPage(initialPage)
            lastInitialPage = initialPage
        }
    }

    var isUiVisible by rememberSaveable { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    val configuredScreenOrientation = when (orientationMode) {
        "PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        "LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        "REVERSE_PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        "REVERSE_LANDSCAPE" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    var screenOrientation by rememberSaveable { mutableIntStateOf(configuredScreenOrientation) }

    var isRecommendationVisible by rememberSaveable { mutableStateOf(showInfoOverlayEnabled) }
    val recommendationDragOffset = remember { Animatable(0f) }

    val currentMedia = remember(pagerState.currentPage, imageList) { imageList.getOrNull(pagerState.currentPage) }

    val currentMetadata by remember(currentMedia?.uri) {
        galleryState?.repository?.mediaDao?.getMetadataSummaryFlow(currentMedia?.uri ?: "")
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val currentMediaTagsFlow by remember(currentMedia?.uri) {
        galleryState?.repository?.getTagsForMedia(currentMedia?.uri ?: "")
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList<com.example.gallery.data.local.entity.TagEntity>())

    var recommendedMediaByVisual by remember { mutableStateOf<List<com.example.gallery.data.repository.MediaRepository.MediaSimilarity>>(emptyList()) }
    var randomMediaList by remember { mutableStateOf<List<MediaData>>(emptyList()) }

    val deletedUris by (galleryState?.repository?.mediaDao?.getDeletedMetadataSummaryFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList<com.example.gallery.data.local.entity.MediaMetadataSummary>())
    val deletedUriSet = remember(deletedUris) { deletedUris.map { it.uri }.toSet() }

    var isFrameSteppingVisible by remember { mutableStateOf(false) }
    var gifFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var isGifFrameLoading by remember { mutableStateOf(false) }
    var gifFrameProgress by remember { mutableStateOf<Float?>(null) }

    var isVideoPlaying by remember(pagerState.currentPage) { mutableStateOf(videoAutoPlay) }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }
    var videoDuration by remember(pagerState.currentPage) { mutableLongStateOf(0L) }
    var videoPosition by remember(pagerState.currentPage) { mutableLongStateOf(0L) }
    var isMuted by rememberSaveable { mutableStateOf(videoDefaultMute) }
    var isSeeking by remember(pagerState.currentPage) { mutableStateOf(false) }
    var seekTargetPosition by remember(pagerState.currentPage) { mutableLongStateOf(-1L) }

    var isVerticalSwiping by remember { mutableStateOf(false) }
    var isOverflowMenuVisible by remember { mutableStateOf(false) }
    var isSlideshowRunning by rememberSaveable { mutableStateOf(false) }
    var ascii2dUploadData by remember { mutableStateOf<Ascii2dUploadData?>(null) }
    var touchIndicatorPoint by remember { mutableStateOf<Offset?>(null) }
    var touchIndicatorToken by remember { mutableIntStateOf(0) }
    var suppressImageTapAfterMagnifier by remember { mutableStateOf(false) }

    fun showViewerTouchIndicator(position: Offset) {
        if (!touchIndicatorEnabled) return
        touchIndicatorPoint = position
        touchIndicatorToken++
    }

    LaunchedEffect(pagerState.currentPage) {
        val media = imageList.getOrNull(pagerState.currentPage)
        logVideoViewerTrace(
            "viewer_page_changed page=${pagerState.currentPage} total=${imageList.size} " +
                "uriHash=${media?.uri?.hashCode()} isVideo=${media?.isVideo} isGif=${media?.isGif}"
        )
        onPageSelected?.invoke(pagerState.currentPage)
        // ページ切り替え時に詳細パネルを閉じる。
        // recommendationDragOffset.snapTo(0f)

        videoPosition = 0L
        videoDuration = 0L
        isSeeking = false
        seekTargetPosition = -1L
        isVideoPlaying = true
    }

    LaunchedEffect(pagerState.currentPage, thumbnailOriginalIndices) {
        val displayIndex = thumbnailOriginalIndices.indexOf(pagerState.currentPage)
        if (displayIndex >= 0) {
            thumbnailListState.animateScrollToItem(displayIndex)
        }
    }

    LaunchedEffect(isSlideshowRunning, slideshowIntervalMs, slideshowSpeedMs, pagerState.currentPage, isCurrentPageZoomed, currentMedia?.uri) {
        if (!isSlideshowRunning || slideshowIntervalMs <= 0 || isCurrentPageZoomed || currentMedia?.isVideo == true || imageList.size <= 1) return@LaunchedEffect
        delay(slideshowIntervalMs.toLong())
        val target = if (randomPlayback) {
            imageList.indices.filter { it != pagerState.currentPage }.randomOrNull() ?: pagerState.currentPage
        } else {
            val next = pagerState.currentPage + 1
            if (next < imageList.size) next else if (continuousPlayback) 0 else pagerState.currentPage
        }
        if (target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target, animationSpec = tween(slideshowSpeedMs))
        }
    }

    val imageLoader = context.imageLoader
    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    val configuration = LocalConfiguration.current
    LaunchedEffect(isUiVisible, insetsController, keepNavigationBarsHidden, fullscreenMode, screenOrientation, configuration.orientation, showSystemBarsPref) {
        window?.navigationBarColor = android.graphics.Color.TRANSPARENT
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (showSystemBarsPref) {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            val hiddenTypes = when (fullscreenMode) {
                "DISABLED" -> 0
                "FULLSCREEN" -> WindowInsetsCompat.Type.systemBars()
                "HIDE_NAV_BAR" -> WindowInsetsCompat.Type.navigationBars()
                else -> WindowInsetsCompat.Type.statusBars()
            }
            if (hiddenTypes == 0) {
                insetsController?.show(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController?.hide(hiddenTypes)
            }
            if (isUiVisible) {
                insetsController?.show(WindowInsetsCompat.Type.navigationBars())
            } else {
                insetsController?.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    DisposableEffect(Unit) {
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        (context as? Activity)?.requestedOrientation = screenOrientation
        onDispose { (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(isRecommendationVisible, pagerState.currentPage) {
        if (isRecommendationVisible && currentMedia != null) {
            val currentAgeRating = currentMetadata?.ageRating ?: "SFW"

            // Prepare random media from the current context.
            val contextMedia = if (currentMedia.isVideo) {
                imageList.filter { it.isVideo && it.uri != currentMedia.uri }
            } else {
                imageList.filter { !it.isVideo && it.uri != currentMedia.uri }
            }

            if (contextMedia.isNotEmpty()) {
                randomMediaList = contextMedia.shuffled().take(20)
            } else {
                scope.launch {
                    if (currentMedia.isVideo) {
                        val allMedia = galleryState?.repository?.getAllMedia() ?: emptyList()
                        randomMediaList = allMedia.filter { it.isVideo && it.uri != currentMedia.uri }.shuffled().take(20)
                    } else {
                        randomMediaList = galleryState?.repository?.getRandomMediaByAgeRating(20, currentAgeRating) ?: emptyList()
                    }
                }
            }

            // Load AI recommendations for images.
            if (!currentMedia.isVideo) {
                scope.launch {
                    if (currentMetadata?.hasFeatureVector == true) {
                        recommendedMediaByVisual = galleryState?.repository?.findSimilarVisualMedia(currentMedia.uri) ?: emptyList()
                    } else if (galleryState != null) {
                        galleryState.vectorSearchService.analyzeSingle(currentMedia)
                        recommendedMediaByVisual = galleryState.repository.findSimilarVisualMedia(currentMedia.uri)
                    }
                }
            } else {
                recommendedMediaByVisual = emptyList()
            }
        }
    }

    DisposableEffect(galleryState, isRecommendationVisible) {
        if (isRecommendationVisible) {
            galleryState?.isZooming = true
        }
        onDispose {
            if (isRecommendationVisible) {
                galleryState?.isZooming = false
            }
        }
    }

    val onShowSimilarity: (MediaData) -> Unit = {
        isRecommendationVisible = true
        scope.launch { recommendationDragOffset.snapTo(0f) }
    }

    DisposableEffect(galleryState) {
        galleryState?.isMediaViewerOpen = true
        onDispose {
            galleryState?.isMediaViewerOpen = false
        }
    }

    var controlInteractionToken by remember { mutableIntStateOf(0) }
    val onToggle = {
        if (!isRecommendationVisible) {
            isUiVisible = !isUiVisible
            controlInteractionToken++
        }
    }

    LaunchedEffect(touchIndicatorToken) {
        if (touchIndicatorPoint != null) {
            delay(450)
            touchIndicatorPoint = null
        }
    }

    val density = LocalDensity.current
    val colors = GalleryThemeTokens.colors
    val maxRecDrag = with(density) { 360.dp.toPx() } // 600dp -> 360dp（約60%に調整）

    val textSizes = GalleryThemeTokens.textSizes
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPageZoomed && !isVerticalSwiping,
            beyondViewportPageCount = 1,
        ) { page ->
            val mediaItem = imageList[page]
            val scale = remember { Animatable(1f) }
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }
            var magnifierBaseScale by remember(page) { mutableFloatStateOf(1f) }
            var magnifierBaseOffsetX by remember(page) { mutableFloatStateOf(0f) }
            var magnifierBaseOffsetY by remember(page) { mutableFloatStateOf(0f) }
            var magnifierFocusOffsetX by remember(page) { mutableFloatStateOf(0f) }
            var magnifierFocusOffsetY by remember(page) { mutableFloatStateOf(0f) }
            var isMagnifierActive by remember(page) { mutableStateOf(false) }
            var magnifierDragOffset by remember(page) { mutableStateOf(Offset.Zero) }

            LaunchedEffect(isRecommendationVisible) {
                if (isRecommendationVisible) {
                    offsetY.animateTo(0f); offsetX.animateTo(0f); scale.animateTo(1f)
                }
            }

            LaunchedEffect(scale.value, pagerState.currentPage) {
                if (pagerState.currentPage == page) isCurrentPageZoomed = scale.value > 1.01f
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                fun clampOffset(scaleValue: Float, rawX: Float, rawY: Float, contentSize: Size? = null): Offset {
                    return clampViewerPanOffset(
                        scale = scaleValue,
                        rawOffsetX = rawX,
                        rawOffsetY = rawY,
                        viewportWidth = viewportWidthPx,
                        viewportHeight = viewportHeightPx,
                        contentSize = contentSize
                    )
                }

                if (mediaItem.isVideo) {
                    VideoPlayer(
                        uri = mediaItem.uri, isUiVisible = isUiVisible,
                        isPlaying = isVideoPlaying && pagerState.currentPage == page,
                        isMuted = isMuted, seekToPosition = if (isSeeking) seekTargetPosition else -1L,
                        onToggleUi = onToggle,
                        onProgressChanged = { pos, dur -> if (pagerState.currentPage == page && !isSeeking) { videoPosition = pos; videoDuration = dur } },
                        scale = scale.value, offsetX = offsetX.value, offsetY = offsetY.value,
                        loopPlayback = videoLoopPlayback,
                        resizeMode = videoResizeMode,
                        onZoomPan = { z, p ->
                            scope.launch {
                                if (isMagnifierActive) {
                                    magnifierDragOffset += p
                                    val targetScale = (magnifierBaseScale * magnifierScale).coerceIn(1f, 5f)
                                    val clamped = clampOffset(
                                        targetScale,
                                        magnifierBaseOffsetX + magnifierDragOffset.x * targetScale,
                                        magnifierBaseOffsetY + magnifierDragOffset.y * targetScale
                                    )
                                    offsetX.snapTo(clamped.x)
                                    offsetY.snapTo(clamped.y)
                                    return@launch
                                }
                                val newScale = (scale.value * z).coerceIn(1f, 5f)
                                scale.snapTo(newScale)
                                val clamped = clampOffset(
                                    newScale,
                                    if (newScale > 1.05f) offsetX.value + p.x * newScale else 0f,
                                    if (newScale > 1.05f) offsetY.value + p.y * newScale else 0f
                                )
                                offsetX.snapTo(clamped.x)
                                offsetY.snapTo(clamped.y)
                            }
                        },
                        onVerticalDrag = { drag ->
                            isVerticalSwiping = true
                            scope.launch {
                                if (swipeUpRecs && isRecommendationVisible) {
                                    val currentRecOffset = recommendationDragOffset.value
                                    recommendationDragOffset.snapTo((currentRecOffset + drag).coerceAtLeast(0f))
                                } else if (swipeUpRecs && !isRecommendationVisible) {
                                    val newOffset = offsetY.value + drag
                                    if (newOffset < -20f) {
                                        scope.launch { recommendationDragOffset.snapTo(0f) }
                                        isRecommendationVisible = true
                                    } else {
                                        offsetY.snapTo(newOffset)
                                    }
                                } else {
                                    offsetY.snapTo(offsetY.value + drag)
                                }
                            }
                        },
                        onSeek = { targetPos ->
                            if (targetPos != videoPosition) {
                                videoPosition = targetPos
                                seekTargetPosition = targetPos
                                isSeeking = true
                                isVideoPlaying = false
                            }
                        },
                        onDragEnd = {
                            isVerticalSwiping = false
                            if (isSeeking) scope.launch { delay(50); isSeeking = false; seekTargetPosition = -1L }

                            if (isRecommendationVisible) {
                                if (recommendationDragOffset.value > 100f) {
                                    scope.launch {
                                        recommendationDragOffset.animateTo(maxRecDrag, tween(200))
                                        isRecommendationVisible = false
                                        delay(200)
                                        recommendationDragOffset.snapTo(0f)
                                    }
                                } else {
                                    scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) }
                                }
                            }

                            if (scale.value < 0.95f) {
                                scope.launch { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } }
                            } else if (scale.value <= 1.05f) {
                                if (swipeDownClose && offsetY.value > 200f) onClickedClose()
                                else scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                        onDoubleTap = {
                            if (doubleTapZoomEnabled && doubleTapFastZoom) {
                                val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                                scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
                            }
                        },
                        isScrollInProgress = pagerState.isScrollInProgress,
                        isSeeking = isSeeking,
                        onTouchIndicator = ::showViewerTouchIndicator,
                        modifier = Modifier.fillMaxSize(),
                        videoPosition = videoPosition,
                        videoDuration = videoDuration
                    )
                } else if (mediaItem.isGif) {
                    GifPlayer(
                        uri = mediaItem.uri, isUiVisible = isUiVisible, onToggleUi = onToggle,
                        scale = scale.value, offsetX = offsetX.value, offsetY = offsetY.value,
                        onZoomPan = { z, p ->
                            scope.launch {
                                val newScale = (scale.value * z).coerceIn(1f, 5f)
                                scale.snapTo(newScale)
                                val clamped = clampOffset(
                                    newScale,
                                    if (newScale > 1.05f) offsetX.value + p.x * newScale else 0f,
                                    if (newScale > 1.05f) offsetY.value + p.y * newScale else 0f
                                )
                                offsetX.snapTo(clamped.x)
                                offsetY.snapTo(clamped.y)
                            }
                        },
                        onVerticalDrag = { drag: Float ->
                            isVerticalSwiping = true
                            scope.launch {
                                if (swipeUpRecs && isRecommendationVisible) {
                                    val currentRecOffset = recommendationDragOffset.value
                                    recommendationDragOffset.snapTo((currentRecOffset + drag).coerceAtLeast(0f))
                                } else if (swipeUpRecs && !isRecommendationVisible) {
                                    val newOffset = offsetY.value + drag
                                    if (newOffset < -20f) {
                                        scope.launch { recommendationDragOffset.snapTo(0f) }
                                        isRecommendationVisible = true
                                    } else {
                                        offsetY.snapTo(newOffset)
                                    }
                                } else {
                                    offsetY.snapTo(offsetY.value + drag)
                                }
                            }
                        },
                        onDragEnd = {
                            isVerticalSwiping = false
                            if (isRecommendationVisible) {
                                if (recommendationDragOffset.value > 100f) {
                                    scope.launch {
                                        recommendationDragOffset.animateTo(maxRecDrag, tween(200))
                                        isRecommendationVisible = false
                                        delay(200)
                                        recommendationDragOffset.snapTo(0f)
                                    }
                                } else {
                                    scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) }
                                }
                            }

                            if (scale.value < 0.95f) {
                                scope.launch { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } }
                            } else {
                                if (offsetY.value > 200f) onClickedClose()
                                else scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                        onDoubleTap = {
                            if (doubleTapFastZoom) {
                                val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                                scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
                            }
                        },
                        onLongPressStart = {
                            if (longPressMagnifier) {
                                magnifierBaseScale = scale.value
                                magnifierBaseOffsetX = offsetX.value
                                magnifierBaseOffsetY = offsetY.value
                                magnifierDragOffset = Offset.Zero
                                isMagnifierActive = true
                                scope.launch {
                                    scale.animateTo((magnifierBaseScale * magnifierScale).coerceIn(1f, 5f))
                                }
                            }
                        },
                        onLongPressEnd = {
                            isMagnifierActive = false
                            scope.launch {
                                scale.animateTo(magnifierBaseScale)
                                offsetX.animateTo(magnifierBaseOffsetX)
                                offsetY.animateTo(magnifierBaseOffsetY)
                            }
                        },
                        onTouchIndicator = ::showViewerTouchIndicator,
                        imageLoader = imageLoader, isFrameSteppingVisible = isFrameSteppingVisible,
                        gifFrames = gifFrames, currentFrameIndex = currentFrameIndex,
                        longPressEnabled = longPressMagnifier,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val finalUri = mediaItem.uri
                    val imagePainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context).data(finalUri).build(),
                        imageLoader = imageLoader
                    )
                    val fittedImageSize = fitContentInsideViewport(
                        intrinsicSize = imagePainter.intrinsicSize,
                        viewportWidth = viewportWidthPx,
                        viewportHeight = viewportHeightPx
                    )
                    Image(
                        painter = imagePainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationX = offsetX.value; translationY = offsetY.value }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    var accumulatedPan = Offset.Zero
                                    var isTransforming = false
                                    var verticalSwipeStarted = false
                                    var longPressCancelReason = ""
                                    val longPressReached = if (longPressMagnifier) {
                                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.count { it.pressed } > 1) {
                                                    longPressCancelReason = "multi_touch"
                                                    return@withTimeoutOrNull false
                                                }

                                                val change = event.changes.firstOrNull { it.id == firstDown.id }
                                                    ?: return@withTimeoutOrNull false
                                                if (!change.pressed) {
                                                    longPressCancelReason = "release"
                                                    return@withTimeoutOrNull false
                                                }

                                                if ((change.position - firstDown.position).getDistance() > viewConfiguration.touchSlop) {
                                                    longPressCancelReason = "move"
                                                    return@withTimeoutOrNull false
                                                }
                                            }
                                        } == null
                                    } else {
                                        false
                                    }

                                    if (longPressReached) {
                                        showViewerTouchIndicator(firstDown.position)
                                        magnifierBaseScale = scale.value
                                        magnifierBaseOffsetX = offsetX.value
                                        magnifierBaseOffsetY = offsetY.value
                                        magnifierDragOffset = Offset.Zero
                                        suppressImageTapAfterMagnifier = true
                                        isMagnifierActive = true

                                        val targetScale = (magnifierBaseScale * magnifierScale).coerceIn(1f, 5f)
                                        var magnifierUpdateJob: Job? = null
                                        fun focusMagnifierAt(position: Offset) {
                                            val scaleRatio = targetScale / magnifierBaseScale.coerceAtLeast(0.01f)
                                            val touchFromCenter = Offset(
                                                x = position.x - viewportWidthPx / 2f,
                                                y = position.y - viewportHeightPx / 2f
                                            )
                                            val focused = clampOffset(
                                                targetScale,
                                                touchFromCenter.x * (1f - scaleRatio) + magnifierBaseOffsetX * scaleRatio,
                                                touchFromCenter.y * (1f - scaleRatio) + magnifierBaseOffsetY * scaleRatio,
                                                fittedImageSize
                                            )
                                            magnifierFocusOffsetX = focused.x
                                            magnifierFocusOffsetY = focused.y
                                            magnifierUpdateJob?.cancel()
                                            magnifierUpdateJob = scope.launch {
                                                scale.snapTo(targetScale)
                                                offsetX.snapTo(focused.x)
                                                offsetY.snapTo(focused.y)
                                            }
                                        }

                                        try {
                                            focusMagnifierAt(firstDown.position)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == firstDown.id }
                                                    ?: break
                                                if (!change.pressed) break
                                                focusMagnifierAt(change.position)
                                                change.consume()
                                            }
                                        } finally {
                                            magnifierUpdateJob?.cancel()
                                            isMagnifierActive = false
                                            scope.launch {
                                                scale.animateTo(magnifierBaseScale)
                                                offsetX.animateTo(magnifierBaseOffsetX)
                                                offsetY.animateTo(magnifierBaseOffsetY)
                                            }
                                        }
                                        return@awaitEachGesture
                                    }

                                    if (longPressCancelReason == "release") {
                                        return@awaitEachGesture
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val currentScale = scale.value
                                        val isZoomed = currentScale > 1.01f
                                        if (isMagnifierActive && event.changes.size == 1 && panChange != Offset.Zero) {
                                            magnifierDragOffset += panChange
                                            scope.launch {
                                                val targetScale = (magnifierBaseScale * magnifierScale).coerceIn(1f, 5f)
                                                val scaleRatio = targetScale / magnifierBaseScale.coerceAtLeast(0.01f)
                                                val currentTouchFromCenter = Offset(
                                                    x = firstDown.position.x + magnifierDragOffset.x - viewportWidthPx / 2f,
                                                    y = firstDown.position.y + magnifierDragOffset.y - viewportHeightPx / 2f
                                                )
                                                val clamped = clampOffset(
                                                    targetScale,
                                                    currentTouchFromCenter.x * (1f - scaleRatio) + magnifierBaseOffsetX * scaleRatio,
                                                    currentTouchFromCenter.y * (1f - scaleRatio) + magnifierBaseOffsetY * scaleRatio,
                                                    fittedImageSize
                                                )
                                                offsetX.snapTo(clamped.x)
                                                offsetY.snapTo(clamped.y)
                                            }
                                            event.changes.forEach { if (it.pressed) it.consume() }
                                            continue
                                        }
                                        if (!verticalSwipeStarted && (event.changes.size >= 2 || isZoomed)) {
                                            accumulatedPan += panChange
                                            val shouldHandleTransform = event.changes.size >= 2 || isTransforming || accumulatedPan.getDistance() > 8f
                                            if (shouldHandleTransform && (zoomChange != 1f || panChange != Offset.Zero)) {
                                                isTransforming = true
                                                scope.launch {
                                                    val newScale = (currentScale * zoomChange).coerceIn(1f, 5f)
                                                    scale.snapTo(newScale)
                                                    val panFactor = if (newScale < 1f) 0.5f else 1f
                                                    val clamped = clampOffset(
                                                        newScale,
                                                        if (newScale > 1.05f) offsetX.value + panChange.x * newScale * panFactor else 0f,
                                                        if (newScale > 1.05f) offsetY.value + panChange.y * newScale * panFactor else 0f,
                                                        fittedImageSize
                                                    )
                                                    offsetX.snapTo(clamped.x)
                                                    offsetY.snapTo(clamped.y)
                                                }
                                                event.changes.forEach { if (it.pressed) it.consume() }
                                            }
                                        } else {
                                            if (panChange != Offset.Zero) {
                                                val isVerticalGesture = Math.abs(panChange.y) > Math.abs(panChange.x) * 3.0f
                                                if (verticalSwipeStarted || isVerticalGesture) {
                                                    if (!verticalSwipeStarted) {
                                                        verticalSwipeStarted = true
                                                        isVerticalSwiping = true
                                                    }
                                                    scope.launch {
                                        if (swipeUpRecs && isRecommendationVisible) {
                                                            val currentRecOffset = recommendationDragOffset.value
                                                            recommendationDragOffset.snapTo((currentRecOffset + panChange.y).coerceAtLeast(0f))
                                                        } else if (swipeUpRecs && !isRecommendationVisible) {
                                                            val newOffset = offsetY.value + panChange.y
                                                            if (newOffset < -20f) {
                                                                scope.launch { recommendationDragOffset.snapTo(0f) }
                                                                isRecommendationVisible = true
                                                            } else {
                                                                offsetY.snapTo(newOffset)
                                                            }
                                                        } else {
                                                            offsetY.snapTo(offsetY.value + panChange.y)
                                                        }
                                                    }
                                                    event.changes.forEach { if (it.pressed) it.consume() }
                                                }
                                            }
                                        }
                                        if (event.changes.all { !it.pressed }) {
                                            if (verticalSwipeStarted) {
                                                isVerticalSwiping = false
                                                if (isRecommendationVisible) {
                                                    if (recommendationDragOffset.value > 100f) {
                                                        scope.launch {
                                                            recommendationDragOffset.animateTo(maxRecDrag, tween(200))
                                                            isRecommendationVisible = false
                                                            delay(200)
                                                            recommendationDragOffset.snapTo(0f)
                                                        }
                                                    } else {
                                                        scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) }
                                                    }
                                                }

                                                if (scale.value <= 1.05f) {
                                                    if (swipeDownClose && offsetY.value > 200f) onClickedClose()
                                                    else scope.launch { offsetY.animateTo(0f) }
                                                }
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    showViewerTouchIndicator(it)
                                    tryAwaitRelease()
                                }, onTap = {
                                    showViewerTouchIndicator(it)
                                    if (suppressImageTapAfterMagnifier) {
                                        suppressImageTapAfterMagnifier = false
                                    } else {
                                        onToggle()
                                    }
                                }, onDoubleTap = {
                                    showViewerTouchIndicator(it)
                                    if (doubleTapZoomEnabled && doubleTapFastZoom) {
                                        val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                                        scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
                                    }
                                })
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        if (isUiVisible && !isCurrentPageZoomed && !isRecommendationVisible) {
            val currentMediaItem = imageList.getOrNull(pagerState.currentPage)
            if (currentMediaItem != null) {
                if (currentMediaItem.isVideo && videoDuration > 0 && !pagerState.isScrollInProgress) {
                    VideoTransportControlRow(
                        currentPosition = videoPosition,
                        duration = videoDuration,
                        isPlaying = isVideoPlaying,
                        onTogglePlay = { isVideoPlaying = !isVideoPlaying },
                        onSeekRequested = { target ->
                            seekTargetPosition = target
                            videoPosition = target
                            isSeeking = true
                            scope.launch { delay(100); isSeeking = false; seekTargetPosition = -1L }
                        },
                        galleryState = galleryState,
                        modifier = Modifier.align(Alignment.Center),
                        defaultSeekInterval = videoSeekIntervalMediaPref
                    )
                }


                LaunchedEffect(showTagDialog) {
                    if (showTagDialog) {
                        delay(100)
                        insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                        insetsController?.show(WindowInsetsCompat.Type.navigationBars())
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = menuAlpha }
                        .background(colors.background)
                        .navigationBarsPadding()
                        .padding(bottom = 2.dp)
                ) {
                    if (currentMediaItem.isVideo && videoDuration > 0 && !pagerState.isScrollInProgress) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}", color = colors.primaryText, fontSize = textSizes.tiny, modifier = Modifier.padding(end = 8.dp))
                                GalleryVideoSeekBar(
                                    positionMs = videoPosition,
                                    durationMs = videoDuration,
                                    onSeekStart = {
                                        if (!isSeeking) {
                                            wasPlayingBeforeSeek = isVideoPlaying
                                        }
                                        isSeeking = true
                                        isVideoPlaying = false
                                    },
                                    onSeek = {
                                        val frameStepMs = 1000L / 30L
                                        val target = (((it + frameStepMs / 2) / frameStepMs) * frameStepMs).coerceIn(0L, videoDuration)
                                        videoPosition = target
                                        seekTargetPosition = target
                                    },
                                    onSeekEnd = {
                                        scope.launch {
                                            delay(50)
                                            isSeeking = false
                                            seekTargetPosition = -1L
                                            if (wasPlayingBeforeSeek) {
                                                isVideoPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(24.dp),
                                    trackColor = colors.primaryText.copy(alpha = 0.3f),
                                    progressColor = colors.primaryText,
                                    thumbColor = colors.primaryText
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                GalleryFloatingActionButton(icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, onClick = { isMuted = !isMuted }, size = 32.dp, iconSize = 18.dp)
                            }
                            if (showFrameBar) {
                                FrameStepControlRow(
                                    currentPosition = videoPosition,
                                    duration = videoDuration,
                                    onFrameStep = { frameDelta ->
                                        val frameStepMs = 1000L / 30L
                                        val target = (videoPosition + frameDelta * frameStepMs).coerceIn(0L, videoDuration)
                                        seekTargetPosition = target
                                        videoPosition = target
                                        isSeeking = true
                                        scope.launch { delay(100); isSeeking = false; seekTargetPosition = -1L }
                                    },
                                    onScrubTo = { target ->
                                        seekTargetPosition = target
                                        videoPosition = target
                                        isSeeking = true
                                    },
                                    onScrubFinished = {
                                        scope.launch { delay(100); isSeeking = false; seekTargetPosition = -1L }
                                    }
                                )
                            }
                        }
                    }

                    if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) {
                        val frameListState = rememberLazyListState()
                        LazyRow(state = frameListState, modifier = Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = (LocalContext.current.resources.displayMetrics.widthPixels / 2 / LocalContext.current.resources.displayMetrics.density).dp - 21.dp)) {
                            itemsIndexed(gifFrames, key = { index, _ -> index }) { index, bitmap ->
                                val isSelected = currentFrameIndex == index
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.3f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) colors.accent else Color.Transparent,
                                            shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small))
                                        )
                                        .clickable {
                                            currentFrameIndex = index; scope.launch {
                                            frameListState.animateScrollToItem(
                                                index
                                            )
                                        }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            state = thumbnailListState,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            itemsIndexed(
                                thumbnailOriginalIndices,
                                key = { _, originalIndex -> imageList.getOrNull(originalIndex)?.uri ?: originalIndex }
                            ) { _, originalIndex ->
                                val mediaItem = imageList[originalIndex]
                                val isSelected = pagerState.currentPage == originalIndex
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small)))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) colors.primaryText else Color.Transparent,
                                            shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small))
                                        )
                                        .clickable { scope.launch { pagerState.scrollToPage(originalIndex) } }) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context).data(mediaItem.uri).apply {
                                                if (mediaItem.isVideo) videoFrameMillis(
                                                    1000
                                                )
                                            }.build(), imageLoader = imageLoader
                                        ), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val barSlots = listOf(
                        stringResource(R.string.label_bottom_left),
                        stringResource(R.string.label_bottom_center_left),
                        stringResource(R.string.label_bottom_center),
                        stringResource(R.string.label_bottom_center_right),
                        stringResource(R.string.label_bottom_right)
                    )
                    val legacyBarSlots = listOf("位置 1", "位置 2", "位置 3", "位置 4", "位置 5")
                    val barPrefs = remember { context.getSharedPreferences("media_viewer_settings", Context.MODE_PRIVATE) }

                    val actionTrash = stringResource(R.string.label_action_trash)
                    val actionSettings = stringResource(R.string.label_action_settings)
                    val actionRotate = stringResource(R.string.label_action_rotate)
                    val actionFavorite = stringResource(R.string.label_action_favorite)
                    val actionSlideshow = stringResource(R.string.label_action_slideshow)
                    val actionGif = stringResource(R.string.label_action_gif)
                    val actionAscii2d = stringResource(R.string.label_action_ascii2d)
                    val actionScreenshot = stringResource(R.string.label_action_screenshot)
                    val actionWallpaper = stringResource(R.string.label_action_wallpaper)
                    val actionFolderThumbnail = stringResource(R.string.label_action_folder_thumbnail)
                    val actionTag = stringResource(R.string.label_action_tag)
                    val action3dot = stringResource(R.string.label_3dot_menu)
                    val labelNone = stringResource(R.string.label_none)

                    val mediaActionCatalog = remember(
                        actionTrash, actionSettings, actionRotate, actionFavorite,
                        actionSlideshow, actionGif, actionAscii2d, actionScreenshot,
                        actionWallpaper, actionFolderThumbnail, actionTag
                    ) {
                        listOf(
                            actionTrash, actionSettings, actionRotate, actionFavorite,
                            actionSlideshow, actionGif, actionAscii2d, actionScreenshot,
                            actionWallpaper, actionFolderThumbnail, actionTag
                        )
                    }
                    val defaultBarAssignments = remember(showDeleteButton, actionTrash, actionSettings, actionRotate, actionFavorite, action3dot) {
                        if (showDeleteButton) listOf(actionTrash, actionSettings, actionRotate, actionFavorite, action3dot)
                        else listOf(actionSettings, actionRotate, actionFavorite, action3dot)
                    }
                            val barAssignments = remember(barPrefs, controlInteractionToken, showDeleteButton, labelNone) {
                                barSlots.mapIndexedNotNull { index, slot ->
                                    val saved = barPrefs.getString("media_bar.$slot", null)
                                        ?: barPrefs.getString("media_bar.${legacyBarSlots[index]}", null)
                                    val fallback = defaultBarAssignments.getOrNull(index)
                                    when {
                                        saved == null -> fallback
                                        saved == labelNone -> null
                                        else -> saved
                                    }
                                }
                            }
                            val menuAssignments = remember(barAssignments, currentMediaItem.uri, currentMediaItem.isGif, currentMediaItem.isVideo) {
                                mediaActionCatalog
                                    .filterNot { action -> barAssignments.contains(action) }
                                    .filter { action -> isMediaViewerActionAvailable(action, currentMediaItem, context) }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val favorites by (galleryState?.repository?.getFavoriteMedia() ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList<MediaData>())
                                val isFavorite = favorites.any { it.uri == currentMediaItem.uri }

                                barAssignments.forEach { function ->
                                    if (isViewerOverflowAction(function, context) || !isMediaViewerActionAvailable(function, currentMediaItem, context)) {
                                        return@forEach
                                    }
                            val action = resolveViewerAction(function, isFavorite = isFavorite, isPlaying = isVideoPlaying, isSlideshowRunning = isSlideshowRunning, isGifStepping = isFrameSteppingVisible)
                            if (action != null) {
                                GalleryFloatingActionButton(
                                    icon = action.icon,
                                    tooltipDescription = action.label,
                                    size = 40.dp,
                                    iconSize = 22.dp,
                                    contentColor = action.color ?: colors.primaryText,
                                    onClick = {
                                        handleViewerAction(
                                            function = function,
                                            context = context,
                                            currentMediaItem = currentMediaItem,
                                            galleryState = galleryState,
                                            scope = scope,
                                            videoPosition = videoPosition,
                                            isFrameSteppingVisible = isFrameSteppingVisible,
                                            gifFrames = gifFrames,
                                            onClickedClose = onClickedClose,
                                            onRotate = {
                                                val target = if (screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                screenOrientation = target
                                                (context as android.app.Activity).requestedOrientation = target
                                            },
                                            onToggleSlideshow = { isSlideshowRunning = !isSlideshowRunning; isUiVisible = !isSlideshowRunning },
                                            onToggleGifStepping = {
                                                if (!isFrameSteppingVisible) {
                                                    isGifFrameLoading = true
                                                    gifFrameProgress = 0f
                                                    extractGifFrames(context, currentMediaItem.uri, scope, onProgress = { gifFrameProgress = it }) { frames ->
                                                        gifFrames = frames
                                                        currentFrameIndex = 0
                                                        isFrameSteppingVisible = frames.isNotEmpty()
                                                        isGifFrameLoading = false
                                                        gifFrameProgress = null
                                                    }
                                                } else {
                                                    isFrameSteppingVisible = false
                                                    gifFrames = emptyList()
                                                    currentFrameIndex = 0
                                                }
                                            },
                                            onShowTagDialog = { showTagDialog = true },
                                            onSearchAscii2d = {
                                                android.widget.Toast.makeText(context, context.getString(R.string.msg_search_ascii2d), android.widget.Toast.LENGTH_SHORT).show()
                                                scope.launch {
                                                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { kotlin.runCatching { prepareAscii2dUploadData(context, currentMediaItem.uri) } }
                                                    result.onSuccess { ascii2dUploadData = it }.onFailure { error -> android.widget.Toast.makeText(context, context.getString(R.string.msg_error_ascii2d, error.message), android.widget.Toast.LENGTH_LONG).show() }
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        if (menuAssignments.isNotEmpty() && barAssignments.any { isViewerOverflowAction(it, context) }) {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                GalleryFloatingActionButton(
                                    icon = Icons.Default.MoreVert,
                                    tooltipDescription = stringResource(R.string.btn_open),
                                    size = 40.dp,
                                    iconSize = 22.dp,
                                    contentColor = colors.primaryText,
                                    onClick = { showMoreMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier.background(GalleryThemeTokens.colors.surfaceVariant)
                                ) {
                                    menuAssignments.forEach { function ->
                                        val action = resolveViewerAction(function, isFavorite = isFavorite, isPlaying = isVideoPlaying, isSlideshowRunning = isSlideshowRunning, isGifStepping = isFrameSteppingVisible)
                                        if (action != null) {
                                            DropdownMenuItem(
                                                text = { Text(action.label, color = colors.primaryText) },
                                                leadingIcon = { Icon(action.icon, null, tint = action.color ?: colors.primaryText) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    handleViewerAction(
                                                        function = function,
                                                        context = context,
                                                        currentMediaItem = currentMediaItem,
                                                        galleryState = galleryState,
                                                        scope = scope,
                                                        videoPosition = videoPosition,
                                                        isFrameSteppingVisible = isFrameSteppingVisible,
                                                        gifFrames = gifFrames,
                                                        onClickedClose = onClickedClose,
                                                        onRotate = {
                                                            val target = if (screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                            screenOrientation = target
                                                            (context as android.app.Activity).requestedOrientation = target
                                                        },
                                                        onToggleSlideshow = { isSlideshowRunning = !isSlideshowRunning; isUiVisible = !isSlideshowRunning },
                                                        onToggleGifStepping = {
                                                            if (!isFrameSteppingVisible) {
                                                                isGifFrameLoading = true
                                                                gifFrameProgress = 0f
                                                                extractGifFrames(context, currentMediaItem.uri, scope, onProgress = { gifFrameProgress = it }) { frames ->
                                                                    gifFrames = frames
                                                                    currentFrameIndex = 0
                                                                    isFrameSteppingVisible = frames.isNotEmpty()
                                                                    isGifFrameLoading = false
                                                                    gifFrameProgress = null
                                                                }
                                                            } else {
                                                                isFrameSteppingVisible = false
                                                                gifFrames = emptyList()
                                                                currentFrameIndex = 0
                                                            }
                                                        },
                                                        onShowTagDialog = { showTagDialog = true },
                                                        onSearchAscii2d = {
                                                            android.widget.Toast.makeText(context, context.getString(R.string.msg_search_ascii2d), android.widget.Toast.LENGTH_SHORT).show()
                                                            scope.launch {
                                                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { kotlin.runCatching { prepareAscii2dUploadData(context, currentMediaItem.uri) } }
                                                                result.onSuccess { ascii2dUploadData = it }.onFailure { error -> android.widget.Toast.makeText(context, context.getString(R.string.msg_error_ascii2d, error.message), android.widget.Toast.LENGTH_LONG).show() }
                                                            }
                                                        }
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
                if (showTagDialog) { galleryState?.let { state -> UnifiedMediaEditDialog(uris = listOf(currentMediaItem.uri), repository = state.repository, onDismiss = { showTagDialog = false }) } }
                ascii2dUploadData?.let { uploadData -> Ascii2dSearchDialog(uploadData = uploadData, onDismiss = { ascii2dUploadData = null }) }
            }
        }

        if (touchIndicatorEnabled) {
            TapZoneGuideOverlay(
                labels = mediaTapZoneGuideLabels(tapZoneCount),
                modifier = Modifier.matchParentSize()
            )
        }

        if (isGifFrameLoading) {
            OperationProgressIndicator(
                label = stringResource(R.string.viewer_extracting_gif),
                progress = gifFrameProgress,
                displayMode = progressDisplayMode,
                minimumStyle = progressMiniStyle,
                modifier = Modifier
                    .align(if (progressDisplayMode == "MIN" && progressMiniStyle == "CIRCLE") Alignment.BottomEnd else Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = if (isUiVisible) 116.dp else 28.dp
                    )
            )
        }

        if (showClockBattery) {
            var clockText by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                while (true) {
                    val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scaleValue = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
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
                    color = colors.primaryText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = textSizes.tiny
                )
            }
        }

        AnimatedVisibility(visible = isRecommendationVisible, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val density = LocalDensity.current
            val maxDrag = with(density) { 360.dp.toPx() }
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.42f).offset { IntOffset(0, recommendationDragOffset.value.roundToInt()) }.background(Color.Black.copy(alpha = 0.95f)).pointerInput(Unit) { detectDragGestures(onDragEnd = { if (recommendationDragOffset.value > 100f) { scope.launch { recommendationDragOffset.animateTo(maxDrag, tween(250)); isRecommendationVisible = false; delay(250); recommendationDragOffset.snapTo(0f) } } else { scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) } } }, onDragCancel = { scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) } }, onDrag = { change, dragAmount -> change.consume(); scope.launch { recommendationDragOffset.snapTo((recommendationDragOffset.value + dragAmount.y).coerceAtLeast(0f)) } }) }) {
                Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) { Surface(modifier = Modifier.width(40.dp).height(4.dp), color = Color.Gray.copy(alpha = 0.5f), shape = CircleShape) {} }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.viewer_details),
                            color = colors.primaryText,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.btn_close),
                            color = colors.secondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { isRecommendationVisible = false }.padding(8.dp)
                        )
                    }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        val currentMediaItem = imageList.getOrNull(pagerState.currentPage)
                        if (currentMediaItem != null) {
                            // Visual recommendations.
                            if (!currentMediaItem.isVideo && !isTrashMode && showSimilarRecs) {
                                item {
                                    Text(
                                        text = stringResource(R.string.viewer_similar_images),
                                        color = colors.primaryText,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                item {
                                    if (currentMetadata?.hasFeatureVector != true) {
                                        Text(
                                            text = stringResource(R.string.viewer_not_analyzed),
                                            color = colors.secondaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    } else if (recommendedMediaByVisual.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.viewer_no_similar),
                                            color = colors.secondaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    } else {
                                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            itemsIndexed(recommendedMediaByVisual) { _, similarity ->
                                                RecommendationCard(mediaItem = similarity.media, score = "${(similarity.similarityScore * 100).toInt()}%", imageLoader = imageLoader, isDeleted = deletedUriSet.contains(similarity.media.uri)) {
                                                    isRecommendationVisible = false
                                                    val index = imageList.indexOfFirst { it.uri == similarity.media.uri }
                                                    if (index != -1) {
                                                        scope.launch {
                                                            pagerState.scrollToPage(index)
                                                            onPageSelected?.invoke(index)
                                                        }
                                                    } else {
                                                        onNavigateToMedia?.invoke(similarity.media.uri)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Random media.
                            if (!isTrashMode && showRandomRecs) {
                                item {
                                    Text(
                                        text = if (currentMediaItem.isVideo) stringResource(R.string.viewer_random_videos) else stringResource(R.string.viewer_random_images),
                                        color = colors.primaryText,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                item {
                                    if (randomMediaList.isEmpty()) {
                                        Text(
                                            text = if (currentMediaItem.isVideo) stringResource(R.string.viewer_no_videos) else stringResource(R.string.viewer_loading),
                                            color = colors.secondaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    } else {
                                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            itemsIndexed(randomMediaList) { _, item ->
                                                RecommendationCard(mediaItem = item, score = null, imageLoader = imageLoader, isDeleted = deletedUriSet.contains(item.uri)) {
                                                    isRecommendationVisible = false
                                                    val index = imageList.indexOfFirst { it.uri == item.uri }
                                                    if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(item.uri)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Tags.
                            item {
                                val tagSuffixGroup = stringResource(R.string.label_tag_suffix_group)
                                val normalTags = remember(currentMediaTagsFlow, tagSuffixGroup) { currentMediaTagsFlow.filter { !it.tag.endsWith(tagSuffixGroup) && it.confidence >= 0.6f }.sortedByDescending { it.confidence } }
                                val currentAgeRating = currentMetadata?.ageRating ?: AppConstants.RATING_SFW

                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.viewer_tags),
                                            color = colors.primaryText,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Surface(
                                            color = when(currentAgeRating) {
                                                AppConstants.RATING_R18 -> colors.danger.copy(alpha = 0.8f)
                                                AppConstants.RATING_R15 -> colors.warning.copy(alpha = 0.8f)
                                                else -> colors.success.copy(alpha = 0.8f)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (isTrashMode) "$currentAgeRating Trash" else currentAgeRating,
                                                color = if (currentAgeRating == AppConstants.RATING_R15) colors.background else colors.primaryText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (!currentMediaItem.isVideo && currentMetadata?.isAiAnalyzed != true) {
                                        Text(
                                            text = stringResource(R.string.viewer_no_tags),
                                            color = colors.secondaryText,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        normalTags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.surfaceVariant)
                                                    .drawWithContent {
                                                        val progressWidth = size.width * tag.confidence
                                                        drawRect(
                                                            color = colors.success.copy(alpha = 0.4f),
                                                            topLeft = Offset(size.width - progressWidth, 0f),
                                                            size = Size(progressWidth, size.height)
                                                        )
                                                        drawContent()
                                                    }
                                                    .clickable { isRecommendationVisible = false; onNavigateToTag?.invoke(tag.tag) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = TagTranslationService.translate(tag.tag),
                                                        color = colors.primaryText,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (tag.confidence > 0f && tag.confidence < 1f) {
                                                        Text(
                                                            text = " ${(tag.confidence * 100).toInt()}%",
                                                            color = colors.secondaryText,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(start = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (!isTrashMode) {
                                            IconButton(
                                                onClick = { showTagDialog = true },
                                                modifier = Modifier.size(32.dp).background(colors.primaryText.copy(alpha = 0.1f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.edit_new_tag), tint = colors.primaryText, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                val media = currentMediaItem
                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 16.dp))
                                    Text(
                                        text = stringResource(R.string.viewer_file_info),
                                        color = colors.primaryText,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    MediaInfoRow(stringResource(R.string.viewer_date), SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(media.dateAdded)))
                                    MediaInfoRow(stringResource(R.string.viewer_file_name), media.fileName)
                                    val actualPath = remember(media.uri) {
                                        try {
                                            val cursor = context.contentResolver.query(Uri.parse(media.uri), arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                                            cursor?.use { if (it.moveToFirst()) it.getString(0) else media.uri } ?: media.uri
                                        } catch (e: Exception) {
                                            media.uri
                                        }
                                    }
                                    MediaInfoRow(stringResource(R.string.viewer_file_path), actualPath)
                                    val tagSuffixGroup = stringResource(R.string.label_tag_suffix_group)
                                val labels = currentMediaTagsFlow.filter { !it.tag.endsWith(tagSuffixGroup) }.map { TagTranslationService.translate(it.tag) }
                                    if (labels.isNotEmpty()) MediaInfoRow(stringResource(R.string.viewer_info_image), labels.joinToString(", "))
                                    MediaInfoRow(stringResource(R.string.viewer_file_size), formatFileSize(media.fileSize))
                                    if (media.width > 0 && media.height > 0) MediaInfoRow(stringResource(R.string.viewer_image_res), "${media.width} x ${media.height}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrameStepControlRow(
    currentPosition: Long,
    duration: Long,
    onFrameStep: (Int) -> Unit,
    onScrubTo: (Long) -> Unit,
    onScrubFinished: () -> Unit
) {
    val frameStepMs = 1000L / 30L
    var isScrubbing by remember { mutableStateOf(false) }
    var previewFrameDelta by remember { mutableIntStateOf(0) }
    val latestCurrentPosition by rememberUpdatedState(currentPosition)
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RepeatFrameStepButton(direction = -1, onFrameStep = onFrameStep)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (isScrubbing) colors.primaryText.copy(alpha = 0.18f) else colors.primaryText.copy(alpha = 0.08f))
                .border(1.dp, colors.primaryText.copy(alpha = if (isScrubbing) 0.45f else 0.18f), RoundedCornerShape(17.dp))
                .pointerInput(duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startPosition = latestCurrentPosition
                        isScrubbing = true
                        previewFrameDelta = 0
                        var totalDragX = 0f
                        var lastTarget = startPosition
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            totalDragX += change.position.x - change.previousPosition.x
                            val frameDelta = (totalDragX / 10f).roundToInt()
                            val target = (startPosition + frameDelta * frameStepMs).coerceIn(0L, duration)
                            previewFrameDelta = frameDelta
                            if (target != lastTarget) {
                                lastTarget = target
                                onScrubTo(target)
                            }
                            change.consume()
                        }
                        isScrubbing = false
                        previewFrameDelta = 0
                        onScrubFinished()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isScrubbing) "${if (previewFrameDelta >= 0) "+" else ""}$previewFrameDelta ${stringResource(R.string.label_frame_unit)}" else stringResource(R.string.label_frame_step),
                        color = if (isScrubbing) colors.accent else colors.primaryText.copy(alpha = 0.75f),
                        fontSize = textSizes.small,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.width(58.dp)
                    )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(1.dp))
                )
            }
        }
        RepeatFrameStepButton(direction = 1, onFrameStep = onFrameStep)
    }
}

@Composable
private fun RepeatFrameStepButton(
    direction: Int,
    onFrameStep: (Int) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val latestOnFrameStep by rememberUpdatedState(onFrameStep)
    val repeatScope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { repeatJob?.cancel() }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .pointerInput(direction) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    latestOnFrameStep(direction)

                    repeatJob?.cancel()
                    repeatJob = repeatScope.launch {
                        delay(1000)
                        while (true) {
                            latestOnFrameStep(direction)
                            delay(80)
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                    }
                    repeatJob?.cancel()
                    repeatJob = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (direction < 0) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.primaryText,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun VideoTransportControlRow(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeekRequested: (Long) -> Unit,
    galleryState: GalleryState?,
    modifier: Modifier = Modifier,
    defaultSeekInterval: Int = 10
) {
    val colors = GalleryThemeTokens.colors
    val currentInterval = galleryState?.videoSeekInterval ?: defaultSeekInterval
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(32.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeekButtonWithPicker(
            isForward = false,
            currentInterval = currentInterval,
            onIntervalSelected = { galleryState?.videoSeekInterval = it },
            onClick = { onSeekRequested((currentPosition - currentInterval * 1000L).coerceAtLeast(0L)) }
        )
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(56.dp).background(colors.primaryText.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.width(24.dp))
        SeekButtonWithPicker(
            isForward = true,
            currentInterval = currentInterval,
            onIntervalSelected = { galleryState?.videoSeekInterval = it },
            onClick = { onSeekRequested((currentPosition + currentInterval * 1000L).coerceAtMost(duration)) }
        )
    }
}

@Composable
fun SeekButtonWithPicker(isForward: Boolean, currentInterval: Int, onIntervalSelected: (Int) -> Unit, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val options = listOf(1, 2, 5, 10, 30, 60)
    var isPickerVisible by remember { mutableStateOf(false) }
    var selectedIndexByDrag by remember(currentInterval) {
        mutableIntStateOf(options.indexOf(currentInterval).let { if (it == -1) 3 else it })
    }
    val currentOnClick by rememberUpdatedState(onClick); val currentOnIntervalSelected by rememberUpdatedState(onIntervalSelected)
    Box(contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.pointerInput(currentInterval) { awaitPointerEventScope { while (true) { val down = awaitFirstDown(); val startTimestamp = System.currentTimeMillis(); var isLongPress = false; val baseIndex = options.indexOf(currentInterval).let { if (it == -1) 3 else it }; selectedIndexByDrag = baseIndex; while (true) { val event = awaitPointerEvent(); val currentTime = System.currentTimeMillis(); if (currentTime - startTimestamp > 400 && !isLongPress) { isLongPress = true; isPickerVisible = true }; if (isLongPress) { val dragChange = event.changes.first(); val dragY = dragChange.position.y - down.position.y; val indexOffset = (dragY / 40f).roundToInt(); selectedIndexByDrag = (baseIndex + indexOffset).coerceIn(0, options.size - 1); dragChange.consume() }; if (event.changes.any { !it.pressed }) { if (isLongPress) { currentOnIntervalSelected(options[selectedIndexByDrag]); isPickerVisible = false } else currentOnClick(); break } } } } }.size(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) { Icon(imageVector = if (isForward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.primaryText.copy(alpha = 0.6f), modifier = Modifier.size(28.dp)); Text(text = currentInterval.toString(), color = colors.primaryText, fontSize = textSizes.bottomNav, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            Text(stringResource(R.string.viewer_skip), color = colors.primaryText, fontSize = textSizes.bottomNav)
        }
        if (isPickerVisible) { Popup(alignment = Alignment.Center, offset = IntOffset(0, 0)) { Surface(color = colors.background.copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp), modifier = Modifier.width(60.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) { options.forEachIndexed { index, value -> val isSelected = index == selectedIndexByDrag; Text(text = value.toString(), color = if (isSelected) colors.accent else colors.primaryText, fontSize = if (isSelected) textSizes.header else textSizes.subtitle, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(vertical = 8.dp)) } } } } }
    }
}

@Composable
fun MediaInfoRow(label: String, value: String) {
    val colors = GalleryThemeTokens.colors
    Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Text(
            text = label,
            color = colors.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            color = colors.primaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatFileSize(size: Long): String { if (size <= 0) return "0 B"; val units = arrayOf("B", "KB", "MB", "GB", "TB"); val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt(); return "%.1f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups]) }

private const val ASCII2D_BASE_URL = "https://ascii2d.net"

private data class Ascii2dUploadData(
    val fileName: String,
    val mimeType: String,
    val filePath: String,
    val byteCount: Int
)

private fun prepareAscii2dUploadData(context: android.content.Context, uriString: String): Ascii2dUploadData {
    val uri = Uri.parse(uriString)
    val sourceBytes = readImageBytes(context, uri, uriString)
    val bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
    val uploadBytes = if (bitmap != null) encodeBitmapForAscii2d(bitmap) else sourceBytes
    if (uploadBytes.isEmpty()) {
        throw IllegalStateException("Image file is empty")
    }

    val fileName = if (bitmap != null) "gallery_ascii2d.jpg" else "gallery_ascii2d.bin"
    val mimeType = if (bitmap != null) "image/jpeg" else "application/octet-stream"
    val uploadFile = File(context.cacheDir, "ascii2d_upload_${System.currentTimeMillis()}_$fileName")
    uploadFile.writeBytes(uploadBytes)
    Log.i(
        "Ascii2dSearch",
        "Prepared upload data: uri=$uriString, sourceBytes=${sourceBytes.size}, " +
            "decoded=${bitmap != null}, uploadBytes=${uploadBytes.size}, file=${uploadFile.absolutePath}, mime=$mimeType"
    )

    return Ascii2dUploadData(
        fileName = fileName,
        mimeType = mimeType,
        filePath = uploadFile.absolutePath,
        byteCount = uploadBytes.size
    )
}

private fun readImageBytes(context: android.content.Context, uri: Uri, uriString: String): ByteArray {
    val resolverStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    if (resolverStream != null) {
        val bytes = resolverStream.use { it.readBytes() }
        Log.i("Ascii2dSearch", "Read image from ContentResolver: uri=$uriString, bytes=${bytes.size}")
        return bytes
    }

    if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
        val file = File(uri.path ?: uriString)
        val bytes = file.readBytes()
        Log.i("Ascii2dSearch", "Read image from file: path=${file.absolutePath}, bytes=${bytes.size}")
        return bytes
    }

    throw IllegalStateException(context.getString(R.string.msg_error_open_image))
}

private fun encodeBitmapForAscii2d(bitmap: Bitmap): ByteArray {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    val maxSide = 1600
    val largest = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
    val scale = (maxSide.toFloat() / largest.toFloat()).coerceAtMost(1f)
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (width == bitmap.width && height == bitmap.height) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(flattened).apply {
        drawColor(android.graphics.Color.WHITE)
        drawBitmap(scaled, 0f, 0f, null)
    }
    val output = java.io.ByteArrayOutputStream()
    flattened.compress(Bitmap.CompressFormat.JPEG, 92, output)
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
    flattened.recycle()
    val bytes = output.toByteArray()
    Log.i(
        "Ascii2dSearch",
        "Encoded image for ascii2d: original=${originalWidth}x${originalHeight}, upload=${width}x${height}, jpegBytes=${bytes.size}"
    )
    return bytes
}

@Composable
private fun Ascii2dSearchDialog(
    uploadData: Ascii2dUploadData,
    onDismiss: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    var isLoading by remember { mutableStateOf(true) }
    var hasRequestedFile by remember(uploadData.filePath) { mutableStateOf(false) }
    var hasProvidedFile by remember(uploadData.filePath) { mutableStateOf(false) }
    var hasOpenedExternalResult by remember(uploadData.filePath) { mutableStateOf(false) }

    DisposableEffect(uploadData.filePath) {
        onDispose {
            runCatching { File(uploadData.filePath).delete() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    fun openResultExternally(url: String?): Boolean {
                        if (url == null || hasOpenedExternalResult || !isAscii2dResultUrl(url)) return false
                        val opened = runCatching {
                            viewContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure { error ->
                            Log.e("Ascii2dSearch", "Failed to open result in external browser: $url", error)
                        }.isSuccess
                        if (opened) {
                            hasOpenedExternalResult = true
                            Log.i("Ascii2dSearch", "Opening result in external browser: $url")
                            onDismiss()
                        }
                        return opened
                    }

                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView,
                                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams
                            ): Boolean {
                                val uploadFile = File(uploadData.filePath)
                                Log.i(
                                    "Ascii2dSearch",
                                    "WebView file chooser requested: exists=${uploadFile.exists()}, " +
                                        "bytes=${uploadFile.length()}, accept=${fileChooserParams.acceptTypes.joinToString()}, " +
                                        "mode=${fileChooserParams.mode}"
                                )
                                if (!uploadFile.exists() || uploadFile.length() <= 0L) {
                                    filePathCallback.onReceiveValue(null)
                                    return true
                                }

                                val uri = FileProvider.getUriForFile(
                                    viewContext,
                                    "${viewContext.packageName}.fileprovider",
                                    uploadFile
                                )
                                filePathCallback.onReceiveValue(arrayOf(uri))
                                hasProvidedFile = true
                                Log.i("Ascii2dSearch", "Provided ascii2d upload uri to WebView: $uri")
                                return true
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                Log.d(
                                    "Ascii2dSearch",
                                    "WebView console ${consoleMessage.messageLevel()} " +
                                        "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                                )
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                return request.isForMainFrame && openResultExternally(request.url.toString())
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                Log.i("Ascii2dSearch", "WebView page started: url=$url")
                                if (openResultExternally(url)) {
                                    view.stopLoading()
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false
                                Log.i(
                                    "Ascii2dSearch",
                                    "WebView page finished: url=$url, hasRequestedFile=$hasRequestedFile, " +
                                        "hasProvidedFile=$hasProvidedFile, uploadBytes=${uploadData.byteCount}"
                                )
                                if (!hasRequestedFile && url?.startsWith(ASCII2D_BASE_URL) == true) {
                                    hasRequestedFile = true
                                    Log.i("Ascii2dSearch", "Scheduling ascii2d file chooser request")
                                    view.postDelayed(
                                        {
                                            Log.i("Ascii2dSearch", "Evaluating ascii2d file chooser JavaScript")
                                            view.evaluateJavascript(
                                                buildAscii2dFileChooserJavascript(),
                                                { result ->
                                                    Log.d("Ascii2dSearch", "File chooser JavaScript evaluation result: $result")
                                                }
                                            )
                                            view.postDelayed(
                                                {
                                                    if (!hasProvidedFile) {
                                                        Log.w(
                                                            "Ascii2dSearch",
                                                            "File chooser callback was not received after JavaScript request; " +
                                                                "ask the user to tap ascii2d file input manually"
                                                        )
                                                    }
                                                },
                                                3000L
                                            )
                                        },
                                        1800L
                                    )
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse
                            ) {
                                Log.w(
                                    "Ascii2dSearch",
                                    "WebView HTTP error: status=${errorResponse.statusCode}, " +
                                        "reason=${errorResponse.reasonPhrase}, mainFrame=${request.isForMainFrame}, " +
                                        "url=${request.url}"
                                )
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                Log.w(
                                    "Ascii2dSearch",
                                    "WebView load error: code=${error.errorCode}, description=${error.description}, " +
                                        "mainFrame=${request.isForMainFrame}, url=${request.url}"
                                )
                            }
                        }
                        Log.i(
                            "Ascii2dSearch",
                            "Opening ascii2d WebView: file=${uploadData.fileName}, mime=${uploadData.mimeType}, " +
                                "bytes=${uploadData.byteCount}, path=${uploadData.filePath}"
                        )
                        loadUrl(ASCII2D_BASE_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isLoading -> stringResource(R.string.viewer_loading_ascii2d)
                        hasProvidedFile -> stringResource(R.string.viewer_ascii2d_ready)
                        hasRequestedFile -> stringResource(R.string.msg_video_frame_stepping_guide)
                        else -> stringResource(R.string.viewer_preparing_selection)
                    },
                    color = colors.primaryText,
                    fontSize = textSizes.subtitle
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close), tint = Color.White)
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }
    }
}

private fun buildAscii2dFileChooserJavascript(): String {
    return """
        (function() {
          function log(message) {
            console.log('ascii2d file chooser: ' + message);
          }
          var form = document.querySelector('form#file_upload');
          var input = form && form.querySelector('input[type="file"], input[name="file"]');
          log('form=' + !!form + ', input=' + !!input);
          if (!form || !input) {
            console.error('ascii2d file chooser: form or file input not found');
            return 'missing-input';
          }
          input.scrollIntoView({ block: 'center', inline: 'nearest' });
          input.click();
          log('file input click requested');
          return 'file-input-clicked';
        })();
    """.trimIndent()
}

private fun isAscii2dResultUrl(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (!uri.host.orEmpty().equals("ascii2d.net", ignoreCase = true)) return false
    val segments = uri.pathSegments
    return segments.size >= 3 &&
        segments[0].equals("search", ignoreCase = true) &&
        segments[1].lowercase(Locale.US) in setOf("color", "bovw")
}

private fun fitContentInsideViewport(
    intrinsicSize: Size,
    viewportWidth: Float,
    viewportHeight: Float
): Size? {
    val intrinsicWidth = intrinsicSize.width
    val intrinsicHeight = intrinsicSize.height
    if (!intrinsicWidth.isFinite() || !intrinsicHeight.isFinite() || intrinsicWidth <= 0f || intrinsicHeight <= 0f) {
        return null
    }

    val fitScale = minOf(viewportWidth / intrinsicWidth, viewportHeight / intrinsicHeight)
    return Size(intrinsicWidth * fitScale, intrinsicHeight * fitScale)
}

private fun clampViewerPanOffset(
    scale: Float,
    rawOffsetX: Float,
    rawOffsetY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    contentSize: Size? = null
): Offset {
    if (scale <= 1.05f) return Offset.Zero

    val contentWidth = contentSize?.width?.takeIf { it.isFinite() && it > 0f } ?: viewportWidth
    val contentHeight = contentSize?.height?.takeIf { it.isFinite() && it > 0f } ?: viewportHeight
    val maxOffsetX = ((contentWidth * scale - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((contentHeight * scale - viewportHeight) / 2f).coerceAtLeast(0f)

    return Offset(
        rawOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
        rawOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String, isUiVisible: Boolean, isPlaying: Boolean, isMuted: Boolean, seekToPosition: Long = -1L,
    onToggleUi: () -> Unit, onProgressChanged: (Long, Long) -> Unit, scale: Float, offsetX: Float, offsetY: Float,
    onZoomPan: (Float, Offset) -> Unit, onVerticalDrag: (Float) -> Unit, onSeek: (Long) -> Unit,
    onDragEnd: () -> Unit, onDoubleTap: () -> Unit, isScrollInProgress: Boolean, modifier: Modifier = Modifier,
    videoPosition: Long = 0L, videoDuration: Long = 0L, isSeeking: Boolean = false,
    onTouchIndicator: (Offset) -> Unit = {},
    loopPlayback: Boolean = true,
    resizeMode: String = "FIT"
) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val exoPlayer = remember(uri) {
        logVideoViewerTrace("player_create uriHash=${uri.hashCode()}")
        ExoPlayer.Builder(context).setRenderersFactory(
            androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    logVideoViewerTrace(
                        "player_state uriHash=${uri.hashCode()} state=$playbackState " +
                            "position=$currentPosition duration=$duration playWhenReady=$playWhenReady"
                    )
                }

                override fun onRenderedFirstFrame() {
                    logVideoViewerTrace(
                        "player_first_frame uriHash=${uri.hashCode()} position=$currentPosition duration=$duration"
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    logVideoViewerTrace(
                        "player_error uriHash=${uri.hashCode()} code=${error.errorCode} message=${error.message}"
                    )
                }
            })
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            repeatMode = if (loopPlayback) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setSeekParameters(SeekParameters.EXACT)
            prepare()
            seekTo(0)
        }
    }
    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }
    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }
    LaunchedEffect(exoPlayer, isPlaying, isScrollInProgress) { while (isPlaying && !isScrollInProgress) { if (exoPlayer.playbackState == Player.STATE_READY) onProgressChanged(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0)); delay(16) } }
    DisposableEffect(Unit) {
        onDispose {
            logVideoViewerTrace(
                "player_release uriHash=${uri.hashCode()} position=${exoPlayer.currentPosition} " +
                    "duration=${exoPlayer.duration} state=${exoPlayer.playbackState}"
            )
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    val lastInternalSeek = remember { mutableLongStateOf(-1L) }
    var scrubbingTouchPoint by remember { mutableStateOf<Offset?>(null) }

    // Apply external seek requests.
    LaunchedEffect(seekToPosition) {
        if (seekToPosition >= 0 && seekToPosition != lastInternalSeek.longValue) {
            exoPlayer.setSeekParameters(SeekParameters.EXACT)
            exoPlayer.seekTo(seekToPosition)
            lastInternalSeek.longValue = seekToPosition
        }
    }

    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(Color.Black)
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    onTouchIndicator(it)
                    onToggleUi()
                },
                onDoubleTap = {
                    onTouchIndicator(it)
                    onDoubleTap()
                }
            )
        }
        .pointerInput(scale) {
            awaitPointerEventScope {
                while (true) {
                    val firstDown = awaitFirstDown()
                    val isScrubbingZone = false

                    var totalDragX = 0f
                    var totalDragY = 0f
                    var decided = false
                    var isHorizontal = false
                    var scrubbingStartPosition = 0L
                    var accumulatedPan = Offset.Zero
                    var isTransforming = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val panChange = event.calculatePan()

                        if (event.changes.size >= 2) {
                            if (event.changes.all { !it.pressed }) break
                            continue
                        }

                        val dragChange = event.changes.firstOrNull { it.id == firstDown.id }
                        if (dragChange == null || !dragChange.pressed) break

                        val delta = dragChange.position - dragChange.previousPosition
                        totalDragX += delta.x
                        totalDragY += delta.y

                        if (!decided) {
                            if (Math.abs(totalDragX) > 15f || Math.abs(totalDragY) > 15f) {
                                decided = true
                                isHorizontal = Math.abs(totalDragX) > Math.abs(totalDragY)
                                if (isHorizontal && isScrubbingZone) {
                                    scrubbingStartPosition = exoPlayer.currentPosition
                                }
                            }
                        }

                        if (decided) {
                            if (isScrubbingZone) {
                                if (isHorizontal) {
                                    showTooltip = true
                                    scrubbingTouchPoint = dragChange.position
                                    val frameStepMs = 1000L / 30L
                                    val frameDelta = (totalDragX / 12f).roundToInt()
                                    val totalSeekDiff = frameDelta * frameStepMs
                                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                                    val targetPos = (scrubbingStartPosition + totalSeekDiff).coerceIn(0L, duration)

                                    if (targetPos != lastInternalSeek.longValue) {
                                        exoPlayer.seekTo(targetPos)
                                        lastInternalSeek.longValue = targetPos
                                        onSeek(targetPos)
                                    }
                                    dragChange.consume()
                                } else {
                                    onVerticalDrag(delta.y)
                                    dragChange.consume()
                                }
                            } else {
                                if (!isHorizontal && Math.abs(totalDragY) > Math.abs(totalDragX) * 1.5f) {
                                    onVerticalDrag(delta.y)
                                    dragChange.consume()
                                }
                            }
                        } else if (isScrubbingZone) {
                            dragChange.consume()
                        }
                    }
                    showTooltip = false
                    scrubbingTouchPoint = null
                }
            }
        }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.changes.all { !it.pressed }) onDragEnd() } } }
    ) {
        key(uri) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        logVideoViewerTrace("player_view_create uriHash=${uri.hashCode()} viewHash=${hashCode()}")
                        tag = uri
                        player = exoPlayer
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setKeepContentOnPlayerReset(false)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        controllerAutoShow = false
                        hideController()
                        this.resizeMode = when (resizeMode) {
                            "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = {
                    logVideoViewerTrace(
                        "player_view_update uriHash=${uri.hashCode()} viewHash=${it.hashCode()} " +
                            "isPlaying=$isPlaying muted=$isMuted scroll=$isScrollInProgress"
                    )
                    if (it.tag != uri) {
                        it.player = null
                        it.tag = uri
                    }
                    it.setShutterBackgroundColor(android.graphics.Color.BLACK)
                    it.setBackgroundColor(android.graphics.Color.BLACK)
                    it.player = exoPlayer
                    exoPlayer.playWhenReady = isPlaying
                    exoPlayer.volume = if (isMuted) 0f else 1f
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier.matchParentSize()
        )

        if (scrubbingTouchPoint != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f),
                            radius = 80.dp.toPx(),
                            center = scrubbingTouchPoint!!,
                            blendMode = BlendMode.Screen
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                                center = scrubbingTouchPoint!!,
                                radius = 40.dp.toPx()
                            ),
                            radius = 40.dp.toPx(),
                            center = scrubbingTouchPoint!!
                        )
                    }
            )
        }

        if (showTooltip && videoDuration > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.viewer_frame_stepping),
                            color = colors.accent,
                            fontSize = textSizes.small,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}",
                            color = colors.primaryText,
                            fontSize = textSizes.header,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GifPlayer(uri: String, isUiVisible: Boolean, onToggleUi: () -> Unit, scale: Float, offsetX: Float, offsetY: Float, onZoomPan: (Float, Offset) -> Unit, onVerticalDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDoubleTap: () -> Unit, onLongPressStart: () -> Unit = {}, onLongPressEnd: () -> Unit = {}, onTouchIndicator: (Offset) -> Unit = {}, imageLoader: ImageLoader, isFrameSteppingVisible: Boolean, gifFrames: List<Bitmap>, currentFrameIndex: Int, longPressEnabled: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val scope = rememberCoroutineScope()
    var suppressNextTap by remember { mutableStateOf(false) }
    val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(uri).build(), imageLoader = imageLoader)
    Box(modifier = modifier.background(Color.Black).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onTouchIndicator(it)
                    if (!longPressEnabled) {
                        tryAwaitRelease()
                    } else {
                        var magnifierShown = false
                        val job = scope.launch {
                            delay(450)
                            magnifierShown = true
                            suppressNextTap = true
                            onLongPressStart()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (magnifierShown) {
                            onLongPressEnd()
                        }
                    }
                },
                onTap = {
                    onTouchIndicator(it)
                    if (suppressNextTap) {
                        suppressNextTap = false
                    } else {
                        onToggleUi()
                    }
                },
                onDoubleTap = {
                    onTouchIndicator(it)
                    onDoubleTap()
                }
            )
        }
        .pointerInput(scale) {
            awaitEachGesture {
                var accumulatedPan = Offset.Zero
                var isTransforming = false
                while (true) {
                    val event = awaitPointerEvent()
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    if (event.changes.size >= 2 || scale > 1.01f) {
                        accumulatedPan += panChange
                        val shouldHandleTransform = event.changes.size >= 2 || isTransforming || accumulatedPan.getDistance() > 8f
                        if (shouldHandleTransform) {
                            isTransforming = true
                            onZoomPan(zoomChange, panChange)
                            event.changes.forEach { it.consume() }
                        }
                    } else if (panChange != Offset.Zero && Math.abs(panChange.y) > Math.abs(panChange.x) * 2.5f) {
                        onVerticalDrag(panChange.y)
                        event.changes.forEach { it.consume() }
                    }
                    if (event.changes.all { !it.pressed }) break
                }
            }
        }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.changes.all { !it.pressed }) onDragEnd() } } }
    ) {
        if (isFrameSteppingVisible && gifFrames.isNotEmpty()) {
            Image(bitmap = gifFrames[currentFrameIndex].asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
fun RecommendationCard(mediaItem: MediaData, score: String?, imageLoader: ImageLoader, isDeleted: Boolean = false, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    Box(modifier = Modifier.size(110.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant).clickable { onClick() }) { Image(painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(mediaItem.uri).apply { if (mediaItem.isVideo) videoFrameMillis(1000) }.build(), imageLoader = imageLoader), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit); if (score != null) Box(modifier = Modifier.align(Alignment.BottomEnd).background(colors.background.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(score, color = colors.primaryText, fontSize = GalleryThemeTokens.textSizes.tiny) }; if (isDeleted) Box(modifier = Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(R.string.viewer_deleted), tint = colors.primaryText.copy(alpha = 0.8f), modifier = Modifier.size(32.dp)) } } }

@Composable
private fun mediaTapZoneLabels(zoneCount: Int): List<String> {
    val prev = stringResource(R.string.label_tap_prev)
    val next = stringResource(R.string.label_tap_next)
    val zoom = stringResource(R.string.label_tap_zoom)
    val save = stringResource(R.string.label_tap_save)
    val toggle = stringResource(R.string.label_tap_toggle_ui)
    val settings = stringResource(R.string.label_tap_settings)
    val search = stringResource(R.string.label_tap_search)

    return when (zoneCount) {
        11 -> listOf(prev, prev, prev, zoom, save, toggle, settings, search, zoom, next, next)
        4 -> listOf(prev, zoom, toggle, next)
        else -> listOf(prev, toggle, next)
    }
}

@Composable
private fun mediaTapZoneGuideLabels(zoneCount: Int): List<String> {
    val prevMedia = stringResource(R.string.label_tap_prev_media)
    val nextMedia = stringResource(R.string.label_tap_next_media)
    val details = stringResource(R.string.label_tap_details)
    val prev = stringResource(R.string.label_tap_prev)
    val next = stringResource(R.string.label_tap_next)
    val zoom = stringResource(R.string.label_tap_zoom)
    val controls = stringResource(R.string.label_tap_controls)
    val settings = stringResource(R.string.label_tap_settings)
    val tagEdit = stringResource(R.string.label_tap_tag_edit)
    val slideshow = stringResource(R.string.label_tap_slideshow)
    val screenshot = stringResource(R.string.label_tap_screenshot)
    val search = stringResource(R.string.label_tap_search)

    return when (zoneCount) {
        11 -> listOf(
            prevMedia, details, nextMedia, prev, zoom, controls, settings, tagEdit, slideshow, screenshot, search
        )
        7 -> listOf(prevMedia, nextMedia, prev, controls, next, zoom, settings)
        5 -> listOf(prev, prevMedia, controls, nextMedia, next)
        4 -> listOf(prev, zoom, controls, next)
        else -> listOf(prev, controls, next)
    }
}

private fun captureVideoFrame(context: android.content.Context, uriString: String, positionMs: Long, onResult: (Bitmap?) -> Unit) { val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO); scope.launch { val retriever = MediaMetadataRetriever(); try { val uri = Uri.parse(uriString); context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd -> retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } ?: retriever.setDataSource(context, uri); val bitmap = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); withContext(Dispatchers.Main) { onResult(bitmap) } } catch (e: Exception) { e.printStackTrace(); withContext(Dispatchers.Main) { onResult(null) } } finally { try { retriever.release() } catch (e: Exception) {} } } }

private fun formatTime(ms: Long): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    val hundredths = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}

private fun extractGifFrames(
    context: android.content.Context,
    uriString: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onProgress: (Float?) -> Unit = {},
    onResult: (List<Bitmap>) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val frames = mutableListOf<Bitmap>()
        val uri = Uri.parse(uriString)

        suspend fun reportProgress(step: Int, total: Int) {
            withContext(Dispatchers.Main) {
                onProgress((step.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }

        val frameCount = 30
        val retriever = MediaMetadataRetriever()
        var retrieverSuccess = false
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } ?: retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (durationMs > 0) {
                for (i in 0 until frameCount) {
                    val timeUs = (durationMs * 1000 / frameCount) * i
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let(frames::add)
                    reportProgress(i + 1, frameCount)
                }
            } else {
                for (i in 0 until frameCount) {
                    val timeUs = i * 100000L
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (bitmap != null) {
                        frames.add(bitmap)
                    } else if (i > 10) {
                        break
                    }
                    reportProgress(i + 1, frameCount)
                }
            }
            retrieverSuccess = frames.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (!retrieverSuccess) {
            frames.clear()
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    @Suppress("DEPRECATION")
                    val movie = Movie.decodeStream(inputStream)
                    if (movie != null) {
                        val durationMs = movie.duration().coerceAtLeast(1)
                        val width = movie.width().coerceAtLeast(1)
                        val height = movie.height().coerceAtLeast(1)
                        if (width > 0 && height > 0) {
                            for (i in 0 until frameCount) {
                                val timeMs = (durationMs / frameCount) * i
                                movie.setTime(timeMs)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                movie.draw(canvas, 0f, 0f)
                                frames.add(bitmap)
                                reportProgress(i + 1, frameCount)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        withContext(Dispatchers.Main) {
            onProgress(null)
            if (frames.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.msg_error_extract_frame), Toast.LENGTH_SHORT).show()
            }
            onResult(frames)
        }
    }
}
private fun saveBitmapToScreenshots(context: android.content.Context, bitmap: Bitmap) { val filename = "Screenshot_${System.currentTimeMillis()}.png"; val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, filename); put(MediaStore.MediaColumns.MIME_TYPE, "image/png"); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots"); put(MediaStore.MediaColumns.IS_PENDING, 1) } }; val resolver = context.contentResolver; try { val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues); if (uri != null) { resolver.openOutputStream(uri)?.use { outputStream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { contentValues.clear(); contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0); resolver.update(uri, contentValues, null, null) }; (context as Activity).runOnUiThread { Toast.makeText(context, context.getString(R.string.msg_screenshot_saved), Toast.LENGTH_SHORT).show() } } } catch (e: Exception) { Log.e("PictureViewer", "Error saving screenshot", e); (context as Activity).runOnUiThread { Toast.makeText(context, context.getString(R.string.msg_error_save_failed), Toast.LENGTH_SHORT).show() } } }

private fun setAsWallpaper(context: android.content.Context, uriString: String) { val wallpaperManager = WallpaperManager.getInstance(context); val uri = Uri.parse(uriString); try { val intent = wallpaperManager.getCropAndSetWallpaperIntent(uri); context.startActivity(intent) } catch (e: Exception) { try { val inputStream = context.contentResolver.openInputStream(uri); wallpaperManager.setStream(inputStream); Toast.makeText(context, context.getString(R.string.msg_wallpaper_set), Toast.LENGTH_SHORT).show() } catch (e2: Exception) { Log.e("PictureViewer", "Failed to set wallpaper", e2); Toast.makeText(context, context.getString(R.string.msg_error_wallpaper_failed), Toast.LENGTH_SHORT).show() } } }

private fun setBitmapAsWallpaper(context: android.content.Context, bitmap: Bitmap) { val wallpaperManager = WallpaperManager.getInstance(context); try { wallpaperManager.setBitmap(bitmap); Toast.makeText(context, context.getString(R.string.msg_wallpaper_set_frame), Toast.LENGTH_SHORT).show() } catch (e: Exception) { Log.e("PictureViewer", "Failed to set bitmap as wallpaper", e); Toast.makeText(context, context.getString(R.string.msg_error_wallpaper_failed), Toast.LENGTH_SHORT).show() } }

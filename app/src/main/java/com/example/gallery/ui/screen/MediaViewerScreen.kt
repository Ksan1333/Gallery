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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.window.Popup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.provider.MediaStore
import android.content.ContentValues
import android.app.WallpaperManager
import android.media.MediaMetadataRetriever
import android.widget.Toast
import com.example.gallery.ui.AppConstants
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.AgeRatingFilter
import com.example.gallery.service.TagTranslationService
import com.example.gallery.ui.component.GalleryFloatingActionButton
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

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

    val thumbnailListState = rememberLazyListState()
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
    var screenOrientation by rememberSaveable { mutableIntStateOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }

    var isRecommendationVisible by rememberSaveable { mutableStateOf(false) }
    val recommendationDragOffset = remember { Animatable(0f) }

    val currentMedia = remember(pagerState.currentPage, imageList) { imageList.getOrNull(pagerState.currentPage) }

    // 計測モード用。
    var viewStartTime by remember { mutableLongStateOf(0L) }
    var lastViewedUriInMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage, galleryState?.isMeasureModeActive) {
        val state = galleryState ?: return@LaunchedEffect
        val currentUri = currentMedia?.uri
        if (state.isMeasureModeActive) {
            // 前の画像の表示時間を記録する。
            if (lastViewedUriInMode != null && viewStartTime > 0) {
                val duration = (System.currentTimeMillis() - viewStartTime) / 1000
                if (duration > 0) {
                    state.repository.updateMeasureStats(lastViewedUriInMode!!, duration)
                }
            }
            // 新しい画像の開始時間を記録する。
            viewStartTime = System.currentTimeMillis()
            lastViewedUriInMode = currentUri
        } else {
            // モード終了時に最後の画像を記録する。
            if (lastViewedUriInMode != null && viewStartTime > 0) {
                val duration = (System.currentTimeMillis() - viewStartTime) / 1000
                if (duration > 0) {
                    state.repository.updateMeasureStats(lastViewedUriInMode!!, duration)
                }
            }
            viewStartTime = 0L
            lastViewedUriInMode = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val state = galleryState
            if (state?.isMeasureModeActive == true && lastViewedUriInMode != null && viewStartTime > 0) {
                val duration = (System.currentTimeMillis() - viewStartTime) / 1000
                if (duration > 0) {
                    val uri = lastViewedUriInMode!!
                    val repo = state.repository
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repo.updateMeasureStats(uri, duration)
                    }
                }
            }
        }
    }

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

    var isVideoPlaying by remember(pagerState.currentPage) { mutableStateOf(true) }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }
    var videoDuration by remember(pagerState.currentPage) { mutableLongStateOf(0L) }
    var videoPosition by remember(pagerState.currentPage) { mutableLongStateOf(0L) }
    var isMuted by rememberSaveable { mutableStateOf(true) }
    var isSeeking by remember(pagerState.currentPage) { mutableStateOf(false) }
    var seekTargetPosition by remember(pagerState.currentPage) { mutableLongStateOf(-1L) }

    var isVerticalSwiping by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val media = imageList.getOrNull(pagerState.currentPage)
        logVideoViewerTrace(
            "viewer_page_changed page=${pagerState.currentPage} total=${imageList.size} " +
                "uriHash=${media?.uri?.hashCode()} isVideo=${media?.isVideo} isGif=${media?.isGif}"
        )
        onPageSelected?.invoke(pagerState.currentPage)
        // ページ切り替え時に詳細パネルを閉じる。
        // isRecommendationVisible = false
        // recommendationDragOffset.snapTo(0f)

        videoPosition = 0L
        videoDuration = 0L
        isSeeking = false
        seekTargetPosition = -1L
        isVideoPlaying = true
    }

    val imageLoader = context.imageLoader
    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(isUiVisible, insetsController, keepNavigationBarsHidden) {
        window?.navigationBarColor = android.graphics.Color.BLACK
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!isUiVisible) {
            insetsController?.hide(WindowInsetsCompat.Type.statusBars())
            insetsController?.show(WindowInsetsCompat.Type.navigationBars())
        } else if (keepNavigationBarsHidden) {
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
            insetsController?.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        onDispose { (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(isRecommendationVisible, pagerState.currentPage) {
        if (isRecommendationVisible && currentMedia != null) {
            val currentAgeRating = currentMetadata?.ageRating ?: "SFW"

            // 1. 現在のコンテキストからランダムなメディアを用意する。
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

            // 2. AI 推薦 (画像のみ)。
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

    val onShowSimilarity: (MediaData) -> Unit = {
        isRecommendationVisible = true
        scope.launch { recommendationDragOffset.snapTo(0f) }
    }

    var controlInteractionToken by remember { mutableIntStateOf(0) }
    val onToggle = {
        if (!isRecommendationVisible) {
            isUiVisible = !isUiVisible
            controlInteractionToken++
        }
    }

    LaunchedEffect(isUiVisible, controlInteractionToken, pagerState.currentPage, currentMedia?.uri) {
        if (isUiVisible && currentMedia?.isVideo == true) {
            delay(2000)
            isUiVisible = false
        }
    }

    val density = LocalDensity.current
    val maxRecDrag = with(density) { 360.dp.toPx() } // 600dp -> 360dp (邏・0%遞句ｺｦ)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPageZoomed && !isVerticalSwiping,
            beyondViewportPageCount = 0
        ) { page ->
            val mediaItem = imageList[page]
            val scale = remember { Animatable(1f) }
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }

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
                        onVerticalDrag = { drag ->
                            isVerticalSwiping = true
                            scope.launch {
                                if (isRecommendationVisible) {
                                    val currentRecOffset = recommendationDragOffset.value
                                    recommendationDragOffset.snapTo((currentRecOffset + drag).coerceAtLeast(0f))
                                } else {
                                    val newOffset = offsetY.value + drag
                                    if (newOffset < -20f) {
                                        scope.launch { recommendationDragOffset.snapTo(0f) }
                                        isRecommendationVisible = true
                                    } else {
                                        offsetY.snapTo(newOffset)
                                    }
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
                                if (offsetY.value > 200f) onClickedClose()
                                else scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                        onDoubleTap = {
                            val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                            scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
                        },
                        isScrollInProgress = pagerState.isScrollInProgress,
                        isSeeking = isSeeking,
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
                                if (isRecommendationVisible) {
                                    val currentRecOffset = recommendationDragOffset.value
                                    recommendationDragOffset.snapTo((currentRecOffset + drag).coerceAtLeast(0f))
                                } else {
                                    val newOffset = offsetY.value + drag
                                    if (newOffset < -20f) {
                                        scope.launch { recommendationDragOffset.snapTo(0f) }
                                        isRecommendationVisible = true
                                    } else {
                                        offsetY.snapTo(newOffset)
                                    }
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
                            val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                            scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
                        },
                        imageLoader = imageLoader, isFrameSteppingVisible = isFrameSteppingVisible,
                        gifFrames = gifFrames, currentFrameIndex = currentFrameIndex,
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
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val currentScale = scale.value
                                        val isZoomed = currentScale > 1.01f
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
                                                        if (isRecommendationVisible) {
                                                            val currentRecOffset = recommendationDragOffset.value
                                                            recommendationDragOffset.snapTo((currentRecOffset + panChange.y).coerceAtLeast(0f))
                                                        } else {
                                                            val newOffset = offsetY.value + panChange.y
                                                            if (newOffset < -20f) {
                                                                scope.launch { recommendationDragOffset.snapTo(0f) }
                                                                isRecommendationVisible = true
                                                            } else {
                                                                offsetY.snapTo(newOffset)
                                                            }
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
                                                    if (offsetY.value > 200f) onClickedClose()
                                                    else scope.launch { offsetY.animateTo(0f) }
                                                }
                                            }
                                            break
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onToggle() }, onDoubleTap = {
                                    val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                                    scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
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
                        modifier = Modifier.align(Alignment.Center)
                    )
                }


                LaunchedEffect(showTagDialog) {
                    if (showTagDialog) {
                        delay(100)
                        insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                        insetsController?.show(WindowInsetsCompat.Type.navigationBars())
                    }
                }

                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.85f)).navigationBarsPadding().padding(bottom = 2.dp)) {
                    if (currentMediaItem.isVideo && videoDuration > 0 && !pagerState.isScrollInProgress) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize, modifier = Modifier.padding(end = 8.dp))
                                var sliderWidth by remember { mutableIntStateOf(0) }
                                Box(
                                    modifier = Modifier.weight(1f).height(24.dp).onGloballyPositioned { coordinates -> sliderWidth = coordinates.size.width },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = videoPosition.toFloat(),
                                        onValueChange = {
                                            if (!isSeeking) {
                                                wasPlayingBeforeSeek = isVideoPlaying
                                            }
                                            val frameStepMs = 1000L / 30L
                                            val target = (((it.toLong() + frameStepMs / 2) / frameStepMs) * frameStepMs).coerceIn(0L, videoDuration)
                                            isSeeking = true
                                            videoPosition = target
                                            seekTargetPosition = target
                                            isVideoPlaying = false
                                        },
                                        onValueChangeFinished = {
                                            scope.launch {
                                                delay(50)
                                                isSeeking = false
                                                seekTargetPosition = -1L
                                                // シーク前の再生状態を復元する。
                                                if (wasPlayingBeforeSeek) {
                                                    isVideoPlaying = true
                                                }
                                            }
                                        },
                                        valueRange = 0f..videoDuration.toFloat().coerceAtLeast(1f),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                                    // スライダーの明るい進捗を thumb の位置に追従させる。
                                    if (isSeeking && sliderWidth > 0) {
                                        val fraction = (videoPosition.toFloat() / videoDuration.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                                        val thumbX = with(LocalDensity.current) { (sliderWidth * fraction).toDp() }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .offset(x = thumbX - 20.dp)
                                                .size(40.dp)
                                                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)), CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                GalleryFloatingActionButton(icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, onClick = { isMuted = !isMuted }, size = 32.dp, iconSize = 18.dp)
                            }
                            FrameStepControlRow(
                                currentPosition = videoPosition,
                                duration = videoDuration,
                                onFrameStep = { direction ->
                                    val frameStepMs = 1000L / 30L
                                    val target = (videoPosition + direction.toLong() * frameStepMs).coerceIn(0L, videoDuration)
                                    seekTargetPosition = target
                                    videoPosition = target
                                    isSeeking = true
                                    isVideoPlaying = false
                                    scope.launch { delay(80); isSeeking = false; seekTargetPosition = -1L }
                                },
                                onScrubTo = { target ->
                                    val bounded = target.coerceIn(0L, videoDuration)
                                    seekTargetPosition = bounded
                                    videoPosition = bounded
                                    isSeeking = true
                                    isVideoPlaying = false
                                },
                                onScrubFinished = {
                                    scope.launch { delay(80); isSeeking = false; seekTargetPosition = -1L }
                                }
                            )
                        }
                    }

                    if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) {
                        val frameListState = rememberLazyListState()
                        val currentCenterIndex by remember {
                            derivedStateOf {
                                val layoutInfo = frameListState.layoutInfo
                                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                                val visibleItems = layoutInfo.visibleItemsInfo
                                if (visibleItems.isNotEmpty()) {
                                    val centerItem = visibleItems.minByOrNull { Math.abs((it.offset + it.size / 2) - viewportCenter) }
                                    centerItem?.index ?: currentFrameIndex
                                } else currentFrameIndex
                            }
                        }
                        LaunchedEffect(currentCenterIndex) { currentFrameIndex = currentCenterIndex }
                        LazyRow(state = frameListState, modifier = Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = (LocalContext.current.resources.displayMetrics.widthPixels / 2 / LocalContext.current.resources.displayMetrics.density).dp - 21.dp)) {
                            itemsIndexed(gifFrames, key = { index, _ -> index }) { index, bitmap ->
                                val isSelected = currentFrameIndex == index
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(if (isSelected) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent).border(width = if (isSelected) 2.dp else 0.dp, color = if (isSelected) Color.Yellow else Color.Transparent, shape = RoundedCornerShape(4.dp)).clickable { currentFrameIndex = index; scope.launch { frameListState.animateScrollToItem((index - 4).coerceAtLeast(0)) } }, contentScale = ContentScale.Crop)
                            }
                        }
                    } else {
                        LazyRow(state = thumbnailListState, modifier = Modifier.fillMaxWidth().height(50.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            itemsIndexed(imageList) { index, mediaItem ->
                                val isSelected = pagerState.currentPage == index
                                Box(modifier = Modifier.padding(horizontal = 2.dp).size(42.dp).clip(RoundedCornerShape(6.dp)).border(width = if (isSelected) 2.dp else 0.dp, color = if (isSelected) Color.White else Color.Transparent, shape = RoundedCornerShape(6.dp)).clickable { scope.launch { pagerState.scrollToPage(index) } }) {
                                    Image(painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(mediaItem.uri).apply { if (mediaItem.isVideo) videoFrameMillis(1000) }.build(), imageLoader = imageLoader), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        // 1. ゴミ箱 / 復元。
                        galleryState?.let { _ ->
                            if (showDeleteButton) {
                                GalleryFloatingActionButton(icon = Icons.Default.Delete, tooltipDescription = "ゴミ箱へ移動", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { galleryState.repository.moveToTrash(listOf(currentMediaItem.uri)); Toast.makeText(context, "ゴミ箱へ移動しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.White)
                            } else {
                                Row {
                                    GalleryFloatingActionButton(icon = Icons.Default.DeleteForever, tooltipDescription = "完全に削除", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { galleryState.repository.permanentlyDelete(listOf(currentMediaItem.uri)); Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.Red)
                                    Spacer(Modifier.width(8.dp))
                                    GalleryFloatingActionButton(icon = Icons.Default.Restore, tooltipDescription = "復元", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { galleryState.repository.restoreFromTrash(listOf(currentMediaItem.uri)); Toast.makeText(context, "復元しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.White)
                                }
                            }
                        }

                        // 2. 蝗櫁ｻ｢
                        GalleryFloatingActionButton(icon = Icons.Default.ScreenRotation, tooltipDescription = "回転 (縦横切替)", size = 36.dp, iconSize = 20.dp, onClick = { val target = if (screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; screenOrientation = target; (context as Activity).requestedOrientation = target })

                        // 3. 再生制御。
                        if (false) GalleryFloatingActionButton(
                            icon = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            tooltipDescription = if (isVideoPlaying) "一時停止" else "再生",
                            size = 36.dp,
                            iconSize = 20.dp,
                            enabled = false,
                            contentColor = Color.Transparent,
                            onClick = { }
                        )

                        // 4. お気に入り。
                        galleryState?.let { state ->
                            val favorites by state.repository.getFavoriteMedia().collectAsState(initial = emptyList<MediaData>())
                            val isFavorite = favorites.any { it.uri == currentMediaItem.uri }
                            GalleryFloatingActionButton(icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tooltipDescription = "お気に入り切替", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { state.repository.toggleFavorite(currentMediaItem.uri) } }, contentColor = if (isFavorite) Color.Red else Color.White)
                        }

                        // 5. その他ボタン。
                        var showOverflowMenu by remember { mutableStateOf(false) }
                        var isAscii2dSearching by remember { mutableStateOf(false) }
                        var ascii2dUploadData by remember { mutableStateOf<Ascii2dUploadData?>(null) }
                        Box {
                            GalleryFloatingActionButton(
                                icon = Icons.Default.MoreVert,
                                tooltipDescription = "その他",
                                size = 36.dp,
                                iconSize = 20.dp,
                                onClick = { showOverflowMenu = true }
                            )
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(GalleryThemeTokens.colors.surfaceVariant)
                            ) {
                                // GIF コマ送り (GIF のみ有効)。
                                DropdownMenuItem(
                                    text = { Text(if (isFrameSteppingVisible) "コマ送り終了" else "GIFコマ送り", color = if (currentMediaItem.isGif) (if (isFrameSteppingVisible) Color.Yellow else Color.White) else Color.Gray) },
                                    leadingIcon = { Icon(if (isFrameSteppingVisible) Icons.Default.Close else Icons.Default.Collections, null, tint = if (currentMediaItem.isGif) (if (isFrameSteppingVisible) Color.Yellow else Color.White) else Color.Gray) },
                                    enabled = currentMediaItem.isGif,
                                    onClick = {
                                        showOverflowMenu = false
                                        if (!isFrameSteppingVisible) {
                                            extractGifFrames(context, currentMediaItem.uri, scope) { frames -> gifFrames = frames; isFrameSteppingVisible = true }
                                        } else {
                                            isFrameSteppingVisible = false
                                        }
                                    }
                                )

                                // 保存 (動画・GIF・コマ送り中のみ有効)。
                                val isAscii2dEnabled = !currentMediaItem.isVideo && !isAscii2dSearching
                                DropdownMenuItem(
                                    text = { Text(if (isAscii2dSearching) "ascii2d検索中..." else "ascii2dで検索", color = if (isAscii2dEnabled) Color.White else Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = if (isAscii2dEnabled) Color.White else Color.Gray) },
                                    enabled = isAscii2dEnabled,
                                    onClick = {
                                        showOverflowMenu = false
                                        isAscii2dSearching = true
                                        Toast.makeText(context, "ascii2dで検索中...", Toast.LENGTH_SHORT).show()
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching { prepareAscii2dUploadData(context, currentMediaItem.uri) }
                                            }
                                            isAscii2dSearching = false
                                            result.onSuccess { uploadData ->
                                                ascii2dUploadData = uploadData
                                            }.onFailure { error ->
                                                Log.e("Ascii2dSearch", "Failed to search ascii2d", error)
                                                Toast.makeText(context, "ascii2d検索に失敗しました: ${error.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )

                                val isSaveEnabled = currentMediaItem.isVideo || currentMediaItem.isGif
                                DropdownMenuItem(
                                    text = { Text("スクリーンショットを保存", color = if (isSaveEnabled) Color.White else Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Screenshot, null, tint = if (isSaveEnabled) Color.White else Color.Gray) },
                                    enabled = isSaveEnabled,
                                    onClick = {
                                        showOverflowMenu = false
                                        if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) saveBitmapToScreenshots(context, gifFrames[currentFrameIndex])
                                        else if (currentMediaItem.isVideo) captureVideoFrame(context, currentMediaItem.uri, videoPosition) { bitmap -> if (bitmap != null) saveBitmapToScreenshots(context, bitmap) else Toast.makeText(context, "動画フレームの取得に失敗しました", Toast.LENGTH_SHORT).show() }
                                        else if (currentMediaItem.isGif) Toast.makeText(context, "GIFはコマ送りモードで保存してください", Toast.LENGTH_SHORT).show()
                                    }
                                )

                                // 壁紙設定 (動画以外。GIF はコマ送り中のみ)。
                                val isWallpaperEnabled = !currentMediaItem.isVideo && (!currentMediaItem.isGif || isFrameSteppingVisible)
                                DropdownMenuItem(
                                    text = { Text("壁紙に設定", color = if (isWallpaperEnabled) Color.White else Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Wallpaper, null, tint = if (isWallpaperEnabled) Color.White else Color.Gray) },
                                    enabled = isWallpaperEnabled,
                                    onClick = {
                                        showOverflowMenu = false
                                        if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) setBitmapAsWallpaper(context, gifFrames[currentFrameIndex])
                                        else setAsWallpaper(context, currentMediaItem.uri)
                                    }
                                )

                                // フォルダのサムネイルに設定する。
                                DropdownMenuItem(
                                    text = { Text("フォルダのサムネイルに設定", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.FolderSpecial, null, tint = Color.White) },
                                    onClick = {
                                        showOverflowMenu = false
                                        scope.launch {
                                            galleryState?.repository?.updateFolderThumbnail(currentMediaItem.folderName, currentMediaItem.uri)
                                            Toast.makeText(context, "フォルダ「${currentMediaItem.folderName}」のサムネイルに設定しました", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // タグ編集 (全メディア共通)。
                                galleryState?.let { _ ->
                                    DropdownMenuItem(
                                        text = { Text("タグ・評価を編集", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.LocalOffer, null, tint = Color.White) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showTagDialog = true
                                        }
                                    )
                                }
                            }
                            ascii2dUploadData?.let { uploadData ->
                                Ascii2dSearchDialog(
                                    uploadData = uploadData,
                                    onDismiss = { ascii2dUploadData = null }
                                )
                            }
                        }
                    }
                }
                if (showTagDialog) { galleryState?.let { state -> UnifiedMediaEditDialog(uris = listOf(currentMediaItem.uri), repository = state.repository, onDismiss = { showTagDialog = false }) } }
            }
        }

        AnimatedVisibility(visible = isRecommendationVisible, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val density = LocalDensity.current
            val maxDrag = with(density) { 360.dp.toPx() } // 約80%に調整。
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.42f).offset { IntOffset(0, recommendationDragOffset.value.roundToInt()) }.background(Color.Black.copy(alpha = 0.95f)).pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (recommendationDragOffset.value > 100f) { scope.launch { recommendationDragOffset.animateTo(maxDrag, tween(250)); isRecommendationVisible = false; delay(250); recommendationDragOffset.snapTo(0f) } } else { scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) } } }, onVerticalDrag = { change, dragAmount -> change.consume(); scope.launch { recommendationDragOffset.snapTo((recommendationDragOffset.value + dragAmount).coerceAtLeast(0f)) } }) }) {
                Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) { Surface(modifier = Modifier.width(40.dp).height(4.dp), color = Color.Gray.copy(alpha = 0.5f), shape = CircleShape) {} }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("詳細・レコメンド", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.BodyFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(
                            "閉じる",
                            color = Color.Gray,
                            fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize,
                            modifier = Modifier.clickable { isRecommendationVisible = false }.padding(8.dp)
                        )
                    }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        val currentMediaItem = imageList.getOrNull(pagerState.currentPage)
                        if (currentMediaItem != null) {
                            // 1. 似た雰囲気の画像 (AIベクトル) - 画像のみ。
                            if (!currentMediaItem.isVideo && !isTrashMode) {
                                item { Text("似た雰囲気の画像 (AIベクトル)", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.BodyFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item {
                                    if (currentMetadata?.hasFeatureVector != true) {
                                        Text("この画像は未解析です。解析すると似た画像を見つけられるようになります。", color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, modifier = Modifier.padding(16.dp))
                                    } else if (recommendedMediaByVisual.isEmpty()) {
                                        Text("似た画像が見つかりませんでした。", color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, modifier = Modifier.padding(16.dp))
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

                            // 2. ランダムな画像 / 動画。
                            if (!isTrashMode) {
                                item { Text(if (currentMediaItem.isVideo) "ランダムな動画" else "ランダムな画像", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.BodyFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item {
                                    if (randomMediaList.isEmpty()) {
                                        Text(if (currentMediaItem.isVideo) "動画が見つかりませんでした" else "読み込み中...", color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, modifier = Modifier.padding(16.dp))
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

                            // 3. タグ (Chips)。
                            item {
                                val normalTags = remember(currentMediaTagsFlow) { currentMediaTagsFlow.filter { !it.tag.endsWith("系") && it.confidence >= 0.6f }.sortedByDescending { it.confidence } }
                                val currentAgeRating = currentMetadata?.ageRating ?: "SFW"

                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("タグ", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.BodyFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Surface(color = when(currentAgeRating) { "R18" -> Color.Red.copy(alpha = 0.8f); "R15" -> Color.Yellow.copy(alpha = 0.8f); else -> Color.Green.copy(alpha = 0.8f) }, shape = RoundedCornerShape(4.dp)) {
                                            Text(text = if (isTrashMode) "$currentAgeRating ゴミ" else currentAgeRating, color = if (currentAgeRating == "R15") Color.Black else Color.White, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }

                                    if (!currentMediaItem.isVideo && currentMetadata?.isAiAnalyzed != true) {
                                        Text("AI解析は未実行です", color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize)
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        normalTags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.DarkGray)
                                                    .drawWithContent {
                                                        val progressWidth = size.width * tag.confidence
                                                        drawRect(
                                                            color = Color.Green.copy(alpha = 0.4f),
                                                            topLeft = Offset(size.width - progressWidth, 0f),
                                                            size = Size(progressWidth, size.height)
                                                        )
                                                        drawContent()
                                                    }
                                                    .clickable { isRecommendationVisible = false; onNavigateToTag?.invoke(tag.tag) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(TagTranslationService.translate(tag.tag), color = Color.White, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize)
                                                    if (tag.confidence > 0f && tag.confidence < 1f) {
                                                        Text(text = " ${(tag.confidence * 100).toInt()}%", color = Color.LightGray, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize, modifier = Modifier.padding(start = 4.dp))
                                                    }
                                                }
                                            }
                                        }
                                        if (!isTrashMode) {
                                            IconButton(onClick = { showTagDialog = true }, modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                                                Icon(Icons.Default.Add, contentDescription = "タグ追加", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                val media = currentMediaItem
                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 16.dp))
                                    Text("ファイル情報", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                                    MediaInfoRow("日時", SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(media.dateAdded)))
                                    MediaInfoRow("ファイル名", media.fileName)
                                    val actualPath = remember(media.uri) {
                                        try {
                                            val cursor = context.contentResolver.query(Uri.parse(media.uri), arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                                            cursor?.use { if (it.moveToFirst()) it.getString(0) else media.uri } ?: media.uri
                                        } catch (e: Exception) {
                                            media.uri
                                        }
                                    }
                                    MediaInfoRow("ファイルパス", actualPath)
                                    val labels = currentMediaTagsFlow.filter { !it.tag.endsWith("系") }.map { TagTranslationService.translate(it.tag) }
                                    if (labels.isNotEmpty()) MediaInfoRow("画像情報", labels.joinToString(", "))
                                    MediaInfoRow("ファイルサイズ", formatFileSize(media.fileSize))
                                    if (media.width > 0 && media.height > 0) MediaInfoRow("画像の幅・高さ", "${media.width} x ${media.height}")
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
                .background(if (isScrubbing) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = if (isScrubbing) 0.45f else 0.18f), RoundedCornerShape(17.dp))
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
                    text = if (isScrubbing) "${if (previewFrameDelta >= 0) "+" else ""}$previewFrameDelta f" else "1 f",
                    color = if (isScrubbing) Color.Cyan else Color.White.copy(alpha = 0.75f),
                    fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
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
            tint = Color.White,
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
    modifier: Modifier = Modifier
) {
    val currentInterval = galleryState?.videoSeekInterval ?: 10
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
            modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
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
    val options = listOf(1, 2, 5, 10, 30, 60)
    var isPickerVisible by remember { mutableStateOf(false) }
    var selectedIndexByDrag by remember(currentInterval) {
        mutableIntStateOf(options.indexOf(currentInterval).let { if (it == -1) 3 else it })
    }
    val currentOnClick by rememberUpdatedState(onClick); val currentOnIntervalSelected by rememberUpdatedState(onIntervalSelected)
    Box(contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.pointerInput(currentInterval) { awaitPointerEventScope { while (true) { val down = awaitFirstDown(); val startTimestamp = System.currentTimeMillis(); var isLongPress = false; val baseIndex = options.indexOf(currentInterval).let { if (it == -1) 3 else it }; selectedIndexByDrag = baseIndex; while (true) { val event = awaitPointerEvent(); val currentTime = System.currentTimeMillis(); if (currentTime - startTimestamp > 400 && !isLongPress) { isLongPress = true; isPickerVisible = true }; if (isLongPress) { val dragChange = event.changes.first(); val dragY = dragChange.position.y - down.position.y; val indexOffset = (dragY / 40f).roundToInt(); selectedIndexByDrag = (baseIndex + indexOffset).coerceIn(0, options.size - 1); dragChange.consume() }; if (event.changes.any { !it.pressed }) { if (isLongPress) { currentOnIntervalSelected(options[selectedIndexByDrag]); isPickerVisible = false } else currentOnClick(); break } } } } }.size(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) { Icon(imageVector = if (isForward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp)); Text(text = currentInterval.toString(), color = Color.White, fontSize = com.example.gallery.ui.AppConstants.BottomNavFontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            Text("skip", color = Color.White, fontSize = com.example.gallery.ui.AppConstants.BottomNavFontSize)
        }
        if (isPickerVisible) { Popup(alignment = Alignment.Center, offset = IntOffset(0, 0)) { Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp), modifier = Modifier.width(60.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) { options.forEachIndexed { index, value -> val isSelected = index == selectedIndexByDrag; Text(text = value.toString(), color = if (isSelected) Color.Cyan else Color.White, fontSize = if (isSelected) com.example.gallery.ui.AppConstants.HeaderFontSize else com.example.gallery.ui.AppConstants.SubtitleFontSize, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(vertical = 8.dp)) } } } } }
    }
}

@Composable
fun MediaInfoRow(label: String, value: String) { Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) { Text(text = label, color = Color.Gray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, modifier = Modifier.width(100.dp)); Text(text = value, color = Color.LightGray, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, modifier = Modifier.weight(1f)) } }

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
        throw IllegalStateException("画像ファイルが空です")
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

    throw IllegalStateException("画像を開けませんでした")
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
                        isLoading -> "ascii2dを読み込み中..."
                        hasProvidedFile -> "画像をセットしました。ascii2dの検索ボタンを押してください"
                        hasRequestedFile -> "ページ内のファイル選択を押すと、この画像をセットします"
                        else -> "画像選択を準備中..."
                    },
                    color = Color.White,
                    fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
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
    videoPosition: Long = 0L, videoDuration: Long = 0L, isSeeking: Boolean = false
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        logVideoViewerTrace("player_create uriHash=${uri.hashCode()}")
        ExoPlayer.Builder(context).setRenderersFactory(
            androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ).build().apply {
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
            repeatMode = Player.REPEAT_MODE_ONE
            // 常に高精度でシークできるように初期化する。
            setSeekParameters(SeekParameters.EXACT)
            prepare()
            // 読み込み完了を待たず、表示上の開始位置へシークを受け付ける。
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

    // シーク要求への反応 (Slider など外部操作)。
    LaunchedEffect(seekToPosition) {
        if (seekToPosition >= 0 && seekToPosition != lastInternalSeek.longValue) {
            exoPlayer.setSeekParameters(SeekParameters.EXACT)
            exoPlayer.seekTo(seekToPosition)
            lastInternalSeek.longValue = seekToPosition
        }
    }

    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(Color.Black)
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }, onDoubleTap = { onDoubleTap() }) }
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
                                    // 開始位置からの累積移動量で計算する。
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
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    logVideoViewerTrace("player_view_create uriHash=${uri.hashCode()} viewHash=${hashCode()}")
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(false)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    controllerAutoShow = false
                    hideController()
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = {
                logVideoViewerTrace(
                    "player_view_update uriHash=${uri.hashCode()} viewHash=${it.hashCode()} " +
                        "isPlaying=$isPlaying muted=$isMuted scroll=$isScrollInProgress"
                )
                exoPlayer.playWhenReady = isPlaying
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier.matchParentSize()
        )

        // 操作フィードバック。
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
                            text = "コマ送り",
                            color = Color.Cyan,
                            fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}",
                            color = Color.White,
                            fontSize = com.example.gallery.ui.AppConstants.HeaderFontSize,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GifPlayer(uri: String, isUiVisible: Boolean, onToggleUi: () -> Unit, scale: Float, offsetX: Float, offsetY: Float, onZoomPan: (Float, Offset) -> Unit, onVerticalDrag: (Float) -> Unit, onDragEnd: () -> Unit, onDoubleTap: () -> Unit, imageLoader: ImageLoader, isFrameSteppingVisible: Boolean, gifFrames: List<Bitmap>, currentFrameIndex: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(uri).build(), imageLoader = imageLoader)
    Box(modifier = modifier.background(Color.Black).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }, onDoubleTap = { onDoubleTap() }) }
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
fun RecommendationCard(mediaItem: MediaData, score: String?, imageLoader: ImageLoader, isDeleted: Boolean = false, onClick: () -> Unit) { Box(modifier = Modifier.size(110.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray).clickable { onClick() }) { Image(painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(mediaItem.uri).apply { if (mediaItem.isVideo) videoFrameMillis(1000) }.build(), imageLoader = imageLoader), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit); if (score != null) Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(score, color = Color.White, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize) }; if (isDeleted) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.Delete, contentDescription = "削除済み", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp)) } } }

private fun captureVideoFrame(context: android.content.Context, uriString: String, positionMs: Long, onResult: (Bitmap?) -> Unit) { val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO); scope.launch { val retriever = MediaMetadataRetriever(); try { val uri = Uri.parse(uriString); context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd -> retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } ?: retriever.setDataSource(context, uri); val bitmap = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); withContext(Dispatchers.Main) { onResult(bitmap) } } catch (e: Exception) { e.printStackTrace(); withContext(Dispatchers.Main) { onResult(null) } } finally { try { retriever.release() } catch (e: Exception) {} } } }

private fun formatTime(ms: Long): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    val hundredths = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}

private fun extractGifFrames(context: android.content.Context, uriString: String, scope: kotlinx.coroutines.CoroutineScope, onResult: (List<Bitmap>) -> Unit) { scope.launch(Dispatchers.IO) { val frames = mutableListOf<Bitmap>(); val uri = Uri.parse(uriString); val retriever = MediaMetadataRetriever(); var retrieverSuccess = false; try { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd -> retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } ?: retriever.setDataSource(context, uri); val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); val durationMs = durationStr?.toLong() ?: 0L; val frameCount = 30; if (durationMs > 0) { for (i in 0 until frameCount) { val timeUs = (durationMs * 1000 / frameCount) * i; val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST); if (bitmap != null) frames.add(bitmap) } } else { for (i in 0 until frameCount) { val timeUs = i * 100000L; val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST); if (bitmap != null) frames.add(bitmap) else if (i > 10) break } }; if (frames.isNotEmpty()) retrieverSuccess = true } catch (e: Exception) { e.printStackTrace() } finally { try { retriever.release() } catch (e: Exception) {} }; if (!retrieverSuccess) { try { context.contentResolver.openInputStream(uri)?.use { inputStream -> @Suppress("DEPRECATION") val movie = Movie.decodeStream(inputStream); if (movie != null) { val durationMs = movie.duration().coerceAtLeast(1); val frameCount = 30; val width = movie.width().coerceAtLeast(1); val height = movie.height().coerceAtLeast(1); if (width > 0 && height > 0) { for (i in 0 until frameCount) { val timeMs = (durationMs / frameCount) * i; movie.setTime(timeMs); val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); movie.draw(canvas, 0f, 0f); frames.add(bitmap) } } } } } catch (e: Exception) { e.printStackTrace() } }; scope.launch(Dispatchers.Main) { if (frames.isEmpty()) Toast.makeText(context, "コマの抽出に失敗しました", Toast.LENGTH_SHORT).show(); onResult(frames) } } }

private fun saveBitmapToScreenshots(context: android.content.Context, bitmap: Bitmap) { val filename = "Screenshot_${System.currentTimeMillis()}.png"; val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, filename); put(MediaStore.MediaColumns.MIME_TYPE, "image/png"); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots"); put(MediaStore.MediaColumns.IS_PENDING, 1) } }; val resolver = context.contentResolver; try { val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues); if (uri != null) { resolver.openOutputStream(uri)?.use { outputStream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { contentValues.clear(); contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0); resolver.update(uri, contentValues, null, null) }; (context as Activity).runOnUiThread { Toast.makeText(context, "スクリーンショットを保存しました", Toast.LENGTH_SHORT).show() } } } catch (e: Exception) { Log.e("PictureViewer", "Error saving screenshot", e); (context as Activity).runOnUiThread { Toast.makeText(context, "保存に失敗しました", Toast.LENGTH_SHORT).show() } } }

private fun setAsWallpaper(context: android.content.Context, uriString: String) { val wallpaperManager = WallpaperManager.getInstance(context); val uri = Uri.parse(uriString); try { val intent = wallpaperManager.getCropAndSetWallpaperIntent(uri); context.startActivity(intent) } catch (e: Exception) { try { val inputStream = context.contentResolver.openInputStream(uri); wallpaperManager.setStream(inputStream); Toast.makeText(context, "壁紙に設定しました", Toast.LENGTH_SHORT).show() } catch (e2: Exception) { Log.e("PictureViewer", "Failed to set wallpaper", e2); Toast.makeText(context, "壁紙の設定に失敗しました", Toast.LENGTH_SHORT).show() } } }

private fun setBitmapAsWallpaper(context: android.content.Context, bitmap: Bitmap) { val wallpaperManager = WallpaperManager.getInstance(context); try { wallpaperManager.setBitmap(bitmap); Toast.makeText(context, "今表示しているコマを壁紙に設定しました", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Log.e("PictureViewer", "Failed to set bitmap as wallpaper", e); Toast.makeText(context, "壁紙の設定に失敗しました", Toast.LENGTH_SHORT).show() } }

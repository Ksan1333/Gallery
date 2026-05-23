package com.example.gallery.ui.component

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.window.Popup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
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
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.AgeRatingFilter
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PictureViewer(
    imageList: List<MediaData>,
    initialPage: Int,
    onClickedClose: () -> Unit,
    modifier: Modifier = Modifier,
    galleryState: GalleryState? = null,
    onNavigateToMedia: ((String) -> Unit)? = null,
    onNavigateToTag: ((String) -> Unit)? = null,
    onPageSelected: ((Int) -> Unit)? = null,
    showDeleteButton: Boolean = true,
    isTrashMode: Boolean = false
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
    var screenOrientation by rememberSaveable { mutableIntStateOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }

    var isRecommendationVisible by rememberSaveable { mutableStateOf(false) }
    val recommendationDragOffset = remember { Animatable(0f) }

    val currentMedia = remember(pagerState.currentPage, imageList) { imageList.getOrNull(pagerState.currentPage) }

    val currentMetadata by remember(currentMedia?.uri) {
        galleryState?.repository?.mediaDao?.getMetadataSummaryFlow(currentMedia?.uri ?: "")
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val currentMediaTagsFlow by remember(currentMedia?.uri) {
        galleryState?.repository?.getTagsForMedia(currentMedia?.uri ?: "")
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var recommendedMediaByVisual by remember { mutableStateOf<List<com.example.gallery.data.repository.MediaRepository.MediaSimilarity>>(emptyList()) }
    var recommendedMediaByTags by remember { mutableStateOf<List<com.example.gallery.data.repository.MediaRepository.MediaSimilarity>>(emptyList()) }
    var randomMediaList by remember { mutableStateOf<List<MediaData>>(emptyList()) }

    val deletedUris by (galleryState?.repository?.mediaDao?.getDeletedMetadataSummaryFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
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
        onPageSelected?.invoke(pagerState.currentPage)
        // ページ切り替え時に詳細パネルの状態を完全にリセット
        isRecommendationVisible = false
        recommendationDragOffset.snapTo(0f)

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

    LaunchedEffect(isUiVisible, insetsController) {
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!isUiVisible) insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        else insetsController?.show(WindowInsetsCompat.Type.systemBars())
    }

    SideEffect {
        if (screenOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            (context as? Activity)?.requestedOrientation = screenOrientation
        }
    }

    DisposableEffect(Unit) {
        onDispose { (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(isRecommendationVisible) {
        if (isRecommendationVisible && currentMedia != null) {
            val currentAgeRating = currentMetadata?.ageRating ?: "SFW"
            if (currentMedia.isVideo) {
                val allMedia = galleryState?.repository?.getAllMedia() ?: emptyList()
                randomMediaList = allMedia.filter { it.isVideo && it.uri != currentMedia.uri }.shuffled().take(20)
                scope.launch {
                    if (currentMetadata?.isAiAnalyzed == true) {
                        recommendedMediaByTags = galleryState?.repository?.findMediaByTagSimilarity(currentMedia.uri) ?: emptyList()
                    } else if (galleryState != null) {
                        galleryState.aiTaggingService.analyzeSingle(currentMedia)
                        recommendedMediaByTags = galleryState.repository.findMediaByTagSimilarity(currentMedia.uri)
                    }
                }
                recommendedMediaByVisual = emptyList()
            } else {
                scope.launch { randomMediaList = galleryState?.repository?.getRandomMediaByAgeRating(20, currentAgeRating) ?: emptyList() }
                scope.launch {
                    if (currentMetadata?.isAiAnalyzed == true) {
                        recommendedMediaByTags = galleryState?.repository?.findMediaByTagSimilarity(currentMedia.uri) ?: emptyList()
                    } else if (galleryState != null) {
                        galleryState.aiTaggingService.analyzeSingle(currentMedia)
                        recommendedMediaByTags = galleryState.repository.findMediaByTagSimilarity(currentMedia.uri)
                    }
                }
                scope.launch {
                    if (currentMetadata?.hasFeatureVector == true) {
                        recommendedMediaByVisual = galleryState?.repository?.findSimilarVisualMedia(currentMedia.uri) ?: emptyList()
                    } else if (galleryState != null) {
                        galleryState.vectorSearchService.analyzeSingle(currentMedia)
                        recommendedMediaByVisual = galleryState.repository.findSimilarVisualMedia(currentMedia.uri)
                    }
                }
            }
        }
    }

    val onShowSimilarity: (MediaData) -> Unit = {
        isRecommendationVisible = true
        scope.launch { recommendationDragOffset.snapTo(0f) }
    }

    val onToggle = { if (!isRecommendationVisible) isUiVisible = !isUiVisible }
    
    val density = LocalDensity.current
    val maxRecDrag = with(density) { 360.dp.toPx() } // 600dp -> 360dp (約40%程度)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPageZoomed && !isRecommendationVisible && !isVerticalSwiping,
            beyondViewportPageCount = 1
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

            Box(modifier = Modifier.fillMaxSize()) {
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
                                offsetX.snapTo(if (newScale > 1.05f) offsetX.value + p.x * newScale else 0f)
                                offsetY.snapTo(offsetY.value + p.y * newScale)
                            }
                        },
                        onVerticalDrag = { drag -> 
                            isVerticalSwiping = true
                            scope.launch { 
                                val newOffset = offsetY.value + drag
                                
                                // 上方向へのスワイプ（レコメンド表示）
                                if (newOffset < -10f && !isRecommendationVisible) {
                                    isRecommendationVisible = true
                                    recommendationDragOffset.snapTo(maxRecDrag)
                                }
                                
                                if (isRecommendationVisible) {
                                    // レコメンドが表示されているときは、画像（offsetY）は動かさず
                                    // パネル（recommendationDragOffset）だけを動かす
                                    val currentRecOffset = recommendationDragOffset.value
                                    recommendationDragOffset.snapTo((currentRecOffset + drag).coerceIn(0f, maxRecDrag))
                                } else {
                                    // それ以外（下スワイプで閉じる動作など）は画像を動かす
                                    offsetY.snapTo(newOffset)
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
                                if (recommendationDragOffset.value > maxRecDrag * 0.75f) {
                                    scope.launch { 
                                        recommendationDragOffset.animateTo(maxRecDrag, tween(250))
                                        isRecommendationVisible = false
                                        recommendationDragOffset.snapTo(0f)
                                    }
                                } else {
                                    scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) }
                                }
                            }
                            
                            if (scale.value < 0.95f) {
                                scope.launch { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } }
                            } else if (scale.value <= 1.05f) {
                                if (!isRecommendationVisible && offsetY.value > 250f) onClickedClose()
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
                                offsetX.snapTo(if (newScale > 1.05f) offsetX.value + p.x * newScale else 0f)
                                offsetY.snapTo(offsetY.value + p.y * newScale)
                            }
                        },
                        onVerticalDrag = { drag: Float -> 
                            isVerticalSwiping = true
                            scope.launch { 
                                val newOffset = offsetY.value + drag
                                
                                if (newOffset < -10f && !isRecommendationVisible) {
                                    isRecommendationVisible = true
                                    recommendationDragOffset.snapTo(maxRecDrag)
                                }
                                
                                if (isRecommendationVisible) {
                                    val currentRecOffset = recommendationDragOffset.value
                                    recommendationDragOffset.snapTo((currentRecOffset + drag).coerceIn(0f, maxRecDrag))
                                } else {
                                    offsetY.snapTo(newOffset)
                                }
                            }
                        },
                        onDragEnd = {
                            isVerticalSwiping = false
                            if (isRecommendationVisible) {
                                if (recommendationDragOffset.value > maxRecDrag * 0.75f) {
                                    scope.launch { 
                                        recommendationDragOffset.animateTo(maxRecDrag, tween(250))
                                        isRecommendationVisible = false
                                        delay(250)
                                        recommendationDragOffset.snapTo(0f)
                                    }
                                } else {
                                    scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) }
                                }
                            }
                            
                            if (scale.value < 0.95f) {
                                scope.launch { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } }
                            } else {
                                if (!isRecommendationVisible && offsetY.value > 300f) onClickedClose()
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
                    val finalUri = remember(mediaItem.uri) {
                        if (mediaItem.uri.startsWith("mock://picsum/")) {
                            val id = mediaItem.uri.substringAfter("mock://picsum/").substringBefore("?")
                            val w = mediaItem.uri.substringAfter("w=").substringBefore("&")
                            val h = mediaItem.uri.substringAfter("h=")
                            "https://picsum.photos/seed/$id/$w/$h"
                        } else mediaItem.uri
                    }
                    Image(
                        painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(finalUri).build(), imageLoader = imageLoader),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; translationX = offsetX.value; translationY = offsetY.value }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val currentScale = scale.value
                                        val isZoomed = currentScale > 1.01f
                                        if (event.changes.size >= 2 || isZoomed) {
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                scope.launch {
                                                    val newScale = (currentScale * zoomChange).coerceIn(0.5f, 5f)
                                                    scale.snapTo(newScale)
                                                    val panFactor = if (newScale < 1f) 0.5f else 1f
                                                    offsetX.snapTo(if (newScale > 1.05f) offsetX.value + panChange.x * newScale * panFactor else 0f)
                                                    offsetY.snapTo(offsetY.value + panChange.y * newScale * panFactor)
                                                }
                                            }
                                            event.changes.forEach { if (it.pressed) it.consume() }
                                        } else {
                                            if (panChange != Offset.Zero && Math.abs(panChange.y) > Math.abs(panChange.x) * 3.0f) {
                                                isVerticalSwiping = true
                                                scope.launch { 
                                                    val newOffset = offsetY.value + panChange.y
                                                    
                                                    if (newOffset < -10f && !isRecommendationVisible) {
                                                        isRecommendationVisible = true
                                                        recommendationDragOffset.snapTo(maxRecDrag)
                                                    }
                                                    
                                                    if (isRecommendationVisible) {
                                                        val currentRecOffset = recommendationDragOffset.value
                                                        recommendationDragOffset.snapTo((currentRecOffset + panChange.y).coerceIn(0f, maxRecDrag))
                                                    } else {
                                                        offsetY.snapTo(newOffset)
                                                    }
                                                }
                                                event.changes.forEach { if (it.pressed) it.consume() }
                                            }
                                        }
                                        if (event.changes.all { !it.pressed }) { isVerticalSwiping = false; break }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onToggle() }, onDoubleTap = {
                                    val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                                    scope.launch { if (targetScale == 1f) { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } else scale.animateTo(3.0f) }
                                })
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.all { !it.pressed }) {
                                            if (isRecommendationVisible) {
                                                if (recommendationDragOffset.value > maxRecDrag * 0.75f) {
                                                    scope.launch { 
                                                        recommendationDragOffset.animateTo(maxRecDrag, tween(250))
                                                        isRecommendationVisible = false
                                                        recommendationDragOffset.snapTo(0f)
                                                    }
                                                } else {
                                                    scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) }
                                                }
                                            }

                                            if (scale.value < 0.95f) { scope.launch { launch { scale.animateTo(1f) }; launch { offsetX.animateTo(0f) }; launch { offsetY.animateTo(0f) } } }
                                            else if (scale.value <= 1.05f) {
                                                if (!isRecommendationVisible && offsetY.value > 250f) onClickedClose()
                                                else scope.launch { offsetY.animateTo(0f) }
                                            }
                                        }
                                    }
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        if (isUiVisible && !isRecommendationVisible) {
            CommonFloatingCloseButton(onClick = { onClickedClose() }, modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp, end = 16.dp))
        }

        if (isUiVisible && !isCurrentPageZoomed && !isRecommendationVisible) {
            val currentMediaItem = imageList.getOrNull(pagerState.currentPage)
            if (currentMediaItem != null) {
                var showTagDialog by remember { mutableStateOf(false) }
                LaunchedEffect(showTagDialog) {
                    if (showTagDialog) {
                        delay(100)
                        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }

                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.85f)).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 2.dp)) {
                    if (currentMediaItem.isVideo && videoDuration > 0 && !pagerState.isScrollInProgress) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
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
                                            isSeeking = true
                                            videoPosition = it.toLong()
                                            seekTargetPosition = it.toLong()
                                            isVideoPlaying = false 
                                        },
                                        onValueChangeFinished = { 
                                            scope.launch { 
                                                delay(50)
                                                isSeeking = false
                                                seekTargetPosition = -1L
                                                // シーク前の再生状態を復元
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
                                    // スライダーの光る演出 (thumbの位置に追従)
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
                                CommonFloatingActionButton(icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, onClick = { isMuted = !isMuted }, size = 32.dp, iconSize = 18.dp)
                            }
                            SeekControlRow(currentPosition = videoPosition, duration = videoDuration, onSeekRequested = { target ->
                                seekTargetPosition = target; videoPosition = target; isSeeking = true
                                scope.launch { delay(100); isSeeking = false; seekTargetPosition = -1L }
                            }, galleryState = galleryState)
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
                        // 1. ゴミ箱 / 復元 (一番左)
                        galleryState?.let { _ -> 
                            if (showDeleteButton) { 
                                CommonFloatingActionButton(icon = Icons.Default.Delete, tooltipDescription = "ゴミ箱へ移動", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { galleryState.repository.moveToTrash(listOf(currentMediaItem.uri)); Toast.makeText(context, "ゴミ箱へ移動しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.White) 
                            } else { 
                                Row {
                                    CommonFloatingActionButton(icon = Icons.Default.DeleteForever, tooltipDescription = "完全に削除", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { galleryState.repository.permanentlyDelete(listOf(currentMediaItem.uri)); Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.Red)
                                    Spacer(Modifier.width(8.dp))
                                    CommonFloatingActionButton(icon = Icons.Default.Restore, tooltipDescription = "復元", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { galleryState.repository.restoreFromTrash(listOf(currentMediaItem.uri)); Toast.makeText(context, "復元しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.White) 
                                }
                            } 
                        }

                        // 2. 回転
                        CommonFloatingActionButton(icon = Icons.Default.ScreenRotation, tooltipDescription = "回転 (縦横切替)", size = 36.dp, iconSize = 20.dp, onClick = { val target = if (screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; screenOrientation = target; (context as Activity).requestedOrientation = target })
                        
                        // 3. 再生制御
                        CommonFloatingActionButton(
                            icon = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                            tooltipDescription = if (isVideoPlaying) "一時停止" else "再生", 
                            size = 36.dp, 
                            iconSize = 20.dp, 
                            enabled = currentMediaItem.isVideo, 
                            contentColor = if (currentMediaItem.isVideo) Color.White else Color.Gray, 
                            onClick = { isVideoPlaying = !isVideoPlaying }
                        )

                        // 4. お気に入り
                        galleryState?.let { state -> 
                            val favorites by state.repository.getFavoriteMedia().collectAsState(initial = emptyList())
                            val isFavorite = favorites.any { it.uri == currentMediaItem.uri }
                            CommonFloatingActionButton(icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tooltipDescription = "お気に入り切替", size = 36.dp, iconSize = 20.dp, onClick = { scope.launch { state.repository.toggleFavorite(currentMediaItem.uri) } }, contentColor = if (isFavorite) Color.Red else Color.White) 
                        }

                        // 5. 三点ボタン (その他)
                        var showOverflowMenu by remember { mutableStateOf(false) }
                        Box {
                            CommonFloatingActionButton(icon = Icons.Default.MoreVert, tooltipDescription = "その他", size = 36.dp, iconSize = 20.dp, onClick = { showOverflowMenu = true })
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(Color(0xFF2A2A2A))
                            ) {
                                // GIFコマ送り (GIFのみ活性)
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
                                
                                // 保存 (動画・GIF・コマ送り中のみ活性)
                                val isSaveEnabled = currentMediaItem.isVideo || currentMediaItem.isGif
                                DropdownMenuItem(
                                    text = { Text("フレーム保存", color = if (isSaveEnabled) Color.White else Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Screenshot, null, tint = if (isSaveEnabled) Color.White else Color.Gray) },
                                    enabled = isSaveEnabled,
                                    onClick = {
                                        showOverflowMenu = false
                                        if (currentMediaItem.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) saveBitmapToScreenshots(context, gifFrames[currentFrameIndex]) 
                                        else if (currentMediaItem.isVideo) captureVideoFrame(context, currentMediaItem.uri, videoPosition) { bitmap -> if (bitmap != null) saveBitmapToScreenshots(context, bitmap) else Toast.makeText(context, "動画フレームの取得に失敗しました", Toast.LENGTH_SHORT).show() }
                                        else if (currentMediaItem.isGif) Toast.makeText(context, "GIFはコマ送りモードで保存してください", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                
                                // 壁紙設定 (動画以外、GIFはコマ送り中のみ)
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
                                
                                // タグ編集 (全メディア共通)
                                galleryState?.let { _ ->
                                    DropdownMenuItem(
                                        text = { Text("タグ編集", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Default.LocalOffer, null, tint = Color.White) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showTagDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (showTagDialog) { galleryState?.let { state -> UnifiedMediaEditDialog(uris = listOf(currentMediaItem.uri), repository = state.repository, onDismiss = { showTagDialog = false }) } }
            }
        }

        AnimatedVisibility(visible = isRecommendationVisible, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val density = LocalDensity.current
            val maxDrag = with(density) { 360.dp.toPx() } // 約40%に調整
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.42f).offset { IntOffset(0, recommendationDragOffset.value.roundToInt()) }.background(Color.Black.copy(alpha = 0.95f)).pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (recommendationDragOffset.value > 100f) { scope.launch { recommendationDragOffset.animateTo(maxDrag, tween(250)); isRecommendationVisible = false; delay(250); recommendationDragOffset.snapTo(0f) } } else { scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) } } }, onVerticalDrag = { change, dragAmount -> change.consume(); scope.launch { recommendationDragOffset.snapTo((recommendationDragOffset.value + dragAmount).coerceAtLeast(0f)) } }) }) {
                Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) { Surface(modifier = Modifier.width(40.dp).height(4.dp), color = Color.Gray.copy(alpha = 0.5f), shape = CircleShape) {} }
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("詳細・レコメンド", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text("閉じる", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.clickable { isRecommendationVisible = false }.padding(8.dp)) }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        val currentMediaItem = imageList.getOrNull(pagerState.currentPage)
                        if (currentMediaItem != null) {
                            if (!currentMediaItem.isVideo && !isTrashMode) {
                                item { Text("似た雰囲気の画像 (AIベクトル)", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item { if (currentMetadata?.hasFeatureVector != true) { Text("この画像は未分析です。分析すると似た画像を見つけられるようになります。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) } else if (recommendedMediaByVisual.isEmpty()) { Text("似た画像が見つかりませんでした。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) } else { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(recommendedMediaByVisual) { _, similarity -> RecommendationCard(mediaItem = similarity.media, score = "${(similarity.similarityScore * 100).toInt()}%", imageLoader = imageLoader, isDeleted = deletedUriSet.contains(similarity.media.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == similarity.media.uri }; if (index != -1) { scope.launch { pagerState.scrollToPage(index); onPageSelected?.invoke(index) } } else onNavigateToMedia?.invoke(similarity.media.uri) } } } } }
                            } else if (!isTrashMode) {
                                item { Text("ランダムな動画", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item { if (randomMediaList.isEmpty()) Text("動画が見つかりませんでした", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) else { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(randomMediaList) { _, item -> RecommendationCard(mediaItem = item, score = null, imageLoader = imageLoader, isDeleted = deletedUriSet.contains(item.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == item.uri }; if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(item.uri) } } } } }
                            }
                            item {
                                val normalTags = remember(currentMediaTagsFlow) { currentMediaTagsFlow.filter { !it.tag.endsWith("系") && it.confidence >= 0.6f }.sortedByDescending { it.confidence } }
                                val currentAgeRating = currentMetadata?.ageRating ?: "SFW"
                                
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("タグ", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Surface(color = when(currentAgeRating) { "R18" -> Color.Red.copy(alpha = 0.8f); "R15" -> Color.Yellow.copy(alpha = 0.8f); else -> Color.Green.copy(alpha = 0.8f) }, shape = RoundedCornerShape(4.dp)) { 
                                            Text(text = if (isTrashMode) "$currentAgeRating ゴミ" else currentAgeRating, color = if (currentAgeRating == "R15") Color.Black else Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) 
                                        }
                                    }
                                    
                                    if (!currentMediaItem.isVideo && currentMetadata?.isAiAnalyzed != true) { 
                                        Text("AIタグ未分析です。分析すると関連アイテムが表示されるようになります。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) 
                                    }
                                    
                                    Spacer(Modifier.height(8.dp))
                                    
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        normalTags.forEach { tag ->
                                            InputChip(
                                                selected = false,
                                                onClick = { isRecommendationVisible = false; onNavigateToTag?.invoke(tag.tag) },
                                                label = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(tag.tag, color = Color.White)
                                                        if (tag.confidence > 0f && tag.confidence < 1f) {
                                                            Text(text = " ${(tag.confidence * 100).toInt()}%", color = Color.Gray, fontSize = 10.sp)
                                                        }
                                                    }
                                                },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "削除",
                                                        modifier = Modifier.size(16.dp).clickable {
                                                            scope.launch {
                                                                galleryState?.repository?.mediaDao?.deleteTag(tag)
                                                            }
                                                        },
                                                        tint = Color.Gray
                                                    )
                                                },
                                                colors = InputChipDefaults.inputChipColors(containerColor = Color.DarkGray)
                                            )
                                        }
                                        if (!isTrashMode) {
                                            IconButton(onClick = { /* showTagDialog logic handled externally */ }, modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) { 
                                                Icon(Icons.Default.Add, contentDescription = "タグ追加", tint = Color.White, modifier = Modifier.size(16.dp)) 
                                            }
                                        }
                                    }
                                }
                            }
                            item { if (!isTrashMode) { if (recommendedMediaByTags.isEmpty()) Text("関連アイテムがありません", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(recommendedMediaByTags) { _, similarity -> RecommendationCard(mediaItem = similarity.media, score = "${(similarity.similarityScore * 100).toInt()}%", imageLoader = imageLoader, isDeleted = deletedUriSet.contains(similarity.media.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == similarity.media.uri }; if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(similarity.media.uri) } } } } }
                            if (!currentMediaItem.isVideo && !isTrashMode) {
                                item { Text("ランダムな画像", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item { if (randomMediaList.isEmpty()) Text("読込中...", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(randomMediaList) { _, item -> RecommendationCard(mediaItem = item, score = null, imageLoader = imageLoader, isDeleted = deletedUriSet.contains(item.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == item.uri }; if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(item.uri) } } } }
                            }
                            item { val media = currentMediaItem; Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) { HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 16.dp)); Text("ファイル情報", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)); MediaInfoRow("日時", SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(media.dateAdded))); MediaInfoRow("ファイル名", media.fileName); val actualPath = remember(media.uri) { try { val cursor = context.contentResolver.query(Uri.parse(media.uri), arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null); cursor?.use { if (it.moveToFirst()) it.getString(0) else media.uri } ?: media.uri } catch (e: Exception) { media.uri } }; MediaInfoRow("ファイルパス", actualPath); val labels = currentMediaTagsFlow.filter { !it.tag.endsWith("系") }.map { it.tag }; if (labels.isNotEmpty()) MediaInfoRow("画像情報", labels.joinToString(", ")); MediaInfoRow("画像サイズ", formatFileSize(media.fileSize)); if (media.width > 0 && media.height > 0) MediaInfoRow("画像の幅x高さ", "${media.width} x ${media.height}") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeekControlRow(currentPosition: Long, duration: Long, onSeekRequested: (Long) -> Unit, galleryState: GalleryState?) {
    val currentInterval = galleryState?.videoSeekInterval ?: 10
    Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        SeekButtonWithPicker(isForward = false, currentInterval = currentInterval, onIntervalSelected = { galleryState?.videoSeekInterval = it }, onClick = { onSeekRequested((currentPosition - currentInterval * 1000L).coerceAtLeast(0L)) })
        Spacer(modifier = Modifier.width(32.dp))
        SeekButtonWithPicker(isForward = true, currentInterval = currentInterval, onIntervalSelected = { galleryState?.videoSeekInterval = it }, onClick = { onSeekRequested((currentPosition + currentInterval * 1000L).coerceAtMost(duration)) })
    }
}

@Composable
fun SeekButtonWithPicker(isForward: Boolean, currentInterval: Int, onIntervalSelected: (Int) -> Unit, onClick: () -> Unit) {
    val options = listOf(1, 2, 5, 10, 30, 60)
    var isPickerVisible by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val currentOnClick by rememberUpdatedState(onClick); val currentOnIntervalSelected by rememberUpdatedState(onIntervalSelected)
    val selectedIndexByDrag = remember(dragY, currentInterval) { val step = 40f; val indexOffset = (dragY / step).roundToInt(); val baseIndex = options.indexOf(currentInterval).let { if (it == -1) 1 else it }; (baseIndex + indexOffset).coerceIn(0, options.size - 1) }
    Box(contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.pointerInput(currentInterval) { awaitPointerEventScope { while (true) { val down = awaitFirstDown(); val startTimestamp = System.currentTimeMillis(); var isLongPress = false; dragY = 0f; while (true) { val event = awaitPointerEvent(); val currentTime = System.currentTimeMillis(); if (currentTime - startTimestamp > 400 && !isLongPress) { isLongPress = true; isPickerVisible = true }; if (isLongPress) { val dragChange = event.changes.first(); dragY = dragChange.position.y - down.position.y; dragChange.consume() }; if (event.changes.any { !it.pressed }) { if (isLongPress) { currentOnIntervalSelected(options[selectedIndexByDrag]); isPickerVisible = false } else currentOnClick(); break } } } } }.size(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) { Icon(imageVector = if (isForward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp)); Text(text = currentInterval.toString(), color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            Text("skip", color = Color.White, fontSize = 9.sp)
        }
        if (isPickerVisible) { Popup(alignment = Alignment.Center, offset = IntOffset(0, 0)) { Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp), modifier = Modifier.width(60.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) { options.forEachIndexed { index, value -> val isSelected = index == selectedIndexByDrag; Text(text = value.toString(), color = if (isSelected) Color.Cyan else Color.White, fontSize = if (isSelected) 18.sp else 14.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(vertical = 8.dp)) } } } } }
    }
}

@Composable
fun MediaInfoRow(label: String, value: String) { Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) { Text(text = label, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(100.dp)); Text(text = value, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f)) } }

private fun formatFileSize(size: Long): String { if (size <= 0) return "0 B"; val units = arrayOf("B", "KB", "MB", "GB", "TB"); val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt(); return "%.1f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups]) }

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
        ExoPlayer.Builder(context).setRenderersFactory(
            androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ).build().apply { 
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            repeatMode = Player.REPEAT_MODE_ONE
            // 常に最高精度でシーク（コマ送り）できるように初期設定
            setSeekParameters(SeekParameters.EXACT)
            prepare()
            // 読み込み完了を待たずにシークを受け付けやすくするため、明示的に開始位置へ
            seekTo(0)
        } 
    }
    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }
    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }
    LaunchedEffect(exoPlayer, isPlaying, isScrollInProgress) { while (isPlaying && !isScrollInProgress) { if (exoPlayer.playbackState == Player.STATE_READY) onProgressChanged(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0)); delay(16) } }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    
    val lastInternalSeek = remember { mutableLongStateOf(-1L) }
    var scrubbingTouchPoint by remember { mutableStateOf<Offset?>(null) }
    
    // シーク要求への反応 (Sliderなど外部からの操作)
    LaunchedEffect(seekToPosition, isSeeking) {
        if (seekToPosition >= 0 && (isSeeking || Math.abs(exoPlayer.currentPosition - seekToPosition) > 1) && seekToPosition != lastInternalSeek.longValue) {
            exoPlayer.seekTo(seekToPosition)
        }
    }

    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(Color.Black).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }, onDoubleTap = { onDoubleTap() }) }
        .pointerInput(scale) {
            awaitPointerEventScope {
                while (true) {
                    val firstDown = awaitFirstDown()
                    val isScrubbingZone = firstDown.position.y > size.height * 0.6f
                    
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var decided = false
                    var isHorizontal = false
                    var scrubbingStartPosition = 0L
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        
                        if (event.changes.size >= 2 || scale > 1.01f) {
                            showTooltip = false
                            onZoomPan(zoomChange, panChange)
                            event.changes.forEach { it.consume() }
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
                                    // 開始位置からの累積移動量で計算
                                    val totalSeekDiff = (totalDragX * 5).toLong()
                                    val targetPos = (scrubbingStartPosition + totalSeekDiff).coerceIn(0, exoPlayer.duration)
                                    
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
            factory = { PlayerView(it).apply { player = exoPlayer; useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER); setShutterBackgroundColor(android.graphics.Color.BLACK); controllerAutoShow = false; hideController(); resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } }, 
            update = { 
                exoPlayer.playWhenReady = isPlaying
                exoPlayer.volume = if (isMuted) 0f else 1f 
            }, 
            modifier = Modifier.fillMaxSize()
        )

        // 操作フィードバック (光る演出)
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
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}",
                            color = Color.White,
                            fontSize = 20.sp,
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
    val context = LocalContext.current; val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(uri).build(), imageLoader = imageLoader)
    Box(modifier = modifier.background(Color.Black).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }, onDoubleTap = { onDoubleTap() }) }
        .pointerInput(scale) { awaitEachGesture { while (true) { val event = awaitPointerEvent(); val zoomChange = event.calculateZoom(); val panChange = event.calculatePan(); if (event.changes.size >= 2 || scale > 1.01f) { onZoomPan(zoomChange, panChange); event.changes.forEach { it.consume() } } else { if (panChange != Offset.Zero && Math.abs(panChange.y) > Math.abs(panChange.x) * 2.5f) { onVerticalDrag(panChange.y); event.changes.forEach { it.consume() } } }; if (event.changes.all { !it.pressed }) break } } }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.changes.all { !it.pressed }) onDragEnd() } } }
    ) { if (isFrameSteppingVisible && gifFrames.isNotEmpty()) Image(bitmap = gifFrames[currentFrameIndex].asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleUi() }, contentScale = ContentScale.Fit) else Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleUi() }, contentScale = ContentScale.Fit) }
}

@Composable
fun RecommendationCard(mediaItem: MediaData, score: String?, imageLoader: ImageLoader, isDeleted: Boolean = false, onClick: () -> Unit) { Box(modifier = Modifier.size(110.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() }) { Image(painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(mediaItem.uri).apply { if (mediaItem.isVideo) videoFrameMillis(1000) }.build(), imageLoader = imageLoader), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop); if (score != null) Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(score, color = Color.White, fontSize = 10.sp) }; if (isDeleted) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.Delete, contentDescription = "削除済み", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp)) } } }

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

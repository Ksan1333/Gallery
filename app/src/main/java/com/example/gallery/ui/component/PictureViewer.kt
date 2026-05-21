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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
import androidx.compose.ui.graphics.graphicsLayer
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
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.provider.MediaStore
import android.content.ContentValues
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
    onNavigateToMedia: ((String) -> Unit)? = null, // 外部への移動通知
    onNavigateToTag: ((String) -> Unit)? = null, // タグギャラリーへの移動通知
    onPageSelected: ((Int) -> Unit)? = null, // ページが切り替わったことを親に通知
    showDeleteButton: Boolean = true, // 追加: 削除ボタンを表示するかどうか
    isTrashMode: Boolean = false // 追加: ゴミ箱モード
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { imageList.size })

    // 回転時のデバッグログ
    LaunchedEffect(initialPage) {
        Log.d("PictureViewer", "Created/Recomposed with initialPage: $initialPage")
    }

    val thumbnailListState = rememberLazyListState()

    // カレントページのズーム状態を親が把握するための状態
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    // 回転時の位置維持と、おすすめからのジャンプを両立させるための仕組み
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

    // メタデータとタグをFlowで監視
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
    var isExtractingFrames by remember { mutableStateOf(false) }

    var isVideoPlaying by remember(pagerState.currentPage) { mutableStateOf(true) }
    var videoDuration by remember(pagerState.currentPage) { mutableLongStateOf(0L) }
    var videoPosition by remember(pagerState.currentPage) { mutableLongStateOf(0L) }
    var isMuted by rememberSaveable { mutableStateOf(true) }
    var isSeeking by remember(pagerState.currentPage) { mutableStateOf(false) }
    var seekTargetPosition by remember(pagerState.currentPage) { mutableLongStateOf(-1L) }

    // ページ切り替え時に親に通知し、状態をリセット
    LaunchedEffect(pagerState.currentPage) {
        onPageSelected?.invoke(pagerState.currentPage)
        // タイミングによっては上記 remember(currentPage) だけでは不十分な場合があるため明示的にもリセット
        videoPosition = 0L
        videoDuration = 0L
        isSeeking = false
        seekTargetPosition = -1L
        isVideoPlaying = true // 次の動画も自動再生
    }

    val imageLoader = context.imageLoader
    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(isUiVisible, insetsController) {
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!isUiVisible) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    SideEffect {
        if (screenOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            (context as? Activity)?.requestedOrientation = screenOrientation
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(isRecommendationVisible) {
        if (isRecommendationVisible && currentMedia != null) {
            val currentAgeRating = currentMetadata?.ageRating ?: "SFW"
            
            if (currentMedia.isVideo) {
                // 動画の場合はランダムな動画を取得 (全動画からランダム抽選)
                val allMedia = galleryState?.repository?.getAllMedia() ?: emptyList()
                randomMediaList = allMedia.filter { it.isVideo && it.uri != currentMedia.uri }.shuffled().take(20)
                
                // 動画でもAIタグセクションを復活させるため、必要データを取得
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
                // 1枚のBitmapデコードを待つ間に他のデータを並列で開始
                scope.launch {
                    randomMediaList = galleryState?.repository?.getRandomMediaByAgeRating(20, currentAgeRating) ?: emptyList()
                }

                scope.launch {
                    if (currentMetadata?.isAiAnalyzed == true) {
                        recommendedMediaByTags = galleryState?.repository?.findMediaByTagSimilarity(currentMedia.uri) ?: emptyList()
                    } else {
                        recommendedMediaByTags = emptyList()
                        if (galleryState != null) {
                            galleryState.aiTaggingService.analyzeSingle(currentMedia)
                            recommendedMediaByTags = galleryState.repository.findMediaByTagSimilarity(currentMedia.uri)
                        }
                    }
                }

                scope.launch {
                    if (currentMetadata?.hasFeatureVector == true) {
                        recommendedMediaByVisual = galleryState?.repository?.findSimilarVisualMedia(currentMedia.uri) ?: emptyList()
                    } else {
                        recommendedMediaByVisual = emptyList()
                        if (galleryState != null) {
                            galleryState.vectorSearchService.analyzeSingle(currentMedia)
                            recommendedMediaByVisual = galleryState.repository.findSimilarVisualMedia(currentMedia.uri)
                        }
                    }
                }
            }
        }
    }

    val onShowSimilarity: (MediaData) -> Unit = { mediaItem ->
        isRecommendationVisible = true
        scope.launch { recommendationDragOffset.snapTo(0f) }
    }

    val onToggle = {
        if (!isRecommendationVisible) {
            isUiVisible = !isUiVisible
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPageZoomed && !isRecommendationVisible,
            beyondViewportPageCount = 1
        ) { page ->
            val mediaItem = imageList[page]
            val scale = remember { Animatable(1f) }
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }

            LaunchedEffect(isRecommendationVisible) {
                if (isRecommendationVisible) {
                    offsetY.animateTo(0f)
                    offsetX.animateTo(0f)
                    scale.animateTo(1f)
                }
            }

            LaunchedEffect(scale.value, pagerState.currentPage) {
                if (pagerState.currentPage == page) {
                    isCurrentPageZoomed = scale.value > 1.01f
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (mediaItem.isVideo) {
                    VideoPlayer(
                        uri = mediaItem.uri, isUiVisible = isUiVisible,
                        isPlaying = isVideoPlaying && pagerState.currentPage == page,
                        isMuted = isMuted, seekToPosition = if (isSeeking) seekTargetPosition else -1L,
                        onToggleUi = onToggle,
                        onProgressChanged = { pos, dur ->
                            if (pagerState.currentPage == page && !isSeeking) {
                                videoPosition = pos
                                videoDuration = dur
                            }
                        },
                        scale = scale.value, offsetX = offsetX.value, offsetY = offsetY.value,
                        onZoomPan = { z, p ->
                            scope.launch {
                                val newScale = (scale.value * z).coerceIn(1f, 5f)
                                scale.snapTo(newScale)
                                if (newScale > 1.05f) {
                                    offsetX.snapTo(offsetX.value + p.x * newScale)
                                } else {
                                    offsetX.snapTo(0f)
                                }
                                offsetY.snapTo(offsetY.value + p.y * newScale)
                            }
                        },
                        onVerticalDrag = { drag ->
                            scope.launch { offsetY.snapTo(offsetY.value + drag) }
                        },
                        onDragEnd = {
                            if (scale.value < 0.95f) {
                                scope.launch {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                }
                            } else if (scale.value <= 1.05f) {
                                if (offsetY.value < -250f) onShowSimilarity(mediaItem)
                                else if (offsetY.value > 250f) onClickedClose()
                                else scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                        onDoubleTap = {
                            val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                            scope.launch {
                                if (targetScale == 1f) {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                } else {
                                    scale.animateTo(3.0f)
                                }
                            }
                        },
                        isScrollInProgress = pagerState.isScrollInProgress,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaItem.isGif) {
                    GifPlayer(
                        uri = mediaItem.uri, isUiVisible = isUiVisible, onToggleUi = onToggle,
                        scale = scale.value, offsetX = offsetX.value, offsetY = offsetY.value,
                        onZoomPan = { z, p ->
                            scope.launch {
                                val newScale = (scale.value * z).coerceIn(1f, 5f)
                                scale.snapTo(newScale)
                                if (newScale > 1.05f) {
                                    offsetX.snapTo(offsetX.value + p.x * newScale)
                                } else {
                                    offsetX.snapTo(0f)
                                }
                                offsetY.snapTo(offsetY.value + p.y * newScale)
                            }
                        },
                        onVerticalDrag = { drag ->
                            scope.launch { offsetY.snapTo(offsetY.value + drag) }
                        },
                        onDragEnd = {
                            if (scale.value < 0.95f) {
                                scope.launch {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                }
                            } else if (scale.value <= 1.05f) {
                                if (offsetY.value < -250f) onShowSimilarity(mediaItem)
                                else if (offsetY.value > 250f) onClickedClose()
                                else scope.launch { offsetY.animateTo(0f) }
                            }
                        },
                        onDoubleTap = {
                            val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                            scope.launch {
                                if (targetScale == 1f) {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                } else {
                                    scale.animateTo(3.0f)
                                }
                            }
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
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                translationX = offsetX.value
                                translationY = offsetY.value
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        
                                        val currentScale = scale.value
                                        val isZoomed = currentScale > 1.01f
                                        
                                        // 2本指操作またはズーム中ならこのレイヤーで消費
                                        if (event.changes.size >= 2 || isZoomed) {
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                scope.launch {
                                                    val newScale = (currentScale * zoomChange).coerceIn(0.5f, 5f)
                                                    scale.snapTo(newScale)
                                                    val panFactor = if (newScale < 1f) 0.5f else 1f
                                                    
                                                    // ズーム中のみ横移動を反映
                                                    if (newScale > 1.05f) {
                                                        offsetX.snapTo(offsetX.value + panChange.x * newScale * panFactor)
                                                    } else {
                                                        offsetX.snapTo(0f)
                                                    }
                                                    offsetY.snapTo(offsetY.value + panChange.y * newScale * panFactor)
                                                }
                                            }
                                            // イベントを消費
                                            event.changes.forEach { if (it.pressed) it.consume() }
                                        } else {
                                            // 非ズーム時かつ1本指: 垂直移動が強く支配的な場合のみ消費（Pagerに渡さない）
                                            if (panChange != Offset.Zero && Math.abs(panChange.y) > Math.abs(panChange.x) * 3.0f) {
                                                scope.launch { offsetY.snapTo(offsetY.value + panChange.y) }
                                                event.changes.forEach { if (it.pressed) it.consume() }
                                            }
                                            // 横方向（スワイプ）は消費せず、Pagerへ流す
                                        }
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onToggle() },
                                    onDoubleTap = {
                                        val targetScale = if (scale.value > 1.1f) 1f else 3.0f
                                        scope.launch {
                                            if (targetScale == 1f) {
                                                launch { scale.animateTo(1f) }
                                                launch { offsetX.animateTo(0f) }
                                                launch { offsetY.animateTo(0f) }
                                            } else {
                                                scale.animateTo(3.0f)
                                            }
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.all { !it.pressed }) {
                                            if (scale.value < 0.95f) {
                                                scope.launch {
                                                    launch { scale.animateTo(1f) }
                                                    launch { offsetX.animateTo(0f) }
                                                    launch { offsetY.animateTo(0f) }
                                                }
                                            } else if (scale.value <= 1.05f) {
                                                if (offsetY.value < -250f) onShowSimilarity(mediaItem)
                                                else if (offsetY.value > 250f) onClickedClose()
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
            val currentMedia = imageList.getOrNull(pagerState.currentPage)
            if (currentMedia != null) {
                var showTagDialog by remember { mutableStateOf(false) }
                LaunchedEffect(showTagDialog) {
                    if (showTagDialog) {
                        delay(100)
                        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.85f))
                        .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 2.dp)
                ) {
                    if (currentMedia.isVideo && videoDuration > 0 && !pagerState.isScrollInProgress) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "${formatTime(videoPosition)} / ${formatTime(videoDuration)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                                Slider(
                                    value = videoPosition.toFloat(),
                                    onValueChange = { isSeeking = true; videoPosition = it.toLong() },
                                    onValueChangeFinished = {
                                        seekTargetPosition = videoPosition
                                        scope.launch { delay(50); isSeeking = false; seekTargetPosition = -1L }
                                    },
                                    valueRange = 0f..videoDuration.toFloat().coerceAtLeast(1f),
                                    modifier = Modifier.weight(1f).height(24.dp),
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                CommonFloatingActionButton(icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.Default.VolumeUp, onClick = { isMuted = !isMuted }, size = 32.dp, iconSize = 18.dp)
                            }
                            SeekControlRow(currentPosition = videoPosition, duration = videoDuration, onSeekRequested = { target ->
                                seekTargetPosition = target; videoPosition = target; isSeeking = true
                                scope.launch { delay(100); isSeeking = false; seekTargetPosition = -1L }
                            }, galleryState = galleryState)
                        }
                    }

                    if (currentMedia.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) {
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
                    Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        CommonFloatingActionButton(icon = Icons.Default.ScreenRotation, tooltipDescription = "回転 (縦横切替)", size = 32.dp, iconSize = 18.dp, onClick = { val target = if (screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; screenOrientation = target; (context as Activity).requestedOrientation = target })
                        CommonFloatingActionButton(icon = if (isFrameSteppingVisible) Icons.Default.Close else Icons.Default.Collections, tooltipDescription = "GIFコマ送り切替", size = 32.dp, iconSize = 18.dp, enabled = currentMedia.isGif, onClick = { if (!isFrameSteppingVisible) { isExtractingFrames = true; extractGifFrames(context, currentMedia.uri, scope) { frames -> gifFrames = frames; isFrameSteppingVisible = true; isExtractingFrames = false } } else isFrameSteppingVisible = false }, contentColor = if (isFrameSteppingVisible) Color.Yellow else Color.White)
                        val isStaticImage = !currentMedia.isGif && !currentMedia.isVideo
                        CommonFloatingActionButton(icon = Icons.Default.Screenshot, tooltipDescription = "動画/GIFフレームを画像として保存", size = 32.dp, iconSize = 18.dp, enabled = !isStaticImage, contentColor = if (isStaticImage) Color.Gray.copy(alpha = 0.5f) else Color.White, onClick = { if (currentMedia.isGif && isFrameSteppingVisible && gifFrames.isNotEmpty()) saveBitmapToScreenshots(context, gifFrames[currentFrameIndex]) else if (currentMedia.isVideo) captureVideoFrame(context, currentMedia.uri, videoPosition) { bitmap -> if (bitmap != null) saveBitmapToScreenshots(context, bitmap) else Toast.makeText(context, "動画フレームの取得に失敗しました", Toast.LENGTH_SHORT).show() } else if (currentMedia.isGif) Toast.makeText(context, "GIFはコマ送りモードで保存してください", Toast.LENGTH_SHORT).show() })
                        CommonFloatingActionButton(icon = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, tooltipDescription = if (isVideoPlaying) "一時停止" else "再生", size = 32.dp, iconSize = 18.dp, enabled = true, contentColor = if (currentMedia.isVideo) Color.White else Color.Gray, onClick = { if (!currentMedia.isVideo) { Toast.makeText(context, "現在のメディア形式ではその操作はできません", Toast.LENGTH_SHORT).show(); return@CommonFloatingActionButton }; isVideoPlaying = !isVideoPlaying })
                        galleryState?.let { state -> CommonFloatingActionButton(icon = Icons.Default.LocalOffer, tooltipDescription = "タグ・年齢制限を編集", size = 32.dp, iconSize = 18.dp, onClick = { showTagDialog = true }) }
                        galleryState?.let { state -> val favorites by state.repository.getFavoriteMedia().collectAsState(initial = emptyList()); val isFavorite = favorites.any { it.uri == currentMedia.uri }; CommonFloatingActionButton(icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tooltipDescription = "お気に入り (Favorite) 切替", size = 32.dp, iconSize = 18.dp, onClick = { scope.launch { state.repository.toggleFavorite(currentMedia.uri) } }, contentColor = if (isFavorite) Color.Red else Color.White) }
                        galleryState?.let { state -> if (showDeleteButton) { CommonFloatingActionButton(icon = Icons.Default.Delete, tooltipDescription = "ゴミ箱へ移動", size = 32.dp, iconSize = 18.dp, onClick = { scope.launch { state.repository.moveToTrash(listOf(currentMedia.uri)); Toast.makeText(context, "ゴミ箱へ移動しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.White) } else { CommonFloatingActionButton(icon = Icons.Default.Restore, tooltipDescription = "復元", size = 32.dp, iconSize = 18.dp, onClick = { scope.launch { state.repository.restoreFromTrash(listOf(currentMedia.uri)); Toast.makeText(context, "復元しました", Toast.LENGTH_SHORT).show(); onClickedClose() } }, contentColor = Color.White) } }
                    }
                }
                if (showTagDialog) { galleryState?.let { state -> UnifiedMediaEditDialog(uris = listOf(currentMedia.uri), repository = state.repository, onDismiss = { showTagDialog = false }) } }
            }
        }

        AnimatedVisibility(visible = isRecommendationVisible, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val density = LocalDensity.current
            val maxDrag = with(density) { 600.dp.toPx() }
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).offset { IntOffset(0, recommendationDragOffset.value.roundToInt()) }.background(Color.Black.copy(alpha = 0.95f)).pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (recommendationDragOffset.value > 150f) { scope.launch { recommendationDragOffset.animateTo(maxDrag, tween(250)); isRecommendationVisible = false; delay(250); recommendationDragOffset.snapTo(0f) } } else { scope.launch { recommendationDragOffset.animateTo(0f, tween(150)) } } }, onVerticalDrag = { change, dragAmount -> change.consume(); scope.launch { recommendationDragOffset.snapTo((recommendationDragOffset.value + dragAmount).coerceAtLeast(0f)) } }) }) {
                Column(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) { Surface(modifier = Modifier.width(40.dp).height(4.dp), color = Color.Gray.copy(alpha = 0.5f), shape = CircleShape) {} }
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("詳細・レコメンド", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold); Text("閉じる", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.clickable { isRecommendationVisible = false }.padding(8.dp)) }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                        val currentMedia = imageList.getOrNull(pagerState.currentPage)
                        if (currentMedia != null) {
                            // 元画像を表示 (画像の場合のみ)
                            if (!currentMedia.isVideo) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(currentMedia.uri).build(), imageLoader = imageLoader),
                                            contentDescription = "元画像",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }

                            if (!currentMedia.isVideo && !isTrashMode) {
                                item { Text("似た雰囲気の画像 (AIベクトル)", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item {
                                    if (currentMetadata?.hasFeatureVector != true) {
                                        Text("この画像は未分析です。分析すると似た画像を見つけられるようになります。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                                    } else if (recommendedMediaByVisual.isEmpty()) {
                                        Text("似た画像が見つかりませんでした。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                                    } else {
                                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(recommendedMediaByVisual) { _, similarity -> RecommendationCard(mediaItem = similarity.media, score = "${(similarity.similarityScore * 100).toInt()}%", imageLoader = imageLoader, isDeleted = deletedUriSet.contains(similarity.media.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == similarity.media.uri }; if (index != -1) { scope.launch { pagerState.scrollToPage(index); onPageSelected?.invoke(index) } } else onNavigateToMedia?.invoke(similarity.media.uri) } } }
                                    }
                                }
                            } else if (!isTrashMode) {
                                item { Text("ランダムな動画", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item { 
                                    if (randomMediaList.isEmpty()) Text("動画が見つかりませんでした", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) 
                                    else { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(randomMediaList) { _, item -> RecommendationCard(mediaItem = item, score = null, imageLoader = imageLoader, isDeleted = deletedUriSet.contains(item.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == item.uri }; if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(item.uri) } } } } 
                                }
                            }
                            
                            // タグセクション（ランダム系の上に配置）
                            item {
                                val normalTags = remember(currentMediaTagsFlow) {
                                    currentMediaTagsFlow.filter { !it.tag.endsWith("系") && it.confidence >= 0.6f }
                                        .sortedByDescending { it.confidence }
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("タグ", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        val currentAgeRating = currentMetadata?.ageRating ?: "SFW"; Spacer(modifier = Modifier.width(12.dp)); Surface(color = when(currentAgeRating) { "R18" -> Color.Red.copy(alpha = 0.8f); "R15" -> Color.Yellow.copy(alpha = 0.8f); else -> Color.Green.copy(alpha = 0.8f) }, shape = RoundedCornerShape(4.dp)) { Text(text = if (isTrashMode) "$currentAgeRating ゴミ" else currentAgeRating, color = if (currentAgeRating == "R15") Color.Black else Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                    }
                                    var showTagAddDialog by remember { mutableStateOf(false) }
                                    
                                    if (!currentMedia.isVideo && currentMetadata?.isAiAnalyzed != true) {
                                        Text("AIタグ未分析です。分析すると関連アイテムが表示されるようになります。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                    }

                                    FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                                        normalTags.forEach { tagEntity -> 
                                            Surface(
                                                color = Color.DarkGray, 
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.clickable { onNavigateToTag?.invoke(tagEntity.tag) }
                                            ) { 
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                                    Text(text = tagEntity.tag, color = Color.White, fontSize = 12.sp)
                                                    if (tagEntity.confidence > 0f && tagEntity.confidence < 1f) {
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(text = "${(tagEntity.confidence * 100).toInt()}%", color = Color.Gray, fontSize = 10.sp)
                                                    }
                                                }
                                            } 
                                        }
                                        if (!isTrashMode) IconButton(onClick = { showTagAddDialog = true }, modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) { Icon(Icons.Default.Add, contentDescription = "タグ追加", tint = Color.White, modifier = Modifier.size(16.dp)) } 
                                    }
                                    if (showTagAddDialog && galleryState != null) UnifiedMediaEditDialog(uris = listOf(currentMedia.uri), repository = galleryState.repository, onDismiss = { showTagAddDialog = false })
                                }
                            }
                            item { if (!isTrashMode) { if (recommendedMediaByTags.isEmpty()) Text("関連アイテムがありません", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(recommendedMediaByTags) { _, similarity -> RecommendationCard(mediaItem = similarity.media, score = "${(similarity.similarityScore * 100).toInt()}%", imageLoader = imageLoader, isDeleted = deletedUriSet.contains(similarity.media.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == similarity.media.uri }; if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(similarity.media.uri) } } } } }

                            if (!currentMedia.isVideo && !isTrashMode) {
                                item { Text("ランダムな画像", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                                item { if (randomMediaList.isEmpty()) Text("読込中...", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(randomMediaList) { _, item -> RecommendationCard(mediaItem = item, score = null, imageLoader = imageLoader, isDeleted = deletedUriSet.contains(item.uri)) { isRecommendationVisible = false; val index = imageList.indexOfFirst { it.uri == item.uri }; if (index != -1) scope.launch { pagerState.scrollToPage(index) } else onNavigateToMedia?.invoke(item.uri) } } } }
                            }

                            item { val media = currentMedia; Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) { HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 16.dp)); Text("ファイル情報", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)); MediaInfoRow("日時", SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(media.dateAdded))); MediaInfoRow("ファイル名", media.fileName); val actualPath = remember(media.uri) { try { val cursor = context.contentResolver.query(Uri.parse(media.uri), arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null); cursor?.use { if (it.moveToFirst()) it.getString(0) else media.uri } ?: media.uri } catch (e: Exception) { media.uri } }; MediaInfoRow("ファイルパス", actualPath); val labels = currentMediaTagsFlow.filter { !it.tag.endsWith("系") }.map { it.tag }; if (labels.isNotEmpty()) MediaInfoRow("画像情報", labels.joinToString(", ")); MediaInfoRow("画像サイズ", formatFileSize(media.fileSize)); if (media.width > 0 && media.height > 0) MediaInfoRow("画像の幅x高さ", "${media.width} x ${media.height}") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeekControlRow(
    currentPosition: Long,
    duration: Long,
    onSeekRequested: (Long) -> Unit,
    galleryState: GalleryState?
) {
    val currentInterval = galleryState?.videoSeekInterval ?: 10
    Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        SeekButtonWithPicker(isForward = false, currentInterval = currentInterval, onIntervalSelected = { galleryState?.videoSeekInterval = it }, onClick = { onSeekRequested((currentPosition - currentInterval * 1000L).coerceAtLeast(0L)) })
        Spacer(modifier = Modifier.width(32.dp))
        SeekButtonWithPicker(isForward = true, currentInterval = currentInterval, onIntervalSelected = { galleryState?.videoSeekInterval = it }, onClick = { onSeekRequested((currentPosition + currentInterval * 1000L).coerceAtMost(duration)) })
    }
}

@Composable
fun SeekButtonWithPicker(
    isForward: Boolean,
    currentInterval: Int,
    onIntervalSelected: (Int) -> Unit,
    onClick: () -> Unit
) {
    val options = listOf(5, 10, 15, 30, 60)
    var isPickerVisible by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnIntervalSelected by rememberUpdatedState(onIntervalSelected)
    val selectedIndexByDrag = remember(dragY, currentInterval) { val step = 40f; val indexOffset = (dragY / step).roundToInt(); val baseIndex = options.indexOf(currentInterval).let { if (it == -1) 1 else it }; (baseIndex + indexOffset).coerceIn(0, options.size - 1) }
    val currentSelectedIndex by rememberUpdatedState(selectedIndexByDrag)

    Box(contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.pointerInput(currentInterval) { awaitPointerEventScope { while (true) { val down = awaitFirstDown(); val startTimestamp = System.currentTimeMillis(); var isLongPress = false; dragY = 0f; while (true) { val event = awaitPointerEvent(); val currentTime = System.currentTimeMillis(); if (currentTime - startTimestamp > 400 && !isLongPress) { isLongPress = true; isPickerVisible = true }; if (isLongPress) { val dragChange = event.changes.first(); dragY = dragChange.position.y - down.position.y; dragChange.consume() }; if (event.changes.any { !it.pressed }) { if (isLongPress) { currentOnIntervalSelected(options[currentSelectedIndex]); isPickerVisible = false } else currentOnClick(); break } } } } }.size(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) { Icon(imageVector = if (isForward) Icons.Default.ArrowForward else Icons.Default.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp)); Text(text = currentInterval.toString(), color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            Text("skip", color = Color.White, fontSize = 9.sp)
        }
        if (isPickerVisible) { Popup(alignment = Alignment.Center, offset = IntOffset(0, 0)) { Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp), modifier = Modifier.width(60.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) { options.forEachIndexed { index, value -> val isSelected = index == selectedIndexByDrag; Text(text = value.toString(), color = if (isSelected) Color.Cyan else Color.White, fontSize = if (isSelected) 18.sp else 14.sp, fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(vertical = 8.dp)) } } } } }
    }
}

@Composable
fun MediaInfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(100.dp))
        Text(text = value, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String, isUiVisible: Boolean, isPlaying: Boolean, isMuted: Boolean, seekToPosition: Long = -1L,
    onToggleUi: () -> Unit, onProgressChanged: (Long, Long) -> Unit, scale: Float, offsetX: Float, offsetY: Float,
    onZoomPan: (Float, Offset) -> Unit, onVerticalDrag: (Float) -> Unit, onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    isScrollInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // remember(uri) を使用して、URIが変わったらプレイヤーを再生成するように明示
    val exoPlayer = remember(uri) { 
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                androidx.media3.exoplayer.DefaultRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            )
            .build()
            .apply { 
                setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare() 
            } 
    }
    LaunchedEffect(seekToPosition) { if (seekToPosition >= 0) exoPlayer.seekTo(seekToPosition) }
    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }
    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }
    LaunchedEffect(exoPlayer, isUiVisible, isPlaying, isScrollInProgress) {
        while (isPlaying && !isScrollInProgress) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                onProgressChanged(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0))
            }
            delay(500)
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
    Box(modifier = modifier.background(Color.Black).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }, onDoubleTap = { onDoubleTap() }) }
        .pointerInput(scale) {
            awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent()
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    
                    // ズーム中または拡大表示中
                    if (event.changes.size >= 2 || scale > 1.01f) {
                        onZoomPan(zoomChange, panChange)
                        event.changes.forEach { it.consume() }
                    } else {
                        // 拡大していない時は、垂直スワイプ（おすすめ表示・閉じる）を優先する
                        if (panChange != Offset.Zero && Math.abs(panChange.y) > Math.abs(panChange.x) * 2.5f) {
                            onVerticalDrag(panChange.y)
                            event.changes.forEach { it.consume() }
                        }
                        // 水平方向へのスワイプはPagerに任せるため消費しない
                    }
                    
                    if (event.changes.all { !it.pressed }) break
                }
            }
        }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.changes.all { !it.pressed }) onDragEnd() } } }
    ) { AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER); setShutterBackgroundColor(android.graphics.Color.BLACK); controllerAutoShow = false; hideController(); resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } }, modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleUi() }) }
}

@Composable
fun GifPlayer(
    uri: String, isUiVisible: Boolean, onToggleUi: () -> Unit, scale: Float, offsetX: Float, offsetY: Float,
    onZoomPan: (Float, Offset) -> Unit, onVerticalDrag: (Float) -> Unit, onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    imageLoader: ImageLoader, isFrameSteppingVisible: Boolean, gifFrames: List<Bitmap>, currentFrameIndex: Int, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(uri).build(), imageLoader = imageLoader)
    Box(modifier = modifier.background(Color.Black).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleUi() }, onDoubleTap = { onDoubleTap() }) }
        .pointerInput(scale) {
            awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent()
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    
                    // ズーム中または拡大表示中
                    if (event.changes.size >= 2 || scale > 1.01f) {
                        onZoomPan(zoomChange, panChange)
                        event.changes.forEach { it.consume() }
                    } else {
                        // 拡大していない時は、垂直スワイプ（おすすめ表示・閉じる）を優先する
                        if (panChange != Offset.Zero && Math.abs(panChange.y) > Math.abs(panChange.x) * 2.5f) {
                            onVerticalDrag(panChange.y)
                            event.changes.forEach { it.consume() }
                        }
                        // 水平方向へのスワイプはPagerに任せるため消費しない
                    }

                    if (event.changes.all { !it.pressed }) break
                }
            }
        }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.changes.all { !it.pressed }) onDragEnd() } } }
    ) { if (isFrameSteppingVisible && gifFrames.isNotEmpty()) Image(bitmap = gifFrames[currentFrameIndex].asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleUi() }, contentScale = ContentScale.Fit) else Image(painter = painter, contentDescription = null, modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggleUi() }, contentScale = ContentScale.Fit) }
}

@Composable
fun RecommendationCard(
    mediaItem: MediaData,
    score: String?,
    imageLoader: ImageLoader,
    isDeleted: Boolean = false,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(110.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() }) {
        Image(painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(mediaItem.uri).apply { if (mediaItem.isVideo) videoFrameMillis(1000) }.build(), imageLoader = imageLoader), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (score != null) Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(score, color = Color.White, fontSize = 10.sp) }
        if (isDeleted) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.Delete, contentDescription = "削除済み", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp)) }
    }
}

private fun captureVideoFrame(context: android.content.Context, uriString: String, positionMs: Long, onResult: (Bitmap?) -> Unit) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        val retriever = MediaMetadataRetriever()
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd -> retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } ?: retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            withContext(Dispatchers.Main) { onResult(bitmap) }
        } catch (e: Exception) { e.printStackTrace(); withContext(Dispatchers.Main) { onResult(null) } } finally { try { retriever.release() } catch (e: Exception) {} }
    }
}

private fun formatTime(ms: Long): String { val totalSeconds = ms / 1000; val minutes = totalSeconds / 60; val seconds = totalSeconds % 60; return "%02d:%02d".format(minutes, seconds) }

private fun extractGifFrames(context: android.content.Context, uriString: String, scope: kotlinx.coroutines.CoroutineScope, onResult: (List<Bitmap>) -> Unit) {
    scope.launch(Dispatchers.IO) {
        val frames = mutableListOf<Bitmap>()
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        var retrieverSuccess = false
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd -> retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) } ?: retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); val durationMs = durationStr?.toLong() ?: 0L; val frameCount = 30
            if (durationMs > 0) { for (i in 0 until frameCount) { val timeUs = (durationMs * 1000 / frameCount) * i; val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST); if (bitmap != null) frames.add(bitmap) } } else { for (i in 0 until frameCount) { val timeUs = i * 100000L; val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST); if (bitmap != null) frames.add(bitmap) else if (i > 10) break } }
            if (frames.isNotEmpty()) retrieverSuccess = true
        } catch (e: Exception) { e.printStackTrace() } finally { try { retriever.release() } catch (e: Exception) {} }
        if (!retrieverSuccess) { try { context.contentResolver.openInputStream(uri)?.use { inputStream -> @Suppress("DEPRECATION") val movie = Movie.decodeStream(inputStream); if (movie != null) { val durationMs = movie.duration().coerceAtLeast(1); val frameCount = 30; val width = movie.width().coerceAtLeast(1); val height = movie.height().coerceAtLeast(1); if (width > 0 && height > 0) { for (i in 0 until frameCount) { val timeMs = (durationMs / frameCount) * i; movie.setTime(timeMs); val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); movie.draw(canvas, 0f, 0f); frames.add(bitmap) } } } } } catch (e: Exception) { e.printStackTrace() } }
        scope.launch(Dispatchers.Main) { if (frames.isEmpty()) Toast.makeText(context, "コマの抽出に失敗しました", Toast.LENGTH_SHORT).show(); onResult(frames) }
    }
}

private fun saveBitmapToScreenshots(context: android.content.Context, bitmap: Bitmap) {
    val filename = "Screenshot_${System.currentTimeMillis()}.png"; val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, filename); put(MediaStore.MediaColumns.MIME_TYPE, "image/png"); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots"); put(MediaStore.MediaColumns.IS_PENDING, 1) } }; val resolver = context.contentResolver
    try { val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues); if (uri != null) { resolver.openOutputStream(uri)?.use { outputStream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { contentValues.clear(); contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0); resolver.update(uri, contentValues, null, null) }; (context as Activity).runOnUiThread { Toast.makeText(context, "スクリーンショットを保存しました", Toast.LENGTH_SHORT).show() } } } catch (e: Exception) { Log.e("PictureViewer", "Error saving screenshot", e); (context as Activity).runOnUiThread { Toast.makeText(context, "保存に失敗しました", Toast.LENGTH_SHORT).show() } }
}

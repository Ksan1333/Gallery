package com.example.gallery.ui.screen

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.component.TapZoneGuideOverlay
import com.example.gallery.ui.component.tapZoneCountForLayout
import com.example.gallery.ui.state.GalleryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VIDEO_FULLSCREEN_SEEK_TRACE = "VIDEO_FULLSCREEN_SEEK"

@OptIn(UnstableApi::class)
@Composable
fun VideoFullscreenViewerScreen(
    videoList: List<MediaData>,
    initialIndex: Int,
    onClose: () -> Unit,
    galleryState: GalleryState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val window = activity?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    val originalBrightness = remember(window) { window?.attributes?.screenBrightness ?: -1f }
    val originalNavigationBarColor = remember(window) { window?.navigationBarColor ?: AndroidColor.BLACK }
    val originalStatusBarColor = remember(window) { window?.statusBarColor ?: AndroidColor.BLACK }
    val originalNavigationBarContrast = remember(window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced ?: false
        } else {
            false
        }
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxMediaVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
    }
    val scope = rememberCoroutineScope()
    val globalSettingsPrefs = remember { context.getSharedPreferences("global_settings", Context.MODE_PRIVATE) }
    val controlPanelAutoHideMs = remember { globalSettingsPrefs.getInt("controlPanelAutoHideMs", AppDefaults.CONTROL_PANEL_AUTO_HIDE_MS).coerceIn(1000, 10000) }
    val touchIndicatorEnabled = remember { globalSettingsPrefs.getBoolean("touchIndicator", false) }
    val tapZonePrefs = remember { context.getSharedPreferences("book_viewer_settings", Context.MODE_PRIVATE) }
    val tapZoneCount = tapZoneCountForLayout(tapZonePrefs.getString("tapZoneLayout", "THREE"))

    var currentIndex by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, (videoList.size - 1).coerceAtLeast(0)))
    }
    val currentVideo = videoList.getOrNull(currentIndex)
    var isPlaying by remember(currentVideo?.uri) { mutableStateOf(true) }
    var positionMs by remember(currentVideo?.uri) { mutableLongStateOf(0L) }
    var durationMs by remember(currentVideo?.uri) { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isControlInteractionActive by remember { mutableStateOf(false) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var requestedOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) }
    var playerVolume by remember(audioManager) {
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxMediaVolume
        mutableStateOf((current.toFloat() / maxMediaVolume.toFloat()).coerceIn(0f, 1f))
    }
    var screenBrightness by remember(window) {
        mutableStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    var hasCustomBrightness by remember { mutableStateOf(false) }
    var adjustmentLabel by remember { mutableStateOf<String?>(null) }
    var adjustmentValue by remember { mutableStateOf(0f) }
    var adjustmentToken by remember { mutableIntStateOf(0) }
    var touchIndicatorPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var touchIndicatorToken by remember { mutableIntStateOf(0) }
    var isVideoStripVisible by remember { mutableStateOf(false) }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }
    val frameStepMs = 1000L / 30L

    fun showControlsTemporarily() {
        isControlsVisible = true
        interactionToken++
    }

    fun showTouchIndicator(position: androidx.compose.ui.geometry.Offset) {
        if (!touchIndicatorEnabled) return
        touchIndicatorPoint = position
        touchIndicatorToken++
    }

    fun restoreBrightness() {
        window?.let { targetWindow ->
            val attrs = targetWindow.attributes
            attrs.screenBrightness = originalBrightness
            targetWindow.attributes = attrs
        }
        hasCustomBrightness = false
    }

    fun closeViewer() {
        restoreBrightness()
        window?.navigationBarColor = originalNavigationBarColor
        window?.statusBarColor = originalStatusBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = originalNavigationBarContrast
        }
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        insetsController?.show(WindowInsetsCompat.Type.systemBars())
        onClose()
    }

    fun showAdjustment(label: String, value: Float) {
        adjustmentLabel = label
        adjustmentValue = value.coerceIn(0f, 1f)
        adjustmentToken++
    }

    val exoPlayer = remember(currentVideo?.uri) {
        currentVideo?.let { video ->
            ExoPlayer.Builder(context)
                .setRenderersFactory(
                    DefaultRenderersFactory(context)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                )
                .build()
                .apply {
                    setSeekParameters(SeekParameters.EXACT)
                    addListener(object : Player.Listener {
                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            Log.d(
                                VIDEO_FULLSCREEN_SEEK_TRACE,
                                "seek_applied uriHash=${video.uri.hashCode()} position=${newPosition.positionMs} reason=$reason"
                            )
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                this@apply.seekTo(0L)
                                this@apply.play()
                            }
                        }
                    })
                    setMediaItem(MediaItem.fromUri(Uri.parse(video.uri)))
                    prepare()
                    playWhenReady = true
                }
        }
    }

    fun seekTo(target: Long, pausePlayback: Boolean = false) {
        val duration = durationMs.takeIf { it > 0 } ?: exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
        val next = target.coerceIn(0L, duration.coerceAtLeast(0L))
        if (pausePlayback) {
            isPlaying = false
            exoPlayer?.pause()
        }
        exoPlayer?.setSeekParameters(SeekParameters.EXACT)
        positionMs = next
        exoPlayer?.seekTo(next)
        Log.d(
            VIDEO_FULLSCREEN_SEEK_TRACE,
            "seek_request uriHash=${currentVideo?.uri?.hashCode()} target=$next pause=$pausePlayback duration=$duration"
        )
    }

    fun moveVideo(delta: Int) {
        if (videoList.isEmpty()) return
        currentIndex = (currentIndex + delta + videoList.size) % videoList.size
        isPlaying = true
        showControlsTemporarily()
    }

    fun rotateOrientation() {
        showControlsTemporarily()
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        activity?.requestedOrientation = requestedOrientation
    }

    fun saveScreenshot() {
        showControlsTemporarily()
        val video = currentVideo ?: return
        val capturePosition = positionMs
        scope.launch(Dispatchers.IO) {
            val bitmap = captureVideoFrame(context, video.uri, capturePosition)
            val saved = bitmap?.let { saveBitmapToPictures(context, it) } == true
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (saved) "Screenshot saved" else "Screenshot failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer ?: return@LaunchedEffect
        exoPlayer.playWhenReady = isPlaying
        while (true) {
            positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(50)
        }
    }

    LaunchedEffect(exoPlayer, playerVolume) {
        exoPlayer?.volume = 1f
    }

    LaunchedEffect(window, screenBrightness, hasCustomBrightness) {
        if (!hasCustomBrightness) return@LaunchedEffect
        window?.let { targetWindow ->
            val attrs = targetWindow.attributes
            attrs.screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
            targetWindow.attributes = attrs
        }
    }

    LaunchedEffect(isControlsVisible, interactionToken, isVideoStripVisible, isControlInteractionActive) {
        if (isControlsVisible) {
            window?.navigationBarColor = AndroidColor.BLACK
            window?.statusBarColor = AndroidColor.BLACK
            window?.decorView?.setBackgroundColor(AndroidColor.BLACK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window?.isNavigationBarContrastEnforced = false
            }
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            insetsController?.hide(WindowInsetsCompat.Type.statusBars())
            insetsController?.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        }
        if (isControlsVisible && !isVideoStripVisible && !isControlInteractionActive) {
            delay(controlPanelAutoHideMs.toLong())
            if (!isControlInteractionActive) {
                isControlsVisible = false
            }
        }
    }

    LaunchedEffect(adjustmentToken) {
        if (adjustmentLabel != null) {
            delay(900)
            adjustmentLabel = null
        }
    }

    LaunchedEffect(touchIndicatorToken) {
        if (touchIndicatorPoint != null) {
            delay(450)
            touchIndicatorPoint = null
        }
    }

    LaunchedEffect(Unit) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    DisposableEffect(Unit) {
        window?.navigationBarColor = AndroidColor.BLACK
        window?.statusBarColor = AndroidColor.BLACK
        window?.decorView?.setBackgroundColor(AndroidColor.BLACK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.show(WindowInsetsCompat.Type.navigationBars())
        insetsController?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            restoreBrightness()
            window?.navigationBarColor = originalNavigationBarColor
            window?.statusBarColor = originalStatusBarColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window?.isNavigationBarContrastEnforced = originalNavigationBarContrast
            }
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isPlaying = false
                exoPlayer?.pause()
                restoreBrightness()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    BackHandler(onBack = { closeViewer() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        exoPlayer?.let { player ->
            key(currentVideo?.uri) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            tag = currentVideo?.uri
                            this.player = player
                            useController = false
                            setShutterBackgroundColor(AndroidColor.BLACK)
                            setKeepContentOnPlayerReset(false)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setBackgroundColor(AndroidColor.BLACK)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = {
                        if (it.tag != currentVideo?.uri) {
                            it.player = null
                            it.tag = currentVideo?.uri
                        }
                        it.setShutterBackgroundColor(AndroidColor.BLACK)
                        it.setBackgroundColor(AndroidColor.BLACK)
                        it.player = player
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(player, durationMs) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalX = 0f
                            var totalY = 0f
                            var consumedFrames = 0
                            var didFrameStep = false
                            var activeGesture: String? = null
                            val dragStartPosition = positionMs
                            val wasPlayingAtDragStart = isPlaying
                            val edgeWidth = size.width * 0.18f
                            val gestureMode = when {
                                down.position.x <= edgeWidth -> "brightness"
                                down.position.x >= size.width - edgeWidth -> "volume"
                                else -> "frame"
                            }
                            val startVolume = playerVolume
                            val startBrightness = screenBrightness

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val delta = change.position - change.previousPosition
                                totalX += delta.x
                                totalY += delta.y

                                if (activeGesture == null) {
                                    activeGesture = when {
                                        gestureMode == "volume" && abs(totalY) > 12f && abs(totalY) > abs(totalX) -> "volume"
                                        gestureMode == "brightness" && abs(totalY) > 12f && abs(totalY) > abs(totalX) -> "brightness"
                                        abs(totalX) > 12f && abs(totalX) > abs(totalY) * 1.2f -> "frame"
                                        else -> null
                                    }
                                }

                                if (activeGesture == "volume") {
                                    playerVolume = (startVolume - totalY / (size.height * 0.25f)).coerceIn(0f, 1f)
                                    val streamVolume = (playerVolume * maxMediaVolume)
                                        .roundToInt()
                                        .coerceIn(0, maxMediaVolume)
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, streamVolume, 0)
                                    val actualStreamVolume =
                                        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: streamVolume
                                    player.volume = 1f
                                    Log.d(
                                        VIDEO_FULLSCREEN_SEEK_TRACE,
                                        "volume_adjust value=${(playerVolume * 100).roundToInt()} requested=$streamVolume actual=$actualStreamVolume max=$maxMediaVolume"
                                    )
                                    showAdjustment("\u97f3\u91cf", playerVolume)
                                    showControlsTemporarily()
                                    change.consume()
                                } else if (activeGesture == "brightness") {
                                    screenBrightness = (startBrightness - totalY / (size.height * 0.25f)).coerceIn(0.01f, 1f)
                                    hasCustomBrightness = true
                                    window?.let { targetWindow ->
                                        val attrs = targetWindow.attributes
                                        attrs.screenBrightness = screenBrightness
                                        targetWindow.attributes = attrs
                                    }
                                    Log.d(
                                        VIDEO_FULLSCREEN_SEEK_TRACE,
                                        "brightness_adjust value=${(screenBrightness * 100).roundToInt()} actual=${window?.attributes?.screenBrightness}"
                                    )
                                    showAdjustment("\u660e\u308b\u3055", screenBrightness)
                                    showControlsTemporarily()
                                    change.consume()
                                } else if (activeGesture == "frame") {
                                    val frameDelta = (totalX / 14f).roundToInt()
                                    val stepDelta = frameDelta - consumedFrames
                                    if (stepDelta != 0) {
                                        val target = dragStartPosition + frameDelta * frameStepMs
                                        Log.d(
                                            VIDEO_FULLSCREEN_SEEK_TRACE,
                                            "frame_drag delta=$stepDelta frameDelta=$frameDelta totalX=$totalX from=$dragStartPosition target=$target"
                                        )
                                        if (!didFrameStep && wasPlayingAtDragStart) {
                                            isPlaying = false
                                            player.pause()
                                        }
                                        seekTo(target, pausePlayback = false)
                                        consumedFrames = frameDelta
                                        didFrameStep = true
                                        showControlsTemporarily()
                                        change.consume()
                                    }
                                }
                            }

                            if (didFrameStep && wasPlayingAtDragStart) {
                                isPlaying = true
                                player.play()
                            }
                            if (!didFrameStep && abs(totalX) < 8f && abs(totalY) < 8f) {
                                showTouchIndicator(down.position)
                                isControlsVisible = !isControlsVisible
                                interactionToken++
                            }
                        }
                        }
                )
            }
        }

        if (isControlsVisible) {
            IconButton(
                onClick = { closeViewer() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Text(
                text = currentVideo
                    ?.let { video ->
                        video.fileName.ifBlank {
                            Uri.parse(video.uri).lastPathSegment ?: video.uri.substringAfterLast('/')
                        }
                    }
                    .orEmpty(),
                color = Color.White.copy(alpha = 0.94f),
                fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 64.dp, end = 64.dp, top = 20.dp)
            )

            IconButton(
                onClick = {
                    isVideoStripVisible = !isVideoStripVisible
                    showControlsTemporarily()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            ) {
                Icon(Icons.Default.Collections, contentDescription = "Video list", tint = Color.White)
            }

            if (isVideoStripVisible) {
                VideoFolderStrip(
                    videoList = videoList,
                    currentIndex = currentIndex,
                    isLandscape = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    onSelected = { index ->
                        currentIndex = index
                        isPlaying = true
                        isVideoStripVisible = false
                        showControlsTemporarily()
                    },
                    modifier = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(92.dp)
                            .padding(top = 58.dp, bottom = 104.dp, end = 8.dp)
                    } else {
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 58.dp, start = 8.dp, end = 8.dp)
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            isControlInteractionActive = true
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break
                            }
                            isControlInteractionActive = false
                            showControlsTemporarily()
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${formatFullscreenVideoTime(positionMs)} / ${formatFullscreenVideoTime(durationMs)}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.32f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlainOverlayIconButton(
                        icon = Icons.Default.Screenshot,
                        contentDescription = "Screenshot",
                        onClick = { saveScreenshot() },
                        iconSize = 22.dp,
                        buttonSize = 42.dp
                    )
                    PlainOverlayIconButton(
                        icon = Icons.Default.ScreenRotation,
                        contentDescription = "Rotate",
                        onClick = { rotateOrientation() },
                        iconSize = 22.dp,
                        buttonSize = 42.dp
                    )
                }
                VideoSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeekStart = {
                        isControlInteractionActive = true
                        wasPlayingBeforeSeek = isPlaying
                        isPlaying = false
                        exoPlayer?.pause()
                    },
                    onSeek = { seekTo(it, pausePlayback = true) },
                    onSeekEnd = {
                        if (wasPlayingBeforeSeek) {
                            isPlaying = true
                            exoPlayer?.play()
                        }
                        isControlInteractionActive = false
                        showControlsTemporarily()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlainOverlayIconButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Previous video",
                            onClick = { moveVideo(-1) }
                        )
                        PlainOverlayIconButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            onClick = {
                                isPlaying = !isPlaying
                                exoPlayer?.playWhenReady = isPlaying
                            },
                            iconSize = 42.dp
                        )
                        PlainOverlayIconButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Next video",
                            onClick = { moveVideo(1) }
                        )
                    }
                }
            }
        }

        if (touchIndicatorEnabled) {
            TapZoneGuideOverlay(
                labels = videoTapZoneGuideLabels(tapZoneCount),
                modifier = Modifier.matchParentSize()
            )
        }

        adjustmentLabel?.let { label ->
            val isBrightness = label == "\u660e\u308b\u3055"
            VerticalAdjustmentBar(
                value = adjustmentValue,
                modifier = Modifier
                    .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 18.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\u73fe\u5728\u306e$label ${(adjustmentValue * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(168.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.26f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(adjustmentValue.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalAdjustmentBar(
    value: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .width(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.20f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .background(Color.White.copy(alpha = 0.92f))
        )
    }
}

@Composable
private fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableIntStateOf(0) }
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val thumbProgress = progress.coerceIn(0.001f, 1f)

    Box(
        modifier = modifier
            .onGloballyPositioned { width = it.size.width }
            .pointerInput(durationMs, width) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onSeekStart()

                    fun seekToX(x: Float) {
                        val ratio = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onSeek((durationMs.coerceAtLeast(1L) * ratio).toLong())
                    }

                    seekToX(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        seekToX(change.position.x)
                        change.consume()
                    }
                    onSeekEnd()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.24f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.92f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(thumbProgress)
                .height(18.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

private fun formatFullscreenVideoTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = safeMs % 1000
    return "%d:%02d.%03d".format(minutes, seconds, millis)
}

private fun videoTapZoneLabels(zoneCount: Int): List<String> {
    return when (zoneCount) {
        11 -> listOf("前の動画", "戻す", "戻す", "明るさ", "スクショ", "再生/停止", "設定", "一覧", "音量", "進める", "次の動画")
        4 -> listOf("前の動画", "明るさ", "音量", "次の動画")
        else -> listOf("前の動画", "再生/停止", "次の動画")
    }
}

private fun videoTapZoneGuideLabels(zoneCount: Int): List<String> {
    return when (zoneCount) {
        11 -> listOf(
            "前の動画",
            "10秒戻す",
            "次の動画",
            "明るさ",
            "スクリーンショット",
            "再生/一時停止",
            "設定",
            "一覧",
            "音量",
            "10秒進む",
            "次の動画"
        )
        7 -> listOf("前の動画", "次の動画", "10秒戻す", "再生/一時停止", "10秒進む", "明るさ", "音量")
        5 -> listOf("10秒戻す", "前の動画", "再生/一時停止", "次の動画", "10秒進む")
        4 -> listOf("前の動画", "再生/一時停止", "音量", "次の動画")
        else -> listOf("前の動画", "再生/一時停止", "次の動画")
    }
}

@Composable
private fun VideoFolderStrip(
    videoList: List<MediaData>,
    currentIndex: Int,
    isLandscape: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(videoList) { index, video ->
                VideoStripItem(video, index == currentIndex) { onSelected(index) }
            }
        }
    } else {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(videoList) { index, video ->
                VideoStripItem(video, index == currentIndex) { onSelected(index) }
            }
        }
    }
}

@Composable
private fun VideoStripItem(
    video: MediaData,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val background = if (isSelected) {
        Color.White.copy(alpha = 0.55f)
    } else {
        Color.Black.copy(alpha = 0.34f)
    }

    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context)
                    .data(video.uri)
                    .videoFrameMillis(1000)
                    .crossfade(false)
                    .build()
            ),
            contentDescription = "Video thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun PlainOverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 50.dp,
    iconSize: Dp = 30.dp
) {
    IconButton(onClick = onClick, modifier = modifier.size(buttonSize)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

private fun captureVideoFrame(context: Context, uriString: String, positionMs: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        } ?: retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun saveBitmapToPictures(context: Context, bitmap: Bitmap): Boolean {
    val filename = "VideoFrame_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        false
    }
}

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
import com.example.gallery.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.compose.rememberAsyncImagePainter
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.util.RollingFrameCacheManager
import com.example.gallery.util.VideoFrameCacheManager
import com.example.gallery.ui.component.GalleryVideoSeekBar
import com.example.gallery.ui.component.TapZoneGuideOverlay
import com.example.gallery.ui.component.isViewerOverflowActionName
import com.example.gallery.ui.component.tapZoneCountForLayout
import com.example.gallery.ui.component.resolveViewerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private fun handleVideoViewerAction(
    function: String,
    context: Context,
    onClose: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    val actionClose = context.getString(R.string.label_action_close_playback)
    val actionSettings = context.getString(R.string.settings_title)
    val actionRotate = context.getString(R.string.label_action_rotate)
    val actionScreenshot = context.getString(R.string.label_action_screenshot)
    val actionPrev = context.getString(R.string.label_action_previous_video)
    val actionNext = context.getString(R.string.label_action_next_video)
    val actionPlayPause = context.getString(R.string.label_action_play_pause)

    when (function) {
        actionClose -> onClose()
        actionSettings -> onNavigateToSettings()
        actionRotate -> onRotate()
        actionScreenshot -> onScreenshot()
        actionPrev -> onPreviousVideo()
        actionNext -> onNextVideo()
        actionPlayPause -> onTogglePlayback()
    }
}

private const val VIDEO_FULLSCREEN_SEEK_TRACE = "VIDEO_FULLSCREEN_SEEK"

@OptIn(UnstableApi::class)
@Composable
fun VideoFullscreenViewerScreen(
    videoList: List<MediaData>,
    initialIndex: Int,
    onClose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    galleryState: GalleryState
) {
    val context = LocalContext.current
    GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
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
    val tapZoneLayout = globalSettingsPrefs.getString("tapZoneLayout", "THREE") ?: "THREE"
    val tapZoneCount = tapZoneCountForLayout(tapZoneLayout)
    val showClockBattery = globalSettingsPrefs.getBoolean("showClockBattery", false)

    var currentIndex by remember {
        mutableIntStateOf(initialIndex.coerceIn(0, (videoList.size - 1).coerceAtLeast(0)))
    }
    val currentVideo = videoList.getOrNull(currentIndex)
    var isPlaying by remember(currentVideo?.uri) { mutableStateOf(true) }
    var positionMs by remember(currentVideo?.uri) { mutableLongStateOf(0L) }
    var durationMs by remember(currentVideo?.uri) { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isControlInteractionActive by remember { mutableStateOf(false) }
    var isOverflowMenuOpen by remember { mutableStateOf(false) }
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
    var isSeekBarDragging by remember { mutableStateOf(false) }
    var isSwipingOnScreen by remember { mutableStateOf(false) }
    var isPlayerBuffering by remember { mutableStateOf(false) }
    var lastSeekRequestedAt by remember { mutableLongStateOf(0L) }
    var showCacheOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(isSeekBarDragging, isPlayerBuffering) {
        if (isSeekBarDragging && isPlayerBuffering) {
            delay(40) // Small grace period to allow player to seek without showing low-res cache
            showCacheOverlay = true
        } else {
            showCacheOverlay = false
        }
    }

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
        // The caller removes this composable asynchronously.  Restore the activity
        // orientation before changing its state so the gallery never inherits a
        // temporary landscape lock while the viewer is being disposed.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true
                    )
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
                            isPlayerBuffering = playbackState == Player.STATE_BUFFERING
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
        
        // Only seek if the target position has significantly changed to avoid flooding
        if (abs(next - positionMs) >= 16) { // ~60fps resolution
            exoPlayer?.setSeekParameters(SeekParameters.EXACT)
            positionMs = next
            exoPlayer?.seekTo(next)
            lastSeekRequestedAt = System.currentTimeMillis()
            Log.d(
                VIDEO_FULLSCREEN_SEEK_TRACE,
                "seek_request uriHash=${currentVideo?.uri?.hashCode()} target=$next pause=$pausePlayback duration=$duration"
            )
        }
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
        val successMsg = context.getString(R.string.msg_screenshot_saved)
        val failMsg = context.getString(R.string.msg_screenshot_failed)
        scope.launch(Dispatchers.IO) {
            val bitmap = captureVideoFrame(context, video.uri, capturePosition)
            val saved = bitmap?.let { saveBitmapToPictures(context, it) } == true
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (saved) successMsg else failMsg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer ?: return@LaunchedEffect
        exoPlayer.playWhenReady = isPlaying
        while (true) {
            if (!isSeekBarDragging) {
                positionMs = exoPlayer.currentPosition
            }
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

    LaunchedEffect(isControlsVisible, interactionToken, isVideoStripVisible, isControlInteractionActive, isOverflowMenuOpen, isPlaying) {
        if (isControlsVisible) {
            window?.navigationBarColor = AndroidColor.TRANSPARENT
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
        if (isControlsVisible && isPlaying && !isVideoStripVisible && !isControlInteractionActive && !isOverflowMenuOpen) {
            delay(controlPanelAutoHideMs.toLong())
            if (!isControlInteractionActive && !isOverflowMenuOpen) {
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
        window?.navigationBarColor = AndroidColor.TRANSPARENT
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

    LaunchedEffect(currentVideo?.uri, durationMs) {
        val uri = currentVideo?.uri ?: return@LaunchedEffect
        if (durationMs > 0) {
            VideoFrameCacheManager.prepareCache(context, uri, durationMs)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { 
            exoPlayer?.release()
            VideoFrameCacheManager.clearCache()
        }
    }

    BackHandler(onBack = { closeViewer() })

    val volumeLabel = stringResource(R.string.label_volume)
    val brightnessLabel = stringResource(R.string.label_brightness)
    val currentAdjustmentFormat = stringResource(R.string.label_current_adjustment_format)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        exoPlayer?.let { player ->
            key(currentVideo?.uri) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                    var lastAppliedSeekDeltaMs = 0L
                                    var didHorizontalSeek = false
                                    var activeGesture: String? = null
                                    val dragStartPosition = positionMs
                                    val wasPlayingAtDragStart = isPlaying
                                    val edgeWidth = size.width * 0.18f
                                    val gestureMode = when {
                                        down.position.x <= edgeWidth -> "brightness"
                                        down.position.x >= size.width - edgeWidth -> "volume"
                                        else -> "seek"
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
                                                abs(totalX) > 12f && abs(totalX) > abs(totalY) * 1.2f -> "seek"
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
                                            showAdjustment(volumeLabel, playerVolume)
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
                                            showAdjustment(brightnessLabel, screenBrightness)
                                            showControlsTemporarily()
                                            change.consume()
                                        } else if (activeGesture == "seek") {
                                            // Rolling cache preparation on first move
                                            if (!didHorizontalSeek) {
                                                currentVideo?.uri?.let { uri ->
                                                    scope.launch {
                                                        RollingFrameCacheManager.prepareRollingCache(context, uri, positionMs)
                                                    }
                                                }
                                            }

                                            // A full-width swipe seeks at most one minute.
                                            val seekDeltaMs = ((totalX / size.width.coerceAtLeast(1)) * 60_000f).roundToLong()
                                            if (abs(seekDeltaMs - lastAppliedSeekDeltaMs) >= 1000L / 30L) {
                                                val target = dragStartPosition + seekDeltaMs
                                                
                                                if (!didHorizontalSeek && wasPlayingAtDragStart) {
                                                    isPlaying = false
                                                    player.pause()
                                                }
                                                isSeekBarDragging = false
                                                isSwipingOnScreen = true
                                                seekTo(target, pausePlayback = false)
                                                lastAppliedSeekDeltaMs = seekDeltaMs
                                                didHorizontalSeek = true
                                                showControlsTemporarily()
                                                change.consume()
                                            }
                                        }
                                    }

                                    if (didHorizontalSeek) {
                                        isSeekBarDragging = false
                                        isSwipingOnScreen = false
                                        RollingFrameCacheManager.clearRollingCache()
                                        if (wasPlayingAtDragStart) {
                                            isPlaying = true
                                            player.play()
                                        }
                                    }
                                    if (!didHorizontalSeek && abs(totalX) < 8f && abs(totalY) < 8f) {
                                        showTouchIndicator(down.position)
                                        isControlsVisible = !isControlsVisible
                                        interactionToken++
                                    }
                                }
                            }
                    )
                    
                    // Full-screen Scrubbing Preview (25-frame low-res cache)
                    if (isSeekBarDragging) {
                        VideoFrameCacheManager.getFrameAt(positionMs)?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Rolling High-Res Preview (10-frame high-res cache for swipe)
                    if (isSwipingOnScreen) {
                        RollingFrameCacheManager.getRollingFrameAt(positionMs)?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Rolling High-Res Preview (10-frame high-res cache for swipe)
                    if (isSwipingOnScreen) {
                        RollingFrameCacheManager.getRollingFrameAt(positionMs)?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        if (isControlsVisible) {
            IconButton(
                onClick = { closeViewer() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimensionResource(R.dimen.spacing_small))
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close), tint = Color.White)
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
                    fontSize = textSizes.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(start = 64.dp, end = 64.dp, top = dimensionResource(R.dimen.icon_size_check))
                )

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
                            .padding(top = dimensionResource(R.dimen.header_height), bottom = dimensionResource(R.dimen.grid_bottom_padding), end = dimensionResource(R.dimen.spacing_small))
                    } else {
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = dimensionResource(R.dimen.header_height), start = dimensionResource(R.dimen.spacing_small), end = dimensionResource(R.dimen.spacing_small))
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = dimensionResource(R.dimen.spacing_medium), vertical = dimensionResource(R.dimen.spacing_small))
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
                    fontSize = textSizes.small,
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                        .background(Color.Black.copy(alpha = 0.32f))
                        .padding(horizontal = dimensionResource(R.dimen.popup_padding_h), vertical = dimensionResource(R.dimen.spacing_tiny))
                )
                GalleryVideoSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeekStart = {
                        isControlInteractionActive = true
                        isSeekBarDragging = true
                        wasPlayingBeforeSeek = isPlaying
                        isPlaying = false
                        exoPlayer?.pause()
                    },
                    onSeek = { seekTo(it, pausePlayback = false) },
                    onSeekEnd = {
                        isSeekBarDragging = false
                        if (wasPlayingBeforeSeek) {
                            isPlaying = true
                            exoPlayer?.play()
                        }
                        isControlInteractionActive = false
                        showControlsTemporarily()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.viewer_seek_bar_height))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimensionResource(R.dimen.spacing_tiny), bottom = dimensionResource(R.dimen.spacing_tiny)),
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium), Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val barSlots = listOf("ボトム左", "ボトム中央左", "ボトム中央", "ボトム中央右", "ボトム右")
                    val legacyBarSlots = listOf("位置 1", "位置 2", "位置 3", "位置 4", "位置 5")
                    val videoPrefs = remember { context.getSharedPreferences("video_viewer_settings", Context.MODE_PRIVATE) }
                    val actionClose = stringResource(R.string.label_action_close_playback)
                    val actionSettings = stringResource(R.string.settings_title)
                    val actionRotate = stringResource(R.string.label_action_rotate)
                    val actionScreenshot = stringResource(R.string.label_action_screenshot)
                    val actionPrev = stringResource(R.string.label_action_previous_video)
                    val actionNext = stringResource(R.string.label_action_next_video)
                    val actionPlayPause = stringResource(R.string.label_action_play_pause)
                    val actionOverflow = stringResource(R.string.label_3dot_menu)
                    val labelNone = stringResource(R.string.label_action_none)

                    val videoActionCatalog = remember(actionClose, actionSettings, actionRotate, actionScreenshot, actionPrev, actionNext, actionPlayPause) {
                        listOf(actionClose, actionSettings, actionRotate, actionScreenshot, actionPrev, actionNext, actionPlayPause)
                    }
                    val defaultBarAssignments = remember(actionRotate, actionPrev, actionPlayPause, actionNext, actionOverflow) {
                        listOf(actionRotate, actionPrev, actionPlayPause, actionNext, actionOverflow)
                    }
                    val barAssignments = remember(videoPrefs, interactionToken, videoActionCatalog, defaultBarAssignments) {
                        barSlots.mapIndexedNotNull { index, slot ->
                            val saved = videoPrefs.getString("video_bar.$slot", null)
                                ?: videoPrefs.getString("video_bar.${legacyBarSlots[index]}", null)
                            val fallback = defaultBarAssignments.getOrNull(index)
                            when {
                                saved == null -> fallback
                                saved == labelNone -> null
                                else -> saved
                            }
                        }
                    }
                    val menuAssignments = remember(barAssignments) {
                        videoActionCatalog.filterNot { action -> barAssignments.contains(action) }
                    }

                    barAssignments.forEach { function ->
                        if (isViewerOverflowActionName(function) || function !in videoActionCatalog) {
                            return@forEach
                        }
                        val action = resolveViewerAction(function, isPlaying = isPlaying)
                        if (action != null) {
                            PlainOverlayIconButton(
                                icon = action.icon,
                                contentDescription = action.label,
                                onClick = {
                                    handleVideoViewerAction(
                                        function = function,
                                        context = context,
                                        onClose = ::closeViewer,
                                        onRotate = ::rotateOrientation,
                                        onScreenshot = ::saveScreenshot,
                                        onNavigateToSettings = onNavigateToSettings,
                                        onPreviousVideo = { moveVideo(-1) },
                                        onNextVideo = { moveVideo(1) },
                                        onTogglePlayback = {
                                            isPlaying = !isPlaying
                                            exoPlayer?.playWhenReady = isPlaying
                                        }
                                    )
                                },
                                iconSize = dimensionResource(R.dimen.icon_size_medium),
                                buttonSize = 44.dp
                            )
                        }
                    }

                    if (menuAssignments.isNotEmpty() && barAssignments.any(::isViewerOverflowActionName)) {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            PlainOverlayIconButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.book_menu),
                                onClick = {
                                    showMoreMenu = true
                                    isOverflowMenuOpen = true
                                },
                                iconSize = dimensionResource(R.dimen.icon_size_medium),
                                buttonSize = 44.dp
                            )
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = {
                                    showMoreMenu = false
                                    isOverflowMenuOpen = false
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.85f))
                            ) {
                                for (function in menuAssignments) {
                                    val action = resolveViewerAction(function, isPlaying = isPlaying)
                                    if (action != null) {
                                        DropdownMenuItem(
                                            text = { Text(action.label, color = Color.White) },
                                            leadingIcon = { Icon(action.icon, null, tint = action.color ?: Color.White) },
                                            onClick = {
                                                handleVideoViewerAction(
                                                    function = function,
                                                    context = context,
                                                    onClose = ::closeViewer,
                                                    onRotate = ::rotateOrientation,
                                                    onScreenshot = ::saveScreenshot,
                                                    onNavigateToSettings = onNavigateToSettings,
                                                    onPreviousVideo = { moveVideo(-1) },
                                                    onNextVideo = { moveVideo(1) },
                                                    onTogglePlayback = {
                                                        isPlaying = !isPlaying
                                                        exoPlayer?.playWhenReady = isPlaying
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
        }

        if (touchIndicatorEnabled) {
            TapZoneGuideOverlay(
                labels = videoTapZoneGuideLabels(context, tapZoneCount),
                modifier = Modifier.matchParentSize()
            )
        }

        if (showClockBattery) {
            var clockText by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                val formatter = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                while (true) {
                    val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scaleValue = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val battery = if (level >= 0 && scaleValue > 0) "${(level * 100 / scaleValue)}%" else "--%"
                    clockText = "${formatter.format(java.util.Date())}  $battery"
                    delay(30_000)
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_full)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (isControlsVisible) dimensionResource(R.dimen.viewer_clock_battery_padding_top) else dimensionResource(R.dimen.spacing_medium), end = dimensionResource(R.dimen.spacing_medium))
            ) {
                Text(
                    text = clockText,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.popup_padding_h), vertical = dimensionResource(R.dimen.spacing_tiny)),
                    fontSize = textSizes.tiny
                )
            }
        }

        adjustmentLabel?.let { label ->
            val isBrightness = label == brightnessLabel
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
                    text = currentAdjustmentFormat.format(label, (adjustmentValue * 100).roundToInt()),
                    color = Color.White,
                    fontSize = textSizes.subtitle,
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

private fun formatFullscreenVideoTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0)
    val totalSeconds = safeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = safeMs % 1000
    return "%d:%02d.%03d".format(Locale.US, minutes, seconds, millis)
}

private fun videoTapZoneLabels(context: Context, zoneCount: Int): List<String> {
    val prev = AppConstants.ACTION_PREV
    val next = AppConstants.ACTION_NEXT
    val back = AppConstants.ACTION_PLAY_PAUSE // TODO: Check logic
    val brightness = context.getString(R.string.label_brightness)
    val screenshot = AppConstants.ACTION_SCREENSHOT
    val playPause = AppConstants.ACTION_PLAY_PAUSE
    val settings = AppConstants.ACTION_SETTINGS
    val list = context.getString(R.string.nav_folders)
    val volume = context.getString(R.string.label_volume)
    val forward = context.getString(R.string.viewer_skip)

    return when (zoneCount) {
        11 -> listOf(prev, back, back, brightness, screenshot, playPause, settings, list, volume, forward, next)
        4 -> listOf(prev, brightness, volume, next)
        else -> listOf(prev, playPause, next)
    }
}

private fun videoTapZoneGuideLabels(context: Context, zoneCount: Int): List<String> {
    val prev = context.getString(R.string.label_action_previous_video)
    val next = context.getString(R.string.label_action_next_video)
    val back10 = context.getString(R.string.skip_10) + context.getString(R.string.btn_reset)
    val forward10 = context.getString(R.string.skip_10) + context.getString(R.string.btn_decide)
    val brightness = context.getString(R.string.label_brightness)
    val screenshot = context.getString(R.string.label_action_screenshot)
    val playPause = context.getString(R.string.label_action_play_pause)
    val settings = context.getString(R.string.settings_title)
    val list = context.getString(R.string.nav_folders)
    val volume = context.getString(R.string.label_volume)

    return when (zoneCount) {
        11 -> listOf(prev, back10, next, brightness, screenshot, playPause, settings, list, volume, forward10, next)
        7 -> listOf(prev, next, back10, playPause, forward10, brightness, volume)
        5 -> listOf(back10, prev, playPause, next, forward10)
        4 -> listOf(prev, playPause, volume, next)
        else -> listOf(prev, playPause, next)
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
                model = ImageRequest.Builder(context)
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

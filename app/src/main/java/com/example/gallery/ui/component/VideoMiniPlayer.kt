package com.example.gallery.ui.component

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoMiniPlayer(
    uri: String,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            prepare()
            playWhenReady = true
        }
    }

    var isControlsVisible by remember { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onClose()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isSeeking) {
        while (!isSeeking) {
            videoPosition = exoPlayer.currentPosition
            videoDuration = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(isControlsVisible, interactionToken) {
        if (isControlsVisible) {
            delay(3000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(uri) {
        isControlsVisible = true
        interactionToken++
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isControlsVisible = !isControlsVisible
                if (isControlsVisible) interactionToken++
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.38f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.42f)
                            )
                        )
                    )
            )

            IconButton(
                onClick = {
                    interactionToken++
                    onFullscreen()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        if (videoDuration > 0) {
            GalleryVideoSeekBar(
                positionMs = videoPosition,
                durationMs = videoDuration,
                onSeekStart = { isSeeking = true },
                onSeek = { target ->
                    videoPosition = target
                    exoPlayer.seekTo(target)
                    interactionToken++
                },
                onSeekEnd = { isSeeking = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(18.dp),
                trackColor = Color.White.copy(alpha = 0.26f),
                progressColor = Color.White,
                thumbColor = Color.White
            )
        }
    }
}

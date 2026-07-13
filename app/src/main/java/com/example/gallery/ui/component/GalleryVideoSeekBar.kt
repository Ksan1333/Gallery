package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GalleryVideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.24f),
    progressColor: Color = Color.White.copy(alpha = 0.92f),
    thumbColor: Color = Color.White,
    trackHeight: Dp = 2.dp,
    thumbSize: Dp = 10.dp
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
                .height(trackHeight)
                .clip(RoundedCornerShape(1.dp))
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight)
                .clip(RoundedCornerShape(1.dp))
                .background(progressColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(thumbProgress)
                .height(thumbSize + 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

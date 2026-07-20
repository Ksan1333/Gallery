package com.example.gallery.ui.component

import android.util.Log
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

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
    thumbSize: Dp = 10.dp,
    reverseLayout: Boolean = false,
    anchors: List<Long> = emptyList()
) {
    var width by remember { mutableIntStateOf(0) }
    val currentAnchors by rememberUpdatedState(anchors)
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)

    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val thumbProgress = progress.coerceIn(0.001f, 1f)

    Box(
        modifier = modifier
            .onGloballyPositioned { width = it.size.width }
            .pointerInput(durationMs, reverseLayout) { // Removed width and anchors from keys
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    Log.d("SeekBar", "onSeekStart triggered")
                    currentOnSeekStart()

                    fun seekToX(x: Float) {
                        val currentWidth = width // Read latest width from state
                        val rawRatio = if (reverseLayout) {
                            1f - (x / currentWidth.coerceAtLeast(1)).coerceIn(0f, 1f)
                        } else {
                            (x / currentWidth.coerceAtLeast(1)).coerceIn(0f, 1f)
                        }
                        
                        var targetPos = (durationMs.coerceAtLeast(1L) * rawRatio).toLong()
                        
                        // Magnetic Snapping
                        val snapThresholdMs = (durationMs / 100).coerceAtLeast(1) // 1% of bar
                        val nearestAnchor = currentAnchors.minByOrNull { abs(it - targetPos) }
                        if (nearestAnchor != null && abs(nearestAnchor - targetPos) < snapThresholdMs) {
                            targetPos = nearestAnchor
                        }
                        
                        Log.d("SeekBar", "onSeek: $targetPos (original: ${(durationMs * rawRatio).toLong()})")
                        currentOnSeek(targetPos)
                    }

                    seekToX(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            Log.d("SeekBar", "Touch released")
                            break
                        }
                        seekToX(change.position.x)
                        change.consume()
                    }
                    Log.d("SeekBar", "onSeekEnd triggered")
                    currentOnSeekEnd()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(1.dp))
                .background(trackColor)
        )
        
        // Anchors
        if (durationMs > 0) {
            anchors.forEach { anchorPos ->
                val anchorRatio = (anchorPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(anchorRatio)
                        .height(thumbSize + 4.dp)
                        .align(if (reverseLayout) Alignment.CenterEnd else Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .align(if (reverseLayout) Alignment.CenterStart else Alignment.CenterEnd)
                            .size(thumbSize + 2.dp)
                            .clip(CircleShape)
                            .background(thumbColor.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // Progress
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight)
                .align(if (reverseLayout) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(RoundedCornerShape(1.dp))
                .background(progressColor)
        )
        
        // Thumb
        Box(
            modifier = Modifier
                .fillMaxWidth(thumbProgress)
                .height(thumbSize + 8.dp)
                .align(if (reverseLayout) Alignment.CenterEnd else Alignment.CenterStart),
            contentAlignment = if (reverseLayout) Alignment.CenterStart else Alignment.CenterEnd
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

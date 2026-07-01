package com.example.gallery.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants
import kotlin.math.roundToInt

@Composable
fun OperationProgressIndicator(
    label: String,
    progress: Float?,
    displayMode: String,
    minimumStyle: String,
    modifier: Modifier = Modifier
) {
    val boundedProgress = progress?.coerceIn(0f, 1f)
    val normalizedDisplayMode = displayMode.uppercase()
    val normalizedMinimumStyle = minimumStyle.uppercase()
    if (normalizedDisplayMode == "MIN") {
        if (normalizedMinimumStyle == "CIRCLE") {
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .size(58.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (boundedProgress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(58.dp),
                        color = Color.Cyan,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { boundedProgress },
                        modifier = Modifier.size(58.dp),
                        color = Color.Cyan,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
                Text(
                    text = boundedProgress?.let { "${(it * 100).roundToInt()}%" } ?: "...",
                    color = Color.White,
                    fontSize = AppConstants.TinyFontSize
                )
            }
        } else {
            Column(modifier = modifier.width(180.dp).padding(horizontal = 8.dp)) {
                Text(label, color = Color.White, fontSize = AppConstants.TinyFontSize)
                if (boundedProgress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { boundedProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
            }
        }
        return
    }

    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = Color.White, fontSize = AppConstants.SmallFontSize)
            Spacer(Modifier.height(6.dp))
            if (boundedProgress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { boundedProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

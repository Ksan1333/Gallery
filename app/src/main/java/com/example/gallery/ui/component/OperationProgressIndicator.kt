package com.example.gallery.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlin.math.roundToInt

private object OperationProgressTokens {
    val CircleSize = 58.dp
    val CollapsedBarHeight = 6.dp
    val CollapsedBarTouchHeight = 18.dp
    val CardCornerRadius = 8.dp
    val CardHorizontalPadding = 14.dp
    val CardVerticalPadding = 10.dp
    val CardGap = 6.dp
    val StrokeWidth = 1.dp
}

@Composable
fun OperationProgressIndicator(
    label: String,
    progress: Float?,
    displayMode: String,
    minimumStyle: String,
    centerText: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val boundedProgress = progress?.coerceIn(0f, 1f)
    val normalizedDisplayMode = displayMode.uppercase()
    val normalizedMinimumStyle = minimumStyle.uppercase()
    if (normalizedDisplayMode == "MIN") {
        if (normalizedMinimumStyle == "CIRCLE") {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val maxOffsetX = with(density) {
                (configuration.screenWidthDp.dp - OperationProgressTokens.CircleSize).toPx().coerceAtLeast(0f)
            }
            val maxOffsetY = with(density) {
                (configuration.screenHeightDp.dp - OperationProgressTokens.CircleSize).toPx().coerceAtLeast(0f)
            }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .size(OperationProgressTokens.CircleSize)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.2f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset = Offset(
                                x = (offset.x + dragAmount.x).coerceIn(0f, maxOffsetX),
                                y = (offset.y + dragAmount.y).coerceIn(0f, maxOffsetY)
                            )
                        }
                    }
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (boundedProgress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OperationProgressTokens.CircleSize),
                        color = colors.accent,
                        trackColor = colors.primaryText.copy(alpha = 0.2f)
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { boundedProgress },
                        modifier = Modifier.size(OperationProgressTokens.CircleSize),
                        color = colors.accent,
                        trackColor = colors.primaryText.copy(alpha = 0.2f)
                    )
                }
                Text(
                    text = centerText ?: boundedProgress?.let { "${(it * 100).roundToInt()}%" } ?: "...",
                    color = colors.primaryText,
                    fontSize = textSizes.tiny
                )
            }
        } else {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(OperationProgressTokens.CollapsedBarTouchHeight)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (boundedProgress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(OperationProgressTokens.CollapsedBarHeight)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { boundedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(OperationProgressTokens.CollapsedBarHeight)
                    )
                }
            }
        }
        return
    }

    Surface(
        color = colors.background.copy(alpha = 0.78f),
        shape = RoundedCornerShape(OperationProgressTokens.CardCornerRadius),
        border = BorderStroke(OperationProgressTokens.StrokeWidth, colors.primaryText.copy(alpha = 0.12f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = OperationProgressTokens.CardHorizontalPadding, vertical = OperationProgressTokens.CardVerticalPadding)) {
            Text(label, color = colors.primaryText, fontSize = textSizes.small)
            Spacer(Modifier.height(OperationProgressTokens.CardGap))
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

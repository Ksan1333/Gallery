package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants

@Suppress("UNUSED_PARAMETER")
@Composable
fun TapZoneGuideOverlay(
    labels: List<String>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    if (labels.isEmpty()) return
    val borderColor = Color.White.copy(alpha = 0.28f)
    val labelBackground = Color.Black.copy(alpha = 0.46f)
    val specs = tapZoneSpecs(labels.size)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cellWidth = maxWidth / 5f
        val cellHeight = maxHeight / 5f
        specs.zip(labels).forEach { (spec, label) ->
            val x = cellWidth * spec.column.toFloat()
            val y = cellHeight * spec.row.toFloat()
            val width = cellWidth * spec.columnSpan.toFloat()
            val height = cellHeight * spec.rowSpan.toFloat()
            Box(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(width = width, height = height)
                    .border(1.dp, borderColor)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                TapZoneLabel(label, labelBackground)
            }
        }
    }
}

@Composable
private fun TapZoneLabel(label: String, background: Color) {
    Text(
        text = label,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = AppConstants.TinyFontSize,
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
    )
}

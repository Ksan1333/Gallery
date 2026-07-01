package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants

@Composable
fun TapZoneGuideOverlay(
    labels: List<String>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    if (labels.isEmpty()) return
    val borderColor = Color.White.copy(alpha = 0.28f)
    val labelBackground = Color.Black.copy(alpha = 0.46f)
    val cellModifier = Modifier
        .border(1.dp, borderColor)
        .padding(4.dp)

    if (vertical) {
        Column(modifier = modifier.fillMaxSize()) {
            labels.forEach { label ->
                Box(
                    modifier = cellModifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    TapZoneLabel(label, labelBackground)
                }
            }
        }
    } else {
        Row(modifier = modifier.fillMaxSize()) {
            labels.forEach { label ->
                Box(
                    modifier = cellModifier
                        .fillMaxHeight()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    TapZoneLabel(label, labelBackground)
                }
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

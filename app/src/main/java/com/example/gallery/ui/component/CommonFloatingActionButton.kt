package com.example.gallery.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommonFloatingActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    contentColor: Color = Color.White,
    contentDescription: String? = null,
    tooltipDescription: String? = null 
) {
    val context = LocalContext.current
    
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = {
                        if (onLongClick != null) {
                            onLongClick()
                        }
                        if (tooltipDescription != null) {
                            android.widget.Toast.makeText(context, tooltipDescription, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false)
                ),
            color = containerColor,
            contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.3f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription ?: tooltipDescription,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

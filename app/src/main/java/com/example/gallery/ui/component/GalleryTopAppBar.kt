package com.example.gallery.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopAppBar(
    title: String,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    navigationEnabled: Boolean = true,
    centered: Boolean = false,
    containerColor: Color = Color.Black,
    contentColor: Color = Color.White,
    disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
    actions: @Composable RowScope.() -> Unit = {}
) {
    val navigation: @Composable () -> Unit = {
        if (navigationIcon != null && onNavigationClick != null) {
            IconButton(onClick = onNavigationClick, enabled = navigationEnabled) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationContentDescription,
                    tint = if (navigationEnabled) contentColor else disabledContentColor
                )
            }
        }
    }

    if (centered) {
        CenterAlignedTopAppBar(
            title = { Text(title, color = contentColor) },
            navigationIcon = navigation,
            actions = actions,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = containerColor)
        )
    } else {
        TopAppBar(
            title = { Text(title, color = contentColor) },
            navigationIcon = navigation,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
        )
    }
}

package com.example.gallery.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GalleryFloatingCloseButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GalleryFloatingActionButton(
        icon = Icons.Default.Close,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        contentDescription = "閉じる"
    )
}

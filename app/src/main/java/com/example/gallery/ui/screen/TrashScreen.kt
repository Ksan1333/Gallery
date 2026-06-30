package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.GalleryTopAppBar
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    onMenuClick: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val trashMedia by galleryState.repository.getTrashMedia().collectAsState(initial = emptyList())

    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryTopAppBar(
                title = "ゴミ箱",
                navigationIcon = if (onMenuClick != null) Icons.Default.Menu else null,
                navigationContentDescription = "メニュー",
                onNavigationClick = onMenuClick,
                actions = {
                    if (isSelectionModeActive && selectedUris.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        galleryState.repository.permanentlyDelete(selectedUris.toList())
                                        clearSelectionSignal++
                                        selectedUris.clear()
                                    }
                                }
                            ) {
                                Text("完全に削除", color = Color.Red, fontSize = com.example.gallery.ui.AppConstants.SmallFontSize)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        galleryState.repository.restoreFromTrash(selectedUris.toList())
                                        clearSelectionSignal++
                                        selectedUris.clear()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("復元")
                            }
                        }
                    }
                }
            )

            if (trashMedia.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ゴミ箱は空です", color = Color.Gray)
                }
            } else {
                GalleryGridView(
                    imageList = trashMedia,
                    onImageClick = { index, _ ->
                        selectedImageIndex = index
                        onShowViewer()
                    },
                    galleryState = galleryState,
                    clearSelectionSignal = clearSelectionSignal,
                    onSelectionModeChanged = { isSelectionModeActive = it },
                    onSelectionChanged = { uris ->
                        selectedUris.clear()
                        selectedUris.addAll(uris)
                    },
                    modifier = Modifier.fillMaxSize(),
                    isFilterEnabled = false, // ゴミ箱ではフィルタを無効にする。
                    isTrashMode = true,
                    onScrollConsumed = { /* ゴミ箱では URI 同期は不要。 */ }
                )
            }
        }

        selectedImageIndex?.let { index ->
            MediaViewerScreen(
                onClickedClose = { selectedImageIndex = null; onHideViewer() },
                initialPage = index,
                imageList = trashMedia,
                galleryState = galleryState,
                onPageSelected = { selectedImageIndex = it },
                showDeleteButton = false, // ゴミ箱内なので削除ボタンの代わりに復元ボタンを表示する。
                isTrashMode = true
            )
        }
    }
}

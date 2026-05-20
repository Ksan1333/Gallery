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
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.PictureViewer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    onMenuClick: (() -> Unit)? = null // 追加
) {
    val scope = rememberCoroutineScope()
    val trashMedia by galleryState.repository.getTrashMedia().collectAsState(initial = emptyList())

    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    var selectedUris = remember { mutableStateListOf<String>() }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ヘッダー
            TopAppBar(
                title = { Text("ゴミ箱", color = Color.White) },
                navigationIcon = {
                    if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "メニュー", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    if (isSelectionModeActive && selectedUris.isNotEmpty()) {
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
                            Text("選択項目を復元")
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
                    isFilterEnabled = false, // ゴミ箱ではフィルタ無効
                    isTrashMode = true
                )
            }
        }

        selectedImageIndex?.let { index ->
            PictureViewer(
                onClickedClose = { selectedImageIndex = null; onHideViewer() },
                initialPage = index,
                imageList = trashMedia,
                galleryState = galleryState,
                onPageSelected = { selectedImageIndex = it },
                showDeleteButton = false, // ゴミ箱内なので削除ボタンの代わりに復元ボタンを表示
                isTrashMode = true
            )
        }
    }
}

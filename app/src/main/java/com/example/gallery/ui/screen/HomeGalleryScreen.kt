package com.example.gallery.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.PictureViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.AgeRatingFilter
import android.util.Log
import androidx.compose.runtime.saveable.rememberSaveable


@Composable
fun HomeGalleryScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    initialMediaUri: String? = null,
    onMenuClick: (() -> Unit)? = null,
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onNavigateToTag: ((String) -> Unit)? = null // 追加
) {
    val context = LocalContext.current
    var imageList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // インスタンス状態への保存を停止（TransactionTooLargeException対策）
    // configChanges を設定しているため、回転時にはこれで保持される
    val flatListForViewerState = remember {
        mutableStateOf(emptyList<MediaData>()) 
    }
    var flatListForViewer by flatListForViewerState

    val scope = rememberCoroutineScope()

    LaunchedEffect(imageList.size) {
        if (selectedIndex != null && flatListForViewer.isEmpty() && imageList.isNotEmpty()) {
            flatListForViewer = imageList
        }
    }

    LaunchedEffect(selectedIndex, flatListForViewer) {
        selectedIndex?.let { idx -> flatListForViewer.getOrNull(idx)?.uri?.let { uri -> galleryState.lastViewedUri = uri } }
    }

    LaunchedEffect(imageList.size, initialMediaUri) {
        if (initialMediaUri != null && imageList.isNotEmpty()) {
            val idx = imageList.indexOfFirst { it.uri == initialMediaUri }
            if (idx != -1) {
                flatListForViewer = imageList
                selectedIndex = idx
                onShowViewer()
            }
        }
    }

    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionModeActive || selectedIndex != null) {
        if (selectedIndex != null) {
            selectedIndex = null
            onHideViewer()
        } else if (isSelectionModeActive) clearSelectionSignal++
    }

    fun localLoadImages() {
        // すでにロード中なら重複して走らせない
        if (isLoading) return
        
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            val allMedia = galleryState.repository.getAllMedia(forceRefresh = false)
            withContext(Dispatchers.Main) {
                // 件数や最初のアイテムが違う場合のみ更新（簡易的な比較）
                if (imageList.size != allMedia.size || imageList.getOrNull(0)?.uri != allMedia.getOrNull(0)?.uri) {
                    imageList = allMedia
                }
                isLoading = false
            }
        }
    }

    // 起動時の初期ロード
    LaunchedEffect(galleryState.refreshTrigger) {
        localLoadImages()
    }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryGridView(
                imageList = imageList,
                onImageClick = { index, list -> flatListForViewer = list; selectedIndex = index; onShowViewer() },
                galleryState = galleryState,
                isLoading = isLoading,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = "すべて",
                scrollToUri = if (selectedIndex == null) galleryState.lastViewedUri else null,
                isFilterEnabled = true,
                onMenuClick = onMenuClick,
                onPageChangedInViewer = { galleryState.lastViewedUri = it },
                onBulkEdit = onBulkEdit,
                onScrollConsumed = { galleryState.lastViewedUri = null }
            )
        }

        selectedIndex?.let { initialPage ->
            if (flatListForViewer.isNotEmpty()) {
                PictureViewer(
                    onClickedClose = { selectedIndex = null; onHideViewer() },
                    initialPage = initialPage,
                    imageList = flatListForViewer,
                    galleryState = galleryState,
                    onNavigateToTag = { tag ->
                        selectedIndex = null
                        onHideViewer()
                        onNavigateToTag?.invoke(tag)
                    },
                    onPageSelected = { selectedIndex = it },
                    onNavigateToMedia = { uri ->
                        val idx = imageList.indexOfFirst { it.uri == uri }
                        if (idx != -1) { flatListForViewer = imageList.toList(); selectedIndex = idx }
                        else {
                            scope.launch {
                                val allMedia = galleryState.repository.getAllMedia()
                                val newIdx = allMedia.indexOfFirst { it.uri == uri }
                                if (newIdx != -1) { flatListForViewer = allMedia; selectedIndex = newIdx }
                                else {
                                    withContext(Dispatchers.Main) {
                                        flatListForViewer = listOf(MediaData(uri, System.currentTimeMillis()))
                                        selectedIndex = 0
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

package com.example.gallery.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val configuration = LocalConfiguration.current
    val deviceAspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    val pagingItems = remember(
        galleryState.mediaTypeFilter,
        galleryState.ageRatingFilter,
        galleryState.deviceFilter,
        galleryState.sortMode,
        galleryState.isAscending,
        galleryState.groupingMode,
        galleryState.refreshTrigger
    ) {
        galleryState.repository.getGridItemPagingFlow(
            mediaType = galleryState.mediaTypeFilter,
            ageRating = galleryState.ageRatingFilter,
            deviceFilter = galleryState.deviceFilter,
            deviceAspectRatio = deviceAspectRatio,
            sortMode = galleryState.sortMode,
            isAscending = galleryState.isAscending,
            groupingMode = galleryState.groupingMode
        )
    }.collectAsLazyPagingItems()

    var imageList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // インスタンス状態への保存を停止（TransactionTooLargeException対策）
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
        if (isLoading) return
        
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            
            // 1. 裏で同期を実行。同期が終わるのを待たずにUIはPagingで勝手に更新される。
            galleryState.repository.syncMediaStoreToRoom()
            
            // 2. Viewer用の全リスト同期などのためにDBから取得
            val updatedMedia = galleryState.repository.getAllMedia(forceRefresh = true)
            withContext(Dispatchers.Main) {
                imageList = updatedMedia
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
                pagingItems = pagingItems,
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
                MediaViewerScreen(
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

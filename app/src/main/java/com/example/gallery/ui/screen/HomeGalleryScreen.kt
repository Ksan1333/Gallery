package com.example.gallery.ui.screen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SCROLL_RESTORE_TRACE = "GALLERY_SCROLL_RESTORE_TRACE"

private fun logScrollRestoreTrace(message: String) {
    Log.d(SCROLL_RESTORE_TRACE, "$SCROLL_RESTORE_TRACE $message")
}

@Composable
fun HomeGalleryScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    initialMediaUri: String? = null,
    onMenuClick: (() -> Unit)? = null,
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onBulkMove: ((List<String>) -> Unit)? = null,
    onNavigateToTag: ((String) -> Unit)? = null // 追加
) {
    val initialScrollIndex = 0
    val initialScrollOffset = 0
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
    var centerViewedMediaOnReturn by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var restoreScrollRequestKey by remember { mutableIntStateOf(0) }
    var pendingScrollRestore by remember { mutableStateOf(false) }
    
    // インスタンス状態への保存を停止（TransactionTooLargeException対策）
    val flatListForViewerState = remember {
        mutableStateOf(emptyList<MediaData>()) 
    }
    var flatListForViewer by flatListForViewerState

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        logScrollRestoreTrace(
            "home_enter initialIndex=$initialScrollIndex initialOffset=$initialScrollOffset " +
                "hasSessionPosition=${galleryState.hasHomeGalleryScrollPosition} " +
                "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                "refresh=${galleryState.refreshTrigger} initialMedia=${initialMediaUri != null}"
        )
    }

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
                centerViewedMediaOnReturn = true
                galleryState.lastViewedUri = initialMediaUri
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

    fun requestSavedScrollRestore(reason: String) {
        if (initialMediaUri != null || selectedIndex != null) {
            logScrollRestoreTrace(
                "home_restore_skip reason=$reason skip=viewer_or_initial_media " +
                    "initialMedia=${initialMediaUri != null} selectedIndex=$selectedIndex " +
                    "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                    "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                    "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                    "imageCount=${imageList.size} refresh=${galleryState.refreshTrigger}"
            )
            return
        }
        if (!galleryState.hasHomeGalleryScrollPosition) {
            logScrollRestoreTrace(
                "home_restore_skip reason=$reason skip=no_session_position " +
                    "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                    "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                    "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                    "imageCount=${imageList.size} refresh=${galleryState.refreshTrigger}"
            )
            return
        }
        val index = galleryState.homeGalleryScrollIndex
        val offset = galleryState.homeGalleryScrollOffset
        if (index <= 0 && offset <= 0) {
            logScrollRestoreTrace(
                "home_restore_skip reason=$reason skip=top_or_empty " +
                    "sessionIndex=$index sessionOffset=$offset imageCount=${imageList.size} " +
                    "refresh=${galleryState.refreshTrigger}"
            )
            return
        }
        pendingScrollRestore = true
        restoreScrollRequestKey++
        logScrollRestoreTrace(
            "home_restore_request reason=$reason key=$restoreScrollRequestKey " +
                "sessionIndex=$index sessionOffset=$offset uriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                "imageCount=${imageList.size} " +
                "refresh=${galleryState.refreshTrigger} grouping=${galleryState.groupingMode} " +
                "sort=${galleryState.sortMode} asc=${galleryState.isAscending}"
        )
    }

    fun localLoadImages() {
        if (isLoading) {
            logScrollRestoreTrace(
                "home_load_skip reason=already_loading refresh=${galleryState.refreshTrigger} " +
                    "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                    "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                    "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                    "imageCount=${imageList.size}"
            )
            return
        }
        
        val startTime = System.currentTimeMillis()
        val refreshAtStart = galleryState.refreshTrigger
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isLoading = true
                logScrollRestoreTrace(
                    "home_load_start refresh=$refreshAtStart sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                        "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                        "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} imageCount=${imageList.size}"
                )
            }
            
            // 1. 裏で同期を実行。同期が終わるのを待たずにUIはPagingで勝手に更新される。
            galleryState.repository.syncMediaStoreToRoom()
            
            // 2. Viewer用の全リスト同期などのためにDBから取得
            val updatedMedia = galleryState.repository.getAllMedia(forceRefresh = true)
            withContext(Dispatchers.Main) {
                imageList = updatedMedia
                isLoading = false
                logScrollRestoreTrace(
                    "home_load_done refreshStart=$refreshAtStart refreshCurrent=${galleryState.refreshTrigger} " +
                        "loaded=${updatedMedia.size} elapsedMs=${System.currentTimeMillis() - startTime} " +
                        "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                        "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                        "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                        "pending=$pendingScrollRestore"
                )
                requestSavedScrollRestore("load_done")
            }
        }
    }

    // 起動時の初期ロード
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, initialMediaUri, selectedIndex) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                logScrollRestoreTrace(
                    "home_on_resume refresh=${galleryState.refreshTrigger} " +
                        "hasSessionPosition=${galleryState.hasHomeGalleryScrollPosition} " +
                        "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                        "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                        "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} imageCount=${imageList.size} " +
                        "pending=$pendingScrollRestore selectedIndex=$selectedIndex"
                )
                requestSavedScrollRestore("home_on_resume")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(galleryState.refreshTrigger) {
        logScrollRestoreTrace(
            "home_refresh_trigger trigger=${galleryState.refreshTrigger} imageCount=${imageList.size} " +
                "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} pending=$pendingScrollRestore"
        )
        localLoadImages()
    }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryGridView(
                imageList = imageList,
                pagingItems = pagingItems,
                onImageClick = { index, list ->
                    centerViewedMediaOnReturn = true
                    galleryState.lastViewedUri = list.getOrNull(index)?.uri
                    flatListForViewer = list
                    selectedIndex = index
                    onShowViewer()
                },
                galleryState = galleryState,
                isLoading = isLoading,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = "すべて",
                scrollToUri = if (selectedIndex == null && centerViewedMediaOnReturn) galleryState.lastViewedUri else null,
                centerScrollToUri = centerViewedMediaOnReturn,
                isFilterEnabled = true,
                onMenuClick = onMenuClick,
                onPageChangedInViewer = {
                    galleryState.lastViewedUri = it
                },
                onBulkEdit = onBulkEdit,
                onBulkMove = onBulkMove,
                initialScrollIndex = initialScrollIndex,
                initialScrollOffset = initialScrollOffset,
                restoreScrollIndex = galleryState.homeGalleryScrollIndex,
                restoreScrollOffset = galleryState.homeGalleryScrollOffset,
                restoreScrollUri = galleryState.homeGalleryScrollUri,
                restoreScrollRequestKey = restoreScrollRequestKey,
                onScrollAnchorChanged = { index, offset, anchorUri ->
                    if (!pendingScrollRestore) {
                        logScrollRestoreTrace(
                            "home_position_save index=$index offset=$offset prevIndex=${galleryState.homeGalleryScrollIndex} " +
                                "prevOffset=${galleryState.homeGalleryScrollOffset} anchorUriHash=${anchorUri?.hashCode()} " +
                                "prevUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} imageCount=${imageList.size} " +
                                "refresh=${galleryState.refreshTrigger}"
                        )
                        galleryState.hasHomeGalleryScrollPosition = true
                        galleryState.homeGalleryScrollIndex = index
                        galleryState.homeGalleryScrollOffset = offset
                        if (anchorUri != null) {
                            galleryState.homeGalleryScrollUri = anchorUri
                        }
                    } else {
                        logScrollRestoreTrace(
                            "home_position_save_suppressed reason=pending_restore index=$index offset=$offset " +
                                "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                                "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                                "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} " +
                                "anchorUriHash=${anchorUri?.hashCode()} key=$restoreScrollRequestKey imageCount=${imageList.size}"
                        )
                    }
                },
                onScrollRestored = { restoredKey ->
                    if (restoredKey == restoreScrollRequestKey) {
                        logScrollRestoreTrace(
                            "home_restore_consumed key=$restoredKey latestKey=$restoreScrollRequestKey " +
                                "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                                "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                                "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} imageCount=${imageList.size}"
                        )
                        pendingScrollRestore = false
                    } else {
                        logScrollRestoreTrace(
                            "home_restore_consumed_stale key=$restoredKey latestKey=$restoreScrollRequestKey " +
                                "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                                "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                                "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()} imageCount=${imageList.size}"
                        )
                    }
                },
                onScrollConsumed = {
                    galleryState.lastViewedUri = null
                    centerViewedMediaOnReturn = false
                }
            )
        }

        selectedIndex?.let { initialPage ->
            if (flatListForViewer.isNotEmpty()) {
                MediaViewerScreen(
                    onClickedClose = {
                        selectedIndex = null
                        onHideViewer()
                    },
                    initialPage = initialPage,
                    imageList = flatListForViewer,
                    galleryState = galleryState,
                    onNavigateToTag = { tag ->
                        selectedIndex = null
                        onHideViewer()
                        onNavigateToTag?.invoke(tag)
                    },
                    onPageSelected = {
                        selectedIndex = it
                        flatListForViewer.getOrNull(it)?.uri?.let { uri -> galleryState.lastViewedUri = uri }
                    },
                    onNavigateToMedia = { uri ->
                        centerViewedMediaOnReturn = true
                        galleryState.lastViewedUri = uri
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

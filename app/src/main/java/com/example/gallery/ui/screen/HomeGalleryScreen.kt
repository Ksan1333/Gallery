package com.example.gallery.ui.screen

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.gallery.R
import com.example.gallery.data.model.MediaData
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.search.filterGallerySearchResults
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.ui.theme.GalleryAlphaTokens
import com.example.gallery.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SCROLL_RESTORE_TRACE = "GALLERY_SCROLL_RESTORE_TRACE"
private val HomeAnalysisOperationTags = setOf("AI_TAGGING", "COLOR_VECTOR", "AUTO_RATING")

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
    onStartAnalysis: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onBulkMove: ((List<String>) -> Unit)? = null,
    onNavigateToTag: ((String) -> Unit)? = null
) {
    val initialScrollIndex = 0
    val initialScrollOffset = 0
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val homeShowVideos by remember(context) {
        val prefs = context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("homeShowVideos", false))
    }
    val deviceAspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    val pagingItems = remember(
        galleryState.mediaTypeFilter,
        galleryState.ageRatingFilter,
        galleryState.deviceFilter,
        galleryState.sortMode,
        galleryState.isAscending,
        galleryState.groupingMode,
        galleryState.refreshTrigger,
        homeShowVideos
    ) {
        val mediaType = if (!homeShowVideos && galleryState.mediaTypeFilter == MediaTypeFilter.ALL) {
            MediaTypeFilter.IMAGE
        } else {
            galleryState.mediaTypeFilter
        }
        galleryState.repository.getGridItemPagingFlow(
            mediaType = mediaType,
            ageRating = galleryState.ageRatingFilter,
            deviceFilter = galleryState.deviceFilter,
            deviceAspectRatio = deviceAspectRatio,
            sortMode = galleryState.sortMode,
            isAscending = galleryState.isAscending,
            groupingMode = galleryState.groupingMode
        )
    }.collectAsLazyPagingItems()

    var imageList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(galleryState.activeMediaViewerIndex) }
    var centerViewedMediaOnReturn by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var restoreScrollRequestKey by remember { mutableIntStateOf(0) }
    val operations by GlobalOperationService.operations.collectAsState(initial = emptyList())
    val isAiAnalysisRunning = operations.any { op ->
        op.tag?.let { it in HomeAnalysisOperationTags } == true || op.id in HomeAnalysisOperationTags
    }
    val aiAnalysisContentDescription = stringResource(
        if (isAiAnalysisRunning) R.string.gallery_ai_analysis_running else R.string.gallery_ai_analysis
    )
    var pendingScrollRestore by remember { mutableStateOf(false) }
    
    // インスタンス状態への保存を停止（TransactionTooLargeException対策）
    val flatListForViewerState = remember {
        mutableStateOf(galleryState.activeMediaViewerList)
    }
    var flatListForViewer by flatListForViewerState
    LaunchedEffect(flatListForViewer, selectedIndex) {
        galleryState.activeMediaViewerList = flatListForViewer
        galleryState.activeMediaViewerIndex = selectedIndex
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            onShowViewer()
        }
    }

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

    fun closeViewer(source: String, restoreViewedMedia: Boolean = true) {
        val viewerIndex = selectedIndex
        val viewerUri = viewerIndex?.let { flatListForViewer.getOrNull(it)?.uri }
            ?: galleryState.lastViewedUri
        if (restoreViewedMedia && viewerUri != null) {
            galleryState.lastViewedUri = viewerUri
            centerViewedMediaOnReturn = true
        } else {
            galleryState.lastViewedUri = null
            centerViewedMediaOnReturn = false
        }
        logScrollRestoreTrace(
            "home_viewer_close source=$source restore=$restoreViewedMedia " +
                "viewerIndex=$viewerIndex viewerUriHash=${viewerUri?.hashCode()} " +
                "listSize=${flatListForViewer.size} center=$centerViewedMediaOnReturn " +
                "sessionIndex=${galleryState.homeGalleryScrollIndex} " +
                "sessionOffset=${galleryState.homeGalleryScrollOffset} " +
                "sessionUriHash=${galleryState.homeGalleryScrollUri?.hashCode()}"
        )
        selectedIndex = null
        flatListForViewer = emptyList()
        galleryState.activeMediaViewerList = emptyList()
        galleryState.activeMediaViewerIndex = null
        onHideViewer()
    }

    BackHandler(
        enabled = isSelectionModeActive ||
            selectedIndex != null ||
            galleryState.returnToUnfilteredHomeOnBack
    ) {
        if (selectedIndex != null) {
            closeViewer("back")
        } else if (isSelectionModeActive) clearSelectionSignal++
        else if (galleryState.returnToUnfilteredHomeOnBack) {
            galleryState.clearHomeSearch()
        }
    }

    fun requestSavedScrollRestore(reason: String) {
        if (initialMediaUri != null || selectedIndex != null || centerViewedMediaOnReturn || galleryState.lastViewedUri != null) {
            logScrollRestoreTrace(
                "home_restore_skip reason=$reason skip=viewer_or_initial_media " +
                    "initialMedia=${initialMediaUri != null} selectedIndex=$selectedIndex " +
                    "centerReturn=$centerViewedMediaOnReturn viewedUriHash=${galleryState.lastViewedUri?.hashCode()} " +
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
            
            // 1. 裏で同期を実行。同期が終わるのを待たずにUIはPagingで更新される。
            galleryState.repository.syncMediaStoreToRoom()
            
            // 2. Viewer用の全リスト同期などのためにDBから取得
            // syncMediaStoreToRoom() has just refreshed the repository cache. Reusing that
            // result avoids immediately performing a second full MediaStore scan.
            val updatedMedia = galleryState.repository.getAllMedia().let { list ->
                if (!homeShowVideos && galleryState.mediaTypeFilter == MediaTypeFilter.ALL) {
                    list.filter { !it.isVideo }
                } else {
                    list
                }
            }
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

    val isSearchActive = galleryState.isHomeSearchActive
    val isFavoriteFilterActive = galleryState.homeFavoritesOnly
    // The Paging grid already reads the small visible window from Room. Loading and grouping the
    // complete tag/metadata tables here is only needed for a home search or favorites filter.
    val shouldLoadFilterMetadata = isSearchActive || isFavoriteFilterActive
    val homeTagsFlow = remember(shouldLoadFilterMetadata, galleryState.repository) {
        if (shouldLoadFilterMetadata) {
            galleryState.repository.getAllTagsWithUris()
        } else {
            flowOf(emptyList<com.example.gallery.data.local.entity.TagEntity>())
        }
    }
    val homeMetadataFlow = remember(shouldLoadFilterMetadata, galleryState.repository) {
        if (shouldLoadFilterMetadata) {
            galleryState.repository.getAllMetadataSummaryFlow()
        } else {
            flowOf(emptyList<com.example.gallery.data.local.entity.MediaMetadataSummary>())
        }
    }
    val allTagsData by homeTagsFlow.collectAsState(initial = emptyList())
    val metadataList by homeMetadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(metadataList) { metadataList.associateBy { it.uri } }
    val tagsByUri = remember(allTagsData) { allTagsData.groupBy({ it.uri }, { it.tag }) }

    LaunchedEffect(galleryState.pendingHomeSearchTag) {
        galleryState.pendingHomeSearchTag?.let { tag ->
            galleryState.homeSearchTags = galleryState.homeSearchTags + tag
            galleryState.homeSearchQuery = ""
            galleryState.pendingHomeSearchTag = null
        }
    }

    var displayedImages by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isSearchFiltering by remember { mutableStateOf(false) }

    LaunchedEffect(
        imageList,
        metadataMap,
        tagsByUri,
        galleryState.homeSearchQuery,
        galleryState.homeSearchTags,
        galleryState.homeSearchMatchMode,
        galleryState.homeSearchMediaTypes,
        galleryState.homeSearchFolders,
        galleryState.homeSearchStorageTypes,
        galleryState.homeSearchAgeRatings,
        galleryState.homeSearchFavoritesOnly,
        galleryState.homeFavoritesOnly
    ) {
        val searchQuery = galleryState.homeSearchQuery
        val searchTags = galleryState.homeSearchTags
        val searchMatchMode = galleryState.homeSearchMatchMode
        val searchMediaTypes = galleryState.homeSearchMediaTypes
        val searchFolders = galleryState.homeSearchFolders
        val searchStorageTypes = galleryState.homeSearchStorageTypes
        val searchAgeRatings = galleryState.homeSearchAgeRatings
        val searchFavoritesOnly = galleryState.homeSearchFavoritesOnly
        val favoritesOnlyFilter = galleryState.homeFavoritesOnly
        val needsFiltering = isSearchActive || favoritesOnlyFilter
        if (!needsFiltering) {
            displayedImages = imageList
            isSearchFiltering = false
        } else {
            isSearchFiltering = true
            displayedImages = withContext(Dispatchers.Default) {
                val searchFiltered = if (!isSearchActive) {
                    imageList
                } else {
                    filterGallerySearchResults(
                        mediaItems = imageList,
                        metadataByUri = metadataMap,
                        tagsByUri = tagsByUri,
                        query = searchQuery,
                        selectedTags = searchTags,
                        matchMode = searchMatchMode,
                        selectedMediaTypes = searchMediaTypes,
                        selectedFolders = searchFolders,
                        selectedStorageTypes = searchStorageTypes,
                        selectedAgeRatings = searchAgeRatings,
                        favoritesOnly = searchFavoritesOnly
                    )
                }
                if (favoritesOnlyFilter) {
                    searchFiltered.filter { item -> metadataMap[item.uri]?.isFavorite == true }
                } else {
                    searchFiltered
                }
            }
            isSearchFiltering = false
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(GalleryThemeTokens.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryGridView(
                imageList = displayedImages,
                pagingItems = if (isSearchActive || isFavoriteFilterActive || isSearchFiltering) null else pagingItems,
                onImageClick = { index, list ->
                    centerViewedMediaOnReturn = false
                    val clickedUri = list.getOrNull(index)?.uri
                    galleryState.lastViewedUri = clickedUri
                    fun openViewerWith(viewerList: List<MediaData>) {
                        val viewerIndex = clickedUri?.let { uri ->
                            viewerList.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }
                        } ?: index.coerceIn(0, (viewerList.size - 1).coerceAtLeast(0))
                        flatListForViewer = viewerList
                        selectedIndex = viewerIndex
                        galleryState.activeMediaViewerList = viewerList
                        galleryState.activeMediaViewerIndex = viewerIndex
                        onShowViewer()
                    }
                    openViewerWith(list)
                },
                galleryState = galleryState,
                isLoading = isLoading,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = stringResource(R.string.label_all_media),
                scrollToUri = if (selectedIndex == null && centerViewedMediaOnReturn) galleryState.lastViewedUri else null,
                centerScrollToUri = centerViewedMediaOnReturn,
                isFilterEnabled = false,
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
                    logScrollRestoreTrace(
                        "home_scroll_to_viewed_consumed uriHash=${galleryState.lastViewedUri?.hashCode()} " +
                            "center=$centerViewedMediaOnReturn imageCount=${imageList.size}"
                    )
                    galleryState.lastViewedUri = null
                    centerViewedMediaOnReturn = false
                },
                topBarActions = {
                    var showSortMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onStartAnalysis,
                        enabled = !isAiAnalysisRunning,
                        modifier = Modifier.semantics { contentDescription = aiAnalysisContentDescription }
                    ) {
                        if (isAiAnalysisRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(dimensionResource(R.dimen.icon_size_small)), // ButtonDefaults.IconSize is ~18dp
                                strokeWidth = dimensionResource(R.dimen.spacing_micro),
                                color = GalleryThemeTokens.colors.accent
                            )
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.gallery_ai_analysis),
                                tint = GalleryThemeTokens.colors.primaryText
                            )
                        }
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.label_sort),
                            tint = GalleryThemeTokens.colors.primaryText
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(GalleryThemeTokens.colors.surfaceVariant)
                        ) {
                            SortMode.entries.forEach { mode ->
                                listOf(true, false).forEach { ascending ->
                                    val isSelected = galleryState.sortMode == mode && galleryState.isAscending == ascending
                                    DropdownMenuItem(
                                        text = {
                                            val modeName = when (mode) {
                                                SortMode.DATE_ADDED -> stringResource(R.string.label_date_added)
                                                SortMode.SIZE -> stringResource(R.string.label_size)
                                                SortMode.NAME -> stringResource(R.string.label_name)
                                            }
                                            val orderName = if (ascending) stringResource(R.string.label_ascending) else stringResource(R.string.label_descending)
                                            Text(
                                                text = stringResource(R.string.sort_format, modeName, orderName),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (isSelected) GalleryThemeTokens.colors.accent else GalleryThemeTokens.colors.primaryText
                                            )
                                        },
                                        onClick = {
                                            galleryState.sortMode = mode
                                            galleryState.isAscending = ascending
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.label_search),
                            tint = if (isSearchActive) GalleryThemeTokens.colors.accent else GalleryThemeTokens.colors.primaryText
                        )
                    }
                }
            )
        }
        val shouldShowSearchProgress = isSearchFiltering || ((isSearchActive || isFavoriteFilterActive) && isLoading)
        if (shouldShowSearchProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GalleryThemeTokens.colors.background.copy(alpha = GalleryAlphaTokens.Overlay)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = GalleryThemeTokens.colors.accent)
            }
        }
        selectedIndex?.let { initialPage ->
            if (flatListForViewer.isNotEmpty()) {
                MediaViewerScreen(
                    onClickedClose = {
                        closeViewer("button")
                    },
                    initialPage = initialPage,
                    imageList = flatListForViewer,
                    galleryState = galleryState,
                    onNavigateToTag = { tag ->
                        closeViewer("tag", restoreViewedMedia = false)
                        galleryState.returnToUnfilteredHomeOnBack = true
                        onNavigateToTag?.invoke(tag)
                    },
                    onPageSelected = {
                        selectedIndex = it
                        galleryState.activeMediaViewerIndex = it
                        flatListForViewer.getOrNull(it)?.uri?.let { uri ->
                            galleryState.lastViewedUri = uri
                            logScrollRestoreTrace(
                                "home_viewer_page_selected index=$it uriHash=${uri.hashCode()} " +
                                    "listSize=${flatListForViewer.size}"
                            )
                        }
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

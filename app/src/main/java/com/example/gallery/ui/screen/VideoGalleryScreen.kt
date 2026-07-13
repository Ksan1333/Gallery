package com.example.gallery.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.component.VideoMiniPlayer
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.SortMode
import com.example.gallery.ui.theme.GalleryThemeTokens

@Composable
fun VideoGalleryScreen(
    galleryState: GalleryState,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFullscreenVideo: (List<MediaData>, Int) -> Unit,
    onFolderStateChanged: (Boolean) -> Unit = {}
) {
    var allVideos by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var foldersWithVideos by remember { mutableStateOf<List<CategoryData>>(emptyList()) }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    var activeVideoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeVideoList by remember { mutableStateOf<List<MediaData>>(emptyList()) }

    fun closePreview() {
        activeVideoIndex = null
        activeVideoList = emptyList()
    }

    fun closeFolder() {
        selectedFolderName = null
        closePreview()
    }

    fun applyVideoList(videos: List<MediaData>) {
        allVideos = videos
        foldersWithVideos = videos
            .groupBy { it.folderName }
            .map { (name, folderVideos) ->
                CategoryData(
                    id = name,
                    title = name,
                    count = folderVideos.size,
                    thumbnail = folderVideos.firstOrNull()?.uri
                )
            }
            .sortedBy { it.title }
    }

    BackHandler(enabled = activeVideoIndex != null) {
        closePreview()
    }

    LaunchedEffect(galleryState.videoHomeRequestToken) {
        if (galleryState.videoHomeRequestToken > 0 && selectedFolderName != null) {
            closeFolder()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, galleryState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                galleryState.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(galleryState.refreshTrigger) {
        val cachedVideos = galleryState.cachedVideoItems
        if (cachedVideos != null && galleryState.cachedVideoRefreshTrigger == galleryState.refreshTrigger) {
            applyVideoList(cachedVideos)
            return@LaunchedEffect
        }

        val videos = galleryState.repository.getAllMedia().filter { it.isVideo }
        galleryState.cachedVideoItems = videos
        galleryState.cachedVideoRefreshTrigger = galleryState.refreshTrigger
        applyVideoList(videos)
    }

    val folderVideos = remember(selectedFolderName, allVideos) {
        if (selectedFolderName == null) emptyList()
        else allVideos.filter { it.folderName == selectedFolderName }
    }
    LaunchedEffect(selectedFolderName) {
        onFolderStateChanged(selectedFolderName != null)
    }
    val playbackVideos = if (activeVideoList.isNotEmpty()) activeVideoList else folderVideos
    val activePlaybackIndex = activeVideoIndex?.takeIf { it in playbackVideos.indices }
    val colors = GalleryThemeTokens.colors

    Scaffold(
        topBar = {
            if (selectedFolderName == null) {
                GalleryTopAppBar(
                    title = stringResource(R.string.nav_videos),
                    navigationIcon = androidx.compose.material.icons.Icons.Default.Menu,
                    navigationContentDescription = stringResource(R.string.btn_open),
                    onNavigationClick = onMenuClick,
                    containerColor = colors.topBar,
                    actions = {
                        androidx.compose.material3.IconButton(onClick = onSettingsClick) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                                tint = colors.primaryText
                            )
                        }
                    }
                )
            }
        },
        containerColor = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
        ) {
            if (activePlaybackIndex != null && playbackVideos.isNotEmpty()) {
                VideoMiniPlayer(
                    uri = playbackVideos[activePlaybackIndex].uri,
                    onClose = ::closePreview,
                    onFullscreen = {
                        onFullscreenVideo(playbackVideos, activePlaybackIndex)
                        closePreview()
                    },
                    onNext = {
                        activeVideoIndex = (activePlaybackIndex + 1) % playbackVideos.size
                    },
                    onPrevious = {
                        activeVideoIndex = (activePlaybackIndex - 1 + playbackVideos.size) % playbackVideos.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                CategoryScreen(
                    title = stringResource(R.string.nav_videos),
                    categories = foldersWithVideos,
                    galleryState = galleryState,
                    onCategoryClick = { selectedFolderName = it.id },
                    onShowViewer = { },
                    onHideViewer = { },
                    onMenuClick = null,
                    selectedCategoryTitle = selectedFolderName,
                    selectedCategoryMedia = folderVideos,
                    onBackFromCategory = ::closeFolder,
                    onPageChangedInViewer = { },
                    onBulkEdit = { },
                    topBarActions = {
                        if (selectedFolderName != null) {
                            var showSortMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.label_sort),
                                    tint = colors.primaryText
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(colors.surfaceVariant)
                            ) {
                                SortMode.entries.forEach { mode ->
                                    listOf(true, false).forEach { ascending ->
                                        val isSelected = galleryState.sortMode == mode && galleryState.isAscending == ascending
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${when (mode) { SortMode.DATE_ADDED -> stringResource(R.string.label_date_added); SortMode.SIZE -> stringResource(R.string.label_size); SortMode.NAME -> stringResource(R.string.label_name) }} / ${if (ascending) stringResource(R.string.label_ascending) else stringResource(R.string.label_descending)}",
                                                    color = if (isSelected) colors.accent else colors.primaryText
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
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.nav_settings),
                                    tint = colors.primaryText
                                )
                            }
                        }
                    },
                    showCategoryTopBar = false,
                    showSelectedCategoryTopBar = false,
                    gridExtraBottomPadding = 0.dp,
                    openInternalViewer = false,
                    onImageClickOverride = { index, list ->
                        val clickedUri = list.getOrNull(index)?.uri
                        activeVideoList = list.filter { it.isVideo }.ifEmpty { folderVideos }
                        activeVideoIndex = clickedUri
                            ?.let { uri -> activeVideoList.indexOfFirst { it.uri == uri } }
                            ?.takeIf { it >= 0 }
                            ?: index.coerceIn(0, (activeVideoList.size - 1).coerceAtLeast(0))
                    }
                )
            }
        }
    }
}

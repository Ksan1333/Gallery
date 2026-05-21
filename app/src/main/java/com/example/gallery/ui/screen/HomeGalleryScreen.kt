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
    val imageList = remember { mutableStateListOf<MediaData>() }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val flatListForViewerState = remember { mutableStateOf(emptyList<MediaData>()) }
    var flatListForViewer by flatListForViewerState
    val scope = rememberCoroutineScope()

    LaunchedEffect(imageList.size) {
        if (selectedIndex != null && flatListForViewer.isEmpty() && imageList.isNotEmpty()) {
            flatListForViewer = imageList.toList()
        }
    }

    LaunchedEffect(selectedIndex, flatListForViewer) {
        selectedIndex?.let { idx -> flatListForViewer.getOrNull(idx)?.uri?.let { uri -> galleryState.lastViewedUri = uri } }
    }

    LaunchedEffect(imageList.size, initialMediaUri) {
        if (initialMediaUri != null && imageList.isNotEmpty()) {
            val idx = imageList.indexOfFirst { it.uri == initialMediaUri }
            if (idx != -1) {
                flatListForViewer = imageList.toList()
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
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            galleryState.repository.getAllMetadataSummary()
            val allMedia = galleryState.repository.getAllMedia()
            withContext(Dispatchers.Main) {
                imageList.clear()
                imageList.addAll(allMedia)
                delay(100)
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) localLoadImages()
    }

    LaunchedEffect(galleryState.isMockMode, galleryState.refreshTrigger) { localLoadImages() }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val allGranted = permissions.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (allGranted) localLoadImages() else permissionLauncher.launch(permissions)
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
                title = if (galleryState.isMockMode) "すべて (MOCK)" else "すべて",
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

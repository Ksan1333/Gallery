package com.example.gallery.ui.screen

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import com.example.gallery.ui.component.CommonFloatingCloseButton
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.PictureViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.gallery.ui.AppConstants

import com.example.gallery.ui.MediaData
import android.util.Log
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.gallery.ui.GalleryState

private const val TAG = "HomeGalleryScreen"

@Composable
fun HomeGalleryScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    initialMediaUri: String? = null // 追加
) {
    val context = LocalContext.current
    val imageList = remember { mutableStateListOf<MediaData>() }
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val flatListForViewerState = rememberSaveable(saver = MediaData.ListSaver) { 
        mutableStateOf(emptyList<MediaData>()) 
    }
    var flatListForViewer by flatListForViewerState
    val scope = rememberCoroutineScope()
    
    // 回転後の復元処理: imageListが読み込まれたらflatListForViewerを同期する
    LaunchedEffect(imageList.size) {
        if (selectedIndex != null && flatListForViewer.isEmpty() && imageList.isNotEmpty()) {
            Log.d(TAG, "Restoring flatListForViewer after rotation")
            flatListForViewer = imageList.toList()
        }
    }
    
    // ツールバーとビュワーの状態を同期
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            onShowViewer()
        } else {
            onHideViewer()
        }
    }
    
    // 初期URI指定がある場合、自動でビュワーを開く
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

    // 選択解除用のシグナル
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionModeActive || selectedIndex != null) {
        if (selectedIndex != null) {
            selectedIndex = null
            onHideViewer()
        } else if (isSelectionModeActive) {
            clearSelectionSignal++
        }
    }

    fun localLoadImages() {
        Log.d(TAG, "localLoadImages: Start")
        scope.launch(Dispatchers.IO) {
            val allMedia = galleryState.repository.getAllMedia()
            withContext(Dispatchers.Main) {
                imageList.clear()
                imageList.addAll(allMedia)
            }
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.any { it }) { 
                localLoadImages()
            }
        }

    LaunchedEffect(galleryState.isMockMode) {
        localLoadImages()
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val allGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            localLoadImages()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryGridView(
                imageList = imageList,
                onImageClick = { index, list ->
                    flatListForViewer = list
                    selectedIndex = index
                    onShowViewer()
                },
                galleryState = galleryState,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = if (galleryState.isMockMode) "すべて (MOCK)" else "すべて"
            )
        }

        selectedIndex?.let { initialPage ->
            if (flatListForViewer.isNotEmpty()) {
                PictureViewer(
                    onClickedClose = {
                        selectedIndex = null
                        onHideViewer()
                    },
                    initialPage = initialPage,
                    imageList = flatListForViewer,
                    galleryState = galleryState,
                    onPageSelected = { selectedIndex = it },
                    onNavigateToMedia = { uri ->
                        val idx = imageList.indexOfFirst { it.uri == uri }
                        if (idx != -1) {
                            flatListForViewer = imageList.toList()
                            selectedIndex = idx
                        } else {
                            // リスト外の場合、フィルタを解除して全件から探す
                            galleryState.ageRatingFilter = com.example.gallery.ui.component.AgeRatingFilter.ALL
                            galleryState.deviceFilter = com.example.gallery.ui.component.DeviceFilter.ALL
                            galleryState.mediaTypeFilter = com.example.gallery.ui.component.MediaTypeFilter.ALL
                            
                            scope.launch {
                                delay(200) // フィルタ解除によるリスト更新を待つ
                                val allMedia = galleryState.repository.getAllMedia()
                                val newIdx = allMedia.indexOfFirst { it.uri == uri }
                                if (newIdx != -1) {
                                    flatListForViewer = allMedia
                                    selectedIndex = newIdx
                                } else {
                                    // それでも見つからない（ありえないはずだが）場合は単一表示
                                    scope.launch(Dispatchers.IO) {
                                        val meta = galleryState.repository.getMetadata(uri)
                                        // 最小限のMediaDataを生成して表示
                                        withContext(Dispatchers.Main) {
                                            flatListForViewer = listOf(MediaData(uri, System.currentTimeMillis()))
                                            selectedIndex = 0
                                        }
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

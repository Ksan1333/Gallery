package com.example.gallery.ui.screen

import android.Manifest
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.GalleryViewMode
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import java.io.File
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Composable
fun FolderGalleryScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    onBackToFolders: () -> Unit = {},
    onStartAnalysis: () -> Unit = {},
    isSelectionMode: Boolean = false, // 追加: フォルダ選択モード
    onFolderSelected: (String) -> Unit = {}, // 追加: フォルダ選択時のコールバック
    onBulkEdit: ((List<String>) -> Unit)? = null // 追加
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // フォルダ表示用
    val folderData = remember { mutableStateMapOf<String, MutableList<MediaData>>() }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // カテゴリ詳細が表示されているかどうかの状態
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }
    
    // 最後に表示していたメディアURIを保持（戻り時のスクロール用）
    var lastViewedUri by rememberSaveable { mutableStateOf<String?>(null) }

    // メタデータ（年齢制限など）を監視 - モード切り替え時に初期値に戻るのを防ぐため、isMockModeをキーにremember
    val metadataFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllMetadataFlow() }
    val allMetadata by metadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }

    fun loadAllMedia() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            
            // メタデータが空の場合、Flowが最初の値を出すのを待つ（特にMOCK切り替え時）
            // repository.getAllMetadata() を使って確実に取得
            galleryState.repository.getAllMetadata()
            
            val newFolderData = mutableMapOf<String, MutableList<MediaData>>()
            val allMedia = galleryState.repository.getAllMedia()
            
            if (galleryState.isMockMode) {
                allMedia.forEach { item ->
                    val folderName = galleryState.repository.getMockFolder(item.uri) ?: "Unknown"
                    val list = newFolderData.getOrPut(folderName) { mutableListOf() }
                    list.add(item)
                }
            } else {
                // MediaData に保持されている folderName を使用してグループ化
                allMedia.forEach { item ->
                    val list = newFolderData.getOrPut(item.folderName) { mutableListOf() }
                    list.add(item)
                }
            }
            newFolderData.keys.forEach { key ->
                newFolderData[key]?.sortByDescending { it.dateAdded }
            }
            
            withContext(Dispatchers.Main) {
                folderData.clear()
                folderData.putAll(newFolderData)
                // データの準備ができたら、さらに一瞬待ってメタデータのState更新（Flowからのemit）が反映される猶予を持たせる
                delay(100)
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            loadAllMedia()
        }
    }

    LaunchedEffect(galleryState.isMockMode) {
        loadAllMedia()
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            loadAllMedia()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = galleryState.galleryViewMode.ordinal,
        pageCount = { GalleryViewMode.entries.size }
    )

    // Pagerの状態をGalleryStateに同期
    LaunchedEffect(pagerState.currentPage) {
        val newMode = GalleryViewMode.entries[pagerState.currentPage]
        galleryState.galleryViewMode = newMode
        
        // 未分析アイテムの件数をトーストで表示
        if (newMode == GalleryViewMode.MYLIST || newMode == GalleryViewMode.COLOR) {
            val count = if (newMode == GalleryViewMode.MYLIST) {
                galleryState.repository.getUnanalyzedAiCount(galleryState.ageRatingFilter).first()
            } else {
                galleryState.repository.getUnanalyzedColorCount(galleryState.ageRatingFilter).first()
            }
            if (count > 0) {
                Toast.makeText(context, "${count}件が未分析です。右上のアイコンから解析してください", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // GalleryStateの変更をPagerに反映（外部からの変更対応）
    LaunchedEffect(galleryState.galleryViewMode) {
        if (pagerState.currentPage != galleryState.galleryViewMode.ordinal) {
            pagerState.animateScrollToPage(galleryState.galleryViewMode.ordinal)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !isSubCategorySelected // 詳細表示中はスワイプ無効
    ) { page ->
        when (GalleryViewMode.entries[page]) {
            GalleryViewMode.FOLDER -> {
                val categories = folderData.map { (name, images) ->
                    // 現在の年齢制限フィルタに合う画像のみ抽出
                    val filteredImages = images.filter { item ->
                        val meta = metadataMap[item.uri]
                        val rating = meta?.ageRating ?: "SFW"
                        when (galleryState.ageRatingFilter) {
                            AgeRatingFilter.ALL -> true
                            AgeRatingFilter.SFW -> rating == "SFW"
                            AgeRatingFilter.R15 -> rating == "R15"
                            AgeRatingFilter.R18 -> rating == "R18"
                        }
                    }
                    
                    CategoryData(
                        id = name,
                        title = name,
                        count = filteredImages.size,
                        thumbnail = filteredImages.firstOrNull()?.uri
                    )
                }.filter { it.count > 0 }.sortedBy { it.title }

                CategoryScreen(
                    title = if (isSelectionMode) "移動先フォルダを選択" else "フォルダ",
                    categories = categories,
                    isLoading = isLoading,
                    galleryState = galleryState,
                    onCategoryClick = { 
                        if (isSelectionMode) {
                            onFolderSelected(it.id)
                        } else {
                            selectedFolderName = it.id 
                            isSubCategorySelected = true
                        }
                    },
                    onShowViewer = onShowViewer,
                    onHideViewer = onHideViewer,
                    selectedCategoryTitle = selectedFolderName,
                    selectedCategoryMedia = folderData[selectedFolderName]?.filter { item ->
                        val meta = metadataMap[item.uri]
                        val rating = meta?.ageRating ?: "SFW"
                        when (galleryState.ageRatingFilter) {
                            AgeRatingFilter.ALL -> true
                            AgeRatingFilter.SFW -> rating == "SFW"
                            AgeRatingFilter.R15 -> rating == "R15"
                            AgeRatingFilter.R18 -> rating == "R18"
                        }
                    } ?: emptyList(),
                    onBackFromCategory = { 
                        selectedFolderName = null
                        isSubCategorySelected = false
                    },
                    onTabIconClick = { onBackToFolders() },
                    lastViewedUri = lastViewedUri,
                    onPageChangedInViewer = { lastViewedUri = it },
                    onBulkEdit = onBulkEdit
                )
            }
            GalleryViewMode.MYLIST -> {
                MyListScreen(
                    onShowViewer = onShowViewer,
                    onHideViewer = onHideViewer,
                    onStartAnalysis = onStartAnalysis,
                    galleryState = galleryState,
                    onBackToMyList = onBackToFolders,
                    onSubCategorySelected = { isSubCategorySelected = it }
                )
            }
            GalleryViewMode.COLOR -> {
                ColorListScreen(
                    onShowViewer = onShowViewer,
                    onHideViewer = onHideViewer,
                    onStartAnalysis = onStartAnalysis,
                    galleryState = galleryState,
                    onBackToColorList = onBackToFolders,
                    onSubCategorySelected = { isSubCategorySelected = it }
                )
            }
        }
    }
}

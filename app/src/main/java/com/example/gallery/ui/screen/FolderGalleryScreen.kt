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
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import java.io.File
import android.widget.Toast
import kotlinx.coroutines.flow.first

@Composable
fun FolderGalleryScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    onBackToFolders: () -> Unit = {},
    onStartAnalysis: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // フォルダ表示用
    val folderData = remember { mutableStateMapOf<String, MutableList<MediaData>>() }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    
    // カテゴリ詳細が表示されているかどうかの状態
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }

    fun loadMediaFromUri(collection: android.net.Uri) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DURATION
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val path = cursor.getString(dataColumn)
                val date = cursor.getLong(dateColumn) * 1000
                val mime = cursor.getString(mimeColumn)
                val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                val folderName = File(path).parentFile?.name ?: "Unknown"
                val contentUri = ContentUris.withAppendedId(collection, id).toString()

                val list = folderData.getOrPut(folderName) { mutableListOf() }
                list.add(MediaData(contentUri, date, mime, duration))
            }
        }
    }

    fun loadAllMedia() {
        folderData.clear()
        loadMediaFromUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        loadMediaFromUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        folderData.keys.forEach { key ->
            folderData[key]?.sortByDescending { it.dateAdded }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            loadAllMedia()
        }
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
                galleryState.repository.getUnanalyzedAiCount().first()
            } else {
                galleryState.repository.getUnanalyzedColorCount().first()
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
                    CategoryData(
                        id = name,
                        title = name,
                        count = images.size,
                        thumbnail = images.firstOrNull()?.uri
                    )
                }.sortedBy { it.title }

                CategoryScreen(
                    title = "フォルダ",
                    categories = categories,
                    galleryState = galleryState,
                    onCategoryClick = { 
                        selectedFolderName = it.id 
                        isSubCategorySelected = true
                    },
                    onShowViewer = onShowViewer,
                    onHideViewer = onHideViewer,
                    selectedCategoryTitle = selectedFolderName,
                    selectedCategoryMedia = folderData[selectedFolderName] ?: emptyList(),
                    onBackFromCategory = { 
                        selectedFolderName = null
                        isSubCategorySelected = false
                    },
                    onTabIconClick = { onBackToFolders() }
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

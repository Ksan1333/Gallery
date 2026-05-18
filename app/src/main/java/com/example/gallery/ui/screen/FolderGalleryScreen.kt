package com.example.gallery.ui.screen

import android.Manifest
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.GalleryViewMode
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import com.example.gallery.ui.component.TooltipWrapper
import java.io.File

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
    
    // マイリスト・カラー表示用（既存のスクリーンからロジックを統合するか、コンポーネントを切り替える）
    // シンプルにするため、モードによって表示するコンテンツを切り替える
    
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

    when (galleryState.galleryViewMode) {
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
                onCategoryClick = { selectedFolderName = it.id },
                onShowViewer = onShowViewer,
                onHideViewer = onHideViewer,
                selectedCategoryTitle = selectedFolderName,
                selectedCategoryMedia = folderData[selectedFolderName] ?: emptyList(),
                onBackFromCategory = { selectedFolderName = null },
                onTabIconClick = { onBackToFolders() },
                topBarActions = {
                    ModeSwitcher(galleryState)
                }
            )
        }
        GalleryViewMode.MYLIST -> {
            MyListScreen(
                onShowViewer = onShowViewer,
                onHideViewer = onHideViewer,
                onStartAnalysis = onStartAnalysis,
                galleryState = galleryState,
                onBackToMyList = onBackToFolders,
                topBarActions = {
                    ModeSwitcher(galleryState)
                }
            )
        }
        GalleryViewMode.COLOR -> {
            ColorListScreen(
                onShowViewer = onShowViewer,
                onHideViewer = onHideViewer,
                onStartAnalysis = onStartAnalysis,
                galleryState = galleryState,
                onBackToColorList = onBackToFolders,
                topBarActions = {
                    ModeSwitcher(galleryState)
                }
            )
        }
    }
}

@Composable
fun ModeSwitcher(galleryState: GalleryState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TooltipWrapper("フォルダ表示") {
            IconButton(onClick = { galleryState.galleryViewMode = GalleryViewMode.FOLDER }) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = if (galleryState.galleryViewMode == GalleryViewMode.FOLDER) Color.Cyan else Color.White
                )
            }
        }
        TooltipWrapper("マイリスト表示") {
            IconButton(onClick = { galleryState.galleryViewMode = GalleryViewMode.MYLIST }) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (galleryState.galleryViewMode == GalleryViewMode.MYLIST) Color.Cyan else Color.White
                )
            }
        }
        TooltipWrapper("カラー表示") {
            IconButton(onClick = { galleryState.galleryViewMode = GalleryViewMode.COLOR }) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = if (galleryState.galleryViewMode == GalleryViewMode.COLOR) Color.Cyan else Color.White
                )
            }
        }
    }
}

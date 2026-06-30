package com.example.gallery.ui.screen

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.state.*
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun FolderGalleryScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    galleryState: GalleryState,
    onMenuClick: (() -> Unit)? = null,
    onBackToFolders: () -> Unit = {},
    onStartAnalysis: () -> Unit = {},
    isSelectionMode: Boolean = false,
    onFolderSelected: (String) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onBulkMove: ((List<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderData = remember { mutableStateMapOf<String, MutableList<MediaData>>() }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val managedFolders by galleryState.repository.getAllManagedFolders().collectAsState(initial = emptyList())
    val managedFolderNames = remember(managedFolders) { managedFolders.map { it.folderName } }
    val folderThumbnails = remember(managedFolders) { managedFolders.associate { it.folderName to it.customThumbnailUri } }
    val folderOrders by galleryState.repository.getAllFolderOrders().collectAsState(initial = emptyList())
    val orderMap = remember(folderOrders) { folderOrders.associate { it.id to it.position } }

    val metadataMap = remember { mutableStateMapOf<String, com.example.gallery.data.local.entity.MediaMetadataSummary>() }
    LaunchedEffect(galleryState.repository) {
        galleryState.repository.getAllMetadataSummaryFlow()
            .debounce(1000)
            .distinctUntilChanged()
            .collect { list ->
                list.forEach { metadataMap[it.uri] = it }
                if (metadataMap.size > list.size + 100) {
                    val uris = list.map { it.uri }.toSet()
                    metadataMap.keys.retainAll(uris)
                }
            }
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }

    var showFolderMenu by remember { mutableStateOf<CategoryData?>(null) }

    fun loadAllMedia() {
        if (isLoading) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            val newMap = mutableMapOf<String, MutableList<MediaData>>()
            val allMedia = galleryState.repository.getAllMedia(forceRefresh = false)

            // ディレクトリ走査は重いので、allMedia の folderName だけで構成する。
            allMedia.forEach { newMap.getOrPut(it.folderName) { mutableListOf() }.add(it) }

            // 明示的に管理されている空フォルダも追加する。
            managedFolderNames.forEach { if (!newMap.containsKey(it)) newMap[it] = mutableListOf() }
            newMap.values.forEach { it.sortByDescending { m -> m.dateAdded } }
            withContext(Dispatchers.Main) {
                folderData.clear(); folderData.putAll(newMap)
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p -> if (p.values.all { it }) loadAllMedia() }

    LaunchedEffect(galleryState.refreshTrigger) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (perms.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            loadAllMedia()
        } else {
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(galleryState.galleryViewMode) {
        // Pager logic removed as it's now independent
    }

    BackHandler(isSubCategorySelected) {
        if (selectedFolderName != null) {
            selectedFolderName = null
            isSubCategorySelected = false
        }
    }

    val categories by remember(folderData, metadataMap, galleryState.ageRatingFilter, folderThumbnails, folderOrders) {
        derivedStateOf {
            folderData.map { (name, images) ->
                val filtered = images.filter { m ->
                    val rating = metadataMap[m.uri]?.ageRating ?: "SFW"
                    galleryState.ageRatingFilter == AgeRatingFilter.ALL || (galleryState.ageRatingFilter == AgeRatingFilter.SFW && rating == "SFW") || (galleryState.ageRatingFilter == AgeRatingFilter.R15 && rating == "R15") || (galleryState.ageRatingFilter == AgeRatingFilter.R18 && rating == "R18")
                }
                CategoryData(
                    id = name,
                    title = name,
                    count = filtered.size,
                    thumbnail = folderThumbnails[name] ?: filtered.firstOrNull()?.uri ?: images.firstOrNull()?.uri,
                    isPhysical = managedFolderNames.contains(name)
                )
            }.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.title }))
            .filter { it.isPhysical || (folderData[it.id]?.size ?: 0) > 0 || folderThumbnails[it.id] != null }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CategoryScreen(
            title = if (isSelectionMode) "移動先フォルダを選択" else "フォルダ",
            categories = categories, isLoading = isLoading, galleryState = galleryState,
            onNavigateToTag = { tag ->
                onBackToFolders() // まずフォルダリストへ戻る。
                onFolderSelected("TAG_NAVIGATION:$tag") // 特別なプレフィックスで MainActivity へ通知する。
            },
            onMenuClick = if (!isSelectionMode) onMenuClick else null,
            topBarActions = {
                var showSortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "並び替え",
                        tint = Color.White
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
                                        Text(
                                            text = "${when (mode) { SortMode.DATE_ADDED -> "追加日"; SortMode.SIZE -> "サイズ"; SortMode.NAME -> "名前" }}・${if (ascending) "昇順" else "降順"}",
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
                if (selectedFolderName == null && !isSelectionMode) {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                Icon(androidx.compose.material.icons.Icons.Default.Add, "フォルダ作成", tint = Color.White)
                    }
                }
            },
            onCategoryClick = { if (isSelectionMode) onFolderSelected(it.id) else { selectedFolderName = it.id; isSubCategorySelected = true } },
            onCategoryLongClick = { showFolderMenu = it },
            onReorder = { ids -> scope.launch { galleryState.repository.updateFolderOrders(ids.mapIndexed { i, id -> com.example.gallery.data.local.entity.FolderOrderEntity(id, i) }) } },
            onShowViewer = onShowViewer, onHideViewer = onHideViewer,
            selectedCategoryTitle = selectedFolderName,
            selectedCategoryMedia = folderData[selectedFolderName]?.filter { m ->
                val rating = metadataMap[m.uri]?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) {
                    AgeRatingFilter.ALL -> true
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                }
            } ?: emptyList(),
            onBackFromCategory = { if (selectedFolderName != null) { selectedFolderName = null; isSubCategorySelected = false } else onBackToFolders() },
            onTabIconClick = { selectedFolderName = null; isSubCategorySelected = false; onBackToFolders() },
            lastViewedUri = galleryState.lastViewedUri,
            onPageChangedInViewer = { galleryState.lastViewedUri = it },
            onBulkEdit = onBulkEdit,
            onBulkMove = onBulkMove,
            onScrollConsumed = { galleryState.lastViewedUri = null }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("フォルダ作成") },
            text = {
                Column {
                    Text("DCIM直下に作成されます", fontSize = com.example.gallery.ui.AppConstants.SmallFontSize, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("フォルダ名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        scope.launch {
                            if (galleryState.repository.createFolderUnderDCIM(newFolderName.trim())) {
                                galleryState.repository.addManagedFolder(newFolderName.trim())
                                loadAllMedia()
                            }
                            showCreateFolderDialog = false
                            newFolderName = ""
                        }
                    }
                }) { Text("作成") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showFolderMenu != null) {
        val folder = showFolderMenu!!
        AlertDialog(
            onDismissRequest = { showFolderMenu = null },
            title = { Text(folder.title) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("サムネイルをリセット") },
                        leadingContent = { Icon(Icons.Default.Refresh, null) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                galleryState.repository.updateFolderThumbnail(folder.id, null)
                                showFolderMenu = null
                                Toast.makeText(context, "サムネイルをリセットしました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFolderMenu = null }) { Text("閉じる") } }
        )
    }
}

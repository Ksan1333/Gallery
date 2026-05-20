package com.example.gallery.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.GalleryViewMode
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

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
    onBulkEdit: ((List<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folderData = remember { mutableStateMapOf<String, MutableList<MediaData>>() }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val folderGroups by galleryState.repository.getAllFolderGroups().collectAsState(initial = emptyList())
    val groupMembers by galleryState.repository.getAllFolderGroupMembers().collectAsState(initial = emptyList())
    val managedFolderNames by galleryState.repository.getAllManagedFolderNames().collectAsState(initial = emptyList())
    val folderOrders by galleryState.repository.getAllFolderOrders().collectAsState(initial = emptyList())
    val orderMap = remember(folderOrders) { folderOrders.associate { it.id to it.position } }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showDeleteGroupConfirm by remember { mutableStateOf<String?>(null) }
    var selectedFoldersForGroup by remember { mutableStateOf<List<String>?>(null) }
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastViewedUri by rememberSaveable { mutableStateOf<String?>(null) }

    val metadataFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllMetadataFlow() }
    val allMetadata by metadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }

    fun loadAllMedia() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            galleryState.repository.getAllMetadata()
            val newMap = mutableMapOf<String, MutableList<MediaData>>()
            val allMedia = galleryState.repository.getAllMedia()

            if (galleryState.isMockMode) {
                allMedia.forEach { item ->
                    val folder = galleryState.repository.getMockFolder(item.uri) ?: "Unknown"
                    newMap.getOrPut(folder) { mutableListOf() }.add(item)
                }
            } else {
                galleryState.repository.scanAllFolders().forEach { newMap[it] = mutableListOf() }
                allMedia.forEach { newMap.getOrPut(it.folderName) { mutableListOf() }.add(it) }
            }
            newMap.values.forEach { it.sortByDescending { m -> m.dateAdded } }
            withContext(Dispatchers.Main) {
                folderData.clear(); folderData.putAll(newMap)
                delay(100); isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p -> if (p.values.all { it }) loadAllMedia() }

    LaunchedEffect(galleryState.isMockMode, galleryState.refreshTrigger) { loadAllMedia() }

    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (perms.all { androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) loadAllMedia()
        else permissionLauncher.launch(perms)
    }

    LaunchedEffect(galleryState.galleryViewMode) {
        // Pager logic removed as it's now independent
    }

    BackHandler(isSubCategorySelected || selectedGroupId != null) {
        if (selectedFolderName != null) {
            selectedFolderName = null
            isSubCategorySelected = false
        } else if (selectedGroupId != null) {
            selectedGroupId = null
        }
    }

    val memberMap = groupMembers.groupBy { it.groupName }.mapValues { e -> e.value.map { it.folderName } }
    val foldersInGroups = groupMembers.map { it.folderName }.toSet()

    val categories = if (selectedGroupId != null) {
        (memberMap[selectedGroupId] ?: emptyList()).map { name ->
            if (name.startsWith("group:")) {
                val gName = name.removePrefix("group:")
                val sub = memberMap[gName] ?: emptyList()
                CategoryData(name, gName, sub.size, null, sub.take(4).mapNotNull { folderData[it]?.firstOrNull()?.uri }, isGroup = true)
            } else {
                val images = folderData[name] ?: emptyList()
                val filtered = images.filter { m ->
                    val rating = metadataMap[m.uri]?.ageRating ?: "SFW"
                    galleryState.ageRatingFilter == AgeRatingFilter.ALL || (galleryState.ageRatingFilter == AgeRatingFilter.SFW && rating == "SFW") || (galleryState.ageRatingFilter == AgeRatingFilter.R15 && rating == "R15") || (galleryState.ageRatingFilter == AgeRatingFilter.R18 && rating == "R18")
                }
                CategoryData(name, name, filtered.size, filtered.firstOrNull()?.uri, isPhysical = managedFolderNames.contains(name))
            }
        }.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.title }))
    } else {
        val topFolders = folderData.filter { !foldersInGroups.contains(it.key) }.map { (name, images) ->
            val filtered = images.filter { m ->
                val rating = metadataMap[m.uri]?.ageRating ?: "SFW"
                galleryState.ageRatingFilter == AgeRatingFilter.ALL || (galleryState.ageRatingFilter == AgeRatingFilter.SFW && rating == "SFW") || (galleryState.ageRatingFilter == AgeRatingFilter.R15 && rating == "R15") || (galleryState.ageRatingFilter == AgeRatingFilter.R18 && rating == "R18")
            }
            CategoryData(name, name, filtered.size, filtered.firstOrNull()?.uri, isPhysical = managedFolderNames.contains(name))
        }
        val groups = folderGroups.filter { !foldersInGroups.contains("group:${it.name}") }.map { g ->
            val sub = memberMap[g.name] ?: emptyList()
            CategoryData("group:${g.name}", g.name, sub.size, null, sub.take(4).mapNotNull { folderData[it]?.firstOrNull()?.uri }, isGroup = true)
        }
        (topFolders + groups).sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.title }))
    }.filter { it.count > 0 || it.isGroup || it.isPhysical }

    Box(modifier = Modifier.fillMaxSize()) {
        CategoryScreen(
            title = if (selectedGroupId != null) selectedGroupId!! else if (isSelectionMode) "移動先フォルダを選択" else "フォルダ",
            categories = categories, isLoading = isLoading, galleryState = galleryState,
            onMenuClick = if (selectedGroupId == null && !isSelectionMode) onMenuClick else null,
            topBarActions = {
                var showTopMenu by remember { mutableStateOf(false) }
                IconButton(onClick = onStartAnalysis) { Icon(Icons.Default.AutoAwesome, "自動解析", tint = Color.White) }
                Box {
                    IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, "メニュー", tint = Color.White) }
                    DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                        if (selectedGroupId != null && !isSelectionMode) DropdownMenuItem(text = { Text("グループ削除", color = Color.White) }, onClick = { showTopMenu = false; showDeleteGroupConfirm = selectedGroupId })
                        if (selectedGroupId == null && !isSelectionMode) {
                            DropdownMenuItem(text = { Text("フォルダ作成", color = Color.White) }, onClick = { showTopMenu = false; showCreateFolderDialog = true })
                            DropdownMenuItem(text = { Text("グループ作成", color = Color.White) }, onClick = { showTopMenu = false; showCreateGroupDialog = true })
                        }
                    }
                }
            },
            onCategoryClick = { if (it.isGroup) selectedGroupId = it.title else { if (isSelectionMode) onFolderSelected(it.id) else { selectedFolderName = it.id; isSubCategorySelected = true } } },
            onMoveSelectedToGroup = { selectedFoldersForGroup = it },
            onDeleteGroups = { if (it.isNotEmpty()) showDeleteGroupConfirm = it.first() },
            onDrop = { drag, target ->
                if (target.startsWith("group:")) { scope.launch { galleryState.repository.addFolderToGroup(drag, target.removePrefix("group:")) } }
                else if (target == "ROOT" && selectedGroupId != null) scope.launch { galleryState.repository.removeFolderFromGroup(drag) }
            },
            onReorder = { ids -> scope.launch { galleryState.repository.updateFolderOrders(ids.mapIndexed { i, id -> com.example.gallery.data.local.entity.FolderOrderEntity(id, i) }) } },
            onShowViewer = onShowViewer, onHideViewer = onHideViewer,
            selectedCategoryTitle = selectedFolderName,
            selectedCategoryMedia = folderData[selectedFolderName]?.filter { m ->
                val rating = metadataMap[m.uri]?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) { AgeRatingFilter.ALL -> true; AgeRatingFilter.SFW -> rating == "SFW"; AgeRatingFilter.R15 -> rating == "R15"; AgeRatingFilter.R18 -> rating == "R18" }
            } ?: emptyList(),
            onBackFromCategory = { if (selectedFolderName != null) { selectedFolderName = null; isSubCategorySelected = false } else if (selectedGroupId != null) selectedGroupId = null else onBackToFolders() },
            onTabIconClick = { selectedFolderName = null; isSubCategorySelected = false; selectedGroupId = null; onBackToFolders() },
            lastViewedUri = lastViewedUri, onPageChangedInViewer = { lastViewedUri = it }, onBulkEdit = onBulkEdit
        )
    }

    if (showCreateGroupDialog) {
        AlertDialog(onDismissRequest = { showCreateGroupDialog = false }, title = { Text("グループ作成") }, text = { OutlinedTextField(value = newGroupName, onValueChange = { newGroupName = it }, label = { Text("グループ名") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { if (newGroupName.isNotBlank()) { scope.launch { galleryState.repository.createFolderGroup(newGroupName.trim()); showCreateGroupDialog = false; newGroupName = "" } } }) { Text("作成") } }, dismissButton = { TextButton(onClick = { showCreateGroupDialog = false }) { Text("キャンセル") } })
    }
    if (showCreateFolderDialog) {
        AlertDialog(onDismissRequest = { showCreateFolderDialog = false }, title = { Text("フォルダ作成") }, text = { Column { Text("DCIM直下に作成されます", fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("フォルダ名") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { Button(onClick = { if (newFolderName.isNotBlank()) { scope.launch { if (galleryState.repository.createFolderUnderDCIM(newFolderName.trim())) { galleryState.repository.addManagedFolder(newFolderName.trim()); loadAllMedia() }; showCreateFolderDialog = false; newFolderName = "" } } }) { Text("作成") } }, dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("キャンセル") } })
    }
    if (showDeleteGroupConfirm != null) {
        val name = showDeleteGroupConfirm!!
        AlertDialog(onDismissRequest = { showDeleteGroupConfirm = null }, title = { Text("グループ削除") }, text = { Text("グループ「$name」を削除しますか？\n中のフォルダは削除されず、外に出されます。") },
            confirmButton = { Button(onClick = { scope.launch { galleryState.repository.deleteFolderGroup(name); if (selectedGroupId == name) selectedGroupId = null; showDeleteGroupConfirm = null } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("削除") } }, dismissButton = { TextButton(onClick = { showDeleteGroupConfirm = null }) { Text("キャンセル") } })
    }
    if (selectedFoldersForGroup != null) {
        val ids = selectedFoldersForGroup!!
        AlertDialog(onDismissRequest = { selectedFoldersForGroup = null }, title = { Text(if (ids.size == 1) "${ids.first()} を移動" else "${ids.size} 件を移動") },
            text = { Column { Button(onClick = { scope.launch { ids.forEach { galleryState.repository.removeFolderFromGroup(it) }; selectedFoldersForGroup = null } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("グループ解除") }; Spacer(Modifier.height(16.dp)); Text("移動先を選択:"); LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { items(folderGroups) { g -> ListItem(headlineContent = { Text(g.name) }, modifier = Modifier.clickable { scope.launch { ids.forEach { galleryState.repository.addFolderToGroup(it, g.name) }; selectedFoldersForGroup = null } }) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { selectedFoldersForGroup = null }) { Text("キャンセル") } })
    }
}

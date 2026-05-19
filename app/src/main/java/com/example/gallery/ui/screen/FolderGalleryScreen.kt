package com.example.gallery.ui.screen

import android.Manifest
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderCopy
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

    // グループ関連
    val folderGroups by galleryState.repository.getAllFolderGroups().collectAsState(initial = emptyList())
    val groupMembers by galleryState.repository.getAllFolderGroupMembers().collectAsState(initial = emptyList())
    val managedFolderNames by galleryState.repository.getAllManagedFolderNames().collectAsState(initial = emptyList())
    val folderOrders by galleryState.repository.getAllFolderOrders().collectAsState(initial = emptyList())
    val orderMap = remember(folderOrders) { folderOrders.associate { it.id to it.position } }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showFolderToGroupDialog by remember { mutableStateOf<String?>(null) } // 追加
    var showDeleteGroupConfirm by remember { mutableStateOf<String?>(null) } // 追加
    var selectedFoldersForGroup by remember { mutableStateOf<List<String>?>(null) } // 追加

    // カテゴリ詳細が表示されているかどうかの状態
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) } // 追加: 現在開いているグループ
    
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
                // 1. 物理的なフォルダをすべてリストアップ (空フォルダ対応)
                val allPhysicalFolders = galleryState.repository.scanAllFolders()
                allPhysicalFolders.forEach { newFolderData[it] = mutableListOf() }

                // 2. MediaData に保持されている folderName を使用してグループ化
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

    LaunchedEffect(galleryState.isMockMode, galleryState.refreshTrigger) {
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

    BackHandler(isSubCategorySelected || selectedGroupId != null) {
        if (selectedFolderName != null) {
            selectedFolderName = null
            isSubCategorySelected = false
        } else if (selectedGroupId != null) {
            selectedGroupId = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isSubCategorySelected // 詳細表示中はスワイプ無効
        ) { page ->
            when (GalleryViewMode.entries[page]) {
                GalleryViewMode.FOLDER -> {
                    val memberMap = groupMembers.groupBy { it.groupName }.mapValues { entry -> entry.value.map { it.folderName } }
                    val foldersInGroups = groupMembers.map { it.folderName }.toSet()

                    // グループ表示 or 特定グループの中身表示 or 全フォルダ表示
                    val rawCategories = if (selectedGroupId != null) {
                        // グループの中身を表示
                        val memberNames = memberMap[selectedGroupId] ?: emptyList()
                        memberNames.map { name ->
                            if (name.startsWith("group:")) {
                                val gName = name.removePrefix("group:")
                                val subMembers = memberMap[gName] ?: emptyList()
                                val thumbnails = subMembers.take(4).mapNotNull { fn ->
                                    folderData[fn]?.firstOrNull()?.uri
                                }
                                CategoryData(
                                    id = name,
                                    title = gName,
                                    count = subMembers.size,
                                    thumbnail = null,
                                    groupThumbnails = thumbnails,
                                    isGroup = true
                                )
                            } else {
                                val images = folderData[name] ?: emptyList()
                                val filtered = images.filter { item ->
                                    val meta = metadataMap[item.uri]
                                    val rating = meta?.ageRating ?: "SFW"
                                    galleryState.ageRatingFilter == AgeRatingFilter.ALL || 
                                    (galleryState.ageRatingFilter == AgeRatingFilter.SFW && rating == "SFW") ||
                                    (galleryState.ageRatingFilter == AgeRatingFilter.R15 && rating == "R15") ||
                                    (galleryState.ageRatingFilter == AgeRatingFilter.R18 && rating == "R18")
                                }
                                CategoryData(
                                    id = name,
                                    title = name,
                                    count = filtered.size,
                                    thumbnail = filtered.firstOrNull()?.uri,
                                    isPhysical = managedFolderNames.contains(name)
                                )
                            }
                        }.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.title }))
                    } else {
                        // トップレベル表示: グループ未所属フォルダ + グループ
                        val topLevelFolders = folderData.filter { (name, _) -> !foldersInGroups.contains(name) }.map { (name, images) ->
                            val filtered = images.filter { item ->
                                val meta = metadataMap[item.uri]
                                val rating = meta?.ageRating ?: "SFW"
                                galleryState.ageRatingFilter == AgeRatingFilter.ALL || 
                                (galleryState.ageRatingFilter == AgeRatingFilter.SFW && rating == "SFW") ||
                                (galleryState.ageRatingFilter == AgeRatingFilter.R15 && rating == "R15") ||
                                (galleryState.ageRatingFilter == AgeRatingFilter.R18 && rating == "R18")
                            }
                            CategoryData(
                                id = name,
                                title = name,
                                count = filtered.size,
                                thumbnail = filtered.firstOrNull()?.uri,
                                isPhysical = managedFolderNames.contains(name) // アプリ作成フォルダならマーク
                            )
                        }

                        val groupItems = folderGroups
                            .filter { group -> !foldersInGroups.contains("group:${group.name}") } // 別のグループに属しているグループを除外
                            .map { group ->
                                val memberFolders = memberMap[group.name] ?: emptyList()
                                // グループ内の最初の4つのフォルダからサムネイルを取得
                                val thumbnails = memberFolders.take(4).mapNotNull { folderName ->
                                    if (folderName.startsWith("group:")) {
                                        val subGroupName = folderName.removePrefix("group:")
                                        memberMap[subGroupName]?.firstOrNull { !it.startsWith("group:") }?.let { fn ->
                                            folderData[fn]?.firstOrNull()?.uri
                                        }
                                    } else {
                                        folderData[folderName]?.firstOrNull()?.uri
                                    }
                                }
                                CategoryData(
                                    id = "group:${group.name}",
                                    title = group.name,
                                    count = memberFolders.size,
                                    thumbnail = null,
                                    groupThumbnails = thumbnails,
                                    isGroup = true
                                )
                            }
                        val combined = topLevelFolders + groupItems
                        combined.sortedWith(compareBy({ orderMap[it.id] ?: Int.MAX_VALUE }, { it.title }))
                    }

                    // 0件のフォルダでも、物理フォルダ(作成直後など)やグループは表示する
                    val categories = rawCategories.filter { it.count > 0 || it.isGroup || it.isPhysical }

                    CategoryScreen(
                        title = if (selectedGroupId != null) selectedGroupId!! else if (isSelectionMode) "移動先フォルダを選択" else "フォルダ",
                        categories = categories,
                        isLoading = isLoading,
                        galleryState = galleryState,
                        topBarActions = {
                            var showTopMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showTopMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "メニュー", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false },
                                    modifier = Modifier.background(Color.DarkGray)
                                ) {
                                    if (selectedGroupId != null && !isSelectionMode) {
                                        DropdownMenuItem(
                                            text = { Text("グループ削除", color = Color.White) },
                                            onClick = {
                                                showTopMenu = false
                                                showDeleteGroupConfirm = selectedGroupId
                                            }
                                        )
                                    }
                                    if (selectedGroupId == null && !isSelectionMode) {
                                        DropdownMenuItem(
                                            text = { Text("フォルダ作成", color = Color.White) },
                                            onClick = {
                                                showTopMenu = false
                                                showCreateFolderDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("グループ作成", color = Color.White) },
                                            onClick = {
                                                showTopMenu = false
                                                showCreateGroupDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onCategoryClick = { 
                            if (it.isGroup) {
                                selectedGroupId = it.title
                            } else {
                                if (isSelectionMode) {
                                    onFolderSelected(it.id)
                                } else {
                                    selectedFolderName = it.id 
                                    isSubCategorySelected = true
                                }
                            }
                        },
                        onMoveSelectedToGroup = { folderIds ->
                            selectedFoldersForGroup = folderIds
                        },
                        onDeleteGroups = { groupIds ->
                            // 最初のグループを削除確認（一括削除はシンプルなダイアログで対応）
                            if (groupIds.isNotEmpty()) {
                                showDeleteGroupConfirm = groupIds.first()
                            }
                        },
                        onDrop = { draggedId, targetId ->
                            if (targetId.startsWith("group:")) {
                                val groupName = targetId.removePrefix("group:")
                                val folderName = draggedId
                                scope.launch {
                                    galleryState.repository.addFolderToGroup(folderName, groupName)
                                }
                            } else if (targetId == "ROOT" && selectedGroupId != null) {
                                // グループ内から外（ROOT）へドロップした場合
                                scope.launch {
                                    galleryState.repository.removeFolderFromGroup(draggedId)
                                }
                            }
                        },
                        onReorder = { newOrderIds ->
                            scope.launch {
                                val entities = newOrderIds.mapIndexed { index, id ->
                                    com.example.gallery.data.local.entity.FolderOrderEntity(id, index)
                                }
                                galleryState.repository.updateFolderOrders(entities)
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
                            if (selectedFolderName != null) {
                                selectedFolderName = null
                                isSubCategorySelected = false
                            } else if (selectedGroupId != null) {
                                selectedGroupId = null
                            } else {
                                onBackToFolders()
                            }
                        },
                        onTabIconClick = { 
                            selectedFolderName = null
                            isSubCategorySelected = false
                            selectedGroupId = null
                            onBackToFolders() 
                        },
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

    fun getUniqueName(baseName: String, existingNames: List<String>): String {
        if (!existingNames.contains(baseName)) return baseName
        var counter = 1
        var newName: String
        do {
            newName = "$baseName ($counter)"
            counter++
        } while (existingNames.contains(newName))
        return newName
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("グループ作成") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("グループ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            val uniqueName = getUniqueName(newGroupName.trim(), folderGroups.map { it.name })
                            scope.launch {
                                galleryState.repository.createFolderGroup(uniqueName)
                                showCreateGroupDialog = false
                                newGroupName = ""
                            }
                        }
                    }
                ) { Text("作成") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("フォルダ作成") },
            text = {
                Column {
                    Text("DCIM直下に作成されます", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
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
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val allExistingFolders = folderData.keys.toList()
                            val uniqueName = getUniqueName(newFolderName.trim(), allExistingFolders)
                            scope.launch {
                                val success = galleryState.repository.createFolderUnderDCIM(uniqueName)
                                if (success) {
                                    galleryState.repository.addManagedFolder(uniqueName)
                                    loadAllMedia()
                                }
                                showCreateFolderDialog = false
                                newFolderName = ""
                            }
                        }
                    }
                ) { Text("作成") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("キャンセル") }
            }
        )
    }

    if (showFolderToGroupDialog != null) {
        val folderName = showFolderToGroupDialog!!
        AlertDialog(
            onDismissRequest = { showFolderToGroupDialog = null },
            title = { Text("グループへ追加/解除") },
            text = {
                Column {
                    Text("フォルダ: $folderName", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 現在の所属グループを確認
                    val currentGroup = groupMembers.find { it.folderName == folderName }?.groupName
                    
                    if (currentGroup != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    galleryState.repository.removeFolderFromGroup(folderName)
                                    showFolderToGroupDialog = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) { Text("現在のグループ($currentGroup)から解除") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Text("追加先を選択:")
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(folderGroups) { group ->
                            if (group.name != currentGroup) {
                                ListItem(
                                    headlineContent = { Text(group.name) },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            galleryState.repository.addFolderToGroup(folderName, group.name)
                                            showFolderToGroupDialog = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderToGroupDialog = null }) { Text("キャンセル") }
            }
        )
    }

    if (showDeleteGroupConfirm != null) {
        val groupName = showDeleteGroupConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirm = null },
            title = { Text("グループ削除") },
            text = { Text("グループ「$groupName」を削除しますか？\n中のフォルダは削除されず、外に出されます。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            galleryState.repository.deleteFolderGroup(groupName)
                            if (selectedGroupId == groupName) {
                                selectedGroupId = null
                            }
                            showDeleteGroupConfirm = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirm = null }) { Text("キャンセル") }
            }
        )
    }

    if (selectedFoldersForGroup != null) {
        val folderIds = selectedFoldersForGroup!!
        AlertDialog(
            onDismissRequest = { selectedFoldersForGroup = null },
            title = { Text(if (folderIds.size == 1) "${folderIds.first()} を移動" else "${folderIds.size} 件を移動") },
            text = {
                Column {
                    // 現在の所属グループを確認（単一選択時のみ表示すると親切）
                    if (folderIds.size == 1) {
                        val currentGroup = groupMembers.find { it.folderName == folderIds.first() }?.groupName
                        if (currentGroup != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        galleryState.repository.removeFolderFromGroup(folderIds.first())
                                        selectedFoldersForGroup = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) { Text("現在のグループ($currentGroup)から解除") }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    folderIds.forEach { galleryState.repository.removeFolderFromGroup(it) }
                                    selectedFoldersForGroup = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) { Text("すべてのグループ設定を解除") }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Text("移動先グループを選択:")
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(folderGroups) { group ->
                            ListItem(
                                headlineContent = { Text(group.name) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        folderIds.forEach { galleryState.repository.addFolderToGroup(it, group.name) }
                                        selectedFoldersForGroup = null
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedFoldersForGroup = null }) { Text("キャンセル") }
            }
        )
    }
}

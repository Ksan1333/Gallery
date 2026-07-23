package com.example.gallery.ui.screen

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.state.*
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.util.FolderGroupDefinition
import com.example.gallery.util.FolderGroupStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID

@OptIn(FlowPreview::class)
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
    onFolderStateChanged: (Boolean) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onBulkMove: ((List<String>) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val globalPrefs = remember { context.getSharedPreferences("global_settings", Context.MODE_PRIVATE) }
    var folderGroups by remember { mutableStateOf(FolderGroupStore.load(globalPrefs)) }
    var pendingGroupFolderIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingGroupTitle by remember { mutableStateOf("") }
    val folderData = remember { mutableStateMapOf<String, MutableList<MediaData>>() }
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }
    // 初期値を true にして、最初のロードが終わるまでスピナーを出す。
    // ただし、既にデータがある場合は isLoading=false から始まるように調整が必要かもしれないが、
    // LaunchedEffect で loadAllMedia が呼ばれるので true 開始で安全。
    var isLoading by remember { mutableStateOf(true) }
    var isCategoriesCalculating by remember { mutableStateOf(false) }
    var isInitialLoadFinished by remember { mutableStateOf(false) }

    val managedFolders by galleryState.repository.getAllManagedFolders().collectAsState(initial = emptyList())
    val managedFolderNames = remember(managedFolders) { managedFolders.map { it.folderName } }
    val folderThumbnails = remember(managedFolders) { managedFolders.associate { it.folderName to it.customThumbnailUri } }
    val folderOrders by galleryState.repository.getAllFolderOrders().collectAsState(initial = emptyList())
    val orderMap = remember(folderOrders) { folderOrders.associate { it.id to it.position } }

    var metadataMap by remember {
        mutableStateOf<Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>>(emptyMap())
    }
    var metadataVersion by remember { mutableIntStateOf(0) }
    var folderDataVersion by remember { mutableIntStateOf(0) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var lastReorderTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(galleryState.repository) {
        galleryState.repository.getAllMetadataSummaryFlow()
            .debounce(1000)
            .distinctUntilChanged()
            .collect { list ->
                metadataMap = withContext(Dispatchers.Default) {
                    list.associateBy { it.uri }
                }
                metadataVersion++
            }
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }

    var showFolderMenu by remember { mutableStateOf<CategoryData?>(null) }

    fun loadAllMedia(isRefresh: Boolean = false) {
        android.util.Log.d("FolderGallery", "loadAllMedia started")
        scope.launch(Dispatchers.IO) {
            if (!isRefresh || folderData.isEmpty()) {
                withContext(Dispatchers.Main) { isLoading = true }
            }
            try {
                val newMap = mutableMapOf<String, MutableList<MediaData>>()
                val allMedia = galleryState.repository.getAllMedia(forceRefresh = isRefresh)
                android.util.Log.d("FolderGallery", "loadAllMedia: allMedia size = ${allMedia.size}")

                // ディレクトリ走査は重いので、allMedia の folderName だけで構成する。
                allMedia.forEach { newMap.getOrPut(it.folderName) { mutableListOf() }.add(it) }

                // 明示的に管理されている空フォルダも追加する。
                managedFolderNames.forEach { if (!newMap.containsKey(it)) newMap[it] = mutableListOf() }
                newMap.values.forEach { it.sortByDescending { m -> m.dateAdded } }
                
                android.util.Log.d("FolderGallery", "loadAllMedia: folderMap keys = ${newMap.keys}")
                
                withContext(Dispatchers.Main) {
                    folderData.clear()
                    folderData.putAll(newMap)
                    folderDataVersion++
                    android.util.Log.d("FolderGallery", "loadAllMedia: folderData cleared and updated, version=$folderDataVersion")
                }
            } catch (e: Exception) {
                android.util.Log.e("FolderGallery", "loadAllMedia error", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    android.util.Log.d("FolderGallery", "loadAllMedia finished, isLoading=false")
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
        if (p.values.any { it }) loadAllMedia()
        else isLoading = false
    }

    LaunchedEffect(galleryState.refreshTrigger) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        val hasAnyPermission = perms.any {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            android.util.Log.d("FolderGallery", "Permission check: $it = $granted")
            granted
        }

        if (hasAnyPermission) {
            loadAllMedia(isRefresh = true)
        } else {
            android.util.Log.d("FolderGallery", "No permission, launching launcher")
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
    LaunchedEffect(selectedFolderName) {
        onFolderStateChanged(selectedFolderName != null)
    }

    var categories by remember { mutableStateOf<List<CategoryData>>(emptyList()) }
    var selectedCategoryMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isMediaCalculationFinished by remember { mutableStateOf(false) }

    LaunchedEffect(folderDataVersion, metadataVersion, galleryState.ageRatingFilter, folderThumbnails, orderMap, managedFolderNames, refreshVersion) {
        if (System.currentTimeMillis() - lastReorderTime < 2000L) return@LaunchedEffect

        android.util.Log.d("FolderGallery", "Categories update LaunchedEffect: folderDataVersion=$folderDataVersion, metadataVersion=$metadataVersion")
        isCategoriesCalculating = true

        val folderSnapshot = folderData.toMap()
        val metadataSnapshot = metadataMap
        val thumbnailSnapshot = folderThumbnails.toMap()
        val orderSnapshot = orderMap.toMap()
        val managedSnapshot = managedFolderNames.toSet()
        val ageFilter = galleryState.ageRatingFilter

        val nextCategories = withContext(Dispatchers.Default) {
            folderSnapshot.map { (name, images) ->
                val filtered = images.filter { m ->
                    val rating = metadataSnapshot[m.uri]?.ageRating ?: "SFW"
                    ageFilter == AgeRatingFilter.ALL ||
                        (ageFilter == AgeRatingFilter.SFW && rating == "SFW") ||
                        (ageFilter == AgeRatingFilter.R15 && rating == "R15") ||
                        (ageFilter == AgeRatingFilter.R18 && rating == "R18")
                }
                CategoryData(
                    id = name,
                    title = name,
                    count = filtered.size,
                    thumbnail = thumbnailSnapshot[name] ?: filtered.firstOrNull()?.uri ?: images.firstOrNull()?.uri,
                    isPhysical = managedSnapshot.contains(name)
                )
            }
                .sortedWith(compareBy({ orderSnapshot[it.id] ?: Int.MAX_VALUE }, { it.title }))
                .filter { it.isPhysical || it.count > 0 || thumbnailSnapshot[it.id] != null }
        }
        
        android.util.Log.d("FolderGallery", "Categories updated: count=${nextCategories.size}")
        categories = nextCategories
        isCategoriesCalculating = false
        isInitialLoadFinished = true
        android.util.Log.d("FolderGallery", "isInitialLoadFinished set to true")
    }

    LaunchedEffect(selectedFolderName, folderDataVersion, metadataVersion, galleryState.ageRatingFilter) {
        val folderName = selectedFolderName
        val mediaSnapshot = folderName?.let { folderData[it]?.toList() }.orEmpty()
        val metadataSnapshot = metadataMap
        val ageFilter = galleryState.ageRatingFilter
        val startedAt = System.currentTimeMillis()
        isMediaCalculationFinished = false
        android.util.Log.d(
            "FolderGallery",
            "folder_open_prepare folder=$folderName media=${mediaSnapshot.size} metadata=${metadataSnapshot.size} filter=$ageFilter"
        )
        selectedCategoryMedia = withContext(Dispatchers.Default) {
            mediaSnapshot.filter { m ->
                val rating = metadataSnapshot[m.uri]?.ageRating ?: "SFW"
                when (ageFilter) {
                    AgeRatingFilter.ALL -> true
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                }
            }
        }
        isMediaCalculationFinished = true
        android.util.Log.d(
            "FolderGallery",
            "folder_open_ready folder=$folderName visible=${selectedCategoryMedia.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
        )
    }

    LaunchedEffect(selectedCategoryMedia, selectedFolderName, isLoading, isMediaCalculationFinished) {
        if (selectedFolderName != null && !isLoading && isMediaCalculationFinished && selectedCategoryMedia.isEmpty()) {
            // Small delay to ensure state has settled and avoid flickers
            delay(500)
            if (selectedFolderName != null && !isLoading && isMediaCalculationFinished && selectedCategoryMedia.isEmpty()) {
                selectedFolderName = null
                isSubCategorySelected = false
            }
        }
    }

    val displayedCategories = remember(categories, folderGroups, refreshVersion) {
        val result = buildFolderGroupCategories(categories, folderGroups)
        android.util.Log.d("FolderGallery", "displayedCategories updated: result size=${result.size}, categories size=${categories.size}, groups size=${folderGroups.size}")
        result
    }

    fun persistFolderGroups(next: List<FolderGroupDefinition>) {
        folderGroups = next
        FolderGroupStore.save(globalPrefs, next)
        refreshVersion++
        galleryState.refresh()
    }

        Box(modifier = Modifier.fillMaxSize()) {
            CategoryScreen(
                title = if (isSelectionMode) stringResource(R.string.folder_picker_title) else stringResource(R.string.nav_folders),
                categories = displayedCategories,
                isLoading = (isLoading || isCategoriesCalculating || !isInitialLoadFinished) && displayedCategories.isEmpty() && !isInitialLoadFinished,
                galleryState = galleryState,
            onNavigateToTag = { tag ->
                onBackToFolders() // まずフォルダリストへ戻る。
                onFolderSelected("TAG_NAVIGATION:$tag") // 特別なプレフィックスで MainActivity へ通知する。
            },
            onMenuClick = if (!isSelectionMode) onMenuClick else null,
            topBarActions = {
                var showSortMenu by remember { mutableStateOf(false) }
                if (selectedFolderName != null) {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.label_sort),
                        tint = colors.primaryText
                    )
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
                                        val modeName = when (mode) {
                                            SortMode.DATE_ADDED -> stringResource(R.string.label_date_added)
                                            SortMode.SIZE -> stringResource(R.string.label_size)
                                            SortMode.NAME -> stringResource(R.string.label_name)
                                        }
                                        val orderName = if (ascending) stringResource(R.string.label_ascending) else stringResource(R.string.label_descending)
                                        Text(
                                            text = stringResource(R.string.sort_format, modeName, orderName),
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
                }
                }
                if (selectedFolderName == null && !isSelectionMode) {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.folder_create), tint = colors.primaryText)
                    }
                }
            },
            onCategoryClick = { if (isSelectionMode) onFolderSelected(it.id) else { selectedFolderName = it.id; isSubCategorySelected = true } },
            onCategoryLongClick = null,
            onReorder = { ids ->
                val categoriesById = displayedCategories.associateBy { it.id }
                val folderIds = ids.flatMap { id ->
                    categoriesById[id]?.groupMembers?.map { it.id }.orEmpty().ifEmpty { listOf(id) }
                }
                
                // Immediately update local categories state to prevent reorder "jump-back"
                val idToFlatCat = categories.associateBy { it.id }
                categories = folderIds.mapNotNull { idToFlatCat[it] }
                refreshVersion++
                lastReorderTime = System.currentTimeMillis()

                scope.launch {
                    galleryState.repository.updateFolderOrders(
                        folderIds.distinct().mapIndexed { i, id ->
                            com.example.gallery.data.local.entity.FolderOrderEntity(id, i)
                        }
                    )
                }
            },
            onCreateCategoryGroup = { folderIds, targetGroupId ->
                if (targetGroupId != null) {
                    val updated = folderGroups.map { group ->
                        if (group.id == targetGroupId) {
                            group.copy(folderIds = (group.folderIds + folderIds).distinct())
                        } else {
                            val remaining = group.folderIds.filterNot { it in folderIds }
                            group.copy(folderIds = remaining)
                        }
                    }.filter { it.folderIds.size >= 2 }
                    persistFolderGroups(updated)
                } else {
                    pendingGroupFolderIds = folderIds.distinct()
                    pendingGroupTitle = context.getString(
                        R.string.folder_group_default_name,
                        folderGroups.size + 1
                    )
                }
            },
            onUngroupCategory = { groupId ->
                persistFolderGroups(folderGroups.filterNot { it.id == groupId })
            },
            onUngroupCategoryMember = { groupId, folderId ->
                val updated = folderGroups.mapNotNull { group ->
                    if (group.id == groupId) {
                        val remaining = group.folderIds.filterNot { it == folderId }
                        if (remaining.size >= 2) {
                            group.copy(folderIds = remaining)
                        } else {
                            null
                        }
                    } else {
                        group
                    }
                }
                persistFolderGroups(updated)
            },
            onRenameCategoryGroup = { groupId, newName ->
                val updated = folderGroups.map { group ->
                    if (group.id == groupId) group.copy(title = newName) else group
                }
                persistFolderGroups(updated)
            },
            showThumbnails = true,
            initialColumnIndex = 3,
            onShowViewer = onShowViewer, onHideViewer = onHideViewer,
            selectedCategoryTitle = selectedFolderName,
            selectedCategoryMedia = selectedCategoryMedia,
            onBackFromCategory = { if (selectedFolderName != null) { selectedFolderName = null; isSubCategorySelected = false } else onBackToFolders() },
            onTabIconClick = { selectedFolderName = null; isSubCategorySelected = false; onBackToFolders() },
            lastViewedUri = galleryState.lastViewedUri,
            onPageChangedInViewer = { galleryState.lastViewedUri = it },
            onBulkEdit = onBulkEdit,
            onBulkMove = onBulkMove,
            onScrollConsumed = { galleryState.lastViewedUri = null },
            gridExtraBottomPadding = dimensionResource(R.dimen.spacing_micro) - dimensionResource(R.dimen.spacing_micro) // 0.dp
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.folder_create)) },
            text = {
                Column {
                    Text(stringResource(R.string.folder_dcim_desc), fontSize = textSizes.small, color = colors.mutedText)
                    Spacer(Modifier.height(dimensionResource(R.dimen.spacing_small)))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.folder_name)) },
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
                }) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    pendingGroupFolderIds?.let { folderIds ->
        AlertDialog(
            onDismissRequest = { pendingGroupFolderIds = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.folder_group_dialog_title), color = colors.primaryText) },
            text = {
                OutlinedTextField(
                    value = pendingGroupTitle,
                    onValueChange = { pendingGroupTitle = it },
                    label = { Text(stringResource(R.string.folder_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = pendingGroupTitle.isNotBlank() && folderIds.size >= 2,
                    onClick = {
                        val selected = folderIds.toSet()
                        val remainingGroups = folderGroups.mapNotNull { group ->
                            val remainingIds = group.folderIds.filterNot { it in selected }
                            group.copy(folderIds = remainingIds).takeIf { remainingIds.size >= 2 }
                        }
                        persistFolderGroups(
                            remainingGroups + FolderGroupDefinition(
                                id = UUID.randomUUID().toString(),
                                title = pendingGroupTitle.trim(),
                                folderIds = folderIds
                            )
                        )
                        pendingGroupFolderIds = null
                    }
                ) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGroupFolderIds = null }) {
                    Text(stringResource(R.string.btn_cancel), color = colors.secondaryText)
                }
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
                        headlineContent = { Text(stringResource(R.string.folder_reset_thumbnail)) },
                        leadingContent = { Icon(Icons.Default.Refresh, null) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                galleryState.repository.updateFolderThumbnail(folder.id, null)
                                showFolderMenu = null
                                Toast.makeText(context, context.getString(R.string.msg_cache_cleared), Toast.LENGTH_SHORT).show() // TODO: check string
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFolderMenu = null }) { Text(stringResource(R.string.btn_close)) } }
        )
    }
}

internal fun buildFolderGroupCategories(
    categories: List<CategoryData>,
    definitions: List<FolderGroupDefinition>
): List<CategoryData> {
    val categoriesById = categories.associateBy { it.id }
    val groupedFolderIds = hashSetOf<String>()
    val validGroups = definitions.mapNotNull { definition ->
        val members = definition.folderIds
            .mapNotNull(categoriesById::get)
            .distinctBy { it.id }
            .filterNot { it.id in groupedFolderIds }
        if (members.size >= 2) {
            groupedFolderIds += members.map { it.id }
            definition to members
        } else {
            null
        }
    }
    val groupByFolderId = linkedMapOf<String, Pair<FolderGroupDefinition, List<CategoryData>>>()
    validGroups.forEach { group ->
        group.second.forEach { member -> groupByFolderId.putIfAbsent(member.id, group) }
    }
    val emittedGroups = hashSetOf<String>()
    return buildList {
        categories.forEach { category ->
            val grouped = groupByFolderId[category.id]
            if (grouped == null) {
                add(category)
            } else if (emittedGroups.add(grouped.first.id)) {
                val definition = grouped.first
                val members = grouped.second
                add(
                    CategoryData(
                        id = "folder-group:${definition.id}",
                        title = definition.title,
                        count = members.sumOf { it.count },
                        thumbnail = members.firstNotNullOfOrNull { it.thumbnail },
                        isPhysical = members.all { it.isPhysical },
                        groupId = definition.id,
                        groupMembers = members
                    )
                )
            }
        }
    }
}

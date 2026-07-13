package com.example.gallery.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.ui.theme.GalleryThemeTokens
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.component.GalleryGridView
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.ui.screen.MediaViewerScreen
import kotlinx.coroutines.launch

@Stable
data class CategoryData(
    val id: String,
    val title: String,
    val count: Int,
    val thumbnail: String?,
    val indicatorColor: Color? = null,
    val isPhysical: Boolean = false,
    val subTitle: String? = null,
    val groupId: String? = null,
    val groupMembers: List<CategoryData> = emptyList()
)

@Composable
fun CategoryScreen(
    title: String,
    categories: List<CategoryData>,
    isLoading: Boolean = false,
    galleryState: GalleryState,
    onCategoryClick: (CategoryData) -> Unit,
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    emptyContent: @Composable BoxScope.() -> Unit = {
        val colors = GalleryThemeTokens.colors
        Text(
            stringResource(R.string.label_no_data),
            color = colors.mutedText
        )
    },
    loadingContent: @Composable BoxScope.() -> Unit = {
        val colors = GalleryThemeTokens.colors
        CircularProgressIndicator(color = colors.primaryText)
    },
    selectedCategoryTitle: String? = null,
    selectedCategoryMedia: List<MediaData> = emptyList(),
    onBackFromCategory: () -> Unit,
    onTabIconClick: (String) -> Unit = {},
    showControlBar: Boolean = true,
    lastViewedUri: String? = null,
    onPageChangedInViewer: (String) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onBulkMove: ((List<String>) -> Unit)? = null,
    onScrollConsumed: () -> Unit = {},
    onNavigateToTag: ((String) -> Unit)? = null,
    onCategoryLongClick: (CategoryData) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
    onCreateCategoryGroup: ((List<String>) -> Unit)? = null,
    onUngroupCategory: ((String) -> Unit)? = null,
    showThumbnails: Boolean = true,
    initialColumnIndex: Int? = null,
    showCategoryTopBar: Boolean = true,
    showSelectedCategoryTopBar: Boolean = true,
    gridExtraBottomPadding: androidx.compose.ui.unit.Dp = 100.dp,
    onImageClickOverride: ((Int, List<MediaData>) -> Unit)? = null,
    openInternalViewer: Boolean = true
) {
    val columnOptions = listOf(10, 7, 4, 3, 1)
    var currentColumnIndex by rememberSaveable {
        mutableIntStateOf(initialColumnIndex ?: 2)
    }
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentMediaListState = remember {
        mutableStateOf(emptyList<MediaData>())
    }
    var currentMediaList by currentMediaListState
    val scope = rememberCoroutineScope()
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes

    // 復元処理
    LaunchedEffect(selectedCategoryMedia) {
        if (selectedImageIndex != null && currentMediaList.isEmpty() && selectedCategoryMedia.isNotEmpty()) {
            currentMediaList = selectedCategoryMedia
        }
    }

    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }

    val selectedCategoryIds = remember { mutableStateListOf<String>() }
    var isCategorySelectionMode by remember { mutableStateOf(false) }
    var showBulkEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategoryIds.size) {
        if (selectedCategoryIds.isEmpty()) {
            isCategorySelectionMode = false
        }
    }

    // ドラッグ＆ドロップ関連
    var draggedCategoryId by remember { mutableStateOf<String?>(null) }
    var hoveredCategoryId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var initialDragCenter by remember { mutableStateOf(Offset.Zero) }
    var initialDragLocalOffset by remember { mutableStateOf(Offset.Zero) }
    val categoryBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val currentOnReorder by rememberUpdatedState(onReorder)

    val previewCategories = remember { mutableStateListOf<CategoryData>() }
    LaunchedEffect(categories) {
        if (draggedCategoryId == null) {
            previewCategories.clear()
            previewCategories.addAll(categories)
        }
    }

    var screenLayoutCoordinates by remember {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }

    val draggedCategory = remember(draggedCategoryId, categories) {
        categories.find { it.id == draggedCategoryId }
    }

    BackHandler(selectedCategoryTitle != null || selectedImageIndex != null || isSelectionModeActive || isCategorySelectionMode) {
        if (selectedImageIndex != null) {
            selectedImageIndex = null
            onHideViewer()
        } else if (isSelectionModeActive) {
            clearSelectionSignal++
        } else if (isCategorySelectionMode) {
            isCategorySelectionMode = false
            selectedCategoryIds.clear()
        } else {
            onBackFromCategory()
        }
    }

    var showSelectionMenu by remember { mutableStateOf(false) }
    var expandedGroupId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .onGloballyPositioned { screenLayoutCoordinates = it }) {
        if (selectedCategoryTitle == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showCategoryTopBar) {
                    Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.topBar)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(dimensionResource(R.dimen.header_height))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isCategorySelectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isCategorySelectionMode = false; selectedCategoryIds.clear()
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.btn_deselect),
                                    tint = colors.primaryText
                                )
                            }
                            Text(
                                stringResource(R.string.trash_item_count, selectedCategoryIds.size),
                                color = colors.primaryText,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.btn_open),
                                    tint = colors.primaryText
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false },
                                modifier = Modifier.background(colors.card)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_bulk), color = colors.primaryText) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showBulkEditDialog = true
                                    }
                                )
                                val selectedCategories = previewCategories.filter { it.id in selectedCategoryIds }
                                if (
                                    onCreateCategoryGroup != null &&
                                    selectedCategories.size >= 2 &&
                                    selectedCategories.all { it.groupId == null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.folder_group_create), color = colors.primaryText) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = colors.primaryText) },
                                        onClick = {
                                            showSelectionMenu = false
                                            onCreateCategoryGroup(selectedCategories.map { it.id })
                                            selectedCategoryIds.clear()
                                            isCategorySelectionMode = false
                                        }
                                    )
                                }
                                val selectedGroup = selectedCategories.singleOrNull()?.groupId
                                if (selectedGroup != null && onUngroupCategory != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.folder_group_ungroup), color = colors.primaryText) },
                                        leadingIcon = { Icon(Icons.Default.CallSplit, null, tint = colors.primaryText) },
                                        onClick = {
                                            showSelectionMenu = false
                                            onUngroupCategory(selectedGroup)
                                            selectedCategoryIds.clear()
                                            isCategorySelectionMode = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onMenuClick != null) {
                                IconButton(onClick = onMenuClick) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.btn_open),
                                        tint = colors.primaryText
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(title, color = colors.primaryText, fontSize = textSizes.header)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { topBarActions() }
                    }
                    }
                }

                if (showControlBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.dp)
                    ) {
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { loadingContent() }
                    } else if (categories.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { emptyContent() }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnOptions[currentColumnIndex]),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 8.dp,
                                end = 12.dp,
                                bottom = 180.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            flingBehavior = ScrollableDefaults.flingBehavior()
                        ) {
                            items(previewCategories, key = { it.id }) { category ->
                                val isDragging = draggedCategoryId == category.id
                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .onGloballyPositioned { layoutCoordinates ->
                                            categoryBounds[category.id] =
                                                layoutCoordinates.boundsInWindow()
                                        }
                                        .pointerInput(category.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    categoryBounds[category.id]?.let { rect ->
                                                        initialDragCenter = rect.center
                                                        draggedCategoryId = category.id
                                                        dragOffset = Offset.Zero
                                                        initialDragLocalOffset =
                                                            screenLayoutCoordinates?.windowToLocal(
                                                                rect.topLeft
                                                            ) ?: Offset.Zero

                                                        if (!selectedCategoryIds.contains(category.id)) {
                                                            if (!isCategorySelectionMode) {
                                                                isCategorySelectionMode = true
                                                            }
                                                            selectedCategoryIds.add(category.id)
                                                        }
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (draggedCategoryId == category.id) {
                                                        change.consume()
                                                        dragOffset += dragAmount
                                                        val currentCenter = initialDragCenter + dragOffset
                                                        val foundTarget = categoryBounds.entries.find { (id, bounds) ->
                                                            id != category.id && bounds.contains(currentCenter)
                                                        }
                                                        if (foundTarget != null) {
                                                            val targetId = foundTarget.key
                                                            val targetBounds = foundTarget.value
                                                            val fromIndex = previewCategories.indexOfFirst { it.id == category.id }
                                                            val toIndex = previewCategories.indexOfFirst { it.id == targetId }

                                                            // Swap if near center of the target to avoid flickering
                                                            val swapThreshold = targetBounds.width * 0.25f
                                                            val isNearTargetCenter = (currentCenter - targetBounds.center).getDistance() < swapThreshold

                                                            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex && isNearTargetCenter) {
                                                                val item = previewCategories.removeAt(fromIndex)
                                                                previewCategories.add(toIndex, item)
                                                                // Update center to new position to avoid immediate re-swap
                                                                initialDragCenter = targetBounds.center
                                                                dragOffset = currentCenter - initialDragCenter
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (draggedCategoryId == category.id) {
                                                        currentOnReorder(previewCategories.map { it.id })
                                                    }
                                                    draggedCategoryId = null
                                                    hoveredCategoryId = null
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    draggedCategoryId = null
                                                    hoveredCategoryId = null
                                                    dragOffset = Offset.Zero
                                                    previewCategories.clear()
                                                    previewCategories.addAll(categories)
                                                }
                                            )
                                        }
                                ) {
                                    CategoryCard(
                                        data = category,
                                        isSelected = selectedCategoryIds.contains(category.id),
                                        isDragging = isDragging,
                                        isDragTarget = false,
                                        alpha = if (isDragging) 0f else 1f,
                                        onClick = {
                                            // Safety reset
                                            if (draggedCategoryId != null) {
                                                draggedCategoryId = null
                                                dragOffset = Offset.Zero
                                                return@CategoryCard
                                            }

                                            if (isCategorySelectionMode) {
                                                if (selectedCategoryIds.contains(category.id)) {
                                                    selectedCategoryIds.remove(category.id)
                                                    if (selectedCategoryIds.isEmpty()) isCategorySelectionMode = false
                                                } else {
                                                    selectedCategoryIds.add(category.id)
                                                }
                                            } else {
                                                if (category.groupMembers.isNotEmpty()) {
                                                    expandedGroupId = category.id
                                                } else {
                                                    onCategoryClick(category)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isCategorySelectionMode) {
                                                isCategorySelectionMode = true
                                                selectedCategoryIds.add(category.id)
                                            }
                                        },
                                        showThumbnail = showThumbnails
                                    )
                                    if (category.groupMembers.isNotEmpty()) {
                                        FolderGroupPopup(
                                            expanded = expandedGroupId == category.id,
                                            category = category,
                                            onDismiss = { expandedGroupId = null },
                                            onFolderClick = { member ->
                                                expandedGroupId = null
                                                onCategoryClick(member)
                                            },
                                            onUngroup = category.groupId?.let { groupId ->
                                                onUngroupCategory?.let { ungroup ->
                                                    {
                                                        expandedGroupId = null
                                                        ungroup(groupId)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            GalleryGridView(
                imageList = selectedCategoryMedia,
                onImageClick = { index, list ->
                    if (onImageClickOverride != null) {
                        onImageClickOverride(index, list)
                    } else if (openInternalViewer) {
                        currentMediaList = list
                        selectedImageIndex = index
                        onShowViewer()
                    }
                },
                galleryState = galleryState,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = if (showSelectedCategoryTopBar) selectedCategoryTitle else null,
                onBackClick = if (showSelectedCategoryTopBar) onBackFromCategory else null,
                modifier = Modifier.fillMaxSize(),
                isFilterEnabled = false,
                scrollToUri = if (selectedImageIndex == null) lastViewedUri else null,
                onPageChangedInViewer = onPageChangedInViewer,
                onBulkEdit = onBulkEdit,
                onBulkMove = onBulkMove,
                onScrollConsumed = onScrollConsumed,
                topBarActions = topBarActions,
                showTopSection = showSelectedCategoryTopBar,
                extraBottomPadding = gridExtraBottomPadding
            )
        }

        if (draggedCategory != null && screenLayoutCoordinates != null) {
            val rect = categoryBounds[draggedCategory.id]
            if (rect != null) {
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (initialDragLocalOffset.x + dragOffset.x).toInt(),
                                (initialDragLocalOffset.y + dragOffset.y).toInt()
                            )
                        }
                        .size(
                            with(density) { rect.width.toDp() },
                            with(density) { rect.height.toDp() }
                        )
                        .zIndex(100f)
                ) {
                    CategoryCard(
                        data = draggedCategory,
                        isDragging = true,
                        scale = 1.1f,
                        showThumbnail = showThumbnails
                    )
                }
            }
        }

        if (openInternalViewer) selectedImageIndex?.let { index ->
            if (currentMediaList.isNotEmpty()) {
                MediaViewerScreen(
                    onClickedClose = { selectedImageIndex = null; onHideViewer() },
                    initialPage = index,
                    imageList = currentMediaList,
                    galleryState = galleryState,
                    onNavigateToTag = { tag: String ->
                        selectedImageIndex = null
                        onHideViewer()
                        onNavigateToTag?.invoke(tag)
                    },
                    onPageSelected = { it: Int ->
                        selectedImageIndex = it
                        currentMediaList.getOrNull(it)?.uri?.let { uri -> onPageChangedInViewer(uri) }
                    },
                    onNavigateToMedia = { uri: String ->
                        val idx = selectedCategoryMedia.indexOfFirst { it.uri == uri }
                        if (idx != -1) {
                            currentMediaList = selectedCategoryMedia
                            selectedImageIndex = idx
                        } else {
                            scope.launch {
                                val allMedia = galleryState.repository.getAllMedia()
                                val newIdx = allMedia.indexOfFirst { it.uri == uri }
                                if (newIdx != -1) {
                                    currentMediaList = allMedia
                                    selectedImageIndex = newIdx
                                } else {
                                    currentMediaList =
                                        listOf(MediaData(uri, System.currentTimeMillis()))
                                    selectedImageIndex = 0
                                }
                            }
                        }
                    }
                )
            }
        }

        if (showBulkEditDialog) {
            val allMediaInSelectedCategories = remember { mutableStateListOf<String>() }
            LaunchedEffect(selectedCategoryIds) {
                val uris = mutableListOf<String>()
                val all = galleryState.repository.getAllMedia()
                selectedCategoryIds.forEach { catId ->
                    uris.addAll(all.filter { it.folderName == catId || it.uri.contains("Tag:$catId") }
                        .map { it.uri })
                }
                allMediaInSelectedCategories.clear()
                allMediaInSelectedCategories.addAll(uris)
            }

            if (allMediaInSelectedCategories.isNotEmpty()) {
                UnifiedMediaEditDialog(
                    uris = allMediaInSelectedCategories.toList(),
                    repository = galleryState.repository,
                    onDismiss = {
                        showBulkEditDialog = false
                        isCategorySelectionMode = false
                        selectedCategoryIds.clear()
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderGroupPopup(
    expanded: Boolean,
    category: CategoryData,
    onDismiss: () -> Unit,
    onFolderClick: (CategoryData) -> Unit,
    onUngroup: (() -> Unit)?
) {
    val colors = GalleryThemeTokens.colors
    val scrollState = rememberScrollState()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(320.dp)
            .background(colors.card)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.title, color = colors.primaryText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        stringResource(R.string.folder_group_count, category.groupMembers.size),
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (onUngroup != null) {
                    IconButton(onClick = onUngroup) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = stringResource(R.string.folder_group_ungroup),
                            tint = colors.primaryText
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.divider)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                category.groupMembers.chunked(2).forEach { rowMembers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowMembers.forEach { member ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onFolderClick(member) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.surfaceVariant)
                                ) {
                                    GroupThumbnailItem(member.thumbnail)
                                }
                                Text(
                                    member.title,
                                    color = colors.primaryText,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    stringResource(R.string.trash_media_item_format, member.count),
                                    color = colors.mutedText,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        repeat(2 - rowMembers.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderGroupThumbnail(members: List<CategoryData>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        members.take(4).chunked(2).forEach { rowMembers ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rowMembers.forEach { member ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        GroupThumbnailItem(member.thumbnail)
                    }
                }
                repeat(2 - rowMembers.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GroupThumbnailItem(uri: String?) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    if (uri == null) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(colors.primaryText.copy(alpha = 0.05f)))
    } else {
        Image(
            painter = rememberAsyncImagePainter(
                model = remember(uri) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .videoFrameMillis(1000)
                        .build()
                }
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryCard(
    data: CategoryData,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isDragging: Boolean = false,
    isDragTarget: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    alpha: Float = 1f,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true
) {
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                when {
                    isDragTarget -> colors.primaryText.copy(alpha = 0.2f)
                    isSelected -> colors.accentSoft.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isDragTarget) Modifier.border(2.dp, colors.primaryText, RoundedCornerShape(12.dp))
                else Modifier
            )
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = if (isDragging) scale else 1f
                this.scaleY = if (isDragging) scale else 1f
                if (isDragging && dragOffset != Offset.Zero) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                }
            }
            .padding(4.dp)
    ) {
        if (showThumbnail) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
            ) {
                if (data.groupMembers.isNotEmpty()) {
                    FolderGroupThumbnail(data.groupMembers)
                    Surface(
                        color = colors.background.copy(alpha = 0.84f),
                        contentColor = colors.primaryText,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.folder_group_count, data.groupMembers.size),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (data.thumbnail != null) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = remember(data.thumbnail) {
                                ImageRequest.Builder(context)
                                    .data(data.thumbnail)
                                    .videoFrameMillis(1000)
                                    .build()
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, null, tint = colors.primaryText.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                    }
                }
                if (isSelected) {
                    Box(modifier = Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = colors.background, modifier = Modifier.size(40.dp))
                    }
                }
                if (data.indicatorColor != null) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp).clip(CircleShape).background(data.indicatorColor).border(1.5.dp, colors.background.copy(alpha = 0.8f), CircleShape))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = data.title, color = colors.primaryText, fontSize = textSizes.scrollbarLabel, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(text = stringResource(R.string.trash_media_item_format, data.count), color = colors.mutedText, fontSize = textSizes.extraSmall, modifier = Modifier.padding(horizontal = 4.dp))
        if (data.subTitle != null) {
            Text(text = data.subTitle, color = colors.accent.copy(alpha = 0.8f), fontSize = textSizes.tiny, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

package com.example.gallery.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.MediaData
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CategoryData(
    val id: String,
    val title: String,
    val count: Int,
    val thumbnail: String?,
    val groupThumbnails: List<String>? = null,
    val indicatorColor: Color? = null,
    val isGroup: Boolean = false,
    val isPhysical: Boolean = false // 追加
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
    onMenuClick: (() -> Unit)? = null, // 追加
    topBarActions: @Composable RowScope.() -> Unit = {},
    emptyContent: @Composable BoxScope.() -> Unit = { Text("データがありません", color = Color.Gray) },
    loadingContent: @Composable BoxScope.() -> Unit = { CircularProgressIndicator(color = Color.White) },
    selectedCategoryTitle: String? = null,
    selectedCategoryMedia: List<MediaData> = emptyList(),
    onBackFromCategory: () -> Unit,
    onTabIconClick: (String) -> Unit = {},
    showControlBar: Boolean = true,
    lastViewedUri: String? = null,
    onPageChangedInViewer: (String) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onDrop: (String, String) -> Unit = { _, _ -> },
    onCategoryLongClick: (CategoryData) -> Unit = {},
    onMoveSelectedToGroup: (List<String>) -> Unit = {},
    onDeleteGroups: (List<String>) -> Unit = {},
    onReorder: (List<String>) -> Unit = {}
) {
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentMediaListState = remember {
        mutableStateOf(emptyList<MediaData>())
    }
    var currentMediaList by currentMediaListState
    val scope = rememberCoroutineScope()

    // 復元処理: currentMediaListが空で、画像が選択されている場合、渡されたリストで同期する
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
    var initialDragLocalOffset by remember { mutableStateOf(Offset.Zero) } // 追加：開始時の位置を固定
    val categoryBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnReorder by rememberUpdatedState(onReorder)

    val previewCategories = remember { mutableStateListOf<CategoryData>() }
    LaunchedEffect(categories) {
        // ドラッグ中でない時のみ同期
        if (draggedCategoryId == null) {
            previewCategories.clear()
            previewCategories.addAll(categories)
        }
    }

    var screenLayoutCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    // ドラッグ中のアイテムのデータ
    val draggedCategory = remember(draggedCategoryId, categories) {
        categories.find { it.id == draggedCategoryId }
    }

    val columnOptions = listOf(10, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }
    val overscrollTranslationY = remember { Animatable(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    scope.launch {
                        val current = overscrollTranslationY.value
                        val delta = available.y * 0.4f
                        val newTranslation = if (current * delta > 0) {
                            current + delta * (1f / (1f + abs(current) / 100f))
                        } else {
                            current + delta
                        }
                        overscrollTranslationY.snapTo(newTranslation)
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                overscrollTranslationY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                return super.onPostFling(consumed, available)
            }
        }
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

    val onlyGroupsSelected = remember(selectedCategoryIds, previewCategories) {
        selectedCategoryIds.isNotEmpty() && selectedCategoryIds.all { id -> previewCategories.find { it.id == id }?.isGroup == true }
    }
    val selectedGroupTitles = remember(selectedCategoryIds, previewCategories) {
        selectedCategoryIds.mapNotNull { id ->
            val cat = previewCategories.find { it.id == id }
            if (cat?.isGroup == true) cat.title else null
        }
    }

    var showSelectionMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(AppConstants.BackgroundColor).onGloballyPositioned { screenLayoutCoordinates = it }) {
        if (selectedCategoryTitle == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars).height(AppConstants.HeaderHeight).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isCategorySelectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isCategorySelectionMode = false; selectedCategoryIds.clear() }) { Icon(Icons.Default.Close, contentDescription = "解除", tint = Color.White) }
                            Text("${selectedCategoryIds.size} 件選択中", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "メニュー", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false },
                                modifier = Modifier.background(Color.DarkGray)
                            ) {
                                if (onlyGroupsSelected) {
                                    DropdownMenuItem(
                                        text = { Text("グループ削除", color = Color.White) },
                                        onClick = {
                                            showSelectionMenu = false
                                            onDeleteGroups(selectedGroupTitles)
                                            selectedCategoryIds.clear()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("グループへ移動", color = Color.White) },
                                    onClick = {
                                        showSelectionMenu = false
                                        onMoveSelectedToGroup(selectedCategoryIds.toList())
                                        selectedCategoryIds.clear()
                                        isCategorySelectionMode = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("一括編集", color = Color.White) },
                                    onClick = {
                                        showSelectionMenu = false
                                        showBulkEditDialog = true
                                    }
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onMenuClick != null) {
                                IconButton(onClick = onMenuClick) {
                                    Icon(Icons.Default.Menu, contentDescription = "メニュー", tint = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(title, color = Color.White, fontSize = AppConstants.HeaderFontSize)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { topBarActions() }
                    }
                }

                if (showControlBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppConstants.HeaderHeight)
                            .background(AppConstants.BackgroundColor.copy(alpha = 0.95f))
                            .onGloballyPositioned { layoutCoordinates ->
                                categoryBounds["ROOT"] = layoutCoordinates.boundsInWindow()
                            }
                    ) {
                        GalleryTopControlBar(galleryState = galleryState, isFilterEnabled = false)
                    }
                }

                Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection).graphicsLayer { translationY = overscrollTranslationY.value }) {
                    if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { loadingContent() } }
                    else if (categories.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { emptyContent() } }
                    else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnOptions[currentColumnIndex]),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 100.dp),
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
                                            categoryBounds[category.id] = layoutCoordinates.boundsInWindow()
                                        }
                                        .pointerInput(category.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    categoryBounds[category.id]?.let { rect ->
                                                        initialDragCenter = rect.center
                                                        draggedCategoryId = category.id
                                                        dragOffset = Offset.Zero
                                                        initialDragLocalOffset = screenLayoutCoordinates?.windowToLocal(rect.topLeft) ?: Offset.Zero

                                                        // 選択されていないアイテムをドラッグし始めたら選択に追加
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

                                                        // ターゲットの検索
                                                        val foundTarget = categoryBounds.entries.find { (id, bounds) ->
                                                            id != category.id && bounds.contains(currentCenter)
                                                        }

                                                        if (foundTarget != null) {
                                                            val targetId = foundTarget.key
                                                            val targetBounds = foundTarget.value
                                                            val targetCat = previewCategories.find { it.id == targetId }

                                                            // グループ化判定 (ターゲットがグループ かつ ドラッグ中心がターゲットの中央付近にある場合)
                                                            val groupZone = targetBounds.deflate(targetBounds.width * 0.2f)

                                                            if (targetCat?.isGroup == true && groupZone.contains(currentCenter) && !selectedCategoryIds.contains(targetId)) {
                                                                hoveredCategoryId = targetId
                                                            } else {
                                                                hoveredCategoryId = null
                                                                // 並び替えアニメーション用プレビューの更新
                                                                val fromIndex = previewCategories.indexOfFirst { it.id == category.id }
                                                                val toIndex = previewCategories.indexOfFirst { it.id == targetId }

                                                                // ターゲットの端の方にいる時だけ入れ替える（ホバー中の安定性向上のため）
                                                                val swapThreshold = targetBounds.width * 0.15f
                                                                val isNearEdges = currentCenter.x < targetBounds.left + swapThreshold ||
                                                                                 currentCenter.x > targetBounds.right - swapThreshold ||
                                                                                 currentCenter.y < targetBounds.top + swapThreshold ||
                                                                                 currentCenter.y > targetBounds.bottom - swapThreshold

                                                                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex && isNearEdges) {
                                                                    val item = previewCategories.removeAt(fromIndex)
                                                                    previewCategories.add(toIndex, item)
                                                                }
                                                            }
                                                        } else {
                                                            // ROOT（連れ出し）エリア判定
                                                            if (categoryBounds["ROOT"]?.contains(currentCenter) == true) {
                                                                hoveredCategoryId = "ROOT"
                                                            } else {
                                                                hoveredCategoryId = null
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (draggedCategoryId == category.id) {
                                                        if (hoveredCategoryId != null) {
                                                            val targetId = hoveredCategoryId!!
                                                            if (targetId == "ROOT" || (previewCategories.find { it.id == targetId }?.isGroup == true)) {
                                                                // グループへの移動または連れ出し
                                                                selectedCategoryIds.forEach { id -> currentOnDrop(id, targetId) }
                                                                selectedCategoryIds.clear()
                                                                isCategorySelectionMode = false
                                                            }
                                                        } else {
                                                            // 並び替えの確定
                                                            currentOnReorder(previewCategories.map { it.id })
                                                        }
                                                        draggedCategoryId = null
                                                        hoveredCategoryId = null
                                                        dragOffset = Offset.Zero
                                                    }
                                                },
                                                onDragCancel = {
                                                    if (draggedCategoryId == category.id) {
                                                        draggedCategoryId = null
                                                        hoveredCategoryId = null
                                                        dragOffset = Offset.Zero
                                                        // 並び替えプレビューをリセット
                                                        previewCategories.clear()
                                                        previewCategories.addAll(categories)
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    CategoryCard(
                                        data = category,
                                        isSelected = selectedCategoryIds.contains(category.id),
                                        isDragging = isDragging,
                                        isDragTarget = hoveredCategoryId == category.id && category.isGroup,
                                        alpha = if (isDragging) 0f else 1f, // ドラッグ中は非表示（背後にあるため）
                                        onClick = {
                                            if (isCategorySelectionMode) {
                                                if (selectedCategoryIds.contains(category.id)) {
                                                    selectedCategoryIds.remove(category.id)
                                                    if (selectedCategoryIds.isEmpty()) isCategorySelectionMode = false
                                                } else { selectedCategoryIds.add(category.id) }
                                            } else { onCategoryClick(category) }
                                        },
                                        onLongClick = null // ドラッグ（detectDragGesturesAfterLongPress）と競合するため常にnull
                                    )
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
                    currentMediaList = list
                    selectedImageIndex = index
                    onShowViewer()
                },
                galleryState = galleryState,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = selectedCategoryTitle,
                onBackClick = onBackFromCategory,
                modifier = Modifier.fillMaxSize(),
                scrollToUri = if (selectedImageIndex == null) lastViewedUri else null,
                onPageChangedInViewer = onPageChangedInViewer,
                onBulkEdit = onBulkEdit
            )
        }

        // --- ドラッグ中のフローティング表示 ---
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
                        scale = 1.1f
                    )
                }
            }
        }

        selectedImageIndex?.let { index ->
            if (currentMediaList.isNotEmpty()) {
                PictureViewer(
                    onClickedClose = { selectedImageIndex = null; onHideViewer() },
                    initialPage = index,
                    imageList = currentMediaList,
                    galleryState = galleryState,
                    onPageSelected = {
                        selectedImageIndex = it
                        currentMediaList.getOrNull(it)?.uri?.let { uri -> onPageChangedInViewer(uri) }
                    },
                    onNavigateToMedia = { uri ->
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
                                    currentMediaList = listOf(MediaData(uri, System.currentTimeMillis()))
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
                    uris.addAll(all.filter { it.folderName == catId || it.uri.contains("Tag:$catId") }.map { it.uri })
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
private fun GroupThumbnailItem(uri: String?) {
    val context = LocalContext.current
    if (uri == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.05f)))
    } else if (uri.startsWith("mock://")) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    } else {
        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .videoFrameMillis(1000)
                    .build()
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                    isDragTarget -> Color.White.copy(alpha = 0.2f)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isDragTarget) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
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
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(if (data.isGroup) Color.DarkGray.copy(alpha = 0.8f) else Color.DarkGray)) {
            if (data.isGroup) {
                if (!data.groupThumbnails.isNullOrEmpty()) {
                    // 2x2 グリッド表示
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)) {
                                GroupThumbnailItem(data.groupThumbnails.getOrNull(0))
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)) {
                                GroupThumbnailItem(data.groupThumbnails.getOrNull(1))
                            }
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)) {
                                GroupThumbnailItem(data.groupThumbnails.getOrNull(2))
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)) {
                                GroupThumbnailItem(data.groupThumbnails.getOrNull(3))
                            }
                        }
                    }
                } else {
                    // グループ用アイコン表示 (中身が空の場合)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.FolderCopy, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    }
                }
            } else if (data.thumbnail != null) {
                if (data.thumbnail.startsWith("mock://")) {
                    Box(modifier = Modifier.fillMaxSize().background(data.indicatorColor?.copy(alpha = 0.5f) ?: Color.Gray.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(model = ImageRequest.Builder(context).data(data.thumbnail).decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(1000).build()),
                        contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(data.indicatorColor?.copy(alpha = 0.5f) ?: Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
            }
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            if (data.indicatorColor != null) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp).clip(CircleShape).background(data.indicatorColor).border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = data.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(text = if (data.isGroup) "${data.count} フォルダ" else "${data.count} 枚", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

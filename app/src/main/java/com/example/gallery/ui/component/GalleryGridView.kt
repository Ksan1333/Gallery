package com.example.gallery.ui.component

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.state.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// グリッド表示用の平坦化されたアイテム型
sealed class GridItem {
    data class Header(val title: String) : GridItem()
    data class Media(val data: MediaData) : GridItem()

    val key: String
        get() = when (this) {
            is Header -> "header_$title"
            is Media -> data.uri
        }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun GalleryGridView(
    imageList: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    galleryState: GalleryState = rememberGalleryState(LocalContext.current),
    onTabIconClick: ((String) -> Unit)? = null,
    clearSelectionSignal: Int = 0,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    title: String? = null,
    onBackClick: (() -> Unit)? = null,
    scrollToUri: String? = null,
    isFilterEnabled: Boolean = true,
    onMenuClick: (() -> Unit)? = null,
    onPageChangedInViewer: (String) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onSelectionChanged: (List<String>) -> Unit = {},
    isTrashMode: Boolean = false,
    onScrollConsumed: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {} // 追加
) {
    val columnOptions = listOf(28, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }

    LaunchedEffect(currentColumnIndex) {
        galleryState.groupingMode = when (columnOptions[currentColumnIndex]) {
            28 -> GroupingMode.YEAR; 7 -> GroupingMode.MONTH; else -> GroupingMode.DAY
        }
    }

    var isSelectionMode by remember { mutableStateOf(false) }

    // GalleryStateと同期
    LaunchedEffect(isSelectionMode) { galleryState.isSelectionMode = isSelectionMode }

    // ズーム中または指を離していない状態を親に伝える（サイドバー抑制用）
    val currentOnSelectionModeChanged by rememberUpdatedState(onSelectionModeChanged)
    LaunchedEffect(galleryState.isZooming, isSelectionMode) {
        currentOnSelectionModeChanged(isSelectionMode || galleryState.isZooming)
    }
    val selectedUris = remember { mutableStateListOf<String>() }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedUris.toList()) { onSelectionChanged(selectedUris.toList()) }

    var visibleCount by remember { mutableIntStateOf(60) }
    var isChangingColumns by remember { mutableStateOf(false) }
    var highlightUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val context = LocalContext.current

    val topTitleHeight = if (title != null) AppConstants.HeaderHeight else 0.dp
    val topActionsHeight = AppConstants.HeaderHeight
    val statusBarsHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalTopBarHeight =
        (if (title != null) topTitleHeight + statusBarsHeight else statusBarsHeight) + topActionsHeight
    val navBarsHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalBottomPadding = navBarsHeight + 100.dp
    val totalTopBarHeightPx = with(LocalDensity.current) { totalTopBarHeight.toPx() }
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = topBarOffsetHeightPx + delta
                if (!isSelectionMode) topBarOffsetHeightPx =
                    newOffset.coerceIn(-totalTopBarHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(clearSelectionSignal) {
        if (clearSelectionSignal > 0) {
            isSelectionMode = false; selectedUris.clear()
        }
    }
    LaunchedEffect(isSelectionMode) {
        onSelectionModeChanged(isSelectionMode); if (isSelectionMode) topBarOffsetHeightPx = 0f
    }

    // メタデータの更新頻度を抑える（分析中のもっさり感を解消）
    val metadataMap = remember { mutableStateMapOf<String, com.example.gallery.data.local.entity.MediaMetadataSummary>() }
    LaunchedEffect(galleryState.repository) {
        galleryState.repository.getAllMetadataSummaryFlow()
            .debounce(1000) // 1秒待機して更新（分析中の連続更新を抑制）
            .distinctUntilChanged()
            .collect { list ->
                list.forEach { metadataMap[it.uri] = it }
                    // 削除されたものを整理（頻繁には起きないはず）
                if (metadataMap.size > list.size + 100) {
                    val uris = list.map { it.uri }.toSet()
                    metadataMap.keys.retainAll(uris)
                }
            }
    }

    val configuration = LocalConfiguration.current
    val deviceAspectRatio =
        configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    // パフォーマンス最適化：フィルタリングとソートをバックグラウンドで実行
    var filteredList by remember { mutableStateOf(emptyList<MediaData>()) }
    
    LaunchedEffect(
        imageList,
        galleryState.mediaTypeFilter,
        galleryState.ageRatingFilter,
        galleryState.deviceFilter,
        galleryState.sortMode,
        galleryState.isAscending,
        metadataMap.size // メタデータが増えた時に再フィルタリング
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val typeFiltered = if (galleryState.mediaTypeFilter == MediaTypeFilter.ALL) imageList
            else imageList.filter { item ->
                when (galleryState.mediaTypeFilter) {
                    MediaTypeFilter.IMAGE -> item.mimeType?.startsWith("image/") == true && !item.isGif
                    MediaTypeFilter.VIDEO -> item.isVideo
                    MediaTypeFilter.GIF -> item.isGif
                    else -> true
                }
            }

            val ageFiltered = if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) typeFiltered
            else typeFiltered.filter { item ->
                val rating = metadataMap[item.uri]?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) {
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                    else -> true
                }
            }

            val deviceFiltered = if (galleryState.deviceFilter == DeviceFilter.ALL) ageFiltered
            else ageFiltered.filter { item ->
                if (item.isVideo || item.width <= 0 || item.height <= 0) false 
                else {
                    val ratio = item.height.toFloat() / item.width.toFloat()
                    when (galleryState.deviceFilter) {
                        DeviceFilter.SMARTPHONE -> item.height > item.width && Math.abs(ratio - deviceAspectRatio) < 0.2f
                        DeviceFilter.PC -> item.width > item.height && (item.width.toFloat() / item.height.toFloat()) > 1.3f
                        else -> true
                    }
                }
            }

            val sorted = when (galleryState.sortMode) {
                SortMode.DATE_ADDED -> deviceFiltered.sortedByDescending { it.dateAdded }
                SortMode.SIZE -> deviceFiltered.sortedBy { it.fileSize }
                SortMode.NAME -> deviceFiltered.sortedWith(
                    compareBy({
                        val firstChar = it.fileName.firstOrNull()
                        firstChar == null || !firstChar.isDigit()
                    }, { it.fileName })
                )
            }
            
            val finalResult = if (galleryState.isAscending) sorted.reversed() else sorted
            filteredList = finalResult
        }
    }

    // 平坦化されたリストの計算（1万件でも高速に動作するように最適化）
    val flatGridItems by remember(filteredList, galleryState.groupingMode, currentColumnIndex) {
        derivedStateOf {
            val is28 = columnOptions[currentColumnIndex] == 28
            val result = ArrayList<GridItem>(filteredList.size + 100)

            if (galleryState.groupingMode == GroupingMode.NONE) {
                filteredList.forEach { result.add(GridItem.Media(it)) }
            } else {
                val sdf = when (galleryState.groupingMode) {
                    GroupingMode.DAY -> SimpleDateFormat("yyyy年M月d日", Locale.JAPAN)
                    GroupingMode.MONTH -> SimpleDateFormat("yyyy年M月", Locale.JAPAN)
                    GroupingMode.YEAR -> SimpleDateFormat("yyyy年", Locale.JAPAN)
                    else -> null
                }
                
                if (sdf != null) {
                    var lastHeaderText: String? = null
                    var currentGroupCount = 0
                    
                    for (media in filteredList) {
                        val headerText = sdf.format(Date(media.dateAdded))
                        if (headerText != lastHeaderText) {
                            result.add(GridItem.Header(headerText))
                            lastHeaderText = headerText
                            currentGroupCount = 0
                        }
                        
                        // 28列表示（非常に小さいサムネイル）の場合、1グループ（年）あたりの件数を制限して
                        // スクロールとレンダリングの負荷を劇的に下げる
                        if (is28 && galleryState.groupingMode == GroupingMode.YEAR) {
                            if (currentGroupCount < 224) {
                                result.add(GridItem.Media(media))
                                currentGroupCount++
                            }
                        } else {
                            result.add(GridItem.Media(media))
                        }
                    }
                } else if (galleryState.groupingMode == GroupingMode.STORAGE) {
                    val groups = filteredList.groupBy {
                        if (it.uri.contains("emulated/0")) "内部ストレージ" 
                        else if (it.uri.contains("sdcard") || it.uri.contains("-")) "SDカード・外部ストレージ" 
                        else "その他"
                    }
                    groups.forEach { (header, items) ->
                        result.add(GridItem.Header(header))
                        items.forEach { result.add(GridItem.Media(it)) }
                    }
                }
            }
            result
        }
    }

    // スクロール位置に基づいて次のアイテムをプリフェッチ（スクロール停止時のみ実行）
    LaunchedEffect(gridState.firstVisibleItemIndex) {
        delay(150) // スクロール中の連続呼び出しを抑制
        val totalCount = flatGridItems.size
        if (totalCount == 0) return@LaunchedEffect

        val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val prefetchCount = 40
        val start = (lastVisibleIndex + 1).coerceAtMost(totalCount - 1)
        val end = (lastVisibleIndex + prefetchCount).coerceAtMost(totalCount - 1)

        if (start <= end) {
            for (i in start..end) {
                val item = flatGridItems[i]
                if (item is GridItem.Media) {
                    val thumbSize = when {
                        columnOptions[currentColumnIndex] >= 28 -> 32; columnOptions[currentColumnIndex] >= 7 -> 128; columnOptions[currentColumnIndex] >= 4 -> 512; else -> 1024
                    }
                    val request = ImageRequest.Builder(context)
                        .data(item.data.uri)
                        .size(thumbSize)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
        }
    }

    val currentFlatImageList = filteredList
    LaunchedEffect(currentColumnIndex) {
        isChangingColumns = true; delay(10)
        delay(100); isChangingColumns = false
    }

    LaunchedEffect(scrollToUri, filteredList, flatGridItems) {
        if (scrollToUri != null && filteredList.isNotEmpty()) {
            val targetGridIndex =
                flatGridItems.indexOfFirst { it is GridItem.Media && it.data.uri == scrollToUri }
            if (targetGridIndex != -1) {
                val isAlreadyVisible =
                    gridState.layoutInfo.visibleItemsInfo.any { it.key == scrollToUri }
                if (!isAlreadyVisible) {
                    val jumpIndex =
                        (targetGridIndex - columnOptions[currentColumnIndex] * 2).coerceAtLeast(0)
                    gridState.scrollToItem(jumpIndex); delay(50); gridState.animateScrollToItem(
                        targetGridIndex
                    )
                }
                highlightUri =
                    scrollToUri; delay(2500); if (highlightUri == scrollToUri) highlightUri = null
                onScrollConsumed() // スクロールが実行された（またはターゲットが見つかった）ので通知
            }
        } else if (scrollToUri == null) {
            highlightUri = null // URIがクリアされたらハイライトも消す
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (isLoading || isChangingColumns) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color.White) }
        } else {
            AnimatedContent(
                targetState = currentColumnIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(
                        animationSpec = tween(250)
                    )
                },
                label = "gridZoom",
                modifier = Modifier.fillMaxSize()
            ) { targetIndex ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnOptions[targetIndex]),
                    userScrollEnabled = !galleryState.isZooming && !isChangingColumns,
                    state = gridState,
                    contentPadding = PaddingValues(
                        top = totalTopBarHeight + 8.dp,
                        bottom = totalBottomPadding,
                        end = 40.dp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                .pointerInput(currentFlatImageList, targetIndex, isSelectionMode) {
                    awaitPointerEventScope {
                        var zoomAccumulator = 1f
                        var zoomAppliedInThisGesture = false
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val changes = event.changes
                            
                            // 2本以上の指が触れている場合はズーム中とみなす
                            if (changes.size >= 2) {
                                galleryState.isZooming = true
                            }

                            if (galleryState.isZooming) {
                                changes.forEach { it.consume() }
                                if (!zoomAppliedInThisGesture && !isSelectionMode) {
                                    val zoom = event.calculateZoom()
                                    zoomAccumulator *= zoom
                                    if (zoomAccumulator > 1.3f && currentColumnIndex < columnOptions.size - 1) {
                                        currentColumnIndex++
                                        zoomAppliedInThisGesture = true
                                    } else if (zoomAccumulator < 0.7f && currentColumnIndex > 0) {
                                        currentColumnIndex--
                                        zoomAppliedInThisGesture = true
                                    }
                                }
                            }
                            
                            // 全ての指が離れたらズーム状態を解除
                            if (changes.all { !it.pressed }) {
                                galleryState.isZooming = false
                                zoomAccumulator = 1f
                                zoomAppliedInThisGesture = false
                                break
                            }
                        }
                    }
                }
                        .pointerInput(currentFlatImageList, isSelectionMode) {
                            if (columnOptions[targetIndex] >= 28) return@pointerInput
                            val touchSlop = viewConfiguration.touchSlop
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isLongPress = false;
                                    var firstItemIndex = -1;
                                    val timeout = 700L;
                                    val startTime = System.currentTimeMillis()
                                    var isCancelledByMovement = false
                                    while (true) {
                                        val event = awaitPointerEvent();
                                        val currentTime = System.currentTimeMillis()
                                        
                                        // 指が動いたら長押しをキャンセル
                                        val currentPos = event.changes.first().position
                                        if (Math.abs(currentPos.x - down.position.x) > touchSlop || 
                                            Math.abs(currentPos.y - down.position.y) > touchSlop) {
                                            if (!isLongPress) isCancelledByMovement = true
                                        }

                                        // ズーム中(galleryState.isZooming)または2本以上の指が触れている場合は、選択モードへの移行や範囲選択を抑制する
                                        val isMultiTouch = event.changes.size >= 2

                                        if (currentTime - startTime > timeout && !isLongPress && !galleryState.isZooming && !isMultiTouch && !isCancelledByMovement) {
                                            isLongPress = true;
                                            val offset = down.position;
                                            val beforePadding =
                                                gridState.layoutInfo.beforeContentPadding
                                            gridState.layoutInfo.visibleItemsInfo.find {
                                                val itemTop = it.offset.y.toFloat() + beforePadding;
                                                val itemBottom =
                                                    itemTop + it.size.height; offset.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() && offset.y in itemTop..itemBottom
                                            }?.let { itemInfo ->
                                                val uri = itemInfo.key as? String
                                                if (uri != null && !uri.startsWith("header:")) {
                                                    if (!isSelectionMode) isSelectionMode =
                                                        true; if (!selectedUris.contains(uri)) selectedUris.add(
                                                        uri
                                                    ); firstItemIndex =
                                                        currentFlatImageList.indexOfFirst { it.uri == uri }
                                                }
                                            }
                                        }
                                        if (isLongPress && firstItemIndex != -1) {
                                            val dragChange = event.changes.first();
                                            val currentOffset = dragChange.position;
                                            val viewHeight = size.height;
                                            val scrollThreshold = 100.dp.toPx()
                                            if (currentOffset.y < scrollThreshold) gridState.dispatchRawDelta(
                                                ((scrollThreshold - currentOffset.y) / scrollThreshold * -30f).toInt()
                                                    .toFloat()
                                            )
                                            else if (currentOffset.y > viewHeight - scrollThreshold) gridState.dispatchRawDelta(
                                                ((currentOffset.y - (viewHeight - scrollThreshold)) / scrollThreshold * 30f).toInt()
                                                    .toFloat()
                                            )
                                            val beforePadding =
                                                gridState.layoutInfo.beforeContentPadding
                                            gridState.layoutInfo.visibleItemsInfo.find {
                                                val itemTop = it.offset.y.toFloat() + beforePadding;
                                                val itemBottom =
                                                    itemTop + it.size.height; currentOffset.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() && currentOffset.y in itemTop..itemBottom
                                            }?.let { itemInfo ->
                                                val currentUri = itemInfo.key as? String
                                                if (currentUri != null && !currentUri.startsWith("header:")) {
                                                    val currentIndex =
                                                        currentFlatImageList.indexOfFirst { it.uri == currentUri }; if (currentIndex != -1) {
                                                        val start =
                                                            minOf(firstItemIndex, currentIndex);
                                                        val end = maxOf(
                                                            firstItemIndex,
                                                            currentIndex
                                                        ); for (i in start..end) {
                                                            val uriToSelect =
                                                                currentFlatImageList[i].uri; if (!selectedUris.contains(
                                                                    uriToSelect
                                                                )
                                                            ) selectedUris.add(uriToSelect)
                                                        }
                                                    }
                                                }
                                            }
                                            dragChange.consume()
                                        }
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                }
                            }
                        }
                ) {
                    items(flatGridItems, key = { it.key }, span = { item ->
                        if (item is GridItem.Header) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    }) { item ->
                        when (item) {
                            is GridItem.Header -> {
                                Text(
                                    item.title,
                                    color = Color.White,
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = AppConstants.HeaderFontSize
                                )
                            }

                            is GridItem.Media -> {
                                val media = item.data
                                val columnCount = columnOptions[targetIndex]
                                
                                // key(media.uri) は items() の key引数で指定済みなので不要
                                GridMediaItem(
                                    media = media,
                                    metadata = metadataMap[media.uri],
                                    columnCount = columnCount,
                                    isHighlighted = highlightUri == media.uri,
                                    isSelected = selectedUris.contains(media.uri),
                                    onImageClick = { 
                                        if (isSelectionMode) {
                                            if (selectedUris.contains(media.uri)) {
                                                selectedUris.remove(media.uri); if (selectedUris.isEmpty()) {
                                                    isSelectionMode = false; lastSelectedIndex = -1
                                                }
                                            } else {
                                                selectedUris.add(media.uri); lastSelectedIndex = filteredList.indexOf(media)
                                            }
                                        } else {
                                            val idx = currentFlatImageList.indexOf(media)
                                            onImageClick(idx, currentFlatImageList)
                                            onPageChangedInViewer(media.uri)
                                        }
                                    },
                                    onLongClick = {
                                        val idx = filteredList.indexOf(media)
                                        if (!isSelectionMode) {
                                            isSelectionMode = true; selectedUris.add(media.uri)
                                        } else {
                                            if (selectedUris.contains(media.uri)) {
                                                selectedUris.remove(media.uri); if (selectedUris.isEmpty()) isSelectionMode = false
                                            } else selectedUris.add(media.uri)
                                        }
                                        lastSelectedIndex = idx
                                    },
                                    modifier = if (columnCount <= 4) Modifier.animateItem() else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!galleryState.isZooming) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, topBarOffsetHeightPx.roundToInt()) }
                    .zIndex(1f)
            ) {
                if (title != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .statusBarsPadding()
                            .height(topTitleHeight)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onMenuClick != null) {
                            IconButton(onMenuClick) {
                                Icon(
                                    Icons.Default.Menu,
                                    null,
                                    tint = Color.White
                                )
                            }; Spacer(Modifier.width(8.dp))
                        } else if (onBackClick != null) {
                            IconButton({ onBackClick() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    null,
                                    tint = Color.White
                                )
                            }
                        }
                        Text(title, color = Color.White, fontSize = AppConstants.HeaderFontSize, modifier = Modifier.weight(1f))
                        topBarActions()
                    }
                } else Spacer(Modifier.statusBarsPadding())

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topActionsHeight)
                        .background(AppConstants.BackgroundColor.copy(alpha = 0.95f))
                ) {
                    if (isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton({
                                    isSelectionMode =
                                        false; selectedUris.clear(); lastSelectedIndex = -1
                                }) { Icon(Icons.Default.Close, null) }
                                Text(
                                    "${selectedUris.size} 件選択中",
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isTrashMode) {
                                    TooltipWrapper("お気に入り") {
                                        IconButton({
                                            scope.launch {
                                                galleryState.repository.bulkUpdateFavorite(
                                                    selectedUris.toList(),
                                                    true
                                                ); isSelectionMode = false; selectedUris.clear()
                                            }
                                        }) { Icon(Icons.Default.Favorite, null, tint = Color.White) }
                                    }
                                    TooltipWrapper("タグ付け・編集") {
                                        IconButton({
                                            if (onBulkEdit != null) {
                                                onBulkEdit(selectedUris.toList()); isSelectionMode =
                                                    false; selectedUris.clear()
                                            }
                                        }) { Icon(Icons.Default.LocalOffer, null, tint = Color.White) }
                                    }
                                }

                                var showSelectionOverflow by remember { mutableStateOf(false) }
                                Box {
                                    IconButton({ showSelectionOverflow = true }) {
                                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                                    }
                                    DropdownMenu(
                                        expanded = showSelectionOverflow,
                                        onDismissRequest = { showSelectionOverflow = false },
                                        modifier = Modifier.background(Color.DarkGray)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("すべて選択", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.SelectAll, null, tint = Color.White) },
                                            onClick = {
                                                showSelectionOverflow = false
                                                selectedUris.clear()
                                                selectedUris.addAll(filteredList.map { it.uri })
                                            }
                                        )

                                        if (selectedUris.size == 1 && !isTrashMode) {
                                            val currentUri = selectedUris.first()
                                            // 現在のコンテキストがフォルダ内であれば、そのフォルダのサムネイルに設定
                                            // title がフォルダ名のことが多い
                                            if (title != null && title != "すべて" && !title.startsWith("すべて")) {
                                                DropdownMenuItem(
                                                    text = { Text("フォルダのサムネイルに設定", color = Color.White) },
                                                    leadingIcon = { Icon(Icons.Default.FolderSpecial, null, tint = Color.White) },
                                                    onClick = {
                                                        showSelectionOverflow = false
                                                        scope.launch {
                                                            galleryState.repository.updateFolderThumbnail(title, currentUri)
                                                            isSelectionMode = false
                                                            selectedUris.clear()
                                                            Toast.makeText(context, "サムネイルを設定しました", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else GalleryTopControlBar(galleryState, isFilterEnabled)
                }
            }
        }

        if (filteredList.isNotEmpty() && !isSelectionMode && !galleryState.isZooming) {
            var isPressed by remember { mutableStateOf(false) }
            val thumbWidth by animateDpAsState(if (isPressed) 12.dp else 4.dp, label = "scrollbarWidth")
            
            // スクロールバーの状態計算を効率化
            val scrollbarInfo by remember(gridState, filteredList.size, galleryState.groupingMode) {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val total = info.totalItemsCount
                    val visible = info.visibleItemsInfo
                    if (visible.isEmpty() || total == 0) null
                    else {
                        val first = visible.first()
                        val last = visible.last()
                        val range = (last.index - first.index + 1).coerceAtLeast(1)
                        val thumbHeightRatio = (range.toFloat() / total).coerceIn(0.1f, 1f)
                        val scrollPosRatio = (first.index.toFloat() / (total - range).coerceAtLeast(1)).coerceIn(0f, 1f)
                        
                        // 日付の取得（推定インデックスから）
                        val estIdx = (first.index.toFloat() / total * filteredList.size).toInt().coerceIn(0, filteredList.size - 1)
                        val dateAdded = filteredList[estIdx].dateAdded
                        
                        Triple(thumbHeightRatio, scrollPosRatio, dateAdded)
                    }
                }
            }
            
            scrollbarInfo?.let { (th, sp, dateAdded) ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(40.dp)
                        .padding(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding + 8.dp)
                        .align(Alignment.CenterEnd)
                        .zIndex(5f)
                        .pointerInput(gridState.layoutInfo.totalItemsCount) {
                            val total = gridState.layoutInfo.totalItemsCount
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    isPressed = true
                                    var tp = (down.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    scope.launch {
                                        gridState.scrollToItem((tp * total).toInt().coerceAtMost(total - 1))
                                    }
                                    drag(down.id) { change ->
                                        change.consume()
                                        tp = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        scope.launch {
                                            gridState.scrollToItem((tp * total).toInt().coerceAtMost(total - 1))
                                        }
                                    }
                                    isPressed = false
                                }
                            }
                        }
                ) {
                    if (isPressed) {
                        val dateText = remember(dateAdded, galleryState.groupingMode) {
                            val d = Date(dateAdded)
                            when (galleryState.groupingMode) {
                                GroupingMode.YEAR -> SimpleDateFormat("yyyy年", Locale.JAPAN).format(d)
                                GroupingMode.MONTH -> SimpleDateFormat("yyyy年MM月", Locale.JAPAN).format(d)
                                else -> SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN).format(d)
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 40.dp)
                                .offset(y = (maxHeight - maxHeight * th) * sp + (maxHeight * th / 2) - 16.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                dateText,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .offset(x = (-20).dp)
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .width(thumbWidth)
                            .height(maxHeight * th)
                            .align(Alignment.TopEnd)
                            .offset(
                                x = (-20.5).dp + (thumbWidth / 2),
                                y = (maxHeight - maxHeight * th) * sp
                            )
                            .clip(CircleShape)
                            .background(if (isPressed) Color.Cyan else Color.White.copy(alpha = 0.8f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridMediaItem(
    media: MediaData,
    metadata: com.example.gallery.data.local.entity.MediaMetadataSummary?,
    columnCount: Int,
    isHighlighted: Boolean,
    isSelected: Boolean,
    onImageClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbSize = remember(columnCount) {
        when {
            columnCount >= 28 -> 32 // 極小表示でも認識可能な最低限のサイズ
            columnCount >= 7 -> 128
            columnCount >= 4 -> 512
            else -> 1024
        }
    }

    // パフォーマンス優先：直接URIを渡し、Coilの内部キャッシュに任せる
    // カスタムサムネイル（ビデオ用など）が必要な場合は、CoilのFetcher側で対応するのが理想的だが、
    // ここではIOチェックを避け、必要最小限のパラメータでRequestを作成する
    val request = remember(media.uri, thumbSize, columnCount) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .size(thumbSize)
            .precision(coil.size.Precision.INEXACT) // 厳密なサイズ合わせを避けて高速化
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .apply {
                // グリッド密度が高い場合は軽量な設定を適用
                if (columnCount >= 4) {
                    bitmapConfig(Bitmap.Config.RGB_565) // メモリ消費とデコード負荷を半分に
                    allowHardware(true) // GPUメモリを直接使用
                    crossfade(false) // アニメーションを無効化してスクロールを優先
                } else {
                    crossfade(true)
                }

                // 動画の場合、あまりに小さいサムネイルではフレーム抽出を行わない（重いため）
                if (media.isVideo && columnCount <= 7) {
                    videoFrameMillis(1000)
                }
            }
            .build()
    }

    if (columnCount >= 28) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(0.2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onImageClick
                ),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (columnCount > 1) Modifier.aspectRatio(1f)
                    else Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                .padding(if (columnCount > 1) 0.5.dp else 0.dp)
                .then(
                    if (columnCount in 2..27) Modifier.clip(RoundedCornerShape(1.dp))
                    else Modifier
                )
                .then(
                    if (isHighlighted) Modifier.border(
                        2.dp,
                        Color.Cyan,
                        if (columnCount > 1) RoundedCornerShape(2.dp) else RoundedCornerShape(12.dp)
                    ) else Modifier
                )
                .combinedClickable(
                    onClick = onImageClick,
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (columnCount > 1) ContentScale.Crop else ContentScale.FillWidth
            )
            
            // アイコン類は必要な時のみ描画
            if (metadata?.isFavorite == true) {
                Icon(
                    Icons.Default.Favorite,
                    null,
                    tint = Color.Red,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(2.dp)
                        .size(if (columnCount > 7) 6.dp else 14.dp)
                )
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(2.dp)
                            .size(if (columnCount > 7) 12.dp else 20.dp)
                    )
                }
            }
            
            val label = remember(media.isGif, media.isVideo, media.duration) {
                when {
                    media.isGif -> "GIF"
                    media.isVideo -> formatDuration(media.duration)
                    else -> null
                }
            }
            
            if (label != null && columnCount <= 7) {
                Text(
                    label,
                    color = Color.White,
                    fontSize = 8.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val s = durationMs / 1000;
    val m = (s / 60) % 60;
    val h = s / 3600;
    val sec = s % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, sec) else String.format(
        "%02d:%02d",
        m,
        sec
    )
}

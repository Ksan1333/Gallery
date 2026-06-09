package com.example.gallery.ui.component

import android.graphics.Bitmap
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.state.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * グリッドに表示するアイテムの型定義
 */
sealed class GridItem {
    // 日付などの区切り見出し
    data class Header(
        val title: String, 
        val subtitle: String = ""
    ) : GridItem()
    
    // 画像・動画のデータ
    data class Media(
        val data: MediaData, 
        val label: String? = null, 
        val index: Int
    ) : GridItem()
    
    // ComposeのLazyGridで再利用性を高めるためのユニークキー
    val key: String get() = when (this) { 
        is Header -> "header_${title}_${subtitle}" 
        is Media -> data.uri 
    }
}

private const val TAG = "GalleryGridView"

/**
 * ギャラリーのメイングリッド表示コンポーネント
 * 1万件以上のデータを高速に、かつ滑らかに表示するための最適化が施されています。
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridView(
    imageList: List<MediaData> = emptyList(),
    pagingItems: LazyPagingItems<GridItem>? = null,
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
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    // 列数の選択肢 (28:年, 7:月, 4, 3, 1:日)
    val columnOptions = listOf(28, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }
    
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    // デバイスのアスペクト比。スマホの画面に収まる画像判定に使用
    val deviceAspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    // 列数（ズームレベル）に応じてグルーピング単位を自動的に切り替える
    LaunchedEffect(currentColumnIndex) {
        galleryState.groupingMode = when (columnOptions[currentColumnIndex]) {
            28 -> GroupingMode.YEAR
            7 -> GroupingMode.MONTH
            else -> GroupingMode.DAY
        }
    }

    // --- 選択モードの管理 ---
    var isSelectionMode by remember { mutableStateOf(false) }
    
    LaunchedEffect(isSelectionMode) { 
        galleryState.isSelectionMode = isSelectionMode 
    }
    
    // 外部（Activity等）に選択モードやズーム中の状態を伝える
    val currentOnSelectionModeChanged by rememberUpdatedState(onSelectionModeChanged)
    LaunchedEffect(galleryState.isZooming, isSelectionMode) { 
        currentOnSelectionModeChanged(isSelectionMode || galleryState.isZooming) 
    }
    
    // 選択されているURIを管理。SnapshotStateMapを使うことで、変更時に個々のアイテムだけが再描画される
    val selectedUris = remember { mutableStateMapOf<String, Boolean>() }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }
    
    LaunchedEffect(selectedUris.size) { 
        onSelectionChanged(selectedUris.keys.toList()) 
    }
    
    // 指定された場所へスクロールした際に強調表示するURI
    var highlightUri by remember { mutableStateOf<String?>(null) }

    // --- トップバーのレイアウト計算 ---
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalTopBarHeight = (if (title != null) AppConstants.HeaderHeight + topPadding else topPadding) + AppConstants.HeaderHeight
    val totalBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
    val totalTopBarHeightPx = with(LocalDensity.current) { totalTopBarHeight.toPx() }
    
    // トップバーをスクロールで隠すためのオフセット（ピクセル単位）
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    // 入れ子スクロールの挙動。グリッドを上にスクロールするとバーを隠し、下にスクロールすると出す
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = topBarOffsetHeightPx + delta
                
                // 選択モード中以外はトップバーを動かす。選択モード中は操作性を優先して固定。
                if (!isSelectionMode) {
                    topBarOffsetHeightPx = newOffset.coerceIn(-totalTopBarHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    // 外部からのクリア信号を受け取って選択をリセット
    LaunchedEffect(clearSelectionSignal) { 
        if (clearSelectionSignal > 0) { 
            isSelectionMode = false
            selectedUris.clear()
            lastSelectedIndex = -1 
        } 
    }

    // --- メタデータの同期 (お気に入り/レーティング等) ---
    var metadataMap by remember { 
        mutableStateOf<Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>>(emptyMap()) 
    }
    LaunchedEffect(galleryState.repository) {
        galleryState.repository.getAllMetadataSummaryFlow()
            .debounce(500)
            .distinctUntilChanged()
            .collect { list ->
                val startTime = System.currentTimeMillis()
                val newMap = withContext(Dispatchers.Default) { 
                    list.associateBy { it.uri } 
                }
                metadataMap = newMap
                Log.d(TAG, "Metadata update: processed ${list.size} items in ${System.currentTimeMillis() - startTime}ms")
            }
    }

    // --- フィルタリングとソート ---
    var sortedList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    // metadataMapをキーに追加。お気に入りやレーティングの変更をリストに即時反映させる
    LaunchedEffect(imageList, galleryState.mediaTypeFilter, galleryState.ageRatingFilter, galleryState.deviceFilter, galleryState.sortMode, galleryState.isAscending, metadataMap) {
        if (imageList.isEmpty()) { 
            sortedList = emptyList()
            return@LaunchedEffect 
        }
        
        val startTime = System.currentTimeMillis()
        withContext(Dispatchers.Default) {
            val sequence = imageList.asSequence()
                .filter { item ->
                    when (galleryState.mediaTypeFilter) {
                        MediaTypeFilter.ALL -> true
                        MediaTypeFilter.IMAGE -> !item.isVideo
                        MediaTypeFilter.VIDEO -> item.isVideo
                        MediaTypeFilter.GIF -> item.isGif
                    }
                }
                .filter { item ->
                    if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) {
                        true
                    } else {
                        val rating = metadataMap[item.uri]?.ageRating ?: "SFW"
                        when (galleryState.ageRatingFilter) {
                            AgeRatingFilter.SFW -> rating == "SFW"
                            AgeRatingFilter.R15 -> rating == "R15"
                            AgeRatingFilter.R18 -> rating == "R18"
                            else -> true
                        }
                    }
                }
                .filter { item ->
                    if (galleryState.deviceFilter == DeviceFilter.ALL) {
                        true
                    } else if (item.isVideo || item.width <= 0 || item.height <= 0) {
                        false 
                    } else {
                        val ratio = item.height.toFloat() / item.width.toFloat()
                        when (galleryState.deviceFilter) {
                            DeviceFilter.SMARTPHONE -> {
                                item.height > item.width && kotlin.math.abs(ratio - deviceAspectRatio) < 0.2f
                            }
                            DeviceFilter.PC -> {
                                item.width > item.height && (item.width.toFloat() / item.height.toFloat()) > 1.3f
                            }
                            else -> true
                        }
                    }
                }

            val sorted = when (galleryState.sortMode) {
                SortMode.DATE_ADDED -> sequence.sortedByDescending { it.dateAdded }
                SortMode.SIZE -> sequence.sortedBy { it.fileSize }
                SortMode.NAME -> sequence.sortedWith(
                    compareBy({ val c = it.fileName.firstOrNull(); c == null || !c.isDigit() }, { it.fileName })
                )
            }
            
            sortedList = if (galleryState.isAscending) sorted.toList().reversed() else sorted.toList()
            Log.d(TAG, "Filter/Sort finished: ${sortedList.size} items in ${System.currentTimeMillis() - startTime}ms")
        }
    }

    // --- グリッド用フラットリストの生成 ---
    var flatGridItems by remember { mutableStateOf<List<GridItem>>(emptyList()) }
    var mediaItemsOnly by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    
    LaunchedEffect(sortedList, galleryState.groupingMode, currentColumnIndex) {
        if (sortedList.isEmpty()) { 
            flatGridItems = emptyList()
            mediaItemsOnly = emptyList()
            return@LaunchedEffect 
        }
        
        val startTime = System.currentTimeMillis()
        withContext(Dispatchers.Default) {
            val is28 = columnOptions[currentColumnIndex] == 28
            val result = ArrayList<GridItem>(sortedList.size + 200)
            val mediaOnly = ArrayList<MediaData>(sortedList.size)
            
            if (galleryState.groupingMode == GroupingMode.NONE) {
                for (idx in sortedList.indices) {
                    val it = sortedList[idx]
                    val label = when { 
                        it.isGif -> "GIF"
                        it.isVideo -> formatDuration(it.duration)
                        else -> null 
                    }
                    result.add(GridItem.Media(it, label, idx))
                    mediaOnly.add(it) 
                }
            } else {
                val displaySdf = when (galleryState.groupingMode) {
                    GroupingMode.DAY -> SimpleDateFormat("yyyy年M月d日", Locale.JAPAN)
                    GroupingMode.MONTH -> SimpleDateFormat("yyyy年M月", Locale.JAPAN)
                    GroupingMode.YEAR -> SimpleDateFormat("yyyy年", Locale.JAPAN)
                    else -> null
                }
                
                if (displaySdf != null) {
                    var lastGroupKey = -1L
                    var lastHeaderText: String? = null
                    var currentGroupCount = 0
                    var mediaIdx = 0
                    val dateObj = Date()
                    
                    val div = when (galleryState.groupingMode) {
                        GroupingMode.DAY -> 86400000L
                        GroupingMode.MONTH -> 86400000L * 30L 
                        else -> 86400000L * 365L
                    }

                    for (media in sortedList) {
                        val currentGroupKey = media.dateAdded / div
                        if (currentGroupKey != lastGroupKey) {
                            dateObj.time = media.dateAdded
                            val headerText = displaySdf.format(dateObj)
                            if (headerText != lastHeaderText) {
                                result.add(GridItem.Header(headerText, currentGroupKey.toString()))
                                lastHeaderText = headerText
                            }
                            lastGroupKey = currentGroupKey
                            currentGroupCount = 0
                        }
                        
                        val label = when { 
                            media.isGif -> "GIF"
                            media.isVideo -> formatDuration(media.duration)
                            else -> null 
                        }
                        val gridMedia = GridItem.Media(media, label, mediaIdx)
                        
                        if (is28 && galleryState.groupingMode == GroupingMode.YEAR) {
                            if (currentGroupCount < 224) { 
                                result.add(gridMedia)
                                mediaOnly.add(media)
                                mediaIdx++
                                currentGroupCount++ 
                            }
                        } else { 
                            result.add(gridMedia)
                            mediaOnly.add(media)
                            mediaIdx++ 
                        }
                    }
                }
            }
            flatGridItems = result
            mediaItemsOnly = mediaOnly
            Log.d(TAG, "FlatGridItems generation: ${result.size} items in ${System.currentTimeMillis() - startTime}ms")
        }
    }

    // URI指定による自動スクロールの実行
    LaunchedEffect(scrollToUri) {
        if (scrollToUri != null) {
            val index = flatGridItems.indexOfFirst { item ->
                item is GridItem.Media && item.data.uri == scrollToUri 
            }
            if (index != -1) {
                gridState.scrollToItem(index)
                highlightUri = scrollToUri
                delay(2000)
                highlightUri = null
                onScrollConsumed()
            }
        }
    }

    val maxLineSpan = columnOptions[currentColumnIndex]
    val thumbSize = remember(maxLineSpan) {
        when {
            maxLineSpan >= 28 -> 48
            maxLineSpan >= 7 -> 160
            maxLineSpan >= 4 -> 512
            else -> 1024
        }
    }

    // --- プレロード (先読み) ---
    LaunchedEffect(gridState, flatGridItems, pagingItems, thumbSize) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .debounce(120) // 高速スクロール中の連発を抑制
            .collect { firstIndex ->
                val totalItems = pagingItems?.itemCount ?: flatGridItems.size
                if (totalItems == 0) return@collect
                
                val visibleCount = gridState.layoutInfo.visibleItemsInfo.size
                // 前後両方向をプレロード（特にスクロール方向を重視）
                val preloadStart = (firstIndex - maxLineSpan).coerceAtLeast(0)
                val preloadEnd = (firstIndex + visibleCount + (maxLineSpan * 6)).coerceAtMost(totalItems - 1)
                
                withContext(Dispatchers.IO) {
                    for (i in preloadStart..preloadEnd) {
                        val item = if (pagingItems != null) {
                            // PagingItemsの場合はpeekを使用して、不必要なロードをトリガーせずにキャッシュにあるか確認
                            pagingItems.peek(i)
                        } else {
                            flatGridItems.getOrNull(i)
                        }
                        
                        if (item is GridItem.Media) {
                            val request = ImageRequest.Builder(context)
                                .data(item.data.uri)
                                .size(thumbSize)
                                .precision(coil.size.Precision.INEXACT)
                                .bitmapConfig(Bitmap.Config.RGB_565)
                                .placeholderMemoryCacheKey(item.data.uri)
                                .build()
                            // enqueueはすでにキャッシュにある場合は何もしないので安全
                            context.imageLoader.enqueue(request)
                        }
                    }
                }
            }
    }

    // --- UIの描画 ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        GalleryGridContent(
            gridState = gridState,
            flatGridItems = if (pagingItems != null) emptyList() else flatGridItems,
            pagingItems = pagingItems,
            maxLineSpan = maxLineSpan,
            thumbSize = thumbSize,
            getTopBarOffset = { topBarOffsetHeightPx },
            totalTopBarHeight = totalTopBarHeight,
            totalBottomPadding = totalBottomPadding,
            isSelectionMode = isSelectionMode,
            selectedUris = selectedUris,
            highlightUri = highlightUri,
            metadataMap = metadataMap,
            mediaItemsOnly = mediaItemsOnly,
            onImageClick = onImageClick,
            onPageChangedInViewer = onPageChangedInViewer,
            onSelectionModeChanged = { isSelectionMode = it },
            onLastSelectedIndexChanged = { lastSelectedIndex = it },
            onZoomIn = { if (currentColumnIndex < columnOptions.size - 1) currentColumnIndex++ },
            onZoomOut = { if (currentColumnIndex > 0) currentColumnIndex-- },
            onZoomingStateChanged = { galleryState.isZooming = it }
        )

        if (!galleryState.isZooming) {
            GalleryTopSection(
                title = title,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedUris.size,
                getTopBarOffset = { topBarOffsetHeightPx },
                isTrashMode = isTrashMode,
                isFilterEnabled = isFilterEnabled,
                galleryState = galleryState,
                onBackClick = onBackClick,
                onMenuClick = onMenuClick,
                onCloseSelection = { 
                    isSelectionMode = false
                    selectedUris.clear()
                    lastSelectedIndex = -1 
                },
                onBulkFavorite = { 
                    scope.launch { 
                        galleryState.repository.bulkUpdateFavorite(selectedUris.keys.toList(), true)
                        isSelectionMode = false
                        selectedUris.clear() 
                    } 
                },
                onBulkDelete = {
                    scope.launch {
                        val uris = selectedUris.keys.toList()
                        if (isTrashMode) galleryState.repository.permanentlyDelete(uris) 
                        else galleryState.repository.moveToTrash(uris)
                        isSelectionMode = false
                        selectedUris.clear()
                    }
                },
                onBulkEdit = { uris: List<String> -> 
                    onBulkEdit?.invoke(uris)
                    isSelectionMode = false
                    selectedUris.clear() 
                },
                onUpdateThumbnail = { uri: String -> 
                    scope.launch { 
                        galleryState.repository.updateFolderThumbnail(title!!, uri)
                        isSelectionMode = false
                        selectedUris.clear()
                        Toast.makeText(context, "サムネイルを設定しました", Toast.LENGTH_SHORT).show() 
                    }
                },
                topBarActions = topBarActions,
                selectedUris = selectedUris.keys.toList()
            )
        }

        if (sortedList.isNotEmpty() && !isSelectionMode && !galleryState.isZooming) {
            GalleryScrollbar(
                gridState = gridState,
                totalTopBarHeight = totalTopBarHeight,
                totalBottomPadding = totalBottomPadding,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * グリッドレイアウトを担当する内部コンポーネント
 */
@Composable
private fun GalleryGridContent(
    gridState: LazyGridState,
    flatGridItems: List<GridItem>,
    pagingItems: LazyPagingItems<GridItem>?,
    maxLineSpan: Int,
    thumbSize: Int,
    getTopBarOffset: () -> Float,
    totalTopBarHeight: androidx.compose.ui.unit.Dp,
    totalBottomPadding: androidx.compose.ui.unit.Dp,
    isSelectionMode: Boolean,
    selectedUris: SnapshotStateMap<String, Boolean>,
    highlightUri: String?,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    mediaItemsOnly: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    onPageChangedInViewer: (String) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onLastSelectedIndexChanged: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomingStateChanged: (Boolean) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(maxLineSpan),
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 32.dp)
            .graphicsLayer { translationY = getTopBarOffset() }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        onZoomingStateChanged(true)
                        if (zoom > 1.2f) onZoomIn() else if (zoom < 0.8f) onZoomOut()
                    }
                }
            },
        contentPadding = PaddingValues(top = totalTopBarHeight, bottom = totalBottomPadding)
    ) {
        if (pagingItems != null) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.key },
                contentType = pagingItems.itemContentType { if (it is GridItem.Header) "header" else "media" },
                span = { index -> 
                    val item = pagingItems[index]
                    if (item is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                }
            ) { index ->
                val item = pagingItems[index] ?: return@items
                GridItemRenderer(
                    item = item,
                    isSelectionMode = isSelectionMode,
                    selectedUris = selectedUris,
                    metadataMap = metadataMap,
                    maxLineSpan = maxLineSpan,
                    thumbSize = thumbSize,
                    highlightUri = highlightUri,
                    mediaItemsOnly = mediaItemsOnly,
                    onImageClick = onImageClick,
                    onPageChangedInViewer = onPageChangedInViewer,
                    onSelectionModeChanged = onSelectionModeChanged,
                    onLastSelectedIndexChanged = onLastSelectedIndexChanged
                )
            }
        } else {
            items(
                items = flatGridItems,
                key = { item -> item.key },
                contentType = { item -> if (item is GridItem.Header) "header" else "media" },
                span = { item -> if (item is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
            ) { item ->
                GridItemRenderer(
                    item = item,
                    isSelectionMode = isSelectionMode,
                    selectedUris = selectedUris,
                    metadataMap = metadataMap,
                    maxLineSpan = maxLineSpan,
                    thumbSize = thumbSize,
                    highlightUri = highlightUri,
                    mediaItemsOnly = mediaItemsOnly,
                    onImageClick = onImageClick,
                    onPageChangedInViewer = onPageChangedInViewer,
                    onSelectionModeChanged = onSelectionModeChanged,
                    onLastSelectedIndexChanged = onLastSelectedIndexChanged
                )
            }
        }
    }
}

@Composable
private fun GridItemRenderer(
    item: GridItem,
    isSelectionMode: Boolean,
    selectedUris: SnapshotStateMap<String, Boolean>,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    maxLineSpan: Int,
    thumbSize: Int,
    highlightUri: String?,
    mediaItemsOnly: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    onPageChangedInViewer: (String) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onLastSelectedIndexChanged: (Int) -> Unit
) {
    when (item) {
        is GridItem.Header -> {
            Text(
                text = item.title, 
                color = Color.White, 
                modifier = Modifier.padding(16.dp), 
                fontSize = AppConstants.HeaderFontSize
            )
        }
        is GridItem.Media -> {
            MediaGridItemWrapper(
                item = item,
                isSelectionMode = isSelectionMode,
                isSelected = selectedUris.containsKey(item.data.uri),
                metadataMap = metadataMap,
                columnCount = maxLineSpan,
                thumbSize = thumbSize,
                isHighlighted = highlightUri == item.data.uri,
                mediaItemsOnly = mediaItemsOnly,
                onImageClick = { index, list ->
                    // Paging時はindexが-1なのでURIから検索
                    val actualIndex = if (index >= 0) index else list.indexOfFirst { it.uri == item.data.uri }
                    if (actualIndex >= 0) {
                        onImageClick(actualIndex, list)
                    } else {
                        // リストがまだロードされていない場合は、単一アイテムとして表示を試みる
                        onImageClick(0, listOf(item.data))
                    }
                },
                onPageChangedInViewer = onPageChangedInViewer,
                onSelectionToggle = {
                    if (selectedUris.containsKey(item.data.uri)) {
                        selectedUris.remove(item.data.uri)
                        if (selectedUris.isEmpty()) onSelectionModeChanged(false)
                    } else {
                        selectedUris[item.data.uri] = true
                        onLastSelectedIndexChanged(item.index)
                        onSelectionModeChanged(true)
                    }
                }
            )
        }
    }
}

/**
 * 個々のタイルの状態を管理するラッパー
 */
@Composable
private fun MediaGridItemWrapper(
    item: GridItem.Media,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    columnCount: Int,
    thumbSize: Int,
    isHighlighted: Boolean,
    mediaItemsOnly: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    onPageChangedInViewer: (String) -> Unit,
    onSelectionToggle: () -> Unit
) {
    val isFavorite = remember(metadataMap, item.data.uri) { 
        metadataMap[item.data.uri]?.isFavorite == true 
    }
    
    val currentOnImageClick by rememberUpdatedState(onImageClick)
    val currentOnPageChanged by rememberUpdatedState(onPageChangedInViewer)
    val currentMediaItemsOnly by rememberUpdatedState(mediaItemsOnly)
    
    MediaGridItem(
        media = item.data,
        label = item.label,
        isFavorite = isFavorite,
        columnCount = columnCount,
        thumbSize = thumbSize,
        isHighlighted = isHighlighted,
        isSelected = isSelected,
        onImageClick = {
            if (isSelectionMode) onSelectionToggle()
            else {
                currentOnImageClick(item.index, currentMediaItemsOnly)
                currentOnPageChanged(item.data.uri)
            }
        },
        onLongClick = onSelectionToggle
    )
}

/**
 * 実際に画像を表示し、クリック等の操作を受け付ける最小単位
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridItem(
    media: MediaData,
    label: String?,
    isFavorite: Boolean,
    columnCount: Int,
    thumbSize: Int,
    isHighlighted: Boolean,
    isSelected: Boolean,
    onImageClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // --- 画像リクエストの構築 ---
    // model を完全に固定することで、再描画時のロード再試行を最小限に抑える
    val model = remember(media.uri, thumbSize, columnCount) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .size(thumbSize)
            .precision(coil.size.Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .crossfade(false)
            .placeholderMemoryCacheKey(media.uri)
            .apply {
                if (media.isVideo && columnCount <= 4) {
                    videoFrameMillis(1000)
                }
            }
            .build()
    }
    
    val itemModifier = remember(isHighlighted, isSelected, columnCount) {
        Modifier
            .fillMaxWidth()
            .then(if (columnCount > 1) Modifier.aspectRatio(1f) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)))
            .padding(if (columnCount > 1) 0.5.dp else 0.dp)
            .then(if (columnCount in 2..27) Modifier.clip(RoundedCornerShape(1.dp)) else Modifier)
            .then(if (isHighlighted) Modifier.border(2.dp, Color.Cyan, if (columnCount > 1) RoundedCornerShape(2.dp) else RoundedCornerShape(12.dp)) else Modifier)
    }

    val clickableModifier = if (columnCount >= 7) Modifier.clickable(onClick = onImageClick)
    else Modifier.combinedClickable(onClick = onImageClick, onLongClick = onLongClick)

    Box(modifier = itemModifier.background(Color(0xFF2C2C2C)).then(clickableModifier)) {
        AsyncImage(
            model = model,
            contentDescription = null, 
            modifier = Modifier.fillMaxSize(), 
            contentScale = if (columnCount > 1) ContentScale.Crop else ContentScale.FillWidth
        )
        
        if (columnCount < 28) {
            if (isFavorite) {
                Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.align(Alignment.BottomStart).padding(2.dp).size(if (columnCount > 7) 6.dp else 14.dp)) 
            }
            if (isSelected) { 
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)), contentAlignment = Alignment.TopEnd) { 
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(2.dp).size(if (columnCount > 7) 12.dp else 20.dp)) 
                } 
            }
            if (label != null && columnCount <= 7) { 
                Text(label, color = Color.White, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp)).padding(horizontal = 2.dp, vertical = 1.dp)) 
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val s = durationMs / 1000
    val m = (s / 60) % 60
    val h = s / 3600
    val sec = s % 60
    return buildString {
        if (h > 0) { append(h).append(':'); if (m < 10) append('0') }
        append(m).append(':')
        if (sec < 10) append('0')
        append(sec)
    }
}

/**
 * ギャラリーの上部セクション
 */
@Composable
private fun GalleryTopSection(
    title: String?,
    isSelectionMode: Boolean,
    selectedCount: Int,
    getTopBarOffset: () -> Float,
    isTrashMode: Boolean,
    isFilterEnabled: Boolean,
    galleryState: GalleryState,
    onBackClick: (() -> Unit)?,
    onMenuClick: (() -> Unit)?,
    onCloseSelection: () -> Unit,
    onBulkFavorite: () -> Unit,
    onBulkDelete: () -> Unit,
    onBulkEdit: (List<String>) -> Unit,
    onUpdateThumbnail: (String) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit,
    selectedUris: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = getTopBarOffset() }.zIndex(1f)) {
        if (title != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(AppConstants.HeaderHeight).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMenuClick != null) { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = Color.White) } }
                else if (onBackClick != null) { IconButton(onClick = { onBackClick() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } }
                Text(text = title, color = Color.White, fontSize = AppConstants.HeaderFontSize, modifier = Modifier.weight(1f))
                topBarActions()
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(AppConstants.HeaderHeight).background(AppConstants.BackgroundColor.copy(alpha = 0.95f))) {
            if (isSelectionMode) {
                SelectionModeBar(selectedCount, isTrashMode, onCloseSelection, onBulkFavorite, onBulkDelete, { onBulkEdit(selectedUris) }, { if (selectedUris.isNotEmpty()) onUpdateThumbnail(selectedUris.first()) }, title != null && title != "すべて" && selectedUris.size == 1)
            } else {
                GalleryTopControlBar(galleryState, isFilterEnabled)
            }
        }
    }
}

/**
 * 選択モード中に表示されるアクションバー
 */
@Composable
private fun SelectionModeBar(
    selectedCount: Int,
    isTrashMode: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onSetThumbnail: () -> Unit,
    canSetThumbnail: Boolean
) {
    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            Text(text = "${selectedCount} 件選択中", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isTrashMode) { IconButton(onClick = onFavorite) { Icon(Icons.Default.Favorite, null, tint = Color.Red) } }
            var showOverflow by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showOverflow = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }, modifier = Modifier.background(Color.DarkGray)) {
                    DropdownMenuItem(text = { Text(if (isTrashMode) "完全に削除" else "ゴミ箱へ", color = Color.White) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.White) }, onClick = { showOverflow = false; onDelete() })
                    DropdownMenuItem(text = { Text("一括編集", color = Color.White) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White) }, onClick = { showOverflow = false; onEdit() })
                    if (canSetThumbnail) {
                        DropdownMenuItem(text = { Text("フォルダのサムネイルに設定", color = Color.White) }, leadingIcon = { Icon(Icons.Default.FolderSpecial, null, tint = Color.White) }, onClick = { showOverflow = false; onSetThumbnail() })
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryScrollbar(
    gridState: LazyGridState,
    totalTopBarHeight: androidx.compose.ui.unit.Dp,
    totalBottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val thumbWidth by animateDpAsState(targetValue = if (isPressed) 12.dp else 4.dp, label = "scrollbarWidth")
    val thumbHeightRatioState = remember(gridState) { derivedStateOf { val info = gridState.layoutInfo; val total = info.totalItemsCount; if (total == 0) 0.1f else (info.visibleItemsInfo.size.toFloat() / total).coerceIn(0.1f, 1f) } }
    val scrollPositionState = remember(gridState) { derivedStateOf { val info = gridState.layoutInfo; val total = info.totalItemsCount; if (info.visibleItemsInfo.isEmpty() || total == 0) 0f else (info.visibleItemsInfo.first().index.toFloat() / (total - info.visibleItemsInfo.size).coerceAtLeast(1)).coerceIn(0f, 1f) } }
    var containerHeight by remember { mutableIntStateOf(0) }
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Box(modifier = modifier.fillMaxHeight().width(32.dp).padding(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding + 8.dp).onSizeChanged { containerHeight = it.height }.zIndex(5f).pointerInput(gridState.layoutInfo.totalItemsCount) { 
        val total = gridState.layoutInfo.totalItemsCount
        if (total == 0) return@pointerInput
        awaitPointerEventScope { 
            while (true) { 
                val down = awaitFirstDown(); isPressed = true; var touchPos = (down.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                scrollJob?.cancel(); scrollJob = scope.launch { gridState.scrollToItem((touchPos * total).toInt().coerceAtMost(total - 1)) }
                drag(down.id) { change -> change.consume(); touchPos = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    scrollJob?.cancel(); scrollJob = scope.launch { gridState.scrollToItem((touchPos * total).toInt().coerceAtMost(total - 1)) }
                }; isPressed = false 
            } 
        } 
    }) {
        Box(modifier = Modifier.width(thumbWidth).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer { 
            val scrollPos = scrollPositionState.value; val heightRatio = thumbHeightRatioState.value; val currentThumbHeight = containerHeight * heightRatio
            translationY = (containerHeight - currentThumbHeight) * scrollPos; translationX = (-4.dp).toPx(); scaleY = heightRatio; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) 
        }.clip(CircleShape).background(if (isPressed) Color.Cyan else Color.White.copy(alpha = 0.8f)))
    }
}

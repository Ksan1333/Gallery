package com.example.gallery.ui.component

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.state.*
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * グリッドに表示するアイテムの型定義。
 */
sealed class GridItem {
    // 日付などの区切り見出し。
    data class Header(
        val title: String,
        val subtitle: String = ""
    ) : GridItem()

    // 画像・動画のデータ。
    data class Media(
        val data: MediaData,
        val label: String? = null,
        val index: Int
    ) : GridItem()

    // LazyGrid の再利用性を高めるためのユニークキー。
    val key: String get() = when (this) {
        is Header -> "header_${title}_${subtitle}"
        is Media -> data.uri
    }
}

private data class GridBuildResult(
    val gridItems: List<GridItem>,
    val mediaItems: List<MediaData>,
    val denseLogMessage: String? = null
)



private const val DENSE28_TRACE = "GALLERY_DENSE28_TRACE"
private const val SCROLL_RESTORE_TRACE = "GALLERY_SCROLL_RESTORE_TRACE"
private const val DENSE28_ROWS_PER_YEAR = 6
private const val DENSE28_THUMB_SIZE = 48
private const val DENSE28_THUMB_LOAD_ROWS = 12
private val EmptyMetadataMap = emptyMap<String, com.example.gallery.data.local.entity.MediaMetadataSummary>()

private fun logDense28Trace(message: String) {
    Log.d(DENSE28_TRACE, "$DENSE28_TRACE $message")
}

private fun logScrollRestoreTrace(message: String) {
    Log.d(SCROLL_RESTORE_TRACE, "$SCROLL_RESTORE_TRACE $message")
}

private fun buildScrollbarDateLabels(gridItems: List<GridItem>): List<String?> {
    if (gridItems.isEmpty()) return emptyList()

    val formatter = SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5", Locale.JAPAN)
    val date = Date()
    val labels = MutableList<String?>(gridItems.size) { null }

    gridItems.forEachIndexed { index, item ->
        if (item is GridItem.Media) {
            date.time = item.data.dateAdded
            labels[index] = formatter.format(date)
        }
    }

    var nextLabel: String? = null
    for (index in labels.indices.reversed()) {
        val label = labels[index]
        if (label == null) {
            labels[index] = nextLabel
        } else {
            nextLabel = label
        }
    }

    var previousLabel: String? = null
    for (index in labels.indices) {
        val label = labels[index]
        if (label == null) {
            labels[index] = previousLabel
        } else {
            previousLabel = label
        }
    }

    return labels
}

/**
 * ギャラリーのメイングリッド表示コンポーネント。
 * 大量データを軽く表示するための最適化を含む。
 */
@OptIn(
    kotlinx.coroutines.FlowPreview::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    ExperimentalMaterial3Api::class
)
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
    centerScrollToUri: Boolean = false,
    isFilterEnabled: Boolean = true,
    onMenuClick: (() -> Unit)? = null,
    onPageChangedInViewer: (String) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onBulkMove: ((List<String>) -> Unit)? = null,
    onSelectionChanged: (List<String>) -> Unit = {},
    isTrashMode: Boolean = false,
    onScrollConsumed: () -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    restoreScrollIndex: Int = initialScrollIndex,
    restoreScrollOffset: Int = initialScrollOffset,
    restoreScrollUri: String? = null,
    restoreScrollRequestKey: Int = 0,
    onScrollPositionChanged: (Int, Int) -> Unit = { _, _ -> },
    onScrollAnchorChanged: (Int, Int, String?) -> Unit = { _, _, _ -> },
    onScrollRestored: (Int) -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    showTopSection: Boolean = true,
    selectOnTap: Boolean = false
) {
    // 列数の選択肢 (28:年, 7:月, 4/3/1:日)
    val columnOptions = listOf(28, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }

    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialScrollIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = initialScrollOffset.coerceAtLeast(0)
    )
    val currentOnScrollPositionChanged by rememberUpdatedState(onScrollPositionChanged)
    val currentOnScrollAnchorChanged by rememberUpdatedState(onScrollAnchorChanged)
    val currentOnScrollRestored by rememberUpdatedState(onScrollRestored)
    var isRestoringScroll by remember { mutableStateOf(false) }

    suspend fun waitForRestoreTargetIndex(source: String, requestedIndex: Int): Int? {
        val safeIndex = requestedIndex.coerceAtLeast(0)
        val startTime = System.currentTimeMillis()
        val completed = withTimeoutOrNull(2500) {
            while (gridState.layoutInfo.totalItemsCount <= safeIndex) {
                delay(50)
            }
            true
        } ?: false
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (totalItems <= 0) {
            logScrollRestoreTrace(
                "grid_restore_wait_no_target source=$source requested=$requestedIndex safe=$safeIndex " +
                    "total=$totalItems first=${gridState.firstVisibleItemIndex} " +
                    "elapsedMs=${System.currentTimeMillis() - startTime} timeout=${!completed}"
            )
            return null
        }
        val targetIndex = safeIndex.coerceIn(0, totalItems - 1)
        logScrollRestoreTrace(
            "grid_restore_wait_done source=$source requested=$requestedIndex safe=$safeIndex " +
                "target=$targetIndex total=$totalItems visible=${gridState.layoutInfo.visibleItemsInfo.size} " +
                "first=${gridState.firstVisibleItemIndex} offset=${gridState.firstVisibleItemScrollOffset} " +
                "elapsedMs=${System.currentTimeMillis() - startTime} timeout=${!completed} " +
                "clamped=${targetIndex != safeIndex} columns=${columnOptions[currentColumnIndex]}"
        )
        return targetIndex
    }

    var hasRestoredInitialScroll by remember(initialScrollIndex, initialScrollOffset) {
        mutableStateOf(initialScrollIndex <= 0 && initialScrollOffset <= 0)
    }
    LaunchedEffect(gridState, initialScrollIndex, initialScrollOffset) {
        if (hasRestoredInitialScroll) return@LaunchedEffect
        isRestoringScroll = true
        logScrollRestoreTrace(
            "grid_initial_restore_start requested=$initialScrollIndex offset=$initialScrollOffset " +
                "firstBefore=${gridState.firstVisibleItemIndex} totalBefore=${gridState.layoutInfo.totalItemsCount} " +
                "columns=${columnOptions[currentColumnIndex]}"
        )
        try {
            val targetIndex = waitForRestoreTargetIndex("initial", initialScrollIndex)
            if (targetIndex != null) {
                gridState.scrollToItem(targetIndex, initialScrollOffset.coerceAtLeast(0))
                logScrollRestoreTrace(
                    "grid_initial_restore_scroll requested=$initialScrollIndex target=$targetIndex " +
                        "offset=$initialScrollOffset firstNow=${gridState.firstVisibleItemIndex} " +
                        "total=${gridState.layoutInfo.totalItemsCount}"
                )
            }
            delay(100)
            hasRestoredInitialScroll = true
            logScrollRestoreTrace(
                "grid_initial_restore_done requested=$initialScrollIndex firstAfter=${gridState.firstVisibleItemIndex} " +
                    "offsetAfter=${gridState.firstVisibleItemScrollOffset} total=${gridState.layoutInfo.totalItemsCount}"
            )
            currentOnScrollRestored(0)
        } finally {
            isRestoringScroll = false
        }
    }
    LaunchedEffect(gridState, restoreScrollRequestKey) {
        if (restoreScrollRequestKey <= 0) return@LaunchedEffect
        isRestoringScroll = true
        logScrollRestoreTrace(
            "grid_restore_request_start key=$restoreScrollRequestKey requested=$restoreScrollIndex " +
                "offset=$restoreScrollOffset firstBefore=${gridState.firstVisibleItemIndex} " +
                "offsetBefore=${gridState.firstVisibleItemScrollOffset} totalBefore=${gridState.layoutInfo.totalItemsCount} " +
                "columns=${columnOptions[currentColumnIndex]}"
        )
        try {
            val targetIndex = waitForRestoreTargetIndex("request_$restoreScrollRequestKey", restoreScrollIndex)
            if (targetIndex != null) {
                gridState.scrollToItem(targetIndex, restoreScrollOffset.coerceAtLeast(0))
                logScrollRestoreTrace(
                    "grid_restore_request_scroll key=$restoreScrollRequestKey requested=$restoreScrollIndex " +
                        "target=$targetIndex offset=$restoreScrollOffset firstNow=${gridState.firstVisibleItemIndex} " +
                        "total=${gridState.layoutInfo.totalItemsCount}"
                )
            } else {
                logScrollRestoreTrace(
                    "grid_restore_request_no_target key=$restoreScrollRequestKey requested=$restoreScrollIndex " +
                        "total=${gridState.layoutInfo.totalItemsCount} first=${gridState.firstVisibleItemIndex}"
                )
            }
            delay(100)
            logScrollRestoreTrace(
                "grid_restore_request_done key=$restoreScrollRequestKey requested=$restoreScrollIndex " +
                    "firstAfter=${gridState.firstVisibleItemIndex} offsetAfter=${gridState.firstVisibleItemScrollOffset} " +
                    "total=${gridState.layoutInfo.totalItemsCount} uriHash=${restoreScrollUri?.hashCode()}"
            )
            if (restoreScrollUri == null) {
                currentOnScrollRestored(restoreScrollRequestKey)
            } else {
                logScrollRestoreTrace(
                    "grid_restore_request_wait_uri key=$restoreScrollRequestKey uriHash=${restoreScrollUri.hashCode()} " +
                        "first=${gridState.firstVisibleItemIndex} total=${gridState.layoutInfo.totalItemsCount}"
                )
            }
        } finally {
            isRestoringScroll = false
        }
    }
    // Track if user is actively using scrollbar to suppress heavy work (bounds updates, prefetch, etc.)
    var isScrollbarDragging by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sequentialImageDispatcher = remember { Dispatchers.IO.limitedParallelism(1) }
    val gridImageLoader = remember(context, sequentialImageDispatcher) {
        // Dedicated loader for grid thumbnails:
        // - Include VideoFrameDecoder so videos show proper frame thumbnails (not black).
        // - Do NOT include Gif/ImageDecoder so GIFs are static thumbnails (first frame).
        ImageLoader.Builder(context)
            .dispatcher(sequentialImageDispatcher)
            .interceptorDispatcher(sequentialImageDispatcher)
            .crossfade(false)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    // デバイスのアスペクト比。端末の画面に収まる画像判定に使う。
    val deviceAspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    // 列数に応じてグルーピング単位を自動で切り替える。
    LaunchedEffect(currentColumnIndex) {
        val nextGroupingMode = when (columnOptions[currentColumnIndex]) {
            28 -> GroupingMode.YEAR
            7 -> GroupingMode.MONTH
            else -> GroupingMode.DAY
        }
        galleryState.groupingMode = nextGroupingMode
        if (columnOptions[currentColumnIndex] >= 28) {
            logDense28Trace(
                "mode_enter columns=${columnOptions[currentColumnIndex]} grouping=$nextGroupingMode " +
                    "rowsPerYear=$DENSE28_ROWS_PER_YEAR maxItemsPerYear=${columnOptions[currentColumnIndex] * DENSE28_ROWS_PER_YEAR} " +
                    "thumbSize=$DENSE28_THUMB_SIZE thumbLoadRows=$DENSE28_THUMB_LOAD_ROWS"
            )
        }
    }

    // --- 選択モードの管理 ---
    var isSelectionMode by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        galleryState.isSelectionMode = isSelectionMode
    }

    // Activity へ選択モードやズーム中の状態を伝える。
    val currentOnSelectionModeChanged by rememberUpdatedState(onSelectionModeChanged)
    LaunchedEffect(galleryState.isZooming, isSelectionMode) {
        currentOnSelectionModeChanged(isSelectionMode || galleryState.isZooming)
    }

    LaunchedEffect(galleryState.isZooming) {
        if (galleryState.isZooming) {
            delay(250)
            galleryState.isZooming = false
        }
    }

    // 選択済み URI を管理する。SnapshotStateMap で変更時の再描画を絞る。
    val selectedUris = remember { mutableStateMapOf<String, Boolean>() }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(selectedUris.size) {
        onSelectionChanged(selectedUris.keys.toList())
    }

    // 指定位置へスクロールした際に強調表示する URI。
    var highlightUri by remember { mutableStateOf<String?>(null) }

    // --- トップバーのレイアウト計算 ---
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalTopBarHeight = if (showTopSection) {
        (if (title != null) AppConstants.HeaderHeight + topPadding else topPadding) + AppConstants.HeaderHeight
    } else {
        0.dp
    }
    val totalBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
    val totalTopBarHeightPx = with(LocalDensity.current) { totalTopBarHeight.toPx() }

    // スクロールでトップバーを隠すためのオフセット。
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    // 入れ子スクロールの挙動。上方向ではバーを隠し、下方向では出す。
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = topBarOffsetHeightPx + delta

                // 選択モード中は操作性を優先してトップバーを固定する。
                if (!isSelectionMode) {
                    topBarOffsetHeightPx = newOffset.coerceIn(-totalTopBarHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    // 外部からのクリア信号を受け取って選択をリセットする。
    LaunchedEffect(clearSelectionSignal) {
        if (clearSelectionSignal > 0) {
            isSelectionMode = false
            selectedUris.clear()
            lastSelectedIndex = -1
        }
    }

    // --- メタデータの同期 (お気に入り・レーティングなど) ---
    var metadataMap by remember {
        mutableStateOf<Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>>(emptyMap())
    }
    LaunchedEffect(galleryState.repository) {
        galleryState.repository.getAllMetadataSummaryFlow()
            .debounce(1000)  // スクロール中の更新を減らすため、反映を少し遅らせる。
            .distinctUntilChanged()
            .collect { list ->
                val newMap = withContext(Dispatchers.Default) {
                    list.associateBy { it.uri }
                }
                metadataMap = newMap
            }
    }

    // --- フィルタリングとソート ---
    var sortedList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    // お気に入りやレーティングの変更をリストへ即時反映する。
    val metadataForFiltering = if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) {
        EmptyMetadataMap
    } else {
        metadataMap
    }

    LaunchedEffect(imageList, galleryState.mediaTypeFilter, galleryState.ageRatingFilter, galleryState.deviceFilter, galleryState.sortMode, galleryState.isAscending, metadataForFiltering) {
        if (imageList.isEmpty()) {
            sortedList = emptyList()
            return@LaunchedEffect
        }

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
                        val rating = metadataForFiltering[item.uri]?.ageRating ?: "SFW"
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
        }
    }

    // --- グリッド用フラットリストの生成 ---
    var flatGridItems by remember { mutableStateOf<List<GridItem>>(emptyList()) }
    var mediaItemsOnly by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var flatGridBuiltColumns by remember { mutableIntStateOf(columnOptions[currentColumnIndex]) }
    val currentFlatGridItems by rememberUpdatedState(flatGridItems)

    LaunchedEffect(sortedList, currentColumnIndex) {
        if (sortedList.isEmpty()) {
            flatGridItems = emptyList()
            mediaItemsOnly = emptyList()
            flatGridBuiltColumns = columnOptions[currentColumnIndex]
            return@LaunchedEffect
        }

        val buildColumnIndex = currentColumnIndex
        val buildColumns = columnOptions[buildColumnIndex]
        val sortedSnapshot = sortedList
        val startTime = System.currentTimeMillis()
        val buildResult = withContext(Dispatchers.Default) {
            val currentColumns = buildColumns
            val effectiveGroupingMode = when (currentColumns) {
                28 -> GroupingMode.YEAR
                7 -> GroupingMode.MONTH
                else -> GroupingMode.DAY
            }
            val is28 = currentColumns == 28
            val isDenseYear = is28 && effectiveGroupingMode == GroupingMode.YEAR
            val denseRowsPerYear = DENSE28_ROWS_PER_YEAR
            val denseMaxItemsPerYear = currentColumns * denseRowsPerYear

            if (isDenseYear) {
                // 28-column overview keeps only six rows per calendar year.
                val result = ArrayList<GridItem>()
                val mediaOnly = ArrayList<MediaData>()
                var lastHeaderText: String? = null
                var currentGroupCount = 0
                var yearCount = 0
                var skippedByLimit = 0
                val dateObj = Date()
                val displaySdf = SimpleDateFormat("yyyy蟷ｴ", Locale.JAPAN)

                for (media in sortedSnapshot) {
                    dateObj.time = media.dateAdded
                    val headerText = displaySdf.format(dateObj)
                    if (headerText != lastHeaderText) {
                        result.add(GridItem.Header(headerText, headerText))
                        lastHeaderText = headerText
                        currentGroupCount = 0
                        yearCount++
                    }
                    if (currentGroupCount < denseMaxItemsPerYear) {
                        val gridMedia = GridItem.Media(media, label = null, index = mediaOnly.size)
                        result.add(gridMedia)
                        mediaOnly.add(media)
                        currentGroupCount++
                    } else {
                        skippedByLimit++
                    }
                }
                GridBuildResult(
                    gridItems = result,
                    mediaItems = mediaOnly,
                    denseLogMessage = "build_done sorted=${sortedSnapshot.size} years=$yearCount headers=${result.size - mediaOnly.size} " +
                        "mediaItems=${mediaOnly.size} gridItems=${result.size} rowsPerYear=$denseRowsPerYear " +
                        "maxItemsPerYear=$denseMaxItemsPerYear skippedByLimit=$skippedByLimit " +
                        "elapsedMs=${System.currentTimeMillis() - startTime}"
                )
            } else {
                val resultCapacity = sortedSnapshot.size + 200
                val mediaCapacity = sortedSnapshot.size
                val result = ArrayList<GridItem>(resultCapacity)
                val mediaOnly = ArrayList<MediaData>(mediaCapacity)

                if (effectiveGroupingMode == GroupingMode.NONE) {
                    for (idx in sortedSnapshot.indices) {
                        val it = sortedSnapshot[idx]
                        val label = when {
                            it.isGif -> "GIF"
                            it.isVideo -> formatDuration(it.duration)
                            else -> null
                        }
                        result.add(GridItem.Media(it, label, idx))
                        mediaOnly.add(it)
                    }
                } else {
                    val displaySdf = when (effectiveGroupingMode) {
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

                        val div = when (effectiveGroupingMode) {
                            GroupingMode.DAY -> 86400000L
                            GroupingMode.MONTH -> 86400000L * 30L
                            else -> 86400000L * 365L
                        }

                        for (media in sortedSnapshot) {
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
                            result.add(gridMedia)
                            mediaOnly.add(media)
                            mediaIdx++
                        }
                    }
                }
                GridBuildResult(
                    gridItems = result,
                    mediaItems = mediaOnly
                )
            }
        }
        if (buildColumnIndex != currentColumnIndex || sortedSnapshot !== sortedList) {
            return@LaunchedEffect
        }
        flatGridItems = buildResult.gridItems
        mediaItemsOnly = buildResult.mediaItems
        flatGridBuiltColumns = buildColumns
        buildResult.denseLogMessage?.let(::logDense28Trace)
    }

    // URI 指定による自動スクロールを実行する。
    fun gridItemForScrollAnchor(index: Int): GridItem? {
        if (index < 0) return null
        val paging = pagingItems
        if (paging != null) {
            val pagingItem = if (index < paging.itemCount) {
                runCatching { paging.peek(index) }.getOrNull()
            } else {
                null
            }
            return pagingItem ?: currentFlatGridItems.getOrNull(index)
        }
        return currentFlatGridItems.getOrNull(index)
    }

    fun firstVisibleMediaUriForScrollAnchor(): String? {
        return gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { itemInfo ->
            (gridItemForScrollAnchor(itemInfo.index) as? GridItem.Media)?.data?.uri
        }
    }

    fun indexOfRestoreUri(uri: String): Int {
        return currentFlatGridItems.indexOfFirst { item ->
            item is GridItem.Media && item.data.uri == uri
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            Triple(
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset,
                gridState.layoutInfo.totalItemsCount
            )
        }
            .distinctUntilChanged()
            .debounce(300)
            .collect { (index, offset, totalItems) ->
                val anchorUri = firstVisibleMediaUriForScrollAnchor()
                if (hasRestoredInitialScroll && !isRestoringScroll && totalItems > 0) {
                    logScrollRestoreTrace(
                        "grid_position_emit index=$index offset=$offset total=$totalItems " +
                            "anchorUriHash=${anchorUri?.hashCode()} columns=${columnOptions[currentColumnIndex]}"
                    )
                    currentOnScrollPositionChanged(index, offset)
                    currentOnScrollAnchorChanged(index, offset, anchorUri)
                } else if (isRestoringScroll) {
                    logScrollRestoreTrace(
                        "grid_position_emit_suppressed reason=restoring index=$index offset=$offset " +
                            "total=$totalItems anchorUriHash=${anchorUri?.hashCode()} columns=${columnOptions[currentColumnIndex]}"
                    )
                }
            }
    }

    LaunchedEffect(gridState, restoreScrollRequestKey, restoreScrollUri) {
        val uri = restoreScrollUri ?: return@LaunchedEffect
        if (restoreScrollRequestKey <= 0) return@LaunchedEffect

        isRestoringScroll = true
        val startTime = System.currentTimeMillis()
        try {
            val ready = withTimeoutOrNull(2500) {
                while (true) {
                    val index = indexOfRestoreUri(uri)
                    if (index >= 0 && gridState.layoutInfo.totalItemsCount > index) {
                        return@withTimeoutOrNull index
                    }
                    delay(50)
                }
            }

            if (ready == null) {
                logScrollRestoreTrace(
                    "grid_restore_uri_no_target key=$restoreScrollRequestKey uriHash=${uri.hashCode()} " +
                        "flatItems=${flatGridItems.size} total=${gridState.layoutInfo.totalItemsCount} " +
                        "elapsedMs=${System.currentTimeMillis() - startTime}"
                )
            } else {
                repeat(16) { step ->
                    val index = indexOfRestoreUri(uri)
                    if (index >= 0 && gridState.layoutInfo.totalItemsCount > index) {
                        gridState.scrollToItem(index, restoreScrollOffset.coerceAtLeast(0))
                        logScrollRestoreTrace(
                            "grid_restore_uri_step key=$restoreScrollRequestKey step=$step uriHash=${uri.hashCode()} " +
                                "target=$index firstNow=${gridState.firstVisibleItemIndex} " +
                                "offsetNow=${gridState.firstVisibleItemScrollOffset} total=${gridState.layoutInfo.totalItemsCount}"
                        )
                    }
                    delay(180)
                }
                logScrollRestoreTrace(
                    "grid_restore_uri_done key=$restoreScrollRequestKey uriHash=${uri.hashCode()} " +
                        "firstAfter=${gridState.firstVisibleItemIndex} offsetAfter=${gridState.firstVisibleItemScrollOffset} " +
                        "total=${gridState.layoutInfo.totalItemsCount} elapsedMs=${System.currentTimeMillis() - startTime}"
                )
            }
        } finally {
            isRestoringScroll = false
            currentOnScrollRestored(restoreScrollRequestKey)
        }
    }

    LaunchedEffect(scrollToUri) {
        if (scrollToUri != null) {
            logScrollRestoreTrace(
                "grid_scroll_to_uri_start uriHash=${scrollToUri.hashCode()} center=$centerScrollToUri " +
                    "flatItems=${flatGridItems.size} firstBefore=${gridState.firstVisibleItemIndex} " +
                    "totalBefore=${gridState.layoutInfo.totalItemsCount}"
            )
            val index = flatGridItems.indexOfFirst { item ->
                item is GridItem.Media && item.data.uri == scrollToUri
            }
            if (index != -1) {
                gridState.scrollToItem(index)
                logScrollRestoreTrace(
                    "grid_scroll_to_uri_found uriHash=${scrollToUri.hashCode()} index=$index " +
                        "firstNow=${gridState.firstVisibleItemIndex} total=${gridState.layoutInfo.totalItemsCount}"
                )
                if (centerScrollToUri) {
                    delay(16)
                    val itemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    if (itemInfo != null) {
                        val itemCenter = itemInfo.offset.y + itemInfo.size.height / 2f
                        val viewportCenter = (gridState.layoutInfo.viewportStartOffset + gridState.layoutInfo.viewportEndOffset) / 2f
                        gridState.scrollBy(itemCenter - viewportCenter)
                        logScrollRestoreTrace(
                            "grid_scroll_to_uri_centered uriHash=${scrollToUri.hashCode()} index=$index " +
                                "firstAfter=${gridState.firstVisibleItemIndex} " +
                                "offsetAfter=${gridState.firstVisibleItemScrollOffset}"
                        )
                    } else {
                        logScrollRestoreTrace(
                            "grid_scroll_to_uri_center_missing_item uriHash=${scrollToUri.hashCode()} index=$index " +
                                "visible=${gridState.layoutInfo.visibleItemsInfo.size}"
                        )
                    }
                }
                highlightUri = scrollToUri
                delay(2000)
                highlightUri = null
                onScrollConsumed()
                logScrollRestoreTrace(
                    "grid_scroll_to_uri_consumed uriHash=${scrollToUri.hashCode()} index=$index " +
                        "firstAfter=${gridState.firstVisibleItemIndex} offsetAfter=${gridState.firstVisibleItemScrollOffset}"
                )
            } else {
                logScrollRestoreTrace(
                    "grid_scroll_to_uri_not_found uriHash=${scrollToUri.hashCode()} flatItems=${flatGridItems.size} " +
                        "first=${gridState.firstVisibleItemIndex} total=${gridState.layoutInfo.totalItemsCount}"
                )
            }
        }
    }

    val maxLineSpan = columnOptions[currentColumnIndex]
    val thumbSize = remember(maxLineSpan) {
        when {
            maxLineSpan >= 28 -> DENSE28_THUMB_SIZE
            maxLineSpan >= 7 -> 160
            maxLineSpan >= 4 -> 512
            else -> 1024
        }
    }

    // --- Prefetch thumbnails near the current viewport (bidirectional, throttled).
    // Enhanced preload feature for smoother gallery browsing.
    LaunchedEffect(gridState, flatGridItems, flatGridBuiltColumns, pagingItems, thumbSize, maxLineSpan, isScrollbarDragging) {
        snapshotFlow {
            Triple(gridState.firstVisibleItemIndex, gridState.isScrollInProgress, isScrollbarDragging)
        }
            .debounce(180)
            .collectLatest { (firstIndex, isScrolling, dragging) ->
                if (maxLineSpan >= 28 && flatGridBuiltColumns != maxLineSpan) {
                    return@collectLatest
                }
                val maxPrefetchRequests = when {
                    maxLineSpan >= 28 -> 12
                    maxLineSpan >= 7 -> 6
                    maxLineSpan >= 4 -> 8
                    else -> 4
                }
                if (dragging) {
                    return@collectLatest
                }
                val totalItems = pagingItems?.itemCount ?: flatGridItems.size
                if (totalItems == 0) return@collectLatest

                val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo
                    .maxOfOrNull { it.index }
                    ?: firstIndex

                // Bidirectional prefetch (previous + next rows) for better preload experience
                val preloadStart = firstIndex.coerceAtLeast(0)
                val preloadEnd = if (maxLineSpan >= 28) {
                    (firstIndex + maxLineSpan * DENSE28_THUMB_LOAD_ROWS).coerceAtMost(totalItems - 1)
                } else {
                    (lastVisibleIndex + maxLineSpan).coerceAtMost(totalItems - 1)
                }
                val preloadIndices = (preloadStart..preloadEnd).toList()
                val preloadRequests = preloadIndices.mapNotNull { i ->
                    val item = if (pagingItems != null) {
                        runCatching { pagingItems.peek(i) }.getOrNull()
                    } else {
                        flatGridItems.getOrNull(i)
                    }

                    if (item !is GridItem.Media) return@mapNotNull null
                    if (gridImageLoader.memoryCache?.get(MemoryCache.Key(item.data.uri)) != null) {
                        return@mapNotNull null
                    }

                    ImageRequest.Builder(context)
                        .data(item.data.uri)
                        .size(thumbSize)
                        .precision(coil.size.Precision.INEXACT)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .memoryCacheKey(item.data.uri)
                        .placeholderMemoryCacheKey(item.data.uri)
                        .apply {
                            if (item.data.isVideo) {
                                videoFrameMillis(1000)
                            }
                        }
                        .build()
                }.take(maxPrefetchRequests)

                for (request in preloadRequests) {
                    gridImageLoader.execute(request)
                    delay(16)
                }
            }
    }

    // --- UI の描画 ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        val useDense28FlatGrid = maxLineSpan >= 28
        val denseGridReady = !useDense28FlatGrid || flatGridBuiltColumns == maxLineSpan
        val displayedFlatGridItems = if (denseGridReady) flatGridItems else emptyList()
        val displayedMediaItemsOnly = if (denseGridReady) mediaItemsOnly else emptyList()
        GalleryGridContent(
            gridState = gridState,
            flatGridItems = if (pagingItems != null && !useDense28FlatGrid) emptyList() else displayedFlatGridItems,
            pagingItems = if (useDense28FlatGrid) null else pagingItems,
            maxLineSpan = maxLineSpan,
            thumbSize = thumbSize,
            imageLoader = gridImageLoader,
            getTopBarOffset = { topBarOffsetHeightPx },
            totalTopBarHeight = totalTopBarHeight,
            totalBottomPadding = totalBottomPadding,
            isSelectionMode = isSelectionMode,
            isScrollbarDragging = isScrollbarDragging,
            selectedUris = selectedUris,
            highlightUri = highlightUri,
            metadataMap = if (maxLineSpan >= 28) EmptyMetadataMap else metadataMap,
            mediaItemsOnly = displayedMediaItemsOnly,
            onImageClick = onImageClick,
            onPageChangedInViewer = onPageChangedInViewer,
            selectOnTap = selectOnTap,
            onSelectionModeChanged = { isSelectionMode = it },
            onLastSelectedIndexChanged = { lastSelectedIndex = it },
            onZoomIn = { if (currentColumnIndex < columnOptions.size - 1) currentColumnIndex++ },
            onZoomOut = { if (currentColumnIndex > 0) currentColumnIndex-- },
            onZoomingStateChanged = { galleryState.isZooming = it },
            onSelectionEmptied = {
                isSelectionMode = false
                lastSelectedIndex = -1
            }
        )

        if (showTopSection && !galleryState.isZooming) {
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
                onBulkMove = { uris: List<String> ->
                    onBulkMove?.invoke(uris)
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

        if (maxLineSpan < 28 && sortedList.isNotEmpty() && !isSelectionMode && !galleryState.isZooming) {
            GalleryScrollbar(
                gridState = gridState,
                columnCount = maxLineSpan,
                positionLabelItems = displayedFlatGridItems,
                totalTopBarHeight = totalTopBarHeight,
                totalBottomPadding = totalBottomPadding,
                isActive = { isScrollbarDragging = it },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * グリッドレイアウトを担当する内部コンポーネント。
 */
@Composable
private fun GalleryGridContent(
    gridState: LazyGridState,
    flatGridItems: List<GridItem>,
    pagingItems: LazyPagingItems<GridItem>?,
    maxLineSpan: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    getTopBarOffset: () -> Float,
    totalTopBarHeight: androidx.compose.ui.unit.Dp,
    totalBottomPadding: androidx.compose.ui.unit.Dp,
    isSelectionMode: Boolean,
    isScrollbarDragging: Boolean,
    selectedUris: SnapshotStateMap<String, Boolean>,
    highlightUri: String?,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    mediaItemsOnly: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    onPageChangedInViewer: (String) -> Unit,
    selectOnTap: Boolean,
    onSelectionModeChanged: (Boolean) -> Unit,
    onLastSelectedIndexChanged: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomingStateChanged: (Boolean) -> Unit,
    onSelectionEmptied: () -> Unit
) {
    val gestureScope = rememberCoroutineScope()
    val rawIsGridScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }

    // Sample scroll velocity (items per second) to support speed-based loading decisions.
    val scrollVelocity = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(gridState, isScrollbarDragging) {
        var lastIdx = gridState.firstVisibleItemIndex
        var lastTime = System.currentTimeMillis()
        while (true) {
            delay(80)
            if (isScrollbarDragging) {
                scrollVelocity.floatValue = 0f
                continue
            }
            val curIdx = gridState.firstVisibleItemIndex
            val curTime = System.currentTimeMillis()
            if (rawIsGridScrolling && curTime > lastTime) {
                val dIdx = kotlin.math.abs(curIdx - lastIdx).toFloat()
                val dt = (curTime - lastTime) / 1000f
                scrollVelocity.floatValue = if (dt > 0.01f) dIdx / dt else 0f
            } else {
                scrollVelocity.floatValue = 0f
            }
            lastIdx = curIdx
            lastTime = curTime
        }
    }

    // Pause uncached image loads only during expensive movement. Taps and slow drags still
    // let a small amount of loading continue so the grid does not appear frozen.
    val thumbnailThrottleVelocityThreshold = if (maxLineSpan >= 28) 18f else 12f
    val isGridScrolling by remember(maxLineSpan, isScrollbarDragging) {
        derivedStateOf {
            isScrollbarDragging || (rawIsGridScrolling && scrollVelocity.floatValue > thumbnailThrottleVelocityThreshold)
        }
    }
    LaunchedEffect(gridState, maxLineSpan, flatGridItems.size, pagingItems?.itemCount) {
        if (maxLineSpan < 28) return@LaunchedEffect
        var lastFirstIndex = gridState.firstVisibleItemIndex
        while (true) {
            delay(700)
            if (rawIsGridScrolling) {
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                val firstIndex = gridState.firstVisibleItemIndex
                val lastIndex = visibleItems.maxOfOrNull { it.index } ?: firstIndex
                val total = gridState.layoutInfo.totalItemsCount
                val deltaIndex = firstIndex - lastFirstIndex
                logDense28Trace(
                    "dense_scroll_sample first=$firstIndex last=$lastIndex delta=$deltaIndex " +
                        "visible=${visibleItems.size} total=$total flatItems=${flatGridItems.size} " +
                        "source=${if (pagingItems == null) "flat_capped" else "paging"} " +
                        "thumbLoadRows=$DENSE28_THUMB_LOAD_ROWS " +
                        "velocityItemsPerSec=${"%.1f".format(Locale.US, scrollVelocity.floatValue)}"
                )
                lastFirstIndex = firstIndex
            }
        }
    }
    val itemBoundsInRoot = remember(maxLineSpan) { mutableStateMapOf<Int, Rect>() }
    var gridBoundsInRoot by remember(maxLineSpan) { mutableStateOf<Rect?>(null) }
    val denseThumbnailStartIndex by remember(gridState, maxLineSpan) {
        derivedStateOf {
            if (maxLineSpan >= 28) gridState.firstVisibleItemIndex else 0
        }
    }
    val denseThumbnailEndIndex by remember(gridState, maxLineSpan) {
        derivedStateOf {
            if (maxLineSpan >= 28) {
                gridState.firstVisibleItemIndex + maxLineSpan * DENSE28_THUMB_LOAD_ROWS
            } else {
                Int.MAX_VALUE
            }
        }
    }

    fun mediaAtGridIndex(index: Int): GridItem.Media? {
        if (index < 0) return null
        val item = if (pagingItems != null) {
            if (index >= pagingItems.itemCount) null else runCatching { pagingItems.peek(index) }.getOrNull()
        } else {
            flatGridItems.getOrNull(index)
        }
        return item as? GridItem.Media
    }

    fun mediaIndexAtRootPosition(position: Offset, allowNearest: Boolean = false): Int? {
        val hit = itemBoundsInRoot.entries.firstOrNull { (_, bounds) -> bounds.contains(position) }
        if (hit != null) return hit.key

        if (!allowNearest || itemBoundsInRoot.isEmpty()) return null
        val nearest = itemBoundsInRoot.minByOrNull { (_, bounds) ->
            kotlin.math.abs(position.y - bounds.center.y)
        }
        return nearest?.key
    }

    fun applySelectionPreview(baseSelection: Set<String>, startGridIndex: Int, endGridIndex: Int, shouldSelect: Boolean) {
        selectedUris.clear()
        baseSelection.forEach { selectedUris[it] = true }
        val start = minOf(startGridIndex, endGridIndex)
        val end = maxOf(startGridIndex, endGridIndex)
        for (index in start..end) {
            val uri = mediaAtGridIndex(index)?.data?.uri ?: continue
            if (shouldSelect) selectedUris[uri] = true else selectedUris.remove(uri)
        }
        if (selectedUris.isEmpty()) onSelectionEmptied() else onSelectionModeChanged(true)
    }

    fun toggleSelection(media: GridItem.Media) {
        val uri = media.data.uri
        if (selectedUris.containsKey(uri)) {
            selectedUris.remove(uri)
            if (selectedUris.isEmpty()) onSelectionEmptied()
        } else {
            selectedUris[uri] = true
            onLastSelectedIndexChanged(media.index)
            onSelectionModeChanged(true)
        }
    }

    fun openMedia(media: GridItem.Media) {
        val actualIndex = if (media.index >= 0) {
            media.index
        } else {
            mediaItemsOnly.indexOfFirst { it.uri == media.data.uri }
        }
        if (actualIndex >= 0) {
            onImageClick(actualIndex, mediaItemsOnly)
        } else {
            onImageClick(0, listOf(media.data))
        }
        onPageChangedInViewer(media.data.uri)
    }

    fun handleTap(media: GridItem.Media) {
        if (selectOnTap || isSelectionMode) toggleSelection(media) else openMedia(media)
    }

    fun beginDragSelection(startGridIndex: Int): Pair<Set<String>, Boolean> {
        val startMedia = mediaAtGridIndex(startGridIndex) ?: return selectedUris.keys.toSet() to true
        val baseSelection = selectedUris.keys.toSet()
        val shouldSelect = !baseSelection.contains(startMedia.data.uri)
        onSelectionModeChanged(true)
        applySelectionPreview(baseSelection, startGridIndex, startGridIndex, shouldSelect)
        return baseSelection to shouldSelect
    }

    fun updateDragSelection(
        rootPosition: Offset,
        baseSelection: Set<String>,
        startGridIndex: Int,
        lastGridIndex: Int,
        shouldSelect: Boolean
    ): Int {
        val gridBounds = gridBoundsInRoot
        if (gridBounds != null) {
            when {
                rootPosition.y < gridBounds.top + 96f -> gestureScope.launch { gridState.scrollBy(-28f) }
                rootPosition.y > gridBounds.bottom - 96f -> gestureScope.launch { gridState.scrollBy(28f) }
            }
        }

        val currentGridIndex = mediaIndexAtRootPosition(rootPosition, allowNearest = true)
        if (currentGridIndex != null && currentGridIndex != lastGridIndex) {
            applySelectionPreview(baseSelection, startGridIndex, currentGridIndex, shouldSelect)
            return currentGridIndex
        }
        return lastGridIndex
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(maxLineSpan),
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 32.dp)
            .graphicsLayer { translationY = getTopBarOffset() }
            .onGloballyPositioned { coordinates ->
                if (!isScrollbarDragging) {
                    gridBoundsInRoot = coordinates.boundsInRoot()
                }
            }
            .pointerInput(maxLineSpan) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var cumulativeZoom = 1f
                    var isPinching = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.all { !it.pressed }) {
                            if (isPinching) onZoomingStateChanged(false)
                            break
                        }

                        if (event.changes.count { it.pressed } >= 2) {
                            if (!isPinching) {
                                isPinching = true
                                onZoomingStateChanged(true)
                            }
                            cumulativeZoom *= event.calculateZoom()
                            event.changes.forEach { it.consume() }

                            if (cumulativeZoom > 1.18f) {
                                onZoomIn()
                                cumulativeZoom = 1f
                            } else if (cumulativeZoom < 0.85f) {
                                onZoomOut()
                                cumulativeZoom = 1f
                            }
                        }
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
                    val item = runCatching { pagingItems.peek(index) }.getOrNull()
                    if (item is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                }
            ) { index ->
                val item = pagingItems[index]
                if (item == null) {
                    PagingPlaceholderGridItem(columnCount = maxLineSpan)
                    return@items
                }
                GridItemRenderer(
                    item = item,
                    gridIndex = index,
                    selectedUris = selectedUris,
                    metadataMap = metadataMap,
                    maxLineSpan = maxLineSpan,
                    thumbSize = thumbSize,
                    imageLoader = imageLoader,
                    isGridScrolling = isGridScrolling,
                    isScrollbarDragging = isScrollbarDragging,
                    denseThumbnailStartIndex = denseThumbnailStartIndex,
                    denseThumbnailEndIndex = denseThumbnailEndIndex,
                    highlightUri = highlightUri,
                    onMediaBoundsChanged = { gridIndex, bounds ->
                        if (!isScrollbarDragging) {
                            if (bounds == null) itemBoundsInRoot.remove(gridIndex) else itemBoundsInRoot[gridIndex] = bounds
                        }
                    },
                    onMediaClick = ::handleTap,
                    onDragSelectionStart = { gridIndex -> beginDragSelection(gridIndex) },
                    onDragSelectionMove = { rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect ->
                        updateDragSelection(rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect)
                    }
                )
            }
        } else {
            itemsIndexed(
                items = flatGridItems,
                key = { _, item -> item.key },
                contentType = { _, item -> if (item is GridItem.Header) "header" else "media" },
                span = { _, item -> if (item is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) }
            ) { index, item ->
                GridItemRenderer(
                    item = item,
                    gridIndex = index,
                    selectedUris = selectedUris,
                    metadataMap = metadataMap,
                    maxLineSpan = maxLineSpan,
                    thumbSize = thumbSize,
                    imageLoader = imageLoader,
                    isGridScrolling = isGridScrolling,
                    isScrollbarDragging = isScrollbarDragging,
                    denseThumbnailStartIndex = denseThumbnailStartIndex,
                    denseThumbnailEndIndex = denseThumbnailEndIndex,
                    highlightUri = highlightUri,
                    onMediaBoundsChanged = { gridIndex, bounds ->
                        if (!isScrollbarDragging) {
                            if (bounds == null) itemBoundsInRoot.remove(gridIndex) else itemBoundsInRoot[gridIndex] = bounds
                        }
                    },
                    onMediaClick = ::handleTap,
                    onDragSelectionStart = { gridIndex -> beginDragSelection(gridIndex) },
                    onDragSelectionMove = { rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect ->
                        updateDragSelection(rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect)
                    }
                )
            }
        }
    }
}

@Composable
private fun PagingPlaceholderGridItem(columnCount: Int) {
    val colors = GalleryThemeTokens.colors
    val itemModifier = remember(columnCount) {
        Modifier
            .fillMaxWidth()
            .then(
                if (columnCount > 1) {
                    Modifier.aspectRatio(1f)
                } else {
                    Modifier
                        .height(220.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                }
            )
            .padding(if (columnCount > 1) 0.5.dp else 0.dp)
            .then(if (columnCount in 2..27) Modifier.clip(RoundedCornerShape(1.dp)) else Modifier)
    }
    Box(
        modifier = itemModifier.background(colors.surfaceVariant)
    )
}

@Composable
private fun GridItemRenderer(
    item: GridItem,
    gridIndex: Int,
    selectedUris: SnapshotStateMap<String, Boolean>,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    maxLineSpan: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    isGridScrolling: Boolean,
    isScrollbarDragging: Boolean,
    denseThumbnailStartIndex: Int,
    denseThumbnailEndIndex: Int,
    highlightUri: String?,
    onMediaBoundsChanged: (Int, Rect?) -> Unit,
    onMediaClick: (GridItem.Media) -> Unit,
    onDragSelectionStart: (Int) -> Pair<Set<String>, Boolean>,
    onDragSelectionMove: (Offset, Set<String>, Int, Int, Boolean) -> Int
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
            if (maxLineSpan >= 28) {
                DenseMediaGridItem(
                    media = item.data,
                    thumbSize = thumbSize,
                    imageLoader = imageLoader,
                    shouldLoadThumbnail = gridIndex in denseThumbnailStartIndex..denseThumbnailEndIndex,
                    isGridScrolling = isGridScrolling,
                    isSelected = selectedUris.containsKey(item.data.uri),
                    isHighlighted = highlightUri == item.data.uri,
                    onClick = { onMediaClick(item) }
                )
            } else {
                MediaGridItemWrapper(
                    item = item,
                    gridIndex = gridIndex,
                    isSelected = selectedUris.containsKey(item.data.uri),
                    metadataMap = metadataMap,
                    columnCount = maxLineSpan,
                    thumbSize = thumbSize,
                    imageLoader = imageLoader,
                    isGridScrolling = isGridScrolling,
                    isScrollbarDragging = isScrollbarDragging,
                    isHighlighted = highlightUri == item.data.uri,
                    onBoundsChanged = onMediaBoundsChanged,
                    onClick = { onMediaClick(item) },
                    onDragSelectionStart = onDragSelectionStart,
                    onDragSelectionMove = onDragSelectionMove
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DenseMediaGridItem(
    media: MediaData,
    thumbSize: Int,
    imageLoader: ImageLoader,
    shouldLoadThumbnail: Boolean,
    isGridScrolling: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = GalleryThemeTokens.colors
    val backgroundColor = when {
        media.isVideo -> colors.accentSoft
        else -> colors.surfaceVariant
    }
    val isMemoryCached = remember(media.uri, imageLoader, isGridScrolling, shouldLoadThumbnail) {
        imageLoader.memoryCache?.get(MemoryCache.Key(media.uri)) != null
    }
    val shouldDrawThumbnail = isMemoryCached || (shouldLoadThumbnail && !isGridScrolling)
    val model = remember(media.uri, thumbSize) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .size(thumbSize)
            .precision(coil.size.Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .crossfade(false)
            .memoryCacheKey(media.uri)
            .placeholderMemoryCacheKey(media.uri)
            .apply {
                if (media.isVideo) {
                    videoFrameMillis(1000)
                }
            }
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(0.25.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(backgroundColor)
            .then(if (isHighlighted) Modifier.border(1.dp, Color.Cyan) else Modifier)
    ) {
        if (shouldDrawThumbnail) {
            AsyncImage(
                model = model,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.45f))
            )
        }
    }
}

/**
 * 個々のタイルの状態を管理するラッパー。
 */
@Composable
private fun MediaGridItemWrapper(
    item: GridItem.Media,
    gridIndex: Int,
    isSelected: Boolean,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    columnCount: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    isGridScrolling: Boolean,
    isScrollbarDragging: Boolean,
    isHighlighted: Boolean,
    onBoundsChanged: (Int, Rect?) -> Unit,
    onClick: () -> Unit,
    onDragSelectionStart: (Int) -> Pair<Set<String>, Boolean>,
    onDragSelectionMove: (Offset, Set<String>, Int, Int, Boolean) -> Int
) {
    // メタデータの細かな再計算を避け、リスト参照ではなく直接値を取得する。
    val isFavorite = if (columnCount < 28) {
        remember(metadataMap, item.data.uri) {
            derivedStateOf { metadataMap[item.data.uri]?.isFavorite == true }
        }.value
    } else {
        false
    }

    MediaGridItem(
        media = item.data,
        label = item.label,
        isFavorite = isFavorite,
        columnCount = columnCount,
        thumbSize = thumbSize,
        imageLoader = imageLoader,
        isGridScrolling = isGridScrolling,
        isScrollbarDragging = isScrollbarDragging,
        isHighlighted = isHighlighted,
        isSelected = isSelected,
        gridIndex = gridIndex,
        onBoundsChanged = onBoundsChanged,
        onClick = onClick,
        onDragSelectionStart = onDragSelectionStart,
        onDragSelectionMove = onDragSelectionMove
    )
}

/**
 * 実際に画像を表示し、クリックなどの操作を受け付ける最小単位。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridItem(
    media: MediaData,
    label: String?,
    isFavorite: Boolean,
    columnCount: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    isGridScrolling: Boolean,
    isScrollbarDragging: Boolean,
    isHighlighted: Boolean,
    isSelected: Boolean,
    gridIndex: Int,
    onBoundsChanged: (Int, Rect?) -> Unit,
    onClick: () -> Unit,
    onDragSelectionStart: (Int) -> Pair<Set<String>, Boolean>,
    onDragSelectionMove: (Offset, Set<String>, Int, Int, Boolean) -> Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDenseGrid = columnCount >= 28
    val colors = GalleryThemeTokens.colors

    // --- 画像リクエストの構築 ---
    // model を固定し、再描画時のロード試行を最小限に抑える。
    val model = remember(media.uri, thumbSize, columnCount) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .size(thumbSize)
            .precision(coil.size.Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .crossfade(false)
            .memoryCacheKey(media.uri)
            .placeholderMemoryCacheKey(media.uri)
            .apply {
                if (media.isVideo) {
                    // Always extract a thumbnail frame for videos in grid (prevents black screens).
                    videoFrameMillis(1000)
                }
            }
            .build()
    }

    // ベース modifier を安定化し、頻繁に変わらない条件のみに依存する。
    val itemModifier = remember(columnCount) {
        Modifier
            .fillMaxWidth()
            .then(if (columnCount > 1) Modifier.aspectRatio(1f) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)))
            .padding(if (columnCount > 1) 0.5.dp else 0.dp)
            .then(if (columnCount in 2..27) Modifier.clip(RoundedCornerShape(1.dp)) else Modifier)
    }

    val itemBackgroundColor = when {
        !isDenseGrid -> colors.surfaceVariant
        media.isVideo -> colors.accentSoft
        else -> colors.surfaceVariant
    }
    val isMemoryCached = remember(media.uri, imageLoader, isGridScrolling) {
        imageLoader.memoryCache?.get(MemoryCache.Key(media.uri)) != null
    }


    // 強調表示と選択状態を graphicsLayer で処理し、レイアウト計算を避ける。
    val highlightModifier = if (isHighlighted) {
        Modifier.border(2.dp, Color.Cyan, if (columnCount > 1) RoundedCornerShape(2.dp) else RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    var boundsInRoot by remember { mutableStateOf<Rect?>(null) }

    DisposableEffect(gridIndex) {
        onDispose { onBoundsChanged(gridIndex, null) }
    }

    Box(
        modifier = itemModifier
            .onGloballyPositioned { coordinates ->
                if (!isScrollbarDragging) {
                    val bounds = coordinates.boundsInRoot()
                    boundsInRoot = bounds
                    onBoundsChanged(gridIndex, bounds)
                }
            }
            .pointerInput(gridIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var movedBeforeLongPress = false
                    val longPressReached = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) {
                                return@withTimeoutOrNull false
                            }

                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull false

                            if (!change.pressed) {
                                return@withTimeoutOrNull false
                            }

                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                movedBeforeLongPress = true
                                return@withTimeoutOrNull false
                            }
                        }
                    } == null

                    if (!longPressReached) {
                        if (!movedBeforeLongPress) onClick()
                        return@awaitEachGesture
                    }

                    val (baseSelection, shouldSelect) = onDragSelectionStart(gridIndex)
                    var lastGridIndex = gridIndex
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break

                        val rootPosition = boundsInRoot?.let { bounds ->
                            Offset(bounds.left + change.position.x, bounds.top + change.position.y)
                        }
                        if (rootPosition != null) {
                            lastGridIndex = onDragSelectionMove(
                                rootPosition,
                                baseSelection,
                                gridIndex,
                                lastGridIndex,
                                shouldSelect
                            )
                        }
                        change.consume()
                    }
                }
            }
            .background(itemBackgroundColor)
            .then(highlightModifier)
    ) {
        val shouldDrawImage = !isGridScrolling || isMemoryCached
        if (shouldDrawImage) {
            AsyncImage(
                model = model,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (columnCount > 1) ContentScale.Crop else ContentScale.FillWidth
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = if (isDenseGrid) 0.45f else 0.3f)),
                contentAlignment = Alignment.TopEnd
            ) {
                if (!isDenseGrid) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(2.dp).size(if (columnCount > 7) 12.dp else 20.dp)
                    )
                }
            }
        }

        if (columnCount < 28) {
            if (isFavorite) {
                Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.align(Alignment.BottomStart).padding(2.dp).size(if (columnCount > 7) 6.dp else 14.dp))
            }
            if (label != null && columnCount <= 7) {
                Text(
                    label,
                    color = Color.White,
                    fontSize = com.example.gallery.ui.AppConstants.DenseBadgeFontSize,
                    lineHeight = com.example.gallery.ui.AppConstants.TinyFontSize,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(colors.background.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 2.dp, vertical = 0.dp)
                )
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
 * ギャラリーの上部セクション。
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
    onBulkMove: (List<String>) -> Unit,
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
                SelectionModeBar(
                    selectedCount,
                    isTrashMode,
                    onCloseSelection,
                    onBulkFavorite,
                    onBulkDelete,
                    { onBulkEdit(selectedUris) },
                    { onBulkMove(selectedUris) },
                    { if (selectedUris.isNotEmpty()) onUpdateThumbnail(selectedUris.first()) },
    title != null && title != "すべて" && selectedUris.size == 1
                )
            } else {
                GalleryTopControlBar(galleryState, isFilterEnabled)
            }
        }
    }
}

/**
 * 選択モード中に表示されるアクションバー。
 */
@Composable
private fun SelectionModeBar(
    selectedCount: Int,
    isTrashMode: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
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
                    DropdownMenuItem(text = { Text("フォルダ移動", color = Color.White) }, leadingIcon = { Icon(Icons.Default.Folder, null, tint = Color.White) }, onClick = { showOverflow = false; onMove() })
                    DropdownMenuItem(text = { Text("一括タグ・評価編集", color = Color.White) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.White) }, onClick = { showOverflow = false; onEdit() })
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
    columnCount: Int,
    positionLabelItems: List<GridItem>,
    totalTopBarHeight: androidx.compose.ui.unit.Dp,
    totalBottomPadding: androidx.compose.ui.unit.Dp,
    isActive: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    val thumbWidth by animateDpAsState(targetValue = if (isPressed) 12.dp else 4.dp, label = "scrollbarWidth")
    // Avoid heavy full layoutInfo reads in derivedStateOf where possible.
    // Use firstVisibleItemIndex (lighter) for position; total and visible size still need layoutInfo but minimized.
    val totalItemsState = remember(gridState) { derivedStateOf { gridState.layoutInfo.totalItemsCount } }
    val visibleItemsSizeState = remember(gridState) { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size } }
    val firstVisibleIndexState = remember(gridState) { derivedStateOf { gridState.firstVisibleItemIndex } }
    // Keep the latest requested index for the thumb preview. The grid follows at a
    // throttled rate while images and bounds work are paused by isScrollbarDragging.
    var scrollbarTargetIndex by remember { mutableIntStateOf(-1) }
    val scrollbarDateLabels = remember(positionLabelItems) {
        buildScrollbarDateLabels(positionLabelItems)
    }
    val thumbHeightRatioState = remember(gridState) { derivedStateOf {
        val total = totalItemsState.value; if (total == 0) 0.1f else (visibleItemsSizeState.value.toFloat() / total).coerceIn(0.1f, 1f)
    } }
    val scrollPositionState = remember(gridState) { derivedStateOf {
        val total = totalItemsState.value
        if (total <= 1) {
            0f
        } else {
            val previewIndex = (if (isPressed && scrollbarTargetIndex >= 0) {
                scrollbarTargetIndex
            } else {
                firstVisibleIndexState.value
            }).coerceIn(0, total - 1)
            previewIndex.toFloat() / (total - 1)
        }
    } }
    val currentPositionLabelState = remember(gridState, scrollbarDateLabels) {
        derivedStateOf {
            val total = totalItemsState.value
            if (total <= 0) {
                null
            } else {
                val previewIndex = (if (isPressed && scrollbarTargetIndex >= 0) {
                    scrollbarTargetIndex
                } else {
                    firstVisibleIndexState.value
                }).coerceIn(0, total - 1)
                scrollbarDateLabels.getOrNull(previewIndex)
            }
        }
    }
    var containerHeight by remember { mutableIntStateOf(0) }
    var labelHeight by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxHeight().width(176.dp).padding(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding + 8.dp).zIndex(5f)) {
        val positionLabel = currentPositionLabelState.value
        AnimatedVisibility(
            visible = isPressed && positionLabel != null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    val scrollPos = scrollPositionState.value
                    val heightRatio = thumbHeightRatioState.value
                    val currentThumbHeight = containerHeight * heightRatio
                    val centeredY = ((containerHeight - currentThumbHeight) * scrollPos) +
                        (currentThumbHeight / 2f) - (labelHeight / 2f)
                    val maxY = (containerHeight - labelHeight).coerceAtLeast(0).toFloat()
                    translationY = centeredY.coerceIn(0f, maxY)
                    translationX = (-28.dp).toPx()
                }
        ) {
            Surface(
                modifier = Modifier.onSizeChanged { labelHeight = it.height },
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.78f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = positionLabel.orEmpty(),
                    color = Color.White,
                    fontSize = com.example.gallery.ui.AppConstants.ScrollbarLabelFontSize,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().width(32.dp).onSizeChanged { containerHeight = it.height }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                if (totalItemsState.value == 0) {
                    awaitFirstDown()
                    continue
                }
                fun currentTotalItems(): Int = totalItemsState.value

                fun targetIndexForTouchY(y: Float): Int {
                    val itemCount = currentTotalItems()
                    if (itemCount <= 1) return 0
                    val railHeight = size.height.toFloat().coerceAtLeast(1f)
                    val thumbRatio = (visibleItemsSizeState.value.toFloat() / itemCount)
                        .coerceIn(0.1f, 1f)
                    val thumbHeight = railHeight * thumbRatio
                    val travelHeight = (railHeight - thumbHeight).coerceAtLeast(1f)
                    val progress = ((y - thumbHeight / 2f) / travelHeight).coerceIn(0f, 1f)
                    return (progress * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                }

                fun isBottomTouch(y: Float): Boolean {
                    return y >= size.height.toFloat() - 1f
                }

                fun isTopTouch(y: Float): Boolean {
                    return y <= 1f
                }

                val down = awaitFirstDown(); isPressed = true; isActive(true)
                var lastTouchY = down.position.y
                val initialIdx = targetIndexForTouchY(lastTouchY)
                scrollbarTargetIndex = initialIdx
                var lastLiveRequestAt = 0L
                var lastLiveRequestedIndex = gridState.firstVisibleItemIndex
                val liveFollowIntervalMs = if (columnCount >= 7) 120L else 90L
                val liveFollowMinDelta = maxOf(columnCount * 2, 8)
                fun requestLiveFollow(target: Int, force: Boolean = false) {
                    val now = System.currentTimeMillis()
                    val deltaFromLast = kotlin.math.abs(target - lastLiveRequestedIndex)
                    val sincePreviousMs = now - lastLiveRequestAt
                    if (!force) {
                        val enoughTimePassed = sincePreviousMs >= liveFollowIntervalMs
                        val enoughDistanceMoved = deltaFromLast >= liveFollowMinDelta
                        if (!enoughTimePassed || !enoughDistanceMoved) {
                            return
                        }
                    }
                    gridState.requestScrollToItem(target)
                    lastLiveRequestAt = now
                    lastLiveRequestedIndex = target
                }
                requestLiveFollow(initialIdx, force = true)
                drag(down.id) { change -> change.consume()
                    lastTouchY = change.position.y
                    val latestTotal = currentTotalItems()
                    val newIdx = targetIndexForTouchY(lastTouchY)
                    // Only update target on significant change + throttle via effect
                    val isEdgeTarget = newIdx == 0 || newIdx == latestTotal - 1
                    if (isEdgeTarget || kotlin.math.abs(newIdx - scrollbarTargetIndex) >= 2) {
                        scrollbarTargetIndex = newIdx
                        requestLiveFollow(newIdx, force = isEdgeTarget)
                    }
                }
                val finalTotal = currentTotalItems()
                val finalTarget = when {
                    isBottomTouch(lastTouchY) -> (finalTotal - 1).coerceAtLeast(0)
                    isTopTouch(lastTouchY) -> 0
                    else -> targetIndexForTouchY(lastTouchY)
                }
                if (finalTarget >= 0) {
                    gridState.requestScrollToItem(finalTarget)
                    if (finalTotal > 0 && finalTarget == finalTotal - 1) {
                        scope.launch {
                            repeat(10) {
                                delay(140)
                                val latestTotal = totalItemsState.value
                                if (latestTotal > 0) {
                                    gridState.requestScrollToItem(latestTotal - 1)
                                }
                            }
                        }
                    }
                }
                isPressed = false
                scope.launch {
                    delay(700)
                    isActive(false)
                }
                scrollbarTargetIndex = -1
            }
        }
    }) {
        Box(modifier = Modifier.width(thumbWidth).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer {
            val scrollPos = scrollPositionState.value; val heightRatio = thumbHeightRatioState.value; val currentThumbHeight = containerHeight * heightRatio
            translationY = (containerHeight - currentThumbHeight) * scrollPos; translationX = (-4.dp).toPx(); scaleY = heightRatio; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        }.clip(CircleShape).background(if (isPressed) Color.Cyan else Color.White.copy(alpha = 0.8f)))
        }
    }
}

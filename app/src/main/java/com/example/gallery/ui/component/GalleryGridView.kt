package com.example.gallery.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.ui.AppConstants
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.local.PreferenceManager
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.state.*
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.ui.theme.GalleryAlphaTokens
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * グリッドに表示するアイテムの型定義。
 */
@Immutable
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

    data class SimilarGroup(
        val id: String,
        val items: List<MediaData>,
        val minimumSimilarity: Float
    ) : GridItem()

    // LazyGrid の再利用性を高めるためのユニークキー。
    val key: String get() = when (this) {
        is Header -> "header_${title}_${subtitle}"
        is Media -> data.uri
        is SimilarGroup -> "similar_group_$id"
    }
}

private data class GridBuildResult(
    val gridItems: List<GridItem>,
    val mediaItems: List<MediaData>,
    val denseLogMessage: String? = null
)

private data class ThumbnailPrefetchPolicy(
    val maxRequests: Int,
    val rowsBehind: Int,
    val rowsAhead: Int,
    val delayMs: Long
)


private const val DENSE28_TRACE = "GALLERY_DENSE28_TRACE"
private const val SCROLL_RESTORE_TRACE = "GALLERY_SCROLL_RESTORE_TRACE"
private const val SELECTION_TRACE = "GALLERY_SELECTION_TRACE"
private const val SCROLLBAR_TRACE = "GALLERY_SCROLLBAR_TRACE"
private const val GRID_LAYOUT_TRACE = "GALLERY_GRID_LAYOUT_TRACE"
private const val DENSE28_ROWS_PER_YEAR = 6
private const val DENSE28_THUMB_SIZE = 48
private const val DENSE28_THUMB_LOAD_ROWS = 12
private val EmptyMetadataMap = emptyMap<String, com.example.gallery.data.local.entity.MediaMetadataSummary>()

private fun calculateGridThumbnailSize(screenWidthPx: Int, columnCount: Int): Int {
    val safeWidthPx = screenWidthPx.coerceAtLeast(1)
    val cellWidthPx = (safeWidthPx / columnCount.coerceAtLeast(1)).coerceAtLeast(1)
    return when {
        columnCount >= 28 -> DENSE28_THUMB_SIZE
        columnCount >= 7 -> (cellWidthPx * 1.15f).roundToInt().coerceIn(96, 192)
        columnCount >= 4 -> (cellWidthPx * 1.20f).roundToInt().coerceIn(192, 384)
        columnCount >= 3 -> (cellWidthPx * 1.20f).roundToInt().coerceIn(256, 512)
        else -> cellWidthPx.coerceIn(512, 960)
    }
}

private fun thumbnailPrefetchPolicy(columnCount: Int, isScrolling: Boolean): ThumbnailPrefetchPolicy {
    return if (isScrolling) {
        ThumbnailPrefetchPolicy(
            maxRequests = when {
                columnCount >= 28 -> 4
                columnCount >= 7 -> 3
                columnCount >= 4 -> 3
                else -> 2
            },
            rowsBehind = 0,
            rowsAhead = 1,
            delayMs = 24L
        )
    } else {
        ThumbnailPrefetchPolicy(
            maxRequests = when {
                columnCount >= 28 -> 12
                columnCount >= 7 -> 8
                columnCount >= 4 -> 8
                else -> 4
            },
            rowsBehind = 1,
            rowsAhead = when {
                columnCount >= 28 -> DENSE28_THUMB_LOAD_ROWS
                columnCount >= 7 -> 3
                columnCount >= 4 -> 2
                else -> 1
            },
            delayMs = 8L
        )
    }
}

private fun buildGridThumbnailRequest(
    context: Context,
    media: MediaData,
    thumbSize: Int,
    includeDisplayHints: Boolean = true
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(media.uri)
        .size(thumbSize)
        .precision(coil.size.Precision.INEXACT)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .memoryCacheKey(media.uri)
        .placeholderMemoryCacheKey(media.uri)
        .apply {
            if (includeDisplayHints) {
                allowHardware(true)
                crossfade(false)
            }
            if (media.isVideo) {
                videoFrameMillis(1000)
            }
        }
        .build()
}

private fun ImageLoader.hasGridMemoryCache(uri: String): Boolean {
    return memoryCache?.get(MemoryCache.Key(uri)) != null
}

private fun logDense28Trace(message: String) {
    Log.d(DENSE28_TRACE, "$DENSE28_TRACE $message")
}

private fun logScrollRestoreTrace(message: String) {
    Log.d(SCROLL_RESTORE_TRACE, "$SCROLL_RESTORE_TRACE $message")
}

private fun logSelectionTrace(message: String) {
    Log.d(SELECTION_TRACE, "$SELECTION_TRACE $message")
}

private fun traceUri(uri: String?): String = uri?.hashCode()?.toString() ?: "none"

private fun GridItem.displayMedia(): MediaData? = when (this) {
    is GridItem.Header -> null
    is GridItem.Media -> data
    is GridItem.SimilarGroup -> items.firstOrNull()
}

private fun buildScrollbarDateLabels(gridItems: List<GridItem>, context: Context): List<String?> {
    if (gridItems.isEmpty()) return emptyList()

    val formatter = SimpleDateFormat(context.getString(R.string.format_date_full), Locale.JAPAN)
    val date = Date()
    val labels = MutableList<String?>(gridItems.size) { null }

    gridItems.forEachIndexed { index, item ->
        val media = item.displayMedia()
        if (media != null) {
            date.time = media.dateAdded
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
    extraBottomPadding: Dp = dimensionResource(R.dimen.grid_bottom_padding),
    selectOnTap: Boolean = false
) {
    // 列数の選択肢 (28:年, 7:月, 4/3/1:日)
    val columnOptions = listOf(28, 7, 4, 3, 2)
    var currentColumnIndex by remember { mutableIntStateOf(2) }

    val colors = GalleryThemeTokens.colors
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = initialScrollIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = initialScrollOffset.coerceAtLeast(0)
    )
    val currentOnScrollPositionChanged by rememberUpdatedState(onScrollPositionChanged)
    val currentOnScrollAnchorChanged by rememberUpdatedState(onScrollAnchorChanged)
    val currentOnScrollRestored by rememberUpdatedState(onScrollRestored)
    var isRestoringScroll by remember { mutableStateOf(false) }
    val density = LocalDensity.current

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
        val requestRestoreUri = restoreScrollUri
        isRestoringScroll = true
        logScrollRestoreTrace(
            "grid_restore_request_start key=$restoreScrollRequestKey requested=$restoreScrollIndex " +
                "offset=$restoreScrollOffset firstBefore=${gridState.firstVisibleItemIndex} " +
                "offsetBefore=${gridState.firstVisibleItemScrollOffset} totalBefore=${gridState.layoutInfo.totalItemsCount} " +
                "columns=${columnOptions[currentColumnIndex]} uriHash=${requestRestoreUri?.hashCode()}"
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
                    "total=${gridState.layoutInfo.totalItemsCount} uriHash=${requestRestoreUri?.hashCode()}"
            )
            if (requestRestoreUri == null) {
                currentOnScrollRestored(restoreScrollRequestKey)
            } else {
                logScrollRestoreTrace(
                    "grid_restore_request_wait_uri key=$restoreScrollRequestKey uriHash=${requestRestoreUri.hashCode()} " +
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
    val globalSettingsPrefs = remember(context) {
        context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
    }
    val lowMemoryMode = remember(globalSettingsPrefs) {
        globalSettingsPrefs.getBoolean("lowMemoryMode", false)
    }
    var similarImageGroupingEnabled by remember(globalSettingsPrefs) {
        mutableStateOf(
            globalSettingsPrefs.getBoolean(PreferenceManager.SIMILAR_IMAGE_GROUPING, true)
        )
    }
    var similarImageThreshold by remember(globalSettingsPrefs) {
        mutableStateOf(
            globalSettingsPrefs.getInt(PreferenceManager.SIMILAR_IMAGE_THRESHOLD, 60) / 100f
        )
    }
    DisposableEffect(globalSettingsPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PreferenceManager.SIMILAR_IMAGE_GROUPING) {
                similarImageGroupingEnabled = prefs.getBoolean(key, true)
            }
            if (key == PreferenceManager.SIMILAR_IMAGE_THRESHOLD) {
                similarImageThreshold = prefs.getInt(key, 60) / 100f
            }
        }
        globalSettingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { globalSettingsPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val gridImageDispatcher = remember(lowMemoryMode) {
        Dispatchers.IO.limitedParallelism(if (lowMemoryMode) 1 else 2)
    }
    val gridImageLoader = remember(context.applicationContext, gridImageDispatcher) {
        // Dedicated loader for grid thumbnails:
        // - Include VideoFrameDecoder so videos show proper frame thumbnails (not black).
        // - Do NOT include Gif/ImageDecoder so GIFs are static thumbnails (first frame).
        // Visible cells can decode two-at-a-time; explicit prefetch remains sequential below.
        ImageLoader.Builder(context.applicationContext)
            .dispatcher(gridImageDispatcher)
            .interceptorDispatcher(gridImageDispatcher)
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
    val selectedUris = remember { mutableStateMapOf<String, Boolean>() }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    fun selectedTraceSample(): String = traceUri(selectedUris.keys.firstOrNull())

    fun setSelectionMode(value: Boolean, source: String) {
        logSelectionTrace(
            "mode_set source=$source before=$isSelectionMode after=$value " +
                "count=${selectedUris.size} first=${selectedTraceSample()} " +
                "clearSignal=$clearSelectionSignal selectOnTap=$selectOnTap titleHash=${title?.hashCode()}"
        )
        isSelectionMode = value
    }

    fun clearSelectedUris(source: String) {
        logSelectionTrace(
            "selection_clear source=$source beforeCount=${selectedUris.size} " +
                "first=${selectedTraceSample()} lastIndex=$lastSelectedIndex mode=$isSelectionMode"
        )
        selectedUris.clear()
        lastSelectedIndex = -1
    }

    LaunchedEffect(isSelectionMode) {
        logSelectionTrace(
            "mode_effect value=$isSelectionMode count=${selectedUris.size} " +
                "first=${selectedTraceSample()} zoom=${galleryState.isZooming}"
        )
        galleryState.isSelectionMode = isSelectionMode
        if (!isSelectionMode && selectedUris.isNotEmpty()) {
            clearSelectedUris("mode_effect_false")
        }
    }

    // Activity へ選択モードやズーム中の状態を伝える。
    val currentOnSelectionModeChanged by rememberUpdatedState(onSelectionModeChanged)
    LaunchedEffect(galleryState.isZooming, isSelectionMode) {
        val parentActive = isSelectionMode || galleryState.isZooming
        logSelectionTrace(
            "parent_emit active=$parentActive localMode=$isSelectionMode " +
                "zoom=${galleryState.isZooming} count=${selectedUris.size}"
        )
        currentOnSelectionModeChanged(parentActive)
    }

    LaunchedEffect(galleryState.isZooming) {
        if (galleryState.isZooming) {
            delay(250)
            galleryState.isZooming = false
        }
    }

    // 選択済み URI を管理する。SnapshotStateMap で変更時の再描画を絞る。
    LaunchedEffect(selectedUris.size) {
        logSelectionTrace(
            "selection_count count=${selectedUris.size} mode=$isSelectionMode " +
                "first=${selectedTraceSample()} lastIndex=$lastSelectedIndex"
        )
        onSelectionChanged(selectedUris.keys.toList())
        if (selectedUris.isEmpty() && isSelectionMode) {
            setSelectionMode(false, "selection_count_empty")
        }
    }

    // 指定位置へスクロールした際に強調表示する URI。
    var highlightUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightUri) {
        val uri = highlightUri ?: return@LaunchedEffect
        delay(900)
        if (highlightUri == uri) {
            highlightUri = null
        }
    }

    // --- トップバーのレイアウト計算 ---
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalTopBarHeight = if (showTopSection) {
        (if (title != null) dimensionResource(R.dimen.header_height) + topPadding else topPadding) + dimensionResource(R.dimen.header_height)
    } else {
        0.dp
    }
    val totalBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extraBottomPadding
    val totalTopBarHeightPx = with(density) { totalTopBarHeight.toPx() }

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
            logSelectionTrace(
                "external_clear signal=$clearSelectionSignal mode=$isSelectionMode " +
                    "count=${selectedUris.size}"
            )
            setSelectionMode(false, "external_clear")
            clearSelectedUris("external_clear")
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

        val nextSortedList = withContext(Dispatchers.Default) {
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
                        val rating = metadataForFiltering[item.uri]?.ageRating ?: AppConstants.RATING_SFW
                        when (galleryState.ageRatingFilter) {
                            AgeRatingFilter.SFW -> rating == AppConstants.RATING_SFW
                            AgeRatingFilter.R15 -> rating == AppConstants.RATING_R15
                            AgeRatingFilter.R18 -> rating == AppConstants.RATING_R18
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
                                item.height > item.width && abs(ratio - deviceAspectRatio) < 0.2f
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

            sorted.toMutableList().apply {
                if (galleryState.isAscending) reverse()
            }
        }
        sortedList = nextSortedList
    }

    // --- グリッド用フラットリストの生成 ---
    val maxLineSpan = columnOptions[currentColumnIndex]
    val vectorReadyCount = remember(metadataMap) { metadataMap.values.count { it.hasFeatureVector } }
    var similarMediaGroups by remember {
        mutableStateOf<List<MediaRepository.SimilarMediaGroup>>(emptyList())
    }
    LaunchedEffect(sortedList, vectorReadyCount, similarImageGroupingEnabled, similarImageThreshold) {
        similarMediaGroups = if (
            similarImageGroupingEnabled && vectorReadyCount >= 2 && sortedList.size >= 2
        ) {
            galleryState.repository.findAdjacentSimilarMediaGroups(
                mediaItems = sortedList,
                threshold = similarImageThreshold
            )
        } else {
            emptyList()
        }
    }
    val activeSimilarGroupByUri = remember(similarMediaGroups, maxLineSpan, sortedList) {
        if (maxLineSpan < 28) {
            val galleryOrderByUri = sortedList
                .mapIndexed { index, media -> media.uri to index }
                .toMap()
            buildMap {
                similarMediaGroups.forEach { group ->
                    val displayOrderedItems = group.items.sortedBy { media ->
                        galleryOrderByUri[media.uri] ?: Int.MAX_VALUE
                    }
                    val displayGroup = group.copy(items = displayOrderedItems)
                    displayGroup.items.forEach { media -> put(media.uri, displayGroup) }
                }
            }
        } else {
            emptyMap()
        }
    }
    val useFlatGridPresentation = maxLineSpan >= 28 || maxLineSpan == 2 || activeSimilarGroupByUri.isNotEmpty()
    val activePagingItems = if (useFlatGridPresentation) null else pagingItems

    var flatGridItems by remember { mutableStateOf<List<GridItem>>(emptyList()) }
    var mediaItemsOnly by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var flatGridBuiltColumns by remember { mutableIntStateOf(columnOptions[currentColumnIndex]) }
    val currentFlatGridItems by rememberUpdatedState(flatGridItems)

    LaunchedEffect(sortedList, currentColumnIndex, activeSimilarGroupByUri) {
        if (sortedList.isEmpty()) {
            flatGridItems = emptyList()
            mediaItemsOnly = emptyList()
            flatGridBuiltColumns = columnOptions[currentColumnIndex]
            return@LaunchedEffect
        }

        val buildColumnIndex = currentColumnIndex
        val buildColumns = columnOptions[buildColumnIndex]
        val sortedSnapshot = sortedList
        val formatYear = context.getString(R.string.format_date_year)
        val formatMonth = context.getString(R.string.format_date_month)
        val formatDay = context.getString(R.string.format_date_day)
        val labelGif = context.getString(R.string.label_gif)
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
                val displaySdf = SimpleDateFormat(formatYear, Locale.JAPAN)

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
                val result = ArrayList<GridItem>(resultCapacity)
                val mediaOnly = sortedSnapshot.toList()
                val displayItems = ArrayList<GridItem>(sortedSnapshot.size)
                val emittedGroupIds = HashSet<String>()

                sortedSnapshot.forEachIndexed { mediaIndex, media ->
                    val similarGroup = activeSimilarGroupByUri[media.uri]
                    if (similarGroup == null) {
                        val label = when {
                            media.isGif -> labelGif
                            media.isVideo -> formatDuration(media.duration)
                            else -> null
                        }
                        displayItems.add(GridItem.Media(media, label, mediaIndex))
                    } else {
                        val firstInGallery = similarGroup.items.first()
                        val lastInGallery = similarGroup.items.last()
                        val groupId = "${firstInGallery.uri}_${lastInGallery.uri}_${similarGroup.items.size}"
                        if (media.uri == firstInGallery.uri && emittedGroupIds.add(groupId)) {
                            displayItems.add(
                                GridItem.SimilarGroup(
                                    id = groupId,
                                    items = similarGroup.items,
                                    minimumSimilarity = similarGroup.minimumSimilarity
                                )
                            )
                        }
                    }
                }

                if (effectiveGroupingMode == GroupingMode.NONE) {
                    result.addAll(displayItems)
                } else {
                    val displaySdf = when (effectiveGroupingMode) {
                        GroupingMode.DAY -> SimpleDateFormat(formatDay, Locale.JAPAN)
                        GroupingMode.MONTH -> SimpleDateFormat(formatMonth, Locale.JAPAN)
                        GroupingMode.YEAR -> SimpleDateFormat(formatYear, Locale.JAPAN)
                        else -> null
                    }
                    var lastHeaderText: String? = null
                    val dateObj = Date()
                    displayItems.forEach { displayItem ->
                        val media = displayItem.displayMedia() ?: return@forEach
                        if (displaySdf != null) {
                            dateObj.time = media.dateAdded
                            val headerText = displaySdf.format(dateObj)
                            if (headerText != lastHeaderText) {
                                result.add(GridItem.Header(headerText, headerText))
                                lastHeaderText = headerText
                            }
                        }
                        result.add(displayItem)
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
        val paging = activePagingItems
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
            gridItemForScrollAnchor(itemInfo.index)?.displayMedia()?.uri
        }
    }

    fun indexOfRestoreUri(uri: String): Int {
        return currentFlatGridItems.indexOfFirst { item ->
            when (item) {
                is GridItem.Header -> false
                is GridItem.Media -> item.data.uri == uri
                is GridItem.SimilarGroup -> item.items.any { it.uri == uri }
            }
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

    LaunchedEffect(gridState, restoreScrollRequestKey) {
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
                val index = indexOfRestoreUri(uri)
                if (index >= 0 && gridState.layoutInfo.totalItemsCount > index) {
                    if (gridState.isScrollInProgress) {
                        logScrollRestoreTrace(
                            "grid_restore_uri_skip_user_scroll key=$restoreScrollRequestKey uriHash=${uri.hashCode()} " +
                                "target=$index firstNow=${gridState.firstVisibleItemIndex} " +
                                "offsetNow=${gridState.firstVisibleItemScrollOffset} total=${gridState.layoutInfo.totalItemsCount}"
                        )
                    } else {
                        gridState.scrollToItem(index, restoreScrollOffset.coerceAtLeast(0))
                        logScrollRestoreTrace(
                            "grid_restore_uri_scroll_once key=$restoreScrollRequestKey uriHash=${uri.hashCode()} " +
                                "target=$index firstNow=${gridState.firstVisibleItemIndex} " +
                                "offsetNow=${gridState.firstVisibleItemScrollOffset} total=${gridState.layoutInfo.totalItemsCount}"
                        )
                    }
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

    LaunchedEffect(scrollToUri, galleryState.isSelectionMode) {
        if (scrollToUri != null) {
            if (galleryState.isSelectionMode) {
                logSelectionTrace(
                    "scroll_to_uri_skip_selection uri=${traceUri(scrollToUri)} " +
                        "first=${gridState.firstVisibleItemIndex} offset=${gridState.firstVisibleItemScrollOffset}"
                )
                onScrollConsumed()
                return@LaunchedEffect
            }
            logScrollRestoreTrace(
                "grid_scroll_to_uri_start uriHash=${scrollToUri.hashCode()} center=$centerScrollToUri " +
                    "flatItems=${flatGridItems.size} firstBefore=${gridState.firstVisibleItemIndex} " +
                    "offsetBefore=${gridState.firstVisibleItemScrollOffset} " +
                    "totalBefore=${gridState.layoutInfo.totalItemsCount} inProgress=${gridState.isScrollInProgress}"
            )
            val index = flatGridItems.indexOfFirst { item ->
                when (item) {
                    is GridItem.Header -> false
                    is GridItem.Media -> item.data.uri == scrollToUri
                    is GridItem.SimilarGroup -> item.items.any { it.uri == scrollToUri }
                }
            }
            if (index != -1) {
                val alreadyVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
                isRestoringScroll = true
                try {
                    if (!alreadyVisible) {
                        gridState.scrollToItem(index)
                    }
                    logScrollRestoreTrace(
                        "grid_scroll_to_uri_found uriHash=${scrollToUri.hashCode()} index=$index visible=$alreadyVisible " +
                            "firstNow=${gridState.firstVisibleItemIndex} offsetNow=${gridState.firstVisibleItemScrollOffset} " +
                            "total=${gridState.layoutInfo.totalItemsCount}"
                    )
                } finally {
                    isRestoringScroll = false
                }
                highlightUri = scrollToUri
                onScrollConsumed()
                logScrollRestoreTrace(
                    "grid_scroll_to_uri_consumed uriHash=${scrollToUri.hashCode()} index=$index " +
                        "firstAfter=${gridState.firstVisibleItemIndex} offsetAfter=${gridState.firstVisibleItemScrollOffset} " +
                        "centerRequested=$centerScrollToUri"
                )
            } else {
                logScrollRestoreTrace(
                    "grid_scroll_to_uri_not_found uriHash=${scrollToUri.hashCode()} flatItems=${flatGridItems.size} " +
                        "first=${gridState.firstVisibleItemIndex} total=${gridState.layoutInfo.totalItemsCount}"
                )
                onScrollConsumed()
            }
        }
    }

    val gridSidePadding = dimensionResource(R.dimen.grid_side_padding)
    val gridContentWidthPx = remember(configuration.screenWidthDp, density.density, gridSidePadding) {
        with(density) {
            (configuration.screenWidthDp.dp - gridSidePadding).toPx().roundToInt()
        }.coerceAtLeast(1)
    }
    val thumbSize = remember(maxLineSpan, gridContentWidthPx) {
        calculateGridThumbnailSize(gridContentWidthPx, maxLineSpan)
    }

    // --- Prefetch thumbnails near the current viewport (bidirectional, throttled).
    // Enhanced preload feature for smoother gallery browsing.
    LaunchedEffect(gridState, flatGridItems, flatGridBuiltColumns, activePagingItems, thumbSize, maxLineSpan, isScrollbarDragging) {
        snapshotFlow {
            Triple(gridState.firstVisibleItemIndex, gridState.isScrollInProgress, isScrollbarDragging)
        }
            .debounce(120)
            .collectLatest { (firstIndex, isScrolling, dragging) ->
                if (maxLineSpan >= 28 && flatGridBuiltColumns != maxLineSpan) {
                    return@collectLatest
                }
                val prefetchPolicy = thumbnailPrefetchPolicy(maxLineSpan, isScrolling)
                if (dragging) {
                    return@collectLatest
                }
                val totalItems = activePagingItems?.itemCount ?: flatGridItems.size
                if (totalItems == 0) return@collectLatest

                val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo
                    .maxOfOrNull { it.index }
                    ?: firstIndex

                val preloadStart = (firstIndex - maxLineSpan * prefetchPolicy.rowsBehind).coerceAtLeast(0)
                val preloadEnd = if (maxLineSpan >= 28) {
                    (firstIndex + maxLineSpan * prefetchPolicy.rowsAhead).coerceAtMost(totalItems - 1)
                } else {
                    (lastVisibleIndex + maxLineSpan * prefetchPolicy.rowsAhead).coerceAtMost(totalItems - 1)
                }
                val viewportCenterIndex = (firstIndex + lastVisibleIndex) / 2
                val preloadIndices = (preloadStart..preloadEnd)
                    .sortedWith(compareBy<Int> { abs(it - viewportCenterIndex) }.thenBy { it })
                val preloadRequests = preloadIndices.asSequence()
                    .mapNotNull { i ->
                        val item = if (activePagingItems != null) {
                            runCatching { activePagingItems.peek(i) }.getOrNull()
                        } else {
                            flatGridItems.getOrNull(i)
                        }

                        val media = item?.displayMedia() ?: return@mapNotNull null
                        if (gridImageLoader.hasGridMemoryCache(media.uri)) {
                            return@mapNotNull null
                        }

                        buildGridThumbnailRequest(context, media, thumbSize, includeDisplayHints = false)
                    }
                    .take(prefetchPolicy.maxRequests)
                    .toList()

                for (request in preloadRequests) {
                    gridImageLoader.execute(request)
                    delay(prefetchPolicy.delayMs)
                }
            }
    }

    // --- UI の描画 ---
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .nestedScroll(nestedScrollConnection)
    ) {
        val displayedFlatGridItems = flatGridItems
        val displayedMediaItemsOnly = mediaItemsOnly
        GalleryGridContent(
            gridState = gridState,
            flatGridItems = if (activePagingItems != null) emptyList() else displayedFlatGridItems,
            pagingItems = activePagingItems,
            maxLineSpan = maxLineSpan,
            thumbSize = thumbSize,
            imageLoader = gridImageLoader,
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
            onSelectionModeChanged = { setSelectionMode(it, "grid_content") },
            onLastSelectedIndexChanged = {
                logSelectionTrace("last_index_set source=grid_content before=$lastSelectedIndex after=$it")
                lastSelectedIndex = it
            },
            onZoomIn = { if (currentColumnIndex < columnOptions.size - 1) currentColumnIndex++ },
            onZoomOut = { if (currentColumnIndex > 0) currentColumnIndex-- },
            onZoomingStateChanged = { galleryState.isZooming = it },
            onSelectionEmptied = {
                logSelectionTrace("selection_empty source=grid_content count=${selectedUris.size}")
                setSelectionMode(false, "grid_content_empty")
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
                    logSelectionTrace("top_close count=${selectedUris.size} mode=$isSelectionMode")
                    setSelectionMode(false, "top_close")
                    clearSelectedUris("top_close")
                },
                onBulkFavorite = {
                    scope.launch {
                        val uris = selectedUris.keys.toList()
                        logSelectionTrace("bulk_favorite count=${uris.size} mode=$isSelectionMode")
                        val shouldFavorite = uris.any { uri -> metadataMap[uri]?.isFavorite != true }
                        galleryState.repository.bulkUpdateFavorite(uris, shouldFavorite)
                        setSelectionMode(false, "bulk_favorite")
                        clearSelectedUris("bulk_favorite")
                    }
                },
                onBulkDelete = {
                    scope.launch {
                        val uris = selectedUris.keys.toList()
                        logSelectionTrace("bulk_delete count=${uris.size} trash=$isTrashMode mode=$isSelectionMode")
                        if (isTrashMode) galleryState.repository.permanentlyDelete(uris)
                        else galleryState.repository.moveToTrash(uris)
                        setSelectionMode(false, "bulk_delete")
                        clearSelectedUris("bulk_delete")
                    }
                },
                onBulkEdit = { uris: List<String> ->
                    logSelectionTrace("bulk_edit count=${uris.size} mode=$isSelectionMode")
                    onBulkEdit?.invoke(uris)
                    setSelectionMode(false, "bulk_edit")
                    clearSelectedUris("bulk_edit")
                },
                onBulkMove = { uris: List<String> ->
                    logSelectionTrace("bulk_move count=${uris.size} mode=$isSelectionMode")
                    onBulkMove?.invoke(uris)
                    setSelectionMode(false, "bulk_move")
                    clearSelectedUris("bulk_move")
                },
                onUpdateThumbnail = { uri: String ->
                    scope.launch {
                        logSelectionTrace("set_thumbnail uri=${traceUri(uri)} mode=$isSelectionMode")
                        galleryState.repository.updateFolderThumbnail(title!!, uri)
                        setSelectionMode(false, "set_thumbnail")
                        clearSelectedUris("set_thumbnail")
                        val msg = context.getString(R.string.msg_set_thumbnail_success)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
    gridState: LazyStaggeredGridState,
    flatGridItems: List<GridItem>,
    pagingItems: LazyPagingItems<GridItem>?,
    maxLineSpan: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    totalTopBarHeight: Dp,
    totalBottomPadding: Dp,
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
    val colors = GalleryThemeTokens.colors
    val rawIsGridScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
    val context = LocalContext.current
    val selectionLongPressMs = remember(context) {
        context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)
            .getInt("selectionLongPressMs", AppDefaults.SELECTION_LONG_PRESS_MS)
            .coerceIn(150, 2000)
            .toLong()
    }
    var previousLineSpan by remember { mutableIntStateOf(maxLineSpan) }
    LaunchedEffect(maxLineSpan) {
        val previous = previousLineSpan
        if (previous != maxLineSpan) {
            val resetUniformLanes = previous == 2 && maxLineSpan in 3..4
            val anchorIndex = gridState.firstVisibleItemIndex
            val anchorOffset = gridState.firstVisibleItemScrollOffset
            if (resetUniformLanes) {
                gridState.requestScrollToItem(anchorIndex, anchorOffset)
            }
            Log.d(
                GRID_LAYOUT_TRACE,
                "columns_changed from=$previous to=$maxLineSpan anchor=$anchorIndex " +
                    "offset=$anchorOffset resetLanes=$resetUniformLanes"
            )
            previousLineSpan = maxLineSpan
        }
    }

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
                val dIdx = abs(curIdx - lastIdx).toFloat()
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
    // Bounds are read only from pointer handlers. Keeping them outside snapshot state prevents
    // every pixel of scroll from invalidating visible item compositions.
    val itemBoundsInRoot = remember(maxLineSpan) { mutableMapOf<Int, Rect>() }
    var gridBoundsInRoot by remember(maxLineSpan) { mutableStateOf<Rect?>(null) }
    var dragAutoScrollDelta by remember { mutableFloatStateOf(0f) }
    var dragSelectionBase by remember { mutableStateOf<Set<String>?>(null) }
    var dragSelectionStartIndex by remember { mutableIntStateOf(-1) }
    var dragSelectionLastIndex by remember { mutableIntStateOf(-1) }
    var dragSelectionShouldSelect by remember { mutableStateOf(true) }
    var dragSelectionActive by remember { mutableStateOf(false) }
    var dragSelectionDirection by remember { mutableIntStateOf(0) }
    var dragAutoScrollEdgeStartedAt by remember { mutableLongStateOf(0L) }
    var dragAutoScrollDirection by remember { mutableIntStateOf(0) }
    var dragAutoScrollEdgeDistance by remember { mutableFloatStateOf(0f) }
    var dragAutoScrollOutsideDistance by remember { mutableFloatStateOf(0f) }
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

    fun gridItemAtIndex(index: Int): GridItem? {
        if (index < 0) return null
        return if (pagingItems != null) {
            if (index >= pagingItems.itemCount) null else runCatching { pagingItems.peek(index) }.getOrNull()
        } else {
            flatGridItems.getOrNull(index)
        }
    }

    fun selectableUrisAtGridIndex(index: Int): List<String> = when (val item = gridItemAtIndex(index)) {
        is GridItem.Media -> listOf(item.data.uri)
        is GridItem.SimilarGroup -> item.items.map { it.uri }
        else -> emptyList()
    }

    fun mediaIndexAtGridPosition(position: Offset, allowNearest: Boolean = false): Int? {
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        val hit = visibleItems.firstOrNull { item ->
            position.x >= item.offset.x &&
                position.x <= item.offset.x + item.size.width &&
                position.y >= item.offset.y &&
                position.y <= item.offset.y + item.size.height &&
                selectableUrisAtGridIndex(item.index).isNotEmpty()
        }
        if (hit != null) return hit.index

        if (!allowNearest) return null
        return visibleItems
            .filter { selectableUrisAtGridIndex(it.index).isNotEmpty() }
            .minByOrNull { item -> abs(position.y - (item.offset.y + item.size.height / 2f)) }
            ?.index
    }

    fun mediaIndexAtRootPosition(position: Offset, allowNearest: Boolean = false): Int? {
        val hit = itemBoundsInRoot.entries.firstOrNull { (_, bounds) -> bounds.contains(position) }
        if (hit != null) return hit.key

        if (allowNearest && itemBoundsInRoot.isNotEmpty()) {
            return itemBoundsInRoot.minByOrNull { (_, bounds) ->
                val horizontalDistance = when {
                    position.x < bounds.left -> bounds.left - position.x
                    position.x > bounds.right -> position.x - bounds.right
                    else -> 0f
                }
                val verticalDistance = when {
                    position.y < bounds.top -> bounds.top - position.y
                    position.y > bounds.bottom -> position.y - bounds.bottom
                    else -> 0f
                }
                horizontalDistance * horizontalDistance + verticalDistance * verticalDistance
            }?.key
        }

        val gridPosition = gridBoundsInRoot?.let { bounds ->
            Offset(position.x - bounds.left, position.y - bounds.top)
        }
        gridPosition?.let { localPosition ->
            mediaIndexAtGridPosition(localPosition, allowNearest)?.let { return it }
        }

        return null
    }

    fun applySelectionPreview(
        baseSelection: Set<String>,
        startGridIndex: Int,
        endGridIndex: Int,
        shouldSelect: Boolean,
        source: String,
        activateSelectionMode: Boolean = true
    ) {
        val beforeCount = selectedUris.size
        selectedUris.clear()
        baseSelection.forEach { selectedUris[it] = true }
        val start = minOf(startGridIndex, endGridIndex)
        val end = maxOf(startGridIndex, endGridIndex)
        for (index in start..end) {
            selectableUrisAtGridIndex(index).forEach { uri ->
                if (shouldSelect) selectedUris[uri] = true else selectedUris.remove(uri)
            }
        }
        logSelectionTrace(
            "range_preview source=$source start=$startGridIndex end=$endGridIndex " +
                "shouldSelect=$shouldSelect base=${baseSelection.size} before=$beforeCount " +
                "after=${selectedUris.size} mode=$isSelectionMode first=${traceUri(selectedUris.keys.firstOrNull())}"
        )
        if (activateSelectionMode) {
            if (selectedUris.isEmpty()) onSelectionEmptied() else onSelectionModeChanged(true)
        }
    }

    fun dragAutoScrollSpeed(edgeDistance: Float, outsideDistance: Float, edgeZone: Float, holdMs: Long): Float {
        val insideEdge = edgeDistance.coerceIn(0f, edgeZone)
        val holdBoost = (holdMs / 1000f * 6f).coerceAtMost(30f)
        return (8f + insideEdge * 0.08f + outsideDistance * 0.14f + holdBoost).coerceAtMost(120f)
    }

    fun stopDragAutoScroll() {
        val wasDragSelectionActive = dragSelectionActive
        val shouldActivateSelectionMode = wasDragSelectionActive && selectedUris.isNotEmpty()
        if (dragSelectionActive || dragSelectionStartIndex >= 0 || dragAutoScrollDelta != 0f) {
            logSelectionTrace(
                "drag_end start=$dragSelectionStartIndex last=$dragSelectionLastIndex " +
                    "autoDelta=$dragAutoScrollDelta count=${selectedUris.size} mode=$isSelectionMode"
            )
        }
        dragAutoScrollDelta = 0f
        dragSelectionBase = null
        dragSelectionStartIndex = -1
        dragSelectionLastIndex = -1
        dragSelectionActive = false
        dragSelectionDirection = 0
        dragAutoScrollEdgeStartedAt = 0L
        dragAutoScrollDirection = 0
        dragAutoScrollEdgeDistance = 0f
        dragAutoScrollOutsideDistance = 0f
        if (wasDragSelectionActive) {
            logSelectionTrace(
                "drag_commit mode=${if (shouldActivateSelectionMode) "activate" else "clear"} " +
                    "count=${selectedUris.size}"
            )
            if (shouldActivateSelectionMode) onSelectionModeChanged(true) else onSelectionEmptied()
        }
    }

    LaunchedEffect(dragAutoScrollDelta) {
        while (dragAutoScrollDelta != 0f) {
            val direction = when {
                dragAutoScrollDirection < 0 -> -1
                dragAutoScrollDirection > 0 -> 1
                dragAutoScrollDelta < 0f -> -1
                else -> 1
            }
            val holdMs = if (dragAutoScrollEdgeStartedAt > 0L) {
                System.currentTimeMillis() - dragAutoScrollEdgeStartedAt
            } else {
                0L
            }
            val liveSpeed = dragAutoScrollSpeed(
                dragAutoScrollEdgeDistance,
                dragAutoScrollOutsideDistance,
                160f,
                holdMs
            )
            val delta = direction * liveSpeed
            val firstBefore = gridState.firstVisibleItemIndex
            val offsetBefore = gridState.firstVisibleItemScrollOffset
            gridState.scrollBy(delta)
            val baseSelection = dragSelectionBase
            val startIndex = dragSelectionStartIndex
            val edgeIndex = gridState.layoutInfo.visibleItemsInfo
                .map { it.index }
                .filter { selectableUrisAtGridIndex(it).isNotEmpty() }
                .let { visible ->
                    if (delta < 0f) visible.minOrNull() else visible.maxOrNull()
                }
            if (baseSelection != null && startIndex >= 0 && edgeIndex != null && edgeIndex != dragSelectionLastIndex) {
                applySelectionPreview(
                    baseSelection,
                    startIndex,
                    edgeIndex,
                    dragSelectionShouldSelect,
                    "auto_scroll",
                    activateSelectionMode = false
                )
                dragSelectionLastIndex = edgeIndex
            }
            logSelectionTrace(
                "drag_auto_scroll delta=${delta.roundToInt()} firstBefore=$firstBefore " +
                    "offsetBefore=$offsetBefore firstAfter=${gridState.firstVisibleItemIndex} " +
                    "offsetAfter=${gridState.firstVisibleItemScrollOffset} edge=$edgeIndex " +
                    "last=$dragSelectionLastIndex active=$dragSelectionActive holdMs=$holdMs " +
                    "edgeDistance=${dragAutoScrollEdgeDistance.roundToInt()} " +
                    "outside=${dragAutoScrollOutsideDistance.roundToInt()}"
            )
            delay(16)
        }
    }

    fun toggleSelection(media: GridItem.Media) {
        val uri = media.data.uri
        val wasSelected = selectedUris.containsKey(uri)
        logSelectionTrace(
            "toggle_before uri=${traceUri(uri)} index=${media.index} wasSelected=$wasSelected " +
                "count=${selectedUris.size} mode=$isSelectionMode selectOnTap=$selectOnTap"
        )
        if (wasSelected) {
            selectedUris.remove(uri)
            if (selectedUris.isEmpty()) onSelectionEmptied()
        } else {
            selectedUris[uri] = true
            onLastSelectedIndexChanged(media.index)
            onSelectionModeChanged(true)
        }
        logSelectionTrace(
            "toggle_after uri=${traceUri(uri)} index=${media.index} " +
                "count=${selectedUris.size} mode=$isSelectionMode"
        )
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
        val shouldToggle = selectOnTap || isSelectionMode
        logSelectionTrace(
            "tap uri=${traceUri(media.data.uri)} index=${media.index} action=${if (shouldToggle) "toggle" else "open"} " +
                "mode=$isSelectionMode count=${selectedUris.size} selectOnTap=$selectOnTap"
        )
        if (shouldToggle) toggleSelection(media) else openMedia(media)
    }

    fun toggleSimilarGroup(group: GridItem.SimilarGroup) {
        val groupUris = group.items.map { it.uri }
        val shouldSelect = groupUris.any { it !in selectedUris }
        groupUris.forEach { uri ->
            if (shouldSelect) selectedUris[uri] = true else selectedUris.remove(uri)
        }
        logSelectionTrace(
            "similar_group_toggle groupSize=${groupUris.size} shouldSelect=$shouldSelect " +
                "count=${selectedUris.size} mode=$isSelectionMode"
        )
        if (selectedUris.isEmpty()) onSelectionEmptied() else onSelectionModeChanged(true)
    }

    fun openMediaFromSimilarGroup(group: GridItem.SimilarGroup, media: MediaData) {
        val groupItems = group.items
        val index = groupItems.indexOfFirst { it.uri == media.uri }
        if (index >= 0) {
            onImageClick(index, groupItems)
        } else {
            onImageClick(0, listOf(media))
        }
        onPageChangedInViewer(media.uri)
    }

    fun beginDragSelection(startGridIndex: Int): Pair<Set<String>, Boolean> {
        val startUris = selectableUrisAtGridIndex(startGridIndex)
        if (startUris.isEmpty()) {
            logSelectionTrace(
                "drag_begin_miss gridIndex=$startGridIndex count=${selectedUris.size} mode=$isSelectionMode"
            )
            return selectedUris.keys.toSet() to true
        }
        val baseSelection = selectedUris.keys.toSet()
        val shouldSelect = startUris.any { it !in baseSelection }
        dragSelectionActive = true
        dragSelectionBase = baseSelection
        dragSelectionStartIndex = startGridIndex
        dragSelectionLastIndex = startGridIndex
        dragSelectionShouldSelect = shouldSelect
        dragSelectionDirection = 0
        logSelectionTrace(
            "drag_begin gridIndex=$startGridIndex uri=${traceUri(startUris.firstOrNull())} groupSize=${startUris.size} " +
                "base=${baseSelection.size} shouldSelect=$shouldSelect mode=$isSelectionMode"
        )
        applySelectionPreview(
            baseSelection,
            startGridIndex,
            startGridIndex,
            shouldSelect,
            "drag_begin",
            activateSelectionMode = false
        )
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
        var edgeIndex: Int? = null
        val wasAutoScrolling = dragAutoScrollDelta != 0f
        dragSelectionBase = baseSelection
        dragSelectionStartIndex = startGridIndex
        dragSelectionShouldSelect = shouldSelect
        dragSelectionActive = true
        val now = System.currentTimeMillis()
        fun edgeHoldMs(direction: Int): Long {
            if (dragAutoScrollDirection != direction || dragAutoScrollEdgeStartedAt == 0L) {
                dragAutoScrollDirection = direction
                dragAutoScrollEdgeStartedAt = now
                return 0L
            }
            return now - dragAutoScrollEdgeStartedAt
        }
        if (gridBounds != null) {
            val edgeZone = 160f
            val topDistance = (gridBounds.top + edgeZone - rootPosition.y).coerceAtLeast(0f)
            val bottomDistance = (rootPosition.y - (gridBounds.bottom - edgeZone)).coerceAtLeast(0f)
            val topOutside = (gridBounds.top - rootPosition.y).coerceAtLeast(0f)
            val bottomOutside = (rootPosition.y - gridBounds.bottom).coerceAtLeast(0f)
            val visibleMediaIndices = gridState.layoutInfo.visibleItemsInfo
                .map { it.index }
                .filter { selectableUrisAtGridIndex(it).isNotEmpty() }
            when {
                topDistance > 0f -> {
                    edgeIndex = visibleMediaIndices.minOrNull()
                    val holdMs = edgeHoldMs(-1)
                    dragAutoScrollEdgeDistance = topDistance
                    dragAutoScrollOutsideDistance = topOutside
                    val speed = dragAutoScrollSpeed(topDistance, topOutside, edgeZone, holdMs)
                    logSelectionTrace(
                        "drag_edge direction=top speed=${speed.roundToInt()} edge=$edgeIndex " +
                            "rootY=${rootPosition.y.roundToInt()} edgeDistance=${topDistance.roundToInt()} " +
                            "outside=${topOutside.roundToInt()} holdMs=$holdMs start=$startGridIndex last=$lastGridIndex " +
                            "kept=$dragSelectionLastIndex"
                    )
                    dragAutoScrollDelta = -speed
                }
                bottomDistance > 0f -> {
                    edgeIndex = visibleMediaIndices.maxOrNull()
                    val holdMs = edgeHoldMs(1)
                    dragAutoScrollEdgeDistance = bottomDistance
                    dragAutoScrollOutsideDistance = bottomOutside
                    val speed = dragAutoScrollSpeed(bottomDistance, bottomOutside, edgeZone, holdMs)
                    logSelectionTrace(
                        "drag_edge direction=bottom speed=${speed.roundToInt()} edge=$edgeIndex " +
                            "rootY=${rootPosition.y.roundToInt()} edgeDistance=${bottomDistance.roundToInt()} " +
                            "outside=${bottomOutside.roundToInt()} holdMs=$holdMs start=$startGridIndex last=$lastGridIndex " +
                            "kept=$dragSelectionLastIndex"
                    )
                    dragAutoScrollDelta = speed
                }
                else -> {
                    dragAutoScrollDelta = 0f
                    dragAutoScrollEdgeStartedAt = 0L
                    dragAutoScrollDirection = 0
                    dragAutoScrollEdgeDistance = 0f
                    dragAutoScrollOutsideDistance = 0f
                }
            }
        } else {
            dragAutoScrollDelta = 0f
            dragAutoScrollEdgeStartedAt = 0L
            dragAutoScrollDirection = 0
            dragAutoScrollEdgeDistance = 0f
            dragAutoScrollOutsideDistance = 0f
        }

        val previousGridIndex = if (dragSelectionLastIndex >= 0) dragSelectionLastIndex else lastGridIndex
        val allowNearest = !wasAutoScrolling && dragAutoScrollDelta == 0f
        val currentGridIndex = edgeIndex ?: mediaIndexAtRootPosition(rootPosition, allowNearest = allowNearest)
        if (currentGridIndex == null) {
            logSelectionTrace(
                "drag_target_none rootY=${rootPosition.y.roundToInt()} previous=$previousGridIndex " +
                    "allowNearest=$allowNearest autoDelta=$dragAutoScrollDelta first=${gridState.firstVisibleItemIndex}"
            )
            return previousGridIndex
        }
        val currentDirection = when {
            currentGridIndex > startGridIndex -> 1
            currentGridIndex < startGridIndex -> -1
            else -> dragSelectionDirection
        }
        if (dragSelectionDirection == 0 && currentDirection != 0) {
            dragSelectionDirection = currentDirection
            logSelectionTrace(
                "drag_direction_set direction=$dragSelectionDirection start=$startGridIndex " +
                    "target=$currentGridIndex previous=$previousGridIndex autoDelta=$dragAutoScrollDelta"
            )
        }
        val isAutoSelecting = wasAutoScrolling || dragAutoScrollDelta != 0f || edgeIndex != null
        if (!isAutoSelecting && currentDirection != 0 && currentDirection != dragSelectionDirection) {
            dragSelectionDirection = currentDirection
            logSelectionTrace(
                "drag_direction_update direction=$dragSelectionDirection start=$startGridIndex " +
                    "target=$currentGridIndex previous=$previousGridIndex autoDelta=$dragAutoScrollDelta"
            )
        }
        val reversedDuringAutoScroll = isAutoSelecting && dragSelectionDirection != 0 && when (dragSelectionDirection) {
            1 -> currentGridIndex < previousGridIndex
            -1 -> currentGridIndex > previousGridIndex
            else -> false
        }
        if (reversedDuringAutoScroll) {
            logSelectionTrace(
                "drag_target_blocked_reverse target=$currentGridIndex previous=$previousGridIndex " +
                    "direction=$dragSelectionDirection edge=$edgeIndex autoDelta=$dragAutoScrollDelta " +
                    "first=${gridState.firstVisibleItemIndex}"
            )
            return previousGridIndex
        }
        if (currentGridIndex != previousGridIndex) {
            logSelectionTrace(
                "drag_target target=$currentGridIndex previous=$previousGridIndex " +
                    "edge=$edgeIndex allowNearest=$allowNearest autoDelta=$dragAutoScrollDelta " +
                    "direction=$dragSelectionDirection " +
                    "first=${gridState.firstVisibleItemIndex}"
            )
            applySelectionPreview(
                baseSelection,
                startGridIndex,
                currentGridIndex,
                shouldSelect,
                "drag_move",
                activateSelectionMode = false
            )
            dragSelectionLastIndex = currentGridIndex
            return currentGridIndex
        }
        return previousGridIndex
    }

    key(maxLineSpan == 2) {
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(maxLineSpan),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(end = dimensionResource(R.dimen.grid_side_padding))
            .onGloballyPositioned { coordinates ->
                if (!isScrollbarDragging) {
                    gridBoundsInRoot = coordinates.boundsInRoot()
                }
            }
            .then(
                if (maxLineSpan >= 28) {
                    Modifier.pointerInput(maxLineSpan, selectionLongPressMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var movedBeforeLongPress = false
                    val longPressReached = withTimeoutOrNull(selectionLongPressMs) {
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

                            if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop &&
                                abs(change.position.x - down.position.x) > abs(change.position.y - down.position.y)) {
                                // Horizontal drag detected before long press: likely a drawer swipe
                                movedBeforeLongPress = true
                                return@withTimeoutOrNull false
                            }

                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                movedBeforeLongPress = true
                                return@withTimeoutOrNull false
                            }
                        }
                    } == null

                    if (!longPressReached) {
                        if (movedBeforeLongPress) {
                            logSelectionTrace(
                                "grid_press_moved_before_long_press x=${down.position.x.roundToInt()} " +
                                    "y=${down.position.y.roundToInt()} longPressMs=$selectionLongPressMs"
                            )
                        }
                        return@awaitEachGesture
                    }

                    val rootDown = gridBoundsInRoot?.let { bounds ->
                        Offset(bounds.left + down.position.x, bounds.top + down.position.y)
                    }
                    val dragRootDown = rootDown
                    if (dragRootDown == null) {
                        logSelectionTrace(
                            "grid_long_press_miss rootY=null " +
                                "first=${gridState.firstVisibleItemIndex} visible=${itemBoundsInRoot.size}"
                        )
                        return@awaitEachGesture
                    }
                    val startGridIndex = mediaIndexAtRootPosition(dragRootDown, allowNearest = false)
                    if (startGridIndex == null) {
                        logSelectionTrace(
                            "grid_long_press_miss rootY=${dragRootDown.y.roundToInt()} " +
                                "first=${gridState.firstVisibleItemIndex} visible=${itemBoundsInRoot.size}"
                        )
                        return@awaitEachGesture
                    }
                    val dragStartGridIndex: Int = startGridIndex

                    logSelectionTrace(
                        "grid_long_press start=$dragStartGridIndex rootY=${dragRootDown.y.roundToInt()} " +
                            "first=${gridState.firstVisibleItemIndex} longPressMs=$selectionLongPressMs"
                    )
                    val (baseSelection, shouldSelect) = beginDragSelection(dragStartGridIndex)
                    var lastGridIndex: Int = dragStartGridIndex
                    down.consume()

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            val rootPosition = gridBoundsInRoot?.let { bounds ->
                                Offset(bounds.left + change.position.x, bounds.top + change.position.y)
                            }
                            if (rootPosition != null) {
                                lastGridIndex = updateDragSelection(
                                    rootPosition,
                                    baseSelection,
                                    dragStartGridIndex,
                                    lastGridIndex,
                                    shouldSelect
                                )
                            } else {
                                logSelectionTrace(
                                    "grid_drag_missing_bounds start=$dragStartGridIndex last=$lastGridIndex " +
                                        "first=${gridState.firstVisibleItemIndex}"
                                )
                            }
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        logSelectionTrace(
                            "grid_drag_pointer_end start=$dragStartGridIndex last=$lastGridIndex " +
                                "autoDelta=$dragAutoScrollDelta first=${gridState.firstVisibleItemIndex}"
                        )
                        stopDragAutoScroll()
                    }
                    }
                    }
                } else {
                    Modifier
                }
            )
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
                    if (item is GridItem.Header) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                }
            ) { index ->
                val item = pagingItems[index]
                if (item == null) {
                    PagingPlaceholderGridItem(columnCount = maxLineSpan)
                    return@items
                }
                GridItemRenderer(
                    item = item,
                    modifier = if (maxLineSpan <= 4) Modifier.animateItem() else Modifier,
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
                    selectionLongPressMs = selectionLongPressMs,
                    highlightUri = highlightUri,
                    onMediaBoundsChanged = { gridIndex, bounds ->
                        if (!isScrollbarDragging) {
                            if (bounds == null) itemBoundsInRoot.remove(gridIndex) else itemBoundsInRoot[gridIndex] = bounds
                        }
                    },
                    onMediaClick = ::handleTap,
                    onSimilarGroupToggle = ::toggleSimilarGroup,
                    onSimilarMediaClick = ::openMediaFromSimilarGroup,
                    isSelectionInteractionActive = isSelectionMode || selectOnTap,
                    onDragSelectionStart = { gridIndex -> beginDragSelection(gridIndex) },
                    onDragSelectionMove = { rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect ->
                        updateDragSelection(rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect)
                    },
                    onDragSelectionEnd = ::stopDragAutoScroll
                )
            }
        } else {
            itemsIndexed(
                items = flatGridItems,
                key = { _, item -> item.key },
                contentType = { _, item -> if (item is GridItem.Header) "header" else "media" },
                span = { _, item ->
                    if (item is GridItem.Header) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                }
            ) { index, item ->
                GridItemRenderer(
                    item = item,
                    modifier = if (maxLineSpan <= 4) Modifier.animateItem() else Modifier,
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
                    selectionLongPressMs = selectionLongPressMs,
                    highlightUri = highlightUri,
                    onMediaBoundsChanged = { gridIndex, bounds ->
                        if (!isScrollbarDragging) {
                            if (bounds == null) itemBoundsInRoot.remove(gridIndex) else itemBoundsInRoot[gridIndex] = bounds
                        }
                    },
                    onMediaClick = ::handleTap,
                    onSimilarGroupToggle = ::toggleSimilarGroup,
                    onSimilarMediaClick = ::openMediaFromSimilarGroup,
                    isSelectionInteractionActive = isSelectionMode || selectOnTap,
                    onDragSelectionStart = { gridIndex -> beginDragSelection(gridIndex) },
                    onDragSelectionMove = { rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect ->
                        updateDragSelection(rootPosition, baseSelection, startGridIndex, lastGridIndex, shouldSelect)
                    },
                    onDragSelectionEnd = ::stopDragAutoScroll
                )
            }
        }
    }
    }
}

@Composable
private fun PagingPlaceholderGridItem(columnCount: Int) {
    val colors = GalleryThemeTokens.colors
    val placeholderHeight = dimensionResource(R.dimen.grid_placeholder_height)
    val horizontalPadding = dimensionResource(R.dimen.spacing_medium)
    val verticalPadding = dimensionResource(R.dimen.spacing_small)
    val cornerRadius = dimensionResource(R.dimen.radius_large)
    val gapPadding = if (columnCount > 1) gridItemGapPadding(columnCount) else 0.dp
    val itemModifier = remember(
        columnCount,
        placeholderHeight,
        horizontalPadding,
        verticalPadding,
        cornerRadius,
        gapPadding
    ) {
        Modifier
            .fillMaxWidth()
            .then(
                if (columnCount > 1) {
                    Modifier.aspectRatio(1f)
                } else {
                    Modifier
                        .height(placeholderHeight)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                        .clip(RoundedCornerShape(cornerRadius))
                }
            )
            .padding(gapPadding)
            .then(
                if (columnCount in 3..4) Modifier.animateContentSize(tween(durationMillis = 220)) else Modifier
            )
            .then(if (columnCount in 2..27) Modifier.clip(RoundedCornerShape(1.dp)) else Modifier)
    }
    Box(
        modifier = itemModifier.background(colors.surfaceVariant)
    )
}

private fun Modifier.highlightFrame(
    isHighlighted: Boolean,
    color: Color,
    width: Dp,
    cornerRadius: Dp
): Modifier {
    if (!isHighlighted) return this
    return drawWithContent {
        drawContent()
        val strokeWidth = width.toPx()
        val inset = strokeWidth / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(
                width = (size.width - strokeWidth).coerceAtLeast(0f),
                height = (size.height - strokeWidth).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            style = Stroke(strokeWidth)
        )
    }
}

@Composable
private fun gridItemGapPadding(columnCount: Int): Dp {
    return when {
        columnCount >= 28 -> dimensionResource(R.dimen.grid_gap_small)
        columnCount >= 8 -> dimensionResource(R.dimen.grid_gap_medium)
        else -> dimensionResource(R.dimen.grid_gap_large)
    }
}

private fun mediaGridAspectRatio(media: MediaData, columnCount: Int): Float {
    if (columnCount != 2) return 1f
    if (media.width <= 0 || media.height <= 0) return 1f
    return (media.width.toFloat() / media.height.toFloat()).coerceIn(0.56f, 1.78f)
}

@Composable
private fun GridItemRenderer(
    item: GridItem,
    modifier: Modifier = Modifier,
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
    selectionLongPressMs: Long,
    highlightUri: String?,
    onMediaBoundsChanged: (Int, Rect?) -> Unit,
    onMediaClick: (GridItem.Media) -> Unit,
    onSimilarGroupToggle: (GridItem.SimilarGroup) -> Unit,
    onSimilarMediaClick: (GridItem.SimilarGroup, MediaData) -> Unit,
    isSelectionInteractionActive: Boolean,
    onDragSelectionStart: (Int) -> Pair<Set<String>, Boolean>,
    onDragSelectionMove: (Offset, Set<String>, Int, Int, Boolean) -> Int,
    onDragSelectionEnd: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    when (item) {
        is GridItem.Header -> {
            Text(
                text = item.title,
                color = colors.primaryText,
                modifier = modifier.padding(dimensionResource(R.dimen.spacing_medium)),
                style = MaterialTheme.typography.titleLarge
            )
        }
        is GridItem.Media -> {
            if (maxLineSpan >= 28) {
                DenseMediaGridItem(
                    media = item.data,
                    modifier = modifier,
                    gridIndex = gridIndex,
                    thumbSize = thumbSize,
                    imageLoader = imageLoader,
                    shouldLoadThumbnail = gridIndex in denseThumbnailStartIndex..denseThumbnailEndIndex,
                    isGridScrolling = isGridScrolling,
                    isScrollbarDragging = isScrollbarDragging,
                    isSelected = selectedUris.containsKey(item.data.uri),
                    isFavorite = metadataMap[item.data.uri]?.isFavorite == true,
                    isHighlighted = highlightUri == item.data.uri,
                    onBoundsChanged = onMediaBoundsChanged,
                    onClick = { onMediaClick(item) }
                )
            } else {
                MediaGridItemWrapper(
                    item = item,
                    modifier = modifier,
                    gridIndex = gridIndex,
                    isSelected = selectedUris.containsKey(item.data.uri),
                    metadataMap = metadataMap,
                    columnCount = maxLineSpan,
                    thumbSize = thumbSize,
                    imageLoader = imageLoader,
                    isGridScrolling = isGridScrolling,
                    isScrollbarDragging = isScrollbarDragging,
                    isHighlighted = highlightUri == item.data.uri,
                    selectionLongPressMs = selectionLongPressMs,
                    onBoundsChanged = onMediaBoundsChanged,
                    onClick = { onMediaClick(item) },
                    onDragSelectionStart = onDragSelectionStart,
                    onDragSelectionMove = onDragSelectionMove,
                    onDragSelectionEnd = onDragSelectionEnd
                )
            }
        }
        is GridItem.SimilarGroup -> {
            SimilarGroupGridItem(
                group = item,
                modifier = modifier,
                gridIndex = gridIndex,
                columnCount = maxLineSpan,
                thumbSize = thumbSize,
                imageLoader = imageLoader,
                isGridScrolling = isGridScrolling,
                isScrollbarDragging = isScrollbarDragging,
                selectedCount = item.items.count { selectedUris.containsKey(it.uri) },
                isHighlighted = highlightUri != null && item.items.any { it.uri == highlightUri },
                isSelectionInteractionActive = isSelectionInteractionActive,
                onBoundsChanged = onMediaBoundsChanged,
                onToggleSelection = { onSimilarGroupToggle(item) },
                onMediaClick = { media -> onSimilarMediaClick(item, media) }
            )
        }
    }
}

@Composable
private fun SimilarGroupGridItem(
    group: GridItem.SimilarGroup,
    modifier: Modifier = Modifier,
    gridIndex: Int,
    columnCount: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    isGridScrolling: Boolean,
    isScrollbarDragging: Boolean,
    selectedCount: Int,
    isHighlighted: Boolean,
    isSelectionInteractionActive: Boolean,
    onBoundsChanged: (Int, Rect?) -> Unit,
    onToggleSelection: () -> Unit,
    onMediaClick: (MediaData) -> Unit
) {
    val representative = group.items.firstOrNull() ?: return
    val context = LocalContext.current
    val colors = GalleryThemeTokens.colors
    val configuration = LocalConfiguration.current
    var expanded by remember(group.id) { mutableStateOf(false) }
    val model = remember(representative.uri, thumbSize) {
        buildGridThumbnailRequest(context, representative, thumbSize)
    }
    val isMemoryCached = remember(representative.uri, imageLoader, isGridScrolling) {
        imageLoader.hasGridMemoryCache(representative.uri)
    }
    val aspectRatio = if (columnCount == 2) {
        if (representative.width > 0 && representative.height > 0) {
            (representative.width.toFloat() / representative.height.toFloat()).coerceIn(0.56f, 1.78f)
        } else {
            1f
        }
    } else {
        1f
    }
    val popupWidth = (configuration.screenWidthDp.dp - 32.dp).coerceIn(
        dimensionResource(R.dimen.similar_group_popup_width_min),
        dimensionResource(R.dimen.similar_group_popup_width_max)
    )
    val visibleRows = minOf(2, (group.items.size + 3) / 4).coerceAtLeast(1)
    val popupTileSize = ((popupWidth - 32.dp) / 4).coerceAtLeast(
        dimensionResource(R.dimen.similar_group_popup_tile_size_min)
    )
    val popupGridHeight = visibleRows.toFloat() * popupTileSize.value + (visibleRows - 1) * 4
    val popupScrollState = rememberScrollState()

    DisposableEffect(gridIndex) {
        onDispose { onBoundsChanged(gridIndex, null) }
    }

    Box {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .padding(gridItemGapPadding(columnCount))
                .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small) / 4))
                .onGloballyPositioned { coordinates ->
                    if (!isScrollbarDragging) onBoundsChanged(gridIndex, coordinates.boundsInRoot())
                }
                .clickable {
                    if (isSelectionInteractionActive) onToggleSelection() else expanded = true
                }
                .background(colors.surfaceVariant)
                .highlightFrame(isHighlighted, colors.accent, dimensionResource(R.dimen.spacing_tiny) / 2, dimensionResource(R.dimen.spacing_tiny) / 2)
        ) {
            if (!isGridScrolling || isMemoryCached) {
                AsyncImage(
                    model = model,
                    imageLoader = imageLoader,
                    contentDescription = stringResource(
                        R.string.gallery_similar_group_content_description,
                        group.items.size
                    ),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Surface(
                color = colors.background.copy(alpha = GalleryAlphaTokens.Badge),
                contentColor = colors.primaryText,
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small)),
                modifier = Modifier.align(Alignment.TopEnd).padding(dimensionResource(R.dimen.spacing_tiny))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.similar_group_badge_padding_h), vertical = dimensionResource(R.dimen.similar_group_badge_padding_v)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.similar_group_badge_padding_v))
                ) {
                    Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(dimensionResource(R.dimen.similar_group_badge_icon_size)))
                    Text(group.items.size.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (selectedCount > 0) {
                Box(
                    modifier = Modifier.fillMaxSize().background(colors.background.copy(alpha = GalleryAlphaTokens.OverlaySelection)),
                    contentAlignment = Alignment.TopStart
                ) {
                    Surface(
                        color = colors.accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.padding(dimensionResource(R.dimen.spacing_tiny))
                    ) {
                        Text(
                            text = "$selectedCount/${group.items.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.similar_group_badge_padding_h), vertical = 2.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(popupWidth),
            properties = PopupProperties(focusable = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.spacing_small) + 2.dp, vertical = dimensionResource(R.dimen.spacing_small))) {
                Text(
                    text = stringResource(R.string.gallery_similar_group_title, group.items.size),
                    color = colors.primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = dimensionResource(R.dimen.spacing_small))
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(popupGridHeight.dp)
                        .verticalScroll(popupScrollState),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_tiny))
                ) {
                    group.items.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_tiny))
                        ) {
                            rowItems.forEach { media ->
                                AsyncImage(
                                    model = buildGridThumbnailRequest(context, media, 192),
                                    imageLoader = imageLoader,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(dimensionResource(R.dimen.spacing_tiny) / 2))
                                        .clickable {
                                            expanded = false
                                            onMediaClick(media)
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DenseMediaGridItem(
    media: MediaData,
    modifier: Modifier = Modifier,
    gridIndex: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    shouldLoadThumbnail: Boolean,
    isGridScrolling: Boolean,
    isScrollbarDragging: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean,
    isHighlighted: Boolean,
    onBoundsChanged: (Int, Rect?) -> Unit,
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
        imageLoader.hasGridMemoryCache(media.uri)
    }
    val shouldDrawThumbnail = isMemoryCached || (shouldLoadThumbnail && !isGridScrolling)
    val model = remember(media.uri, thumbSize) {
        buildGridThumbnailRequest(context, media, thumbSize)
    }
    DisposableEffect(gridIndex) {
        onDispose { onBoundsChanged(gridIndex, null) }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(gridItemGapPadding(28))
            .onGloballyPositioned { coordinates ->
                if (!isScrollbarDragging) {
                    onBoundsChanged(gridIndex, coordinates.boundsInRoot())
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(backgroundColor)
            .highlightFrame(isHighlighted, colors.accent, dimensionResource(R.dimen.spacing_hairline), 0.dp)
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
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(dimensionResource(R.dimen.spacing_hairline))
                    .size(dimensionResource(R.dimen.icon_size_favorite_micro))
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
    modifier: Modifier = Modifier,
    gridIndex: Int,
    isSelected: Boolean,
    metadataMap: Map<String, com.example.gallery.data.local.entity.MediaMetadataSummary>,
    columnCount: Int,
    thumbSize: Int,
    imageLoader: ImageLoader,
    isGridScrolling: Boolean,
    isScrollbarDragging: Boolean,
    isHighlighted: Boolean,
    selectionLongPressMs: Long,
    onBoundsChanged: (Int, Rect?) -> Unit,
    onClick: () -> Unit,
    onDragSelectionStart: (Int) -> Pair<Set<String>, Boolean>,
    onDragSelectionMove: (Offset, Set<String>, Int, Int, Boolean) -> Int,
    onDragSelectionEnd: () -> Unit
) {
    // メタデータの細かな再計算を避け、リスト参照ではなく直接値を取得する。
    val isFavorite = remember(metadataMap, item.data.uri) {
        derivedStateOf { metadataMap[item.data.uri]?.isFavorite == true }
    }.value

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
        selectionLongPressMs = selectionLongPressMs,
        onBoundsChanged = onBoundsChanged,
        onClick = onClick,
        onDragSelectionStart = onDragSelectionStart,
        onDragSelectionMove = onDragSelectionMove,
        onDragSelectionEnd = onDragSelectionEnd,
        modifier = modifier
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
    selectionLongPressMs: Long,
    onBoundsChanged: (Int, Rect?) -> Unit,
    onClick: () -> Unit,
    onDragSelectionStart: (Int) -> Pair<Set<String>, Boolean>,
    onDragSelectionMove: (Offset, Set<String>, Int, Int, Boolean) -> Int,
    onDragSelectionEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDenseGrid = columnCount >= 28
    val colors = GalleryThemeTokens.colors

    // --- 画像リクエストの構築 ---
    // model を固定し、再描画時のロード試行を最小限に抑える。
    val model = remember(media.uri, thumbSize, columnCount) {
        buildGridThumbnailRequest(context, media, thumbSize)
    }

    // ベース modifier を安定化し、頻繁に変わらない条件のみに依存する。
    val itemAspectRatio = remember(media.width, media.height, columnCount) {
        mediaGridAspectRatio(media, columnCount)
    }
    val itemModifier = modifier
        .fillMaxWidth()
        .then(
            if (columnCount > 1) {
                Modifier.aspectRatio(itemAspectRatio)
            } else {
                Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_medium), vertical = dimensionResource(R.dimen.spacing_small)).clip(RoundedCornerShape(dimensionResource(R.dimen.radius_large)))
            }
        )
        .padding(if (columnCount > 1) gridItemGapPadding(columnCount) else 0.dp)
        .then(
            if (columnCount in 3..4) {
                Modifier.animateContentSize(animationSpec = tween(durationMillis = 220))
            } else {
                Modifier
            }
        )
        .then(if (columnCount in 2..27) Modifier.clip(RoundedCornerShape(dimensionResource(R.dimen.radius_small) / 4)) else Modifier)

    val itemBackgroundColor = when {
        !isDenseGrid -> colors.surfaceVariant
        media.isVideo -> colors.accentSoft
        else -> colors.surfaceVariant
    }
    val isMemoryCached = remember(media.uri, imageLoader, isGridScrolling) {
        imageLoader.hasGridMemoryCache(media.uri)
    }


    // 強調表示と選択状態を graphicsLayer で処理し、レイアウト計算を避ける。
    val highlightCornerRadius = if (columnCount > 1) dimensionResource(R.dimen.radius_small) / 2 else dimensionResource(R.dimen.radius_large)
    val currentOnClick by rememberUpdatedState(onClick)
    val itemBoundsInRootRef = remember(gridIndex) { arrayOfNulls<Rect>(1) }
    val dragBaseSelectionRef = remember(gridIndex) { arrayOfNulls<Set<String>>(1) }
    val dragShouldSelectRef = remember(gridIndex) { BooleanArray(1) }
    val dragLastIndexRef = remember(gridIndex) { IntArray(1) { gridIndex } }
    val dragMoveCountRef = remember(gridIndex) { IntArray(1) }
    val suppressTapRef = remember(gridIndex) { BooleanArray(1) }
    val interactionSource = remember { MutableInteractionSource() }

    DisposableEffect(gridIndex) {
        onDispose {
            itemBoundsInRootRef[0] = null
            onBoundsChanged(gridIndex, null)
        }
    }

    Box(
        modifier = itemModifier
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                itemBoundsInRootRef[0] = bounds
                if (!isScrollbarDragging) {
                    onBoundsChanged(gridIndex, bounds)
                }
            }
            .pointerInput(gridIndex, selectionLongPressMs) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedAt = System.currentTimeMillis()
                    var firstMoveLogged = false
                    logSelectionTrace(
                        "tile_raw_down index=$gridIndex uri=${traceUri(media.uri)} " +
                            "x=${down.position.x.roundToInt()} y=${down.position.y.roundToInt()}"
                    )
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            logSelectionTrace(
                                "tile_raw_up index=$gridIndex held=${System.currentTimeMillis() - startedAt}ms " +
                                    "moved=$firstMoveLogged consumed=${change.isConsumed}"
                            )
                            break
                        }
                        if (!firstMoveLogged &&
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            firstMoveLogged = true
                            logSelectionTrace(
                                "tile_raw_move index=$gridIndex dx=${(change.position.x - down.position.x).roundToInt()} " +
                                    "dy=${(change.position.y - down.position.y).roundToInt()} consumed=${change.isConsumed}"
                            )
                        }
                    }
                }
            }
            .pointerInput(gridIndex, selectionLongPressMs) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        val (baseSelection, shouldSelect) = onDragSelectionStart(gridIndex)
                        dragBaseSelectionRef[0] = baseSelection
                        dragShouldSelectRef[0] = shouldSelect
                        dragLastIndexRef[0] = gridIndex
                        dragMoveCountRef[0] = 0
                        suppressTapRef[0] = true
                        val bounds = itemBoundsInRootRef[0]
                        logSelectionTrace(
                            "tile_drag_start index=$gridIndex uri=${traceUri(media.uri)} " +
                                "bounds=${bounds?.let { "${it.left.roundToInt()},${it.top.roundToInt()},${it.right.roundToInt()},${it.bottom.roundToInt()}" }} " +
                                "longPressMs=$selectionLongPressMs"
                        )
                    },
                    onDrag = { change, _ ->
                        val baseSelection = dragBaseSelectionRef[0] ?: return@detectDragGesturesAfterLongPress
                        val bounds = itemBoundsInRootRef[0]
                        if (bounds != null) {
                            val previousIndex = dragLastIndexRef[0]
                            val rootPosition = Offset(
                                bounds.left + change.position.x,
                                bounds.top + change.position.y
                            )
                            dragLastIndexRef[0] = onDragSelectionMove(
                                rootPosition,
                                baseSelection,
                                gridIndex,
                                previousIndex,
                                dragShouldSelectRef[0]
                            )
                            dragMoveCountRef[0] += 1
                            if (dragMoveCountRef[0] == 1 || dragLastIndexRef[0] != previousIndex) {
                                logSelectionTrace(
                                    "tile_drag_move index=$gridIndex event=${dragMoveCountRef[0]} " +
                                        "root=${rootPosition.x.roundToInt()},${rootPosition.y.roundToInt()} " +
                                        "target=${dragLastIndexRef[0]} previous=$previousIndex consumed=${change.isConsumed}"
                                )
                            }
                        } else {
                            logSelectionTrace("tile_drag_missing_bounds index=$gridIndex")
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        logSelectionTrace(
                            "tile_drag_end index=$gridIndex events=${dragMoveCountRef[0]} " +
                                "last=${dragLastIndexRef[0]} selected=${dragBaseSelectionRef[0]?.size ?: -1}"
                        )
                        dragBaseSelectionRef[0] = null
                        onDragSelectionEnd()
                    },
                    onDragCancel = {
                        logSelectionTrace(
                            "tile_drag_cancel index=$gridIndex events=${dragMoveCountRef[0]} " +
                                "last=${dragLastIndexRef[0]}"
                        )
                        dragBaseSelectionRef[0] = null
                        suppressTapRef[0] = false
                        onDragSelectionEnd()
                    }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (suppressTapRef[0]) {
                    logSelectionTrace("tile_click_suppressed index=$gridIndex")
                    suppressTapRef[0] = false
                } else {
                    logSelectionTrace("tile_click index=$gridIndex uri=${traceUri(media.uri)}")
                    currentOnClick()
                }
            }
            .background(itemBackgroundColor)
            .highlightFrame(isHighlighted, colors.accent, dimensionResource(R.dimen.spacing_tiny) / 2, highlightCornerRadius)
    ) {
        val shouldDrawImage = !isGridScrolling || isMemoryCached
        if (shouldDrawImage) {
            AsyncImage(
                model = model,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (columnCount == 1) ContentScale.FillWidth else ContentScale.Crop
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = if (isDenseGrid) GalleryAlphaTokens.Clock else GalleryAlphaTokens.LowContrast)),
                contentAlignment = Alignment.TopEnd
            ) {
                if (!isDenseGrid) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(dimensionResource(R.dimen.spacing_tiny) / 2).size(if (columnCount > 7) dimensionResource(R.dimen.icon_size_check_small) else dimensionResource(R.dimen.icon_size_check))
                    )
                }
            }
        }

        if (columnCount < 28) {
            if (isFavorite) {
                Icon(Icons.Default.Favorite, null, tint = colors.danger, modifier = Modifier.align(Alignment.BottomStart).padding(dimensionResource(R.dimen.spacing_tiny) / 2).size(if (columnCount > 7) dimensionResource(R.dimen.icon_size_favorite_small) else dimensionResource(R.dimen.icon_size_favorite)))
            }
            if (label != null && columnCount <= 7) {
                Text(
                    label,
                    color = colors.primaryText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensionResource(R.dimen.spacing_tiny) / 2)
                        .background(colors.background.copy(alpha = GalleryAlphaTokens.Muted), RoundedCornerShape(dimensionResource(R.dimen.radius_small) / 2))
                        .padding(horizontal = dimensionResource(R.dimen.spacing_tiny) / 2, vertical = 0.dp)
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
    val colors = GalleryThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = getTopBarOffset() }.zIndex(1f)) {
        if (title != null) {
            GalleryTopAppBar(
                title = title,
                navigationIcon = when {
                    onMenuClick != null -> Icons.Default.Menu
                    onBackClick != null -> Icons.AutoMirrored.Filled.ArrowBack
                    else -> null
                },
                navigationContentDescription = stringResource(if (onMenuClick != null) R.string.btn_open else R.string.btn_back),
                onNavigationClick = { (onMenuClick ?: onBackClick)?.invoke() },
                containerColor = colors.topBar,
                contentColor = colors.primaryText,
                actions = topBarActions
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.header_height)).background(colors.background.copy(alpha = GalleryAlphaTokens.Recommendation))) {
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
    title != null && title != stringResource(R.string.label_all_media) && selectedUris.size == 1
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
    val colors = GalleryThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = dimensionResource(R.dimen.spacing_medium)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
            Text(
                text = stringResource(R.string.trash_item_count, selectedCount),
                style = MaterialTheme.typography.titleLarge
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isTrashMode) { IconButton(onClick = onFavorite) { Icon(Icons.Default.Favorite, null, tint = colors.danger) } }
            var showOverflow by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showOverflow = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }, modifier = Modifier.background(colors.card)) {
        DropdownMenuItem(text = { Text(if (isTrashMode) stringResource(R.string.trash_permanently_delete) else stringResource(R.string.trash_move_to), color = colors.primaryText) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.primaryText) }, onClick = { showOverflow = false; onDelete() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit_move_folder), color = colors.primaryText) }, leadingIcon = { Icon(Icons.Default.Folder, null, tint = colors.primaryText) }, onClick = { showOverflow = false; onMove() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit_bulk_tags_rating), color = colors.primaryText) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = colors.primaryText) }, onClick = { showOverflow = false; onEdit() })
                    if (canSetThumbnail) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_set_folder_thumbnail), color = colors.primaryText) }, leadingIcon = { Icon(Icons.Default.FolderSpecial, null, tint = colors.primaryText) }, onClick = { showOverflow = false; onSetThumbnail() })
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryScrollbar(
    gridState: LazyStaggeredGridState,
    columnCount: Int,
    positionLabelItems: List<GridItem>,
    totalTopBarHeight: Dp,
    totalBottomPadding: Dp,
    isActive: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollbarDensity = LocalDensity.current
    val labelOffset = dimensionResource(R.dimen.scrollbar_label_offset)
    val thumbOffset = dimensionResource(R.dimen.spacing_tiny)
    val labelOffsetPx = with(scrollbarDensity) { labelOffset.toPx() }
    val thumbOffsetPx = with(scrollbarDensity) { thumbOffset.toPx() }
    val railOffsetPx = with(scrollbarDensity) { 2.dp.toPx() }
    var isPressed by remember { mutableStateOf(false) }
    val thumbWidth by animateDpAsState(
        targetValue = if (isPressed) dimensionResource(R.dimen.scrollbar_width_active) else dimensionResource(R.dimen.scrollbar_width_inactive),
        label = "scrollbarWidth"
    )
    // Avoid heavy full layoutInfo reads in derivedStateOf where possible.
    // Use firstVisibleItemIndex (lighter) for position; total and visible size still need layoutInfo but minimized.
    val totalItemsState = remember(gridState) { derivedStateOf { gridState.layoutInfo.totalItemsCount } }
    val visibleItemsSizeState = remember(gridState) { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size } }
    val firstVisibleIndexState = remember(gridState) { derivedStateOf { gridState.firstVisibleItemIndex } }
    val firstVisibleScrollOffsetState = remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemScrollOffset }
    }
    // Keep the latest requested index for the thumb preview. The grid follows at a
    // throttled rate while images and bounds work are paused by isScrollbarDragging.
    var scrollbarTargetIndex by remember { mutableIntStateOf(-1) }
    var scrollbarDateLabels by remember { mutableStateOf<List<String?>>(emptyList()) }
    LaunchedEffect(positionLabelItems) {
        val itemsSnapshot = positionLabelItems
        scrollbarDateLabels = emptyList()
        scrollbarDateLabels = withContext(Dispatchers.Default) {
            buildScrollbarDateLabels(itemsSnapshot, context)
        }
    }
    val thumbHeightRatioState = remember(gridState) { derivedStateOf {
        val total = totalItemsState.value; if (total == 0) 0.1f else (visibleItemsSizeState.value.toFloat() / total).coerceIn(0.1f, 1f)
    } }
    val scrollPositionState = remember(gridState) { derivedStateOf {
        val total = totalItemsState.value
        if (total <= 1) {
            0f
        } else if (!gridState.canScrollForward) {
            // In a staggered grid the first visible item is not necessarily the
            // final item at the bottom.  The actual scroll boundary is authoritative.
            1f
        } else if (!gridState.canScrollBackward) {
            0f
        } else {
            val previewPosition = if (isPressed && scrollbarTargetIndex >= 0) {
                scrollbarTargetIndex.toFloat()
            } else {
                val firstIndex = firstVisibleIndexState.value
                val firstItemHeight = gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == firstIndex }
                    ?.size
                    ?.height
                    ?.toFloat()
                    ?.coerceAtLeast(1f)
                    ?: 1f
                firstIndex + (firstVisibleScrollOffsetState.value / firstItemHeight)
                    .coerceIn(0f, 0.999f)
            }.coerceIn(0f, (total - 1).toFloat())
            previewPosition / (total - 1)
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
    var dragSessionId by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(dimensionResource(R.dimen.scrollbar_container_width))
            .padding(top = totalTopBarHeight, bottom = totalBottomPadding)
            .zIndex(5f)
    ) {
        // Keep the date label wide enough to be readable, while limiting pointer
        // capture to the rail at the far right so gallery scrolling still works.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(dimensionResource(R.dimen.scrollbar_touch_area_width))
                .onSizeChanged { containerHeight = it.height }
            .pointerInput(columnCount) {
                awaitEachGesture {
                    fun currentTotalItems() = totalItemsState.value
                    fun targetIndexForTouchY(y: Float, grabOffset: Float): Int {
                        val total = currentTotalItems()
                        if (total <= 1) return 0
                        val railHeight = size.height.toFloat().coerceAtLeast(1f)
                        val thumbHeight = railHeight * thumbHeightRatioState.value
                        val travelHeight = (railHeight - thumbHeight).coerceAtLeast(1f)
                        val effectiveY = (y - grabOffset).coerceIn(0f, travelHeight)
                        return ((effectiveY / travelHeight) * (total - 1)).roundToInt().coerceIn(0, total - 1)
                    }

                    fun isTopTouch(y: Float): Boolean {
                        return y <= 1f
                    }

                    fun isBottomTouch(y: Float): Boolean {
                        return y >= size.height - 1f
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initialItemCount = currentTotalItems().coerceAtLeast(1)
                    val railHeight = size.height.toFloat().coerceAtLeast(1f)
                    val initialThumbRatio = (visibleItemsSizeState.value.toFloat() / initialItemCount)
                        .coerceIn(0.1f, 1f)
                    val initialThumbHeight = railHeight * initialThumbRatio
                    val initialTravelHeight = (railHeight - initialThumbHeight).coerceAtLeast(0f)
                    val initialThumbTop = initialTravelHeight * scrollPositionState.value
                    val thumbHitSlop = 16.dp.toPx()
                    val startsOnThumb = down.position.y in
                        (initialThumbTop - thumbHitSlop)..(initialThumbTop + initialThumbHeight + thumbHitSlop)

                    if (startsOnThumb) {
                        down.consume()
                    }

                    val longPressTimeoutMs = minOf(viewConfiguration.longPressTimeoutMillis, 380L)
                    val dragActivated = startsOnThumb || withTimeoutOrNull(longPressTimeoutMs) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull false
                            if (!change.pressed) {
                                return@withTimeoutOrNull false
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop * 2.5f) {
                                return@withTimeoutOrNull false
                            }
                        }
                    } == null

                    if (!dragActivated) {
                        Log.d(SCROLLBAR_TRACE, "short_touch ignored y=${down.position.y.roundToInt()} timeoutMs=$longPressTimeoutMs")
                        return@awaitEachGesture
                    }

                    if (!startsOnThumb) {
                        down.consume()
                    }
                    isPressed = true
                    dragSessionId += 1
                    val sessionId = dragSessionId
                    isActive(true)
                    var lastTouchY = down.position.y
                    val grabOffsetY = if (startsOnThumb) {
                        (down.position.y - initialThumbTop).coerceIn(0f, initialThumbHeight)
                    } else {
                        initialThumbHeight / 2f
                    }
                    val initialIdx = if (startsOnThumb) {
                        gridState.firstVisibleItemIndex.coerceIn(0, initialItemCount - 1)
                    } else {
                        targetIndexForTouchY(lastTouchY, grabOffsetY)
                    }
                    scrollbarTargetIndex = initialIdx
                    var lastLiveRequestAt = 0L
                    var lastLiveRequestedIndex = gridState.firstVisibleItemIndex
                    val liveFollowIntervalMs = if (columnCount >= 7) 70L else 45L
                    val liveFollowMinDelta = maxOf(columnCount / 2, 1)
                    fun requestLiveFollow(target: Int, force: Boolean = false) {
                        val now = System.currentTimeMillis()
                        if (!force && target == lastLiveRequestedIndex) {
                            return
                        }
                        val deltaFromLast = abs(target - lastLiveRequestedIndex)
                        val sincePreviousMs = now - lastLiveRequestAt
                        if (!force) {
                            val enoughTimePassed = sincePreviousMs >= liveFollowIntervalMs
                            val enoughDistanceMoved = deltaFromLast >= liveFollowMinDelta
                            if (!enoughTimePassed && !enoughDistanceMoved) {
                                return
                            }
                        }
                        gridState.requestScrollToItem(target)
                        Log.d(
                            SCROLLBAR_TRACE,
                            "follow target=$target force=$force total=${currentTotalItems()} first=${gridState.firstVisibleItemIndex}"
                        )
                        lastLiveRequestAt = now
                        lastLiveRequestedIndex = target
                    }
                    Log.d(
                        SCROLLBAR_TRACE,
                        "drag_start directThumb=$startsOnThumb y=${lastTouchY.roundToInt()} target=$initialIdx " +
                            "grabOffset=${grabOffsetY.roundToInt()} total=${currentTotalItems()} columns=$columnCount"
                    )
                    if (!startsOnThumb) {
                        requestLiveFollow(initialIdx, force = true)
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastTouchY = change.position.y
                            if (!change.pressed) break
                            change.consume()
                            val latestTotal = currentTotalItems()
                            val newIdx = targetIndexForTouchY(lastTouchY, grabOffsetY)
                            val isEdgeTarget = newIdx == 0 || newIdx == latestTotal - 1
                            scrollbarTargetIndex = newIdx
                            requestLiveFollow(newIdx, force = isEdgeTarget)
                        }
                        val finalTotal = currentTotalItems()
                        val finalTarget = when {
                            isBottomTouch(lastTouchY) -> (finalTotal - 1).coerceAtLeast(0)
                            isTopTouch(lastTouchY) -> 0
                            else -> targetIndexForTouchY(lastTouchY, grabOffsetY)
                        }
                        if (finalTarget >= 0 && (!startsOnThumb || finalTarget != initialIdx)) {
                            gridState.requestScrollToItem(finalTarget)
                            if (finalTotal > 0 && finalTarget == finalTotal - 1) {
                                scope.launch {
                                    repeat(2) {
                                        delay(120)
                                        val latestTotal = totalItemsState.value
                                        if (latestTotal > 0) {
                                            gridState.requestScrollToItem(latestTotal - 1)
                                        }
                                    }
                                }
                            }
                        }
                        Log.d(
                            SCROLLBAR_TRACE,
                            "drag_end y=${lastTouchY.roundToInt()} target=$finalTarget total=$finalTotal"
                        )
                    } finally {
                        isPressed = false
                        scrollbarTargetIndex = -1
                        scope.launch {
                            delay(180)
                            if (dragSessionId == sessionId) {
                                isActive(false)
                            }
                        }
                    }
                }
            }
        )

        val positionLabel = currentPositionLabelState.value
        val colors = GalleryThemeTokens.colors
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(2.dp)
                .fillMaxHeight()
                .graphicsLayer { translationX = -thumbOffsetPx - railOffsetPx }
                .background(colors.divider.copy(alpha = 0.7f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(12.dp)
                .height(3.dp)
                .graphicsLayer { translationX = -thumbOffsetPx }
                .background(
                    if (!gridState.canScrollBackward) colors.accent else colors.divider,
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(12.dp)
                .height(3.dp)
                .graphicsLayer { translationX = -thumbOffsetPx }
                .background(
                    if (!gridState.canScrollForward) colors.accent else colors.divider,
                    CircleShape
                )
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.label_slot_top),
            tint = if (!gridState.canScrollBackward) colors.accent else colors.secondaryText,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 20.dp)
                .size(16.dp)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.label_slot_bottom),
            tint = if (!gridState.canScrollForward) colors.accent else colors.secondaryText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 2.dp, end = 20.dp)
                .size(16.dp)
        )
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
                    translationX = -labelOffsetPx
                }
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 128.dp)
                    .onSizeChanged { labelHeight = it.height },
                shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small)),
                color = colors.background.copy(alpha = GalleryAlphaTokens.Tooltip),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = positionLabel.orEmpty(),
                    color = colors.primaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensionResource(R.dimen.popup_padding_h),
                            vertical = dimensionResource(R.dimen.popup_padding_v)
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .width(thumbWidth)
                .fillMaxHeight()
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    val scrollPos = scrollPositionState.value
                    val heightRatio = thumbHeightRatioState.value
                    val currentThumbHeight = containerHeight * heightRatio
                    translationY = (containerHeight - currentThumbHeight) * scrollPos
                    translationX = -thumbOffsetPx
                    scaleY = heightRatio
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                }
                .clip(CircleShape)
                .background(if (isPressed) colors.accent else colors.primaryText.copy(alpha = 0.8f))
        )
    }
}


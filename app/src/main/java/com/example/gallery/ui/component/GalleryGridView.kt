package com.example.gallery.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.GroupingMode
import com.example.gallery.ui.MediaTypeFilter
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.DeviceFilter
import com.example.gallery.ui.SortMode
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import kotlin.math.roundToInt

@Composable
fun GalleryGridView(
    imageList: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    galleryState: GalleryState = com.example.gallery.ui.rememberGalleryState(LocalContext.current),
    onTabIconClick: ((String) -> Unit)? = null,
    clearSelectionSignal: Int = 0,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    title: String? = null,
    onBackClick: (() -> Unit)? = null,
    scrollToUri: String? = null, // 追加：戻り時にスクロールさせたいURI
    isFilterEnabled: Boolean = true, // 追加
    onPageChangedInViewer: (String) -> Unit = {}, // 追加：ビュワー内でのページ変更通知
    onBulkEdit: ((List<String>) -> Unit)? = null // 追加: 一括編集のリクエスト
) {
    val columnOptions = listOf(28, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) } // 初期は4列
    
    // 表示列数に応じて自動的にグループ化モードを変更
    LaunchedEffect(currentColumnIndex) {
        galleryState.groupingMode = when (columnOptions[currentColumnIndex]) {
            28 -> GroupingMode.YEAR
            7 -> GroupingMode.MONTH
            else -> GroupingMode.DAY
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    // var showZoomMenu by remember { mutableStateOf(false) }
    // var showFilterMenu by remember { mutableStateOf(false) }
    // var showAgeFilterMenu by remember { mutableStateOf(false) }
    // var showGroupingMenu by remember { mutableStateOf(false) }

    // 複数選択用の状態
    var isSelectionMode by remember { mutableStateOf(false) }
    var isZooming by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    // Lazy Loading 用の変数
    var visibleCount by remember { mutableIntStateOf(60) }
    
    // カラム切り替え時のローディング状態
    var isChangingColumns by remember { mutableStateOf(false) }

    // 戻り時のハイライト用
    var highlightUri by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    val context = LocalContext.current

    // トップバーの表示/非表示制御 (タイトル + 操作バーの両方を含める)
    val topTitleHeight = if (title != null) AppConstants.HeaderHeight else 0.dp
    val topActionsHeight = AppConstants.HeaderHeight
    
    val statusBarsHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalTopBarHeight = (if (title != null) topTitleHeight + statusBarsHeight else statusBarsHeight) + topActionsHeight
    
    val navBarsHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalBottomPadding = navBarsHeight + 100.dp

    val totalTopBarHeightPx = with(LocalDensity.current) { totalTopBarHeight.toPx() }
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = topBarOffsetHeightPx + delta
                if (!isSelectionMode) {
                    topBarOffsetHeightPx = newOffset.coerceIn(-totalTopBarHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    var showBulkEditDialog by remember { mutableStateOf(false) }

    // 外部からの選択解除要求を監視
    LaunchedEffect(clearSelectionSignal) {
        if (clearSelectionSignal > 0) {
            isSelectionMode = false
            selectedUris.clear()
        }
    }

    // モード変更を通知
    LaunchedEffect(isSelectionMode) {
        onSelectionModeChanged(isSelectionMode)
        if (isSelectionMode) topBarOffsetHeightPx = 0f 
    }

    // メタデータ（年齢制限など）をFlowで監視
    val allMetadata by galleryState.repository.getAllMetadataFlow().collectAsState(initial = emptyList())
    val metadataMap by remember(allMetadata) {
        derivedStateOf { allMetadata.associateBy { it.uri } }
    }

    val configuration = LocalConfiguration.current
    val deviceAspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    val filteredList by remember(galleryState.mediaTypeFilter, galleryState.ageRatingFilter, galleryState.deviceFilter, metadataMap, imageList) {
        derivedStateOf {
            val typeFiltered = if (galleryState.mediaTypeFilter == MediaTypeFilter.ALL) imageList.toList()
            else imageList.filter { item ->
                when (galleryState.mediaTypeFilter) {
                    MediaTypeFilter.IMAGE -> item.mimeType?.startsWith("image/") == true && !item.isGif
                    MediaTypeFilter.VIDEO -> item.isVideo
                    MediaTypeFilter.GIF -> item.isGif
                    else -> true
                }
            }

            val ageFiltered = if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) typeFiltered
            else {
                typeFiltered.filter { item ->
                    val meta = metadataMap[item.uri]
                    // メタデータがない場合はデフォルトの SFW として扱う
                    val rating = meta?.ageRating ?: "SFW"
                    
                    // 厳密なフィルタリング (指定されたレベルのみを表示)
                    when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                        else -> true
                    }
                }
            }

            val deviceFiltered = if (galleryState.deviceFilter == DeviceFilter.ALL) ageFiltered
            else {
                ageFiltered.filter { item ->
                    if (item.isVideo || item.width <= 0 || item.height <= 0) false
                    else {
                        val ratio = item.height.toFloat() / item.width.toFloat()
                        when (galleryState.deviceFilter) {
                            DeviceFilter.SMARTPHONE -> {
                                item.height > item.width && kotlin.math.abs(ratio - deviceAspectRatio) < 0.2f
                            }
                            DeviceFilter.PC -> {
                                val pcRatio = item.width.toFloat() / item.height.toFloat()
                                item.width > item.height && pcRatio > 1.3f
                            }
                            else -> true
                        }
                    }
                }
            }

            // 並び替え適用
            val sorted = when (galleryState.sortMode) {
                SortMode.DATE_ADDED -> deviceFiltered.sortedBy { it.dateAdded }
                SortMode.SIZE -> deviceFiltered.sortedBy { it.fileSize }
                SortMode.NAME -> deviceFiltered.sortedWith(compareBy({ 
                    val firstChar = it.fileName.firstOrNull()
                    if (firstChar != null && firstChar.isDigit()) false else true
                }, { it.fileName }))
            }
            if (galleryState.isAscending) sorted else sorted.reversed()
        }
    }

    // 各モードごとのグループ化計算を、現在のモードに応じてのみ行うように最適化
    val groupedForDisplay by remember(galleryState.groupingMode, filteredList, visibleCount, currentColumnIndex) {
        derivedStateOf {
            val is28ColumnMode = columnOptions[currentColumnIndex] == 28
            
            val baseGroups = when (galleryState.groupingMode) {
                GroupingMode.NONE -> listOf("All" to filteredList)
                GroupingMode.DAY -> {
                    filteredList.groupBy { 
                        val cal = Calendar.getInstance().apply { timeInMillis = it.dateAdded }
                        "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
                    }.toList()
                }
                GroupingMode.MONTH -> {
                    filteredList.groupBy { 
                        val cal = Calendar.getInstance().apply { timeInMillis = it.dateAdded }
                        "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月"
                    }.toList()
                }
                GroupingMode.YEAR -> {
                    filteredList.groupBy { 
                        val cal = Calendar.getInstance().apply { timeInMillis = it.dateAdded }
                        "${cal.get(Calendar.YEAR)}年"
                    }.toList()
                }
                GroupingMode.STORAGE -> {
                    filteredList.groupBy { item ->
                        if (item.uri.contains("emulated/0")) "内部ストレージ"
                        else if (item.uri.contains("sdcard") || item.uri.contains("-")) "SDカード・外部ストレージ"
                        else "その他"
                    }.toList()
                }
            }

            // 表示件数(visibleCount)で制限をかける
            var currentCount = 0
            val result = mutableListOf<Pair<String, List<MediaData>>>()
            
            for (group in baseGroups) {
                val header = group.first
                val items = group.second
                
                // 28列モードの時は1年につき8行（224枚）に制限し、まんべんなく抽選
                val processedItems = if (is28ColumnMode && galleryState.groupingMode == GroupingMode.YEAR && items.size > 224) {
                    val sampled = mutableListOf<MediaData>()
                    val step = items.size.toDouble() / 224.0
                    for (i in 0 until 224) {
                        sampled.add(items[(i * step).toInt().coerceAtMost(items.size - 1)])
                    }
                    sampled
                } else {
                    items
                }
                
                val itemsToTake = minOf(processedItems.size, (visibleCount - currentCount).coerceAtLeast(0))
                
                if (itemsToTake > 0) {
                    result.add(header to processedItems.take(itemsToTake))
                    currentCount += itemsToTake
                }
                if (currentCount >= visibleCount) break
            }
            result
        }
    }

    val currentFlatImageList = filteredList

    LaunchedEffect(currentColumnIndex) { 
        zoomScale = 1f 
        isChangingColumns = true
        delay(10) // 描画スレッドに譲る
        // 修正: 4列モードなどで無駄に大量読み込みしないよう、列数に応じた適切な初期値を設定
        visibleCount = when {
            columnOptions[currentColumnIndex] >= 28 -> 1500
            columnOptions[currentColumnIndex] >= 7 -> 500
            else -> 80 // 4列以下なら少なめに開始
        }
        delay(100)
        isChangingColumns = false
    }

    // scrollToUri が指定された場合、その画像の位置までスクロール
    LaunchedEffect(scrollToUri, filteredList, groupedForDisplay) {
        if (scrollToUri != null && filteredList.isNotEmpty()) {
            // グループ化（ヘッダー）を考慮した正確なグリッド上のインデックスを計算
            var targetGridIndex = -1
            var currentGridIndex = 0
            
            for (group in groupedForDisplay) {
                if (galleryState.groupingMode != GroupingMode.NONE) {
                    currentGridIndex++ // ヘッダーの分
                }
                val items = group.second
                val localIndex = items.indexOfFirst { it.uri == scrollToUri }
                if (localIndex != -1) {
                    targetGridIndex = currentGridIndex + localIndex
                    break
                }
                currentGridIndex += items.size
            }

            if (targetGridIndex != -1) {
                // 表示件数を拡張する必要があるか確認
                if (targetGridIndex >= visibleCount) {
                    visibleCount = targetGridIndex + 100
                }
                
                // スクロール実行（少し余裕を持って表示するため offset も考慮可能だがまずはシンプルに）
                gridState.scrollToItem(targetGridIndex)
                
                // ハイライト表示を開始
                highlightUri = scrollToUri
                delay(1200) // 1秒強表示
                if (highlightUri == scrollToUri) {
                    highlightUri = null
                }
            } else {
                // まだリストに読み込まれていない可能性があるため、visibleCountを一時的に増やして再試行
                if (visibleCount < filteredList.size) {
                    visibleCount += 200
                } else {
                    // Toast.makeText(context, "元のギャラリーにない画像のため、移動できません", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
            .background(AppConstants.BackgroundColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (isLoading || isChangingColumns) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnOptions[currentColumnIndex]), 
                userScrollEnabled = !isZooming && !isChangingColumns,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(currentFlatImageList, groupedForDisplay, currentColumnIndex) {
                        // 二本指タップ/ピンチ操作の検出を優先（fast path）
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    isZooming = true
                                    // ズームモードに入ったら現在のジェスチャーを消費
                                    event.changes.forEach { it.consume() }
                                } else if (event.changes.all { !it.pressed }) {
                                    isZooming = false
                                }
                            }
                        }
                    }
                    .pointerInput(currentFlatImageList, groupedForDisplay, currentColumnIndex) {
                        if (columnOptions[currentColumnIndex] >= 28) return@pointerInput // 28列モードでは選択無効
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var isLongPress = false
                                var firstItemIndex = -1
                                
                                // 長押し判定
                                val timeout = 1200L // 長押し時間をさらに延長
                                val startTime = System.currentTimeMillis()
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentTime = System.currentTimeMillis()
                                    
                                    if (currentTime - startTime > timeout && !isLongPress) {
                                        isLongPress = true
                                        // 開始位置のアイテムを特定
                                        val offset = down.position
                                        val beforePadding = gridState.layoutInfo.beforeContentPadding
                                        gridState.layoutInfo.visibleItemsInfo.find { 
                                            val itemTop = it.offset.y.toFloat() + beforePadding
                                            val itemBottom = itemTop + it.size.height
                                            offset.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() && 
                                            offset.y in itemTop..itemBottom
                                        }?.let { itemInfo ->
                                            val uri = itemInfo.key as? String
                                            if (uri != null && !uri.startsWith("header:")) {
                                                if (!isSelectionMode) isSelectionMode = true
                                                if (!selectedUris.contains(uri)) selectedUris.add(uri)
                                                firstItemIndex = currentFlatImageList.indexOfFirst { it.uri == uri }
                                            }
                                        }
                                    }
                                    
                                    if (isLongPress && firstItemIndex != -1) {
                                        val dragChange = event.changes.first()
                                        val offset = dragChange.position
                                        val beforePadding = gridState.layoutInfo.beforeContentPadding
                                        gridState.layoutInfo.visibleItemsInfo.find { 
                                            val itemTop = it.offset.y.toFloat() + beforePadding
                                            val itemBottom = itemTop + it.size.height
                                            offset.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() && 
                                            offset.y in itemTop..itemBottom
                                        }?.let { itemInfo ->
                                            val currentUri = itemInfo.key as? String
                                            if (currentUri != null && !currentUri.startsWith("header:")) {
                                                val currentIndex = currentFlatImageList.indexOfFirst { it.uri == currentUri }
                                                if (currentIndex != -1) {
                                                    // 起点から現在の地点までの範囲をすべて選択
                                                    val start = minOf(firstItemIndex, currentIndex)
                                                    val end = maxOf(firstItemIndex, currentIndex)
                                                    
                                                    // 選択状態を更新（範囲内のアイテムを追加）
                                                    for (i in start..end) {
                                                        val uriToSelect = currentFlatImageList[i].uri
                                                        if (!selectedUris.contains(uriToSelect)) {
                                                            selectedUris.add(uriToSelect)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        dragChange.consume()
                                    }
                                    
                                    if (event.changes.any { !it.pressed }) break
                                }
                            }
                        }
                    }
                    .pointerInput(currentColumnIndex, filteredList) {
                        // 2本指以上での操作を検知した場合はズームを優先し、他のジェスチャー（スクロール等）を抑制する
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    isZooming = true
                                }
                                if (isZooming) {
                                    // ズーム中（2本指以上）は全イベントを消費してスクロールを止める
                                    event.changes.forEach { it.consume() }
                                }
                                if (event.changes.all { !it.pressed }) {
                                    isZooming = false
                                    break
                                }
                            }
                        }
                    }
                    .pointerInput(currentColumnIndex, filteredList) {
                        detectTransformGestures(panZoomLock = true) { _, _, zoom, _ ->
                            if (!isSelectionMode) {
                                zoomScale *= zoom
                                // 判定を少し敏感にする (1.3 -> 1.15, 0.7 -> 0.85)
                                if (zoomScale > 1.15f && currentColumnIndex < columnOptions.size - 1) { 
                                    currentColumnIndex++
                                    zoomScale = 1.0f 
                                }
                                else if (zoomScale < 0.85f && currentColumnIndex > 0) {
                                    currentColumnIndex--
                                    zoomScale = 1.0f 
                                }
                            }
                        }
                    },
                state = gridState,
                contentPadding = PaddingValues(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding, end = 40.dp)
            ) {
                var itemsThresholdReached = false
                groupedForDisplay.forEach { (header, items) ->
                    if (galleryState.groupingMode != GroupingMode.NONE) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header:$header") { Text(text = header, color = Color.White, modifier = Modifier.padding(16.dp), fontSize = AppConstants.HeaderFontSize) }
                    }
                    itemsIndexed(items = items, key = { _, item -> item.uri }) { localIndex, item ->
                        // インデックス計算を最適化（indexOfを避ける）
                        if (!itemsThresholdReached && localIndex >= items.size - 5 && visibleCount < filteredList.size) {
                            itemsThresholdReached = true
                            SideEffect { visibleCount += 200 } 
                        }

                        val thumbSize = when {
                            columnOptions[currentColumnIndex] >= 28 -> 8
                            columnOptions[currentColumnIndex] >= 7 -> 128
                            columnOptions[currentColumnIndex] >= 4 -> 512
                            else -> 1024
                        }
                        val isHighDensity = columnOptions[currentColumnIndex] >= 7
                        val isUltraHighDensity = columnOptions[currentColumnIndex] >= 28 // 28列モード用
                        val isNormalDensity = columnOptions[currentColumnIndex] <= 4 // 4列以下用

                        if (isUltraHighDensity) {
                            // 28列モード専用の極限まで軽量化したアイテム
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(0.5.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null // 波紋エフェクトもOFF
                                    ) {
                                        val finalIndex = currentFlatImageList.indexOf(item)
                                        onImageClick(finalIndex, currentFlatImageList)
                                        onPageChangedInViewer(item.uri)
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.uri)
                                        .size(thumbSize)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .bitmapConfig(Bitmap.Config.RGB_565)
                                        .crossfade(false)
                                        .decoderFactory(VideoFrameDecoder.Factory())
                                        .videoFrameMillis(1000)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            // 通常モード（既存のロジックをベースに最適化）
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .then(if (isNormalDensity) Modifier.animateItem() else Modifier)
                                    .then(if (columnOptions[currentColumnIndex] > 1) Modifier.aspectRatio(1f) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)))
                                    .padding(if (columnOptions[currentColumnIndex] > 1) 1.dp else 0.dp)
                                    .then(if (columnOptions[currentColumnIndex] in 2..27) Modifier.clip(RoundedCornerShape(2.dp)) else Modifier)
                                    .then(
                                        if (highlightUri == item.uri) {
                                            Modifier.border(2.dp, Color.White, if (columnOptions[currentColumnIndex] > 1) RoundedCornerShape(2.dp) else RoundedCornerShape(12.dp))
                                        } else Modifier
                                    )
                                    .combinedClickable(
                                        onClick = { 
                                            if (isSelectionMode) {
                                                if (selectedUris.contains(item.uri)) {
                                                    selectedUris.remove(item.uri)
                                                    if (selectedUris.isEmpty()) {
                                                        isSelectionMode = false
                                                        lastSelectedIndex = -1
                                                    }
                                                } else {
                                                    selectedUris.add(item.uri)
                                                    lastSelectedIndex = filteredList.indexOf(item)
                                                }
                                            } else {
                                                val finalIndex = currentFlatImageList.indexOf(item)
                                                onImageClick(finalIndex, currentFlatImageList)
                                                // クリックしたURIを即座に記録
                                                onPageChangedInViewer(item.uri)
                                            }
                                        },
                                        onLongClick = {
                                            val currentIndex = filteredList.indexOf(item)
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedUris.add(item.uri)
                                            } else {
                                                if (selectedUris.contains(item.uri)) {
                                                    selectedUris.remove(item.uri)
                                                    if (selectedUris.isEmpty()) isSelectionMode = false
                                                } else {
                                                    selectedUris.add(item.uri)
                                                }
                                            }
                                            lastSelectedIndex = currentIndex
                                        }
                                    )
                            ) {
                                // 最適化: 7列モードなどでは重いデコーダを条件付きで実行
                                val request = remember(item.uri, thumbSize, isHighDensity) {
                                    ImageRequest.Builder(context)
                                        .data(item.uri)
                                        .size(thumbSize)
                                        .apply {
                                            if (isHighDensity) {
                                                bitmapConfig(Bitmap.Config.RGB_565)
                                                crossfade(false)
                                            } else {
                                                crossfade(true)
                                            }
                                        }
                                        .decoderFactory(VideoFrameDecoder.Factory())
                                        .videoFrameMillis(1000)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .build()
                                }

                                AsyncImage(
                                    model = request,
                                    contentDescription = null, 
                                    modifier = Modifier.fillMaxSize(), 
                                    contentScale = if (columnOptions[currentColumnIndex] > 1) ContentScale.Crop else ContentScale.FillWidth
                                )

                                val isFavorite = metadataMap[item.uri]?.isFavorite == true
                                if (isFavorite) {
                                    Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomStart) {
                                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(if (columnOptions[currentColumnIndex] > 7) 8.dp else 16.dp))
                                    }
                                }

                                if (selectedUris.contains(item.uri)) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)), contentAlignment = Alignment.TopEnd) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(4.dp).size(24.dp))
                                    }
                                }
                                
                                val label = when { 
                                    item.isGif -> "GIF"
                                    item.isVideo -> formatDuration(item.duration)
                                    else -> null 
                                }
                                if (label != null) {
                                    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                                        Text(text = label, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // トップバー全体のコンテナ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, topBarOffsetHeightPx.roundToInt()) }
                .zIndex(1f)
        ) {
            // 1. タイトルバー (Home, Folder, MyList の固有タイトル)
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
                    if (onBackClick != null) {
                        IconButton(onClick = { onBackClick() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                        }
                    }
                    Text(text = title, color = Color.White, fontSize = AppConstants.HeaderFontSize)
                }
            } else {
                Spacer(modifier = Modifier.statusBarsPadding())
            }

        // 2. 操作バー (フィルタ、グループ化など)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topActionsHeight)
                .background(AppConstants.BackgroundColor.copy(alpha = 0.95f))
        ) {
                if (isSelectionMode) {
                    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                isSelectionMode = false
                                selectedUris.clear()
                                lastSelectedIndex = -1
                            }) { Icon(Icons.Default.Close, contentDescription = "解除") }
                            Text("${selectedUris.size} 件選択中", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Row {
                            TooltipWrapper("すべて選択") { IconButton(onClick = { selectedUris.clear(); selectedUris.addAll(filteredList.map { it.uri }) }) { Icon(Icons.Default.SelectAll, contentDescription = "全選択") } }
                            TooltipWrapper("お気に入りに追加") { IconButton(onClick = { scope.launch { galleryState.repository.bulkUpdateFavorite(selectedUris.toList(), true); isSelectionMode = false; selectedUris.clear() } }) { Icon(Icons.Default.Favorite, contentDescription = "お気に入り") } }
                            TooltipWrapper("タグ・年齢制限を一括編集") { 
                                IconButton(onClick = { 
                                    if (onBulkEdit != null) {
                                        onBulkEdit(selectedUris.toList())
                                        isSelectionMode = false
                                        selectedUris.clear()
                                    } else {
                                        showBulkEditDialog = true 
                                    }
                                }) { Icon(Icons.Default.LocalOffer, contentDescription = "一括編集") } 
                            }
                        }
                    }
                } else {
                    GalleryTopControlBar(
                        galleryState = galleryState,
                        isFilterEnabled = isFilterEnabled
                    )
                }
            }
        }

        if (showBulkEditDialog) {
            UnifiedMediaEditDialog(
                uris = selectedUris.toList(),
                repository = galleryState.repository,
                onDismiss = { showBulkEditDialog = false; isSelectionMode = false; selectedUris.clear() }
            )
        }

        // 高速スクロールバー
        if (filteredList.isNotEmpty() && !isSelectionMode) {
            // スクロールバーの状態
            var isPressed by remember { mutableStateOf(false) }
            val thumbWidth by animateDpAsState(targetValue = if (isPressed) 12.dp else 4.dp, label = "thumbWidth")

            // 全ての計算を derivedStateOf でまとめ、スクロールに対して反応するようにする
            val scrollbarState by remember(gridState, filteredList, galleryState.groupingMode) {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val totalItemsCount = info.totalItemsCount
                    val visibleItems = info.visibleItemsInfo
                    
                    if (visibleItems.isEmpty() || totalItemsCount == 0) {
                        null
                    } else {
                        val firstVisibleItem = visibleItems.first()
                        val lastVisibleItem = visibleItems.last()
                        
                        val visibleRange = (lastVisibleItem.index - firstVisibleItem.index + 1).coerceAtLeast(1)
                        val thumbHeightPercent = (visibleRange.toFloat() / totalItemsCount).coerceIn(0.1f, 1f)
                        val scrollPositionPercent = (firstVisibleItem.index.toFloat() / (totalItemsCount - visibleRange).coerceAtLeast(1)).coerceIn(0f, 1f)

                        // 日付の取得
                        val estimatedMediaIndex = (firstVisibleItem.index.toFloat() / totalItemsCount * filteredList.size).toInt().coerceIn(0, filteredList.size - 1)
                        val item = filteredList.getOrNull(estimatedMediaIndex)
                        val dateText = if (item != null) {
                            val date = Date(item.dateAdded)
                            when (galleryState.groupingMode) {
                                GroupingMode.YEAR -> SimpleDateFormat("yyyy年", Locale.getDefault()).format(date)
                                GroupingMode.MONTH -> SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(date)
                                else -> SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(date)
                            }
                        } else null
                        
                        Triple(thumbHeightPercent, scrollPositionPercent, dateText)
                    }
                }
            }

            scrollbarState?.let { (thumbHeightPercent, scrollPositionPercent, indicatorDate) ->
                val totalItemsCount = gridState.layoutInfo.totalItemsCount
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(40.dp) // タップ判定範囲を狭める (100dp -> 40dp)
                        .padding(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding + 8.dp)
                        .align(Alignment.CenterEnd)
                        .zIndex(5f) // 確実に最前面へ
                        .pointerInput(totalItemsCount) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    isPressed = true
                                    val availableHeightPx = size.height.toFloat()
                                    
                                    // タップ位置にジャンプ
                                    var targetPercent = (down.position.y / availableHeightPx).coerceIn(0f, 1f)
                                    var targetIndex = (targetPercent * totalItemsCount).toInt().coerceAtMost(totalItemsCount - 1)
                                    scope.launch {
                                        gridState.scrollToItem(targetIndex)
                                    }
                                    
                                    // そのままドラッグ/長押し
                                    drag(down.id) { change ->
                                        change.consume()
                                        targetPercent = (change.position.y / availableHeightPx).coerceIn(0f, 1f)
                                        targetIndex = (targetPercent * totalItemsCount).toInt().coerceAtMost(totalItemsCount - 1)
                                        scope.launch {
                                            gridState.scrollToItem(targetIndex)
                                        }
                                    }
                                    
                                    isPressed = false
                                }
                            }
                        }
                ) {
                    val availableHeight = maxHeight
                    val thumbHeight = availableHeight * thumbHeightPercent
                    
                    // 日付インジケーター
                    if (isPressed && indicatorDate != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 40.dp)
                                .offset(y = (availableHeight - thumbHeight) * scrollPositionPercent + (thumbHeight / 2) - 16.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = indicatorDate,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 1. 縦線 (トラック)
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .offset(x = (-20).dp)
                            .background(Color.White.copy(alpha = 0.5f))
                    )

                    // 2. バー (つまみ)
                    Box(
                        modifier = Modifier
                            .width(thumbWidth)
                            .height(thumbHeight)
                            .align(Alignment.TopEnd)
                            .offset(
                                x = (-20.5).dp + (thumbWidth / 2), // センターを揃える (-20.5 + 6 = -14.5)
                                y = (availableHeight - thumbHeight) * scrollPositionPercent
                            )
                            .clip(CircleShape)
                            .background(if (isPressed) Color.Cyan else Color.White.copy(alpha = 0.8f))
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

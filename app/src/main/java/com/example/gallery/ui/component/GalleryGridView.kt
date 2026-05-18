package com.example.gallery.ui.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlin.math.roundToInt

@Composable
fun GalleryGridView(
    imageList: List<MediaData>,
    onImageClick: (Int, List<MediaData>) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showGroupingButton: Boolean = true,
    galleryState: GalleryState = com.example.gallery.ui.rememberGalleryState(LocalContext.current),
    onTabIconClick: ((String) -> Unit)? = null,
    clearSelectionSignal: Int = 0,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    title: String? = null,
    onBackClick: (() -> Unit)? = null
) {
    val columnOptions = listOf(10, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }
    
    val animatedColumns by animateIntAsState(
        targetValue = columnOptions[currentColumnIndex],
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "columnAnimation"
    )

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAgeFilterMenu by remember { mutableStateOf(false) }
    var showGroupingMenu by remember { mutableStateOf(false) }

    // 複数選択用の状態
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    // Lazy Loading 用の変数
    var visibleCount by remember { mutableIntStateOf(60) }
    
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // トップバーの表示/非表示制御 (タイトル + 操作バーの両方を含める)
    val topTitleHeight = if (title != null) AppConstants.HeaderHeight else 0.dp
    val topActionsHeight = AppConstants.HeaderHeight
    
    val statusBarsHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalTopBarHeight = (if (title != null) topTitleHeight + statusBarsHeight else statusBarsHeight) + topActionsHeight
    
    val navBarsHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalBottomPadding = navBarsHeight + 100.dp

    val totalTopBarHeightPx = with(LocalDensity.current) { totalTopBarHeight.toPx() }
    var topBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    // オーバースクロール（画面全体を動かす）ための状態
    val overscrollTranslationY = remember { Animatable(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = topBarOffsetHeightPx + delta
                if (!isSelectionMode && overscrollTranslationY.value == 0f) {
                    topBarOffsetHeightPx = newOffset.coerceIn(-totalTopBarHeightPx, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    // 消費されなかったスクロール量で画面全体を移動させる
                    // 指を動かすほど抵抗が大きくなる（対数的な挙動）
                    scope.launch {
                        val current = overscrollTranslationY.value
                        val delta = available.y * 0.4f
                        val newTranslation = if (current * delta > 0) {
                            // 同じ方向に動かしている場合、距離に応じて減衰
                            current + delta * (1f / (1f + Math.abs(current) / 100f))
                        } else {
                            // 逆方向に戻している場合
                            current + delta
                        }
                        overscrollTranslationY.snapTo(newTranslation)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 指を離した時に元の位置に戻る
                overscrollTranslationY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                return super.onPostFling(consumed, available)
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
                    val rating = meta?.ageRating ?: "SFW"
                    when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                        else -> true
                    }
                }
            }

            if (galleryState.deviceFilter == DeviceFilter.ALL) ageFiltered
            else {
                ageFiltered.filter { item ->
                    if (item.isVideo || item.width <= 0 || item.height <= 0) false
                    else {
                        val ratio = item.height.toFloat() / item.width.toFloat()
                        when (galleryState.deviceFilter) {
                            DeviceFilter.SMARTPHONE -> {
                                item.height > item.width && Math.abs(ratio - deviceAspectRatio) < 0.2f
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
        }
    }

    val groupedForDisplay by remember(galleryState.groupingMode, filteredList, visibleCount) {
        derivedStateOf {
            val displayedItems = filteredList.take(visibleCount)
            when (galleryState.groupingMode) {
                GroupingMode.NONE -> listOf("All" to displayedItems)
                GroupingMode.DAY -> {
                    val fmt = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                    displayedItems.groupBy { fmt.format(Date(it.dateAdded)) }.toList()
                }
                GroupingMode.MONTH -> {
                    val fmt = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
                    displayedItems.groupBy { fmt.format(Date(it.dateAdded)) }.toList()
                }
            }
        }
    }

    val currentFlatImageList = filteredList

    LaunchedEffect(currentColumnIndex) { zoomScale = 1f }

    Box(
        modifier = modifier.fillMaxSize()
            .background(AppConstants.BackgroundColor)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(animatedColumns.coerceAtLeast(1)), 
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { translationY = overscrollTranslationY.value } // リスト部分のみ動かす
                    .pointerInput(currentColumnIndex, filteredList) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (!isSelectionMode) {
                                zoomScale *= zoom
                                if (zoomScale > 1.4f && currentColumnIndex < columnOptions.size - 1) { currentColumnIndex++; zoomScale = 1.0f }
                                else if (zoomScale < 0.6f && currentColumnIndex > 0) { currentColumnIndex--; zoomScale = 1.0f }
                            }
                        }
                    },
                state = gridState,
                contentPadding = PaddingValues(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding)
            ) {
                groupedForDisplay.forEach { (header, items) ->
                    if (galleryState.groupingMode != GroupingMode.NONE) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Text(text = header, color = Color.White, modifier = Modifier.padding(16.dp), fontSize = AppConstants.HeaderFontSize) }
                    }
                    itemsIndexed(items = items, key = { _, item -> item.uri }) { _, item ->
                        val globalIndex = filteredList.indexOf(item)
                        if (globalIndex >= visibleCount - 10 && visibleCount < filteredList.size) {
                            SideEffect { visibleCount += 60 }
                        }

                        val thumbSize = when { animatedColumns >= 10 -> 100; animatedColumns >= 7 -> 200; animatedColumns >= 4 -> 400; else -> 1000 }
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .then(if (columnOptions[currentColumnIndex] > 1) Modifier.aspectRatio(1f) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)))
                                .padding(if (columnOptions[currentColumnIndex] > 1) 1.dp else 0.dp)
                                .then(if (columnOptions[currentColumnIndex] > 1) Modifier.clip(RoundedCornerShape(2.dp)) else Modifier)
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
                                        }
                                    },
                                    onLongClick = {
                                        val currentIndex = filteredList.indexOf(item)
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedUris.add(item.uri)
                                            lastSelectedIndex = currentIndex
                                        } else {
                                            // 範囲選択
                                            if (lastSelectedIndex != -1) {
                                                val start = minOf(lastSelectedIndex, currentIndex)
                                                val end = maxOf(lastSelectedIndex, currentIndex)
                                                for (i in start..end) {
                                                    val uri = filteredList[i].uri
                                                    if (!selectedUris.contains(uri)) {
                                                        selectedUris.add(uri)
                                                    }
                                                }
                                            }
                                            lastSelectedIndex = currentIndex
                                        }
                                    }
                                )
                        ) {
                            if (item.uri.startsWith("mock://")) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                                }
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(item.uri).size(thumbSize).crossfade(true).decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(1000).build(),
                                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = if (columnOptions[currentColumnIndex] > 1) ContentScale.Crop else ContentScale.FillWidth
                                )
                            }

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
                            
                            val label = when { item.isGif -> "GIF"; item.isVideo -> formatDuration(item.duration); else -> null }
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
                            TooltipWrapper("タグ・年齢制限を一括編集") { IconButton(onClick = { showBulkEditDialog = true }) { Icon(Icons.Default.LocalOffer, contentDescription = "一括編集") } }
                        }
                    }
                } else if (showGroupingButton) {
                    GalleryTopControlBar(
                        galleryState = galleryState,
                        columnOptions = columnOptions,
                        currentColumnIndex = currentColumnIndex,
                        onColumnIndexChange = { currentColumnIndex = it },
                        showGroupingButton = true,
                        isGroupingEnabled = true // グリッド表示（フォルダ詳細など）では日付グループ活性
                    )
                }
            }
        }

        if (showBulkEditDialog) {
            UnifiedMediaEditDialog(uris = selectedUris.toList(), repository = galleryState.repository, onDismiss = { showBulkEditDialog = false; isSelectionMode = false; selectedUris.clear() })
        }

        // ドラッグ可能なスクロールバー
        val layoutInfo = gridState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems > 0 && !isSelectionMode) {
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isNotEmpty()) {
                val firstVisibleItem = visibleItemsInfo.first()
                val visibleCountInGrid = visibleItemsInfo.size
                
                // スクロールバーのパラメータ
                val thumbHeightPercent = (visibleCountInGrid.toFloat() / totalItems).coerceIn(0.1f, 1f)
                val scrollPositionPercent = (firstVisibleItem.index.toFloat() / (totalItems - visibleCountInGrid).coerceAtLeast(1)).coerceIn(0f, 1f)
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(32.dp) // タップ領域を広めに
                        .padding(top = totalTopBarHeight + 16.dp, bottom = totalBottomPadding + 16.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    val availableHeight = maxHeight
                    val availableHeightPx = with(LocalDensity.current) { availableHeight.toPx() }
                    val thumbHeight = availableHeight * thumbHeightPercent
                    val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }

                    // トラック（背景ライン）
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .padding(end = 4.dp)
                            .align(Alignment.CenterEnd)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    )

                    // スクロールバーのつまみ
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(thumbHeight)
                            .offset(y = (availableHeight - thumbHeight) * scrollPositionPercent)
                            .padding(end = 4.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.5f))
                            .pointerInput(totalItems, visibleCountInGrid) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newScrollPosPx = (availableHeightPx - thumbHeightPx) * scrollPositionPercent + dragAmount.y
                                    val newPercent = (newScrollPosPx / (availableHeightPx - thumbHeightPx)).coerceIn(0f, 1f)
                                    val targetIndex = (newPercent * (totalItems - visibleCountInGrid)).toInt()
                                    scope.launch {
                                        gridState.scrollToItem(targetIndex)
                                    }
                                }
                            }
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

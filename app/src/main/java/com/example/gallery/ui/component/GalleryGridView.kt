package com.example.gallery.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import kotlinx.coroutines.coroutineScope
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
    scrollToUri: String? = null,
    isFilterEnabled: Boolean = true,
    onMenuClick: (() -> Unit)? = null,
    onPageChangedInViewer: (String) -> Unit = {},
    onBulkEdit: ((List<String>) -> Unit)? = null,
    onSelectionChanged: (List<String>) -> Unit = {},
    isTrashMode: Boolean = false
) {
    val columnOptions = listOf(28, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }

    LaunchedEffect(currentColumnIndex) {
        galleryState.groupingMode = when (columnOptions[currentColumnIndex]) {
            28 -> GroupingMode.YEAR; 7 -> GroupingMode.MONTH; else -> GroupingMode.DAY
        }
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var isZooming by remember { mutableStateOf(false) }
    // ズーム中または指を離していない状態を親に伝える（サイドバー抑制用）
    val currentOnSelectionModeChanged by rememberUpdatedState(onSelectionModeChanged)
    LaunchedEffect(isZooming) {
        currentOnSelectionModeChanged(isSelectionMode || isZooming)
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
                if (!isSelectionMode) topBarOffsetHeightPx = newOffset.coerceIn(-totalTopBarHeightPx, 0f)
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(clearSelectionSignal) { if (clearSelectionSignal > 0) { isSelectionMode = false; selectedUris.clear() } }
    LaunchedEffect(isSelectionMode) { onSelectionModeChanged(isSelectionMode); if (isSelectionMode) topBarOffsetHeightPx = 0f }

    val allMetadata by galleryState.repository.getAllMetadataFlow().collectAsState(initial = emptyList())
    val metadataMap by remember(allMetadata) { derivedStateOf { allMetadata.associateBy { it.uri } } }
    val configuration = LocalConfiguration.current
    val deviceAspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()

    val filteredList by remember(galleryState.mediaTypeFilter, galleryState.ageRatingFilter, galleryState.deviceFilter, metadataMap, imageList, galleryState.sortMode, galleryState.isAscending) {
        derivedStateOf {
            val typeFiltered = if (galleryState.mediaTypeFilter == MediaTypeFilter.ALL) imageList.toList()
            else imageList.filter { item -> when (galleryState.mediaTypeFilter) { MediaTypeFilter.IMAGE -> item.mimeType?.startsWith("image/") == true && !item.isGif; MediaTypeFilter.VIDEO -> item.isVideo; MediaTypeFilter.GIF -> item.isGif; else -> true } }
            val ageFiltered = if (galleryState.ageRatingFilter == AgeRatingFilter.ALL) typeFiltered
            else typeFiltered.filter { item -> val rating = metadataMap[item.uri]?.ageRating ?: "SFW"; when (galleryState.ageRatingFilter) { AgeRatingFilter.SFW -> rating == "SFW"; AgeRatingFilter.R15 -> rating == "R15"; AgeRatingFilter.R18 -> rating == "R18"; else -> true } }
            val deviceFiltered = if (galleryState.deviceFilter == DeviceFilter.ALL) ageFiltered
            else ageFiltered.filter { item -> if (item.isVideo || item.width <= 0 || item.height <= 0) false else { val ratio = item.height.toFloat() / item.width.toFloat(); when (galleryState.deviceFilter) { DeviceFilter.SMARTPHONE -> item.height > item.width && Math.abs(ratio - deviceAspectRatio) < 0.2f; DeviceFilter.PC -> item.width > item.height && (item.width.toFloat() / item.height.toFloat()) > 1.3f; else -> true } } }
            val sorted = when (galleryState.sortMode) { SortMode.DATE_ADDED -> deviceFiltered.sortedBy { it.dateAdded }; SortMode.SIZE -> deviceFiltered.sortedBy { it.fileSize }; SortMode.NAME -> deviceFiltered.sortedWith(compareBy({ val firstChar = it.fileName.firstOrNull(); firstChar == null || !firstChar.isDigit() }, { it.fileName })) }
            if (galleryState.isAscending) sorted else sorted.reversed()
        }
    }

    val groupedForDisplay by remember(galleryState.groupingMode, filteredList, visibleCount, currentColumnIndex) {
        derivedStateOf {
            val is28 = columnOptions[currentColumnIndex] == 28
            val baseGroups = when (galleryState.groupingMode) {
                GroupingMode.NONE -> listOf("All" to filteredList)
                GroupingMode.DAY -> filteredList.groupBy { val cal = Calendar.getInstance().apply { timeInMillis = it.dateAdded }; "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日" }.toList()
                GroupingMode.MONTH -> filteredList.groupBy { val cal = Calendar.getInstance().apply { timeInMillis = it.dateAdded }; "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月" }.toList()
                GroupingMode.YEAR -> filteredList.groupBy { val cal = Calendar.getInstance().apply { timeInMillis = it.dateAdded }; "${cal.get(Calendar.YEAR)}年" }.toList()
                GroupingMode.STORAGE -> filteredList.groupBy { if (it.uri.contains("emulated/0")) "内部ストレージ" else if (it.uri.contains("sdcard") || it.uri.contains("-")) "SDカード・外部ストレージ" else "その他" }.toList()
            }
            var currentCount = 0; val result = mutableListOf<Pair<String, List<MediaData>>>()
            for (group in baseGroups) {
                val items = group.second; val processedItems = if (is28 && galleryState.groupingMode == GroupingMode.YEAR && items.size > 224) { val sampled = mutableListOf<MediaData>(); val step = items.size.toDouble() / 224.0; for (i in 0 until 224) sampled.add(items[(i * step).toInt().coerceAtMost(items.size - 1)]); sampled } else items
                val itemsToTake = minOf(processedItems.size, (visibleCount - currentCount).coerceAtLeast(0))
                if (itemsToTake > 0) { result.add(group.first to processedItems.take(itemsToTake)); currentCount += itemsToTake }
                if (currentCount >= visibleCount) break
            }
            result
        }
    }

    val currentFlatImageList = filteredList
    LaunchedEffect(currentColumnIndex) {
        isChangingColumns = true; delay(10)
        visibleCount = when { columnOptions[currentColumnIndex] >= 28 -> 1500; columnOptions[currentColumnIndex] >= 7 -> 500; else -> 80 }
        delay(100); isChangingColumns = false
    }

    LaunchedEffect(scrollToUri, filteredList, groupedForDisplay) {
        if (scrollToUri != null && filteredList.isNotEmpty()) {
            var targetGridIndex = -1; var currentGridIndex = 0
            for (group in groupedForDisplay) {
                if (galleryState.groupingMode != GroupingMode.NONE) currentGridIndex++
                val localIndex = group.second.indexOfFirst { it.uri == scrollToUri }
                if (localIndex != -1) { targetGridIndex = currentGridIndex + localIndex; break }
                currentGridIndex += group.second.size
            }
            if (targetGridIndex != -1) {
                if (targetGridIndex >= visibleCount) visibleCount = targetGridIndex + 100
                val isAlreadyVisible = gridState.layoutInfo.visibleItemsInfo.any { it.key == scrollToUri }
                if (!isAlreadyVisible) {
                    val jumpIndex = (targetGridIndex - columnOptions[currentColumnIndex] * 2).coerceAtLeast(0)
                    gridState.scrollToItem(jumpIndex); delay(50); gridState.animateScrollToItem(targetGridIndex)
                }
                highlightUri = scrollToUri; delay(2500); if (highlightUri == scrollToUri) highlightUri = null
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppConstants.BackgroundColor).nestedScroll(nestedScrollConnection)) {
        if (isLoading || isChangingColumns) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        } else {
            AnimatedContent(
                targetState = currentColumnIndex,
                transitionSpec = { fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250)) },
                label = "gridZoom",
                modifier = Modifier.fillMaxSize()
            ) { targetIndex ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnOptions[targetIndex]),
                    userScrollEnabled = !isZooming && !isChangingColumns,
                    state = gridState,
                    contentPadding = PaddingValues(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding, end = 40.dp),
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(currentFlatImageList, targetIndex, isSelectionMode) {
                            awaitPointerEventScope {
                                var zoomAccumulator = 1f; var zoomAppliedInThisGesture = false
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val changes = event.changes
                                    if (changes.size >= 2) isZooming = true
                                    if (isZooming) {
                                        changes.forEach { it.consume() }
                                        if (!zoomAppliedInThisGesture && !isSelectionMode) {
                                            val zoom = event.calculateZoom(); zoomAccumulator *= zoom
                                            if (zoomAccumulator > 1.3f && currentColumnIndex < columnOptions.size - 1) { currentColumnIndex++; zoomAppliedInThisGesture = true }
                                            else if (zoomAccumulator < 0.7f && currentColumnIndex > 0) { currentColumnIndex--; zoomAppliedInThisGesture = true }
                                        }
                                    }
                                    if (changes.all { !it.pressed }) { isZooming = false; zoomAccumulator = 1f; zoomAppliedInThisGesture = false }
                                }
                            }
                        }
                        .pointerInput(currentFlatImageList, isSelectionMode) {
                            if (columnOptions[targetIndex] >= 28) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isLongPress = false; var firstItemIndex = -1; val timeout = 700L; val startTime = System.currentTimeMillis()
                                    while (true) {
                                        val event = awaitPointerEvent(); val currentTime = System.currentTimeMillis()
                                        
                                        // ズーム中(isZooming)または2本以上の指が触れている場合は、選択モードへの移行や範囲選択を抑制する
                                        val isMultiTouch = event.changes.size >= 2
                                        
                                        if (currentTime - startTime > timeout && !isLongPress && !isZooming && !isMultiTouch) {
                                            isLongPress = true; val offset = down.position; val beforePadding = gridState.layoutInfo.beforeContentPadding
                                            gridState.layoutInfo.visibleItemsInfo.find { val itemTop = it.offset.y.toFloat() + beforePadding; val itemBottom = itemTop + it.size.height; offset.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() && offset.y in itemTop..itemBottom }?.let { itemInfo ->
                                                val uri = itemInfo.key as? String
                                                if (uri != null && !uri.startsWith("header:")) { if (!isSelectionMode) isSelectionMode = true; if (!selectedUris.contains(uri)) selectedUris.add(uri); firstItemIndex = currentFlatImageList.indexOfFirst { it.uri == uri } }
                                            }
                                        }
                                        if (isLongPress && firstItemIndex != -1) {
                                            val dragChange = event.changes.first(); val currentOffset = dragChange.position; val viewHeight = size.height; val scrollThreshold = 100.dp.toPx()
                                            if (currentOffset.y < scrollThreshold) gridState.dispatchRawDelta(((scrollThreshold - currentOffset.y) / scrollThreshold * -30f).toInt().toFloat())
                                            else if (currentOffset.y > viewHeight - scrollThreshold) gridState.dispatchRawDelta(((currentOffset.y - (viewHeight - scrollThreshold)) / scrollThreshold * 30f).toInt().toFloat())
                                            val beforePadding = gridState.layoutInfo.beforeContentPadding
                                            gridState.layoutInfo.visibleItemsInfo.find { val itemTop = it.offset.y.toFloat() + beforePadding; val itemBottom = itemTop + it.size.height; currentOffset.x in it.offset.x.toFloat()..(it.offset.x + it.size.width).toFloat() && currentOffset.y in itemTop..itemBottom }?.let { itemInfo ->
                                                val currentUri = itemInfo.key as? String
                                                if (currentUri != null && !currentUri.startsWith("header:")) { val currentIndex = currentFlatImageList.indexOfFirst { it.uri == currentUri }; if (currentIndex != -1) { val start = minOf(firstItemIndex, currentIndex); val end = maxOf(firstItemIndex, currentIndex); for (i in start..end) { val uriToSelect = currentFlatImageList[i].uri; if (!selectedUris.contains(uriToSelect)) selectedUris.add(uriToSelect) } } }
                                            }
                                            dragChange.consume()
                                        }
                                        if (event.changes.all { !it.pressed }) break
                                    }
                                }
                            }
                        }
                ) {
                    groupedForDisplay.forEach { (header, items) ->
                        if (galleryState.groupingMode != GroupingMode.NONE) item(span = { GridItemSpan(maxLineSpan) }, key = "header:$header") { Text(header, color = Color.White, modifier = Modifier.padding(16.dp), fontSize = AppConstants.HeaderFontSize) }
                        itemsIndexed(items, key = { _, item -> item.uri }) { localIndex, item ->
                            if (localIndex >= items.size - 5 && visibleCount < filteredList.size) SideEffect { visibleCount += 200 }
                            val thumbSize = when { columnOptions[targetIndex] >= 28 -> 8; columnOptions[targetIndex] >= 7 -> 128; columnOptions[targetIndex] >= 4 -> 512; else -> 1024 }
                            if (columnOptions[targetIndex] >= 28) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(0.5.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { val idx = currentFlatImageList.indexOf(item); onImageClick(idx, currentFlatImageList); onPageChangedInViewer(item.uri) }) {
                                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(item.uri).size(thumbSize).memoryCachePolicy(coil.request.CachePolicy.ENABLED).diskCachePolicy(coil.request.CachePolicy.ENABLED).bitmapConfig(Bitmap.Config.RGB_565).crossfade(false).decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(1000).build(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().then(if (columnOptions[targetIndex] <= 4) Modifier.animateItem() else Modifier).then(if (columnOptions[targetIndex] > 1) Modifier.aspectRatio(1f) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))).padding(if (columnOptions[targetIndex] > 1) 1.dp else 0.dp).then(if (columnOptions[targetIndex] in 2..27) Modifier.clip(RoundedCornerShape(2.dp)) else Modifier).then(if (highlightUri == item.uri) Modifier.border(3.dp, Color.Cyan, if (columnOptions[targetIndex] > 1) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp)) else Modifier).combinedClickable(onClick = { if (isSelectionMode) { if (selectedUris.contains(item.uri)) { selectedUris.remove(item.uri); if (selectedUris.isEmpty()) { isSelectionMode = false; lastSelectedIndex = -1 } } else { selectedUris.add(item.uri); lastSelectedIndex = filteredList.indexOf(item) } } else { val idx = currentFlatImageList.indexOf(item); onImageClick(idx, currentFlatImageList); onPageChangedInViewer(item.uri) } }, onLongClick = { val idx = filteredList.indexOf(item); if (!isSelectionMode) { isSelectionMode = true; selectedUris.add(item.uri) } else { if (selectedUris.contains(item.uri)) { selectedUris.remove(item.uri); if (selectedUris.isEmpty()) isSelectionMode = false } else selectedUris.add(item.uri) }; lastSelectedIndex = idx })) {
                                    AsyncImage(model = ImageRequest.Builder(context).data(item.uri).size(thumbSize).apply { if (columnOptions[targetIndex] >= 7) { bitmapConfig(Bitmap.Config.RGB_565); crossfade(false) } else crossfade(true) }.decoderFactory(VideoFrameDecoder.Factory()).videoFrameMillis(1000).memoryCachePolicy(coil.request.CachePolicy.ENABLED).diskCachePolicy(coil.request.CachePolicy.ENABLED).build(), null, modifier = Modifier.fillMaxSize(), contentScale = if (columnOptions[targetIndex] > 1) ContentScale.Crop else ContentScale.FillWidth)
                                    if (metadataMap[item.uri]?.isFavorite == true) Box(modifier = Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.BottomStart) { Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(if (columnOptions[targetIndex] > 7) 8.dp else 16.dp)) }
                                    if (selectedUris.contains(item.uri)) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)), contentAlignment = Alignment.TopEnd) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(4.dp).size(24.dp)) }
                                    val label = when { item.isGif -> "GIF"; item.isVideo -> formatDuration(item.duration); else -> null }
                                    if (label != null) Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) { Text(label, color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().offset { IntOffset(0, topBarOffsetHeightPx.roundToInt()) }.zIndex(1f)) {
            if (title != null) {
                Row(modifier = Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(topTitleHeight).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onMenuClick != null) { IconButton(onMenuClick) { Icon(Icons.Default.Menu, null, tint = Color.White) }; Spacer(Modifier.width(8.dp)) }
                    else if (onBackClick != null) { IconButton({ onBackClick() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } }
                    Text(title, color = Color.White, fontSize = AppConstants.HeaderFontSize)
                }
            } else Spacer(Modifier.statusBarsPadding())

            Box(modifier = Modifier.fillMaxWidth().height(topActionsHeight).background(AppConstants.BackgroundColor.copy(alpha = 0.95f))) {
                if (isSelectionMode) {
                    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ isSelectionMode = false; selectedUris.clear(); lastSelectedIndex = -1 }) { Icon(Icons.Default.Close, null) }
                            Text("${selectedUris.size} 件選択中", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Row {
                            TooltipWrapper("すべて選択") { IconButton({ selectedUris.clear(); selectedUris.addAll(filteredList.map { it.uri }) }) { Icon(Icons.Default.SelectAll, null) } }
                            if (!isTrashMode) {
                                TooltipWrapper("お気に入り") { IconButton({ scope.launch { galleryState.repository.bulkUpdateFavorite(selectedUris.toList(), true); isSelectionMode = false; selectedUris.clear() } }) { Icon(Icons.Default.Favorite, null) } }
                                TooltipWrapper("一括編集") { IconButton({ if (onBulkEdit != null) { onBulkEdit(selectedUris.toList()); isSelectionMode = false; selectedUris.clear() } }) { Icon(Icons.Default.LocalOffer, null) } }
                            }
                        }
                    }
                } else GalleryTopControlBar(galleryState, isFilterEnabled)
            }
        }

        if (filteredList.isNotEmpty() && !isSelectionMode) {
            var isPressed by remember { mutableStateOf(false) }
            val thumbWidth by animateDpAsState(if (isPressed) 12.dp else 4.dp)
            val scrollbarState by remember(gridState, filteredList, galleryState.groupingMode) {
                derivedStateOf {
                    val info = gridState.layoutInfo; val total = info.totalItemsCount; val visible = info.visibleItemsInfo
                    if (visible.isEmpty() || total == 0) null
                    else {
                        val first = visible.first(); val last = visible.last(); val range = (last.index - first.index + 1).coerceAtLeast(1)
                        val thumbHeight = (range.toFloat() / total).coerceIn(0.1f, 1f); val scrollPos = (first.index.toFloat() / (total - range).coerceAtLeast(1)).coerceIn(0f, 1f)
                        val estIdx = (first.index.toFloat() / total * filteredList.size).toInt().coerceIn(0, filteredList.size - 1)
                        val dateText = filteredList.getOrNull(estIdx)?.let { val d = Date(it.dateAdded); when (galleryState.groupingMode) { GroupingMode.YEAR -> SimpleDateFormat("yyyy年").format(d); GroupingMode.MONTH -> SimpleDateFormat("yyyy年MM月").format(d); else -> SimpleDateFormat("yyyy年MM月dd日").format(d) } }
                        Triple(thumbHeight, scrollPos, dateText)
                    }
                }
            }
            scrollbarState?.let { (th, sp, date) ->
                BoxWithConstraints(modifier = Modifier.fillMaxHeight().width(40.dp).padding(top = totalTopBarHeight + 8.dp, bottom = totalBottomPadding + 8.dp).align(Alignment.CenterEnd).zIndex(5f).pointerInput(gridState.layoutInfo.totalItemsCount) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(); isPressed = true; var tp = (down.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                            scope.launch { gridState.scrollToItem((tp * gridState.layoutInfo.totalItemsCount).toInt().coerceAtMost(gridState.layoutInfo.totalItemsCount - 1)) }
                            drag(down.id) { change -> change.consume(); tp = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f); scope.launch { gridState.scrollToItem((tp * gridState.layoutInfo.totalItemsCount).toInt().coerceAtMost(gridState.layoutInfo.totalItemsCount - 1)) } }
                            isPressed = false
                        }
                    }
                }) {
                    if (isPressed && date != null) Surface(modifier = Modifier.align(Alignment.TopEnd).padding(end = 40.dp).offset(y = (maxHeight - maxHeight * th) * sp + (maxHeight * th / 2) - 16.dp), color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp)) { Text(date, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().align(Alignment.CenterEnd).offset(x = (-20).dp).background(Color.White.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.width(thumbWidth).height(maxHeight * th).align(Alignment.TopEnd).offset(x = (-20.5).dp + (thumbWidth / 2), y = (maxHeight - maxHeight * th) * sp).clip(CircleShape).background(if (isPressed) Color.Cyan else Color.White.copy(alpha = 0.8f)))
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val s = durationMs / 1000; val m = (s / 60) % 60; val h = s / 3600; val sec = s % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}

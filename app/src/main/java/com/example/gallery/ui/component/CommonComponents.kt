package com.example.gallery.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.gallery.ui.GalleryState
import kotlinx.coroutines.delay
import android.content.Intent
import android.net.Uri
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.DeviceFilter
import com.example.gallery.ui.GroupingMode
import com.example.gallery.ui.MediaTypeFilter

@Composable
fun TooltipWrapper(
    description: String,
    modifier: Modifier = Modifier,
    showExternally: Boolean = false,
    content: @Composable () -> Unit
) {
    // 外部からのトリガーか、内部での状態保持
    var showTooltipLocal by remember { mutableStateOf(false) }
    val isVisible = showExternally || showTooltipLocal

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        content()

        if (isVisible) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, -100), // 下部に表示（ナビゲーションバーより少し上）
                properties = PopupProperties(
                    focusable = false, 
                    dismissOnClickOutside = true,
                    clippingEnabled = false,
                    usePlatformDefaultWidth = false // タップイベントの吸い込み防止のため
                )
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp) // 画面下端からの余白
                ) {
                    Text(
                        text = description,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    delay(2000)
                    showTooltipLocal = false
                }
            }
        }
    }
}

@Composable
fun GalleryTopControlBar(
    galleryState: GalleryState,
    columnOptions: List<Int>,
    currentColumnIndex: Int,
    onColumnIndexChange: (Int) -> Unit,
    showGroupingButton: Boolean = true,
    isGroupingEnabled: Boolean = true // 新規：日付グループの活性/非活性制御
) {
    var showZoomMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAgeFilterMenu by remember { mutableStateOf(false) }
    var showGroupingMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showGroupingButton) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var showTooltip by remember { mutableStateOf(false) }
                TooltipWrapper(description = "グループ化切替", showExternally = showTooltip) {
                    Box {
                        IconButton(
                            onClick = { showGroupingMenu = true },
                            onLongClick = { if (isGroupingEnabled) showTooltip = true },
                            enabled = isGroupingEnabled
                        ) { 
                            Icon(
                                Icons.Default.DateRange, 
                                null, 
                                tint = if (isGroupingEnabled) Color.White else Color.Gray.copy(alpha = 0.5f)
                            ) 
                        }
                        DropdownMenu(expanded = showGroupingMenu, onDismissRequest = { showGroupingMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                            GroupingMode.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(text = when (mode) { GroupingMode.NONE -> "なし"; GroupingMode.DAY -> "日別"; GroupingMode.MONTH -> "月別" }, color = Color.White) }, onClick = { galleryState.groupingMode = mode; showGroupingMenu = false })
                            }
                        }
                    }
                }
                if (showTooltip) { LaunchedEffect(Unit) { delay(2000); showTooltip = false } }
                Text(
                    text = when (galleryState.groupingMode) { GroupingMode.NONE -> "なし"; GroupingMode.DAY -> "日別"; GroupingMode.MONTH -> "月別" }, 
                    color = if (isGroupingEnabled) Color.White else Color.Gray.copy(alpha = 0.5f), 
                    fontSize = 10.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            var showTooltip by remember { mutableStateOf(false) }
            TooltipWrapper(description = "表示列数", showExternally = showTooltip) {
                Box {
                    IconButton(
                        onClick = { showZoomMenu = true },
                        onLongClick = { showTooltip = true }
                    ) { Icon(Icons.Default.ViewModule, null, tint = Color.White) }
                    DropdownMenu(expanded = showZoomMenu, onDismissRequest = { showZoomMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                        columnOptions.forEachIndexed { index, count ->
                            DropdownMenuItem(text = { Text("${count}列", color = Color.White) }, onClick = { onColumnIndexChange(index); showZoomMenu = false })
                        }
                    }
                }
            }
            if (showTooltip) { LaunchedEffect(Unit) { delay(2000); showTooltip = false } }
            Text("${columnOptions[currentColumnIndex]}列", color = Color.White, fontSize = 10.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            var showTooltip by remember { mutableStateOf(false) }
            TooltipWrapper(description = "フィルタ", showExternally = showTooltip) {
                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        onLongClick = { showTooltip = true }
                    ) { Icon(Icons.Default.FilterAlt, null, tint = Color.White) }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                        Text("メディア種別", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                        MediaTypeFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(text = when (filter) { MediaTypeFilter.ALL -> "すべて"; MediaTypeFilter.IMAGE -> "画像"; MediaTypeFilter.VIDEO -> "動画"; MediaTypeFilter.GIF -> "GIF" }, color = if(galleryState.mediaTypeFilter == filter) Color.Cyan else Color.White) }, onClick = { galleryState.mediaTypeFilter = filter; showFilterMenu = false })
                        }
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                        Text("デバイス背景", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                        DeviceFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(text = when (filter) { DeviceFilter.ALL -> "すべて"; DeviceFilter.SMARTPHONE -> "スマホ背景"; DeviceFilter.PC -> "PC背景" }, color = if(galleryState.deviceFilter == filter) Color.Cyan else Color.White) }, onClick = { galleryState.deviceFilter = filter; showFilterMenu = false })
                        }
                    }
                }
            }
            if (showTooltip) { LaunchedEffect(Unit) { delay(2000); showTooltip = false } }
            Text("フィルタ", color = Color.White, fontSize = 10.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            var showTooltip by remember { mutableStateOf(false) }
            TooltipWrapper(description = "年齢制限", showExternally = showTooltip) {
                Box {
                    IconButton(
                        onClick = { showAgeFilterMenu = true },
                        onLongClick = { showTooltip = true }
                    ) {
                        Icon(imageVector = Icons.Default.PrivacyTip, contentDescription = null, tint = when(galleryState.ageRatingFilter) {
                            AgeRatingFilter.SFW -> Color.Green; AgeRatingFilter.R15 -> Color.Yellow; AgeRatingFilter.R18 -> Color.Red; else -> Color.White
                        })
                    }
                    DropdownMenu(expanded = showAgeFilterMenu, onDismissRequest = { showAgeFilterMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                        AgeRatingFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(text = when (filter) { AgeRatingFilter.ALL -> "すべて"; AgeRatingFilter.SFW -> "健全"; AgeRatingFilter.R15 -> "R-15"; AgeRatingFilter.R18 -> "R-18" }, color = if(galleryState.ageRatingFilter == filter) Color.Cyan else Color.White) }, onClick = { galleryState.ageRatingFilter = filter; showAgeFilterMenu = false })
                        }
                    }
                }
            }
            if (showTooltip) { LaunchedEffect(Unit) { delay(2000); showTooltip = false } }
            Text(when(galleryState.ageRatingFilter) { AgeRatingFilter.ALL -> "ALL"; AgeRatingFilter.SFW -> "健全"; AgeRatingFilter.R15 -> "R15"; AgeRatingFilter.R18 -> "R18" }, color = Color.White, fontSize = 10.sp)
        }

        var showSearchTooltip by remember { mutableStateOf(false) }
        TooltipWrapper(description = "画像検索", showExternally = showSearchTooltip) {
            val context = LocalContext.current
            IconButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ascii2d.net/"))) },
                onLongClick = { showSearchTooltip = true }
            ) { Icon(Icons.Default.ImageSearch, null, tint = Color.White) }
        }
        if (showSearchTooltip) { LaunchedEffect(Unit) { delay(2000); showSearchTooltip = false } }
    }
}

// 補助コンポーネント: IconButtonにlongClickを追加
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        val color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
        CompositionLocalProvider(LocalContentColor provides color, content = content)
    }
}

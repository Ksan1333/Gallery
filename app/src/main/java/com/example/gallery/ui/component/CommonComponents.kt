package com.example.gallery.ui.component

import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.List
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.DeviceFilter
import com.example.gallery.ui.GroupingMode
import com.example.gallery.ui.MediaTypeFilter
import com.example.gallery.ui.SortMode

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
    isFilterEnabled: Boolean = true // 新規：フィルタ・並び替えの活性/非活性
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAgeFilterMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var showFilterTooltip by remember { mutableStateOf(false) }
        TooltipWrapper(description = "フィルタ", showExternally = showFilterTooltip) {
            IconButton(
                onClick = { showFilterMenu = true },
                onLongClick = { showFilterTooltip = true },
                modifier = Modifier.size(36.dp),
                enabled = isFilterEnabled
            ) { 
                Icon(Icons.Default.FilterAlt, null, tint = if (isFilterEnabled) Color.White else Color.Gray, modifier = Modifier.size(20.dp))
                if (isFilterEnabled) {
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
        }
        if (showFilterTooltip) { LaunchedEffect(Unit) { delay(2000); showFilterTooltip = false } }

        Spacer(modifier = Modifier.width(4.dp))

        var showAgeTooltip by remember { mutableStateOf(false) }
        TooltipWrapper(description = "年齢制限", showExternally = showAgeTooltip) {
            IconButton(
                onClick = { showAgeFilterMenu = true },
                onLongClick = { showAgeTooltip = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(imageVector = Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(20.dp), tint = when(galleryState.ageRatingFilter) {
                    AgeRatingFilter.SFW -> Color.Green; AgeRatingFilter.R15 -> Color.Yellow; AgeRatingFilter.R18 -> Color.Red; else -> Color.White
                })
                DropdownMenu(expanded = showAgeFilterMenu, onDismissRequest = { showAgeFilterMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                    AgeRatingFilter.entries.forEach { filter ->
                        DropdownMenuItem(text = { Text(text = when (filter) { AgeRatingFilter.ALL -> "すべて"; AgeRatingFilter.SFW -> "健全"; AgeRatingFilter.R15 -> "R-15"; AgeRatingFilter.R18 -> "R-18" }, color = if(galleryState.ageRatingFilter == filter) Color.Cyan else Color.White) }, onClick = { galleryState.ageRatingFilter = filter; showAgeFilterMenu = false })
                    }
                }
            }
        }
        if (showAgeTooltip) { LaunchedEffect(Unit) { delay(2000); showAgeTooltip = false } }

        Spacer(modifier = Modifier.width(4.dp))

        var showSortMenu by remember { mutableStateOf(false) }
        var showSortTooltip by remember { mutableStateOf(false) }
        TooltipWrapper(description = "並び替え", showExternally = showSortTooltip) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showSortMenu = true },
                    onLongClick = { showSortTooltip = true },
                    modifier = Modifier.size(36.dp),
                    enabled = isFilterEnabled
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, tint = if (isFilterEnabled) Color.White else Color.Gray, modifier = Modifier.size(20.dp))
                    if (isFilterEnabled) {
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }, modifier = Modifier.background(Color.DarkGray)) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(text = when (mode) { SortMode.DATE_ADDED -> "追加日"; SortMode.SIZE -> "サイズ"; SortMode.NAME -> "名前" }, color = if(galleryState.sortMode == mode) Color.Cyan else Color.White) },
                                    onClick = { galleryState.sortMode = mode; showSortMenu = false }
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { galleryState.isAscending = !galleryState.isAscending },
                    modifier = Modifier.size(32.dp),
                    enabled = isFilterEnabled
                ) {
                    Icon(
                        imageVector = if (galleryState.isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = "昇順/降順",
                        tint = if (isFilterEnabled) Color.White.copy(alpha = 0.7f) else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (showSortTooltip) { LaunchedEffect(Unit) { delay(2000); showSortTooltip = false } }


        Spacer(modifier = Modifier.width(4.dp))

        var showSearchTooltip by remember { mutableStateOf(false) }
        TooltipWrapper(description = "画像検索", showExternally = showSearchTooltip) {
            val context = LocalContext.current
            IconButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ascii2d.net/"))) },
                onLongClick = { showSearchTooltip = true },
                modifier = Modifier.size(36.dp) // 28dp -> 36dp
            ) { Icon(Icons.Default.ImageSearch, null, tint = Color.White, modifier = Modifier.size(20.dp)) } // 18dp -> 20dp
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

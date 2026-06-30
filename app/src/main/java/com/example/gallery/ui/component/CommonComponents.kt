package com.example.gallery.ui.component

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.gallery.service.AnalysisService
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.OperationState
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.AgeRatingFilter
import com.example.gallery.ui.state.DeviceFilter
import com.example.gallery.ui.state.GroupingMode
import com.example.gallery.ui.state.MediaTypeFilter
import com.example.gallery.ui.state.SortMode
import com.example.gallery.ui.theme.GalleryThemeTokens
import kotlinx.coroutines.delay

@Composable
fun GlobalProgressOverlay() {
    val operations by GlobalOperationService.operations.collectAsState(initial = emptyList())
    val context = LocalContext.current
    var isMinimized by rememberSaveable { mutableStateOf(false) }

    if (operations.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(2000f)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 最大2つまで表示する。
                operations.take(2).forEach { op ->
                    OperationCard(
                        op = op,
                        isMinimized = isMinimized,
                        onMinimizeToggle = { isMinimized = !isMinimized },
                        onCancel = {
                            GlobalOperationService.requestCancel(op.id)
                            if (op.isAnalysisOperation) {
                                AnalysisService.cancel(context, op.id)
                            }
                        }
                    )
                }

                if (operations.size > 2 && !isMinimized) {
                    Text(
                    text = "他 ${operations.size - 2} 件のタスクが進行中...",
                        color = Color.Gray,
                        fontSize = com.example.gallery.ui.AppConstants.TinyFontSize,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    } else {
        SideEffect { isMinimized = false }
    }
}

@Composable
private fun OperationCard(
    op: OperationState,
    isMinimized: Boolean,
    onMinimizeToggle: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    if (isMinimized) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onMinimizeToggle() },
            color = colors.surface.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(op.progress.coerceIn(0f, 1f))
                        .background(if (op.tag == "STARTUP_TASKS") colors.secondaryText else colors.accent)
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y < -10f) onMinimizeToggle()
                        }
                    )
                },
            color = colors.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            text = op.title,
                            color = colors.primaryText,
                            fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onMinimizeToggle,
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        ) {
                            Icon(Icons.Default.ArrowDropUp, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${kotlin.math.round(op.progress * 100).toInt()}%",
                            color = colors.accent,
                            fontSize = com.example.gallery.ui.AppConstants.SmallFontSize,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        if (op.canCancel) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(24.dp)
                            ) {
                        Icon(Icons.Default.Close, "中断", tint = colors.mutedText, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { op.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.accent,
                    trackColor = colors.divider
                )
                if (op.text.isNotEmpty()) {
                    Text(
                        text = op.text,
                        color = colors.mutedText,
                        fontSize = com.example.gallery.ui.AppConstants.TinyFontSize,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val OperationState.isAnalysisOperation: Boolean
    get() = tag in analysisOperationTags || id in analysisOperationTags

private val analysisOperationTags = setOf("AI_TAGGING", "COLOR_VECTOR", "AUTO_RATING")

@Composable
fun TooltipWrapper(
    description: String,
    modifier: Modifier = Modifier,
    showExternally: Boolean = false,
    content: @Composable () -> Unit
) {
    // 外部からのトリガーと内部の表示状態をまとめて扱う。
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
                offset = androidx.compose.ui.unit.IntOffset(0, -100), // ナビゲーションバーより少し上に表示する。
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    clippingEnabled = false,
                    usePlatformDefaultWidth = false // タップイベントの吸い込みを防ぐ。
                )
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp) // 画面下端からの余白。
                ) {
                    Text(
                        text = description,
                        color = Color.White,
                        fontSize = com.example.gallery.ui.AppConstants.SubtitleFontSize,
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
    isFilterEnabled: Boolean = true
) {
    val colors = GalleryThemeTokens.colors
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAgeFilterMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFilterEnabled) {
            var showFilterTooltip by remember { mutableStateOf(false) }
            TooltipWrapper(description = "フィルタ", showExternally = showFilterTooltip) {
                IconButton(
                    onClick = { showFilterMenu = true },
                    onLongClick = { showFilterTooltip = true },
                    modifier = Modifier.size(36.dp),
                    enabled = isFilterEnabled
                ) {
                    Icon(Icons.Default.FilterAlt, null, tint = if (isFilterEnabled) colors.primaryText else colors.disabled, modifier = Modifier.size(20.dp))
                    if (isFilterEnabled) {
                        DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, modifier = Modifier.background(colors.surfaceVariant)) {
                            Text("メディア種別", color = colors.mutedText, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize, modifier = Modifier.padding(8.dp))
                            MediaTypeFilter.entries.forEach { filter ->
                                DropdownMenuItem(text = { Text(text = when (filter) { MediaTypeFilter.ALL -> "すべて"; MediaTypeFilter.IMAGE -> "画像"; MediaTypeFilter.VIDEO -> "動画"; MediaTypeFilter.GIF -> "GIF" }, color = if(galleryState.mediaTypeFilter == filter) colors.accent else colors.primaryText) }, onClick = { galleryState.mediaTypeFilter = filter; showFilterMenu = false })
                            }
                            HorizontalDivider(color = colors.divider)
                            Text("デバイス背景", color = colors.mutedText, fontSize = com.example.gallery.ui.AppConstants.TinyFontSize, modifier = Modifier.padding(8.dp))
                            DeviceFilter.entries.forEach { filter ->
                                DropdownMenuItem(text = { Text(text = when (filter) { DeviceFilter.ALL -> "すべて"; DeviceFilter.SMARTPHONE -> "スマホ背景"; DeviceFilter.PC -> "PC背景" }, color = if(galleryState.deviceFilter == filter) colors.accent else colors.primaryText) }, onClick = { galleryState.deviceFilter = filter; showFilterMenu = false })
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
                        AgeRatingFilter.SFW -> colors.success; AgeRatingFilter.R15 -> Color.Yellow; AgeRatingFilter.R18 -> colors.danger; else -> colors.primaryText
                    })
                    DropdownMenu(expanded = showAgeFilterMenu, onDismissRequest = { showAgeFilterMenu = false }, modifier = Modifier.background(colors.surfaceVariant)) {
                        AgeRatingFilter.entries.forEach { filter ->
                            DropdownMenuItem(text = { Text(text = when (filter) { AgeRatingFilter.ALL -> "すべて"; AgeRatingFilter.SFW -> "健全"; AgeRatingFilter.R15 -> "R-15"; AgeRatingFilter.R18 -> "R-18" }, color = if(galleryState.ageRatingFilter == filter) colors.accent else colors.primaryText) }, onClick = { galleryState.ageRatingFilter = filter; showAgeFilterMenu = false })
                        }
                    }
                }
            }
            if (showAgeTooltip) { LaunchedEffect(Unit) { delay(2000); showAgeTooltip = false } }
        }
    }
}

// IconButton に longClick を追加した補助コンポーネント。
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

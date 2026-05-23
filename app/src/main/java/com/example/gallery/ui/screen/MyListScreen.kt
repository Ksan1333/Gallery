package com.example.gallery.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MyListScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onStartAnalysis: (String) -> Unit,
    galleryState: GalleryState,
    onMenuClick: (() -> Unit)? = null,
    onBackToMyList: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    onSubCategorySelected: (Boolean) -> Unit = {},
    initialCategoryType: String? = null, // 追加
    initialTagName: String? = null // 追加
) {
    var selectedCategoryType by rememberSaveable { mutableStateOf<String?>(initialCategoryType) }
    var selectedTagName by rememberSaveable { mutableStateOf<String?>(initialTagName) }
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }

    var showAnalysisDialog by remember { mutableStateOf(false) }
    var analysisPeriodDays by remember { mutableStateOf(30) } // デフォルト30日
    var analysisTypePending by remember { mutableStateOf("AI_TAGGING") }
    var showDatePicker by remember { mutableStateOf(false) }
    var customStartTime by remember { mutableStateOf<Long?>(null) }

    // 初期遷移のための LaunchedEffect
    LaunchedEffect(initialCategoryType, initialTagName) {
        if (initialCategoryType != null) {
            selectedCategoryType = initialCategoryType
            selectedTagName = initialTagName
        }
    }

    LaunchedEffect(selectedCategoryType) {
        isSubCategorySelected = selectedCategoryType != null
        onSubCategorySelected(isSubCategorySelected)
    }

    val selectedCategoryTitle = remember(selectedCategoryType, selectedTagName) {
        when (selectedCategoryType) {
            "Favorites" -> "お気に入り"
            "Untagged" -> "タグなし"
            "Tag" -> selectedTagName
            else -> null
        }
    }

    var showTagCreateDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var allMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(true) }

    val metadataFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllMetadataSummaryFlow() }
    val allMetadata by metadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }

    val allTagsData by galleryState.repository.getAllTagsWithUris().collectAsState(initial = emptyList())
    val tagToUrisMap = remember(allTagsData) { allTagsData.groupBy({ it.tag }, { it.uri }) }
    val taggedUriSet = remember(allTagsData) { allTagsData.filter { !it.tag.endsWith("系") }.map { it.uri }.toSet() }

    LaunchedEffect(galleryState.isMockMode, galleryState.refreshTrigger) {
        if (isLoadingMedia && allMedia.isNotEmpty()) return@LaunchedEffect
        isLoadingMedia = true
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val media = galleryState.repository.getAllMedia(forceRefresh = false)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (allMedia.size != media.size || allMedia.getOrNull(0)?.uri != media.getOrNull(0)?.uri) {
                    allMedia = media
                }
                isLoadingMedia = false
            }
        }
    }

    val filterItem: (MediaData) -> Boolean = { item ->
        val meta = metadataMap[item.uri]
        val rating = meta?.ageRating ?: "SFW"
        when (galleryState.ageRatingFilter) {
            AgeRatingFilter.ALL -> true
            AgeRatingFilter.SFW -> rating == "SFW"
            AgeRatingFilter.R15 -> rating == "R15"
            AgeRatingFilter.R18 -> rating == "R18"
        }
    }

    val favorites = remember(allMedia, metadataMap, galleryState.ageRatingFilter) {
        allMedia.filter { metadataMap[it.uri]?.isFavorite == true && filterItem(it) }
    }
    val untaggedMedia = remember(allMedia, taggedUriSet, galleryState.ageRatingFilter, metadataMap) {
        allMedia.filter { !it.isVideo && it.uri !in taggedUriSet && filterItem(it) }
    }

    val categories = remember(allMedia, tagToUrisMap, favorites, untaggedMedia, galleryState.ageRatingFilter, metadataMap) {
        val allMediaMap = allMedia.associateBy { it.uri }
        mutableListOf<CategoryData>().apply {
            if (favorites.isNotEmpty()) add(CategoryData("Favorites", "お気に入り", favorites.size, favorites.firstOrNull()?.uri))
            
            // 未タグ・未分析の通知を含めたカテゴリ
            val unanalyzedAi = allMedia.filter { !it.isVideo && metadataMap[it.uri]?.isAiAnalyzed != true && filterItem(it) }
            val unanalyzedVector = allMedia.filter { !it.isVideo && metadataMap[it.uri]?.hasFeatureVector != true && filterItem(it) }
            
            if (untaggedMedia.isNotEmpty() || unanalyzedAi.isNotEmpty() || unanalyzedVector.isNotEmpty()) {
                val subText = buildString {
                    if (untaggedMedia.isNotEmpty()) append("未タグ:${unanalyzedAi.size}\n")
                    if (unanalyzedVector.isNotEmpty()) append("未ベクトル:${unanalyzedVector.size}")
                }.trim()
                add(CategoryData("Untagged", "未整理・未分析", (untaggedMedia + unanalyzedAi + unanalyzedVector).distinctBy { it.uri }.size, untaggedMedia.firstOrNull()?.uri ?: unanalyzedAi.firstOrNull()?.uri ?: unanalyzedVector.firstOrNull()?.uri, subTitle = subText))
            }

            tagToUrisMap.filter { !it.key.endsWith("系") }.forEach { (tag, uris) ->
                val filtered = uris.mapNotNull { allMediaMap[it] }.filter { filterItem(it) }
                if (filtered.isNotEmpty()) add(CategoryData("Tag:$tag", tag, filtered.size, filtered.firstOrNull()?.uri))
            }
        }.sortedByDescending { it.count }
    }

    var taggedMediaDetail by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(selectedCategoryType, selectedTagName, categories, favorites, untaggedMedia, allMedia, metadataMap, galleryState.ageRatingFilter) {
        taggedMediaDetail = when (selectedCategoryType) {
            "Favorites" -> favorites
            "Untagged" -> {
                val unanalyzedAi = allMedia.filter { !it.isVideo && metadataMap[it.uri]?.isAiAnalyzed != true && filterItem(it) }
                val unanalyzedVector = allMedia.filter { !it.isVideo && metadataMap[it.uri]?.hasFeatureVector != true && filterItem(it) }
                (untaggedMedia + unanalyzedAi + unanalyzedVector).distinctBy { it.uri }
            }
            "Tag" -> {
                val allMediaMap = allMedia.associateBy { it.uri }
                tagToUrisMap[selectedTagName]?.mapNotNull { allMediaMap[it] }?.filter { filterItem(it) } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    val isStartupCompleted by com.example.gallery.service.ThumbnailGenerationService.isStartupTasksCompleted.collectAsState()
    val isStartupProcessing by com.example.gallery.service.ThumbnailGenerationService.isProcessing.collectAsState()
    
    // サムネイルやベクトル分析が残っているかチェック
    val hasPendingTasks = remember(allMedia, metadataMap, isStartupProcessing, isStartupCompleted, isLoadingMedia) {
        // 1. 読み込み中、または起動時タスク実行中なら確実にブロック
        if (isLoadingMedia || isStartupProcessing || !isStartupCompleted) return@remember true
        
        // 2. メディアが空の場合は、全て完了した（または元々無い）とみなす
        if (allMedia.isEmpty()) return@remember false

        // 3. 具体的な未処理アイテム（ベクトル未抽出）の有無をチェック
        allMedia.any { !it.isVideo && metadataMap[it.uri]?.hasFeatureVector != true }
    }

    CategoryScreen(
        title = "マイリスト",
        categories = categories,
        isLoading = isLoadingMedia,
        galleryState = galleryState,
        onNavigateToTag = { tag ->
            selectedCategoryType = "Tag"
            selectedTagName = tag
        },
        onMenuClick = if (selectedCategoryType == null) onMenuClick else null,
        onCategoryClick = { category ->
            when {
                category.id == "Favorites" -> selectedCategoryType = "Favorites"
                category.id == "Untagged" -> selectedCategoryType = "Untagged"
                category.id.startsWith("Tag:") -> { selectedCategoryType = "Tag"; selectedTagName = category.title }
            }
        },
        onShowViewer = onShowViewer,
        onHideViewer = onHideViewer,
        topBarActions = {
            topBarActions()
            IconButton(
                onClick = { 
                    analysisTypePending = "AI_TAGGING"
                    showAnalysisDialog = true 
                },
                enabled = !hasPendingTasks
            ) { 
                Icon(
                    Icons.Default.AutoAwesome, 
                    "AI解析", 
                    tint = if (!hasPendingTasks) Color.White else Color.Gray
                )
            }
            IconButton(onClick = { showTagCreateDialog = true }) { Icon(Icons.Default.Add, "タグ作成", tint = Color.White) }
        },
        selectedCategoryTitle = selectedCategoryTitle,
        selectedCategoryMedia = taggedMediaDetail,
        onBackFromCategory = { selectedCategoryType = null; selectedTagName = null },
        onTabIconClick = { onBackToMyList() },
        lastViewedUri = galleryState.lastViewedUri,
        onPageChangedInViewer = { galleryState.lastViewedUri = it },
        onScrollConsumed = { galleryState.lastViewedUri = null },
        showThumbnails = false,
        initialColumnIndex = 3 // 3列表示に設定
    )

    if (showTagCreateDialog) {
        AlertDialog(
            onDismissRequest = { showTagCreateDialog = false },
            containerColor = Color(0xFF1A1A1A), titleContentColor = Color.White, textContentColor = Color.LightGray,
            title = { Text("新規タグ作成", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                TextField(value = newTagName, onValueChange = { newTagName = it }, placeholder = { Text("タグ名を入力", color = Color.Gray) }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.Black.copy(alpha = 0.3f), unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { Button(onClick = { if (newTagName.isNotBlank()) { showTagCreateDialog = false; newTagName = "" } }) { Text("作成") } },
            dismissButton = { TextButton(onClick = { showTagCreateDialog = false }) { Text("キャンセル") } },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAnalysisDialog) {
        val filteredList = remember(allMedia, analysisPeriodDays, customStartTime, metadataMap, analysisTypePending) {
            val startTime = if (analysisPeriodDays == -2) {
                customStartTime ?: 0L
            } else if (analysisPeriodDays == -1) {
                0L
            } else {
                System.currentTimeMillis() - (analysisPeriodDays.toLong() * 24 * 3600 * 1000)
            }
            
            allMedia.filter { item ->
                !item.isVideo && 
                (item.dateAdded >= startTime) &&
                (if (analysisTypePending == "AI_TAGGING") metadataMap[item.uri]?.isAiAnalyzed != true 
                 else metadataMap[item.uri]?.hasFeatureVector != true)
            }
        }
        
        val estimatedMinutes = remember(filteredList.size) {
            // 1枚あたり約0.5秒と仮定 (機種により変動)
            val totalSeconds = filteredList.size * 0.5
            (totalSeconds / 60).toInt().coerceAtLeast(1)
        }

        AlertDialog(
            onDismissRequest = { showAnalysisDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("AI解析の実行", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("解析対象の期間を選択してください:", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    val periods = listOf(
                        7 to "直近7日間",
                        30 to "直近30日間",
                        -1 to "すべての期間",
                        -2 to if (customStartTime != null) {
                            "カスタム: ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(customStartTime!!))} 以降"
                        } else {
                            "カレンダーから選ぶ..."
                        }
                    )
                    
                    periods.forEach { (days, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (days == -2) {
                                        showDatePicker = true
                                    } else {
                                        analysisPeriodDays = days
                                        customStartTime = null
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = if (days == -2) analysisPeriodDays == -2 else analysisPeriodDays == days, 
                                onClick = { 
                                    if (days == -2) {
                                        showDatePicker = true
                                    } else {
                                        analysisPeriodDays = days
                                        customStartTime = null
                                    }
                                }
                            )
                            Text(label, color = if (days == -2 && customStartTime != null) Color.Cyan else Color.White, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    
                    Text("対象画像: ${filteredList.size} 枚", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("予想時間: 約 $estimatedMinutes 分", color = Color.Cyan)
                    
                    if (filteredList.isEmpty()) {
                        Text("\n※対象となる未解析の画像がありません。", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showAnalysisDialog = false
                        // periodDays が -2 の場合は customStartTime を渡す必要があるが、
                        // AnalysisProgressScreen と AnalysisService が Long の startTime を受け取れるように修正が必要。
                        // 現状は Int の periodDays なので、暫定的に 0L からの差分日数に変換して渡すか、インターフェースを拡張する。
                        val daysToPass = if (analysisPeriodDays == -2 && customStartTime != null) {
                            val diff = System.currentTimeMillis() - customStartTime!!
                            (diff / (24 * 3600 * 1000)).toInt().coerceAtLeast(0)
                        } else {
                            analysisPeriodDays
                        }
                        
                        galleryState.navController?.currentBackStackEntry?.savedStateHandle?.set("periodDays", daysToPass)
                        onStartAnalysis(analysisTypePending) 
                    },
                    enabled = filteredList.isNotEmpty()
                ) {
                    Text("解析開始")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnalysisDialog = false }) {
                    Text("キャンセル", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customStartTime ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customStartTime = datePickerState.selectedDateMillis
                    analysisPeriodDays = -2
                    showDatePicker = false
                }) {
                    Text("決定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("キャンセル")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

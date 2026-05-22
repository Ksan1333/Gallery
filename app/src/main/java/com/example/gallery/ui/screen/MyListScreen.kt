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
import kotlinx.coroutines.withContext

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
        withContext(Dispatchers.IO) {
            val media = galleryState.repository.getAllMedia(forceRefresh = false)
            withContext(Dispatchers.Main) {
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
                onClick = { onStartAnalysis("AI_TAGGING") },
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
            title = { Text("新規タグ作成", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
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
}

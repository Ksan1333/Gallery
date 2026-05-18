package com.example.gallery.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.example.gallery.ui.component.FloatingRandomImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MyListScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onStartAnalysis: () -> Unit,
    galleryState: GalleryState,
    onBackToMyList: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    onSubCategorySelected: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedCategoryType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTagName by rememberSaveable { mutableStateOf<String?>(null) }
    
    // 親にカテゴリ選択状態を通知
    LaunchedEffect(selectedCategoryType) {
        onSubCategorySelected(selectedCategoryType != null)
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

    // 全メディアを取得
    var allMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(true) }
    
    // メタデータの取得 - モード切り替え時のリセットを防ぐ
    val metadataFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllMetadataFlow() }
    val allMetadata by metadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }

    // タグ情報の取得
    val allTagsData by galleryState.repository.mediaDao.getAllTagsWithUris().collectAsState(initial = emptyList())
    
    // タグ名 -> URIリストのマップを作成
    val tagToUrisMap = remember(allTagsData) {
        allTagsData.groupBy({ it.tag }, { it.uri })
    }

    // タグ付け済みURIの取得（「〜系」は除外して判定）
    val taggedUriSet = remember(allTagsData) {
        allTagsData.filter { !it.tag.endsWith("系") }.map { it.uri }.toSet()
    }

    // メディアのロード
    LaunchedEffect(galleryState.isMockMode) {
        isLoadingMedia = true
        withContext(Dispatchers.IO) {
            // メタデータの準備を待つ
            galleryState.repository.getAllMetadata()
            
            val media = galleryState.repository.getAllMedia()
            withContext(Dispatchers.Main) {
                allMedia = media
                delay(100)
                isLoadingMedia = false
            }
        }
    }

    // 共通のフィルタリング関数
    val filterItem: (MediaData) -> Boolean = { item ->
        val meta = metadataMap[item.uri]
        val rating = meta?.ageRating ?: "SFW"
        when (galleryState.ageRatingFilter) {
            AgeRatingFilter.ALL -> true
            AgeRatingFilter.SFW -> rating == "SFW"
            AgeRatingFilter.R15 -> rating == "SFW" || rating == "R15"
            AgeRatingFilter.R18 -> true
        }
    }
    
    // お気に入り（フィルタ適用済み）
    val favorites = remember(allMedia, metadataMap, galleryState.ageRatingFilter) {
        allMedia.filter { item -> 
            metadataMap[item.uri]?.isFavorite == true && filterItem(item)
        }
    }
    
    // タグなし（フィルタ適用済み）
    val untaggedMedia = remember(allMedia, taggedUriSet, galleryState.ageRatingFilter, metadataMap) {
        allMedia.filter { item -> 
            !item.isVideo && item.uri !in taggedUriSet && filterItem(item)
        }
    }

    // 各カテゴリの構築（フィルタを即座に反映）
    val categories = remember(allMedia, tagToUrisMap, favorites, untaggedMedia, galleryState.ageRatingFilter, metadataMap) {
        val allMediaMap = allMedia.associateBy { it.uri }
        val list = mutableListOf<CategoryData>()
        
        if (favorites.isNotEmpty()) {
            list.add(CategoryData("Favorites", "お気に入り", favorites.size, favorites.firstOrNull()?.uri))
        }
        if (untaggedMedia.isNotEmpty()) {
            list.add(CategoryData("Untagged", "タグなし", untaggedMedia.size, untaggedMedia.firstOrNull()?.uri))
        }
        
        tagToUrisMap.filter { !it.key.endsWith("系") }.forEach { (tag, uris) ->
            val filteredForTag = uris.mapNotNull { allMediaMap[it] }.filter { filterItem(it) }
            if (filteredForTag.isNotEmpty()) {
                list.add(CategoryData(
                    "Tag:$tag", 
                    tag, 
                    filteredForTag.size, 
                    filteredForTag.firstOrNull()?.uri
                ))
            }
        }
        list.sortedBy { it.title }
    }

    var taggedMediaDetail by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(selectedCategoryType, selectedTagName, categories, favorites, untaggedMedia) {
        taggedMediaDetail = when (selectedCategoryType) {
            "Favorites" -> favorites
            "Untagged" -> untaggedMedia
            "Tag" -> {
                val allMediaMap = allMedia.associateBy { it.uri }
                tagToUrisMap[selectedTagName]?.mapNotNull { allMediaMap[it] }?.filter { filterItem(it) } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    val unanalyzedCount by remember(galleryState.ageRatingFilter) { 
        galleryState.repository.getUnanalyzedAiCount(galleryState.ageRatingFilter) 
    }.collectAsState(initial = 0)
    var showWarning by remember { mutableStateOf(false) }

    LaunchedEffect(unanalyzedCount) {
        if (unanalyzedCount > 0) {
            delay(1000)
            showWarning = true
            delay(4000)
            showWarning = false
        }
    }

    CategoryScreen(
        title = "マイリスト",
        categories = categories,
        isLoading = isLoadingMedia,
        galleryState = galleryState,
        onCategoryClick = { category ->
            if (category.id == "Favorites") {
                selectedCategoryType = "Favorites"
            } else if (category.id == "Untagged") {
                selectedCategoryType = "Untagged"
            } else if (category.id.startsWith("Tag:")) {
                selectedCategoryType = "Tag"
                selectedTagName = category.title
            }
        },
        onShowViewer = onShowViewer,
        onHideViewer = onHideViewer,
        topBarActions = {
            topBarActions()
            IconButton(onClick = onStartAnalysis) { Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI・カラー自動解析", tint = Color.White) }
            IconButton(onClick = { showTagCreateDialog = true }) { Icon(Icons.Default.Add, contentDescription = "タグ作成", tint = Color.White) }
        },
        selectedCategoryTitle = selectedCategoryTitle,
        selectedCategoryMedia = taggedMediaDetail,
        onBackFromCategory = {
            selectedCategoryType = null
            selectedTagName = null
        },
        onTabIconClick = { onBackToMyList() }
    )

    if (showTagCreateDialog) {
        AlertDialog(
            onDismissRequest = { showTagCreateDialog = false },
            containerColor = Color(0xFF1A1A1A), titleContentColor = Color.White, textContentColor = Color.LightGray,
            title = { Text("新規タグ作成", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                TextField(value = newTagName, onValueChange = { newTagName = it }, placeholder = { Text("タグ名を入力", color = Color.Gray) }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.Black.copy(alpha = 0.3f), unfocusedContainerColor = Color.Black.copy(alpha = 0.3f), focusedIndicatorColor = MaterialTheme.colorScheme.primary, unfocusedIndicatorColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { Button(onClick = { if (newTagName.isNotBlank()) { showTagCreateDialog = false; newTagName = "" } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("作成", color = Color.White) } },
            dismissButton = { TextButton(onClick = { showTagCreateDialog = false }) { Text("キャンセル", color = Color.Gray) } },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }

    if (showWarning && selectedCategoryType == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            val displayMediaList = remember(allMedia, galleryState.ageRatingFilter, metadataMap) {
                allMedia.filter { item ->
                    val meta = metadataMap[item.uri]
                    val rating = meta?.ageRating ?: "SFW"
                    when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.ALL -> true
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "SFW" || rating == "R15"
                        AgeRatingFilter.R18 -> true
                    }
                }
            }
            if (displayMediaList.isNotEmpty()) {
                FloatingRandomImage(
                    mediaList = displayMediaList,
                    ageRatingFilter = galleryState.ageRatingFilter,
                    metadataMap = metadataMap
                )
            }
            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 32.dp, end = 32.dp)
            ) {
                Text(
                    text = "${unanalyzedCount}件が未分析です。右上から分析してください",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

package com.example.gallery.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.MediaData
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MyListScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onStartAnalysis: () -> Unit,
    galleryState: GalleryState,
    onBackToMyList: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    var selectedCategoryType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTagName by rememberSaveable { mutableStateOf<String?>(null) }
    
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

    // 高速化のため、全メディアを一度だけ取得してメモリ内でフィルタリングする
    var allMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(true) }
    
    val tagCountsList by galleryState.repository.getAllTagsWithCounts().collectAsState(initial = emptyList())
    val tagThumbnails = remember { mutableStateMapOf<String, String?>() }
    
    // お気に入りURIの取得
    val favoriteUrisFlow = remember {
        galleryState.repository.getAllMetadataFlow().map { list -> 
            list.filter { it.isFavorite }.map { it.uri }.toSet() 
        }
    }
    val favoriteUris by favoriteUrisFlow.collectAsState(initial = emptySet())

    // タグ付け済みURIの取得
    val taggedUrisFlow = remember { galleryState.repository.getManualTaggedUrisFlow() }
    val taggedUris by taggedUrisFlow.collectAsState(initial = emptyList())
    val taggedUriSet = remember(taggedUris) { taggedUris.toSet() }

    // メディアのロード (リポジトリ側のキャッシュが効くので速くなる)
    LaunchedEffect(galleryState.isMockMode) {
        isLoadingMedia = true
        withContext(Dispatchers.IO) {
            val media = galleryState.repository.getAllMedia()
            withContext(Dispatchers.Main) {
                allMedia = media
                isLoadingMedia = false
            }
        }
    }

    // タグサムネイルのロード
    LaunchedEffect(tagCountsList) {
        tagCountsList.forEach { tagCount ->
            if (!tagCount.tag.endsWith("系") && !tagThumbnails.containsKey(tagCount.tag)) {
                launch {
                    val thumb = galleryState.repository.getThumbnailForTag(tagCount.tag).first()
                    if (thumb != null) {
                        tagThumbnails[tagCount.tag] = thumb
                    }
                }
            }
        }
    }

    val favorites = remember(allMedia, favoriteUris) {
        allMedia.filter { it.uri in favoriteUris }
    }
    
    val untaggedMedia = remember(allMedia, taggedUriSet) {
        allMedia.filter { !it.isVideo && it.uri !in taggedUriSet }
    }

    var taggedMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(selectedCategoryType, selectedTagName, allMedia) {
        if (selectedCategoryType == "Tag" && selectedTagName != null) {
            galleryState.repository.getMediaForTag(selectedTagName!!)
                .collect { taggedMedia = it }
        }
    }

    val unanalyzedCount by remember { galleryState.repository.getUnanalyzedAiCount() }.collectAsState(initial = 0)
    var showWarning by remember { mutableStateOf(false) }

    LaunchedEffect(unanalyzedCount) {
        if (unanalyzedCount > 0) {
            delay(1000)
            showWarning = true
            delay(4000)
            showWarning = false
        }
    }

    val categories = remember(favorites.size, untaggedMedia.size, tagCountsList, tagThumbnails.size) {
        val list = mutableListOf<CategoryData>()
        list.add(CategoryData("Favorites", "お気に入り", favorites.size, favorites.firstOrNull()?.uri))
        list.add(CategoryData("Untagged", "タグなし", untaggedMedia.size, untaggedMedia.firstOrNull()?.uri))
        
        tagCountsList.filter { !it.tag.endsWith("系") }.forEach { tagCount ->
            list.add(CategoryData("Tag:${tagCount.tag}", tagCount.tag, tagCount.count, tagThumbnails[tagCount.tag]))
        }
        list
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
        selectedCategoryMedia = when (selectedCategoryType) {
            "Favorites" -> favorites
            "Untagged" -> untaggedMedia
            "Tag" -> taggedMedia
            else -> emptyList()
        },
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

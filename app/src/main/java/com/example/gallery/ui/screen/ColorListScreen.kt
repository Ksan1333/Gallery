package com.example.gallery.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.gallery.ui.AgeRatingFilter
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import com.example.gallery.ui.component.FloatingRandomImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ColorListScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onStartAnalysis: () -> Unit,
    galleryState: GalleryState,
    onBackToColorList: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    onSubCategorySelected: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    
    // カテゴリ詳細が表示されているかどうかの状態
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }

    // 親にカテゴリ選択状態を通知
    LaunchedEffect(selectedCategoryId) {
        isSubCategorySelected = selectedCategoryId != null
        onSubCategorySelected(isSubCategorySelected)
    }

    // 最後に表示していたメディアURIを保持（戻り時のスクロール用）
    var lastViewedUri by rememberSaveable { mutableStateOf<String?>(null) }

    // 色の並び順を定義
    val colorOrder = listOf("レッド系", "オレンジ系", "イエロー系", "グリーン系", "ブルー系", "パープル系", "ピンク系", "ホワイト系", "グレー系", "ブラック系")

    // メタデータの取得 - モード切り替え時のリセットを防ぐ
    val metadataFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllMetadataFlow() }
    val allMetadata by metadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }

    // 全てのカラータグデータを取得 - モード切り替え時のリセットを防ぐ
    val colorTagFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllColorTags() }
    val allColorTagMedia by colorTagFlow.collectAsState(initial = emptyMap())

    // ロード状態の管理
    var isGlobalLoading by remember { mutableStateOf(false) }
    LaunchedEffect(galleryState.isMockMode) {
        isGlobalLoading = true
        galleryState.repository.getAllMetadata()
        delay(300) // データの準備とFlowの反映を待つ
        isGlobalLoading = false
    }

    // フィルタリングされたカテゴリデータを作成
    val filteredColorCategories = remember(allColorTagMedia, galleryState.ageRatingFilter, metadataMap) {
        allColorTagMedia.map { (tag, mediaList) ->
            val filteredMedia = mediaList.filter { item ->
                val meta = metadataMap[item.uri]
                val rating = meta?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) {
                    AgeRatingFilter.ALL -> true
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                }
            }
            CategoryData(
                id = tag,
                title = tag,
                count = filteredMedia.size,
                thumbnail = filteredMedia.firstOrNull()?.uri,
                indicatorColor = getColorForTag(tag)
            )
        }
        .filter { it.count > 0 }
        .sortedBy { cat -> colorOrder.indexOf(cat.title).let { if (it == -1) 999 else it } }
    }

    // ロード状態の管理
    val isLoadingState = remember(allColorTagMedia, allMetadata) {
        allColorTagMedia.isEmpty() && allMetadata.isEmpty()
    }
    
    var taggedMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(selectedCategoryId, allColorTagMedia, galleryState.ageRatingFilter, metadataMap) {
        if (selectedCategoryId != null) {
            val mediaInTag = allColorTagMedia[selectedCategoryId] ?: emptyList()
            taggedMedia = mediaInTag.filter { item ->
                val meta = metadataMap[item.uri]
                val rating = meta?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) {
                    AgeRatingFilter.ALL -> true
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                }
            }
        }
    }

    val unanalyzedCount by remember(galleryState.ageRatingFilter) { 
        galleryState.repository.getUnanalyzedColorCount(galleryState.ageRatingFilter) 
    }.collectAsState(initial = 0)
    var showWarning by remember { mutableStateOf(false) }
    
    // 画面表示から少し待って、かつカテゴリが読み込まれている場合のみ警告を出す
    LaunchedEffect(unanalyzedCount, filteredColorCategories, isGlobalLoading, isLoadingState) {
        if (!isGlobalLoading && !isLoadingState && unanalyzedCount > 0 && filteredColorCategories.isNotEmpty()) {
            delay(2000)
            showWarning = true
            delay(4000)
            showWarning = false
        } else {
            showWarning = false
        }
    }

    CategoryScreen(
        title = "カラーリスト",
        categories = filteredColorCategories,
        isLoading = isLoadingState && unanalyzedCount > 0, // 全くデータがない場合のみロード表示
        galleryState = galleryState,
        onCategoryClick = { selectedCategoryId = it.id },
        onShowViewer = onShowViewer,
        onHideViewer = onHideViewer,
        topBarActions = {
            topBarActions()
            IconButton(onClick = onStartAnalysis) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI・カラー自動解析", tint = Color.White)
            }
        },
        loadingContent = {
            val allMediaList = remember { mutableStateListOf<MediaData>() }
            LaunchedEffect(Unit) {
                var media = galleryState.repository.getAllMedia()
                var retryCount = 0
                while (media.isEmpty() && retryCount < 5) {
                    delay(500)
                    media = galleryState.repository.getAllMedia()
                    retryCount++
                }
                allMediaList.addAll(media)
            }

            if (allMediaList.isNotEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    FloatingRandomImage(
                        mediaList = allMediaList,
                        ageRatingFilter = galleryState.ageRatingFilter,
                        metadataMap = metadataMap
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    val analyzedCount = allColorTagMedia.size
                    val totalExpected = 10 // Approximate color categories
                    val progressPercent = if (totalExpected > 0) (analyzedCount.toFloat() / totalExpected * 100).toInt().coerceAtMost(100) else 0
                    Text("${progressPercent}%", color = Color.White, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("カラーカテゴリを読み込み中...", color = Color.White, fontSize = 16.sp)
            }
        },
        emptyContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("解析済みの画像がありません", color = Color.Gray)
                Button(onClick = onStartAnalysis, modifier = Modifier.padding(top = 16.dp)) {
                    Text("解析を開始する")
                }
            }
        },
        selectedCategoryTitle = selectedCategoryId,
        selectedCategoryMedia = taggedMedia,
        onBackFromCategory = { selectedCategoryId = null },
        onTabIconClick = { onBackToColorList() },
        lastViewedUri = lastViewedUri,
        onPageChangedInViewer = { lastViewedUri = it }
    )

    if (showWarning && selectedCategoryId == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            var fullMediaList by remember { mutableStateOf<List<MediaData>>(emptyList()) }
            LaunchedEffect(galleryState.ageRatingFilter, metadataMap) {
                fullMediaList = galleryState.repository.getAllMedia().filter { item ->
                    val meta = metadataMap[item.uri]
                    val rating = meta?.ageRating ?: "SFW"
                    when (galleryState.ageRatingFilter) {
                        AgeRatingFilter.ALL -> true
                        AgeRatingFilter.SFW -> rating == "SFW"
                        AgeRatingFilter.R15 -> rating == "R15"
                        AgeRatingFilter.R18 -> rating == "R18"
                    }
                }
            }

            if (fullMediaList.isNotEmpty()) {
                FloatingRandomImage(
                    mediaList = fullMediaList,
                    ageRatingFilter = galleryState.ageRatingFilter,
                    metadataMap = metadataMap
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.9f),
                shape = RoundedCornerShape(24.dp),
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

fun getColorForTag(title: String): Color {
    return when {
        title.contains("レッド") -> Color(0xFFEF5350)
        title.contains("オレンジ") -> Color(0xFFFFA726)
        title.contains("イエロー") -> Color(0xFFFFEE58)
        title.contains("グリーン") -> Color(0xFF66BB6A)
        title.contains("ブルー") -> Color(0xFF42A5F5)
        title.contains("パープル") -> Color(0xFFAB47BC)
        title.contains("ピンク") -> Color(0xFFEC407A)
        title.contains("ブラック") -> Color(0xFF212121)
        title.contains("ホワイト") -> Color(0xFFF5F5F5)
        title.contains("グレー") -> Color(0xFF9E9E9E)
        else -> Color.Gray
    }
}

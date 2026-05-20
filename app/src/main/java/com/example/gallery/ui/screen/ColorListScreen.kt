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
import kotlinx.coroutines.launch

@Composable
fun ColorListScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onStartAnalysis: () -> Unit,
    galleryState: GalleryState,
    onMenuClick: (() -> Unit)? = null,
    onBackToColorList: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    onSubCategorySelected: (Boolean) -> Unit = {}
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var isSubCategorySelected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedCategoryId) {
        isSubCategorySelected = selectedCategoryId != null
        onSubCategorySelected(isSubCategorySelected)
    }

    var lastViewedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val colorOrder = listOf("レッド系", "オレンジ系", "イエロー系", "グリーン系", "ブルー系", "パープル系", "ピンク系", "ホワイト系", "グレー系", "ブラック系")

    val metadataFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllMetadataFlow() }
    val allMetadata by metadataFlow.collectAsState(initial = emptyList())
    val metadataMap = remember(allMetadata) { allMetadata.associateBy { it.uri } }

    val colorTagFlow = remember(galleryState.isMockMode) { galleryState.repository.getAllColorTags() }
    val allColorTagMedia by colorTagFlow.collectAsState(initial = emptyMap())

    var isGlobalLoading by remember { mutableStateOf(false) }
    LaunchedEffect(galleryState.isMockMode, galleryState.refreshTrigger) {
        isGlobalLoading = true
        galleryState.repository.getAllMetadata()
        delay(300)
        isGlobalLoading = false
    }

    val filteredCategories = remember(allColorTagMedia, galleryState.ageRatingFilter, metadataMap) {
        allColorTagMedia.map { (tag, mediaList) ->
            val filtered = mediaList.filter { item ->
                val rating = metadataMap[item.uri]?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) {
                    AgeRatingFilter.ALL -> true
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                }
            }
            CategoryData(tag, tag, filtered.size, filtered.firstOrNull()?.uri, indicatorColor = getColorForTag(tag))
        }.filter { it.count > 0 }.sortedBy { colorOrder.indexOf(it.title).let { i -> if (i == -1) 999 else i } }
    }

    val isLoadingState = allColorTagMedia.isEmpty() && allMetadata.isEmpty()

    var taggedMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(selectedCategoryId, allColorTagMedia, galleryState.ageRatingFilter, metadataMap) {
        if (selectedCategoryId != null) {
            taggedMedia = (allColorTagMedia[selectedCategoryId] ?: emptyList()).filter { item ->
                val rating = metadataMap[item.uri]?.ageRating ?: "SFW"
                when (galleryState.ageRatingFilter) {
                    AgeRatingFilter.ALL -> true
                    AgeRatingFilter.SFW -> rating == "SFW"
                    AgeRatingFilter.R15 -> rating == "R15"
                    AgeRatingFilter.R18 -> rating == "R18"
                }
            }
        }
    }

    val unanalyzedCount by remember(galleryState.ageRatingFilter) { galleryState.repository.getUnanalyzedColorCount(galleryState.ageRatingFilter) }.collectAsState(initial = 0)
    var showWarning by remember { mutableStateOf(false) }

    LaunchedEffect(unanalyzedCount, filteredCategories, isGlobalLoading, isLoadingState) {
        if (!isGlobalLoading && !isLoadingState && unanalyzedCount > 0 && filteredCategories.isNotEmpty()) {
            delay(2000); showWarning = true; delay(4000); showWarning = false
        } else showWarning = false
    }

    CategoryScreen(
        title = "カラーリスト",
        categories = filteredCategories,
        isLoading = isLoadingState && unanalyzedCount > 0,
        galleryState = galleryState,
        onMenuClick = if (selectedCategoryId == null) onMenuClick else null,
        onCategoryClick = { selectedCategoryId = it.id },
        onShowViewer = onShowViewer,
        onHideViewer = onHideViewer,
        topBarActions = {
            topBarActions()
            IconButton(onClick = onStartAnalysis) { Icon(Icons.Default.AutoAwesome, "自動解析", tint = Color.White) }
        },
        loadingContent = {
            val allMediaList = remember { mutableStateListOf<MediaData>() }
            LaunchedEffect(Unit) {
                var media = galleryState.repository.getAllMedia()
                repeat(5) { if (media.isEmpty()) { delay(500); media = galleryState.repository.getAllMedia() } }
                allMediaList.addAll(media)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.fillMaxSize())
                    val progress = (filteredCategories.size.toFloat() / 10f * 100).toInt().coerceAtMost(100)
                    Text("$progress%", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("カラーカテゴリを読み込み中...", color = Color.White, fontSize = 16.sp)
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
            Surface(
                color = Color.Black.copy(alpha = 0.9f), shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp, start = 32.dp, end = 32.dp)
            ) {
                Text("${unanalyzedCount}件が未分析です。右上から分析してください", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    }
}

fun getColorForTag(title: String) = when {
    title.contains("レッド") -> Color(0xFFEF5350); title.contains("オレンジ") -> Color(0xFFFFA726)
    title.contains("イエロー") -> Color(0xFFFFEE58); title.contains("グリーン") -> Color(0xFF66BB6A)
    title.contains("ブルー") -> Color(0xFF42A5F5); title.contains("パープル") -> Color(0xFFAB47BC)
    title.contains("ピンク") -> Color(0xFFEC407A); title.contains("ブラック") -> Color(0xFF212121)
    title.contains("ホワイト") -> Color(0xFFF5F5F5); title.contains("グレー") -> Color(0xFF9E9E9E)
    else -> Color.Gray
}

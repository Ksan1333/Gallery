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
import com.example.gallery.ui.component.CategoryData
import com.example.gallery.ui.component.CategoryScreen
import com.example.gallery.ui.component.FloatingRandomImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

@Composable
fun ColorListScreen(
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    onStartAnalysis: () -> Unit,
    galleryState: GalleryState,
    onBackToColorList: () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {}
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    
    // ロード状態の管理
    var isLoadingState by remember { mutableStateOf(true) }

    // 色の並び順を定義
    val colorOrder = listOf("レッド系", "オレンジ系", "イエロー系", "グリーン系", "ブルー系", "パープル系", "ピンク系", "ホワイト系", "グレー系", "ブラック系")

    val colorTagData by remember {
        galleryState.repository.getAllTagsWithCounts().map { tagCounts ->
            tagCounts.filter { it.tag.endsWith("系") }
                .sortedBy { tagCount -> colorOrder.indexOf(tagCount.tag).let { if (it == -1) 999 else it } }
        }
    }.collectAsState(initial = emptyList())

    val colorThumbnails = remember { mutableStateMapOf<String, String?>() }
    colorTagData.forEach { tagCount ->
        val tag = tagCount.tag
        val thumb by galleryState.repository.getThumbnailForTag(tag).collectAsState(initial = null)
        LaunchedEffect(thumb) {
            if (thumb != null) colorThumbnails[tag] = thumb
        }
    }
    
    // タグのデータが揃ったかチェック
    LaunchedEffect(colorTagData) {
        if (colorTagData.isNotEmpty()) {
            isLoadingState = false
        }
    }

    // タグが一つもない場合もロード完了とする
    val allTags by galleryState.repository.getAllTagNames().collectAsState(initial = null)
    LaunchedEffect(allTags) {
        if (allTags != null && allTags!!.none { it.endsWith("系") }) {
            isLoadingState = false
        }
    }
    
    var taggedMedia by remember { mutableStateOf<List<MediaData>>(emptyList()) }
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            galleryState.repository.getMediaForTag(selectedCategoryId!!)
                .collect { taggedMedia = it }
        }
    }

    val unanalyzedCount by remember { galleryState.repository.getUnanalyzedColorCount() }.collectAsState(initial = 0)
    var showWarning by remember { mutableStateOf(false) }
    
    LaunchedEffect(unanalyzedCount) {
        if (unanalyzedCount > 0) {
            delay(1000)
            showWarning = true
            delay(4000)
            showWarning = false
        }
    }

    val categories = colorTagData.map { tagCount ->
        CategoryData(
            id = tagCount.tag,
            title = tagCount.tag,
            count = tagCount.count,
            thumbnail = colorThumbnails[tagCount.tag],
            indicatorColor = getColorForTag(tagCount.tag)
        )
    }

    CategoryScreen(
        title = "カラーリスト",
        categories = categories,
        isLoading = isLoadingState,
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
                    FloatingRandomImage(mediaList = allMediaList)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                    val progressPercent = if (colorTagData.isNotEmpty()) (colorThumbnails.size.toFloat() / colorTagData.size.toFloat() * 100).toInt() else 0
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
        onTabIconClick = { onBackToColorList() }
    )

    if (showWarning && selectedCategoryId == null) {
        Box(modifier = Modifier.fillMaxSize()) {
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

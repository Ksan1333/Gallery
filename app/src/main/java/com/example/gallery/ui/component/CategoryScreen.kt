package com.example.gallery.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.MediaData
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CategoryData(
    val id: String,
    val title: String,
    val count: Int,
    val thumbnail: String?,
    val indicatorColor: Color? = null
)

@Composable
fun CategoryScreen(
    title: String,
    categories: List<CategoryData>,
    isLoading: Boolean = false,
    galleryState: GalleryState,
    onCategoryClick: (CategoryData) -> Unit,
    onShowViewer: () -> Unit,
    onHideViewer: () -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    emptyContent: @Composable BoxScope.() -> Unit = { Text("データがありません", color = Color.Gray) },
    loadingContent: @Composable BoxScope.() -> Unit = { CircularProgressIndicator(color = Color.White) },
    selectedCategoryTitle: String? = null,
    selectedCategoryMedia: List<MediaData> = emptyList(),
    onBackFromCategory: () -> Unit,
    onTabIconClick: (String) -> Unit = {},
    showControlBar: Boolean = true
) {
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentMediaListState = rememberSaveable(saver = MediaData.ListSaver) {
        mutableStateOf(emptyList<MediaData>())
    }
    var currentMediaList by currentMediaListState
    val scope = rememberCoroutineScope()

    // 選択解除用のシグナル (GalleryGridView用)
    var clearSelectionSignal by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }

    // カラム設定の状態を CategoryScreen で保持
    val columnOptions = listOf(10, 7, 4, 3, 1)
    var currentColumnIndex by remember { mutableIntStateOf(2) }

    // オーバースクロール（画面全体を動かす）のための状態
    val overscrollTranslationY = remember { Animatable(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    // 消費されなかったスクロール量で画面全体を移動させる
                    scope.launch {
                        val current = overscrollTranslationY.value
                        val delta = available.y * 0.4f
                        val newTranslation = if (current * delta > 0) {
                            current + delta * (1f / (1f + abs(current) / 100f))
                        } else {
                            current + delta
                        }
                        overscrollTranslationY.snapTo(newTranslation)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 指を離した時に元の位置に戻る
                overscrollTranslationY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                return super.onPostFling(consumed, available)
            }
        }
    }

    BackHandler(selectedCategoryTitle != null || selectedImageIndex != null || isSelectionModeActive) {
        if (selectedImageIndex != null) {
            selectedImageIndex = null
            onHideViewer()
        } else if (isSelectionModeActive) {
            clearSelectionSignal++
        } else {
            onBackFromCategory()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppConstants.BackgroundColor)
    ) {
        if (selectedCategoryTitle == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ヘッダー (タイトル + 切り替えボタン等)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(AppConstants.HeaderHeight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(title, color = Color.White, fontSize = AppConstants.HeaderFontSize)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        topBarActions()
                    }
                }

                // 操作バー (フィルタ、列数など)
                if (showControlBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppConstants.HeaderHeight)
                            .background(AppConstants.BackgroundColor.copy(alpha = 0.95f))
                    ) {
                        GalleryTopControlBar(
                            galleryState = galleryState,
                            showGroupingButton = true, // 元のUIに合わせる
                            isGroupingEnabled = false // カテゴリ一覧画面（フォルダ・マイリスト・カラー）では日付グループ非活性
                        )
                    }
                }

                // グリッド部分
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                        .graphicsLayer { translationY = overscrollTranslationY.value }
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            loadingContent()
                        }
                    } else if (categories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            emptyContent()
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnOptions[currentColumnIndex]),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            flingBehavior = ScrollableDefaults.flingBehavior()
                        ) {
                            items(categories) { category ->
                                CategoryCard(
                                    data = category,
                                    onClick = { onCategoryClick(category) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            GalleryGridView(
                imageList = selectedCategoryMedia,
                onImageClick = { index, list ->
                    currentMediaList = list
                    selectedImageIndex = index
                    onShowViewer()
                },
                galleryState = galleryState,
                clearSelectionSignal = clearSelectionSignal,
                onSelectionModeChanged = { isSelectionModeActive = it },
                title = selectedCategoryTitle,
                onBackClick = onBackFromCategory,
                modifier = Modifier.fillMaxSize()
            )
        }

        selectedImageIndex?.let { index ->
            if (currentMediaList.isNotEmpty()) {
                PictureViewer(
                    onClickedClose = {
                        selectedImageIndex = null
                        onHideViewer()
                    },
                    initialPage = index,
                    imageList = currentMediaList,
                    galleryState = galleryState,
                    onPageSelected = { selectedImageIndex = it },
                    onNavigateToMedia = { uri ->
                        val idx = selectedCategoryMedia.indexOfFirst { it.uri == uri }
                        if (idx != -1) {
                            currentMediaList = selectedCategoryMedia
                            selectedImageIndex = idx
                        } else {
                            scope.launch {
                                val allMedia = galleryState.repository.getAllMedia()
                                val newIdx = allMedia.indexOfFirst { it.uri == uri }
                                if (newIdx != -1) {
                                    currentMediaList = allMedia
                                    selectedImageIndex = newIdx
                                } else {
                                    currentMediaList = listOf(MediaData(uri, System.currentTimeMillis()))
                                    selectedImageIndex = 0
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    data: CategoryData,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
        ) {
            if (data.thumbnail != null) {
                if (data.thumbnail.startsWith("mock://")) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(data.indicatorColor?.copy(alpha = 0.5f) ?: Color.Gray.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context)
                                .data(data.thumbnail)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .videoFrameMillis(1000)
                                .build()
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(data.indicatorColor?.copy(alpha = 0.5f) ?: Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            if (data.indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(data.indicatorColor)
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = data.title,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "${data.count} 枚",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

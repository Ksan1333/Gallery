package com.example.gallery.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gallery.ui.GalleryState
import com.example.gallery.ui.GalleryViewMode
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryBottomNavigationBar(
    navController: NavHostController,
    galleryState: GalleryState,
    onIconClick: (String) -> Unit = {},
    onMockModeToggle: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 現在のギャラリーモードに応じたアイコンとタイトル
    val currentGalleryIcon = when(galleryState.galleryViewMode) {
        GalleryViewMode.FOLDER -> Icons.AutoMirrored.Filled.List
        GalleryViewMode.MYLIST -> Icons.Default.Favorite
        GalleryViewMode.COLOR -> Icons.Default.Palette
    }
    val currentGalleryTitle = when(galleryState.galleryViewMode) {
        GalleryViewMode.FOLDER -> "フォルダ"
        GalleryViewMode.MYLIST -> "マイリスト"
        GalleryViewMode.COLOR -> "カラー"
    }

    val items = listOf(
        NavigationItem("home", "すべて", Icons.Default.Home),
        NavigationItem("folders", currentGalleryTitle, currentGalleryIcon, enabled = !galleryState.isMockMode),
        NavigationItem("pinterest", "Pinterest", Icons.Default.PushPin),
        NavigationItem("books", "本", Icons.AutoMirrored.Filled.MenuBook)
    )

    // ジェスチャー状態
    var isDraggingGallery by remember { mutableStateOf(false) }
    var dragAmountY by remember { mutableFloatStateOf(0f) }
    
    // 縦に並べるためのしきい値
    val hoverMode = remember(dragAmountY) {
        when {
            dragAmountY < -180f -> GalleryViewMode.COLOR
            dragAmountY < -90f -> GalleryViewMode.MYLIST
            else -> GalleryViewMode.FOLDER
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // クイックスイッチャーメニュー (ドラッグ中のみ表示) - 縦配置
        if (isDraggingGallery) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-80).dp)
                    .zIndex(2f)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 順番: Color (一番上), MyList, Folder (一番下)
                        listOf(GalleryViewMode.COLOR, GalleryViewMode.MYLIST, GalleryViewMode.FOLDER).forEach { mode ->
                            val icon = when(mode) {
                                GalleryViewMode.FOLDER -> Icons.AutoMirrored.Filled.List
                                GalleryViewMode.MYLIST -> Icons.Default.Favorite
                                GalleryViewMode.COLOR -> Icons.Default.Palette
                            }
                            val isHovered = hoverMode == mode
                            val color = if (isHovered) Color.Cyan else Color.White.copy(alpha = 0.5f)
                            val scale = if (isHovered) 1.3f else 1.0f

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size((28 * scale).dp)
                                )
                                Text(
                                    text = when(mode) {
                                        GalleryViewMode.FOLDER -> "フォルダ"
                                        GalleryViewMode.MYLIST -> "マイリスト"
                                        GalleryViewMode.COLOR -> "カラー"
                                    },
                                    color = color,
                                    fontSize = (9 * scale).sp,
                                    fontWeight = if (isHovered) androidx.compose.ui.text.font.FontWeight.Bold else null
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp)),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val isActuallyEnabled = item.enabled
                    
                    val isFoldersItem = item.route == "folders"

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .then(
                                if (isFoldersItem && isActuallyEnabled) {
                                    Modifier.pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { isDraggingGallery = true; dragAmountY = 0f },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                dragAmountY += drag.y
                                            },
                                            onDragEnd = {
                                                // モードを更新
                                                galleryState.galleryViewMode = hoverMode
                                                isDraggingGallery = false
                                                dragAmountY = 0f
                                                
                                                // 画面遷移（すでに folders ならリセット処理）
                                                if (currentRoute != "folders") {
                                                    navController.navigate("folders") {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                                onIconClick("folders")
                                            },
                                            onDragCancel = {
                                                isDraggingGallery = false
                                                dragAmountY = 0f
                                            }
                                        )
                                    }.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple()
                                    ) {
                                        onIconClick(item.route)
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                } else {
                                    Modifier.combinedClickable(
                                        enabled = isActuallyEnabled,
                                        onClick = {
                                            onIconClick(item.route)
                                            if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        onLongClick = { },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple()
                                    )
                                }
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (!isActuallyEnabled) Color.DarkGray else if (isSelected) Color.White else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            color = if (!isActuallyEnabled) Color.DarkGray else if (isSelected) Color.White else Color.Gray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null
                        )
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 4.dp),
                    color = Color.Gray.copy(alpha = 0.3f)
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .combinedClickable(
                            onClick = { 
                                galleryState.toggleMockMode()
                                onMockModeToggle()
                            },
                            onLongClick = { },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple()
                        )
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = if (galleryState.isMockMode) Icons.Default.BugReport else Icons.Default.SdStorage,
                        contentDescription = "MOCK",
                        tint = if (galleryState.isMockMode) Color.Yellow else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MOCK",
                        color = if (galleryState.isMockMode) Color.Yellow else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = if (galleryState.isMockMode) androidx.compose.ui.text.font.FontWeight.Bold else null
                    )
                }
            }
        }
    }
}

data class NavigationItem(val route: String, val title: String, val icon: ImageVector, val enabled: Boolean = true)

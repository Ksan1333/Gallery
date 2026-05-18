package com.example.gallery.ui.component

import android.util.Log
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
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
        NavigationItem("folders", currentGalleryTitle, currentGalleryIcon, enabled = true),
        NavigationItem("pinterest", "Pinterest", Icons.Default.PushPin),
        NavigationItem("books", "本", Icons.AutoMirrored.Filled.MenuBook)
    )

    // ジェスチャー状態
    var isDraggingGallery by remember { mutableStateOf(false) }
    var dragAmountY by remember { mutableFloatStateOf(0f) }
    
    // 縦に並べるためのしきい値
    val hoverMode by remember(dragAmountY) {
        derivedStateOf {
            when {
                dragAmountY < -180f -> GalleryViewMode.COLOR
                dragAmountY < -90f -> GalleryViewMode.MYLIST
                else -> GalleryViewMode.FOLDER
            }
        }
    }
    
    // pointerInput 内で最新の値を参照するための state
    val currentHoverMode by rememberUpdatedState(hoverMode)
    val currentDragAmountY by rememberUpdatedState(dragAmountY)

    // 全体を覆うBoxでZ-Indexを管理
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
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

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // アイコン本体のColumn
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isFoldersItem && isActuallyEnabled) {
                                        Modifier
                                            .pointerInput(Unit) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { 
                                                        Log.d("Switcher", "onDragStart")
                                                        isDraggingGallery = true
                                                        dragAmountY = 0f 
                                                    },
                                                    onDrag = { change, drag ->
                                                        change.consume()
                                                        dragAmountY += drag.y
                                                        // リアルタイムの判定をログ出力（描画用の hoverMode は recompose で更新される）
                                                        Log.d("Switcher", "onDrag: dragAmountY=$dragAmountY")
                                                    },
                                                    onDragEnd = {
                                                        val finalMode = currentHoverMode
                                                        Log.d("Switcher", "onDragEnd: finalMode=$finalMode, dragAmountY=$dragAmountY")
                                                        
                                                        galleryState.galleryViewMode = finalMode
                                                        isDraggingGallery = false
                                                        dragAmountY = 0f
                                                        
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
                                                        Log.d("Switcher", "onDragCancel")
                                                        isDraggingGallery = false
                                                        dragAmountY = 0f
                                                    }
                                                )
                                            }
                                            .clickable(
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

                        // フォルダアイコンの右上に上矢印を表示 (ドラッグ中でない時)
                        if (isFoldersItem && !isDraggingGallery) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropUp,
                                contentDescription = "メニュー展開",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 8.dp)
                                    .size(16.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        isDraggingGallery = true
                                        dragAmountY = 0f
                                    }
                            )
                        }
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

    // クイックスイッチャーメニュー (Popup を使ってレイアウトから完全に切り離す)
    if (isDraggingGallery) {
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, -64), // ナビゲーションバーの高さ分だけ上へ
            onDismissRequest = { isDraggingGallery = false },
            properties = PopupProperties(
                focusable = true, // タップイベントを受け取るために必要
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
                clippingEnabled = false
            )
        ) {
            // itemsの幅（1/4ずつ）に合わせて「フォルダ」アイコンの位置に配置
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(0.25f)) // "すべて"
                Box(
                    modifier = Modifier.weight(0.25f), // "フォルダ"
                    contentAlignment = Alignment.BottomCenter
                ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(28.dp),
                            shadowElevation = 12.dp,
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(bottom = 8.dp) // バーとの間に少し隙間
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
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
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = ripple(bounded = false, radius = 30.dp)
                                            ) {
                                                // アイコンタップで即時遷移
                                                galleryState.galleryViewMode = mode
                                                isDraggingGallery = false
                                                if (currentRoute != "folders") {
                                                    navController.navigate("folders") {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                                onIconClick("folders")
                                            }
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
                Spacer(modifier = Modifier.weight(0.5f)) // Pinterest, 本
            }
        }
    }
    }
}

data class NavigationItem(val route: String, val title: String, val icon: ImageVector, val enabled: Boolean = true)

package com.example.gallery.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.res.stringResource
import com.example.gallery.R
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.GalleryViewMode
import com.example.gallery.ui.theme.GalleryThemeTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryBottomNavigationBar(
    navController: NavHostController,
    galleryState: GalleryState,
    onIconClick: (String) -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes

    val items = listOf(
        BottomNavItem(AppRoutes.HOME, stringResource(R.string.label_all_media), Icons.Default.Home),
        BottomNavItem(AppRoutes.FOLDERS, stringResource(R.string.nav_folders), Icons.AutoMirrored.Filled.List),
        BottomNavItem(AppRoutes.VIDEOS, stringResource(R.string.nav_videos), Icons.Default.PlayCircle),
        BottomNavItem(AppRoutes.BOOKS, stringResource(R.string.nav_books), Icons.AutoMirrored.Filled.MenuBook),
        BottomNavItem(AppRoutes.TRASH, stringResource(R.string.nav_trash), Icons.Default.Delete)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 8.dp)
    ) {
        Surface(
            color = colors.surface.copy(alpha = 0.96f),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp)),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    val canClick = !isSelected || item.route == AppRoutes.VIDEOS
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    enabled = canClick,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple()
                                ) {
                                    // 画面モードの更新
                                    when (item.route) {
                                        AppRoutes.HOME -> {
                                            galleryState.clearHomeSearch()
                                        }
                                        AppRoutes.FOLDERS -> galleryState.galleryViewMode = GalleryViewMode.FOLDER
                                        AppRoutes.VIDEOS -> galleryState.galleryViewMode = GalleryViewMode.VIDEO
                                        AppRoutes.TRASH -> galleryState.galleryViewMode = GalleryViewMode.TRASH
                                    }

                                    onIconClick(item.route)
                                    // HOMEボタンは常に反応するように (ただし重複ナビゲーションは避ける)
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = false
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    } else if (item.route == AppRoutes.HOME) {
                                        // すでにHOMEにいる場合は、スタックの先頭まで戻るなどの処理が必要であればここ
                                    }
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isSelected) colors.accent else colors.mutedText,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                color = if (isSelected) colors.accent else colors.mutedText,
                                fontSize = textSizes.bottomNav,
                                maxLines = 1,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null
                            )
                        }
                    }
                }
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val enabled: Boolean = true
)

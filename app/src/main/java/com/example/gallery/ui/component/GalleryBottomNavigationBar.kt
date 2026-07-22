package com.example.gallery.ui.component

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gallery.R
import com.example.gallery.ui.AppConstants
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
    val context = LocalContext.current
    val labelNone = stringResource(R.string.label_action_none)
    val prefs = remember { context.getSharedPreferences("global_settings", Context.MODE_PRIVATE) }

    val barSlots = listOf(
        AppConstants.SLOT_BOTTOM_LEFT,
        AppConstants.SLOT_BOTTOM_CENTER_LEFT,
        AppConstants.SLOT_BOTTOM_CENTER,
        AppConstants.SLOT_BOTTOM_CENTER_RIGHT,
        AppConstants.SLOT_BOTTOM_RIGHT
    )

    val allNavFunctions = listOf(
        AppRoutes.HOME, AppRoutes.FOLDERS, AppRoutes.VIDEOS, AppRoutes.BOOKS, AppRoutes.TRASH,
        AppRoutes.REFERENCES, AppRoutes.VIDEO_DOWNLOADER, AppRoutes.FAVORITE_ARTISTS,
        AppRoutes.FAVORITE_SITES, AppRoutes.SETTINGS, AppRoutes.ABOUT,
        AppConstants.ACTION_OVERFLOW
    )

    val barAssignments = remember(prefs, labelNone) {
        val defaultRoutes = listOf(AppRoutes.HOME, AppRoutes.FOLDERS, AppRoutes.VIDEOS, AppRoutes.BOOKS, AppRoutes.TRASH)
        barSlots.mapIndexedNotNull { index, slot ->
            val saved = prefs.getString("nav_bar.$slot", null)
            val fallback = defaultRoutes.getOrNull(index)
            when {
                saved == null -> fallback
                saved == labelNone -> null
                saved == AppRoutes.BOOK_BOOKMARKS -> fallback
                else -> saved
            }
        }
    }

    val menuAssignments = remember(barAssignments) {
        allNavFunctions.filterNot { it in barAssignments || it == AppConstants.ACTION_OVERFLOW }
    }

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
                barAssignments.forEach { route ->
                    if (route == AppConstants.ACTION_OVERFLOW) {
                        var expanded by remember { mutableStateOf(false) }
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
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple()
                                    ) { expanded = true }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.label_3dot_menu),
                                    tint = colors.mutedText,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.label_3dot_menu),
                                    color = colors.mutedText,
                                    fontSize = textSizes.bottomNav,
                                    maxLines = 1
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(colors.surface)
                            ) {
                                menuAssignments.forEach { menuRoute ->
                                    val action = resolveViewerAction(menuRoute)
                                    if (action != null) {
                                        DropdownMenuItem(
                                            text = { Text(action.label, color = if (currentRoute == menuRoute) colors.accent else colors.primaryText) },
                                            leadingIcon = { Icon(action.icon, null, tint = if (currentRoute == menuRoute) colors.accent else colors.primaryText) },
                                            onClick = {
                                                expanded = false
                                                navigate(menuRoute, navController, galleryState, onIconClick)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val action = resolveViewerAction(route)
                        if (action != null) {
                            val isSelected = currentRoute == route
                            val canClick = !isSelected || route == AppRoutes.VIDEOS

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
                                            navigate(route, navController, galleryState, onIconClick)
                                        }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.label,
                                        tint = if (isSelected) colors.accent else colors.mutedText,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = action.label,
                                        color = if (isSelected) colors.accent else colors.mutedText,
                                        fontSize = textSizes.bottomNav,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun navigate(
    route: String,
    navController: NavHostController,
    galleryState: GalleryState,
    onIconClick: (String) -> Unit
) {
    // 画面モードの更新
    when (route) {
        AppRoutes.HOME -> galleryState.clearHomeSearch()
        AppRoutes.FOLDERS -> galleryState.galleryViewMode = GalleryViewMode.FOLDER
        AppRoutes.VIDEOS -> galleryState.galleryViewMode = GalleryViewMode.VIDEO
        AppRoutes.TRASH -> galleryState.galleryViewMode = GalleryViewMode.TRASH
    }

    onIconClick(route)
    if (navController.currentBackStackEntry?.destination?.route != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }
}

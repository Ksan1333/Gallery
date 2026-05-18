package com.example.gallery.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gallery.ui.GalleryState

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

    val items = listOf(
        NavigationItem("folders", "フォルダ", Icons.AutoMirrored.Filled.List, enabled = !galleryState.isMockMode)
    )

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
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .combinedClickable(
                                enabled = isActuallyEnabled,
                                onClick = {
                                    onIconClick(item.route)
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                onLongClick = { },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple()
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

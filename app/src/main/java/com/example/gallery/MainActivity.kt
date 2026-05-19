package com.example.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gallery.ui.screen.ColorListScreen
import com.example.gallery.ui.screen.AnalysisProgressScreen
import com.example.gallery.ui.screen.FolderGalleryScreen
import com.example.gallery.ui.screen.HomeGalleryScreen
import com.example.gallery.ui.screen.MyListScreen
import com.example.gallery.ui.screen.PinterestScreen
import com.example.gallery.ui.screen.BookScreen
import com.example.gallery.ui.rememberGalleryState
import com.example.gallery.ui.component.GalleryBottomNavigationBar
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SdStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val startDestination = "home"
    
    val navController = rememberNavController()
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }
    
    val galleryState = rememberGalleryState(context)

    // フォルダ選択用の状態
    var isSelectingFolderForMove by rememberSaveable { mutableStateOf(false) }
    var selectedFolderForMove by rememberSaveable { mutableStateOf("") }
    var urisToMove by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // DBクリーンアップ: タグテーブルに含まれる年齢制限ラベルを削除
    LaunchedEffect(Unit) {
        galleryState.repository.mediaDao.cleanupAgeRatingTags()
    }

    // システムバーの設定
    val window = (context as? android.app.Activity)?.window
    fun setupSystemBars(visible: Boolean) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // ナビゲーションバーは常に表示、ステータスバーのみ切り替え
            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            
            if (visible) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    LaunchedEffect(isBottomBarVisible) {
        setupSystemBars(isBottomBarVisible)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable("home") {
                isBottomBarVisible = true
                HomeGalleryScreen(
                    onShowViewer = { isBottomBarVisible = false },
                    onHideViewer = { isBottomBarVisible = true },
                    galleryState = galleryState,
                    onBulkEdit = { uris ->
                        urisToMove = uris
                        isSelectingFolderForMove = true
                    }
                )
            }
            composable("folders") {
                isBottomBarVisible = true
                FolderGalleryScreen(
                    onShowViewer = { isBottomBarVisible = false },
                    onHideViewer = { isBottomBarVisible = true },
                    galleryState = galleryState,
                    onBackToFolders = { 
                        navController.navigate("folders") { 
                            popUpTo("folders") { inclusive = true } 
                        } 
                    },
                    onStartAnalysis = { navController.navigate("analysis") },
                    onBulkEdit = { uris ->
                        urisToMove = uris
                        isSelectingFolderForMove = true
                    }
                )
            }
            composable("folders_select") {
                isBottomBarVisible = false
                FolderGalleryScreen(
                    onShowViewer = {},
                    onHideViewer = {},
                    galleryState = galleryState,
                    isSelectionMode = true,
                    onFolderSelected = { folder ->
                        selectedFolderForMove = folder
                        navController.popBackStack()
                    },
                    onBackToFolders = { navController.popBackStack() }
                )
            }
            composable("analysis") {
                isBottomBarVisible = false
                AnalysisProgressScreen(
                    galleryState = galleryState,
                    onComplete = {
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }
            composable("pinterest") {
                isBottomBarVisible = true
                PinterestScreen()
            }
            composable("books") {
                isBottomBarVisible = true
                BookScreen()
            }
        }

        if (isSelectingFolderForMove) {
            UnifiedMediaEditDialog(
                uris = urisToMove,
                repository = galleryState.repository,
                onDismiss = { isSelectingFolderForMove = false; urisToMove = emptyList(); selectedFolderForMove = "" },
                onSelectFolder = { navController.navigate("folders_select") },
                initialFolderName = selectedFolderForMove
            )
        }

        if (isBottomBarVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                
                GalleryBottomNavigationBar(
                    navController = navController,
                    galleryState = galleryState,
                    onIconClick = { route ->
                        if (currentRoute == route) {
                            // 現在表示中の画面に対して「トップに戻る（リセット）」を指示
                            navController.navigate(route) {
                                // そのルートまで戻り、かつそのルート自体も再生成することで状態を初期化
                                popUpTo(route) { inclusive = true }
                            }
                        }
                    },
                    onMockModeToggle = {
                        val currentRouteInner = navController.currentBackStackEntry?.destination?.route
                        if (currentRouteInner == "folders" || currentRouteInner == "home") {
                            navController.navigate(currentRouteInner) {
                                popUpTo(currentRouteInner) { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}


data class NavigationItem(val route: String, val title: String, val icon: ImageVector, val enabled: Boolean = true)

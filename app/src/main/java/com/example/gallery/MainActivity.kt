package com.example.gallery

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.gallery.ui.screen.VideoDownloadScreen
import com.example.gallery.ui.screen.AnalysisProgressScreen
import com.example.gallery.ui.screen.FolderGalleryScreen
import com.example.gallery.ui.screen.HomeGalleryScreen
import com.example.gallery.ui.screen.MyListScreen
import com.example.gallery.ui.screen.BookScreen
import com.example.gallery.ui.screen.FolderPickerScreen
import com.example.gallery.ui.screen.TrashScreen
import com.example.gallery.ui.rememberGalleryState
import com.example.gallery.ui.component.GalleryBottomNavigationBar
import com.example.gallery.ui.component.GlobalProgressOverlay
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.ui.GalleryViewMode
import com.example.gallery.service.ThumbnailGenerationService
import com.example.gallery.service.TagTranslationService
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val galleryState = (context.applicationContext as GalleryApplication).galleryState
    val scope = rememberCoroutineScope()
    val startDestination = "home"

    val navController = rememberNavController()
    galleryState.navController = navController
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    // 通知権限の要求 (Android 13+)
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        // 権限結果の処理
    }

    // ストレージ権限の要求
    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            galleryState.refresh()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // ストレージ権限のチェックと要求
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val allGranted = permissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            storagePermissionLauncher.launch(permissions)
        }
    }

    // アプリが前面に戻ったときにファイルの状態を同期
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // cleanupDeletedFiles は repository.getAllMedia 内で一括処理されるため、
                // ここでは refreshTrigger を引くだけにする
                galleryState.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // サイドバーが開いているときに戻るボタンで閉じる
    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val refreshTrigger = galleryState.refreshTrigger
    LaunchedEffect(refreshTrigger) {
        // 起動時(0)は1秒待機し、権限許可によるリフレッシュ(>0)時は即座に開始
        if (refreshTrigger == 0) delay(1000)
        ThumbnailGenerationService.startGenerating(
            context,
            galleryState.repository,
            force = refreshTrigger > 0
        )
    }

    LaunchedEffect(Unit) {
        galleryState.repository.mediaDao.cleanupAgeRatingTags()
    }

    // システムバーの設定
    val window = (context as? android.app.Activity)?.window
    fun setupSystemBars(isViewerVisible: Boolean, currentRoute: String?) {
        if (window != null) {
            val insetsController =
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            val isFullScreenRoute = currentRoute == "mass_edit" || 
                                   currentRoute == "folders_select" || 
                                   currentRoute?.startsWith("analysis") == true

            if (isFullScreenRoute) {
                // 一括編集、フォルダ選択、分析画面ではすべて非表示
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                return
            }

            if (!isViewerVisible) {
                // ビュワー以外の通常画面では両方表示
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            } else {
                // ビュワー表示中はすべて非表示にする
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    val navBackStackEntryForSystemBars by navController.currentBackStackEntryAsState()
    val currentRouteForSystemBars = navBackStackEntryForSystemBars?.destination?.route

    LaunchedEffect(isBottomBarVisible, currentRouteForSystemBars) {
        setupSystemBars(!isBottomBarVisible, currentRouteForSystemBars)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF121212),
                drawerContentColor = Color.White,
                modifier = Modifier.width(260.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "ギャラリーメニュー",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                NavigationDrawerItem(
                    label = { Text("ホーム") },
                    selected = navController.currentBackStackEntryAsState().value?.destination?.route == "home",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )

                Text(
                    "フォルダ",
                    modifier = Modifier.padding(16.dp, 8.dp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                NavigationDrawerItem(
                    label = { Text("　フォルダ別") },
                    selected = galleryState.galleryViewMode == GalleryViewMode.FOLDER && navController.currentBackStackEntryAsState().value?.destination?.route == "folders",
                    onClick = {
                        scope.launch { drawerState.close() }
                        galleryState.galleryViewMode = GalleryViewMode.FOLDER
                        navController.navigate("folders") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )
                NavigationDrawerItem(
                    label = { Text("　タグ別") },
                    selected = galleryState.galleryViewMode == GalleryViewMode.MYLIST && navController.currentBackStackEntryAsState().value?.destination?.route == "mylist",
                    onClick = {
                        scope.launch { drawerState.close() }
                        galleryState.galleryViewMode = GalleryViewMode.MYLIST
                        navController.navigate("mylist") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Favorite, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.Gray.copy(alpha = 0.3f)
                )

                NavigationDrawerItem(
                    label = { Text("本") },
                    selected = navController.currentBackStackEntryAsState().value?.destination?.route == "books",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("books")
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )
                NavigationDrawerItem(
                    label = { Text("ゴミ箱") },
                    selected = navController.currentBackStackEntryAsState().value?.destination?.route == "trash",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("trash") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Delete, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )

                NavigationDrawerItem(
                    label = { Text("動画DL (X/Twitter)") },
                    selected = navController.currentBackStackEntryAsState().value?.destination?.route == "video_downloader",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("video_downloader")
                    },
                    icon = { Icon(Icons.Default.Download, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )

                Spacer(Modifier.weight(1f))

                NavigationDrawerItem(
                    label = { Text(if (galleryState.isMockMode) "MOCKモードを終了" else "MOCKモードを起動") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        galleryState.toggleMockMode()
                        val currentRouteInner =
                            navController.currentBackStackEntry?.destination?.route
                        if (currentRouteInner == "folders" || currentRouteInner == "home") {
                            navController.navigate(currentRouteInner) {
                                popUpTo(currentRouteInner) { inclusive = true }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            if (galleryState.isMockMode) Icons.Default.SdStorage else Icons.Default.BugReport,
                            null
                        )
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = if (galleryState.isMockMode) Color.Yellow else Color.White,
                        unselectedIconColor = if (galleryState.isMockMode) Color.Yellow else Color.White
                    )
                )
                Spacer(Modifier.height(12.dp))
            }
        },
        gesturesEnabled = isBottomBarVisible && !galleryState.isSelectionMode && !galleryState.isZooming
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // エッジスワイプを補助するためのエリア（左端 25dp）
            // ギャラリー内のズーム操作との衝突を避けるため、ボトムバーが表示されている（＝ビュワー等でない）時のみ有効
            val navBackStackEntryForDrawer by navController.currentBackStackEntryAsState()
            val currentRouteForDrawer = navBackStackEntryForDrawer?.destination?.route
            val isGalleryGridVisible =
                currentRouteForDrawer == "home" || currentRouteForDrawer == "folders" || currentRouteForDrawer == "mylist"

            if (!drawerState.isOpen && isBottomBarVisible && isGalleryGridVisible && !galleryState.isSelectionMode && !galleryState.isZooming) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(25.dp)
                        .align(Alignment.CenterStart)
                        .zIndex(100f)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDrag = 0f
                                    var isOpening = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.first()

                                        if (change.pressed) {
                                            val dragAmount =
                                                change.position.x - change.previousPosition.x
                                            totalDrag += dragAmount

                                            // 右スワイプを検知
                                            if (totalDrag > 40f && !isOpening) {
                                                isOpening = true
                                                scope.launch { drawerState.open() }
                                            }

                                            if (isOpening) {
                                                change.consume()
                                            }
                                        } else {
                                            break
                                        }
                                    }
                                }
                            }
                        }
                )
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable("home") {
                    isBottomBarVisible = true
                    // ホームに戻ったときにハイライトをリセット
                    LaunchedEffect(Unit) {
                        galleryState.lastViewedUri = null
                    }
                    HomeGalleryScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateToTag = { tag ->
                            galleryState.galleryViewMode = com.example.gallery.ui.GalleryViewMode.MYLIST
                            navController.navigate("mylist/$tag") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onBulkEdit = { uris ->
                            galleryState.urisToMove = uris
                            navController.navigate("mass_edit")
                        }
                    )
                }
                composable("folders") {
                    isBottomBarVisible = true
                    FolderGalleryScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBackToFolders = { navController.popBackStack() },
                        onStartAnalysis = { navController.navigate("analysis/AI_TAGGING") },
                        onFolderSelected = { target ->
                            if (target.startsWith("TAG_NAVIGATION:")) {
                                val tag = target.removePrefix("TAG_NAVIGATION:")
                                galleryState.galleryViewMode = com.example.gallery.ui.GalleryViewMode.MYLIST
                                navController.navigate("mylist/$tag") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onBulkEdit = { uris ->
                            galleryState.urisToMove = uris
                            navController.navigate("mass_edit")
                        }
                    )
                }
                composable("mylist") {
                    isBottomBarVisible = true
                    MyListScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        onStartAnalysis = { type ->
                            navController.navigate("analysis/$type")
                        },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBackToMyList = { navController.popBackStack() }
                    )
                }
                composable("mylist/{tagName}") { backStackEntry ->
                    val tagName = backStackEntry.arguments?.getString("tagName")
                    isBottomBarVisible = true
                    MyListScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        onStartAnalysis = { type ->
                            navController.navigate("analysis/$type")
                        },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBackToMyList = { navController.popBackStack() },
                        initialCategoryType = "Tag",
                        initialTagName = tagName
                    )
                }
                composable("analysis/{type}") { backStackEntry ->
                    val analysisType = backStackEntry.arguments?.getString("type") ?: "AI_TAGGING"
                    val periodDays = backStackEntry.savedStateHandle.get<Int>("periodDays") ?: -1
                    isBottomBarVisible = false
                    AnalysisProgressScreen(
                        galleryState = galleryState,
                        analysisType = analysisType,
                        periodDays = periodDays,
                        onComplete = {
                            // 完了時もマイリスト画面へ
                            navController.navigate("mylist") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onCancel = {
                            // 中断時はマイリスト画面へ
                            navController.navigate("mylist") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("mass_edit") {
                    isBottomBarVisible = false
                    DisposableEffect(Unit) {
                        isBottomBarVisible = false
                        onDispose { }
                    }
                    // Pickerから戻ってきたフォルダ名を取得
                    val selectedFolder = it.savedStateHandle.get<String>("selected_folder")
                    if (selectedFolder != null) {
                        galleryState.selectedFolderForMove = selectedFolder
                        it.savedStateHandle.remove<String>("selected_folder")
                    }

                    UnifiedMediaEditDialog(
                        uris = galleryState.urisToMove,
                        repository = galleryState.repository,
                        onDismiss = { navController.popBackStack() },
                        onSelectFolder = { navController.navigate("folders_select") },
                        initialFolderName = galleryState.selectedFolderForMove
                    )
                }
                composable("folders_select") {
                    isBottomBarVisible = false
                    DisposableEffect(Unit) {
                        isBottomBarVisible = false
                        onDispose { }
                    }
                    FolderPickerScreen(
                        galleryState = galleryState,
                        onFolderSelected = { folder ->
                            // 戻り先に値を渡す
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                "selected_folder",
                                folder
                            )
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("books") {
                    var isViewerOpen by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(isViewerOpen) {
                        isBottomBarVisible = !isViewerOpen
                    }

                    BookScreen(onViewerStateChanged = { isViewerOpen = it })
                }
                composable("trash") {
                    isBottomBarVisible = true
                    TrashScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                }
                composable("video_downloader") {
                    isBottomBarVisible = true
                    VideoDownloadScreen(
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                }
            }

            // グローバルプログレス表示
            GlobalProgressOverlay()

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val isHideRoute = currentRoute == null ||
                currentRoute == "mass_edit" || 
                currentRoute == "folders_select" || 
                currentRoute.startsWith("analysis")

            if (isBottomBarVisible && !isHideRoute) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
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
                        }
                    )
                }
            }
        }
    }
}

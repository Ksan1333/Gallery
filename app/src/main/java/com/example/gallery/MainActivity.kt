package com.example.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gallery.ui.screen.*
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.GalleryViewMode
import com.example.gallery.ui.component.GalleryBottomNavigationBar
import com.example.gallery.ui.component.GlobalProgressOverlay
import com.example.gallery.ui.component.TutorialDialog
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.ThumbnailGenerationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BOOKMARKS_PREFS = "book_bookmarks"

class MainActivity : ComponentActivity() {
    private var sharedXUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedXUrl = intent.extractXStatusUrl()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            AppNavigation(
                sharedXUrl = sharedXUrl,
                onSharedXUrlConsumed = { sharedXUrl = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedXUrl = intent.extractXStatusUrl()
    }
}

@Composable
fun AppNavigation(
    sharedXUrl: String? = null,
    onSharedXUrlConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val galleryState = (context.applicationContext as GalleryApplication).galleryState
    val scope = rememberCoroutineScope()
    val startDestination = "home"

    val navController = rememberNavController()
    galleryState.navController = navController
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }

    var showTutorial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tutorial_shown", false)) {
            showTutorial = true
            prefs.edit().putBoolean("tutorial_shown", true).apply()
        }
    }

    val bookmarksPrefs = remember { context.getSharedPreferences(BOOKMARKS_PREFS, Context.MODE_PRIVATE) }
    var bookmarksCount by remember { mutableIntStateOf(bookmarksPrefs.all.size) }

    var pendingBookmarkBookId by remember { mutableStateOf<String?>(null) }
    var pendingBookmarkPage by remember { mutableIntStateOf(-1) }

    LaunchedEffect(sharedXUrl) {
        if (sharedXUrl != null) {
            navController.navigate("video_downloader") {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
    }

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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                galleryState.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val refreshTrigger = galleryState.refreshTrigger
    LaunchedEffect(refreshTrigger) {
        ThumbnailGenerationService.startGenerating(
            context,
            galleryState.repository,
            force = false
        )
    }

    LaunchedEffect(Unit) {
        galleryState.repository.mediaDao.cleanupAgeRatingTags()
    }

    val window = (context as? android.app.Activity)?.window
    fun setupSystemBars(isViewerVisible: Boolean, currentRoute: String?) {
        if (window != null) {
            val insetsController =
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            val isFullScreenRoute = currentRoute == "folders_select" ||
                                   currentRoute?.startsWith("analysis") == true

            val isAlwaysShowNavBarRoute = currentRoute == "about" || currentRoute == "mass_edit" || currentRoute == "book_bookmarks" ||
                currentRoute == "references" || currentRoute?.startsWith("reference_detail") == true || currentRoute?.startsWith("reference_search") == true

            if (isAlwaysShowNavBarRoute) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                return
            }

            if (isFullScreenRoute) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                return
            }

            if (!isViewerVisible) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            } else {
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

    val navBackStackEntryForDrawer by navController.currentBackStackEntryAsState()
    val currentRouteForDrawer = navBackStackEntryForDrawer?.destination?.route
    val isRefOrBookmarkRoute = currentRouteForDrawer == "book_bookmarks" || currentRouteForDrawer == "references"
    val isReferenceInteriorRoute = currentRouteForDrawer?.startsWith("reference_detail") == true ||
        currentRouteForDrawer?.startsWith("reference_search") == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF121212),
                drawerContentColor = Color.White,
                modifier = Modifier.width(260.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(12.dp))
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp).size(28.dp)
                    )
                    Text(
                        "ギャラリーメニュー",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                    Text(
                        "基本機能",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

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
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("フォルダ") },
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
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text("タグ") },
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
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("本") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "books",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("books")
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                        modifier = Modifier.height(44.dp),
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
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    )

                    Text(
                        "便利機能",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    NavigationDrawerItem(
                        label = { Text("動画DL") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "video_downloader",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("video_downloader")
                        },
                        icon = { Icon(Icons.Default.Download, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("お気に入りクリエイター") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "favorite_artists",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("favorite_artists")
                        },
                        icon = { Icon(Icons.Default.Star, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("お気に入りサイト") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "favorite_sites",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("favorite_sites")
                        },
                        icon = { Icon(Icons.Default.Language, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("お絵描き資料") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "references",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("references")
                        },
                        icon = { Icon(Icons.Default.Brush, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("本のしおり ($bookmarksCount)") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "book_bookmarks",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("book_bookmarks")
                        },
                        icon = { Icon(if (bookmarksCount > 0) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    )

                    Text(
                        "計測",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    NavigationDrawerItem(
                        label = { Text(if (galleryState.isMeasureModeActive) "計測を停止する" else "計測を開始する（開発中）") },
                        selected = galleryState.isMeasureModeActive,
                        onClick = {
                            galleryState.isMeasureModeActive = !galleryState.isMeasureModeActive
                        },
                        icon = { 
                            Icon(
                                if (galleryState.isMeasureModeActive) Icons.Default.Pause else Icons.Default.PlayArrow, 
                                null,
                                tint = if (galleryState.isMeasureModeActive) Color.Red else Color.Green
                            ) 
                        },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White,
                            selectedContainerColor = Color.DarkGray,
                            selectedTextColor = Color.Red,
                            selectedIconColor = Color.Red
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("おすすめ（開発中）") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "recommendations",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("recommendations")
                        },
                        icon = { Icon(Icons.Default.Star, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.Gray.copy(alpha = 0.2f)
                    )

                    Text(
                        "情報",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    NavigationDrawerItem(
                        label = { Text("使い方ガイド") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showTutorial = true
                        },
                        icon = { Icon(Icons.Default.Info, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    NavigationDrawerItem(
                        label = { Text("このアプリについて") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "about",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("about")
                        },
                        icon = { Icon(Icons.Default.Info, null) },
                        modifier = Modifier.height(44.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        )
                    )

                    Text(
                        "v0.3.0",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        gesturesEnabled = (isBottomBarVisible || isRefOrBookmarkRoute) && !isReferenceInteriorRoute && !galleryState.isSelectionMode && !galleryState.isZooming
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val navBackStackEntryForDrawer by navController.currentBackStackEntryAsState()
            val currentRouteForDrawer = navBackStackEntryForDrawer?.destination?.route
            val isGalleryGridVisible =
                currentRouteForDrawer == "home" || currentRouteForDrawer == "folders" || 
                currentRouteForDrawer == "mylist" || currentRouteForDrawer == "references" || 
                currentRouteForDrawer == "book_bookmarks" || currentRouteForDrawer == "books"

            if (!drawerState.isOpen && (isBottomBarVisible || isRefOrBookmarkRoute) && !isReferenceInteriorRoute && isGalleryGridVisible && !galleryState.isSelectionMode && !galleryState.isZooming) {
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
                    LaunchedEffect(Unit) {
                        galleryState.lastViewedUri = null
                    }
                    HomeGalleryScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateToTag = { tag ->
                            galleryState.galleryViewMode = GalleryViewMode.MYLIST
                            navController.navigate("mylist/$tag") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onBulkEdit = { uris: List<String> ->
                            galleryState.urisToMove = uris
                            navController.navigate("mass_edit")
                        },
                        onBulkMove = { uris: List<String> ->
                            galleryState.urisToMove = uris
                            navController.navigate("bulk_move_selection")
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
                                galleryState.galleryViewMode = GalleryViewMode.MYLIST
                                navController.navigate("mylist/$tag") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onBulkEdit = { uris: List<String> ->
                            galleryState.urisToMove = uris
                            navController.navigate("mass_edit")
                        },
                        onBulkMove = { uris: List<String> ->
                            galleryState.urisToMove = uris
                            navController.navigate("bulk_move_selection")
                        }
                    )
                }
                composable("mylist") {
                    isBottomBarVisible = true
                    MyListScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
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
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBackToMyList = { navController.popBackStack() },
                        initialCategoryType = "Tag",
                        initialTagName = tagName
                    )
                }
                composable(
                    "analysis/{type}/{periodDays}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("periodDays") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val analysisType = backStackEntry.arguments?.getString("type") ?: "AI_TAGGING"
                    val periodDays = backStackEntry.arguments?.getInt("periodDays") ?: -1
                    isBottomBarVisible = false
                    AnalysisProgressScreen(
                        analysisType = analysisType,
                        periodDays = periodDays,
                        onComplete = {
                            navController.navigate("mylist") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onCancel = {
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

                    UnifiedMediaEditDialog(
                        uris = galleryState.urisToMove,
                        repository = galleryState.repository,
                        onDismiss = { navController.popBackStack() }
                    )
                }
                composable("bulk_move_selection") {
                    isBottomBarVisible = false
                    FolderPickerScreen(
                        galleryState = galleryState,
                        onFolderSelected = { folder ->
                            scope.launch {
                                GlobalOperationService.startOperation("アイテムを移動中...")
                                galleryState.repository.moveMediaToFolder(galleryState.urisToMove, folder)
                                GlobalOperationService.finishOperation()
                                navController.popBackStack()
                            }
                        },
                        onBack = { navController.popBackStack() }
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

                    BookScreen(
                        onViewerStateChanged = { isViewerOpen = it },
                        onBookmarksChanged = { bookmarksCount = bookmarksPrefs.all.size },
                        onNavigateToBookmarks = { navController.navigate("book_bookmarks") },
                        initialJumpBookId = pendingBookmarkBookId,
                        initialJumpPage = pendingBookmarkPage,
                        onJumpHandled = { 
                            pendingBookmarkBookId = null
                            pendingBookmarkPage = -1
                        }
                    )
                }
                composable("book_bookmarks") {
                    isBottomBarVisible = false
                    BookBookmarksScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBookClick = { id ->
                            val data = bookmarksPrefs.getString(id, null)
                            if (data != null) {
                                runCatching {
                                    val json = org.json.JSONObject(data)
                                    pendingBookmarkBookId = id
                                    pendingBookmarkPage = json.optInt("page", 0)
                                    navController.navigate("books")
                                }
                            }
                        },
                        onBookmarksChanged = { bookmarksCount = bookmarksPrefs.all.size }
                    )
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
                composable("references") {
                    isBottomBarVisible = false
                    ReferenceProjectScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onProjectClick = { id -> navController.navigate("reference_detail/$id") }
                    )
                }
                composable("reference_detail/{projectId}") { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLong() ?: 0L
                    isBottomBarVisible = false
                    ReferenceDetailScreen(
                        projectId = projectId,
                        onBack = { navController.popBackStack() },
                        onAddClick = { navController.navigate("reference_search/$projectId") }
                    )
                }
                composable("reference_search/{projectId}") { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLong() ?: 0L
                    isBottomBarVisible = false
                    ReferenceSearchScreen(
                        projectId = projectId,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("video_downloader") {
                    isBottomBarVisible = true
                    VideoDownloadScreen(
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateHome = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        initialUrl = sharedXUrl,
                        onInitialUrlConsumed = onSharedXUrlConsumed,
                        onViewerVisibleChanged = { isVisible ->
                            isBottomBarVisible = !isVisible
                        }
                    )
                }
                composable("favorite_artists") {
                    isBottomBarVisible = true
                    FavoriteArtistsScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateHome = {
                            galleryState.lastViewedUri = null
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable("favorite_sites") {
                    isBottomBarVisible = true
                    FavoriteSitesScreen(
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                }
                composable("recommendations") {
                    isBottomBarVisible = true
                    RecommendationScreen(
                        galleryState = galleryState,
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                }
                composable("about") {
                    isBottomBarVisible = false
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            GlobalProgressOverlay()

            if (showTutorial) {
                TutorialDialog(onDismiss = { showTutorial = false })
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            // 基本機能（ホーム、フォルダ、タグ、本、ゴミ箱）以外はボトムバーを非表示にする
            val isBasicFunction = currentRoute == "home" || 
                                 currentRoute == "folders" || 
                                 currentRoute == "mylist" || 
                                 currentRoute == "books" || 
                                 currentRoute == "trash"

            val isHideRoute = !isBasicFunction ||
                currentRoute == null ||
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
                                navController.navigate(route) {
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

private fun Intent.extractXStatusUrl(): String? {
    val candidates = buildList {
        dataString?.let { add(it) }
        getStringExtra(Intent.EXTRA_TEXT)?.let { add(it) }
        getStringExtra(Intent.EXTRA_SUBJECT)?.let { add(it) }
    }

    return candidates
        .asSequence()
        .mapNotNull { X_STATUS_URL_REGEX.find(it)?.value }
        .firstOrNull()
}

private val X_STATUS_URL_REGEX = Regex(
    """https?://(?:www\.)?(?:x\.com|twitter\.com)/[^\s/]+/status/\d+[^\s]*""",
    RegexOption.IGNORE_CASE
)

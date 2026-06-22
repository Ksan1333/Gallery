package com.example.gallery

import android.Manifest
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
import androidx.compose.material.icons.Icons
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
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.service.ThumbnailGenerationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

            val isFullScreenRoute = currentRoute == "mass_edit" ||
                                   currentRoute == "folders_select" ||
                                   currentRoute?.startsWith("analysis") == true

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
                    "\u30ae\u30e3\u30e9\u30ea\u30fc\u30e1\u30cb\u30e5\u30fc",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                NavigationDrawerItem(
                    label = { Text("\u30db\u30fc\u30e0") },
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
                    "\u30d5\u30a9\u30eb\u30c0",
                    modifier = Modifier.padding(16.dp, 8.dp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                NavigationDrawerItem(
                    label = { Text("\u30d5\u30a9\u30eb\u30c0\u5225") },
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
                    label = { Text("\u30bf\u30b0\u5225") },
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
                    label = { Text("\u672c") },
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
                    label = { Text("\u30b4\u30df\u7bb1") },
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
                    label = { Text("\u52d5\u753bDL (X/Twitter)") },
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

                NavigationDrawerItem(
                    label = { Text("\u304a\u6c17\u306b\u5165\u308a\u30af\u30ea\u30a8\u30a4\u30bf\u30fc") },
                    selected = navController.currentBackStackEntryAsState().value?.destination?.route == "favorite_artists",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("favorite_artists")
                    },
                    icon = { Icon(Icons.Default.Favorite, null) },
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
                    label = { Text(if (galleryState.isMeasureModeActive) "Measure mode running..." else "Start measure mode") },
                    selected = galleryState.isMeasureModeActive,
                    onClick = {
                        galleryState.isMeasureModeActive = !galleryState.isMeasureModeActive
                        if (!galleryState.isMeasureModeActive) {
                        }
                    },
                    icon = { 
                        Icon(
                            if (galleryState.isMeasureModeActive) Icons.Default.Pause else Icons.Default.PlayArrow, 
                            null,
                            tint = if (galleryState.isMeasureModeActive) Color.Red else Color.Green
                        ) 
                    },
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
                    label = { Text("Recommendations") },
                    selected = navController.currentBackStackEntryAsState().value?.destination?.route == "recommendations",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("recommendations")
                    },
                    icon = { Icon(Icons.Default.Star, null) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White,
                        unselectedIconColor = Color.White
                    )
                )

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
            }
        },
        gesturesEnabled = isBottomBarVisible && !galleryState.isSelectionMode && !galleryState.isZooming
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
            }

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

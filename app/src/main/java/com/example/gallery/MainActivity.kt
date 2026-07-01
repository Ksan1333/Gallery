package com.example.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.AppText
import com.example.gallery.data.model.MediaData
import com.example.gallery.ui.screen.VideoGalleryScreen
import com.example.gallery.ui.screen.VideoFullscreenViewerScreen
import com.example.gallery.ui.screen.*
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.GalleryViewMode
import com.example.gallery.ui.component.GalleryBottomNavigationBar
import com.example.gallery.ui.component.GlobalProgressOverlay
import com.example.gallery.ui.component.TutorialChooserDialog
import com.example.gallery.ui.component.TutorialDialog
import com.example.gallery.ui.component.TutorialSetupDialog
import com.example.gallery.ui.component.allTutorialTargets
import com.example.gallery.ui.component.defaultTutorialTargetIds
import com.example.gallery.ui.component.tutorialTargetById
import com.example.gallery.ui.component.tutorialTargetForRoute
import com.example.gallery.ui.component.UnifiedMediaEditDialog
import com.example.gallery.ui.theme.GalleryColors
import com.example.gallery.ui.theme.GalleryColorTokens
import com.example.gallery.ui.theme.GalleryTheme
import com.example.gallery.ui.theme.GalleryThemeMode
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.ThumbnailGenerationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BOOKMARKS_PREFS = "book_bookmarks"
private const val APP_PREFS = "app_prefs"
private const val GLOBAL_SETTINGS_PREFS = "global_settings"
private const val THEME_MODE_PREF = "theme_mode"
private const val TEXT_SCALE_PREF = "text_scale"
private const val CUSTOM_PALETTE_ENABLED_PREF = "custom_palette_enabled"
private const val CUSTOM_PALETTE_PREFIX = "custom_palette_"
private const val SCROLL_RESTORE_TRACE = "GALLERY_SCROLL_RESTORE_TRACE"
private const val CUSTOM_ANALYSIS_PERIOD = -2
private const val TUTORIAL_SETUP_DONE_PREF = "tutorial_setup_done"
private const val TUTORIAL_ENABLED_PREFIX = "tutorial_enabled_"
private const val TUTORIAL_SHOWN_PREFIX = "tutorial_shown_"

private fun logScrollRestoreTrace(message: String) {
    Log.d(SCROLL_RESTORE_TRACE, "$SCROLL_RESTORE_TRACE $message")
}

private fun loadThemeMode(context: Context): GalleryThemeMode {
    val stored = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .getString(THEME_MODE_PREF, GalleryThemeMode.SYSTEM.name)
    return runCatching { GalleryThemeMode.valueOf(stored ?: GalleryThemeMode.SYSTEM.name) }
        .getOrDefault(GalleryThemeMode.SYSTEM)
}

private fun saveThemeMode(context: Context, mode: GalleryThemeMode) {
    context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE_PREF, mode.name)
        .apply()
}

private fun loadTextScale(context: Context): Float =
    context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .getFloat(TEXT_SCALE_PREF, 1f)
        .coerceIn(0.75f, 1.45f)

private fun loadStartupRoute(context: Context): String {
    val route = context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString("startupScreen", AppRoutes.HOME)
    return when (route) {
        AppRoutes.HOME,
        AppRoutes.FOLDERS,
        AppRoutes.VIDEOS,
        AppRoutes.BOOKS,
        AppRoutes.REFERENCES,
        AppRoutes.SETTINGS,
        "favorite_artists",
        "favorite_sites",
        "book_bookmarks",
        "video_downloader",
        "about" -> route
        else -> AppRoutes.HOME
    }
}

private fun saveTextScale(context: Context, scale: Float) {
    context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(TEXT_SCALE_PREF, scale.coerceIn(0.75f, 1.45f))
        .apply()
}

private fun loadCustomPalette(context: Context): GalleryColors? {
    val prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(CUSTOM_PALETTE_ENABLED_PREF, false)) return null

    val fallback = GalleryColorTokens.Dark
    fun color(key: String, fallbackColor: Color): Color =
        Color(prefs.getInt(CUSTOM_PALETTE_PREFIX + key, fallbackColor.toArgb()).toLong() and 0xFFFFFFFFL)

    return GalleryColors(
        background = color("background", fallback.background),
        surface = color("surface", fallback.surface),
        surfaceVariant = color("surfaceVariant", fallback.surfaceVariant),
        topBar = color("topBar", fallback.topBar),
        drawer = color("drawer", fallback.drawer),
        card = color("card", fallback.card),
        field = color("field", fallback.field),
        primaryText = color("primaryText", fallback.primaryText),
        secondaryText = color("secondaryText", fallback.secondaryText),
        mutedText = color("mutedText", fallback.mutedText),
        accent = color("accent", fallback.accent),
        accentSoft = color("accentSoft", fallback.accentSoft),
        danger = color("danger", fallback.danger),
        success = color("success", fallback.success),
        divider = color("divider", fallback.divider),
        disabled = color("disabled", fallback.disabled)
    )
}

private fun saveCustomPalette(context: Context, colors: GalleryColors?) {
    context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        .edit()
        .apply {
            if (colors == null) {
                putBoolean(CUSTOM_PALETTE_ENABLED_PREF, false)
            } else {
                putBoolean(CUSTOM_PALETTE_ENABLED_PREF, true)
                putInt(CUSTOM_PALETTE_PREFIX + "background", colors.background.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "surface", colors.surface.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "surfaceVariant", colors.surfaceVariant.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "topBar", colors.topBar.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "drawer", colors.drawer.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "card", colors.card.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "field", colors.field.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "primaryText", colors.primaryText.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "secondaryText", colors.secondaryText.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "mutedText", colors.mutedText.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "accent", colors.accent.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "accentSoft", colors.accentSoft.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "danger", colors.danger.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "success", colors.success.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "divider", colors.divider.toArgb())
                putInt(CUSTOM_PALETTE_PREFIX + "disabled", colors.disabled.toArgb())
            }
        }
        .apply()
}

class MainActivity : ComponentActivity() {
    private var sharedXUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedXUrl = intent.extractXStatusUrl()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val context = LocalContext.current
            var themeMode by rememberSaveable { mutableStateOf(loadThemeMode(context)) }
            var textScale by rememberSaveable { mutableFloatStateOf(loadTextScale(context)) }
            var customPalette by remember { mutableStateOf(loadCustomPalette(context)) }
            GalleryTheme(themeMode = themeMode, customColors = customPalette, textScale = textScale) {
                AppNavigation(
                    sharedXUrl = sharedXUrl,
                    themeMode = themeMode,
                    customPalette = customPalette,
                    textScale = textScale,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        saveThemeMode(context, mode)
                    },
                    onCustomPaletteChange = { palette ->
                        customPalette = palette
                        saveCustomPalette(context, palette)
                    },
                    onTextScaleChange = { scale ->
                        textScale = scale.coerceIn(0.75f, 1.45f)
                        saveTextScale(context, textScale)
                    },
                    onSharedXUrlConsumed = { sharedXUrl = null }
                )
            }
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
    themeMode: GalleryThemeMode = GalleryThemeMode.SYSTEM,
    customPalette: GalleryColors? = null,
    textScale: Float = 1f,
    onThemeModeChange: (GalleryThemeMode) -> Unit = {},
    onCustomPaletteChange: (GalleryColors?) -> Unit = {},
    onTextScaleChange: (Float) -> Unit = {},
    onSharedXUrlConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val galleryState = (context.applicationContext as GalleryApplication).galleryState
    val scope = rememberCoroutineScope()
    val startDestination = remember { loadStartupRoute(context) }
    val globalSettingsPrefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
    val startupPasswordEnabled = remember {
        globalSettingsPrefs.getBoolean("startupPasswordEnabled", false)
    }
    val startupPassword = remember { globalSettingsPrefs.getString("startupPassword", "").orEmpty() }
    var isStartupUnlocked by rememberSaveable(startupPasswordEnabled) { mutableStateOf(!startupPasswordEnabled) }
    var startupPasswordInput by rememberSaveable { mutableStateOf("") }
    var startupPasswordError by rememberSaveable { mutableStateOf(false) }

    val navController = rememberNavController()
    galleryState.navController = navController
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }
    var isVideoFolderOpen by rememberSaveable { mutableStateOf(false) }

    val tutorialPrefs = remember { context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE) }
    var showTutorialSetup by rememberSaveable { mutableStateOf(false) }
    var showTutorialChooser by rememberSaveable { mutableStateOf(false) }
    var activeTutorialTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var tutorialSetupSelection by remember { mutableStateOf(defaultTutorialTargetIds()) }
    LaunchedEffect(Unit) {
        if (!tutorialPrefs.getBoolean(TUTORIAL_SETUP_DONE_PREF, false)) {
            tutorialSetupSelection = defaultTutorialTargetIds()
            showTutorialSetup = true
        }
    }

    val bookmarksPrefs = remember { context.getSharedPreferences(BOOKMARKS_PREFS, Context.MODE_PRIVATE) }
    var bookmarksCount by remember { mutableIntStateOf(bookmarksPrefs.all.size) }

    var pendingBookmarkBookId by remember { mutableStateOf<String?>(null) }
    var pendingBookmarkPage by remember { mutableIntStateOf(-1) }
    var showAnalysisPeriodDialog by rememberSaveable { mutableStateOf(false) }
    var showAnalysisDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingAnalysisType by rememberSaveable { mutableStateOf(AppDefaults.ANALYSIS_TYPE_AI_TAGGING) }
    var analysisPeriodDays by rememberSaveable { mutableIntStateOf(AppDefaults.ANALYSIS_PERIOD_ALL) }
    var customAnalysisStartTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var analysisTargetCount by remember { mutableStateOf<Int?>(null) }

    fun openAnalysisPeriodDialog(type: String = AppDefaults.ANALYSIS_TYPE_AI_TAGGING) {
        pendingAnalysisType = type
        analysisPeriodDays = AppDefaults.ANALYSIS_PERIOD_ALL
        customAnalysisStartTime = null
        analysisTargetCount = null
        showAnalysisPeriodDialog = true
    }

    fun resolveAnalysisPeriodDays(): Int {
        return if (analysisPeriodDays == CUSTOM_ANALYSIS_PERIOD && customAnalysisStartTime != null) {
            val diff = System.currentTimeMillis() - customAnalysisStartTime!!
            (diff / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        } else {
            analysisPeriodDays
        }
    }

    fun startPendingAnalysis() {
        val daysToPass = resolveAnalysisPeriodDays()
        showAnalysisPeriodDialog = false
        navController.navigate(AppRoutes.analysis(type = pendingAnalysisType, periodDays = daysToPass))
    }

    LaunchedEffect(
        showAnalysisPeriodDialog,
        pendingAnalysisType,
        analysisPeriodDays,
        customAnalysisStartTime,
        galleryState.refreshTrigger
    ) {
        if (!showAnalysisPeriodDialog) return@LaunchedEffect
        analysisTargetCount = null
        val periodDays = resolveAnalysisPeriodDays()
        analysisTargetCount = withContext(Dispatchers.IO) {
            countAnalysisTargets(galleryState, pendingAnalysisType, periodDays)
        }
    }

    LaunchedEffect(sharedXUrl) {
        if (sharedXUrl != null) {
            navController.navigate("video_downloader") {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
                restoreState = false
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

    LaunchedEffect(isStartupUnlocked) {
        if (!isStartupUnlocked) return@LaunchedEffect
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
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && isStartupUnlocked) {
                logScrollRestoreTrace(
                    "main_on_resume_refresh before=${galleryState.refreshTrigger} " +
                        "route=${navController.currentBackStackEntry?.destination?.route}"
                )
                galleryState.refresh()
                logScrollRestoreTrace("main_on_resume_refresh after=${galleryState.refreshTrigger}")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val refreshTrigger = galleryState.refreshTrigger
    LaunchedEffect(refreshTrigger, isStartupUnlocked) {
        if (!isStartupUnlocked) return@LaunchedEffect
        ThumbnailGenerationService.startGenerating(
            context,
            galleryState.repository,
            force = false
        )
    }

    LaunchedEffect(isStartupUnlocked) {
        if (isStartupUnlocked) galleryState.repository.mediaDao.cleanupAgeRatingTags()
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

            val isAlwaysShowNavBarRoute = currentRoute == "about" || currentRoute == AppRoutes.SETTINGS || currentRoute == AppRoutes.SEARCH || currentRoute == "mass_edit" || currentRoute == "book_bookmarks" ||
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
    LaunchedEffect(currentRouteForDrawer) {
        if (currentRouteForDrawer != AppRoutes.VIDEOS) {
            isVideoFolderOpen = false
        }
    }
    BackHandler(
        enabled = drawerState.isClosed && currentRouteForDrawer in setOf(
            AppRoutes.HOME,
            AppRoutes.FOLDERS,
            AppRoutes.VIDEOS,
            AppRoutes.BOOKS,
            AppRoutes.TRASH
        )
    ) {
        if (currentRouteForDrawer != AppRoutes.HOME) {
            navController.navigate(AppRoutes.HOME) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    LaunchedEffect(currentRouteForDrawer, showTutorialSetup) {
        if (showTutorialSetup || !tutorialPrefs.getBoolean(TUTORIAL_SETUP_DONE_PREF, false)) {
            return@LaunchedEffect
        }
        val target = tutorialTargetForRoute(currentRouteForDrawer) ?: return@LaunchedEffect
        val enabled = tutorialPrefs.getBoolean(TUTORIAL_ENABLED_PREFIX + target.id, false)
        val alreadyShown = tutorialPrefs.getBoolean(TUTORIAL_SHOWN_PREFIX + target.id, false)
        if (enabled && !alreadyShown) {
            activeTutorialTargetId = target.id
            tutorialPrefs.edit()
                .putBoolean(TUTORIAL_SHOWN_PREFIX + target.id, true)
                .apply()
        }
    }
    val colors = GalleryThemeTokens.colors
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = Color.Transparent,
        unselectedTextColor = colors.primaryText,
        unselectedIconColor = colors.primaryText,
        selectedContainerColor = colors.accentSoft,
        selectedTextColor = colors.accent,
        selectedIconColor = colors.accent
    )
    val isRefOrBookmarkRoute = currentRouteForDrawer == AppRoutes.BOOK_BOOKMARKS || currentRouteForDrawer == AppRoutes.REFERENCES
    val isReferenceInteriorRoute = currentRouteForDrawer?.startsWith("reference_detail") == true ||
        currentRouteForDrawer?.startsWith("reference_search") == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.drawer,
                drawerContentColor = colors.primaryText,
                modifier = Modifier.width(AppConstants.DrawerWidth)
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
                        tint = colors.accent,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp).size(28.dp)
                    )
                    Text(
                        "ギャラリーメニュー",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = AppConstants.SubtitleFontSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    HorizontalDivider(color = colors.divider)

                    Text(
                        "基本機能",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = AppConstants.ExtraSmallFontSize,
                        color = colors.mutedText
                    )

                    NavigationDrawerItem(
                        label = { Text("ホーム") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "home",
                        onClick = {
                            scope.launch { drawerState.close() }
                            galleryState.lastViewedUri = null
                            navController.navigate(AppRoutes.HOME) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(Icons.Default.Home, null) },
                        modifier = Modifier.height(44.dp),
                        colors = drawerItemColors
                    )

                    NavigationDrawerItem(
                        label = { Text("フォルダ") },
                        selected = galleryState.galleryViewMode == GalleryViewMode.FOLDER && navController.currentBackStackEntryAsState().value?.destination?.route == "folders",
                        onClick = {
                            scope.launch { drawerState.close() }
                            galleryState.galleryViewMode = GalleryViewMode.FOLDER
                            navController.navigate("folders") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        modifier = Modifier.height(44.dp),
                        colors = drawerItemColors
                    )

                    NavigationDrawerItem(
                        label = { Text("動画") },
                        selected = galleryState.galleryViewMode == GalleryViewMode.VIDEO && navController.currentBackStackEntryAsState().value?.destination?.route == "videos",
                        onClick = {
                            scope.launch { drawerState.close() }
                            galleryState.galleryViewMode = GalleryViewMode.VIDEO
                            navController.navigate("videos") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(Icons.Default.PlayCircle, null) },
                        modifier = Modifier.height(44.dp),
                        colors = drawerItemColors
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
                        colors = drawerItemColors
                    )

                    NavigationDrawerItem(
                        label = { Text("ゴミ箱") },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == "trash",
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate("trash") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(Icons.Default.Delete, null) },
                        modifier = Modifier.height(44.dp),
                        colors = drawerItemColors
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = colors.divider
                    )

                    Text(
                        "便利機能",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = AppConstants.ExtraSmallFontSize,
                        color = colors.mutedText
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
                        colors = drawerItemColors
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
                        colors = drawerItemColors
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
                        colors = drawerItemColors
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
                        colors = drawerItemColors
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
                        colors = drawerItemColors
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = colors.divider
                    )


                    Text(
                        "情報",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        fontSize = AppConstants.ExtraSmallFontSize,
                        color = colors.mutedText
                    )

                    NavigationDrawerItem(
                        label = { Text(AppText.SETTINGS) },
                        selected = navController.currentBackStackEntryAsState().value?.destination?.route == AppRoutes.SETTINGS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(AppRoutes.SETTINGS)
                        },
                        icon = { Icon(Icons.Default.Settings, null) },
                        modifier = Modifier.height(44.dp),
                        colors = drawerItemColors
                    )

                    NavigationDrawerItem(
                        label = { Text("チュートリアル") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showTutorialChooser = true
                        },
                        icon = { Icon(Icons.Default.Info, null) },
                        modifier = Modifier.height(44.dp),
                        colors = drawerItemColors
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
                        colors = drawerItemColors
                    )

                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        fontSize = AppConstants.ExtraSmallFontSize,
                        color = colors.mutedText
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        gesturesEnabled = (isBottomBarVisible || isRefOrBookmarkRoute) && !isReferenceInteriorRoute && !(currentRouteForDrawer == AppRoutes.VIDEOS && isVideoFolderOpen) && !galleryState.isSelectionMode && !galleryState.isZooming
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val navBackStackEntryForDrawer by navController.currentBackStackEntryAsState()
            val currentRouteForDrawer = navBackStackEntryForDrawer?.destination?.route
            val isGalleryGridVisible =
                currentRouteForDrawer == AppRoutes.HOME ||
                    currentRouteForDrawer == AppRoutes.FOLDERS ||
                    currentRouteForDrawer == AppRoutes.REFERENCES ||
                    currentRouteForDrawer == AppRoutes.BOOK_BOOKMARKS ||
                    currentRouteForDrawer == AppRoutes.BOOKS ||
                    (currentRouteForDrawer == AppRoutes.VIDEOS && !isVideoFolderOpen)

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

            if (isStartupUnlocked) {
                NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AppRoutes.HOME) {
                    isBottomBarVisible = true
                    LaunchedEffect(Unit) {
                        galleryState.lastViewedUri = null
                    }
                    HomeGalleryScreen(
                        onShowViewer = { isBottomBarVisible = false },
                        onHideViewer = { isBottomBarVisible = true },
                        galleryState = galleryState,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onStartAnalysis = { openAnalysisPeriodDialog() },
                        onOpenSearch = { navController.navigate(AppRoutes.SEARCH) },
                        onNavigateToTag = { tag ->
                            galleryState.pendingHomeSearchTag = tag
                            navController.navigate(AppRoutes.HOME) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
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
                composable(AppRoutes.SEARCH) {
                    isBottomBarVisible = false
                    fun navigateToUnfilteredHome() {
                        galleryState.clearHomeSearch()
                        navController.navigate(AppRoutes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                    HomeSearchScreen(
                        galleryState = galleryState,
                        onBack = { navigateToUnfilteredHome() },
                        onShowResults = {
                            navController.navigate(AppRoutes.HOME) {
                                launchSingleTop = false
                                restoreState = false
                            }
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
                        onStartAnalysis = { openAnalysisPeriodDialog() },
                        onFolderSelected = { target ->
                            if (target.startsWith("TAG_NAVIGATION:")) {
                                val tag = target.removePrefix("TAG_NAVIGATION:")
                                galleryState.pendingHomeSearchTag = tag
                                navController.navigate(AppRoutes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
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
                composable(
                    AppRoutes.ANALYSIS_PATTERN,
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
                            navController.navigate(AppRoutes.HOME) {
                                popUpTo(AppRoutes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onCancel = {
                            navController.navigate(AppRoutes.HOME) {
                                popUpTo(AppRoutes.HOME) { inclusive = false }
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
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBookmarksChanged = { bookmarksCount = bookmarksPrefs.all.size },
                        onNavigateToBookmarks = { navController.navigate("book_bookmarks") },
                        onOpenBookSettings = { navController.navigate(AppRoutes.BOOK_VIEWER_SETTINGS) },
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
                composable(AppRoutes.VIDEOS) {
                    var fullscreenVideoList by remember { mutableStateOf<List<MediaData>?>(null) }
                    var fullscreenVideoIndex by remember { mutableIntStateOf(0) }
                    isBottomBarVisible = fullscreenVideoList == null
                    LaunchedEffect(fullscreenVideoList) {
                        if (fullscreenVideoList == null) {
                            window?.let { targetWindow ->
                                targetWindow.statusBarColor = android.graphics.Color.TRANSPARENT
                                targetWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    targetWindow.isNavigationBarContrastEnforced = false
                                }
                                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(targetWindow, false)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        VideoGalleryScreen(
                            galleryState = galleryState,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onFolderStateChanged = { isVideoFolderOpen = it },
                            onFullscreenVideo = { list, index ->
                                window?.let { targetWindow ->
                                    targetWindow.navigationBarColor = android.graphics.Color.BLACK
                                    targetWindow.statusBarColor = android.graphics.Color.BLACK
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        targetWindow.isNavigationBarContrastEnforced = false
                                    }
                                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(targetWindow, false)
                                }
                                fullscreenVideoList = list
                                fullscreenVideoIndex = index
                            }
                        )
                        fullscreenVideoList?.let { list ->
                            VideoFullscreenViewerScreen(
                                videoList = list,
                                initialIndex = fullscreenVideoIndex,
                                onClose = { fullscreenVideoList = null },
                                galleryState = galleryState
                            )
                        }
                    }
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
                        galleryState = galleryState,
                        onBack = { navController.popBackStack() },
                        onAddClick = { navController.navigate("reference_search/$projectId") },
                        onGalleryAddClick = { navController.navigate("reference_gallery_picker/$projectId") }
                    )
                }
                composable("reference_gallery_picker/{projectId}") { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId")?.toLong() ?: 0L
                    isBottomBarVisible = false
                    ReferenceGalleryPickerScreen(
                        projectId = projectId,
                        galleryState = galleryState,
                        onBack = { navController.popBackStack() }
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
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
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
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
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
                composable("about") {
                    isBottomBarVisible = false
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoutes.SETTINGS) {
                    isBottomBarVisible = false
                    AppSettingsScreen(
                        themeMode = themeMode,
                        customPalette = customPalette,
                        textScale = textScale,
                        onThemeModeChange = onThemeModeChange,
                        onCustomPaletteChange = onCustomPaletteChange,
                        onTextScaleChange = onTextScaleChange,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoutes.BOOK_VIEWER_SETTINGS) {
                    isBottomBarVisible = true
                    AppSettingsScreen(
                        themeMode = themeMode,
                        customPalette = customPalette,
                        textScale = textScale,
                        onThemeModeChange = onThemeModeChange,
                        onCustomPaletteChange = onCustomPaletteChange,
                        onTextScaleChange = onTextScaleChange,
                        onBack = { navController.popBackStack() },
                        initialPage = SettingsPage.BOOK_VIEWER,
                        directBackFromInitialPage = true
                    )
                }
            }

            }

            if (isStartupUnlocked && showAnalysisPeriodDialog) {
                AnalysisPeriodDialog(
                    selectedPeriodDays = analysisPeriodDays,
                    customStartTime = customAnalysisStartTime,
                    targetCount = analysisTargetCount,
                    onSelectPeriod = { days ->
                        if (days == CUSTOM_ANALYSIS_PERIOD) {
                            showAnalysisDatePicker = true
                        } else {
                            analysisPeriodDays = days
                            customAnalysisStartTime = null
                        }
                    },
                    onDismiss = { showAnalysisPeriodDialog = false },
                    onStart = ::startPendingAnalysis
                )
            }

            if (isStartupUnlocked && showAnalysisDatePicker) {
                AnalysisDatePickerDialog(
                    initialSelectedDateMillis = customAnalysisStartTime ?: System.currentTimeMillis(),
                    onDismiss = { showAnalysisDatePicker = false },
                    onSelected = { selectedMillis ->
                        customAnalysisStartTime = selectedMillis
                        analysisPeriodDays = CUSTOM_ANALYSIS_PERIOD
                        showAnalysisDatePicker = false
                    }
                )
            }

            if (isStartupUnlocked) {
                GlobalProgressOverlay()
            }

            if (!isStartupUnlocked) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("起動パスワード") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startupPasswordInput,
                                onValueChange = {
                                    startupPasswordInput = it.filter(Char::isDigit)
                                    startupPasswordError = false
                                },
                                label = { Text("パスワード") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                isError = startupPasswordError
                            )
                            if (startupPasswordError) {
                                Text("パスワードが違います", color = GalleryThemeTokens.colors.danger)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (startupPasswordInput == startupPassword) {
                                isStartupUnlocked = true
                            } else {
                                startupPasswordError = true
                            }
                        }) {
                            Text("解除")
                        }
                    }
                )
            }

            if (showTutorialSetup) {
                TutorialSetupDialog(
                    selectedIds = tutorialSetupSelection,
                    onToggle = { targetId ->
                        tutorialSetupSelection = if (targetId in tutorialSetupSelection) {
                            tutorialSetupSelection - targetId
                        } else {
                            tutorialSetupSelection + targetId
                        }
                    },
                    onConfirm = {
                        val editor = tutorialPrefs.edit()
                            .putBoolean(TUTORIAL_SETUP_DONE_PREF, true)
                        allTutorialTargets().forEach { target ->
                            editor
                                .putBoolean(TUTORIAL_ENABLED_PREFIX + target.id, target.id in tutorialSetupSelection)
                                .putBoolean(TUTORIAL_SHOWN_PREFIX + target.id, false)
                        }
                        editor.apply()
                        showTutorialSetup = false
                    },
                    onSkip = {
                        val editor = tutorialPrefs.edit()
                            .putBoolean(TUTORIAL_SETUP_DONE_PREF, true)
                        allTutorialTargets().forEach { target ->
                            editor
                                .putBoolean(TUTORIAL_ENABLED_PREFIX + target.id, false)
                                .putBoolean(TUTORIAL_SHOWN_PREFIX + target.id, false)
                        }
                        editor.apply()
                        showTutorialSetup = false
                    }
                )
            }

            if (showTutorialChooser) {
                TutorialChooserDialog(
                    onSelect = { target ->
                        activeTutorialTargetId = target.id
                        showTutorialChooser = false
                    },
                    onDismiss = { showTutorialChooser = false }
                )
            }

            tutorialTargetById(activeTutorialTargetId)?.let { target ->
                TutorialDialog(
                    target = target,
                    onDismiss = { activeTutorialTargetId = null }
                )
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // 基本機能では下部ナビゲーションを表示する。
            val isBasicFunction = currentRoute == AppRoutes.HOME ||
                currentRoute == AppRoutes.FOLDERS ||
                currentRoute == AppRoutes.BOOKS ||
                currentRoute == AppRoutes.TRASH ||
                currentRoute == AppRoutes.VIDEOS

            val isHideRoute = currentRoute == null ||
                !isBasicFunction ||
                currentRoute == AppRoutes.MASS_EDIT ||
                currentRoute == AppRoutes.FOLDERS_SELECT ||
                currentRoute.startsWith("analysis")

            if (isBottomBarVisible && !isHideRoute) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    GalleryBottomNavigationBar(
                        navController = navController,
                        galleryState = galleryState,
                        onIconClick = { route ->
                            if (route == AppRoutes.VIDEOS && currentRoute == AppRoutes.VIDEOS) {
                                galleryState.requestVideoHome()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisPeriodDialog(
    selectedPeriodDays: Int,
    customStartTime: Long?,
    targetCount: Int?,
    onSelectPeriod: (Int) -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val customLabel = customStartTime?.let { startTime ->
        val formatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        "${formatter.format(java.util.Date(startTime))} 以降"
    } ?: "カレンダーから選ぶ"
    val periods = listOf(
        7 to "直近7日",
        30 to "直近30日",
        AppDefaults.ANALYSIS_PERIOD_ALL to "すべての期間",
        CUSTOM_ANALYSIS_PERIOD to customLabel
    )
    val estimatedSeconds = targetCount?.times(4)
    val estimatedText = when (estimatedSeconds) {
        null -> "計算中..."
        0 -> "約1分"
        else -> "約${(estimatedSeconds + 59) / 60}分（${targetCount}件）"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "AI分析を実行",
                color = colors.primaryText,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("分析対象の期間を選択してください", color = colors.secondaryText)
                Spacer(Modifier.height(8.dp))
                periods.forEach { (days, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPeriod(days) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedPeriodDays == days,
                            onClick = { onSelectPeriod(days) }
                        )
                        Text(
                            text = label,
                            color = if (days == CUSTOM_ANALYSIS_PERIOD && customStartTime != null) {
                                colors.accent
                            } else {
                                colors.primaryText
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = colors.card,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "対象件数: ${targetCount?.toString() ?: "計算中..."}件",
                            color = colors.primaryText,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "推定時間: $estimatedText",
                            color = colors.secondaryText
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                enabled = (selectedPeriodDays != CUSTOM_ANALYSIS_PERIOD || customStartTime != null) &&
                    targetCount != null
            ) {
                Text("分析開始")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisDatePickerDialog(
    initialSelectedDateMillis: Long,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let(onSelected)
                },
                enabled = datePickerState.selectedDateMillis != null
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    ) {
        DatePicker(state = datePickerState)
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

private suspend fun countAnalysisTargets(
    galleryState: GalleryState,
    analysisType: String,
    periodDays: Int
): Int {
    val startTime = if (periodDays == AppDefaults.ANALYSIS_PERIOD_ALL) {
        0L
    } else {
        System.currentTimeMillis() - periodDays.toLong() * 24L * 60L * 60L * 1000L
    }
    val allMedia = galleryState.repository.getAllMedia(forceRefresh = false)
    val metadataByUri = galleryState.repository.getAllMetadataSummary().associateBy { it.uri }

    return allMedia.count { item ->
        if (item.isVideo) return@count false
        if (periodDays != AppDefaults.ANALYSIS_PERIOD_ALL && item.dateAdded < startTime) return@count false

        val metadata = metadataByUri[item.uri]
        when (analysisType) {
            AppDefaults.ANALYSIS_TYPE_AI_TAGGING -> metadata?.isAiAnalyzed != true
            "COLOR_VECTOR" -> metadata?.hasFeatureVector != true
            else -> true
        }
    }
}

private val X_STATUS_URL_REGEX = Regex(
    """https?://(?:www\.)?(?:x\.com|twitter\.com)/[^\s/]+/status/\d+[^\s]*""",
    RegexOption.IGNORE_CASE
)

package com.example.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import com.example.gallery.R
import com.example.gallery.data.local.PreferenceManager
import com.example.gallery.data.model.MediaData
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.component.*
import com.example.gallery.ui.screen.*
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.state.GalleryViewMode
import com.example.gallery.ui.theme.*
import com.example.gallery.service.GlobalOperationService
import com.example.gallery.service.ThumbnailGenerationService
import com.example.gallery.ui.AppConstants
import com.example.gallery.util.AppUpdateManager
import com.example.gallery.util.AppUpdateRelease
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
    val pm = PreferenceManager(context)
    val stored = pm.getString(PreferenceManager.THEME_MODE, GalleryThemeMode.SYSTEM.name)
    return runCatching { GalleryThemeMode.valueOf(stored ?: GalleryThemeMode.SYSTEM.name) }
        .getOrDefault(GalleryThemeMode.SYSTEM)
}

private fun saveThemeMode(context: Context, mode: GalleryThemeMode) {
    PreferenceManager(context).setString(PreferenceManager.THEME_MODE, mode.name)
}

private fun loadTextScale(context: Context): Float =
    PreferenceManager(context).getFloat(PreferenceManager.TEXT_SCALE, 1f)
        .coerceIn(0.75f, 1.45f)

private fun loadStartupRoute(context: Context): String {
    val pm = PreferenceManager(context)
    return when (val route = pm.getGlobalString(PreferenceManager.STARTUP_SCREEN, AppRoutes.HOME)) {
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
    PreferenceManager(context).setFloat(PreferenceManager.TEXT_SCALE, scale.coerceIn(0.75f, 1.45f))
}

private fun loadCustomPalette(context: Context): GalleryColors? {
    val pm = PreferenceManager(context)
    if (!pm.getBoolean(PreferenceManager.CUSTOM_PALETTE_ENABLED, false)) return null

    // We can't use @Composable inside loadCustomPalette which is called in onCreate,
    // but the fallback was GalleryColorTokens.Dark which is now @Composable.
    // However, the original code used GalleryColorTokens.Dark.background directly.
    // Let's use hardcoded fallbacks or just null as it was.

    fun color(key: String, fallbackArgb: Int): Color =
        Color(pm.getInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + key, fallbackArgb).toLong() and 0xFFFFFFFFL)

    return GalleryColors(
        background = color("background", 0xFF101418.toInt()),
        surface = color("surface", 0xFF151B22.toInt()),
        surfaceVariant = color("surfaceVariant", 0xFF1D2630.toInt()),
        topBar = color("topBar", 0xFF0B0F14.toInt()),
        drawer = color("drawer", 0xFF101418.toInt()),
        card = color("card", 0xFF17202A.toInt()),
        field = color("field", 0xFF1F2B36.toInt()),
        primaryText = color("primaryText", 0xFFF4F8FC.toInt()),
        secondaryText = color("secondaryText", 0xFFB7C6D5.toInt()),
        mutedText = color("mutedText", 0xFF8392A3.toInt()),
        accent = color("accent", 0xFF4DA3FF.toInt()),
        accentSoft = color("accentSoft", 0xFF163B5F.toInt()),
        danger = color("danger", 0xFFFF6B7A.toInt()),
        success = color("success", 0xFF47D18C.toInt()),
        warning = color("warning", 0xFFFFC857.toInt()),
        info = color("info", 0xFF4DA3FF.toInt()),
        divider = color("divider", 0x33FFFFFF.toInt()),
        disabled = color("disabled", 0xFF6E7A86.toInt())
    )
}

private fun saveCustomPalette(context: Context, colors: GalleryColors?) {
    val pm = PreferenceManager(context)
    if (colors == null) {
        pm.setBoolean(PreferenceManager.CUSTOM_PALETTE_ENABLED, false)
    } else {
        pm.setBoolean(PreferenceManager.CUSTOM_PALETTE_ENABLED, true)
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "background", colors.background.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "surface", colors.surface.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "surfaceVariant", colors.surfaceVariant.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "topBar", colors.topBar.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "drawer", colors.drawer.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "card", colors.card.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "field", colors.field.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "primaryText", colors.primaryText.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "secondaryText", colors.secondaryText.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "mutedText", colors.mutedText.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "accent", colors.accent.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "accentSoft", colors.accentSoft.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "danger", colors.danger.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "success", colors.success.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "warning", colors.warning.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "info", colors.info.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "divider", colors.divider.toArgb())
        pm.setInt(PreferenceManager.CUSTOM_PALETTE_PREFIX + "disabled", colors.disabled.toArgb())
    }
}

class MainActivity : ComponentActivity() {
    private var sharedXUrl by mutableStateOf<String?>(null)
    private var openUpdateScreen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedXUrl = intent.extractXStatusUrl()
        openUpdateScreen = intent.getBooleanExtra(AppUpdateManager.EXTRA_OPEN_UPDATE, false)
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
                    onSharedXUrlConsumed = { sharedXUrl = null },
                    openUpdateScreen = openUpdateScreen,
                    onUpdateScreenOpened = { openUpdateScreen = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedXUrl = intent.extractXStatusUrl()
        if (intent.getBooleanExtra(AppUpdateManager.EXTRA_OPEN_UPDATE, false)) {
            openUpdateScreen = true
        }
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
    onSharedXUrlConsumed: () -> Unit = {},
    openUpdateScreen: Boolean = false,
    onUpdateScreenOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val galleryState = (context.applicationContext as GalleryApplication).galleryState
    remember { PreferenceManager(context) }
    val scope = rememberCoroutineScope()
    val startDestination = remember { loadStartupRoute(context) }
    val globalSettingsPrefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var edgeSwipeForDrawer by remember(globalSettingsPrefs) {
        mutableStateOf(
            globalSettingsPrefs.getBoolean(PreferenceManager.EDGE_SWIPE_FOR_DRAWER, true)
        )
    }
    DisposableEffect(globalSettingsPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PreferenceManager.EDGE_SWIPE_FOR_DRAWER) {
                edgeSwipeForDrawer = prefs.getBoolean(key, true)
            }
        }
        globalSettingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { globalSettingsPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val startupPasswordEnabled = remember {
        globalSettingsPrefs.getBoolean("startupPasswordEnabled", false)
    }
    val startupPassword = remember { globalSettingsPrefs.getString("startupPassword", "").orEmpty() }
    var isStartupUnlocked by rememberSaveable(startupPasswordEnabled) { mutableStateOf(!startupPasswordEnabled) }
    var startupPasswordInput by rememberSaveable { mutableStateOf("") }
    var startupPasswordError by rememberSaveable { mutableStateOf(false) }
    var startupUpdate by remember { mutableStateOf<AppUpdateRelease?>(null) }
    var dismissedStartupUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var isStartupUpdateDownloading by remember { mutableStateOf(false) }
    var startupUpdateDownloadProgress by remember { mutableFloatStateOf(0f) }
    var startupUpdateError by remember { mutableStateOf<String?>(null) }

    val navController = rememberNavController()
    galleryState.navController = navController
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isBottomBarVisible by rememberSaveable { mutableStateOf(true) }
    var isFolderGalleryOpen by rememberSaveable { mutableStateOf(false) }
    var isVideoFolderOpen by rememberSaveable { mutableStateOf(false) }
    var isBookFolderOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openUpdateScreen, isStartupUnlocked) {
        if (openUpdateScreen && isStartupUnlocked) {
            navController.navigate(AppRoutes.ABOUT) {
                launchSingleTop = true
            }
            onUpdateScreenOpened()
        }
    }

    LaunchedEffect(isStartupUnlocked) {
        if (!isStartupUnlocked) return@LaunchedEffect
        val update = runCatching {
            withContext(Dispatchers.IO) {
                AppUpdateManager.checkForUpdate(context, force = true)
            }
        }.getOrNull()
        startupUpdate = update?.takeUnless { it.version == dismissedStartupUpdateVersion }
    }

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

    var pendingBookmarkBookId by remember { mutableStateOf<String?>(null) }
    var pendingBookmarkPage by remember { mutableIntStateOf(-1) }
    var showAnalysisPeriodDialog by rememberSaveable { mutableStateOf(false) }
    var showAnalysisDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingAnalysisType by rememberSaveable { mutableStateOf(AppDefaults.ANALYSIS_TYPE_AI_TAGGING) }
    var pendingAiTaggerModel by rememberSaveable {
        mutableStateOf(
            AppDefaults.normalizedAiTaggerModel(
                globalSettingsPrefs.getString(
                    AppDefaults.AI_TAGGER_MODEL_KEY,
                    AppDefaults.AI_TAGGER_MODEL_NORMAL
                )
            )
        )
    }
    var analysisPeriodDays by rememberSaveable { mutableIntStateOf(AppDefaults.ANALYSIS_PERIOD_ALL) }
    var customAnalysisStartTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var analysisTargetCount by remember { mutableStateOf<Int?>(null) }

    fun openAnalysisPeriodDialog(type: String = AppDefaults.ANALYSIS_TYPE_AI_TAGGING) {
        pendingAnalysisType = type
        if (type == AppDefaults.ANALYSIS_TYPE_AI_TAGGING) {
            pendingAiTaggerModel = AppDefaults.normalizedAiTaggerModel(
                globalSettingsPrefs.getString(
                    AppDefaults.AI_TAGGER_MODEL_KEY,
                    AppDefaults.AI_TAGGER_MODEL_NORMAL
                )
            )
        }
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
        if (pendingAnalysisType == AppDefaults.ANALYSIS_TYPE_AI_TAGGING) {
            globalSettingsPrefs.edit()
                .putString(AppDefaults.AI_TAGGER_MODEL_KEY, pendingAiTaggerModel)
                .apply()
        }
        showAnalysisPeriodDialog = false
        navController.navigate(AppRoutes.analysis(type = pendingAnalysisType, periodDays = daysToPass))
    }

    LaunchedEffect(
        showAnalysisPeriodDialog,
        pendingAnalysisType,
        pendingAiTaggerModel,
        analysisPeriodDays,
        customAnalysisStartTime,
        galleryState.refreshTrigger
    ) {
        if (!showAnalysisPeriodDialog) return@LaunchedEffect
        analysisTargetCount = null
        val periodDays = resolveAnalysisPeriodDays()
        analysisTargetCount = withContext(Dispatchers.IO) {
            countAnalysisTargets(galleryState, pendingAnalysisType, periodDays, pendingAiTaggerModel)
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

    var pendingMoveUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingMoveTarget by remember { mutableStateOf<String?>(null) }
    var pendingMoveAlreadyMoved by remember { mutableIntStateOf(0) }
    var pendingMoveFailedUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val folderMovePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val retryUris = pendingMoveUris
        val retryTarget = pendingMoveTarget
        val alreadyMoved = pendingMoveAlreadyMoved
        val alreadyFailedUris = pendingMoveFailedUris
        pendingMoveUris = emptyList()
        pendingMoveTarget = null
        pendingMoveAlreadyMoved = 0
        pendingMoveFailedUris = emptyList()
        if (activityResult.resultCode != Activity.RESULT_OK || retryUris.isEmpty() || retryTarget == null) {
            Log.w(
                "FOLDER_MOVE_TRACE",
                "permission_result granted=false pending=${retryUris.size} target=$retryTarget result=${activityResult.resultCode}"
            )
            galleryState.urisToMove = (alreadyFailedUris + retryUris).distinct()
            galleryState.refresh()
            Toast.makeText(context, R.string.msg_move_permission_cancelled, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            when (val retryResult = galleryState.repository.moveMediaToFolder(retryUris, retryTarget)) {
                is MediaRepository.MoveMediaResult.Completed -> {
                    val totalMoved = alreadyMoved + retryResult.movedCount
                    val combinedFailedUris = (alreadyFailedUris + retryResult.failedUris).distinct()
                    Log.d(
                        "FOLDER_MOVE_TRACE",
                        "permission_retry_complete moved=$totalMoved failed=${combinedFailedUris.size}"
                    )
                    galleryState.urisToMove = combinedFailedUris
                    galleryState.refresh()
                    if (combinedFailedUris.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.msg_move_partially_failed,
                                totalMoved,
                                combinedFailedUris.size
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.msg_move_completed, totalMoved),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (
                        combinedFailedUris.isEmpty() &&
                        navController.currentBackStackEntry?.destination?.route == "bulk_move_selection"
                    ) {
                        navController.popBackStack()
                    }
                }
                is MediaRepository.MoveMediaResult.PermissionRequired -> {
                    Log.e(
                        "FOLDER_MOVE_TRACE",
                        "permission_retry_still_required pending=${retryResult.pendingUris.size} target=${retryResult.targetFolder}"
                    )
                    galleryState.urisToMove = (
                        alreadyFailedUris + retryResult.failedUris + retryResult.pendingUris
                    ).distinct()
                    galleryState.refresh()
                    Toast.makeText(context, R.string.msg_move_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(isStartupUnlocked) {
        if (!isStartupUnlocked) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

        val hasAnyPermission = permissions.any {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!hasAnyPermission) {
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

    val systemBarBackgroundColor = GalleryThemeTokens.colors.background.toArgb()
    val systemBarTopColor = GalleryThemeTokens.colors.topBar.toArgb()
    val window = (context as? Activity)?.window
    fun setupSystemBars(isViewerVisible: Boolean, currentRoute: String?) {
        if (window != null) {
            val insetsController =
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }

            val isAlwaysShowNavBarRoute = currentRoute == "about" || currentRoute == AppRoutes.SETTINGS || currentRoute == AppRoutes.MEDIA_VIEWER_SETTINGS || currentRoute == AppRoutes.BOOK_VIEWER_SETTINGS || currentRoute == AppRoutes.VIDEO_VIEWER_SETTINGS || currentRoute == AppRoutes.SEARCH || currentRoute == "mass_edit" || currentRoute == "book_bookmarks" ||
                currentRoute == "references" || currentRoute?.startsWith("reference_detail") == true || currentRoute?.startsWith("reference_search") == true

            if (isAlwaysShowNavBarRoute) {
                window.statusBarColor = systemBarTopColor
                window.navigationBarColor = systemBarBackgroundColor
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                return
            }

            val isViewerHostRoute = currentRoute in setOf(
                AppRoutes.HOME,
                AppRoutes.FOLDERS,
                AppRoutes.TRASH,
                AppRoutes.VIDEOS,
                AppRoutes.BOOKS
            )
            if (isViewerVisible && isViewerHostRoute) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            } else {
                window.navigationBarColor = systemBarBackgroundColor
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
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
        if (currentRouteForDrawer != AppRoutes.FOLDERS) {
            isFolderGalleryOpen = false
        }
        if (currentRouteForDrawer != AppRoutes.VIDEOS) {
            isVideoFolderOpen = false
        }
        if (currentRouteForDrawer != AppRoutes.BOOKS) {
            isBookFolderOpen = false
        }
    }
    BackHandler(
        enabled = drawerState.isClosed && currentRouteForDrawer in setOf(
            AppRoutes.HOME,
            AppRoutes.FOLDERS,
            AppRoutes.VIDEOS,
            AppRoutes.BOOKS,
            AppRoutes.TRASH,
            AppRoutes.REFERENCES,
            "favorite_artists",
            "favorite_sites",
            "book_bookmarks",
            "video_downloader",
            AppRoutes.SETTINGS
        )
    ) {
        if (currentRouteForDrawer != AppRoutes.HOME) {
            val startDestination = loadStartupRoute(context)
            if (currentRouteForDrawer == startDestination) {
                navController.navigate(AppRoutes.HOME) {
                    popUpTo(startDestination) { inclusive = true }
                }
            } else {
                navController.navigate(AppRoutes.HOME) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                    launchSingleTop = true
                    restoreState = false
                }
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
    val textSizes = GalleryThemeTokens.textSizes
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        unselectedContainerColor = Color.Transparent,
        unselectedTextColor = colors.primaryText,
        unselectedIconColor = colors.primaryText,
        selectedContainerColor = colors.accentSoft,
        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    val isDrawerRoute = currentRouteForDrawer in setOf(
        AppRoutes.HOME,
        AppRoutes.FOLDERS,
        AppRoutes.VIDEOS,
        AppRoutes.BOOKS,
        AppRoutes.TRASH,
        AppRoutes.REFERENCES,
        "favorite_artists",
        "favorite_sites",
        AppRoutes.BOOK_BOOKMARKS,
        "video_downloader",
        AppRoutes.SETTINGS
    )
    val isInteriorDrawerBlocked =
        (currentRouteForDrawer == AppRoutes.FOLDERS && isFolderGalleryOpen) ||
            (currentRouteForDrawer == AppRoutes.VIDEOS && isVideoFolderOpen) ||
            (currentRouteForDrawer == AppRoutes.BOOKS && isBookFolderOpen)
    val canOpenDrawerForRoute = isDrawerRoute && !isInteriorDrawerBlocked
    val isReferenceInteriorRoute = currentRouteForDrawer?.startsWith("reference_detail") == true ||
        currentRouteForDrawer?.startsWith("reference_search") == true
    val isGalleryGridVisible =
        currentRouteForDrawer == AppRoutes.HOME ||
            currentRouteForDrawer == AppRoutes.FOLDERS ||
            currentRouteForDrawer == AppRoutes.REFERENCES ||
            currentRouteForDrawer == AppRoutes.BOOK_BOOKMARKS ||
            (currentRouteForDrawer == AppRoutes.BOOKS && !isBookFolderOpen) ||
            (currentRouteForDrawer == AppRoutes.VIDEOS && !isVideoFolderOpen) ||
            currentRouteForDrawer == AppRoutes.TRASH ||
            currentRouteForDrawer == "favorite_artists" ||
            currentRouteForDrawer == "favorite_sites" ||
            currentRouteForDrawer == "video_downloader" ||
            currentRouteForDrawer == AppRoutes.SETTINGS
    val isDrawerEdgeSwipeEnabled =
        !drawerState.isOpen &&
            canOpenDrawerForRoute &&
            !isReferenceInteriorRoute &&
            isGalleryGridVisible &&
            !galleryState.isSelectionMode &&
            !galleryState.isZooming &&
            !galleryState.isMediaViewerOpen &&
            edgeSwipeForDrawer
    val drawerEdgeSwipeModifier = if (isDrawerEdgeSwipeEnabled) {
        Modifier.pointerInput(edgeSwipeForDrawer) {
            awaitPointerEventScope {
                val edgeWidth = 25.dp.toPx()
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgeWidth) {
                        while (true) {
                            if (awaitPointerEvent().changes.all { !it.pressed }) break
                        }
                        continue
                    }

                    var isOpening = false
                    var isHorizontalDrawerDrag = false
                    var isNonDrawerGesture = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val totalX = change.position.x - down.position.x
                        val totalY = change.position.y - down.position.y
                        if (!isHorizontalDrawerDrag && !isNonDrawerGesture &&
                            totalX * totalX + totalY * totalY >
                            viewConfiguration.touchSlop * viewConfiguration.touchSlop
                        ) {
                            isHorizontalDrawerDrag =
                                totalX > 0f && kotlin.math.abs(totalX) > kotlin.math.abs(totalY)
                            isNonDrawerGesture = !isHorizontalDrawerDrag
                        }

                        if (isHorizontalDrawerDrag) {
                            change.consume()
                            if (totalX > 40f && !isOpening) {
                                isOpening = true
                                scope.launch { drawerState.open() }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.drawer,
                drawerContentColor = colors.primaryText,
                modifier = Modifier.width(dimensionResource(R.dimen.drawer_width))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
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
                        stringResource(R.string.drawer_menu_title),
                        modifier = Modifier.padding(16.dp, 8.dp),
                        style = galleryTypography.title.copy(fontSize = textSizes.subtitle)
                    )
                    HorizontalDivider(color = colors.divider)

                    Text(
                        stringResource(R.string.drawer_basic_features),
                        modifier = Modifier.padding(16.dp, 8.dp),
                        style = galleryTypography.label
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_home)) },
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
                        label = { Text(stringResource(R.string.nav_folders)) },
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
                        label = { Text(stringResource(R.string.nav_videos)) },
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
                        label = { Text(stringResource(R.string.nav_books)) },
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
                        label = { Text(stringResource(R.string.nav_trash)) },
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
                        stringResource(R.string.drawer_handy_features),
                        modifier = Modifier.padding(16.dp, 8.dp),
                        style = galleryTypography.label,
                        color = colors.secondaryText
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_references)) },
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
                        label = { Text(stringResource(R.string.nav_fav_creators)) },
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
                        label = { Text(stringResource(R.string.nav_fav_sites)) },
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
                        label = { Text(stringResource(R.string.nav_video_dl)) },
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
                        stringResource(R.string.drawer_info),
                        modifier = Modifier.padding(16.dp, 8.dp),
                        style = galleryTypography.label,
                        color = colors.secondaryText
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_settings)) },
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
                        label = { Text(stringResource(R.string.nav_tutorial)) },
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
                        label = { Text(stringResource(R.string.nav_about)) },
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
                        style = galleryTypography.label,
                        color = colors.secondaryText
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        // Opening is handled by the direction-aware edge handler below.  Keeping the
        // Material drawer disabled while closed prevents it from winning a vertical
        // gallery drag that starts at the left edge.  It remains enabled while open
        // so the drawer can still be closed by swiping left.
        gesturesEnabled = drawerState.isOpen
    ) {
        Box(modifier = Modifier.fillMaxSize().then(drawerEdgeSwipeModifier)) {

            if (isStartupUnlocked) {
                NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AppRoutes.HOME) {
                    isBottomBarVisible = galleryState.activeMediaViewerIndex == null
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
                        onFolderStateChanged = { isFolderGalleryOpen = it },
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
                                when (val moveResult = galleryState.repository.moveMediaToFolder(galleryState.urisToMove, folder)) {
                                    is MediaRepository.MoveMediaResult.Completed -> {
                                        galleryState.urisToMove = moveResult.failedUris
                                        galleryState.refresh()
                                        if (moveResult.failedCount > 0) {
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.msg_move_partially_failed,
                                                    moveResult.movedCount,
                                                    moveResult.failedCount
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.msg_move_completed, moveResult.movedCount),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        if (moveResult.failedCount == 0) navController.popBackStack()
                                    }
                                    is MediaRepository.MoveMediaResult.PermissionRequired -> {
                                        pendingMoveUris = moveResult.pendingUris
                                        pendingMoveTarget = moveResult.targetFolder
                                        pendingMoveAlreadyMoved = moveResult.movedCount
                                        pendingMoveFailedUris = moveResult.failedUris
                                        folderMovePermissionLauncher.launch(
                                            IntentSenderRequest.Builder(moveResult.intentSender).build()
                                        )
                                    }
                                }
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
                        onBookmarksChanged = {},
                        onNavigateToBookmarks = { navController.navigate("book_bookmarks") },
                        onOpenBookSettings = { navController.navigate(AppRoutes.BOOK_VIEWER_SETTINGS) },
                        onFolderStateChanged = { isBookFolderOpen = it },
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
                        onBookmarksChanged = {}
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
                            onSettingsClick = { navController.navigate(AppRoutes.VIDEO_VIEWER_SETTINGS) },
                            onFolderStateChanged = { isVideoFolderOpen = it },
                            onFullscreenVideo = { list, index ->
                                window?.let { targetWindow ->
                                    targetWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
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
                                onNavigateToSettings = { navController.navigate(AppRoutes.VIDEO_VIEWER_SETTINGS) },
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
//                        onNavigateHome = {
//                            galleryState.lastViewedUri = null
//                            navController.navigate("home") {
//                                popUpTo(navController.graph.findStartDestination().id) {
//                                    saveState = false
//                                }
//                                launchSingleTop = true
//                                restoreState = false
//                            }
//                        }
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
                composable(AppRoutes.MEDIA_VIEWER_SETTINGS) {
                    isBottomBarVisible = false
                    AppSettingsScreen(
                        themeMode = themeMode,
                        customPalette = customPalette,
                        textScale = textScale,
                        onThemeModeChange = onThemeModeChange,
                        onCustomPaletteChange = onCustomPaletteChange,
                        onTextScaleChange = onTextScaleChange,
                        onBack = { navController.popBackStack() },
                        initialPage = SettingsPage.MEDIA_VIEWER,
                        directBackFromInitialPage = true
                    )
                }
                composable(AppRoutes.BOOK_VIEWER_SETTINGS) {
                    isBottomBarVisible = false
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
                composable(AppRoutes.VIDEO_VIEWER_SETTINGS) {
                    isBottomBarVisible = false
                    AppSettingsScreen(
                        themeMode = themeMode,
                        customPalette = customPalette,
                        textScale = textScale,
                        onThemeModeChange = onThemeModeChange,
                        onCustomPaletteChange = onCustomPaletteChange,
                        onTextScaleChange = onTextScaleChange,
                        onBack = { navController.popBackStack() },
                        initialPage = SettingsPage.VIDEO_VIEWER,
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
                    analysisType = pendingAnalysisType,
                    selectedAiTaggerModel = pendingAiTaggerModel,
                    onSelectPeriod = { days ->
                        if (days == CUSTOM_ANALYSIS_PERIOD) {
                            showAnalysisDatePicker = true
                        } else {
                            analysisPeriodDays = days
                            customAnalysisStartTime = null
                        }
                    },
                    onSelectAiTaggerModel = { model ->
                        pendingAiTaggerModel = AppDefaults.normalizedAiTaggerModel(model)
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
                    title = { Text(stringResource(R.string.auth_startup_password)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startupPasswordInput,
                                onValueChange = {
                                    startupPasswordInput = it.filter(Char::isDigit)
                                    startupPasswordError = false
                                },
                                label = { Text(stringResource(R.string.auth_password_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                isError = startupPasswordError
                            )
                            if (startupPasswordError) {
                                Text(stringResource(R.string.auth_wrong_password_msg), color = GalleryThemeTokens.colors.danger)
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
                            Text(stringResource(R.string.btn_unlock))
                        }
                    }
                )
            }

            startupUpdate?.let { release ->
                val isMandatory = AppUpdateManager.isMandatoryUpdate(release.version)
                if (isMandatory) {
                    BackHandler(enabled = true) {}
                }
                StartupUpdateDialog(
                    release = release,
                    isMandatory = isMandatory,
                    isDownloading = isStartupUpdateDownloading,
                    downloadProgress = startupUpdateDownloadProgress,
                    error = startupUpdateError,
                    onUpdate = onUpdate@{
                        if (isStartupUpdateDownloading) return@onUpdate
                        scope.launch {
                            isStartupUpdateDownloading = true
                            startupUpdateDownloadProgress = 0f
                            startupUpdateError = null
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    AppUpdateManager.downloadApk(context, release) { progress ->
                                        scope.launch { startupUpdateDownloadProgress = progress }
                                    }
                                }
                            }.onSuccess { apk ->
                                if (!AppUpdateManager.installApk(context, apk)) {
                                    startupUpdateError = context.getString(R.string.update_allow_install_source)
                                }
                            }.onFailure { cause ->
                                startupUpdateError = context.getString(
                                    R.string.update_download_error,
                                    cause.localizedMessage ?: cause.javaClass.simpleName
                                )
                            }
                            isStartupUpdateDownloading = false
                        }
                    },
                    onCancel = {
                        dismissedStartupUpdateVersion = release.version
                        startupUpdate = null
                    }
                )
            }

            if (showTutorialSetup) {
                TutorialSetupDialog(
                    onConfirm = {
                        val editor = tutorialPrefs.edit()
                            .putBoolean(TUTORIAL_SETUP_DONE_PREF, true)
                        allTutorialTargets().forEach { target ->
                            editor
                                .putBoolean(TUTORIAL_ENABLED_PREFIX + target.id, true)
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
            val prefs = remember { context.getSharedPreferences("global_settings", Context.MODE_PRIVATE) }
            val navBarAssignments = remember(prefs) {
                listOf(
                    AppConstants.SLOT_BOTTOM_LEFT,
                    AppConstants.SLOT_BOTTOM_CENTER_LEFT,
                    AppConstants.SLOT_BOTTOM_CENTER,
                    AppConstants.SLOT_BOTTOM_CENTER_RIGHT,
                    AppConstants.SLOT_BOTTOM_RIGHT
                ).mapNotNull { slot ->
                    prefs.getString("nav_bar.$slot", null)?.takeIf { it != "なし" }
                }
            }

            // 基本機能またはユーザーが設定した画面では下部ナビゲーションを表示する。
            val isAssignedRoute = currentRoute in navBarAssignments || 
                (navBarAssignments.isEmpty() && currentRoute in listOf(AppRoutes.HOME, AppRoutes.FOLDERS, AppRoutes.VIDEOS, AppRoutes.BOOKS, AppRoutes.TRASH))
            
            val isBasicFunction = isAssignedRoute ||
                currentRoute == AppRoutes.HOME ||
                currentRoute == AppRoutes.FOLDERS ||
                currentRoute == AppRoutes.BOOKS ||
                currentRoute == AppRoutes.TRASH ||
                currentRoute == AppRoutes.VIDEOS ||
                currentRoute == AppRoutes.REFERENCES ||
                currentRoute == AppRoutes.VIDEO_DOWNLOADER ||
                currentRoute == AppRoutes.FAVORITE_ARTISTS ||
                currentRoute == AppRoutes.FAVORITE_SITES ||
                currentRoute == AppRoutes.BOOK_BOOKMARKS ||
                currentRoute == AppRoutes.SETTINGS ||
                currentRoute == AppRoutes.ABOUT
            val isInteriorGalleryRoute =
                (currentRoute == AppRoutes.FOLDERS && isFolderGalleryOpen) ||
                    (currentRoute == AppRoutes.VIDEOS && isVideoFolderOpen) ||
                    (currentRoute == AppRoutes.BOOKS && isBookFolderOpen)

            val isHideRoute = currentRoute == null ||
                !isBasicFunction ||
                isInteriorGalleryRoute ||
                currentRoute == AppRoutes.MASS_EDIT ||
                currentRoute == AppRoutes.FOLDERS_SELECT ||
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
private fun StartupUpdateDialog(
    release: AppUpdateRelease,
    isMandatory: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    error: String?,
    onUpdate: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val progressPercent = (downloadProgress.coerceIn(0f, 1f) * 100).toInt()
    AlertDialog(
        onDismissRequest = {
            if (!isMandatory && !isDownloading) onCancel()
        },
        title = {
            Text(
                stringResource(
                    if (isMandatory) R.string.update_required_dialog_title else R.string.update_dialog_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_available, release.version))
                if (isMandatory) {
                    Text(
                        stringResource(R.string.update_required_description),
                        color = colors.danger
                    )
                }
                if (release.notes.isNotBlank()) {
                    Text(release.notes, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accent
                    )
                    Text(stringResource(R.string.update_downloading, progressPercent))
                }
                error?.let { message ->
                    Text(message, color = colors.danger)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = !isDownloading) {
                Text(
                    if (isDownloading) {
                        stringResource(R.string.update_downloading, progressPercent)
                    } else {
                        stringResource(R.string.update_download_and_install)
                    }
                )
            }
        },
        dismissButton = if (isMandatory) {
            null
        } else {
            {
                TextButton(onClick = onCancel, enabled = !isDownloading) {
                    Text(stringResource(R.string.update_cancel))
                }
            }
        }
    )
}

@Composable
private fun AnalysisPeriodDialog(
    selectedPeriodDays: Int,
    customStartTime: Long?,
    targetCount: Int?,
    analysisType: String,
    selectedAiTaggerModel: String,
    onSelectPeriod: (Int) -> Unit,
    onSelectAiTaggerModel: (String) -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val customLabel = customStartTime?.let { startTime ->
        val formatter = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
        stringResource(R.string.analysis_period_custom, formatter.format(java.util.Date(startTime)))
    } ?: stringResource(R.string.analysis_period_calendar)
    val periods = listOf(
        7 to stringResource(R.string.analysis_period_7d),
        30 to stringResource(R.string.analysis_period_30d),
        AppDefaults.ANALYSIS_PERIOD_ALL to stringResource(R.string.analysis_period_all),
        CUSTOM_ANALYSIS_PERIOD to customLabel
    )
    val estimatedText = when (val estimatedSeconds = targetCount?.times(4)) {
        null -> stringResource(R.string.analysis_calculating)
        0 -> stringResource(R.string.analysis_zero_minutes)
        else -> stringResource(R.string.analysis_estimated_minutes, (estimatedSeconds + 59) / 60, targetCount)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.analysis_run_ai),
                color = colors.primaryText,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (analysisType == AppDefaults.ANALYSIS_TYPE_AI_TAGGING) {
                    Text(
                        text = stringResource(R.string.settings_ai_tagger_model),
                        color = colors.primaryText,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    listOf(
                        AppDefaults.AI_TAGGER_MODEL_NORMAL to stringResource(R.string.opt_ai_model_normal),
                        AppDefaults.AI_TAGGER_MODEL_HIGH to stringResource(R.string.opt_ai_model_high)
                    ).forEach { (model, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAiTaggerModel(model) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedAiTaggerModel == model,
                                onClick = { onSelectAiTaggerModel(model) }
                            )
                            Text(
                                text = label,
                                color = colors.primaryText,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_ai_tagger_model_desc),
                        color = colors.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(stringResource(R.string.analysis_select_period_desc), color = colors.secondaryText)
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
                            text = stringResource(R.string.analysis_target_count_label, targetCount ?: 0),
                            color = colors.primaryText,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.analysis_estimated_time_label, estimatedText),
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
                Text(stringResource(R.string.analysis_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
    periodDays: Int,
    selectedAiTaggerModel: String = AppDefaults.AI_TAGGER_MODEL_NORMAL
): Int {
    val startTime = if (periodDays == AppDefaults.ANALYSIS_PERIOD_ALL) {
        0L
    } else {
        System.currentTimeMillis() - periodDays.toLong() * 24L * 60L * 60L * 1000L
    }
    val allMedia = galleryState.repository.getAllMedia(forceRefresh = false)
    val metadataByUri = galleryState.repository.getAllMetadataSummary().associateBy { it.uri }
    val selectedTaggerModel = if (analysisType == AppDefaults.ANALYSIS_TYPE_AI_TAGGING) {
        AppDefaults.normalizedAiTaggerModel(selectedAiTaggerModel)
    } else {
        AppDefaults.AI_TAGGER_MODEL_NORMAL
    }

    return allMedia.count { item ->
        if (item.isVideo) return@count false
        if (periodDays != AppDefaults.ANALYSIS_PERIOD_ALL && item.dateAdded < startTime) return@count false

        val metadata = metadataByUri[item.uri]
        when (analysisType) {
            AppDefaults.ANALYSIS_TYPE_AI_TAGGING -> metadata == null ||
                !metadata.isAiAnalyzed ||
                AppDefaults.aiTaggerModelRank(metadata.aiAnalysisModel) < AppDefaults.aiTaggerModelRank(selectedTaggerModel)
            "COLOR_VECTOR" -> metadata?.hasFeatureVector != true
            else -> true
        }
    }
}

private val X_STATUS_URL_REGEX = Regex(
    """https?://(?:www\.)?(?:x\.com|twitter\.com)/[^\s/]+/status/\d+[^\s]*""",
    RegexOption.IGNORE_CASE
)

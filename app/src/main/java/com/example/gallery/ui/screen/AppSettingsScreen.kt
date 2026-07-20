package com.example.gallery.ui.screen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gallery.R
import com.example.gallery.data.local.PreferenceManager
import com.example.gallery.ui.AppConstants
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.AppRoutes
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.component.resolveViewerActionLabel
import com.example.gallery.ui.component.tapZoneCountForLayout
import com.example.gallery.ui.component.tapZoneSpecs
import com.example.gallery.ui.theme.GalleryColorTokens
import com.example.gallery.ui.theme.GalleryColors
import com.example.gallery.ui.theme.GalleryThemeMode
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.example.gallery.ui.theme.GalleryThemePresets
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import com.example.gallery.ui.theme.GalleryPaletteSwatches
import com.example.gallery.ui.theme.galleryTypography
import com.example.gallery.util.GalleryBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val GLOBAL_SETTINGS_PREFS = "global_settings"
private const val BOOK_VIEWER_PREFS = "book_viewer_settings"
private const val VIDEO_VIEWER_PREFS = "video_viewer_settings"
private const val MEDIA_VIEWER_PREFS = "media_viewer_settings"
private const val APP_PREFS = "app_prefs"
private const val THEME_MODE_PREF = "theme_mode"
private const val TEXT_SCALE_PREF = "text_scale"
private const val CUSTOM_PALETTE_ENABLED_PREF = "custom_palette_enabled"
private const val CUSTOM_PALETTE_PREFIX = "custom_palette_"
private const val THEME_PRESET_PREF = "themePreset"

enum class SettingsPage { MENU, GLOBAL, THEME, MEDIA_VIEWER, VIDEO_VIEWER, BOOK_VIEWER }

@Composable
fun AppSettingsScreen(
    themeMode: GalleryThemeMode,
    customPalette: GalleryColors?,
    textScale: Float,
    onThemeModeChange: (GalleryThemeMode) -> Unit,
    onCustomPaletteChange: (GalleryColors?) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
    initialPage: SettingsPage = SettingsPage.MENU,
    directBackFromInitialPage: Boolean = false
) {
    val colors = GalleryThemeTokens.colors
    val context = LocalContext.current
    var currentPage by rememberSaveable(initialPage) { mutableStateOf(initialPage) }
    var resetToken by remember { mutableIntStateOf(0) }
    var pendingResetPage by remember { mutableStateOf<SettingsPage?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Reset scroll when page changes
    LaunchedEffect(currentPage, resetToken) {
        listState.scrollToItem(0)
    }

    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    // 設定画面ではナビゲーションバーを常に表示する
    DisposableEffect(window, insetsController) {
        if (window != null && insetsController != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = colors.topBar.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        onDispose {
            if (window != null && insetsController != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val title = when (currentPage) {
        SettingsPage.MENU -> stringResource(R.string.settings_title)
        SettingsPage.GLOBAL -> stringResource(R.string.settings_global)
        SettingsPage.THEME -> stringResource(R.string.settings_theme)
        SettingsPage.MEDIA_VIEWER -> stringResource(R.string.settings_media_viewer)
        SettingsPage.VIDEO_VIEWER -> stringResource(R.string.settings_video_viewer)
        SettingsPage.BOOK_VIEWER -> stringResource(R.string.settings_book_viewer)
    }

    fun handleBack() {
        if (currentPage == SettingsPage.MENU || (directBackFromInitialPage && currentPage == initialPage)) {
            onBack()
        } else {
            currentPage = SettingsPage.MENU
        }
    }

    BackHandler(enabled = currentPage != SettingsPage.MENU || directBackFromInitialPage) { handleBack() }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = title,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.btn_back),
                onNavigationClick = ::handleBack,
                containerColor = colors.topBar,
                contentColor = colors.primaryText,
                actions = {
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { GalleryBackupManager.exportAllToFile(context) }
                                .onSuccess { file ->
                                    withContext(Dispatchers.Main) {
                                        val msg = context.getString(R.string.msg_saved_to, file.absolutePath)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                                .onFailure { error ->
                                    withContext(Dispatchers.Main) {
                                        val msg = context.getString(R.string.msg_save_failed, error.message)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = stringResource(R.string.btn_save),
                            tint = colors.primaryText
                        )
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { GalleryBackupManager.importSettingsFromFile(context) }
                                .onSuccess {
                                    withContext(Dispatchers.Main) {
                                        applyRuntimeSettings(context, onThemeModeChange, onCustomPaletteChange, onTextScaleChange)
                                        resetToken++
                                        val msg = context.getString(R.string.msg_loaded)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .onFailure { error ->
                                    withContext(Dispatchers.Main) {
                                        val msg = context.getString(R.string.msg_load_failed, error.message)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.btn_load),
                            tint = colors.primaryText
                        )
                    }
                    IconButton(onClick = { pendingResetPage = currentPage }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.settings_reset_to_default),
                            tint = colors.primaryText
                        )
                    }
                }
            )
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(padding)
                .padding(horizontal = dimensionResource(R.dimen.spacing_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base)),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            when (currentPage) {
                SettingsPage.MENU -> {
                    item {
                        SettingsMenu(
                            onGlobalClick = { currentPage = SettingsPage.GLOBAL },
                            onThemeClick = { currentPage = SettingsPage.THEME },
                            onMediaViewerClick = { currentPage = SettingsPage.MEDIA_VIEWER },
                            onBookViewerClick = { currentPage = SettingsPage.BOOK_VIEWER },
                            onVideoViewerClick = { currentPage = SettingsPage.VIDEO_VIEWER }
                        )
                    }
                }

                SettingsPage.GLOBAL -> {
                    globalSettingsItems(scope, listState)
                }

                SettingsPage.THEME -> {
                    themeSettingsItems(
                        themeMode = themeMode,
                        customPalette = customPalette,
                        textScale = textScale,
                        onThemeModeChange = onThemeModeChange,
                        onCustomPaletteChange = onCustomPaletteChange,
                        onTextScaleChange = onTextScaleChange,
                        scope = scope,
                        listState = listState
                    )
                }

                SettingsPage.MEDIA_VIEWER -> {
                    mediaViewerSettingsItems(context, scope, listState)
                }

                SettingsPage.VIDEO_VIEWER -> {
                    videoViewerSettingsItems(context, scope, listState)
                }

                SettingsPage.BOOK_VIEWER -> {
                    bookViewerSettingsItems(context, scope, listState)
                }
            }
        }
    }

    pendingResetPage?.let { page ->
        AlertDialog(
            onDismissRequest = { pendingResetPage = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.settings_reset_confirm_title), color = colors.primaryText) },
            text = { Text(stringResource(R.string.settings_reset_confirm_text), color = colors.secondaryText) },
            confirmButton = {
                Button(onClick = {
                    val pageToReset = page
                    resetSettingsPage(context, pageToReset, onThemeModeChange, onCustomPaletteChange, onTextScaleChange)
                    pendingResetPage = null
                    resetToken++
                }) {
                    Text(stringResource(R.string.btn_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetPage = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsMenu(
    onGlobalClick: () -> Unit,
    onThemeClick: () -> Unit,
    onMediaViewerClick: () -> Unit,
    onVideoViewerClick: () -> Unit,
    onBookViewerClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base))) {
        SettingsMenuItem(
            icon = Icons.Default.SettingsSuggest,
            title = stringResource(R.string.settings_global),
            description = stringResource(R.string.settings_global_desc),
            onClick = onGlobalClick
        )
        SettingsMenuItem(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_theme),
            description = stringResource(R.string.settings_theme_desc),
            onClick = onThemeClick
        )
        SettingsMenuItem(
            icon = Icons.Default.Image,
            title = stringResource(R.string.settings_media_viewer),
            description = stringResource(R.string.settings_media_viewer_desc),
            onClick = onMediaViewerClick
        )
        SettingsMenuItem(
            icon = Icons.Default.PlayArrow,
            title = stringResource(R.string.settings_video_viewer),
            description = stringResource(R.string.settings_video_viewer_desc),
            onClick = onVideoViewerClick
        )
        SettingsMenuItem(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.settings_book_viewer),
            description = stringResource(R.string.settings_book_viewer_desc),
            onClick = onBookViewerClick
        )
    }
}

@Composable
private fun SettingsMenuItem(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    GalleryThemeTokens.textSizes
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium)),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_settings_horizontal), vertical = dimensionResource(R.dimen.spacing_base)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base))
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(dimensionResource(R.dimen.icon_size_settings_menu)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_micro))) {
                Text(title, style = galleryTypography.body.copy(fontWeight = FontWeight.Bold), color = colors.primaryText)
                Text(description, style = galleryTypography.body, color = colors.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.btn_open), style = galleryTypography.body.copy(fontWeight = FontWeight.Bold), color = colors.accent)
        }
    }
}

private fun resetSettingsPage(
    context: Context,
    page: SettingsPage,
    onThemeModeChange: (GalleryThemeMode) -> Unit,
    onCustomPaletteChange: (GalleryColors?) -> Unit,
    onTextScaleChange: (Float) -> Unit
) {
    when (page) {
        SettingsPage.MENU -> {
            context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences(VIDEO_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences(MEDIA_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            onThemeModeChange(GalleryThemeMode.SYSTEM)
            onCustomPaletteChange(null)
            onTextScaleChange(1f)
        }
        SettingsPage.GLOBAL -> context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SettingsPage.MEDIA_VIEWER -> context.getSharedPreferences(MEDIA_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SettingsPage.BOOK_VIEWER -> context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SettingsPage.VIDEO_VIEWER -> context.getSharedPreferences(VIDEO_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SettingsPage.THEME -> {
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit()
                .remove(THEME_MODE_PREF)
                .remove(TEXT_SCALE_PREF)
                .remove(THEME_PRESET_PREF)
                .putBoolean(CUSTOM_PALETTE_ENABLED_PREF, false)
                .apply()
            onThemeModeChange(GalleryThemeMode.SYSTEM)
            onCustomPaletteChange(null)
            onTextScaleChange(1f)
        }
    }
}

@Composable
private fun SettingsTOC(
    sections: List<Pair<String, Int>>,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val colors = GalleryThemeTokens.colors
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(sections) { (label, index) ->
            SuggestionChip(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(index)
                    }
                },
                label = { Text(label, style = galleryTypography.label) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = colors.accentSoft.copy(alpha = 0.5f),
                    labelColor = colors.accent
                ),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.globalSettingsItems(
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    item {
        val sections = listOf(
            stringResource(R.string.settings_section_viewer_display) to 0,
            stringResource(R.string.settings_section_playback) to 1,
            stringResource(R.string.settings_section_operation) to 2,
            stringResource(R.string.settings_section_progress) to 3,
            stringResource(R.string.settings_section_launch_protection) to 4,
            stringResource(R.string.label_bottom_bar_assignment) to 5,
            stringResource(R.string.settings_section_cache) to 6
        )
        SettingsTOC(sections, scope, listState)
        Spacer(Modifier.height(8.dp))

        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
        var smoothing by remember { mutableStateOf(prefs.getString("smoothing", "BILINEAR") ?: "BILINEAR") }
        var fullscreenMode by remember { mutableStateOf(prefs.getString("fullscreenMode", "DISABLED") ?: "DISABLED") }
        var orientation by remember { mutableStateOf(prefs.getString("orientation", "AUTO") ?: "AUTO") }
        var showClockBattery by remember { mutableStateOf(prefs.getBoolean("showClockBattery", false)) }
        var homeShowVideos by remember { mutableStateOf(prefs.getBoolean("homeShowVideos", false)) }
        var showDateHeaders by remember { mutableStateOf(prefs.getBoolean("showDateHeaders", true)) }
        var similarImageGrouping by remember {
            mutableStateOf(prefs.getBoolean(PreferenceManager.SIMILAR_IMAGE_GROUPING, true))
        }
        var similarImageThreshold by remember {
            mutableIntStateOf(prefs.getInt(PreferenceManager.SIMILAR_IMAGE_THRESHOLD, 60))
        }
        var menuOpacity by remember { mutableIntStateOf(prefs.getInt("menuOpacityPercent", 0)) }

        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_viewer_display), stringResource(R.string.settings_section_viewer_display_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_smoothing), smoothingOptions, smoothing, description = stringResource(R.string.desc_smoothing)) { smoothing = it; saveString("smoothing", it) }
            SettingsChoiceRow(stringResource(R.string.settings_fullscreen), fullscreenOptions, fullscreenMode, description = stringResource(R.string.desc_fullscreen)) { fullscreenMode = it; saveString("fullscreenMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_orientation), orientationOptions, orientation, description = stringResource(R.string.desc_orientation)) { orientation = it; saveString("orientation", it) }
            SwitchSetting(stringResource(R.string.settings_show_clock_battery), showClockBattery, description = stringResource(R.string.desc_show_clock_battery)) { showClockBattery = it; saveBoolean("showClockBattery", it) }
            SwitchSetting(stringResource(R.string.settings_show_date_headers), showDateHeaders, description = stringResource(R.string.desc_show_date_headers)) { showDateHeaders = it; saveBoolean("showDateHeaders", it) }
            SwitchSetting(stringResource(R.string.settings_home_show_videos), homeShowVideos, description = stringResource(R.string.desc_home_show_videos)) { homeShowVideos = it; saveBoolean("homeShowVideos", it) }
            SwitchSetting(
                stringResource(R.string.settings_similar_image_grouping),
                similarImageGrouping,
                description = stringResource(R.string.desc_similar_image_grouping)
            ) {
                similarImageGrouping = it
                saveBoolean(PreferenceManager.SIMILAR_IMAGE_GROUPING, it)
            }
            if (similarImageGrouping) {
                SliderSetting(
                    stringResource(R.string.settings_similar_image_grouping) + " (%)",
                    similarImageThreshold,
                    1f..100f,
                    98,
                    stringResource(R.string.unit_percent),
                    description = stringResource(R.string.desc_similar_image_grouping)
                ) {
                    similarImageThreshold = it
                    saveInt(PreferenceManager.SIMILAR_IMAGE_THRESHOLD, it)
                }
            }
            SliderSetting(stringResource(R.string.settings_menu_opacity), menuOpacity, 0f..100f, 9, stringResource(R.string.unit_percent), description = stringResource(R.string.desc_menu_opacity)) { menuOpacity = it; saveInt("menuOpacityPercent", it) }
        }
    }

    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
        var transitionSpeed by remember { mutableIntStateOf(prefs.getInt("transitionSpeedMs", 250)) }
        var slideshowInterval by remember { mutableIntStateOf(prefs.getInt("slideshowIntervalMs", 5000)) }
        var slideshowSpeed by remember { mutableIntStateOf(prefs.getInt("slideshowSpeedMs", 250)) }
        var randomPlayback by remember { mutableStateOf(prefs.getBoolean("randomPlayback", false)) }
        var continuousPlayback by remember { mutableStateOf(prefs.getBoolean("continuousPlayback", true)) }
        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_playback), stringResource(R.string.settings_section_playback_desc)) {
            SliderSetting(stringResource(R.string.settings_transition_speed), transitionSpeed, 0f..2000f, 19, stringResource(R.string.unit_ms), description = stringResource(R.string.desc_transition_speed)) { transitionSpeed = it; saveInt("transitionSpeedMs", it) }
            SliderSetting(stringResource(R.string.settings_slideshow_interval), slideshowInterval, 1000f..30000f, 28, stringResource(R.string.unit_ms), description = stringResource(R.string.desc_slideshow_interval)) { slideshowInterval = it; saveInt("slideshowIntervalMs", it) }
            SliderSetting(stringResource(R.string.settings_slideshow_speed), slideshowSpeed, 0f..2000f, 19, stringResource(R.string.unit_ms), description = stringResource(R.string.desc_slideshow_speed)) { slideshowSpeed = it; saveInt("slideshowSpeedMs", it) }
            SwitchSetting(stringResource(R.string.settings_random_playback), randomPlayback, description = stringResource(R.string.desc_random_playback)) { randomPlayback = it; saveBoolean("randomPlayback", it) }
            SwitchSetting(stringResource(R.string.settings_continuous_playback), continuousPlayback, description = stringResource(R.string.desc_continuous_playback)) { continuousPlayback = it; saveBoolean("continuousPlayback", it) }
        }
    }

    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
        var magnifierScale by remember { mutableIntStateOf(prefs.getInt("magnifierScalePercent", 200)) }
        var longPressMagnifier by remember { mutableStateOf(prefs.getBoolean("longPressMagnifier", false)) }
        var doubleTapFastZoom by remember { mutableStateOf(prefs.getBoolean("doubleTapFastZoom", true)) }
        var confirmDelete by remember { mutableStateOf(prefs.getBoolean("confirmDelete", true)) }
        var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("showHiddenFiles", false)) }
        var controlPanelAutoHideMs by remember { mutableIntStateOf(prefs.getInt("controlPanelAutoHideMs", AppDefaults.CONTROL_PANEL_AUTO_HIDE_MS)) }
        var selectionLongPressMs by remember { mutableIntStateOf(prefs.getInt("selectionLongPressMs", AppDefaults.SELECTION_LONG_PRESS_MS)) }
        var searchHistoryLimit by remember { mutableIntStateOf(prefs.getInt("searchHistoryLimit", 5).coerceIn(1, 10)) }

        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_operation), stringResource(R.string.settings_section_operation_desc)) {
            SwitchSetting(stringResource(R.string.settings_long_press_magnifier), longPressMagnifier, description = stringResource(R.string.desc_long_press_magnifier)) { longPressMagnifier = it; saveBoolean("longPressMagnifier", it) }
            SwitchSetting(stringResource(R.string.settings_double_tap_zoom), doubleTapFastZoom, description = stringResource(R.string.desc_double_tap_zoom)) { doubleTapFastZoom = it; saveBoolean("doubleTapFastZoom", it) }
            SwitchSetting(stringResource(R.string.settings_confirm_delete), confirmDelete, description = stringResource(R.string.desc_confirm_delete)) { confirmDelete = it; saveBoolean("confirmDelete", it) }
            SwitchSetting(stringResource(R.string.settings_show_hidden_files), showHiddenFiles, description = stringResource(R.string.desc_show_hidden_files)) { showHiddenFiles = it; saveBoolean("showHiddenFiles", it) }
            SliderSetting(stringResource(R.string.settings_control_panel_hide), controlPanelAutoHideMs, 1000f..10000f, 8, stringResource(R.string.unit_ms), description = stringResource(R.string.desc_control_panel_hide)) { controlPanelAutoHideMs = it; saveInt("controlPanelAutoHideMs", it) }
            SliderSetting(stringResource(R.string.label_selection_long_press), selectionLongPressMs, 250f..1200f, 18, stringResource(R.string.unit_ms), description = stringResource(R.string.desc_selection_long_press)) { selectionLongPressMs = it; saveInt("selectionLongPressMs", it) }
            SliderSetting(stringResource(R.string.settings_search_history_limit), searchHistoryLimit, 1f..10f, 9, stringResource(R.string.unit_count), description = stringResource(R.string.desc_search_history_limit)) { searchHistoryLimit = it; saveInt("searchHistoryLimit", it.coerceIn(1, 10)) }
            SliderSetting(stringResource(R.string.settings_magnifier_scale), magnifierScale, 100f..300f, 19, stringResource(R.string.unit_percent), description = stringResource(R.string.desc_magnifier_scale)) { magnifierScale = it; saveInt("magnifierScalePercent", it) }
        }
    }

    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
        var progressDisplayMode by remember { mutableStateOf(prefs.getString("progressDisplayMode", "MAX") ?: "MAX") }
        var progressMiniStyle by remember { mutableStateOf(prefs.getString("progressMiniStyle", "BAR") ?: "BAR") }
        var defaultAgeFilter by remember { mutableStateOf(prefs.getString("defaultAgeFilter", AppConstants.RATING_SFW) ?: AppConstants.RATING_SFW) }
        var aiAnalysisSpeedMode by remember {
            mutableStateOf(
                prefs.getString(
                    AppDefaults.AI_ANALYSIS_SPEED_MODE_KEY,
                    AppDefaults.AI_ANALYSIS_SPEED_BALANCED
                ) ?: AppDefaults.AI_ANALYSIS_SPEED_BALANCED
            )
        }
        var aiTaggerModel by remember {
            mutableStateOf(
                prefs.getString(
                    AppDefaults.AI_TAGGER_MODEL_KEY,
                    AppDefaults.AI_TAGGER_MODEL_NORMAL
                ) ?: AppDefaults.AI_TAGGER_MODEL_NORMAL
            )
        }

        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_progress), stringResource(R.string.settings_section_progress_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_ai_analysis_speed), aiAnalysisSpeedOptions, aiAnalysisSpeedMode, description = stringResource(R.string.settings_ai_analysis_speed_desc)) { aiAnalysisSpeedMode = it; saveString(AppDefaults.AI_ANALYSIS_SPEED_MODE_KEY, it) }
            SettingsChoiceRow(stringResource(R.string.settings_ai_tagger_model), aiTaggerModelOptions, aiTaggerModel, description = stringResource(R.string.settings_ai_tagger_model_desc)) { aiTaggerModel = it; saveString(AppDefaults.AI_TAGGER_MODEL_KEY, it) }
            SettingsChoiceRow(stringResource(R.string.settings_display_size), progressDisplayOptions, progressDisplayMode, description = stringResource(R.string.desc_display_size)) { progressDisplayMode = it; saveString("progressDisplayMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_mini_progress_style), progressMiniStyleOptions, progressMiniStyle, description = stringResource(R.string.desc_mini_progress_style)) { progressMiniStyle = it; saveString("progressMiniStyle", it) }
            SettingsChoiceRow(stringResource(R.string.settings_default_age_filter), ageFilterOptions, defaultAgeFilter, description = stringResource(R.string.desc_default_age_filter)) { defaultAgeFilter = it; saveString("defaultAgeFilter", it) }
        }
    }

    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
        var startupScreen by remember { mutableStateOf(prefs.getString("startupScreen", "home") ?: "home") }
        var startupPasswordEnabled by remember { mutableStateOf(prefs.getBoolean("startupPasswordEnabled", false)) }
        var password by remember { mutableStateOf(prefs.getString("startupPassword", "") ?: "") }
        var lowMemoryMode by remember { mutableStateOf(prefs.getBoolean("lowMemoryMode", false)) }

        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_launch_protection), stringResource(R.string.settings_section_launch_protection_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_startup_screen), startupScreenOptions, startupScreen, columns = 1, description = stringResource(R.string.desc_startup_screen)) { startupScreen = it; saveString("startupScreen", it) }
            SwitchSetting(stringResource(R.string.auth_startup_password), startupPasswordEnabled, description = stringResource(R.string.desc_startup_password)) { startupPasswordEnabled = it; saveBoolean("startupPasswordEnabled", it) }
            OutlinedTextField(
                value = password,
                onValueChange = {
                    val digits = it.filter(Char::isDigit)
                    password = digits
                    saveString("startupPassword", digits)
                },
                label = { Text(stringResource(R.string.auth_password_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            SwitchSetting(stringResource(R.string.settings_low_memory_mode), lowMemoryMode, description = stringResource(R.string.desc_low_memory_mode)) { lowMemoryMode = it; saveBoolean("lowMemoryMode", it) }
        }
    }

    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }

        val navBarSlots = listOf(
            AppConstants.SLOT_BOTTOM_LEFT,
            AppConstants.SLOT_BOTTOM_CENTER_LEFT,
            AppConstants.SLOT_BOTTOM_CENTER,
            AppConstants.SLOT_BOTTOM_CENTER_RIGHT,
            AppConstants.SLOT_BOTTOM_RIGHT
        )
        val navBarFunctions = listOf(
            AppRoutes.HOME,
            AppRoutes.FOLDERS,
            AppRoutes.VIDEOS,
            AppRoutes.BOOKS,
            AppRoutes.TRASH,
            AppRoutes.REFERENCES,
            AppRoutes.VIDEO_DOWNLOADER,
            AppRoutes.FAVORITE_ARTISTS,
            AppRoutes.FAVORITE_SITES,
            AppRoutes.BOOK_BOOKMARKS,
            AppRoutes.SETTINGS,
            AppRoutes.ABOUT,
            AppConstants.ACTION_OVERFLOW
        )

        AssignmentEditor(
            title = stringResource(R.string.label_bottom_bar_assignment),
            prefs = prefs,
            keyPrefix = "nav_bar",
            slots = navBarSlots,
            functions = navBarFunctions,
            duplicateAllowed = false,
            layoutType = AssignmentLayoutType.BAR,
            defaultAssignments = defaultBarAssignmentsFor("nav", false, navBarSlots),
            description = stringResource(R.string.desc_bottom_bar_assignment)
        )
    }

    item {
        val context = LocalContext.current
        SettingsSectionCard(stringResource(R.string.settings_section_cache), stringResource(R.string.desc_clear_cache)) {
            val cacheClearedMsg = stringResource(R.string.msg_cache_cleared)
            OutlinedButton(
                onClick = {
                    context.cacheDir.deleteRecursively()
                    Toast.makeText(context, cacheClearedMsg, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteSweep, null)
                Spacer(Modifier.width(dimensionResource(R.dimen.spacing_small)))
                Text(stringResource(R.string.settings_clear_cache), style = galleryTypography.body.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.themeSettingsItems(
    themeMode: GalleryThemeMode,
    customPalette: GalleryColors?,
    textScale: Float,
    onThemeModeChange: (GalleryThemeMode) -> Unit,
    onCustomPaletteChange: (GalleryColors?) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    item {
        val sections = listOf(
            stringResource(R.string.settings_display_theme) to 0,
            stringResource(R.string.settings_color_palette) to 1,
            stringResource(R.string.settings_text_size) to 2
        )
        SettingsTOC(sections, scope, listState)
        Spacer(Modifier.height(8.dp))

        SettingsSectionCard(stringResource(R.string.settings_display_theme), stringResource(R.string.settings_display_theme_desc)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_display_theme),
                options = listOf(
                    R.string.theme_system to GalleryThemeMode.SYSTEM.name,
                    R.string.theme_dark to GalleryThemeMode.DARK.name,
                    R.string.theme_light to GalleryThemeMode.LIGHT.name
                ),
                selected = themeMode.name,
                description = stringResource(R.string.desc_theme_mode)
            ) { value -> onThemeModeChange(GalleryThemeMode.valueOf(value)) }
        }
    }

    item {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE) }
        val fallbackPalette = if (themeMode == GalleryThemeMode.LIGHT) GalleryColorTokens.Light else GalleryColorTokens.Dark
        var themePreset by remember { mutableStateOf(prefs.getString(THEME_PRESET_PREF, if (customPalette == null) "DEFAULT" else "CUSTOM") ?: "DEFAULT") }
        var editablePalette by remember(customPalette, themeMode) { mutableStateOf(customPalette ?: fallbackPalette) }
        var showColorPickerFor by remember { mutableStateOf<ColorField?>(null) }

        fun applyPalette(value: String, palette: GalleryColors?) {
            themePreset = value
            prefs.edit().putString(THEME_PRESET_PREF, value).apply()
            val nextPalette = palette ?: if (value == "DEFAULT") null else editablePalette
            if (nextPalette != null) editablePalette = nextPalette
            onCustomPaletteChange(nextPalette)
        }

        fun updatePalette(next: GalleryColors) {
            editablePalette = next
            themePreset = "CUSTOM"
            prefs.edit().putString(THEME_PRESET_PREF, "CUSTOM").apply()
            onCustomPaletteChange(next)
        }

        val presets = GalleryThemePresets.List
        SettingsSectionCard(stringResource(R.string.settings_color_palette), stringResource(R.string.settings_color_palette_desc)) {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_theme_preset),
                options = presets.map { it.labelRes to it.value },
                selected = themePreset,
                description = stringResource(R.string.desc_theme_preset)
            ) { value ->
                applyPalette(value, presets.find { it.value == value }?.colors)
            }
            OutlinedButton(onClick = { applyPalette("DEFAULT", null) }, enabled = customPalette != null) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text(stringResource(R.string.settings_restore_standard_colors), modifier = Modifier.padding(start = 8.dp))
            }
            colorFields.forEach { field ->
                ColorIconRow(
                    label = stringResource(field.labelRes),
                    color = field.get(editablePalette),
                    onClick = { showColorPickerFor = field }
                )
            }
        }

        if (showColorPickerFor != null) {
            val field = showColorPickerFor!!
            ColorPickerDialog(
                initialColor = field.get(editablePalette),
                onColorSelected = { color ->
                    updatePalette(field.set(editablePalette, color))
                    showColorPickerFor = null
                },
                onDismiss = { showColorPickerFor = null }
            )
        }
    }

    item {
        TextSizeSettingsSection(textScale, onTextScaleChange)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mediaViewerSettingsItems(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    item {
        val sections = listOf(
            stringResource(R.string.settings_media_viewer) to 0,
            stringResource(R.string.label_bottom_bar_assignment_title) to 1
        )
        SettingsTOC(sections, scope, listState)
        Spacer(Modifier.height(8.dp))

        val prefs = remember { context.getSharedPreferences(MEDIA_VIEWER_PREFS, Context.MODE_PRIVATE) }
        var showInfoOverlay by remember { mutableStateOf(prefs.getBoolean("showInfoOverlay", false)) }
        var loopGif by remember { mutableStateOf(prefs.getBoolean("loopGif", true)) }
        var showFrameBar by remember { mutableStateOf(prefs.getBoolean("showFrameBar", false)) }
        var showRandomRecs by remember { mutableStateOf(prefs.getBoolean("showRandomRecs", true)) }
        var showSimilarRecs by remember { mutableStateOf(prefs.getBoolean("showSimilarRecs", true)) }
        var swipeUpRecs by remember { mutableStateOf(prefs.getBoolean("swipeUpRecs", true)) }
        var swipeDownClose by remember { mutableStateOf(prefs.getBoolean("swipeDownClose", true)) }
        var doubleTapZoom by remember { mutableStateOf(prefs.getBoolean("doubleTapZoom", true)) }
        var showSystemBars by remember { mutableStateOf(prefs.getBoolean("showSystemBars", false)) }
        var seekInterval by remember { mutableStateOf(prefs.getString("seekInterval", "10") ?: "10") }

        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_media_viewer), stringResource(R.string.settings_media_viewer_desc)) {
            SwitchSetting(stringResource(R.string.settings_show_info_overlay), showInfoOverlay, description = stringResource(R.string.desc_show_info_overlay)) { showInfoOverlay = it; saveBoolean("showInfoOverlay", it) }
            SwitchSetting(stringResource(R.string.viewer_gif_stepping_stop), loopGif, description = stringResource(R.string.viewer_gif_stepping_stop)) { loopGif = it; saveBoolean("loopGif", it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = GalleryThemeTokens.colors.divider)

            SettingsChoiceRow(stringResource(R.string.label_seek_interval), videoSeekIntervalOptions, seekInterval, description = stringResource(R.string.desc_seek_interval)) { seekInterval = it; saveString("seekInterval", it) }
            SwitchSetting(stringResource(R.string.label_frame_bar), showFrameBar, statusText = if (showFrameBar) stringResource(R.string.label_frame_bar_status_dual) else stringResource(R.string.label_frame_bar_status_single), description = stringResource(R.string.desc_frame_bar)) { showFrameBar = it; saveBoolean("showFrameBar", it) }
            SwitchSetting(stringResource(R.string.label_rec_random), showRandomRecs, description = stringResource(R.string.desc_rec_random)) { showRandomRecs = it; saveBoolean("showRandomRecs", it) }
            SwitchSetting(stringResource(R.string.label_rec_similar), showSimilarRecs, description = stringResource(R.string.desc_similar_image_grouping)) { showSimilarRecs = it; saveBoolean("showSimilarRecs", it) }
            SwitchSetting(stringResource(R.string.label_swipe_up_rec), swipeUpRecs, description = stringResource(R.string.desc_swipe_up_rec)) { swipeUpRecs = it; saveBoolean("swipeUpRecs", it) }
            SwitchSetting(stringResource(R.string.label_swipe_down_close), swipeDownClose, description = stringResource(R.string.desc_swipe_down_close)) { swipeDownClose = it; saveBoolean("swipeDownClose", it) }
            SwitchSetting(stringResource(R.string.label_double_tap_zoom), doubleTapZoom, description = stringResource(R.string.desc_double_tap_zoom_viewer)) { doubleTapZoom = it; saveBoolean("doubleTapZoom", it) }
            SwitchSetting(stringResource(R.string.label_show_system_bars), showSystemBars, description = stringResource(R.string.desc_show_system_bars)) { showSystemBars = it; saveBoolean("showSystemBars", it) }
        }
    }
    val mediaPrefs = context.getSharedPreferences(MEDIA_VIEWER_PREFS, Context.MODE_PRIVATE)
    viewerAssignmentItems(mediaPrefs, "media", includeMediaActions = true, includeTouch = false)
}

private fun androidx.compose.foundation.lazy.LazyListScope.bookViewerSettingsItems(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    item {
        val sections = listOf(
            stringResource(R.string.settings_section_bookshelf) to 0,
            stringResource(R.string.settings_section_display) to 1,
            stringResource(R.string.label_touch_assignment_title) to 2,
            stringResource(R.string.label_bottom_bar_assignment_title) to 3
        )
        SettingsTOC(sections, scope, listState)
        Spacer(Modifier.height(8.dp))

        val prefs = remember { context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE) }
        var fileSort by remember { mutableStateOf(prefs.getString("fileSort", "NAME_ASC") ?: "NAME_ASC") }
        var showThumbnail by remember { mutableStateOf(prefs.getBoolean("showThumbnail", true)) }
        var smoothFilter by remember { mutableStateOf(prefs.getString("smoothFilter", "BILINEAR") ?: "BILINEAR") }
        var enableSwipeDeleteBook by remember { mutableStateOf(prefs.getBoolean("enableSwipeDeleteBook", false)) }
        var autoLoadOnLaunch by remember { mutableStateOf(prefs.getBoolean("autoLoadOnLaunch", true)) }

        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_bookshelf), stringResource(R.string.settings_section_bookshelf_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_file_sort), fileSortOptions, fileSort, description = stringResource(R.string.desc_file_sort)) { fileSort = it; saveString("fileSort", it) }
            SwitchSetting(stringResource(R.string.settings_show_thumbnail), showThumbnail, description = stringResource(R.string.desc_show_thumbnail)) { showThumbnail = it; saveBoolean("showThumbnail", it) }
            SettingsChoiceRow(stringResource(R.string.settings_thumbnail_smoothing), smoothingOptions, smoothFilter, description = stringResource(R.string.desc_thumbnail_smoothing)) { smoothFilter = it; saveString("smoothFilter", it) }
            SwitchSetting(stringResource(R.string.settings_swipe_delete_book), enableSwipeDeleteBook, description = stringResource(R.string.desc_swipe_delete_book)) { enableSwipeDeleteBook = it; saveBoolean("enableSwipeDeleteBook", it) }
            SwitchSetting(stringResource(R.string.settings_auto_load_on_launch), autoLoadOnLaunch, description = stringResource(R.string.desc_auto_load_on_launch)) { autoLoadOnLaunch = it; saveBoolean("autoLoadOnLaunch", it) }
        }
    }

    item {
        val globalPrefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
        val prefs = remember { context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE) }
        var bindingDirection by remember { mutableStateOf(prefs.getString("bindingDirection", "RIGHT") ?: "RIGHT") }
        var scrollMode by remember { mutableStateOf(prefs.getString("scrollMode", "PAGE") ?: "PAGE") }
        var transitionEffect by remember { mutableStateOf(prefs.getString("transitionEffect", "SLIDE_HORIZONTAL") ?: "SLIDE_HORIZONTAL") }
        var readMark by remember { mutableStateOf(prefs.getString("readMark", "NONE") ?: "NONE") }
        var seekAnchorsEnabled by remember { mutableStateOf(prefs.getBoolean("seekAnchorsEnabled", true)) }
        var maxSeekAnchors by remember { mutableIntStateOf(prefs.getInt("maxSeekAnchors", 3)) }
        var tapZoneLayout by remember { mutableStateOf(normalizeTapZoneLayoutSetting(prefs.getString("tapZoneLayout", "THREE"))) }
        var touchIndicator by remember { mutableStateOf(prefs.getBoolean("touchIndicator", globalPrefs.getBoolean("touchIndicator", false))) }

        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_section_display), stringResource(R.string.settings_section_display_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_binding_direction), listOf(R.string.opt_right to "RIGHT", R.string.opt_left to "LEFT"), bindingDirection, description = stringResource(R.string.desc_binding_direction)) { bindingDirection = it; saveString("bindingDirection", it) }
            SettingsChoiceRow(stringResource(R.string.settings_viewer_mode), bookScrollModeOptions, scrollMode, description = stringResource(R.string.desc_viewer_mode)) { scrollMode = it; saveString("scrollMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_transition_effect), pageTransitionEffectOptions, transitionEffect, description = stringResource(R.string.desc_transition_effect)) { transitionEffect = it; saveString("transitionEffect", it) }
            SettingsChoiceRow(stringResource(R.string.settings_read_mark), readMarkOptions, readMark, description = stringResource(R.string.desc_read_mark)) { readMark = it; saveString("readMark", it) }
            SwitchSetting(stringResource(R.string.settings_seek_anchors), seekAnchorsEnabled, description = stringResource(R.string.desc_seek_anchors)) { seekAnchorsEnabled = it; saveBoolean("seekAnchorsEnabled", it) }
            if (seekAnchorsEnabled) {
                SliderSetting(stringResource(R.string.settings_max_seek_anchors), maxSeekAnchors, 1f..5f, 4, stringResource(R.string.unit_count)) { maxSeekAnchors = it; saveInt("maxSeekAnchors", it) }
            }
            SettingsChoiceRow(stringResource(R.string.settings_screen_allocation), tapZoneLayoutOptions, tapZoneLayout, description = stringResource(R.string.desc_screen_allocation)) { tapZoneLayout = it; saveString("tapZoneLayout", it) }
            SwitchSetting(stringResource(R.string.settings_touch_indicator), touchIndicator, description = stringResource(R.string.desc_touch_indicator)) { touchIndicator = it; saveBoolean("touchIndicator", it) }
        }
    }

    val bookPrefs = context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)
    val tapZoneLayout = normalizeTapZoneLayoutSetting(bookPrefs.getString("tapZoneLayout", "THREE"))
    viewerAssignmentItems(bookPrefs, "book", includeMediaActions = false, includeTouch = true, touchLayout = tapZoneLayout)
}

private fun androidx.compose.foundation.lazy.LazyListScope.videoViewerSettingsItems(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    item {
        val sections = listOf(
            stringResource(R.string.settings_video_viewer) to 0,
            stringResource(R.string.label_bottom_bar_assignment_title) to 1
        )
        SettingsTOC(sections, scope, listState)
        Spacer(Modifier.height(8.dp))

        val prefs = remember { context.getSharedPreferences(VIDEO_VIEWER_PREFS, Context.MODE_PRIVATE) }
        var autoPlay by remember { mutableStateOf(prefs.getBoolean("autoPlay", true)) }
        var loopPlayback by remember { mutableStateOf(prefs.getBoolean("loopPlayback", true)) }
        var defaultMute by remember { mutableStateOf(prefs.getBoolean("defaultMute", false)) }
        var directViewer by remember { mutableStateOf(prefs.getBoolean("directViewer", false)) }
        var seekInterval by remember { mutableStateOf(prefs.getString("seekInterval", "10") ?: "10") }
        var resizeMode by remember { mutableStateOf(prefs.getString("resizeMode", "FIT") ?: "FIT") }

        fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }

        SettingsSectionCard(stringResource(R.string.settings_video_viewer), stringResource(R.string.settings_video_viewer_desc)) {
            SwitchSetting(stringResource(R.string.settings_video_auto_play), autoPlay, description = stringResource(R.string.desc_transition_speed)) { autoPlay = it; saveBoolean("autoPlay", it) }
            SwitchSetting(stringResource(R.string.settings_video_loop), loopPlayback, description = stringResource(R.string.settings_video_loop)) { loopPlayback = it; saveBoolean("loopPlayback", it) }
            SwitchSetting(stringResource(R.string.settings_video_default_mute), defaultMute, description = stringResource(R.string.settings_video_default_mute)) { defaultMute = it; saveBoolean("defaultMute", it) }
            SwitchSetting(stringResource(R.string.settings_video_direct_viewer), directViewer, description = stringResource(R.string.desc_video_direct_viewer)) { directViewer = it; saveBoolean("directViewer", it) }
            SettingsChoiceRow(stringResource(R.string.settings_video_seek_interval), videoSeekIntervalOptions, seekInterval, description = stringResource(R.string.desc_seek_interval)) { seekInterval = it; saveString("seekInterval", it) }
            SettingsChoiceRow(stringResource(R.string.settings_video_resize_mode), videoResizeModeOptions, resizeMode, description = stringResource(R.string.desc_transition_effect)) { resizeMode = it; saveString("resizeMode", it) }
        }
    }
    val videoPrefs = context.getSharedPreferences(VIDEO_VIEWER_PREFS, Context.MODE_PRIVATE)
    viewerAssignmentItems(videoPrefs, "video", includeMediaActions = false, includeTouch = false)
}

private fun androidx.compose.foundation.lazy.LazyListScope.viewerAssignmentItems(
    prefs: SharedPreferences,
    prefix: String,
    includeMediaActions: Boolean,
    includeTouch: Boolean,
    touchLayout: String? = null
) {
    if (includeTouch) {
        item {
            val touchSlots = tapZoneSlotsForLayout(touchLayout)
            val touchFunctions = listOf(
                AppConstants.ACTION_PREV,
                AppConstants.ACTION_NEXT,
                AppConstants.ACTION_TOGGLE_UI,
                AppConstants.ACTION_ZOOM,
                AppConstants.ACTION_SETTINGS,
                AppConstants.ACTION_SEARCH,
                AppConstants.ACTION_SAVE,
                AppConstants.ACTION_TAG
            )
            val defaultTouchAssignments = defaultTouchAssignmentsFor(prefix, touchSlots)
            AssignmentEditor(
                title = stringResource(R.string.label_touch_assignment_title),
                prefs = prefs,
                keyPrefix = "${prefix}_touch",
                slots = touchSlots,
                functions = touchFunctions,
                duplicateAllowed = true,
                layoutType = AssignmentLayoutType.TOUCH,
                defaultAssignments = defaultTouchAssignments,
                description = stringResource(R.string.desc_touch_assignment)
            )
        }
    }

    val barSlots = listOf(
        AppConstants.SLOT_BOTTOM_LEFT,
        AppConstants.SLOT_BOTTOM_CENTER_LEFT,
        AppConstants.SLOT_BOTTOM_CENTER,
        AppConstants.SLOT_BOTTOM_CENTER_RIGHT,
        AppConstants.SLOT_BOTTOM_RIGHT
    )
    val baseButtonFunctions = listOf(
        AppConstants.ACTION_CLOSE,
        AppConstants.ACTION_SETTINGS,
        AppConstants.ACTION_BOOKMARK,
        AppConstants.ACTION_ROTATE,
        AppConstants.ACTION_SCREENSHOT,
        AppConstants.ACTION_PREV,
        AppConstants.ACTION_NEXT,
        AppConstants.ACTION_PLAY_PAUSE,
        AppConstants.ACTION_OVERFLOW
    )
    val mediaButtonFunctions = listOf(
        AppConstants.ACTION_TRASH,
        AppConstants.ACTION_FAVORITE,
        AppConstants.ACTION_SLIDESHOW,
        AppConstants.ACTION_GIF,
        AppConstants.ACTION_ASCII2D,
        AppConstants.ACTION_WALLPAPER,
        AppConstants.ACTION_THUMBNAIL,
        AppConstants.ACTION_TAG
    )
    val buttonFunctions = if (includeMediaActions) baseButtonFunctions + mediaButtonFunctions else baseButtonFunctions
    val defaultBarAssignments = defaultBarAssignmentsFor(prefix, includeMediaActions, barSlots)

    item {
        AssignmentEditor(
            title = stringResource(R.string.label_bottom_bar_assignment_title),
            prefs = prefs,
            keyPrefix = "${prefix}_bar",
            slots = barSlots,
            functions = buttonFunctions,
            duplicateAllowed = false,
            layoutType = AssignmentLayoutType.BAR,
            defaultAssignments = defaultBarAssignments,
            description = stringResource(R.string.desc_bottom_bar_assignment)
        )
    }
}

@Composable
private fun ColorIconRow(label: String, color: Color, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimensionResource(R.dimen.spacing_settings_halo)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_medium))
    ) {
        Box(
            modifier = Modifier
                .size(dimensionResource(R.dimen.icon_size_large))
                .background(color, CircleShape)
                .border(1.dp, colors.divider, CircleShape)
        )
        Text(label, style = galleryTypography.body, color = colors.primaryText, modifier = Modifier.weight(1f))
        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(dimensionResource(R.dimen.icon_size_check)))
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val palette = GalleryPaletteSwatches.Preset

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.settings_color_palette), color = colors.primaryText) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.color_picker_preview_height))
                        .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                        .background(initialColor)
                        .border(1.dp, colors.divider, RoundedCornerShape(dimensionResource(R.dimen.radius_medium))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initialColor.toHexString(), color = if (initialColor.luminance() > 0.5f) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(dimensionResource(R.dimen.spacing_medium)))
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))) {
                    palette.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))) {
                            row.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(dimensionResource(R.dimen.color_picker_swatch_size))
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { onColorSelected(color) }
                                        .border(if (color == initialColor) 2.dp else 0.dp, colors.accent,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

@Composable
private fun ThemeModeRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.accentSoft else colors.card),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.spacing_base), vertical = dimensionResource(R.dimen.spacing_settings_horizontal) / 1.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = galleryTypography.body, color = colors.primaryText, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun tapZoneSlotsForLayout(layout: String?): List<String> {
    return tapZoneSpecs(tapZoneCountForLayout(layout)).map { spec -> tapZoneSlotLabel(spec.id) }
}

private fun normalizeTapZoneLayoutSetting(value: String?): String {
    return when (value) {
        "ELEVEN", "ELEVEN_SPLIT" -> "ELEVEN"
        "SEVEN", "SEVEN_SPLIT" -> "SEVEN"
        "FIVE", "FIVE_SPLIT" -> "FIVE"
        "FOUR", "FOUR_SPLIT" -> "FOUR"
        else -> "THREE"
    }
}

@Composable
private fun tapZoneSlotLabel(id: String): String {
    return when (id) {
        "top_start" -> stringResource(R.string.tap_zone_top_start)
        "top_center" -> stringResource(R.string.tap_zone_top_center)
        "top_end" -> stringResource(R.string.tap_zone_top_end)
        "left_upper" -> stringResource(R.string.tap_zone_left_upper)
        "left_lower" -> stringResource(R.string.tap_zone_left_lower)
        "left" -> stringResource(R.string.tap_zone_left)
        "center" -> stringResource(R.string.tap_zone_center)
        "right" -> stringResource(R.string.tap_zone_right)
        "right_upper" -> stringResource(R.string.tap_zone_right_upper)
        "right_lower" -> stringResource(R.string.tap_zone_right_lower)
        "bottom_start" -> stringResource(R.string.tap_zone_bottom_start)
        "bottom_center" -> stringResource(R.string.tap_zone_bottom_center)
        "bottom_end" -> stringResource(R.string.tap_zone_bottom_end)
        "top" -> stringResource(R.string.tap_zone_top)
        "bottom" -> stringResource(R.string.tap_zone_bottom)
        else -> id
    }
}

private fun defaultBarAssignmentsFor(
    prefix: String,
    includeMediaActions: Boolean,
    slots: List<String>
): Map<String, String> {
    val defaults = when {
        prefix == "nav" -> listOf(
            AppRoutes.HOME,
            AppRoutes.FOLDERS,
            AppRoutes.VIDEOS,
            AppRoutes.BOOKS,
            AppRoutes.TRASH
        )
        includeMediaActions -> listOf(
            AppConstants.ACTION_TRASH,
            AppConstants.ACTION_SETTINGS,
            AppConstants.ACTION_ROTATE,
            AppConstants.ACTION_FAVORITE,
            AppConstants.ACTION_OVERFLOW
        )
        prefix == "book" -> listOf(
            AppConstants.ACTION_CLOSE,
            AppConstants.ACTION_PREV,
            AppConstants.ACTION_BOOKMARK,
            AppConstants.ACTION_NEXT,
            AppConstants.ACTION_OVERFLOW
        )
        prefix == "video" -> listOf(
            AppConstants.ACTION_ROTATE,
            AppConstants.ACTION_PREV,
            AppConstants.ACTION_PLAY_PAUSE,
            AppConstants.ACTION_NEXT,
            AppConstants.ACTION_OVERFLOW
        )
        else -> listOf(
            AppConstants.ACTION_CLOSE,
            AppConstants.ACTION_PREV,
            AppConstants.ACTION_SETTINGS,
            AppConstants.ACTION_NEXT,
            AppConstants.ACTION_OVERFLOW
        )
    }
    return slots.zip(defaults).toMap()
}

private fun defaultTouchAssignmentsFor(
    prefix: String,
    slots: List<String>
): Map<String, String> {
    if (prefix != "book") return emptyMap()
    val defaultsBySlot = mapOf(
        "左上" to AppConstants.ACTION_SETTINGS,
        "上" to AppConstants.ACTION_TOGGLE_UI,
        "右上" to AppConstants.ACTION_SEARCH,
        "左上側" to AppConstants.ACTION_PREV,
        "左下側" to AppConstants.ACTION_PREV,
        "左" to AppConstants.ACTION_PREV,
        "中央" to AppConstants.ACTION_ZOOM,
        "右" to AppConstants.ACTION_NEXT,
        "右上側" to AppConstants.ACTION_NEXT,
        "右下側" to AppConstants.ACTION_NEXT,
        "左下" to AppConstants.ACTION_PREV,
        "下" to AppConstants.ACTION_TOGGLE_UI,
        "右下" to AppConstants.ACTION_NEXT
    )
    return slots.associateWith { slot -> defaultsBySlot[slot] ?: "なし" }
}

private enum class AssignmentLayoutType { TOUCH, BAR, MENU }

@Composable
private fun AssignmentEditor(
    title: String,
    prefs: SharedPreferences,
    keyPrefix: String,
    slots: List<String>,
    functions: List<String>,
    duplicateAllowed: Boolean,
    layoutType: AssignmentLayoutType,
    defaultAssignments: Map<String, String> = emptyMap(),
    description: String? = null
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val labelNone = stringResource(R.string.label_action_none)
    val slotBounds = remember { mutableStateMapOf<String, Rect>() }
    var assignments by remember(keyPrefix, slots, defaultAssignments) {
        mutableStateOf(slots.associateWith { slot -> prefs.getString("$keyPrefix.$slot", defaultAssignments[slot] ?: labelNone) ?: labelNone })
    }

    // Drag state
    var draggingFunction by remember { mutableStateOf<String?>(null) }
    var editorRootPos by remember { mutableStateOf(Offset.Zero) }
    var dragRootPos by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedItemBounds by remember { mutableStateOf<Rect?>(null) }
    var hoveredSlot by remember { mutableStateOf<String?>(null) }

    fun clearDragState() {
        draggingFunction = null
        draggedItemBounds = null
        dragTouchOffset = Offset.Zero
        dragRootPos = Offset.Zero
        hoveredSlot = null
    }

    fun updateHoveredSlot(position: Offset) {
        val nextSlot = slotBounds.entries.firstOrNull { it.value.contains(position) }?.key
        if (nextSlot != hoveredSlot) {
            hoveredSlot = nextSlot
            if (nextSlot != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    fun assign(slot: String, function: String) {
        val next = assignments.toMutableMap()
        if (!duplicateAllowed && function != "なし") {
            next.entries.filter { it.key != slot && it.value == function }.forEach { entry ->
                next[entry.key] = "なし"
                prefs.edit().putString("$keyPrefix.${entry.key}", "なし").apply()
            }
        }
        next[slot] = function
        assignments = next
        prefs.edit().putString("$keyPrefix.$slot", function).apply()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    SettingsSectionCard(title, description ?: "") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { editorRootPos = it.boundsInRoot().topLeft }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Layout Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.field, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (layoutType) {
                        AssignmentLayoutType.TOUCH -> {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .width(dimensionResource(R.dimen.scrollbar_container_width))
                                    .aspectRatio(0.7f)
                                    .border(2.dp, colors.divider, RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                                    .padding(dimensionResource(R.dimen.spacing_tiny))
                            ) {
                                val cellWidth = maxWidth / 5f
                                val cellHeight = maxHeight / 5f
                                tapZoneSpecs(slots.size).zip(slots).forEach { (spec, slot) ->
                                    val func = assignments[slot] ?: labelNone
                                    Surface(
                                        color = if (func == labelNone) colors.surface.copy(alpha = 0.3f) else colors.accentSoft,
                                        shape = RoundedCornerShape(dimensionResource(R.dimen.radius_small)),
                                        border = BorderStroke(1.dp, if (func == labelNone) colors.divider else colors.accent),
                                        modifier = Modifier
                                            .offset(
                                                x = cellWidth * spec.column.toFloat(),
                                                y = cellHeight * spec.row.toFloat()
                                            )
                                            .size(
                                                width = cellWidth * spec.columnSpan.toFloat(),
                                                height = cellHeight * spec.rowSpan.toFloat()
                                            )
                                            .padding(2.dp)
                                            .onGloballyPositioned { slotBounds[slot] = it.boundsInRoot() }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(slot, color = colors.mutedText, fontSize = textSizes.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (func != labelNone) {
                                                Text(resolveViewerActionLabel(func), color = colors.accent, fontSize = textSizes.badge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        AssignmentLayoutType.BAR -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dimensionResource(R.dimen.header_height))
                                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                                    .padding(horizontal = dimensionResource(R.dimen.spacing_small)),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                slots.forEach { slot ->
                                    val func = assignments[slot] ?: labelNone
                                    val isActive = func != labelNone
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(if (isActive) colors.accentSoft else colors.surface.copy(alpha = 0.5f))
                                            .border(1.dp, if (isActive) colors.accent else colors.divider, CircleShape)
                                            .onGloballyPositioned { slotBounds[slot] = it.boundsInRoot() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isActive) {
                                            Text(resolveViewerActionLabel(func).take(2), color = colors.accent, fontSize = textSizes.badge, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = colors.mutedText)
                                        }
                                    }
                                }
                            }
                        }
                        AssignmentLayoutType.MENU -> {
                            Column(
                                modifier = Modifier
                                    .width(dimensionResource(R.dimen.grid_placeholder_height))
                                    .background(colors.surface, RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                                    .border(1.dp, colors.divider, RoundedCornerShape(dimensionResource(R.dimen.radius_medium)))
                                    .padding(vertical = dimensionResource(R.dimen.spacing_tiny)),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                slots.forEach { slot ->
                                    val func = assignments[slot] ?: labelNone
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(dimensionResource(R.dimen.drawer_item_height))
                                            .padding(horizontal = dimensionResource(R.dimen.spacing_base))
                                            .onGloballyPositioned { slotBounds[slot] = it.boundsInRoot() },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))
                                    ) {
                                        Icon(
                                            if (func == labelNone) Icons.Default.CheckBoxOutlineBlank else Icons.Default.RadioButtonChecked,
                                            null,
                                            modifier = Modifier.size(dimensionResource(R.dimen.spacing_medium)),
                                            tint = if (func == labelNone) colors.mutedText else colors.accent
                                        )
                                        Text(
                                            text = if (func == labelNone) "($slot)" else resolveViewerActionLabel(func),
                                            color = if (func == labelNone) colors.mutedText else colors.primaryText,
                                            style = galleryTypography.bodySecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Palette
                Text(stringResource(R.string.label_action_catalog_guide), color = colors.secondaryText, style = galleryTypography.bodySecondary)
                val allFunctions = listOf("なし") + functions
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allFunctions) { function ->
                        var itemBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
                        Surface(
                            color = if (function == "なし") colors.surfaceVariant else colors.accentSoft,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, if (function == "なし") colors.divider else colors.accent),
                            modifier = Modifier
                                .onGloballyPositioned { itemBoundsInRoot = it.boundsInRoot() }
                                .pointerInput(function, slotBounds) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val bounds = itemBoundsInRoot
                                            if (bounds != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggingFunction = function
                                                draggedItemBounds = bounds
                                                dragTouchOffset = offset
                                                dragRootPos = bounds.topLeft + offset
                                                updateHoveredSlot(dragRootPos)
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            itemBoundsInRoot?.let { bounds ->
                                                dragRootPos = bounds.topLeft + change.position
                                                updateHoveredSlot(dragRootPos)
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggingFunction != null) {
                                                (hoveredSlot ?: slotBounds.entries.firstOrNull { it.value.contains(dragRootPos) }?.key)?.let { slot ->
                                                    assign(slot, function)
                                                }
                                            }
                                            clearDragState()
                                        },
                                        onDragCancel = { clearDragState() }
                                    )
                                }
                        ) {
                            Text(
                                text = resolveViewerActionLabel(function),
                                color = if (function == "なし") colors.primaryText else colors.accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            hoveredSlot?.let { slot ->
                slotBounds[slot]?.let { bounds ->
                    val halo = dimensionResource(R.dimen.spacing_settings_halo)
                    val haloPx = with(density) { halo.toPx() }
                    val localTopLeft = bounds.topLeft - editorRootPos - Offset(haloPx, haloPx)
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(localTopLeft.x.roundToInt(), localTopLeft.y.roundToInt())
                            }
                            .then(
                                with(density) {
                                    Modifier
                                        .width(bounds.width.toDp() + halo * 2)
                                        .height(bounds.height.toDp() + halo * 2)
                                }
                            )
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.92f),
                                shape = if (layoutType == AssignmentLayoutType.BAR) CircleShape else RoundedCornerShape(dimensionResource(R.dimen.radius_medium) + 2.dp)
                            )
                            .zIndex(9f)
                    )
                }
            }

            // Drag Overlay
            draggingFunction?.let { func ->
                val draggedBounds = draggedItemBounds
                val localTopLeft = dragRootPos - editorRootPos - dragTouchOffset
                Surface(
                    color = colors.accent.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(dimensionResource(R.dimen.radius_full)),
                    modifier = Modifier
                        .offset {
                            IntOffset(localTopLeft.x.roundToInt(), localTopLeft.y.roundToInt())
                        }
                        .then(
                            if (draggedBounds != null) {
                                with(density) {
                                    Modifier
                                        .width(draggedBounds.width.toDp())
                                        .height(draggedBounds.height.toDp())
                                }
                            } else {
                                Modifier
                                    .height(dimensionResource(R.dimen.button_size_viewer_action))
                                    .widthIn(min = dimensionResource(R.dimen.drag_overlay_width_min), max = dimensionResource(R.dimen.drag_overlay_width_max))
                            }
                        )
                        .zIndex(10f),
                    shadowElevation = dimensionResource(R.dimen.card_elevation_default)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                        Text(
                            resolveViewerActionLabel(func),
                            color = colors.background,
                            fontWeight = FontWeight.Bold,
                            fontSize = textSizes.small,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dp.toSp() = with(LocalDensity.current) { this@toSp.toSp() }

@Composable
private fun SettingsSectionCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium)),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.spacing_settings_horizontal)), verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base))) {
            Text(title, style = galleryTypography.title, color = colors.primaryText)
            Text(description, style = galleryTypography.body, color = colors.secondaryText)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsChoiceRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: String,
    description: String? = null,
    columns: Int = 2,
    onSelected: (String) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.second == selected }?.let { stringResource(it.first) } ?: selected

    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))) {
        Text(title, style = galleryTypography.body.copy(fontWeight = FontWeight.Bold), color = colors.primaryText)
        if (description != null) {
            Text(description, style = galleryTypography.body, color = colors.secondaryText)
        }

        if (options.size >= 3) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = galleryTypography.body,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        unfocusedContainerColor = colors.field,
                        focusedContainerColor = colors.field,
                        unfocusedBorderColor = colors.divider,
                        focusedBorderColor = colors.accent
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium))
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    options.forEach { (labelRes, value) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes), style = galleryTypography.body, color = colors.primaryText) },
                            onClick = {
                                onSelected(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        } else {
            options.chunked(columns.coerceAtLeast(1)).forEach { rowOptions ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))) {
                    rowOptions.forEach { (labelRes, value) ->
                        FilterChip(
                            selected = selected == value,
                            onClick = { onSelected(value) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(labelRes), style = galleryTypography.body, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                    repeat(columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    suffix: String,
    description: String? = null,
    onValueChange: (Int) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    var draftValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = galleryTypography.body.copy(fontWeight = FontWeight.Bold), color = colors.primaryText)
            Text("${draftValue.toInt()}$suffix", style = galleryTypography.body.copy(fontWeight = FontWeight.Bold), color = colors.accent)
        }
        if (description != null) {
            Text(description, style = galleryTypography.body, color = colors.secondaryText)
        }
        Slider(
            value = draftValue,
            onValueChange = {
                draftValue = it
                onValueChange(it.toInt())
            },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    description: String? = null,
    statusText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensionResource(R.dimen.spacing_tiny)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_base)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = galleryTypography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.primaryText
            )
            if (description != null) {
                Text(description, style = galleryTypography.body, color = colors.secondaryText)
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = galleryTypography.label,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TextSizeSettingsSection(textScale: Float, onTextScaleChange: (Float) -> Unit) {
    val colors = GalleryThemeTokens.colors
    var draftScale by remember(textScale) { mutableFloatStateOf(textScale) }
    SettingsSectionCard(stringResource(R.string.settings_text_size), stringResource(R.string.settings_text_size_desc)) {
        Text(stringResource(R.string.settings_current_value, (draftScale * 100).toInt()), style = galleryTypography.body.copy(fontWeight = FontWeight.Bold), color = colors.accent)
        Slider(
            value = draftScale,
            onValueChange = {
                draftScale = it
                onTextScaleChange(it)
            },
            valueRange = 0.75f..1.45f,
            steps = 13
        )
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(dimensionResource(R.dimen.radius_medium)),
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(dimensionResource(R.dimen.spacing_base)), verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_small))) {
                Text(stringResource(R.string.settings_sample_header), style = MaterialTheme.typography.titleLarge, color = colors.primaryText, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_sample_body), style = MaterialTheme.typography.bodyLarge, color = colors.secondaryText)
                Text(stringResource(R.string.settings_sample_label), style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
            }
        }
    }
}

private data class ColorField(
    val labelRes: Int,
    val get: (GalleryColors) -> Color,
    val set: (GalleryColors, Color) -> GalleryColors
)

private val colorFields = listOf(
    ColorField(R.string.color_background, { it.background }, { colors, value -> colors.copy(background = value) }),
    ColorField(R.string.color_surface, { it.surface }, { colors, value -> colors.copy(surface = value) }),
    ColorField(R.string.color_primary_text, { it.primaryText }, { colors, value -> colors.copy(primaryText = value) }),
    ColorField(R.string.color_secondary_text, { it.secondaryText }, { colors, value -> colors.copy(secondaryText = value) }),
    ColorField(R.string.color_accent, { it.accent }, { colors, value -> colors.copy(accent = value) }),
    ColorField(R.string.color_danger, { it.danger }, { colors, value -> colors.copy(danger = value) }),
    ColorField(R.string.color_success, { it.success }, { colors, value -> colors.copy(success = value) }),
    ColorField(R.string.color_divider, { it.divider }, { colors, value -> colors.copy(divider = value) })
)

private val smoothingOptions = listOf(
    R.string.opt_nearest to "NEAREST",
    R.string.opt_averaging to "AVERAGING",
    R.string.opt_bilinear to "BILINEAR",
    R.string.opt_bicubic to "BICUBIC",
    R.string.opt_lanczos3 to "LANCZOS3"
)
private val fullscreenOptions = listOf(
    R.string.opt_disabled to "DISABLED",
    R.string.opt_fullscreen to "FULLSCREEN",
    R.string.opt_hide_status_bar to "HIDE_STATUS_BAR",
    R.string.opt_hide_nav_bar to "HIDE_NAV_BAR"
)
private val orientationOptions = listOf(
    R.string.opt_auto to "AUTO",
    R.string.opt_portrait to "PORTRAIT",
    R.string.opt_landscape to "LANDSCAPE",
    R.string.opt_reverse_portrait to "REVERSE_PORTRAIT",
    R.string.opt_reverse_landscape to "REVERSE_LANDSCAPE"
)
private val bookScrollModeOptions = listOf(
    R.string.opt_page to "PAGE",
    R.string.opt_vertical to "VERTICAL",
    R.string.opt_horizontal to "HORIZONTAL"
)
private val pageTransitionEffectOptions = listOf(
    R.string.opt_none to "NONE",
    R.string.opt_page_curl to "PAGE_CURL",
    R.string.opt_slide_horizontal to "SLIDE_HORIZONTAL",
    R.string.opt_slide_vertical to "SLIDE_VERTICAL",
    R.string.opt_cover to "COVER",
    R.string.opt_fade to "FADE"
)
private val readMarkOptions = listOf(
    R.string.opt_none to "NONE",
    R.string.opt_icon to "ICON",
    R.string.opt_page to "PAGE",
    R.string.opt_percent to "PERCENT",
    R.string.opt_progress_bar to "PROGRESS_BAR"
)
private val fileSortOptions = listOf(
    R.string.opt_name_asc to "NAME_ASC",
    R.string.opt_name_desc to "NAME_DESC",
    R.string.opt_date_desc to "DATE_DESC",
    R.string.opt_date_asc to "DATE_ASC"
)
private val tapZoneLayoutOptions = listOf(
    R.string.opt_three_split to "THREE",
    R.string.opt_four_split to "FOUR",
    R.string.opt_five_split to "FIVE",
    R.string.opt_seven_split to "SEVEN",
    R.string.opt_eleven_split to "ELEVEN"
)
private val progressDisplayOptions = listOf(R.string.opt_max to "MAX", R.string.opt_min to "MIN")
private val progressMiniStyleOptions = listOf(R.string.opt_bar to "BAR", R.string.opt_circle to "CIRCLE")
private val aiAnalysisSpeedOptions = listOf(
    R.string.opt_ai_speed_balanced to AppDefaults.AI_ANALYSIS_SPEED_BALANCED,
    R.string.opt_ai_speed_fast to AppDefaults.AI_ANALYSIS_SPEED_FAST,
    R.string.opt_ai_speed_accuracy to AppDefaults.AI_ANALYSIS_SPEED_ACCURACY
)
private val aiTaggerModelOptions = listOf(
    R.string.opt_ai_model_normal to AppDefaults.AI_TAGGER_MODEL_NORMAL,
    R.string.opt_ai_model_high to AppDefaults.AI_TAGGER_MODEL_HIGH
)

private val videoResizeModeOptions = listOf(
    R.string.opt_fit to "FIT",
    R.string.opt_fill to "FILL",
    R.string.opt_zoom to "ZOOM"
)

private val videoSeekIntervalOptions = listOf(
    R.string.skip_1 to "1",
    R.string.skip_2 to "2",
    R.string.skip_5 to "5",
    R.string.skip_10 to "10",
    R.string.skip_30 to "30",
    R.string.skip_60 to "60"
)

private val ageFilterOptions = listOf(
    R.string.opt_age_sfw to AppConstants.RATING_SFW,
    R.string.opt_age_r15 to AppConstants.RATING_R15,
    R.string.opt_age_r18 to AppConstants.RATING_R18
)

private val startupScreenOptions = listOf(
    R.string.nav_home to "home",
    R.string.nav_folders to "folders",
    R.string.nav_videos to "videos",
    R.string.nav_books to "books",
    R.string.nav_references to "references",
    R.string.nav_fav_creators to "favorite_artists",
    R.string.nav_fav_sites to "favorite_sites",
    R.string.nav_book_bookmarks to "book_bookmarks",
    R.string.nav_video_dl to "video_downloader",
    R.string.nav_settings to "settings",
    R.string.nav_about to "about"
)

private fun Color.toHexString(): String {
    val argb = toArgb()
    return "#%08X".format(argb)
}

private fun exportAllSettingsJson(context: Context): String {
    val json = JSONObject()
    json.put("global", prefsToJson(context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE)))
    json.put("theme", prefsToJson(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)))
    json.put("mediaViewer", prefsToJson(context.getSharedPreferences(MEDIA_VIEWER_PREFS, Context.MODE_PRIVATE)))
    json.put("bookViewer", prefsToJson(context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)))
    json.put("videoViewer", prefsToJson(context.getSharedPreferences(VIDEO_VIEWER_PREFS, Context.MODE_PRIVATE)))
    return json.toString(2)
}

private fun importAllSettingsJson(context: Context, raw: String) {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
    if (json.has("global") || json.has("theme") || json.has("mediaViewer") || json.has("bookViewer") || json.has("videoViewer")) {
        json.optJSONObject("global")?.let {
            importPrefsJson(context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE), it)
        }
        json.optJSONObject("theme")?.let {
            importPrefsJson(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE), it)
        }
        json.optJSONObject("mediaViewer")?.let {
            importPrefsJson(context.getSharedPreferences(MEDIA_VIEWER_PREFS, Context.MODE_PRIVATE), it)
        }
        json.optJSONObject("bookViewer")?.let {
            importPrefsJson(context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE), it)
        }
        json.optJSONObject("videoViewer")?.let {
            importPrefsJson(context.getSharedPreferences(VIDEO_VIEWER_PREFS, Context.MODE_PRIVATE), it)
        }
    } else {
        importPrefsJson(context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE), json)
    }
}

private fun prefsToJson(prefs: SharedPreferences): JSONObject {
    val json = JSONObject()
    prefs.all.toSortedMap().forEach { (key, value) ->
        when (value) {
            is Boolean -> json.put(key, value)
            is Int -> json.put(key, value)
            is Long -> json.put(key, value)
            is Float -> json.put(key, value.toDouble())
            is String -> json.put(key, value)
            is Set<*> -> json.put(key, JSONArray(value.filterIsInstance<String>()))
        }
    }
    return json
}

private fun importPrefsJson(prefs: SharedPreferences, json: JSONObject) {
    val editor = prefs.edit().clear()
    json.keys().forEach { key ->
        when (val value = json.get(key)) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            is String -> editor.putString(key, value)
            is JSONArray -> {
                val values = buildSet {
                    for (index in 0 until value.length()) {
                        value.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                editor.putStringSet(key, values)
            }
        }
    }
    editor.apply()
}

private fun applyRuntimeSettings(
    context: Context,
    onThemeModeChange: (GalleryThemeMode) -> Unit,
    onCustomPaletteChange: (GalleryColors?) -> Unit,
    onTextScaleChange: (Float) -> Unit
) {
    val prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    val modeString = prefs.getString(THEME_MODE_PREF, GalleryThemeMode.SYSTEM.name)
    val mode = runCatching {
        GalleryThemeMode.valueOf(modeString ?: GalleryThemeMode.SYSTEM.name)
    }.getOrDefault(GalleryThemeMode.SYSTEM)
    onThemeModeChange(mode)
    val textScale = when (val value = prefs.all[TEXT_SCALE_PREF]) {
        is Float -> value
        is Int -> value.toFloat()
        is Long -> value.toFloat()
        is Double -> value.toFloat()
        is String -> value.toFloatOrNull() ?: 1f
        else -> 1f
    }
    onTextScaleChange(textScale.coerceIn(0.75f, 1.45f))
    onCustomPaletteChange(loadCustomPaletteFromPrefs(context, prefs))
}

private fun loadCustomPaletteFromPrefs(context: Context, prefs: SharedPreferences): GalleryColors? {
    if (!prefs.getBoolean(CUSTOM_PALETTE_ENABLED_PREF, false)) return null

    fun color(key: String, fallbackArgb: Int): Color =
        Color(prefs.getInt(CUSTOM_PALETTE_PREFIX + key, fallbackArgb).toLong() and 0xFFFFFFFFL)

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

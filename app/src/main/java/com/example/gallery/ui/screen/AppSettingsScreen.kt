package com.example.gallery.ui.screen

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import com.example.gallery.R
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryColorTokens
import com.example.gallery.ui.theme.GalleryColors
import com.example.gallery.ui.theme.GalleryThemeMode
import com.example.gallery.ui.theme.GalleryThemeTokens
import org.json.JSONArray
import org.json.JSONObject

private const val GLOBAL_SETTINGS_PREFS = "global_settings"
private const val BOOK_VIEWER_PREFS = "book_viewer_settings"
private const val APP_PREFS = "app_prefs"
private const val THEME_MODE_PREF = "theme_mode"
private const val TEXT_SCALE_PREF = "text_scale"
private const val CUSTOM_PALETTE_ENABLED_PREF = "custom_palette_enabled"
private const val CUSTOM_PALETTE_PREFIX = "custom_palette_"
private const val THEME_PRESET_PREF = "themePreset"

enum class SettingsPage { MENU, GLOBAL, THEME, BOOK_VIEWER }

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
    val title = when (currentPage) {
        SettingsPage.MENU -> stringResource(R.string.settings_title)
        SettingsPage.GLOBAL -> stringResource(R.string.settings_global)
        SettingsPage.THEME -> stringResource(R.string.settings_theme)
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
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                key(currentPage, resetToken) {
                    when (currentPage) {
                        SettingsPage.MENU -> SettingsMenu(
                            resetKey = resetToken,
                            onGlobalClick = { currentPage = SettingsPage.GLOBAL },
                            onThemeClick = { currentPage = SettingsPage.THEME },
                            onBookViewerClick = { currentPage = SettingsPage.BOOK_VIEWER },
                            onImported = {
                                applyRuntimeSettings(context, onThemeModeChange, onCustomPaletteChange, onTextScaleChange)
                                resetToken++
                            }
                        )

                        SettingsPage.GLOBAL -> GlobalSettingsSection()
                        SettingsPage.THEME -> ThemeSettingsSection(
                            themeMode = themeMode,
                            customPalette = customPalette,
                            textScale = textScale,
                            onThemeModeChange = onThemeModeChange,
                            onCustomPaletteChange = onCustomPaletteChange,
                            onTextScaleChange = onTextScaleChange
                        )

                        SettingsPage.BOOK_VIEWER -> BookViewerSettingsSection()
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
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
                    resetSettingsPage(context, page, onThemeModeChange, onCustomPaletteChange, onTextScaleChange)
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
    resetKey: Int,
    onGlobalClick: () -> Unit,
    onThemeClick: () -> Unit,
    onBookViewerClick: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    var settingsJson by remember(resetKey) { mutableStateOf(exportAllSettingsJson(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.settings_book_viewer),
            description = stringResource(R.string.settings_book_viewer_desc),
            onClick = onBookViewerClick
        )
        SettingsSectionCard(stringResource(R.string.settings_data_title), stringResource(R.string.settings_data_desc)) {
            SettingsDataSection(
                json = settingsJson,
                onJsonChange = { settingsJson = it },
                onExport = { settingsJson = exportAllSettingsJson(context) },
                onImport = {
                    importAllSettingsJson(context, settingsJson)
                    settingsJson = exportAllSettingsJson(context)
                    onImported()
                }
            )
        }
    }
}

@Composable
private fun SettingsMenuItem(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold)
                Text(description, color = colors.secondaryText)
            }
            Text(stringResource(R.string.btn_open), color = colors.accent)
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
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            onThemeModeChange(GalleryThemeMode.SYSTEM)
            onCustomPaletteChange(null)
            onTextScaleChange(1f)
        }

        SettingsPage.GLOBAL -> context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
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

        SettingsPage.BOOK_VIEWER -> context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
private fun GlobalSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var smoothing by remember { mutableStateOf(prefs.getString("smoothing", "BILINEAR") ?: "BILINEAR") }
    var fullscreenMode by remember { mutableStateOf(prefs.getString("fullscreenMode", "DISABLED") ?: "DISABLED") }
    var orientation by remember { mutableStateOf(prefs.getString("orientation", "AUTO") ?: "AUTO") }
    var startupScreen by remember { mutableStateOf(prefs.getString("startupScreen", "home") ?: "home") }
    var transitionSpeed by remember { mutableIntStateOf(prefs.getInt("transitionSpeedMs", 250)) }
    var slideshowInterval by remember { mutableIntStateOf(prefs.getInt("slideshowIntervalMs", 5000)) }
    var slideshowSpeed by remember { mutableIntStateOf(prefs.getInt("slideshowSpeedMs", 250)) }
    var controlPanelAutoHideMs by remember { mutableIntStateOf(prefs.getInt("controlPanelAutoHideMs", AppDefaults.CONTROL_PANEL_AUTO_HIDE_MS)) }
    var menuOpacity by remember { mutableIntStateOf(prefs.getInt("menuOpacityPercent", 0)) }
    var magnifierScale by remember { mutableIntStateOf(prefs.getInt("magnifierScalePercent", 200)) }
    var longPressMagnifier by remember { mutableStateOf(prefs.getBoolean("longPressMagnifier", false)) }
    var doubleTapFastZoom by remember { mutableStateOf(prefs.getBoolean("doubleTapFastZoom", true)) }
    var touchIndicator by remember { mutableStateOf(prefs.getBoolean("touchIndicator", false)) }
    var randomPlayback by remember { mutableStateOf(prefs.getBoolean("randomPlayback", false)) }
    var continuousPlayback by remember { mutableStateOf(prefs.getBoolean("continuousPlayback", true)) }
    var startupPasswordEnabled by remember { mutableStateOf(prefs.getBoolean("startupPasswordEnabled", false)) }
    var password by remember { mutableStateOf(prefs.getString("startupPassword", "") ?: "") }
    var lowMemoryMode by remember { mutableStateOf(prefs.getBoolean("lowMemoryMode", false)) }
    var progressDisplayMode by remember { mutableStateOf(prefs.getString("progressDisplayMode", "MAX") ?: "MAX") }
    var progressMiniStyle by remember { mutableStateOf(prefs.getString("progressMiniStyle", "BAR") ?: "BAR") }

    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionCard(stringResource(R.string.settings_section_viewer_display), stringResource(R.string.settings_section_viewer_display_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_smoothing), smoothingOptions, smoothing) { smoothing = it; saveString("smoothing", it) }
            SettingsChoiceRow(stringResource(R.string.settings_fullscreen), fullscreenOptions, fullscreenMode) { fullscreenMode = it; saveString("fullscreenMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_orientation), orientationOptions, orientation) { orientation = it; saveString("orientation", it) }
            SliderSetting(stringResource(R.string.settings_menu_opacity), menuOpacity, 0f..100f, 9, stringResource(R.string.unit_percent)) { menuOpacity = it; saveInt("menuOpacityPercent", it) }
        }
        SettingsSectionCard(stringResource(R.string.settings_section_playback), stringResource(R.string.settings_section_playback_desc)) {
            SliderSetting(stringResource(R.string.settings_transition_speed), transitionSpeed, 0f..2000f, 19, stringResource(R.string.unit_ms)) { transitionSpeed = it; saveInt("transitionSpeedMs", it) }
            SliderSetting(stringResource(R.string.settings_slideshow_interval), slideshowInterval, 1000f..30000f, 28, stringResource(R.string.unit_ms)) { slideshowInterval = it; saveInt("slideshowIntervalMs", it) }
            SliderSetting(stringResource(R.string.settings_slideshow_speed), slideshowSpeed, 0f..2000f, 19, stringResource(R.string.unit_ms)) { slideshowSpeed = it; saveInt("slideshowSpeedMs", it) }
            SwitchSetting(stringResource(R.string.settings_random_playback), randomPlayback) { randomPlayback = it; saveBoolean("randomPlayback", it) }
            SwitchSetting(stringResource(R.string.settings_continuous_playback), continuousPlayback) { continuousPlayback = it; saveBoolean("continuousPlayback", it) }
        }
        SettingsSectionCard(stringResource(R.string.settings_section_operation), stringResource(R.string.settings_section_operation_desc)) {
            SwitchSetting(stringResource(R.string.settings_long_press_magnifier), longPressMagnifier) { longPressMagnifier = it; saveBoolean("longPressMagnifier", it) }
            SwitchSetting(stringResource(R.string.settings_double_tap_zoom), doubleTapFastZoom) { doubleTapFastZoom = it; saveBoolean("doubleTapFastZoom", it) }
            SwitchSetting(stringResource(R.string.settings_touch_indicator), touchIndicator) { touchIndicator = it; saveBoolean("touchIndicator", it) }
            SliderSetting(stringResource(R.string.settings_control_panel_hide), controlPanelAutoHideMs, 1000f..10000f, 8, stringResource(R.string.unit_ms)) { controlPanelAutoHideMs = it; saveInt("controlPanelAutoHideMs", it) }
            SliderSetting(stringResource(R.string.settings_magnifier_scale), magnifierScale, 100f..300f, 19, stringResource(R.string.unit_percent)) { magnifierScale = it; saveInt("magnifierScalePercent", it) }
        }
        SettingsSectionCard(stringResource(R.string.settings_section_progress), stringResource(R.string.settings_section_progress_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_display_size), progressDisplayOptions, progressDisplayMode) { progressDisplayMode = it; saveString("progressDisplayMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_mini_progress_style), progressMiniStyleOptions, progressMiniStyle) { progressMiniStyle = it; saveString("progressMiniStyle", it) }
        }
        SettingsSectionCard(stringResource(R.string.settings_section_launch_protection), stringResource(R.string.settings_section_launch_protection_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_startup_screen), startupScreenOptions, startupScreen, columns = 1) { startupScreen = it; saveString("startupScreen", it) }
            SwitchSetting(stringResource(R.string.auth_startup_password), startupPasswordEnabled) { startupPasswordEnabled = it; saveBoolean("startupPasswordEnabled", it) }
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
            SwitchSetting(stringResource(R.string.settings_low_memory_mode), lowMemoryMode) { lowMemoryMode = it; saveBoolean("lowMemoryMode", it) }
        }
    }
}

@Composable
private fun ThemeSettingsSection(
    themeMode: GalleryThemeMode,
    customPalette: GalleryColors?,
    textScale: Float,
    onThemeModeChange: (GalleryThemeMode) -> Unit,
    onCustomPaletteChange: (GalleryColors?) -> Unit,
    onTextScaleChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE) }
    val fallbackPalette = if (themeMode == GalleryThemeMode.LIGHT) GalleryColorTokens.Light else GalleryColorTokens.Dark
    var themePreset by remember { mutableStateOf(prefs.getString(THEME_PRESET_PREF, if (customPalette == null) "DEFAULT" else "CUSTOM") ?: "DEFAULT") }
    var editablePalette by remember(customPalette, themeMode) { mutableStateOf(customPalette ?: fallbackPalette) }

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionCard(stringResource(R.string.settings_display_theme), stringResource(R.string.settings_display_theme_desc)) {
            ThemeModeRow(stringResource(R.string.theme_system), themeMode == GalleryThemeMode.SYSTEM) { onThemeModeChange(GalleryThemeMode.SYSTEM) }
            ThemeModeRow(stringResource(R.string.theme_dark), themeMode == GalleryThemeMode.DARK) { onThemeModeChange(GalleryThemeMode.DARK) }
            ThemeModeRow(stringResource(R.string.theme_light), themeMode == GalleryThemeMode.LIGHT) { onThemeModeChange(GalleryThemeMode.LIGHT) }
        }
        SettingsSectionCard(stringResource(R.string.settings_color_palette), stringResource(R.string.settings_color_palette_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_theme_preset), themePresetOptions, themePreset) { value ->
                applyPalette(value, themePresetPalette(value))
            }
            OutlinedButton(onClick = { applyPalette("DEFAULT", null) }, enabled = customPalette != null) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text(stringResource(R.string.settings_restore_standard_colors), modifier = Modifier.padding(start = 6.dp))
            }
            colorFields.forEach { field ->
                ColorEditRow(
                    label = stringResource(field.labelRes),
                    color = field.get(editablePalette),
                    onColorChange = { parsed -> updatePalette(field.set(editablePalette, parsed)) }
                )
            }
        }
        TextSizeSettingsSection(textScale, onTextScaleChange)
    }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = colors.primaryText, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun ColorEditRow(label: String, color: Color, onColorChange: (Color) -> Unit) {
    val colors = GalleryThemeTokens.colors
    var raw by remember(color) { mutableStateOf(color.toHexString()) }
    val parsed = parseHexColor(raw)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(parsed ?: color)
        )
        OutlinedTextField(
            value = raw,
            onValueChange = { value ->
                raw = value
                parseHexColor(value)?.let(onColorChange)
            },
            label = { Text(label) },
            supportingText = {
                if (parsed == null) Text(stringResource(R.string.settings_hex_hint), color = colors.danger)
            },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BookViewerSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE) }
    var fileSort by remember { mutableStateOf(prefs.getString("fileSort", "NAME_ASC") ?: "NAME_ASC") }
    var showThumbnail by remember { mutableStateOf(prefs.getBoolean("showThumbnail", true)) }
    var smoothFilter by remember { mutableStateOf(prefs.getString("smoothFilter", "BILINEAR") ?: "BILINEAR") }
    var fullscreenMode by remember { mutableStateOf(prefs.getString("fullscreenMode", "DISABLED") ?: "DISABLED") }
    var orientation by remember { mutableStateOf(prefs.getString("orientation", "AUTO") ?: "AUTO") }
    var bindingDirection by remember { mutableStateOf(prefs.getString("bindingDirection", "RIGHT") ?: "RIGHT") }
    var scrollMode by remember { mutableStateOf(prefs.getString("scrollMode", "PAGE") ?: "PAGE") }
    var transitionEffect by remember { mutableStateOf(prefs.getString("transitionEffect", "SLIDE_HORIZONTAL") ?: "SLIDE_HORIZONTAL") }
    var transitionSpeed by remember { mutableIntStateOf(prefs.getInt("transitionSpeedMs", 250)) }
    var slideshowSeconds by remember { mutableIntStateOf(prefs.getInt("slideshowSeconds", 0)) }
    var slideshowSpeed by remember { mutableIntStateOf(prefs.getInt("slideshowSpeedMs", 250)) }
    var menuOpacity by remember { mutableIntStateOf(prefs.getInt("menuOpacityPercent", 0)) }
    var magnifierScale by remember { mutableIntStateOf(prefs.getInt("magnifierScalePercent", 200)) }
    var readMark by remember { mutableStateOf(prefs.getString("readMark", "NONE") ?: "NONE") }
    var showClockBattery by remember { mutableStateOf(prefs.getBoolean("showClockBattery", false)) }
    var tapZoneLayout by remember { mutableStateOf(prefs.getString("tapZoneLayout", "THREE") ?: "THREE") }
    var enableSwipeDeleteBook by remember { mutableStateOf(prefs.getBoolean("enableSwipeDeleteBook", false)) }
    var longPressMagnifier by remember { mutableStateOf(prefs.getBoolean("longPressMagnifier", false)) }
    var doubleTapFastZoom by remember { mutableStateOf(prefs.getBoolean("doubleTapFastZoom", true)) }
    var touchIndicator by remember { mutableStateOf(prefs.getBoolean("touchIndicator", false)) }
    var autoLoadOnLaunch by remember { mutableStateOf(prefs.getBoolean("autoLoadOnLaunch", true)) }

    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionCard(stringResource(R.string.settings_section_bookshelf), stringResource(R.string.settings_section_bookshelf_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_file_sort), fileSortOptions, fileSort) { fileSort = it; saveString("fileSort", it) }
            SwitchSetting(stringResource(R.string.settings_show_thumbnail), showThumbnail) { showThumbnail = it; saveBoolean("showThumbnail", it) }
            SettingsChoiceRow(stringResource(R.string.settings_thumbnail_smoothing), smoothingOptions, smoothFilter) { smoothFilter = it; saveString("smoothFilter", it) }
            SwitchSetting(stringResource(R.string.settings_swipe_delete_book), enableSwipeDeleteBook) { enableSwipeDeleteBook = it; saveBoolean("enableSwipeDeleteBook", it) }
            SwitchSetting(stringResource(R.string.settings_auto_load_on_launch), autoLoadOnLaunch) { autoLoadOnLaunch = it; saveBoolean("autoLoadOnLaunch", it) }
        }
        SettingsSectionCard(stringResource(R.string.settings_section_display), stringResource(R.string.settings_section_display_desc)) {
            SettingsChoiceRow(stringResource(R.string.settings_fullscreen), fullscreenOptions, fullscreenMode) { fullscreenMode = it; saveString("fullscreenMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_orientation), orientationOptions, orientation) { orientation = it; saveString("orientation", it) }
            SettingsChoiceRow(stringResource(R.string.settings_binding_direction), listOf(R.string.opt_right to "RIGHT", R.string.opt_left to "LEFT"), bindingDirection) { bindingDirection = it; saveString("bindingDirection", it) }
            SettingsChoiceRow(stringResource(R.string.settings_viewer_mode), bookScrollModeOptions, scrollMode) { scrollMode = it; saveString("scrollMode", it) }
            SettingsChoiceRow(stringResource(R.string.settings_transition_effect), pageTransitionEffectOptions, transitionEffect) { transitionEffect = it; saveString("transitionEffect", it) }
            SliderSetting(stringResource(R.string.settings_transition_speed), transitionSpeed, 0f..2000f, 19, stringResource(R.string.unit_ms)) { transitionSpeed = it; saveInt("transitionSpeedMs", it) }
            SliderSetting(stringResource(R.string.settings_slideshow_interval), slideshowSeconds, 0f..30f, 29, stringResource(R.string.unit_seconds)) { slideshowSeconds = it; saveInt("slideshowSeconds", it) }
            SliderSetting(stringResource(R.string.settings_slideshow_speed), slideshowSpeed, 0f..2000f, 19, stringResource(R.string.unit_ms)) { slideshowSpeed = it; saveInt("slideshowSpeedMs", it) }
            SliderSetting(stringResource(R.string.settings_menu_opacity), menuOpacity, 0f..100f, 9, stringResource(R.string.unit_percent)) { menuOpacity = it; saveInt("menuOpacityPercent", it) }
            SettingsChoiceRow(stringResource(R.string.settings_screen_allocation), tapZoneLayoutOptions, tapZoneLayout) { tapZoneLayout = it; saveString("tapZoneLayout", it) }
            SettingsChoiceRow(stringResource(R.string.settings_read_mark), readMarkOptions, readMark) { readMark = it; saveString("readMark", it) }
            SwitchSetting(stringResource(R.string.settings_show_clock_battery), showClockBattery) { showClockBattery = it; saveBoolean("showClockBattery", it) }
        }
        SettingsSectionCard(stringResource(R.string.settings_section_operation), stringResource(R.string.settings_section_operation_desc)) {
            SwitchSetting(stringResource(R.string.settings_long_press_magnifier), longPressMagnifier) { longPressMagnifier = it; saveBoolean("longPressMagnifier", it) }
            SwitchSetting(stringResource(R.string.settings_double_tap_zoom), doubleTapFastZoom) { doubleTapFastZoom = it; saveBoolean("doubleTapFastZoom", it) }
            SwitchSetting(stringResource(R.string.settings_touch_indicator), touchIndicator) { touchIndicator = it; saveBoolean("touchIndicator", it) }
            SliderSetting(stringResource(R.string.settings_magnifier_scale), magnifierScale, 100f..300f, 19, stringResource(R.string.unit_percent)) { magnifierScale = it; saveInt("magnifierScalePercent", it) }
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold)
            Text(description, color = colors.secondaryText)
            content()
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: String,
    columns: Int = 2,
    onSelected: (String) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = colors.secondaryText)
        options.chunked(columns.coerceAtLeast(1)).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (labelRes, value) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(labelRes), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    )
                }
                repeat(columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
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
    onValueChange: (Int) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    var draftValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$title ${draftValue.toInt()}$suffix", color = colors.secondaryText)
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
private fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = GalleryThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = colors.secondaryText, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TextSizeSettingsSection(textScale: Float, onTextScaleChange: (Float) -> Unit) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    var draftScale by remember(textScale) { mutableFloatStateOf(textScale) }
    SettingsSectionCard(stringResource(R.string.settings_text_size), stringResource(R.string.settings_text_size_desc)) {
        Text(stringResource(R.string.settings_current_value, (draftScale * 100).toInt()), color = colors.accent, fontWeight = FontWeight.Bold)
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
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_sample_header), color = colors.primaryText, fontSize = textSizes.header, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_sample_body), color = colors.secondaryText, fontSize = textSizes.body)
                Text(stringResource(R.string.settings_sample_label), color = colors.mutedText, fontSize = textSizes.small)
            }
        }
    }
}

@Composable
private fun SettingsDataSection(
    json: String,
    onJsonChange: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = json,
            onValueChange = onJsonChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text(stringResource(R.string.settings_json_label)) },
            minLines = 6,
            maxLines = 8
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport) { Text(stringResource(R.string.btn_save)) }
            Button(onClick = onImport) { Text(stringResource(R.string.btn_load)) }
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
    ColorField(R.string.color_surface_variant, { it.surfaceVariant }, { colors, value -> colors.copy(surfaceVariant = value) }),
    ColorField(R.string.color_top_bar, { it.topBar }, { colors, value -> colors.copy(topBar = value) }),
    ColorField(R.string.color_drawer, { it.drawer }, { colors, value -> colors.copy(drawer = value) }),
    ColorField(R.string.color_card, { it.card }, { colors, value -> colors.copy(card = value) }),
    ColorField(R.string.color_field, { it.field }, { colors, value -> colors.copy(field = value) }),
    ColorField(R.string.color_primary_text, { it.primaryText }, { colors, value -> colors.copy(primaryText = value) }),
    ColorField(R.string.color_secondary_text, { it.secondaryText }, { colors, value -> colors.copy(secondaryText = value) }),
    ColorField(R.string.color_muted_text, { it.mutedText }, { colors, value -> colors.copy(mutedText = value) }),
    ColorField(R.string.color_accent, { it.accent }, { colors, value -> colors.copy(accent = value) }),
    ColorField(R.string.color_accent_soft, { it.accentSoft }, { colors, value -> colors.copy(accentSoft = value) }),
    ColorField(R.string.color_danger, { it.danger }, { colors, value -> colors.copy(danger = value) }),
    ColorField(R.string.color_success, { it.success }, { colors, value -> colors.copy(success = value) }),
    ColorField(R.string.color_divider, { it.divider }, { colors, value -> colors.copy(divider = value) }),
    ColorField(R.string.color_disabled, { it.disabled }, { colors, value -> colors.copy(disabled = value) })
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

private data class ThemePreset(val labelRes: Int, val value: String, val colors: GalleryColors?)

private fun palette(
    background: Long,
    surface: Long,
    surfaceVariant: Long,
    topBar: Long,
    drawer: Long,
    card: Long,
    field: Long,
    primaryText: Long,
    secondaryText: Long,
    mutedText: Long,
    accent: Long,
    accentSoft: Long,
    danger: Long,
    success: Long,
    divider: Long,
    disabled: Long
) = GalleryColors(
    background = Color(background),
    surface = Color(surface),
    surfaceVariant = Color(surfaceVariant),
    topBar = Color(topBar),
    drawer = Color(drawer),
    card = Color(card),
    field = Color(field),
    primaryText = Color(primaryText),
    secondaryText = Color(secondaryText),
    mutedText = Color(mutedText),
    accent = Color(accent),
    accentSoft = Color(accentSoft),
    danger = Color(danger),
    success = Color(success),
    divider = Color(divider),
    disabled = Color(disabled)
)

private val themePresets = listOf(
    ThemePreset(R.string.preset_standard, "DEFAULT", null),
    ThemePreset(R.string.preset_custom, "CUSTOM", null),
    ThemePreset(R.string.preset_midnight, "MIDNIGHT", palette(background = 0xFF101418, surface = 0xFF151B22, surfaceVariant = 0xFF1D2630, topBar = 0xFF0B0F14, drawer = 0xFF101418, card = 0xFF17202A, field = 0xFF1F2B36, primaryText = 0xFFF4F8FC, secondaryText = 0xFFB7C6D5, mutedText = 0xFF8392A3, accent = 0xFF4DA3FF, accentSoft = 0xFF163B5F, danger = 0xFFFF6B7A, success = 0xFF47D18C, divider = 0x33FFFFFF, disabled = 0xFF6E7A86)),
    ThemePreset(R.string.preset_paper, "PAPER", palette(background = 0xFFF4F8FC, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE8F0F8, topBar = 0xFFFFFFFF, drawer = 0xFFFFFFFF, card = 0xFFFFFFFF, field = 0xFFEAF2FA, primaryText = 0xFF101820, secondaryText = 0xFF435466, mutedText = 0xFF6D7C8C, accent = 0xFF006ACB, accentSoft = 0xFFD8ECFF, danger = 0xFFD9364E, success = 0xFF168A52, divider = 0x1F000000, disabled = 0xFF9AA8B6)),
    ThemePreset(R.string.preset_sunrise, "SUNRISE", palette(background = 0xFFFFF7ED, surface = 0xFFFFFBF5, surfaceVariant = 0xFFFFE8D8, topBar = 0xFFFFF1E3, drawer = 0xFFFFF7ED, card = 0xFFFFFFFF, field = 0xFFFFEDE0, primaryText = 0xFF2D1B16, secondaryText = 0xFF6F4A3E, mutedText = 0xFF997264, accent = 0xFFE65F3C, accentSoft = 0xFFFFD6C5, danger = 0xFFD6384D, success = 0xFF2D9A68, divider = 0x24000000, disabled = 0xFFC6A79A)),
    ThemePreset(R.string.preset_sunset, "SUNSET", palette(background = 0xFF171018, surface = 0xFF241525, surfaceVariant = 0xFF332038, topBar = 0xFF100A13, drawer = 0xFF171018, card = 0xFF24182B, field = 0xFF33243A, primaryText = 0xFFFFF2E8, secondaryText = 0xFFE0BBAA, mutedText = 0xFFAA7D75, accent = 0xFFFF8A3D, accentSoft = 0xFF5C2A1D, danger = 0xFFFF6175, success = 0xFF62D58E, divider = 0x33FFFFFF, disabled = 0xFF786273)),
    ThemePreset(R.string.preset_gorgeous, "GORGEOUS", palette(background = 0xFF100B12, surface = 0xFF1A121D, surfaceVariant = 0xFF2A1C31, topBar = 0xFF09060B, drawer = 0xFF130D17, card = 0xFF201625, field = 0xFF2E2234, primaryText = 0xFFFFF7E2, secondaryText = 0xFFE8D2A0, mutedText = 0xFFB19B69, accent = 0xFFFFC857, accentSoft = 0xFF4D3915, danger = 0xFFFF6678, success = 0xFF5DDD9B, divider = 0x40FFDFA3, disabled = 0xFF82745E)),
    ThemePreset(R.string.preset_calm, "CALM", palette(background = 0xFF121715, surface = 0xFF17201D, surfaceVariant = 0xFF22302B, topBar = 0xFF0D1210, drawer = 0xFF121715, card = 0xFF19231F, field = 0xFF24332E, primaryText = 0xFFEFF8F2, secondaryText = 0xFFC3D5CA, mutedText = 0xFF8FA69A, accent = 0xFF8FD19E, accentSoft = 0xFF254932, danger = 0xFFFF7480, success = 0xFF67D695, divider = 0x2EFFFFFF, disabled = 0xFF6E8277)),
    ThemePreset(R.string.preset_spring, "SPRING", palette(background = 0xFFFFF7FB, surface = 0xFFFFFFFF, surfaceVariant = 0xFFFFE4EF, topBar = 0xFFFFF0F7, drawer = 0xFFFFF7FB, card = 0xFFFFFFFF, field = 0xFFFFEAF2, primaryText = 0xFF25151D, secondaryText = 0xFF69485A, mutedText = 0xFF967083, accent = 0xFFE85D93, accentSoft = 0xFFFFD6E8, danger = 0xFFD83A55, success = 0xFF2BA86B, divider = 0x22000000, disabled = 0xFFC5A4B3)),
    ThemePreset(R.string.preset_summer, "SUMMER", palette(background = 0xFFF1FBFF, surface = 0xFFFFFFFF, surfaceVariant = 0xFFDDF6FF, topBar = 0xFFEAFBFF, drawer = 0xFFF1FBFF, card = 0xFFFFFFFF, field = 0xFFE0F7FF, primaryText = 0xFF0C2430, secondaryText = 0xFF315D6E, mutedText = 0xFF688C98, accent = 0xFF00A7C8, accentSoft = 0xFFC9F4FF, danger = 0xFFD93A4E, success = 0xFF149B70, divider = 0x20000000, disabled = 0xFF9BB9C3)),
    ThemePreset(R.string.preset_winter, "WINTER", palette(background = 0xFF0E141B, surface = 0xFF141D27, surfaceVariant = 0xFF1D2B39, topBar = 0xFF080D13, drawer = 0xFF0E141B, card = 0xFF15202B, field = 0xFF223140, primaryText = 0xFFF2FAFF, secondaryText = 0xFFC5D8E6, mutedText = 0xFF8FA8B9, accent = 0xFF8ED8FF, accentSoft = 0xFF1D4560, danger = 0xFFFF7180, success = 0xFF64D69A, divider = 0x33FFFFFF, disabled = 0xFF708290)),
    ThemePreset(R.string.preset_forest, "FOREST", palette(background = 0xFF0F1711, surface = 0xFF172119, surfaceVariant = 0xFF223325, topBar = 0xFF09100B, drawer = 0xFF0F1711, card = 0xFF16231A, field = 0xFF243528, primaryText = 0xFFF0F8EF, secondaryText = 0xFFC6D8C1, mutedText = 0xFF8EA184, accent = 0xFF7ACB6A, accentSoft = 0xFF294B25, danger = 0xFFFF746F, success = 0xFF6FDA83, divider = 0x2EFFFFFF, disabled = 0xFF6F806D)),
    ThemePreset(R.string.preset_neon, "NEON", palette(background = 0xFF090B12, surface = 0xFF10131F, surfaceVariant = 0xFF1A1F35, topBar = 0xFF05070D, drawer = 0xFF090B12, card = 0xFF111528, field = 0xFF1C2340, primaryText = 0xFFF1FFF9, secondaryText = 0xFFB8F7E6, mutedText = 0xFF7BCBB7, accent = 0xFF49F2C2, accentSoft = 0xFF123F39, danger = 0xFFFF4D8A, success = 0xFF6DFF85, divider = 0x4049F2C2, disabled = 0xFF586D7A)),
    ThemePreset(R.string.preset_mono, "MONO", palette(background = 0xFF111315, surface = 0xFF191C1F, surfaceVariant = 0xFF25292D, topBar = 0xFF090A0B, drawer = 0xFF111315, card = 0xFF1C2024, field = 0xFF2A2E33, primaryText = 0xFFF2F4F6, secondaryText = 0xFFC6CBD0, mutedText = 0xFF90979F, accent = 0xFFE6EAF0, accentSoft = 0xFF30353C, danger = 0xFFFF6B76, success = 0xFF7FD39A, divider = 0x33FFFFFF, disabled = 0xFF767D84)),
    ThemePreset(R.string.preset_sakura_mist, "SAKURA_MIST", palette(background = 0xFFFFF4F8, surface = 0xFFFFFBFD, surfaceVariant = 0xFFFFE2EC, topBar = 0xFFFFEDF5, drawer = 0xFFFFF4F8, card = 0xFFFFFFFF, field = 0xFFFFE8F0, primaryText = 0xFF2D1821, secondaryText = 0xFF704B5A, mutedText = 0xFF9B7482, accent = 0xFFD95F8D, accentSoft = 0xFFFFD9E8, danger = 0xFFD94359, success = 0xFF2F9E71, divider = 0x22000000, disabled = 0xFFC8A5B3)),
    ThemePreset(R.string.preset_fresh_leaf, "FRESH_LEAF", palette(background = 0xFFF5FFF2, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE3F8DF, topBar = 0xFFF0FFEC, drawer = 0xFFF5FFF2, card = 0xFFFFFFFF, field = 0xFFE8F8E4, primaryText = 0xFF142314, secondaryText = 0xFF3F633F, mutedText = 0xFF6F916D, accent = 0xFF3FA35B, accentSoft = 0xFFD8F5DD, danger = 0xFFD94050, success = 0xFF1D9B5A, divider = 0x20000000, disabled = 0xFFA5BFA2)),
    ThemePreset(R.string.preset_deep_sea, "DEEP_SEA", palette(background = 0xFF071218, surface = 0xFF0D1D25, surfaceVariant = 0xFF15313D, topBar = 0xFF040A0E, drawer = 0xFF071218, card = 0xFF0E222B, field = 0xFF173542, primaryText = 0xFFE9FCFF, secondaryText = 0xFFB5DCE3, mutedText = 0xFF78A7B2, accent = 0xFF38C6D9, accentSoft = 0xFF123B45, danger = 0xFFFF6576, success = 0xFF58D899, divider = 0x3338C6D9, disabled = 0xFF5C7880)),
    ThemePreset(R.string.preset_starry, "STARRY", palette(background = 0xFF080A18, surface = 0xFF11142A, surfaceVariant = 0xFF1B2142, topBar = 0xFF050611, drawer = 0xFF080A18, card = 0xFF12162E, field = 0xFF20264A, primaryText = 0xFFF3F6FF, secondaryText = 0xFFC8D2FF, mutedText = 0xFF8C9AD6, accent = 0xFFB7C7FF, accentSoft = 0xFF29325F, danger = 0xFFFF6B82, success = 0xFF6CE0A2, divider = 0x33FFFFFF, disabled = 0xFF677095)),
    ThemePreset(R.string.preset_coffee, "COFFEE", palette(background = 0xFF15100C, surface = 0xFF211811, surfaceVariant = 0xFF302419, topBar = 0xFF0C0806, drawer = 0xFF15100C, card = 0xFF241B13, field = 0xFF34271C, primaryText = 0xFFFFF4E7, secondaryText = 0xFFE2C4A0, mutedText = 0xFFA88663, accent = 0xFFD7A86E, accentSoft = 0xFF4B3520, danger = 0xFFFF6B70, success = 0xFF75D18E, divider = 0x35FFE0B8, disabled = 0xFF806C58)),
    ThemePreset(R.string.preset_jade, "JADE", palette(background = 0xFF071410, surface = 0xFF10231D, surfaceVariant = 0xFF18362D, topBar = 0xFF040D0A, drawer = 0xFF071410, card = 0xFF112820, field = 0xFF1B3B31, primaryText = 0xFFE9FFF6, secondaryText = 0xFFB9E6D4, mutedText = 0xFF7FB69E, accent = 0xFF59D6A3, accentSoft = 0xFF184934, danger = 0xFFFF6776, success = 0xFF73E28F, divider = 0x3359D6A3, disabled = 0xFF5E7D70)),
    ThemePreset(R.string.preset_grape, "GRAPE", palette(background = 0xFF150C18, surface = 0xFF211126, surfaceVariant = 0xFF321A39, topBar = 0xFF0C070F, drawer = 0xFF150C18, card = 0xFF25142B, field = 0xFF382141, primaryText = 0xFFFFF0FF, secondaryText = 0xFFE0B8ED, mutedText = 0xFFA777B5, accent = 0xFFD083FF, accentSoft = 0xFF442052, danger = 0xFFFF657C, success = 0xFF68D99A, divider = 0x35FFFFFF, disabled = 0xFF765781)),
    ThemePreset(R.string.preset_porcelain, "PORCELAIN", palette(background = 0xFFF8FAFC, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE8EEF5, topBar = 0xFFFFFFFF, drawer = 0xFFF8FAFC, card = 0xFFFFFFFF, field = 0xFFEAF0F6, primaryText = 0xFF17202A, secondaryText = 0xFF4B5E72, mutedText = 0xFF72839A, accent = 0xFF527AA3, accentSoft = 0xFFDCEAF7, danger = 0xFFD83E50, success = 0xFF218F62, divider = 0x1F000000, disabled = 0xFFA5B2BF)),
    ThemePreset(R.string.preset_autumn_leaf, "AUTUMN_LEAF", palette(background = 0xFFFFF8F0, surface = 0xFFFFFCF8, surfaceVariant = 0xFFFFE7D4, topBar = 0xFFFFF0E3, drawer = 0xFFFFF8F0, card = 0xFFFFFFFF, field = 0xFFFFEBDD, primaryText = 0xFF2B1A11, secondaryText = 0xFF6B4936, mutedText = 0xFF96705C, accent = 0xFFC65A2E, accentSoft = 0xFFFFDDC8, danger = 0xFFD33E4E, success = 0xFF2C955F, divider = 0x22000000, disabled = 0xFFC2A28F)),
    ThemePreset(R.string.preset_jewel, "JEWEL", palette(background = 0xFF0B0A12, surface = 0xFF151326, surfaceVariant = 0xFF242044, topBar = 0xFF05050B, drawer = 0xFF0B0A12, card = 0xFF17152C, field = 0xFF29234A, primaryText = 0xFFFFF4FF, secondaryText = 0xFFE2C4FF, mutedText = 0xFFA98CCE, accent = 0xFFFF4FD8, accentSoft = 0xFF4D1E55, danger = 0xFFFF667B, success = 0xFF65E2A0, divider = 0x33FFFFFF, disabled = 0xFF72638C))
)

private val themePresetOptions = themePresets.map { it.labelRes to it.value }
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

private fun themePresetPalette(value: String): GalleryColors? = themePresets.firstOrNull { it.value == value }?.colors

private fun Color.toHexString(): String {
    val argb = toArgb()
    return "#%08X".format(argb)
}

private fun parseHexColor(raw: String): Color? {
    val value = raw.trim().removePrefix("#")
    if (value.length != 6 && value.length != 8) return null
    return runCatching {
        val argb = if (value.length == 6) {
            "FF$value".toLong(16)
        } else {
            value.toLong(16)
        }
        Color(argb and 0xFFFFFFFFL)
    }.getOrNull()
}

private fun exportAllSettingsJson(context: Context): String {
    val json = JSONObject()
    json.put("global", prefsToJson(context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE)))
    json.put("theme", prefsToJson(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)))
    json.put("bookViewer", prefsToJson(context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)))
    return json.toString(2)
}

private fun importAllSettingsJson(context: Context, raw: String) {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
    if (json.has("global") || json.has("theme") || json.has("bookViewer")) {
        json.optJSONObject("global")?.let {
            importPrefsJson(context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE), it)
        }
        json.optJSONObject("theme")?.let {
            importPrefsJson(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE), it)
        }
        json.optJSONObject("bookViewer")?.let {
            importPrefsJson(context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE), it)
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
    val mode = runCatching {
        GalleryThemeMode.valueOf(prefs.getString(THEME_MODE_PREF, GalleryThemeMode.SYSTEM.name) ?: GalleryThemeMode.SYSTEM.name)
    }.getOrDefault(GalleryThemeMode.SYSTEM)
    onThemeModeChange(mode)
    onTextScaleChange(prefs.getFloat(TEXT_SCALE_PREF, 1f).coerceIn(0.75f, 1.45f))
    onCustomPaletteChange(loadCustomPaletteFromPrefs(context, prefs))
}

private fun loadCustomPaletteFromPrefs(context: Context, prefs: SharedPreferences): GalleryColors? {
    if (!prefs.getBoolean(CUSTOM_PALETTE_ENABLED_PREF, false)) return null

    fun color(key: String, resId: Int): Color =
        Color(prefs.getInt(CUSTOM_PALETTE_PREFIX + key, context.getColor(resId)).toLong() and 0xFFFFFFFFL)

    return GalleryColors(
        background = color("background", R.color.bg_dark),
        surface = color("surface", R.color.surface_dark),
        surfaceVariant = color("surfaceVariant", R.color.surface_variant_dark),
        topBar = color("topBar", R.color.top_bar_dark),
        drawer = color("drawer", R.color.drawer_dark),
        card = color("card", R.color.card_dark),
        field = color("field", R.color.field_dark),
        primaryText = color("primaryText", R.color.primary_text_dark),
        secondaryText = color("secondaryText", R.color.secondary_text_dark),
        mutedText = color("mutedText", R.color.muted_text_dark),
        accent = color("accent", R.color.accent_dark),
        accentSoft = color("accentSoft", R.color.accent_soft_dark),
        danger = color("danger", R.color.danger_dark),
        success = color("success", R.color.success_dark),
        divider = color("divider", R.color.divider_dark),
        disabled = color("disabled", R.color.disabled_dark)
    )
}

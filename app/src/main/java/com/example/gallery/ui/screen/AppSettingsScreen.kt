package com.example.gallery.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppDefaults
import com.example.gallery.ui.AppText
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryColorTokens
import com.example.gallery.ui.theme.GalleryColors
import com.example.gallery.ui.theme.GalleryThemeMode
import com.example.gallery.ui.theme.GalleryThemeTokens
import org.json.JSONObject

private const val GLOBAL_SETTINGS_PREFS = "global_settings"
private const val BOOK_VIEWER_PREFS = "book_viewer_settings"

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
        SettingsPage.MENU -> AppText.SETTINGS
        SettingsPage.GLOBAL -> "全体設定"
        SettingsPage.THEME -> "テーマ"
        SettingsPage.BOOK_VIEWER -> "ブックビューア"
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
                navigationContentDescription = "戻る",
                onNavigationClick = ::handleBack,
                containerColor = colors.topBar,
                contentColor = colors.primaryText,
                actions = {
                    IconButton(onClick = { pendingResetPage = currentPage }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "デフォルトに戻す", tint = colors.primaryText)
                    }
                }
            )
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(colors.background).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                key(currentPage, resetToken) {
                    when (currentPage) {
                        SettingsPage.MENU -> SettingsMenu(
                            onGlobalClick = { currentPage = SettingsPage.GLOBAL },
                            onThemeClick = { currentPage = SettingsPage.THEME },
                            onBookViewerClick = { currentPage = SettingsPage.BOOK_VIEWER }
                        )
                        SettingsPage.GLOBAL -> GlobalSettingsSection()
                        SettingsPage.THEME -> ThemeSettingsSection(themeMode, customPalette, textScale, onThemeModeChange, onCustomPaletteChange, onTextScaleChange)
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
            title = { Text("デフォルトに戻しますか？", color = colors.primaryText) },
            text = { Text("この画面の設定を初期値に戻します。", color = colors.secondaryText) },
            confirmButton = {
                Button(onClick = {
                    resetSettingsPage(context, page, onThemeModeChange, onCustomPaletteChange, onTextScaleChange)
                    pendingResetPage = null
                    resetToken++
                }) {
                    Text("戻す")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetPage = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun SettingsMenu(onGlobalClick: () -> Unit, onThemeClick: () -> Unit, onBookViewerClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsMenuItem(Icons.Default.SettingsSuggest, "全体設定", "ビューア、起動、パスワード、操作の設定です。", onGlobalClick)
        SettingsMenuItem(Icons.Default.Palette, "テーマ", "表示テーマ、カラーパレット、文字サイズを設定します。", onThemeClick)
        SettingsMenuItem(Icons.AutoMirrored.Filled.MenuBook, "ブックビューア", "本の表示、操作、読書補助の設定です。", onBookViewerClick)
    }
}

@Composable
private fun SettingsMenuItem(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    Surface(color = colors.card, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.divider), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold)
                Text(description, color = colors.secondaryText)
            }
            Text("開く", color = colors.accent)
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
            onThemeModeChange(GalleryThemeMode.SYSTEM)
            onCustomPaletteChange(null)
            onTextScaleChange(1f)
        }
        SettingsPage.GLOBAL -> context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        SettingsPage.THEME -> {
            context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE).edit().remove("themePreset").apply()
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
    var settingsJson by remember { mutableStateOf(exportSettingsJson(prefs)) }

    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionCard("ビューア表示", "表示、全画面、画面の向き、メニュー透明度を設定します。") {
            SettingsChoiceRow("画像平滑化", smoothingOptions, smoothing) { smoothing = it; saveString("smoothing", it) }
            SettingsChoiceRow("全画面表示", fullscreenOptions, fullscreenMode) { fullscreenMode = it; saveString("fullscreenMode", it) }
            SettingsChoiceRow("画面の向き", orientationOptions, orientation) { orientation = it; saveString("orientation", it) }
            SliderSetting("メニュー透明度", menuOpacity, 0f..100f, 9, "%") { menuOpacity = it; saveInt("menuOpacityPercent", it) }
        }
        SettingsSectionCard("再生", "ページ遷移、スライドショー、ランダム再生を設定します。") {
            SliderSetting("ページ遷移のスピード", transitionSpeed, 0f..2000f, 19, "ms") { transitionSpeed = it; saveInt("transitionSpeedMs", it) }
            SliderSetting("スライドショー切り替え時間", slideshowInterval, 1000f..30000f, 28, "ms") { slideshowInterval = it; saveInt("slideshowIntervalMs", it) }
            SliderSetting("切り替えスピード", slideshowSpeed, 0f..2000f, 19, "ms") { slideshowSpeed = it; saveInt("slideshowSpeedMs", it) }
            SwitchSetting("ランダム再生", randomPlayback) { randomPlayback = it; saveBoolean("randomPlayback", it) }
            SwitchSetting("連続再生", continuousPlayback) { continuousPlayback = it; saveBoolean("continuousPlayback", it) }
        }
        SettingsSectionCard("操作", "長押し、ダブルタップ、タッチ表示、自動非表示を設定します。") {
            SwitchSetting("長押し拡大鏡", longPressMagnifier) { longPressMagnifier = it; saveBoolean("longPressMagnifier", it) }
            SwitchSetting("ダブルタップ高速ズーム", doubleTapFastZoom) { doubleTapFastZoom = it; saveBoolean("doubleTapFastZoom", it) }
            SwitchSetting("タッチインジケーター", touchIndicator) { touchIndicator = it; saveBoolean("touchIndicator", it) }
            SliderSetting("コントロール自動非表示", controlPanelAutoHideMs, 1000f..10000f, 8, "ms") { controlPanelAutoHideMs = it; saveInt("controlPanelAutoHideMs", it) }
            SliderSetting("拡大鏡の拡大率", magnifierScale, 100f..300f, 19, "%") { magnifierScale = it; saveInt("magnifierScalePercent", it) }
        }
        SettingsSectionCard("進捗表示", "読み込みや解析中の進捗表示を設定します。") {
            SettingsChoiceRow("表示サイズ", progressDisplayOptions, progressDisplayMode) { progressDisplayMode = it; saveString("progressDisplayMode", it) }
            SettingsChoiceRow("最小表示の形", progressMiniStyleOptions, progressMiniStyle) { progressMiniStyle = it; saveString("progressMiniStyle", it) }
        }
        SettingsSectionCard("起動と保護", "起動時の画面、パスワード、省メモリモードを設定します。") {
            SettingsChoiceRow("起動時の画面", startupScreenOptions, startupScreen) { startupScreen = it; saveString("startupScreen", it) }
            SwitchSetting("起動パスワード", startupPasswordEnabled) { startupPasswordEnabled = it; saveBoolean("startupPasswordEnabled", it) }
            OutlinedTextField(
                value = password,
                onValueChange = {
                    val digits = it.filter(Char::isDigit)
                    password = digits
                    saveString("startupPassword", digits)
                },
                label = { Text("パスワード") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            SwitchSetting("省メモリモード", lowMemoryMode) { lowMemoryMode = it; saveBoolean("lowMemoryMode", it) }
        }
        SettingsSectionCard("設定データ", "設定をJSONとして保存・読み込みします。") {
            SettingsDataSection(settingsJson, { settingsJson = it }, { settingsJson = exportSettingsJson(prefs) }) {
                importSettingsJson(prefs, settingsJson)
                settingsJson = exportSettingsJson(prefs)
            }
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
    val prefs = remember { context.getSharedPreferences(GLOBAL_SETTINGS_PREFS, Context.MODE_PRIVATE) }
    var themePreset by remember { mutableStateOf(prefs.getString("themePreset", "DEFAULT") ?: "DEFAULT") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSectionCard("表示テーマ", "システム、ダーク、ライトを切り替えます。") {
            ThemeModeRow("システム", themeMode == GalleryThemeMode.SYSTEM) { onThemeModeChange(GalleryThemeMode.SYSTEM) }
            ThemeModeRow("ダーク", themeMode == GalleryThemeMode.DARK) { onThemeModeChange(GalleryThemeMode.DARK) }
            ThemeModeRow("ライト", themeMode == GalleryThemeMode.LIGHT) { onThemeModeChange(GalleryThemeMode.LIGHT) }
        }
        SettingsSectionCard("カラーパレット", "用意されたテーマプリセットを適用します。") {
            SettingsChoiceRow("テーマプリセット", themePresetOptions, themePreset) { value ->
                themePreset = value
                prefs.edit().putString("themePreset", value).apply()
                onCustomPaletteChange(themePresetPalette(value))
            }
            OutlinedButton(onClick = { onCustomPaletteChange(null) }, enabled = customPalette != null) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text("リセット", modifier = Modifier.padding(start = 6.dp))
            }
        }
        TextSizeSettingsSection(textScale, onTextScaleChange)
    }
}

@Composable
private fun ThemeModeRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = GalleryThemeTokens.colors
    Card(colors = CardDefaults.cardColors(containerColor = if (selected) colors.accentSoft else colors.card), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.primaryText, modifier = Modifier.weight(1f))
            RadioButton(selected = selected, onClick = onClick)
        }
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
        SettingsSectionCard("本棚", "本一覧とサムネイルの設定です。") {
            SettingsChoiceRow("ファイルの並び替え", fileSortOptions, fileSort) { fileSort = it; saveString("fileSort", it) }
            SwitchSetting("サムネイルを表示", showThumbnail) { showThumbnail = it; saveBoolean("showThumbnail", it) }
            SettingsChoiceRow("サムネイルの画像平滑化", smoothingOptions, smoothFilter) { smoothFilter = it; saveString("smoothFilter", it) }
            SwitchSetting("スワイプで本を削除", enableSwipeDeleteBook) { enableSwipeDeleteBook = it; saveBoolean("enableSwipeDeleteBook", it) }
            SwitchSetting("起動時のファイル自動読み込み", autoLoadOnLaunch) { autoLoadOnLaunch = it; saveBoolean("autoLoadOnLaunch", it) }
        }
        SettingsSectionCard("表示", "ブックビューアの表示とページ移動を設定します。") {
            SettingsChoiceRow("全画面表示", fullscreenOptions, fullscreenMode) { fullscreenMode = it; saveString("fullscreenMode", it) }
            SettingsChoiceRow("画面の向き", orientationOptions, orientation) { orientation = it; saveString("orientation", it) }
            SettingsChoiceRow("綴じ方向", listOf("右" to "RIGHT", "左" to "LEFT"), bindingDirection) { bindingDirection = it; saveString("bindingDirection", it) }
            SettingsChoiceRow("ビューアモード", bookScrollModeOptions, scrollMode) { scrollMode = it; saveString("scrollMode", it) }
            SettingsChoiceRow("ページ遷移の効果", pageTransitionEffectOptions, transitionEffect) { transitionEffect = it; saveString("transitionEffect", it) }
            SliderSetting("ページ遷移のスピード", transitionSpeed, 0f..2000f, 19, "ms") { transitionSpeed = it; saveInt("transitionSpeedMs", it) }
            SliderSetting("スライドショー切り替え時間", slideshowSeconds, 0f..30f, 29, "秒") { slideshowSeconds = it; saveInt("slideshowSeconds", it) }
            SliderSetting("切り替えスピード", slideshowSpeed, 0f..2000f, 19, "ms") { slideshowSpeed = it; saveInt("slideshowSpeedMs", it) }
            SliderSetting("メニュー透明度", menuOpacity, 0f..100f, 9, "%") { menuOpacity = it; saveInt("menuOpacityPercent", it) }
            SettingsChoiceRow("スクリーン割り当て", tapZoneLayoutOptions, tapZoneLayout) { tapZoneLayout = it; saveString("tapZoneLayout", it) }
            SettingsChoiceRow("既読マーク", readMarkOptions, readMark) { readMark = it; saveString("readMark", it) }
            SwitchSetting("時計とバッテリー残量を表示", showClockBattery) { showClockBattery = it; saveBoolean("showClockBattery", it) }
        }
        SettingsSectionCard("操作", "長押し、ダブルタップ、タッチフィードバックを設定します。") {
            SwitchSetting("長押し拡大鏡", longPressMagnifier) { longPressMagnifier = it; saveBoolean("longPressMagnifier", it) }
            SwitchSetting("ダブルタップ高速ズーム", doubleTapFastZoom) { doubleTapFastZoom = it; saveBoolean("doubleTapFastZoom", it) }
            SwitchSetting("タッチインジケーター", touchIndicator) { touchIndicator = it; saveBoolean("touchIndicator", it) }
            SliderSetting("拡大鏡の拡大率", magnifierScale, 100f..300f, 19, "%") { magnifierScale = it; saveInt("magnifierScalePercent", it) }
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = GalleryThemeTokens.colors
    Surface(color = colors.card, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.divider), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = colors.primaryText, fontWeight = FontWeight.Bold)
            Text(description, color = colors.secondaryText)
            content()
        }
    }
}

@Composable
private fun SettingsChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    val colors = GalleryThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = colors.secondaryText)
        options.chunked(2).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (label, value) ->
                    FilterChip(selected = selected == value, onClick = { onSelected(value) }, modifier = Modifier.weight(1f), label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) })
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int, suffix: String, onValueChange: (Int) -> Unit) {
    val colors = GalleryThemeTokens.colors
    var draftValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$title ${draftValue.toInt()}$suffix", color = colors.secondaryText)
        Slider(value = draftValue, onValueChange = { draftValue = it; onValueChange(it.toInt()) }, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = GalleryThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.secondaryText, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TextSizeSettingsSection(textScale: Float, onTextScaleChange: (Float) -> Unit) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    var draftScale by remember(textScale) { mutableFloatStateOf(textScale) }
    SettingsSectionCard("文字サイズ", "アプリ全体の文字サイズを調整します。") {
        Text("現在: ${(draftScale * 100).toInt()}%", color = colors.accent, fontWeight = FontWeight.Bold)
        Slider(value = draftScale, onValueChange = { draftScale = it; onTextScaleChange(it) }, valueRange = 0.75f..1.45f, steps = 13)
        Surface(color = colors.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.divider), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("見出しサンプル", color = colors.primaryText, fontSize = textSizes.header, fontWeight = FontWeight.Bold)
                Text("本文サンプル。選択した値がアプリ内の文字に反映されます。", color = colors.secondaryText, fontSize = textSizes.body)
                Text("補足 / ラベル", color = colors.mutedText, fontSize = textSizes.small)
            }
        }
    }
}

@Composable
private fun SettingsDataSection(json: String, onJsonChange: (String) -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = json, onValueChange = onJsonChange, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("設定JSON") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport) { Text("保存") }
            Button(onClick = onImport) { Text("読み込み") }
        }
    }
}

private val smoothingOptions = listOf("最近傍法" to "NEAREST", "平均画素法" to "AVERAGING", "バイリニア補間" to "BILINEAR", "バイキュービック補間" to "BICUBIC", "Lanczos3" to "LANCZOS3")
private val fullscreenOptions = listOf("無効" to "DISABLED", "全画面表示" to "FULLSCREEN", "ステータスバーを隠す" to "HIDE_STATUS_BAR", "ナビゲーションバーを非表示" to "HIDE_NAV_BAR")
private val orientationOptions = listOf("自動回転" to "AUTO", "縦" to "PORTRAIT", "横" to "LANDSCAPE", "縦の逆" to "REVERSE_PORTRAIT", "横の逆" to "REVERSE_LANDSCAPE")
private val bookScrollModeOptions = listOf("ページ" to "PAGE", "縦スクロール" to "VERTICAL", "横スクロール" to "HORIZONTAL")
private val pageTransitionEffectOptions = listOf("なし" to "NONE", "ページめくり" to "PAGE_CURL", "スライド(左右)" to "SLIDE_HORIZONTAL", "スライド(上下)" to "SLIDE_VERTICAL", "カバー" to "COVER", "フェードイン" to "FADE")
private val readMarkOptions = listOf("なし" to "NONE", "アイコン" to "ICON", "ページ" to "PAGE", "パーセント" to "PERCENT", "プログレスバー" to "PROGRESS_BAR")
private val fileSortOptions = listOf("名前 昇順" to "NAME_ASC", "名前 降順" to "NAME_DESC", "新しい順" to "DATE_DESC", "古い順" to "DATE_ASC")
private val tapZoneLayoutOptions = listOf("3分割" to "THREE", "4分割" to "FOUR", "11分割" to "ELEVEN")
private val progressDisplayOptions = listOf("最大表示" to "MAX", "最小表示" to "MIN")
private val progressMiniStyleOptions = listOf("バー" to "BAR", "円" to "CIRCLE")

private data class ThemePreset(val label: String, val value: String, val colors: GalleryColors?)

private val themePresets = listOf(
    ThemePreset("標準", "DEFAULT", null),
    ThemePreset("ミッドナイト", "MIDNIGHT", GalleryColorTokens.Dark),
    ThemePreset("ペーパー", "PAPER", GalleryColorTokens.Light),
    ThemePreset("朝焼け", "SUNRISE", GalleryColorTokens.Light.copy(background = Color(0xFFFFF7ED), surface = Color(0xFFFFFBF5), accent = Color(0xFFE65F3C), accentSoft = Color(0xFFFFD6C5))),
    ThemePreset("夕焼け", "SUNSET", GalleryColorTokens.Dark.copy(background = Color(0xFF171018), surface = Color(0xFF241525), accent = Color(0xFFFF8A3D), accentSoft = Color(0xFF5C2A1D))),
    ThemePreset("ゴージャス", "GORGEOUS", GalleryColorTokens.Dark.copy(background = Color(0xFF100B12), surface = Color(0xFF1A121D), accent = Color(0xFFFFC857), accentSoft = Color(0xFF4D3915))),
    ThemePreset("落ち着く", "CALM", GalleryColorTokens.Dark.copy(background = Color(0xFF121715), surface = Color(0xFF17201D), accent = Color(0xFF8FD19E), accentSoft = Color(0xFF254932))),
    ThemePreset("春", "SPRING", GalleryColorTokens.Light.copy(background = Color(0xFFFFF7FB), surface = Color(0xFFFFFFFF), accent = Color(0xFFE85D93), accentSoft = Color(0xFFFFD6E8))),
    ThemePreset("夏", "SUMMER", GalleryColorTokens.Light.copy(background = Color(0xFFF1FBFF), surface = Color(0xFFFFFFFF), accent = Color(0xFF00A7C8), accentSoft = Color(0xFFC9F4FF))),
    ThemePreset("冬", "WINTER", GalleryColorTokens.Dark.copy(background = Color(0xFF0E141B), surface = Color(0xFF141D27), accent = Color(0xFF8ED8FF), accentSoft = Color(0xFF1D4560))),
    ThemePreset("森", "FOREST", GalleryColorTokens.Dark.copy(background = Color(0xFF0F1711), surface = Color(0xFF172119), accent = Color(0xFF7ACB6A), accentSoft = Color(0xFF294B25))),
    ThemePreset("ネオン", "NEON", GalleryColorTokens.Dark.copy(background = Color(0xFF090B12), surface = Color(0xFF10131F), accent = Color(0xFF49F2C2), accentSoft = Color(0xFF123F39))),
    ThemePreset("モノクロ", "MONO", GalleryColorTokens.Dark.copy(accent = Color(0xFFE6EAF0), accentSoft = Color(0xFF30353C))),
    ThemePreset("桜霞", "SAKURA_MIST", GalleryColorTokens.Light.copy(background = Color(0xFFFFF4F8), surface = Color(0xFFFFFBFD), accent = Color(0xFFD95F8D), accentSoft = Color(0xFFFFD9E8))),
    ThemePreset("若葉", "FRESH_LEAF", GalleryColorTokens.Light.copy(background = Color(0xFFF5FFF2), surface = Color(0xFFFFFFFF), accent = Color(0xFF3FA35B), accentSoft = Color(0xFFD8F5DD))),
    ThemePreset("深海", "DEEP_SEA", GalleryColorTokens.Dark.copy(background = Color(0xFF071218), surface = Color(0xFF0D1D25), accent = Color(0xFF38C6D9), accentSoft = Color(0xFF123B45))),
    ThemePreset("星空", "STARRY", GalleryColorTokens.Dark.copy(background = Color(0xFF080A18), surface = Color(0xFF11142A), accent = Color(0xFFB7C7FF), accentSoft = Color(0xFF29325F))),
    ThemePreset("コーヒー", "COFFEE", GalleryColorTokens.Dark.copy(background = Color(0xFF15100C), surface = Color(0xFF211811), accent = Color(0xFFD7A86E), accentSoft = Color(0xFF4B3520))),
    ThemePreset("翡翠", "JADE", GalleryColorTokens.Dark.copy(background = Color(0xFF071410), surface = Color(0xFF10231D), accent = Color(0xFF59D6A3), accentSoft = Color(0xFF184934))),
    ThemePreset("葡萄", "GRAPE", GalleryColorTokens.Dark.copy(background = Color(0xFF150C18), surface = Color(0xFF211126), accent = Color(0xFFD083FF), accentSoft = Color(0xFF442052))),
    ThemePreset("磁器", "PORCELAIN", GalleryColorTokens.Light.copy(background = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF), accent = Color(0xFF527AA3), accentSoft = Color(0xFFDCEAF7))),
    ThemePreset("紅葉", "AUTUMN_LEAF", GalleryColorTokens.Light.copy(background = Color(0xFFFFF8F0), surface = Color(0xFFFFFCF8), accent = Color(0xFFC65A2E), accentSoft = Color(0xFFFFDDC8))),
    ThemePreset("宝石", "JEWEL", GalleryColorTokens.Dark.copy(background = Color(0xFF0B0A12), surface = Color(0xFF151326), accent = Color(0xFFFF4FD8), accentSoft = Color(0xFF4D1E55)))
)

private val themePresetOptions = themePresets.map { it.label to it.value }
private val startupScreenOptions = listOf("ホーム" to "home", "フォルダ" to "folders", "動画" to "videos", "本" to "books", "お絵描き資料" to "references", "お気に入りクリエイター" to "favorite_artists", "お気に入りサイト" to "favorite_sites", "本のしおり" to "book_bookmarks", "動画DL" to "video_downloader", "設定" to "settings", "このアプリについて" to "about")

private fun themePresetPalette(value: String): GalleryColors? = themePresets.firstOrNull { it.value == value }?.colors

private fun exportSettingsJson(prefs: android.content.SharedPreferences): String {
    val json = JSONObject()
    prefs.all.toSortedMap().forEach { (key, value) ->
        when (value) {
            is Boolean -> json.put(key, value)
            is Int -> json.put(key, value)
            is Long -> json.put(key, value)
            is Float -> json.put(key, value.toDouble())
            is String -> json.put(key, value)
        }
    }
    return json.toString(2)
}

private fun importSettingsJson(prefs: android.content.SharedPreferences, raw: String) {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
    val editor = prefs.edit()
    json.keys().forEach { key ->
        when (val value = json.get(key)) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            is String -> editor.putString(key, value)
        }
    }
    editor.apply()
}

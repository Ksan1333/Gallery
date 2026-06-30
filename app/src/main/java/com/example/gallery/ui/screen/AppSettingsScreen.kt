package com.example.gallery.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.gallery.ui.AppText
import com.example.gallery.ui.component.GalleryTopAppBar
import com.example.gallery.ui.theme.GalleryColors
import com.example.gallery.ui.theme.GalleryPaletteSwatches
import com.example.gallery.ui.theme.GalleryThemeMode
import com.example.gallery.ui.theme.GalleryThemeTokens
import java.util.Locale

private object SettingsLabels {
    const val CUSTOM_PALETTE_ON = "カスタムパレットが適用されています。リセットするとデフォルトの色に戻ります。"
    const val CUSTOM_PALETTE_OFF = "デフォルトのテーマ色が使用されています。"
    const val BACK = "戻る"
    const val DISPLAY_THEME = "表示テーマ"
    const val SYSTEM = "システム設定に従う"
    const val SYSTEM_DESC = "Android のダーク/ライトテーマ設定に従います"
    const val DARK = "ダークテーマ"
    const val DARK_DESC = "目に優しく、バッテリー消費を抑えます"
    const val LIGHT = "ライトテーマ"
    const val LIGHT_DESC = "明るく清潔感のある表示にします"
    const val PALETTE = "カラーパレット"
    const val TEXT_SIZE = "文字サイズ"
    const val RESET = "リセット"
    const val APPLY = "適用"
    const val CANCEL = "キャンセル"
    const val BOOK_VIEWER = "ブックビューア設定"
}

private const val BOOK_VIEWER_PREFS = "book_viewer_settings"

private enum class SettingsPage {
    MENU,
    THEME,
    BOOK_VIEWER,
    PALETTE,
    TEXT_SIZE
}

@Composable
fun AppSettingsScreen(
    themeMode: GalleryThemeMode,
    customPalette: GalleryColors?,
    textScale: Float,
    onThemeModeChange: (GalleryThemeMode) -> Unit,
    onCustomPaletteChange: (GalleryColors?) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    var editingColor by remember { mutableStateOf<PaletteColorItem?>(null) }
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.MENU) }
    val title = when (currentPage) {
        SettingsPage.MENU -> AppText.SETTINGS
        SettingsPage.THEME -> SettingsLabels.DISPLAY_THEME
        SettingsPage.BOOK_VIEWER -> SettingsLabels.BOOK_VIEWER
        SettingsPage.PALETTE -> SettingsLabels.PALETTE
        SettingsPage.TEXT_SIZE -> SettingsLabels.TEXT_SIZE
    }
    val handleBack: () -> Unit = {
        if (currentPage == SettingsPage.MENU) {
            onBack()
        } else {
            currentPage = SettingsPage.MENU
        }
    }

    BackHandler(enabled = currentPage != SettingsPage.MENU) {
        currentPage = SettingsPage.MENU
    }

    Scaffold(
        topBar = {
            GalleryTopAppBar(
                title = title,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = SettingsLabels.BACK,
                onNavigationClick = handleBack,
                containerColor = colors.topBar,
                contentColor = colors.primaryText
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
            when (currentPage) {
                SettingsPage.MENU -> item {
                    SettingsMenu(
                        onThemeClick = { currentPage = SettingsPage.THEME },
                        onBookViewerClick = { currentPage = SettingsPage.BOOK_VIEWER },
                        onPaletteClick = { currentPage = SettingsPage.PALETTE },
                        onTextSizeClick = { currentPage = SettingsPage.TEXT_SIZE }
                    )
                }
                SettingsPage.THEME -> item {
                    ThemeSettingsSection(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange
                    )
                }
                SettingsPage.BOOK_VIEWER -> item {
                    BookViewerSettingsSection()
                }
                SettingsPage.PALETTE -> item {
                    PaletteSettingsCard(
                        isCustomPaletteEnabled = customPalette != null,
                        onReset = { onCustomPaletteChange(null) },
                        items = colors.paletteItems { nextColors ->
                            onCustomPaletteChange(nextColors)
                        },
                        onItemClick = { editingColor = it }
                    )
                }
                SettingsPage.TEXT_SIZE -> item {
                    TextSizeSettingsSection(
                        textScale = textScale,
                        onTextScaleChange = onTextScaleChange
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    editingColor?.let { item ->
        ColorEditDialog(
            item = item,
            onDismiss = { editingColor = null },
            onConfirm = { color ->
                item.onChange(color)
                editingColor = null
            }
        )
    }
}

@Composable
private fun SettingsMenu(
    onThemeClick: () -> Unit,
    onBookViewerClick: () -> Unit,
    onPaletteClick: () -> Unit,
    onTextSizeClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsMenuItem(
            icon = Icons.Default.DarkMode,
            title = SettingsLabels.DISPLAY_THEME,
            description = "ダーク / ライト / システム連動を切り替えます。",
            onClick = onThemeClick
        )
        SettingsMenuItem(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = SettingsLabels.BOOK_VIEWER,
            description = "ページ表示、読み方向、画質、タップ領域などを調整します。",
            onClick = onBookViewerClick
        )
        SettingsMenuItem(
            icon = Icons.Default.Palette,
            title = SettingsLabels.PALETTE,
            description = "背景、文字、アクセントなどの配色を編集します。",
            onClick = onPaletteClick
        )
        SettingsMenuItem(
            icon = Icons.Default.SettingsSuggest,
            title = SettingsLabels.TEXT_SIZE,
            description = "アプリ全体の文字サイズをまとめて調整します。",
            onClick = onTextSizeClick
        )
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
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
            Text("開く", color = colors.accent)
        }
    }
}

@Composable
private fun ThemeSettingsSection(
    themeMode: GalleryThemeMode,
    onThemeModeChange: (GalleryThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeModeRow(
            icon = Icons.Default.SettingsSuggest,
            title = SettingsLabels.SYSTEM,
            description = SettingsLabels.SYSTEM_DESC,
            selected = themeMode == GalleryThemeMode.SYSTEM,
            onClick = { onThemeModeChange(GalleryThemeMode.SYSTEM) }
        )
        ThemeModeRow(
            icon = Icons.Default.DarkMode,
            title = SettingsLabels.DARK,
            description = SettingsLabels.DARK_DESC,
            selected = themeMode == GalleryThemeMode.DARK,
            onClick = { onThemeModeChange(GalleryThemeMode.DARK) }
        )
        ThemeModeRow(
            icon = Icons.Default.LightMode,
            title = SettingsLabels.LIGHT,
            description = SettingsLabels.LIGHT_DESC,
            selected = themeMode == GalleryThemeMode.LIGHT,
            onClick = { onThemeModeChange(GalleryThemeMode.LIGHT) }
        )
    }
}

@Composable
private fun ThemeModeRow(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.accentSoft else colors.card
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(title, color = colors.primaryText)
                Text(description, color = colors.secondaryText)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun BookViewerSettingsSection() {
    val colors = GalleryThemeTokens.colors
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(BOOK_VIEWER_PREFS, Context.MODE_PRIVATE)
    }
    var pageLayout by remember { mutableStateOf(prefs.getString("pageLayout", "AUTO") ?: "AUTO") }
    var readingDirection by remember { mutableStateOf(prefs.getString("readingDirection", "RIGHT_TO_LEFT") ?: "RIGHT_TO_LEFT") }
    var fitMode by remember { mutableStateOf(prefs.getString("fitMode", "SCREEN") ?: "SCREEN") }
    var background by remember { mutableStateOf(prefs.getString("background", "BLACK") ?: "BLACK") }
    var renderQuality by remember { mutableStateOf(prefs.getString("renderQuality", "STANDARD") ?: "STANDARD") }
    var scrollMode by remember { mutableStateOf(prefs.getString("scrollMode", "PAGE") ?: "PAGE") }
    var smoothFilter by remember { mutableStateOf(prefs.getString("smoothFilter", "BILINEAR") ?: "BILINEAR") }
    var colorMode by remember { mutableStateOf(prefs.getString("colorMode", "NORMAL") ?: "NORMAL") }
    var pageGap by remember { mutableIntStateOf(prefs.getInt("pageGapDp", 0)) }
    var preloadPages by remember { mutableIntStateOf(prefs.getInt("preloadPages", 1)) }
    var fixedSizePercent by remember { mutableIntStateOf(prefs.getInt("fixedSizePercent", 100)) }
    var brightness by remember { mutableIntStateOf(prefs.getInt("brightness", 0)) }
    var contrast by remember { mutableIntStateOf(prefs.getInt("contrast", 0)) }
    var gamma by remember { mutableIntStateOf(prefs.getInt("gamma", 100)) }
    var tapZoneCount by remember { mutableIntStateOf(prefs.getInt("tapZoneCount", 3)) }
    var slideshowSeconds by remember { mutableIntStateOf(prefs.getInt("slideshowSeconds", 0)) }
    var autoTrimWhiteBorder by remember { mutableStateOf(prefs.getBoolean("autoTrimWhiteBorder", false)) }
    var cacheAroundPages by remember { mutableStateOf(prefs.getBoolean("cacheAroundPages", true)) }
    var balloonMagnifier by remember { mutableStateOf(prefs.getBoolean("balloonMagnifier", false)) }
    var tapNavigation by remember { mutableStateOf(prefs.getBoolean("tapNavigation", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", true)) }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    Surface(
        color = colors.card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(SettingsLabels.BOOK_VIEWER, color = colors.primaryText, fontWeight = FontWeight.Bold)
            Text("Perfect Viewer の主要項目を参考にした本モード設定です。", color = colors.secondaryText)
            SettingsChoiceRow("ページ表示", listOf("自動" to "AUTO", "1ページ" to "SINGLE", "見開き" to "DOUBLE"), pageLayout) { pageLayout = it; saveString("pageLayout", it) }
            SettingsChoiceRow("読み方向", listOf("右から左" to "RIGHT_TO_LEFT", "左から右" to "LEFT_TO_RIGHT"), readingDirection) { readingDirection = it; saveString("readingDirection", it) }
            SettingsChoiceRow("ページ送り", listOf("ページ" to "PAGE", "縦" to "VERTICAL", "横" to "HORIZONTAL"), scrollMode) { scrollMode = it; saveString("scrollMode", it) }
            SettingsChoiceRow(
                "ページの合わせ方",
                listOf("画面内" to "SCREEN", "幅" to "WIDTH", "高さ" to "HEIGHT", "原寸" to "FULL_SIZE", "固定" to "FIXED_SIZE", "伸縮" to "STRETCH"),
                fitMode
            ) { fitMode = it; saveString("fitMode", it) }
            SettingsChoiceRow("背景", listOf("黒" to "BLACK", "グレー" to "GRAY", "白" to "WHITE"), background) { background = it; saveString("background", it) }
            SettingsChoiceRow("表示画質", listOf("標準" to "STANDARD", "高画質" to "HIGH", "最高" to "ULTRA"), renderQuality) { renderQuality = it; saveString("renderQuality", it) }
            SettingsChoiceRow("スムージング", listOf("平均" to "AVERAGING", "Bilinear" to "BILINEAR", "Bicubic" to "BICUBIC", "Lanczos3" to "LANCZOS3"), smoothFilter) { smoothFilter = it; saveString("smoothFilter", it) }
            SettingsChoiceRow("色補正", listOf("通常" to "NORMAL", "グレー" to "GRAYSCALE", "色味補正" to "COLORIZE"), colorMode) { colorMode = it; saveString("colorMode", it) }
            SliderSetting("ページ間隔", pageGap, 0f..24f, 5, "dp") { pageGap = it; saveInt("pageGapDp", it) }
            SliderSetting("先読み", preloadPages, 0f..5f, 4, "ページ") { preloadPages = it; saveInt("preloadPages", it) }
            SliderSetting("固定サイズ", fixedSizePercent, 50f..200f, 14, "%") { fixedSizePercent = it; saveInt("fixedSizePercent", it) }
            SliderSetting("明るさ", brightness, -50f..50f, 19, "") { brightness = it; saveInt("brightness", it) }
            SliderSetting("コントラスト", contrast, -50f..50f, 19, "") { contrast = it; saveInt("contrast", it) }
            SliderSetting("ガンマ", gamma, 50f..200f, 14, "%") { gamma = it; saveInt("gamma", it) }
            SliderSetting("タップ領域", tapZoneCount, 1f..5f, 3, "分割") { tapZoneCount = it; saveInt("tapZoneCount", it) }
            SliderSetting("スライドショー", slideshowSeconds, 0f..30f, 29, "秒") { slideshowSeconds = it; saveInt("slideshowSeconds", it) }
            SwitchSetting("白い余白を自動カット", autoTrimWhiteBorder) { autoTrimWhiteBorder = it; saveBoolean("autoTrimWhiteBorder", it) }
            SwitchSetting("前後ページをキャッシュ", cacheAroundPages) { cacheAroundPages = it; saveBoolean("cacheAroundPages", it) }
            SwitchSetting("バルーン拡大鏡", balloonMagnifier) { balloonMagnifier = it; saveBoolean("balloonMagnifier", it) }
            SwitchSetting("左右タップでページ送り", tapNavigation) { tapNavigation = it; saveBoolean("tapNavigation", it) }
            SwitchSetting("閲覧中は画面を消さない", keepScreenOn) { keepScreenOn = it; saveBoolean("keepScreenOn", it) }
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = colors.secondaryText)
        options.chunked(3).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (label, value) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        label = { Text(label) }
                    )
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
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
private fun TextSizeSettingsSection(
    textScale: Float,
    onTextScaleChange: (Float) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    val textSizes = GalleryThemeTokens.textSizes
    var draftScale by remember(textScale) { mutableFloatStateOf(textScale) }
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(SettingsLabels.TEXT_SIZE, color = colors.primaryText, fontWeight = FontWeight.Bold)
            Text("アプリ全体の文字サイズをまとめて調整します。", color = colors.secondaryText)
            Text(
                text = "現在: ${(draftScale * 100).toInt()}%",
                color = colors.accent,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = draftScale,
                onValueChange = {
                    draftScale = it
                    onTextScaleChange(it)
                },
                valueRange = 0.75f..1.45f,
                steps = 13
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    draftScale = 0.9f
                    onTextScaleChange(0.9f)
                }) {
                    Text("小さめ")
                }
                OutlinedButton(onClick = {
                    draftScale = 1f
                    onTextScaleChange(1f)
                }) {
                    Text("標準")
                }
                OutlinedButton(onClick = {
                    draftScale = 1.18f
                    onTextScaleChange(1.18f)
                }) {
                    Text("大きめ")
                }
            }
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("見出しサンプル", color = colors.primaryText, fontSize = textSizes.header, fontWeight = FontWeight.Bold)
                    Text("本文サンプルです。設定した倍率が各画面の文字サイズに反映されます。", color = colors.secondaryText, fontSize = textSizes.body)
                    Text("補足テキスト / ラベル", color = colors.mutedText, fontSize = textSizes.small)
                }
            }
        }
    }
}

@Composable
private fun PaletteSettingsCard(
    isCustomPaletteEnabled: Boolean,
    onReset: () -> Unit,
    items: List<PaletteColorItem>,
    onItemClick: (PaletteColorItem) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(SettingsLabels.PALETTE, color = colors.primaryText, fontWeight = FontWeight.Bold)
                    Text(
                        if (isCustomPaletteEnabled) SettingsLabels.CUSTOM_PALETTE_ON else SettingsLabels.CUSTOM_PALETTE_OFF,
                        color = colors.secondaryText
                    )
                }
                OutlinedButton(onClick = onReset, enabled = isCustomPaletteEnabled) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text(SettingsLabels.RESET)
                }
            }
            items.forEach { item ->
                PaletteColorRow(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun PaletteHeader(
    isCustomPaletteEnabled: Boolean,
    onReset: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(SettingsLabels.PALETTE, color = colors.primaryText, fontWeight = FontWeight.Bold)
                Text(
                    if (isCustomPaletteEnabled) {
                        "カスタムパレットを適用中です。特定の色をタップして調整できます。"
                    } else {
                        "デフォルトの配色を使用しています。"
                    },
                    color = colors.secondaryText
                )
            }
            OutlinedButton(onClick = onReset, enabled = isCustomPaletteEnabled) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(SettingsLabels.RESET)
            }
        }
    }
}

@Composable
private fun PaletteColorRow(
    item: PaletteColorItem,
    onClick: () -> Unit
) {
    val colors = GalleryThemeTokens.colors
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(item.color)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, color = colors.primaryText)
                Text(item.description, color = colors.secondaryText)
            }
            Text(item.color.toHexText(), color = colors.mutedText)
        }
    }
}

@Composable
private fun ColorEditDialog(
    item: PaletteColorItem,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val colors = GalleryThemeTokens.colors
    var input by remember(item.key, item.color) { mutableStateOf(item.color.toHexText()) }
    val parsedColor = remember(input) { input.parseColorOrNull() }
    val hasError = parsedColor == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("${item.label}を編集", color = colors.primaryText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsedColor ?: item.color)
                    )
                    Text(item.description, color = colors.secondaryText)
                }
                Text("プリセット", color = colors.secondaryText)
                presetPaletteColors.chunked(6).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowColors.forEach { preset ->
                            Surface(
                                color = preset,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.divider),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { input = preset.toHexText() }
                            ) {}
                        }
                    }
                }
                Text("すべての色", color = colors.secondaryText)
                allPaletteColors.chunked(8).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowColors.forEach { preset ->
                            Surface(
                                color = preset,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, colors.divider),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { input = preset.toHexText() }
                            ) {}
                        }
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("#RRGGBB または #AARRGGBB") },
                    singleLine = true,
                    isError = hasError,
                    supportingText = {
                        if (hasError) {
                            Text("例: #006ACB または #FF006ACB")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsedColor?.let(onConfirm) },
                enabled = parsedColor != null
            ) {
                Text(SettingsLabels.APPLY)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(SettingsLabels.CANCEL)
            }
        }
    )
}

private data class PaletteColorItem(
    val key: String,
    val label: String,
    val description: String,
    val color: Color,
    val onChange: (Color) -> Unit
)

private fun GalleryColors.paletteItems(onPaletteChange: (GalleryColors) -> Unit): List<PaletteColorItem> = listOf(
    PaletteColorItem("base", "背景", "メインの背景色", background) {
        onPaletteChange(copy(background = it, drawer = it))
    },
    PaletteColorItem("surface", "サーフェス", "カードやダイアログの背景色", surface) {
        onPaletteChange(copy(surface = it, surfaceVariant = it, topBar = it, card = it, field = it))
    },
    PaletteColorItem("primaryText", "主テキスト", "見出しや本文などの文字色", primaryText) {
        onPaletteChange(copy(primaryText = it))
    },
    PaletteColorItem("secondaryText", "副テキスト", "補足情報やラベルなどの文字色", secondaryText) {
        onPaletteChange(copy(secondaryText = it, mutedText = it))
    },
    PaletteColorItem("accent", "アクセント", "ボタンやアイコン、選択状態の色", accent) {
        onPaletteChange(copy(accent = it))
    },
    PaletteColorItem("accentSoft", "アクセント（淡）", "リストの選択背景など", accentSoft) {
        onPaletteChange(copy(accentSoft = it))
    },
    PaletteColorItem("danger", "危険", "削除ボタンやエラーの色", danger) {
        onPaletteChange(copy(danger = it))
    },
    PaletteColorItem("success", "成功", "完了や有効化の色", success) {
        onPaletteChange(copy(success = it))
    },
    PaletteColorItem("divider", "区切り線", "要素間の境界線や無効化された色", divider) {
        onPaletteChange(copy(divider = it, disabled = it))
    }
)

private val presetPaletteColors = GalleryPaletteSwatches.Preset
private val allPaletteColors = GalleryPaletteSwatches.Expanded

private fun Color.toHexText(): String =
    String.format(Locale.US, "#%08X", toArgb())

private fun String.parseColorOrNull(): Color? {
    val clean = trim().removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return null
    return when (clean.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}

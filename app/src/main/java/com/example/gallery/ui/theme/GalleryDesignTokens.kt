package com.example.gallery.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.gallery.R

enum class GalleryThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class GalleryColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val topBar: Color,
    val drawer: Color,
    val card: Color,
    val field: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val divider: Color,
    val disabled: Color
)

data class GalleryTextSizes(
    val title: TextUnit,
    val header: TextUnit,
    val body: TextUnit,
    val subtitle: TextUnit,
    val small: TextUnit,
    val extraSmall: TextUnit,
    val tiny: TextUnit,
    val bottomNav: TextUnit,
    val scrollbarLabel: TextUnit,
    val badge: TextUnit,
    val denseBadge: TextUnit
)

data class GalleryTypography(
    val title: TextStyle,
    val header: TextStyle,
    val body: TextStyle,
    val bodySecondary: TextStyle,
    val bodyMuted: TextStyle,
    val small: TextStyle,
    val smallMuted: TextStyle,
    val tiny: TextStyle,
    val accent: TextStyle,
    val danger: TextStyle,
    val success: TextStyle,
    val label: TextStyle
)

object GalleryAlphaTokens {
    const val Overlay = 0.55f
    const val Recommendation = 0.95f
    const val Clock = 0.45f
    const val LowContrast = 0.3f
    const val Muted = 0.6f
    const val SemiTransparent = 0.5f
    const val Highlight = 0.7f
    const val SubtleBackground = 0.2f
    const val OverlaySelection = 0.34f
    const val Tooltip = 0.78f
    const val Badge = 0.82f
    const val TransitionFade = 0.55f
    const val TransitionCoverAlpha = 0.25f
    const val TransitionCoverScale = 0.08f
    const val TransitionSlideVertical = 0.18f
    const val TransitionSlideVerticalAlpha = 0.2f
    const val TransitionPageCurlAlpha = 0.18f
    const val Faint = 0.15f
    const val Subtle = 0.08f
}

object GalleryTextSizeTokens {
    private const val MinScale = 0.75f
    private const val MaxScale = 1.45f

    val Default: GalleryTextSizes
        @Composable
        @ReadOnlyComposable
        get() = GalleryTextSizes(
            title = dimensionResource(R.dimen.text_size_title).value.sp,
            header = dimensionResource(R.dimen.text_size_header).value.sp,
            body = dimensionResource(R.dimen.text_size_body).value.sp,
            subtitle = dimensionResource(R.dimen.text_size_subtitle).value.sp,
            small = dimensionResource(R.dimen.text_size_small).value.sp,
            extraSmall = dimensionResource(R.dimen.text_size_extra_small).value.sp,
            tiny = dimensionResource(R.dimen.text_size_tiny).value.sp,
            bottomNav = dimensionResource(R.dimen.text_size_bottom_nav).value.sp,
            scrollbarLabel = dimensionResource(R.dimen.text_size_scrollbar_label).value.sp,
            badge = dimensionResource(R.dimen.text_size_badge).value.sp,
            denseBadge = dimensionResource(R.dimen.text_size_dense_badge).value.sp
        )

    @Composable
    @ReadOnlyComposable
    fun scaled(scale: Float): GalleryTextSizes {
        val safeScale = scale.coerceIn(MinScale, MaxScale)
        val default = Default
        fun TextUnit.scaled() = (value * safeScale).sp
        return GalleryTextSizes(
            title = default.title.scaled(),
            header = default.header.scaled(),
            body = default.body.scaled(),
            subtitle = default.subtitle.scaled(),
            small = default.small.scaled(),
            extraSmall = default.extraSmall.scaled(),
            tiny = default.tiny.scaled(),
            bottomNav = default.bottomNav.scaled(),
            scrollbarLabel = default.scrollbarLabel.scaled(),
            badge = default.badge.scaled(),
            denseBadge = default.denseBadge.scaled()
        )
    }
}

object GalleryColorTokens {
    @Composable
    @ReadOnlyComposable
    fun palette(
        background: Color,
        surface: Color,
        primaryText: Color,
        secondaryText: Color,
        accent: Color,
        danger: Color,
        success: Color,
        divider: Color,
        surfaceVariant: Color = surface,
        topBar: Color = background,
        drawer: Color = background,
        card: Color = surface,
        field: Color = surfaceVariant,
        mutedText: Color = secondaryText,
        accentSoft: Color = accent,
        warning: Color = colorResource(R.color.warning_dark),
        info: Color = accent,
        disabled: Color = secondaryText
    ) = GalleryColors(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        topBar = topBar,
        drawer = drawer,
        card = card,
        field = field,
        primaryText = primaryText,
        secondaryText = secondaryText,
        mutedText = mutedText,
        accent = accent,
        accentSoft = accentSoft,
        danger = danger,
        success = success,
        warning = warning,
        info = info,
        divider = divider,
        disabled = disabled
    )

    val Dark: GalleryColors
        @Composable
        @ReadOnlyComposable
        get() = GalleryColors(
            background = colorResource(R.color.bg_dark),
            surface = colorResource(R.color.surface_dark),
            surfaceVariant = colorResource(R.color.surface_variant_dark),
            topBar = colorResource(R.color.top_bar_dark),
            drawer = colorResource(R.color.drawer_dark),
            card = colorResource(R.color.card_dark),
            field = colorResource(R.color.field_dark),
            primaryText = colorResource(R.color.primary_text_dark),
            secondaryText = colorResource(R.color.secondary_text_dark),
            mutedText = colorResource(R.color.muted_text_dark),
            accent = colorResource(R.color.accent_dark),
            accentSoft = colorResource(R.color.accent_soft_dark),
            danger = colorResource(R.color.danger_dark),
            success = colorResource(R.color.success_dark),
            warning = colorResource(R.color.warning_dark),
            info = colorResource(R.color.info_dark),
            divider = colorResource(R.color.divider_dark),
            disabled = colorResource(R.color.disabled_dark)
        )

    val Light: GalleryColors
        @Composable
        @ReadOnlyComposable
        get() = GalleryColors(
            background = colorResource(R.color.bg_light),
            surface = colorResource(R.color.surface_light),
            surfaceVariant = colorResource(R.color.surface_variant_light),
            topBar = colorResource(R.color.top_bar_light),
            drawer = colorResource(R.color.drawer_light),
            card = colorResource(R.color.card_light),
            field = colorResource(R.color.field_light),
            primaryText = colorResource(R.color.primary_text_light),
            secondaryText = colorResource(R.color.secondary_text_light),
            mutedText = colorResource(R.color.muted_text_light),
            accent = colorResource(R.color.accent_light),
            accentSoft = colorResource(R.color.accent_soft_light),
            danger = colorResource(R.color.danger_light),
            success = colorResource(R.color.success_light),
            warning = colorResource(R.color.warning_light),
            info = colorResource(R.color.info_light),
            divider = colorResource(R.color.divider_light),
            disabled = colorResource(R.color.disabled_light)
        )
}

data class ThemePreset(val labelRes: Int, val value: String, val colors: GalleryColors?)

object GalleryThemePresets {
    val List: List<ThemePreset>
        @Composable
        @ReadOnlyComposable
        get() = listOf(
            ThemePreset(R.string.preset_standard, "DEFAULT", null),
            ThemePreset(R.string.preset_custom, "CUSTOM", null),
            ThemePreset(R.string.preset_midnight, "MIDNIGHT", GalleryColorTokens.Dark),
            ThemePreset(R.string.preset_paper, "PAPER", GalleryColorTokens.Light),
            ThemePreset(R.string.preset_sunrise, "SUNRISE", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_sunrise_bg),
                surface = colorResource(R.color.preset_sunrise_surface),
                primaryText = colorResource(R.color.preset_sunrise_text_primary),
                secondaryText = colorResource(R.color.preset_sunrise_text_secondary),
                accent = colorResource(R.color.preset_sunrise_accent),
                danger = colorResource(R.color.preset_sunrise_danger),
                success = colorResource(R.color.preset_sunrise_success),
                divider = colorResource(R.color.preset_sunrise_divider)
            )),
            ThemePreset(R.string.preset_sunset, "SUNSET", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_sunset_bg),
                surface = colorResource(R.color.preset_sunset_surface),
                primaryText = colorResource(R.color.preset_sunset_text_primary),
                secondaryText = colorResource(R.color.preset_sunset_text_secondary),
                accent = colorResource(R.color.preset_sunset_accent),
                danger = colorResource(R.color.preset_sunset_danger),
                success = colorResource(R.color.preset_sunset_success),
                divider = colorResource(R.color.preset_sunset_divider)
            )),
            ThemePreset(R.string.preset_gorgeous, "GORGEOUS", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_gorgeous_bg),
                surface = colorResource(R.color.preset_gorgeous_surface),
                primaryText = colorResource(R.color.preset_gorgeous_text_primary),
                secondaryText = colorResource(R.color.preset_gorgeous_text_secondary),
                accent = colorResource(R.color.preset_gorgeous_accent),
                danger = colorResource(R.color.preset_gorgeous_danger),
                success = colorResource(R.color.preset_gorgeous_success),
                divider = colorResource(R.color.preset_gorgeous_divider)
            )),
            ThemePreset(R.string.preset_calm, "CALM", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_calm_bg),
                surface = colorResource(R.color.preset_calm_surface),
                primaryText = colorResource(R.color.preset_calm_text_primary),
                secondaryText = colorResource(R.color.preset_calm_text_secondary),
                accent = colorResource(R.color.preset_calm_accent),
                danger = colorResource(R.color.preset_calm_danger),
                success = colorResource(R.color.preset_calm_success),
                divider = colorResource(R.color.preset_calm_divider)
            )),
            ThemePreset(R.string.preset_spring, "SPRING", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_spring_bg),
                surface = colorResource(R.color.preset_spring_surface),
                primaryText = colorResource(R.color.preset_spring_text_primary),
                secondaryText = colorResource(R.color.preset_spring_text_secondary),
                accent = colorResource(R.color.preset_spring_accent),
                danger = colorResource(R.color.preset_spring_danger),
                success = colorResource(R.color.preset_spring_success),
                divider = colorResource(R.color.preset_spring_divider)
            )),
            ThemePreset(R.string.preset_summer, "SUMMER", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_summer_bg),
                surface = colorResource(R.color.preset_summer_surface),
                primaryText = colorResource(R.color.preset_summer_text_primary),
                secondaryText = colorResource(R.color.preset_summer_text_secondary),
                accent = colorResource(R.color.preset_summer_accent),
                danger = colorResource(R.color.preset_summer_danger),
                success = colorResource(R.color.preset_summer_success),
                divider = colorResource(R.color.preset_summer_divider)
            )),
            ThemePreset(R.string.preset_winter, "WINTER", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_winter_bg),
                surface = colorResource(R.color.preset_winter_surface),
                primaryText = colorResource(R.color.preset_winter_text_primary),
                secondaryText = colorResource(R.color.preset_winter_text_secondary),
                accent = colorResource(R.color.preset_winter_accent),
                danger = colorResource(R.color.preset_winter_danger),
                success = colorResource(R.color.preset_winter_success),
                divider = colorResource(R.color.preset_winter_divider)
            )),
            ThemePreset(R.string.preset_forest, "FOREST", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_forest_bg),
                surface = colorResource(R.color.preset_forest_surface),
                primaryText = colorResource(R.color.preset_forest_text_primary),
                secondaryText = colorResource(R.color.preset_forest_text_secondary),
                accent = colorResource(R.color.preset_forest_accent),
                danger = colorResource(R.color.preset_forest_danger),
                success = colorResource(R.color.preset_forest_success),
                divider = colorResource(R.color.preset_forest_divider)
            )),
            ThemePreset(R.string.preset_neon, "NEON", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_neon_bg),
                surface = colorResource(R.color.preset_neon_surface),
                primaryText = colorResource(R.color.preset_neon_text_primary),
                secondaryText = colorResource(R.color.preset_neon_text_secondary),
                accent = colorResource(R.color.preset_neon_accent),
                danger = colorResource(R.color.preset_neon_danger),
                success = colorResource(R.color.preset_neon_success),
                divider = colorResource(R.color.preset_neon_divider)
            )),
            ThemePreset(R.string.preset_mono, "MONO", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_mono_bg),
                surface = colorResource(R.color.preset_mono_surface),
                primaryText = colorResource(R.color.preset_mono_text_primary),
                secondaryText = colorResource(R.color.preset_mono_text_secondary),
                accent = colorResource(R.color.preset_mono_accent),
                danger = colorResource(R.color.preset_mono_danger),
                success = colorResource(R.color.preset_mono_success),
                divider = colorResource(R.color.preset_mono_divider)
            )),
            ThemePreset(R.string.preset_sakura_mist, "SAKURA_MIST", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_sakura_mist_bg),
                surface = colorResource(R.color.preset_sakura_mist_surface),
                primaryText = colorResource(R.color.preset_sakura_mist_text_primary),
                secondaryText = colorResource(R.color.preset_sakura_mist_text_secondary),
                accent = colorResource(R.color.preset_sakura_mist_accent),
                danger = colorResource(R.color.preset_sakura_mist_danger),
                success = colorResource(R.color.preset_sakura_mist_success),
                divider = colorResource(R.color.preset_sakura_mist_divider)
            )),
            ThemePreset(R.string.preset_fresh_leaf, "FRESH_LEAF", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_fresh_leaf_bg),
                surface = colorResource(R.color.preset_fresh_leaf_surface),
                primaryText = colorResource(R.color.preset_fresh_leaf_text_primary),
                secondaryText = colorResource(R.color.preset_fresh_leaf_text_secondary),
                accent = colorResource(R.color.preset_fresh_leaf_accent),
                danger = colorResource(R.color.preset_fresh_leaf_danger),
                success = colorResource(R.color.preset_fresh_leaf_success),
                divider = colorResource(R.color.preset_fresh_leaf_divider)
            )),
            ThemePreset(R.string.preset_deep_sea, "DEEP_SEA", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_deep_sea_bg),
                surface = colorResource(R.color.preset_deep_sea_surface),
                primaryText = colorResource(R.color.preset_deep_sea_text_primary),
                secondaryText = colorResource(R.color.preset_deep_sea_text_secondary),
                accent = colorResource(R.color.preset_deep_sea_accent),
                danger = colorResource(R.color.preset_deep_sea_danger),
                success = colorResource(R.color.preset_deep_sea_success),
                divider = colorResource(R.color.preset_deep_sea_divider)
            )),
            ThemePreset(R.string.preset_starry, "STARRY", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_starry_bg),
                surface = colorResource(R.color.preset_starry_surface),
                primaryText = colorResource(R.color.preset_starry_text_primary),
                secondaryText = colorResource(R.color.preset_starry_text_secondary),
                accent = colorResource(R.color.preset_starry_accent),
                danger = colorResource(R.color.preset_starry_danger),
                success = colorResource(R.color.preset_starry_success),
                divider = colorResource(R.color.preset_starry_divider)
            )),
            ThemePreset(R.string.preset_coffee, "COFFEE", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_coffee_bg),
                surface = colorResource(R.color.preset_coffee_surface),
                primaryText = colorResource(R.color.preset_coffee_text_primary),
                secondaryText = colorResource(R.color.preset_coffee_text_secondary),
                accent = colorResource(R.color.preset_coffee_accent),
                danger = colorResource(R.color.preset_coffee_danger),
                success = colorResource(R.color.preset_coffee_success),
                divider = colorResource(R.color.preset_coffee_divider)
            )),
            ThemePreset(R.string.preset_jade, "JADE", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_jade_bg),
                surface = colorResource(R.color.preset_jade_surface),
                primaryText = colorResource(R.color.preset_jade_text_primary),
                secondaryText = colorResource(R.color.preset_jade_text_secondary),
                accent = colorResource(R.color.preset_jade_accent),
                danger = colorResource(R.color.preset_jade_danger),
                success = colorResource(R.color.preset_jade_success),
                divider = colorResource(R.color.preset_jade_divider)
            )),
            ThemePreset(R.string.preset_grape, "GRAPE", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_grape_bg),
                surface = colorResource(R.color.preset_grape_surface),
                primaryText = colorResource(R.color.preset_grape_text_primary),
                secondaryText = colorResource(R.color.preset_grape_text_secondary),
                accent = colorResource(R.color.preset_grape_accent),
                danger = colorResource(R.color.preset_grape_danger),
                success = colorResource(R.color.preset_grape_success),
                divider = colorResource(R.color.preset_grape_divider)
            )),
            ThemePreset(R.string.preset_porcelain, "PORCELAIN", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_porcelain_bg),
                surface = colorResource(R.color.preset_porcelain_surface),
                primaryText = colorResource(R.color.preset_porcelain_text_primary),
                secondaryText = colorResource(R.color.preset_porcelain_text_secondary),
                accent = colorResource(R.color.preset_porcelain_accent),
                danger = colorResource(R.color.preset_porcelain_danger),
                success = colorResource(R.color.preset_porcelain_success),
                divider = colorResource(R.color.preset_porcelain_divider)
            )),
            ThemePreset(R.string.preset_autumn_leaf, "AUTUMN_LEAF", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_autumn_leaf_bg),
                surface = colorResource(R.color.preset_autumn_leaf_surface),
                primaryText = colorResource(R.color.preset_autumn_leaf_text_primary),
                secondaryText = colorResource(R.color.preset_autumn_leaf_text_secondary),
                accent = colorResource(R.color.preset_autumn_leaf_accent),
                danger = colorResource(R.color.preset_autumn_leaf_danger),
                success = colorResource(R.color.preset_autumn_leaf_success),
                divider = colorResource(R.color.preset_autumn_leaf_divider)
            )),
            ThemePreset(R.string.preset_jewel, "JEWEL", GalleryColorTokens.palette(
                background = colorResource(R.color.preset_jewel_bg),
                surface = colorResource(R.color.preset_jewel_surface),
                primaryText = colorResource(R.color.preset_jewel_text_primary),
                secondaryText = colorResource(R.color.preset_jewel_text_secondary),
                accent = colorResource(R.color.preset_jewel_accent),
                danger = colorResource(R.color.preset_jewel_danger),
                success = colorResource(R.color.preset_jewel_success),
                divider = colorResource(R.color.preset_jewel_divider)
            ))
        )
}

object GalleryPaletteSwatches {
    val Preset: List<Color>
        @Composable
        @ReadOnlyComposable
        get() = listOf(
            colorResource(R.color.swatch_01),
            colorResource(R.color.swatch_02),
            colorResource(R.color.swatch_03),
            colorResource(R.color.swatch_04),
            colorResource(R.color.swatch_05),
            colorResource(R.color.swatch_06),
            colorResource(R.color.swatch_07),
            colorResource(R.color.swatch_08),
            colorResource(R.color.swatch_09),
            colorResource(R.color.swatch_10),
            colorResource(R.color.swatch_11),
            colorResource(R.color.swatch_12),
            colorResource(R.color.swatch_13),
            colorResource(R.color.swatch_14),
            colorResource(R.color.swatch_15),
            colorResource(R.color.swatch_16),
            colorResource(R.color.swatch_17),
            colorResource(R.color.swatch_18),
            colorResource(R.color.swatch_19),
            colorResource(R.color.swatch_20)
        )

    val Expanded = listOf(
        Color(0xFF0B0F14),
        Color(0xFF253241),
        Color(0xFF5C6B78),
        Color(0xFFB7C6D5),
        Color(0xFFFFFFFF),
        Color(0xFFFFF4E8),
        Color(0xFFFFE4D6),
        Color(0xFFFFD6E7),
        Color(0xFF0E4C92),
        Color(0xFF006ACB),
        Color(0xFF4DA3FF),
        Color(0xFF9BD3FF),
        Color(0xFF195B4A),
        Color(0xFF168A52),
        Color(0xFF47D18C),
        Color(0xFFA7E8C6),
        Color(0xFF5A3D8A),
        Color(0xFF7C5CFF),
        Color(0xFFB18CFF),
        Color(0xFFE0D2FF),
        Color(0xFF8A4B12),
        Color(0xFFD4871F),
        Color(0xFFFFBE55),
        Color(0xFFFFE3A3),
        Color(0xFF8A1D35),
        Color(0xFFD9364E),
        Color(0xFFFF6B7A),
        Color(0xFFFFB3BF),
        Color(0xFF3A2B24),
        Color(0xFF6B5A4D),
        Color(0xFFA88D76),
        Color(0xFFE4D2BE)
    )
}

val LocalGalleryColors = staticCompositionLocalOf {
    GalleryColors(
        background = Color(0xFF101418),
        surface = Color(0xFF151B22),
        surfaceVariant = Color(0xFF1D2630),
        topBar = Color(0xFF0B0F14),
        drawer = Color(0xFF101418),
        card = Color(0xFF17202A),
        field = Color(0xFF1F2B36),
        primaryText = Color(0xFFF4F8FC),
        secondaryText = Color(0xFFB7C6D5),
        mutedText = Color(0xFF8392A3),
        accent = Color(0xFF4DA3FF),
        accentSoft = Color(0xFF163B5F),
        danger = Color(0xFFFF6B7A),
        success = Color(0xFF47D18C),
        warning = Color(0xFFFFB300),
        info = Color(0xFF0288D1),
        divider = Color(0x33FFFFFF),
        disabled = Color(0xFF6E7A86)
    )
}
val LocalGalleryTextSizes = staticCompositionLocalOf {
    GalleryTextSizes(
        title = 24.sp,
        header = 20.sp,
        body = 16.sp,
        subtitle = 14.sp,
        small = 12.sp,
        extraSmall = 11.sp,
        tiny = 10.sp,
        bottomNav = 9.sp,
        scrollbarLabel = 13.sp,
        badge = 10.sp,
        denseBadge = 9.sp
    )
}

val LocalGalleryTypography = staticCompositionLocalOf {
    GalleryTypography(
        title = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        header = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodySecondary = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMuted = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
        small = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
        smallMuted = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
        tiny = TextStyle(fontWeight = FontWeight.Normal, fontSize = 10.sp),
        accent = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        danger = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        success = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
    )
}

object GalleryThemeTokens {
    val colors: GalleryColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGalleryColors.current

    val textSizes: GalleryTextSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalGalleryTextSizes.current

    val typography: GalleryTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalGalleryTypography.current
}

val galleryColors: GalleryColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGalleryColors.current

val galleryTextSizes: GalleryTextSizes
    @Composable
    @ReadOnlyComposable
    get() = LocalGalleryTextSizes.current

val galleryTypography: GalleryTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalGalleryTypography.current

package com.example.gallery.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
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
            warning = Color(0xFFFFB300),
            info = Color(0xFF0288D1),
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
            warning = Color(0xFFFBC02D),
            info = Color(0xFF0288D1),
            divider = colorResource(R.color.divider_light),
            disabled = colorResource(R.color.disabled_light)
        )
}

object GalleryPaletteSwatches {
    val Preset = listOf(
        Color(0xFF101418),
        Color(0xFF1D2630),
        Color(0xFFF4F8FC),
        Color(0xFFFFFFFF),
        Color(0xFF101820),
        Color(0xFFF4F8FC),
        Color(0xFF006ACB),
        Color(0xFF4DA3FF),
        Color(0xFFD8ECFF),
        Color(0xFFFF6B7A),
        Color(0xFF47D18C),
        Color(0xFF9AA8B6)
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

object GalleryThemeTokens {
    val colors: GalleryColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGalleryColors.current

    val textSizes: GalleryTextSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalGalleryTextSizes.current
}

val galleryColors: GalleryColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGalleryColors.current

val galleryTextSizes: GalleryTextSizes
    @Composable
    @ReadOnlyComposable
    get() = LocalGalleryTextSizes.current

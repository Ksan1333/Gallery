package com.example.gallery.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

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

    val Default = GalleryTextSizes(
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

    fun scaled(scale: Float): GalleryTextSizes {
        val safeScale = scale.coerceIn(MinScale, MaxScale)
        fun TextUnit.scaled() = (value * safeScale).sp
        return GalleryTextSizes(
            title = Default.title.scaled(),
            header = Default.header.scaled(),
            body = Default.body.scaled(),
            subtitle = Default.subtitle.scaled(),
            small = Default.small.scaled(),
            extraSmall = Default.extraSmall.scaled(),
            tiny = Default.tiny.scaled(),
            bottomNav = Default.bottomNav.scaled(),
            scrollbarLabel = Default.scrollbarLabel.scaled(),
            badge = Default.badge.scaled(),
            denseBadge = Default.denseBadge.scaled()
        )
    }
}

object GalleryColorTokens {
    private val Blue = Color(0xFF4DA3FF)
    private val BlueSoft = Color(0xFF163B5F)

    val Dark = GalleryColors(
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
        accent = Blue,
        accentSoft = BlueSoft,
        danger = Color(0xFFFF6B7A),
        success = Color(0xFF47D18C),
        divider = Color(0x33FFFFFF),
        disabled = Color(0xFF6E7A86)
    )

    val Light = GalleryColors(
        background = Color(0xFFF4F8FC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE8F0F8),
        topBar = Color(0xFFFFFFFF),
        drawer = Color(0xFFFFFFFF),
        card = Color(0xFFFFFFFF),
        field = Color(0xFFEAF2FA),
        primaryText = Color(0xFF101820),
        secondaryText = Color(0xFF435466),
        mutedText = Color(0xFF6D7C8C),
        accent = Color(0xFF006ACB),
        accentSoft = Color(0xFFD8ECFF),
        danger = Color(0xFFD9364E),
        success = Color(0xFF168A52),
        divider = Color(0x1F000000),
        disabled = Color(0xFF9AA8B6)
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

val LocalGalleryColors = staticCompositionLocalOf { GalleryColorTokens.Dark }
val LocalGalleryTextSizes = staticCompositionLocalOf { GalleryTextSizeTokens.Default }

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

package com.example.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float = if (value <= 0.03928f) {
        value / 12.92f
    } else {
        Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.copy(alpha = 1f).relativeLuminance()
    val secondLuminance = second.copy(alpha = 1f).relativeLuminance()
    val lighter = maxOf(firstLuminance, secondLuminance)
    val darker = minOf(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

internal fun readableColor(
    preferred: Color,
    backgrounds: List<Color>,
    minimumContrast: Float
): Color {
    if (backgrounds.isEmpty()) return preferred
    fun minimumRatio(candidate: Color): Float = backgrounds.minOf { contrastRatio(candidate, it) }
    val opaquePreferred = preferred.copy(alpha = 1f)
    if (minimumRatio(opaquePreferred) >= minimumContrast) return opaquePreferred
    val blackRatio = minimumRatio(Color.Black)
    val whiteRatio = minimumRatio(Color.White)
    return if (whiteRatio >= blackRatio) Color.White else Color.Black
}

private fun Color.blendToward(target: Color, fraction: Float): Color {
    val amount = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * amount,
        green = green + (target.green - green) * amount,
        blue = blue + (target.blue - blue) * amount,
        alpha = alpha + (target.alpha - alpha) * amount
    )
}

private fun readableContainer(
    container: Color,
    fallback: Color,
    text: Color,
    minimumContrast: Float
): Color {
    if (contrastRatio(text, container) >= minimumContrast) return container

    var low = 0f
    var high = 1f
    repeat(12) {
        val middle = (low + high) / 2f
        if (contrastRatio(text, container.blendToward(fallback, middle)) >= minimumContrast) {
            high = middle
        } else {
            low = middle
        }
    }
    return container.blendToward(fallback, high)
}

internal fun ensureReadableTextColors(colors: GalleryColors): GalleryColors {
    val primaryText = readableColor(
        preferred = colors.primaryText,
        backgrounds = listOf(colors.background),
        minimumContrast = 4.5f
    )
    val adjusted = colors.copy(
        surface = readableContainer(colors.surface, colors.background, primaryText, 4.5f),
        surfaceVariant = readableContainer(colors.surfaceVariant, colors.background, primaryText, 4.5f),
        topBar = readableContainer(colors.topBar, colors.background, primaryText, 4.5f),
        drawer = readableContainer(colors.drawer, colors.background, primaryText, 4.5f),
        card = readableContainer(colors.card, colors.background, primaryText, 4.5f),
        field = readableContainer(colors.field, colors.background, primaryText, 4.5f),
        primaryText = primaryText
    )
    val backgrounds = listOf(
        adjusted.background,
        adjusted.surface,
        adjusted.surfaceVariant,
        adjusted.topBar,
        adjusted.drawer,
        adjusted.card,
        adjusted.field
    )
    fun readableOrPrimary(preferred: Color, minimumContrast: Float): Color {
        val candidate = readableColor(preferred, backgrounds, minimumContrast)
        return if (backgrounds.all { contrastRatio(candidate, it) >= minimumContrast }) {
            candidate
        } else {
            primaryText
        }
    }
    return adjusted.copy(
        secondaryText = readableOrPrimary(colors.secondaryText, 3f),
        mutedText = readableOrPrimary(colors.mutedText, 3f),
        disabled = readableOrPrimary(colors.disabled, 2.5f)
    )
}

internal fun buildGalleryColorScheme(colors: GalleryColors): ColorScheme {
    val useDarkContainers = colors.background.relativeLuminance() < 0.5f
    val onAccent = readableColor(colors.primaryText, listOf(colors.accent), 4.5f)
    val onAccentSoft = readableColor(colors.primaryText, listOf(colors.accentSoft), 4.5f)
    val onDanger = readableColor(colors.primaryText, listOf(colors.danger), 4.5f)
    val onSuccess = readableColor(colors.primaryText, listOf(colors.success), 4.5f)
    val common: ColorScheme = if (useDarkContainers) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = onAccentSoft,
            secondary = colors.accent,
            onSecondary = onAccent,
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.primaryText,
            tertiary = colors.success,
            onTertiary = onSuccess,
            tertiaryContainer = colors.success,
            onTertiaryContainer = onSuccess,
            background = colors.background,
            onBackground = colors.primaryText,
            surface = colors.surface,
            onSurface = colors.primaryText,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.secondaryText,
            surfaceDim = colors.background,
            surfaceBright = colors.surfaceVariant,
            surfaceContainerLowest = colors.background,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.surfaceVariant,
            surfaceContainerHighest = colors.field,
            error = colors.danger,
            onError = onDanger,
            errorContainer = colors.danger,
            onErrorContainer = onDanger,
            outline = colors.divider,
            outlineVariant = colors.divider,
            inverseSurface = colors.primaryText,
            inverseOnSurface = colors.background,
            inversePrimary = colors.accent
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = onAccentSoft,
            secondary = colors.accent,
            onSecondary = onAccent,
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.primaryText,
            tertiary = colors.success,
            onTertiary = onSuccess,
            tertiaryContainer = colors.success,
            onTertiaryContainer = onSuccess,
            background = colors.background,
            onBackground = colors.primaryText,
            surface = colors.surface,
            onSurface = colors.primaryText,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.secondaryText,
            surfaceDim = colors.surfaceVariant,
            surfaceBright = colors.background,
            surfaceContainerLowest = colors.background,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.card,
            surfaceContainerHigh = colors.surfaceVariant,
            surfaceContainerHighest = colors.field,
            error = colors.danger,
            onError = onDanger,
            errorContainer = colors.danger,
            onErrorContainer = onDanger,
            outline = colors.divider,
            outlineVariant = colors.divider,
            inverseSurface = colors.primaryText,
            inverseOnSurface = colors.background,
            inversePrimary = colors.accent
        )
    }
    return common
}

@Composable
fun GalleryTheme(
    themeMode: GalleryThemeMode = GalleryThemeMode.SYSTEM,
    customColors: GalleryColors? = null,
    textScale: Float = 1f,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        GalleryThemeMode.SYSTEM -> isSystemInDarkTheme()
        GalleryThemeMode.DARK -> true
        GalleryThemeMode.LIGHT -> false
    }
    val baseGalleryColors = if (darkTheme) GalleryColorTokens.Dark else GalleryColorTokens.Light
    val galleryColors = ensureReadableTextColors(customColors ?: baseGalleryColors)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (galleryColors.background.relativeLuminance() < 0.5f) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        else -> buildGalleryColorScheme(galleryColors)
    }
    val textSizes = GalleryTextSizeTokens.scaled(textScale)
    val typography = buildGalleryTypography(galleryColors, textSizes)

    CompositionLocalProvider(
        LocalGalleryColors provides galleryColors,
        LocalGalleryTextSizes provides textSizes,
        LocalGalleryTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = galleryTypography(textSizes),
            content = content
        )
    }
}

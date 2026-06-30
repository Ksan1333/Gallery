package com.example.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

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
    val galleryColors = customColors ?: baseGalleryColors
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = galleryColors.accent,
            secondary = galleryColors.accentSoft,
            tertiary = galleryColors.success,
            background = galleryColors.background,
            surface = galleryColors.surface,
            surfaceVariant = galleryColors.surfaceVariant,
            onPrimary = galleryColors.primaryText,
            onSecondary = galleryColors.primaryText,
            onBackground = galleryColors.primaryText,
            onSurface = galleryColors.primaryText,
            onSurfaceVariant = galleryColors.secondaryText,
            error = galleryColors.danger
        )

        else -> lightColorScheme(
            primary = galleryColors.accent,
            secondary = galleryColors.accentSoft,
            tertiary = galleryColors.success,
            background = galleryColors.background,
            surface = galleryColors.surface,
            surfaceVariant = galleryColors.surfaceVariant,
            onPrimary = galleryColors.primaryText,
            onSecondary = galleryColors.primaryText,
            onBackground = galleryColors.primaryText,
            onSurface = galleryColors.primaryText,
            onSurfaceVariant = galleryColors.secondaryText,
            error = galleryColors.danger
        )
    }
    val textSizes = GalleryTextSizeTokens.scaled(textScale)

    CompositionLocalProvider(
        LocalGalleryColors provides galleryColors,
        LocalGalleryTextSizes provides textSizes
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = galleryTypography(textSizes),
            content = content
        )
    }
}

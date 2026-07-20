package com.example.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun galleryTypography(textSizes: GalleryTextSizes): Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = textSizes.title,
        lineHeight = textSizes.title * 1.2f,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = textSizes.header,
        lineHeight = textSizes.header * 1.2f,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = textSizes.body,
        lineHeight = textSizes.body * 1.5f,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = textSizes.subtitle,
        lineHeight = textSizes.subtitle * 1.5f,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = textSizes.small,
        lineHeight = textSizes.small * 1.5f,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = textSizes.small,
        lineHeight = textSizes.small * 1.2f,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = textSizes.extraSmall,
        lineHeight = textSizes.extraSmall * 1.2f,
        letterSpacing = 0.5.sp
    )
)

fun buildGalleryTypography(colors: GalleryColors, textSizes: GalleryTextSizes): GalleryTypography {
    return GalleryTypography(
        title = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = textSizes.title,
            color = colors.primaryText
        ),
        header = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = textSizes.header,
            color = colors.primaryText
        ),
        body = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = textSizes.body,
            color = colors.primaryText
        ),
        bodySecondary = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = textSizes.subtitle,
            color = colors.secondaryText
        ),
        bodyMuted = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = textSizes.subtitle,
            color = colors.mutedText
        ),
        small = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = textSizes.small,
            color = colors.secondaryText
        ),
        smallMuted = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = textSizes.small,
            color = colors.mutedText
        ),
        tiny = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = textSizes.tiny,
            color = colors.mutedText
        ),
        accent = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = textSizes.body,
            color = colors.accent
        ),
        danger = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = textSizes.body,
            color = colors.danger
        ),
        success = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = textSizes.body,
            color = colors.success
        ),
        label = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = textSizes.extraSmall,
            color = colors.secondaryText
        )
    )
}

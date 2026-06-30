package com.example.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

fun galleryTypography(textSizes: GalleryTextSizes): Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = textSizes.body,
        lineHeight = textSizes.title,
        letterSpacing = textSizes.body * 0.03f
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = textSizes.header,
        lineHeight = textSizes.title,
        letterSpacing = textSizes.body * 0f
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = textSizes.extraSmall,
        lineHeight = textSizes.body,
        letterSpacing = textSizes.body * 0.03f
    )
)

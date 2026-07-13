package com.example.gallery.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun replacesTextThatMatchesDarkBackground() {
        val colors = testColors(background = Color.Black, text = Color.Black)

        val readable = ensureReadableTextColors(colors)

        assertTrue(contrastRatio(readable.primaryText, readable.background) >= 4.5f)
    }

    @Test
    fun replacesTextThatMatchesLightBackground() {
        val colors = testColors(background = Color.White, text = Color.White)

        val readable = ensureReadableTextColors(colors)

        assertTrue(contrastRatio(readable.primaryText, readable.card) >= 4.5f)
    }

    @Test
    fun materialContainersUseGalleryPalette() {
        val colors = ensureReadableTextColors(testColors(Color(0xFF101418), Color.White))

        val scheme = buildGalleryColorScheme(colors)

        assertEquals(colors.card, scheme.surfaceContainer)
        assertEquals(colors.field, scheme.surfaceContainerHighest)
        assertTrue(contrastRatio(scheme.onSurface, scheme.surface) >= 4.5f)
    }

    @Test
    fun adjustsMixedContainersThatWouldHideSharedText() {
        val colors = testColors(background = Color.White, text = Color.Black).copy(
            topBar = Color.Black,
            card = Color.Black,
            field = Color.Black
        )

        val readable = ensureReadableTextColors(colors)

        listOf(readable.background, readable.topBar, readable.card, readable.field).forEach { container ->
            assertTrue(contrastRatio(readable.primaryText, container) >= 4.5f)
        }
    }

    private fun testColors(background: Color, text: Color) = GalleryColors(
        background = background,
        surface = background,
        surfaceVariant = background,
        topBar = background,
        drawer = background,
        card = background,
        field = background,
        primaryText = text,
        secondaryText = text,
        mutedText = text,
        accent = text,
        accentSoft = background,
        danger = Color.Red,
        success = Color.Green,
        warning = Color.Yellow,
        info = Color.Cyan,
        divider = text.copy(alpha = 0.2f),
        disabled = text
    )
}

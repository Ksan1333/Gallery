package com.example.gallery.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GallerySelectionGestureTest {
    @Test
    fun normalGalleryAddsIntentDelayBeforeEnteringSelection() {
        assertEquals(
            720L,
            gallerySelectionLongPressTimeoutMs(
                configuredTimeoutMs = 500L,
                isSelectionMode = false,
                pressedItemSelected = false
            )
        )
    }

    @Test
    fun existingSelectionModeKeepsTheShortTimeout() {
        assertEquals(
            220L,
            gallerySelectionLongPressTimeoutMs(
                configuredTimeoutMs = 500L,
                isSelectionMode = true,
                pressedItemSelected = false
            )
        )
    }

    @Test
    fun selectedItemKeepsTheShortestTimeout() {
        assertEquals(
            150L,
            gallerySelectionLongPressTimeoutMs(
                configuredTimeoutMs = 500L,
                isSelectionMode = true,
                pressedItemSelected = true
            )
        )
    }

    @Test
    fun normalGalleryDelayNeverExceedsTheSupportedMaximum() {
        assertEquals(
            2000L,
            gallerySelectionLongPressTimeoutMs(
                configuredTimeoutMs = 1900L,
                isSelectionMode = false,
                pressedItemSelected = false
            )
        )
    }
}

package com.example.gallery.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GallerySelectionGestureTest {
    @Test
    fun normalGalleryAddsIntentDelayBeforeEnteringSelection() {
        assertEquals(
            500L,
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
            1900L,
            gallerySelectionLongPressTimeoutMs(
                configuredTimeoutMs = 1900L,
                isSelectionMode = false,
                pressedItemSelected = false
            )
        )
    }

    @Test
    fun selectedTileTogglesInsteadOfOpeningWhileSelectionModeIsActive() {
        assertEquals(
            true,
            shouldToggleGalleryMediaTap(
                selectionEnabled = true,
                isSelectionMode = true,
                selectOnTap = false
            )
        )
    }

    @Test
    fun normalTapStillOpensWhenSelectionModeIsInactive() {
        assertEquals(
            false,
            shouldToggleGalleryMediaTap(
                selectionEnabled = true,
                isSelectionMode = false,
                selectOnTap = false
            )
        )
    }

    @Test
    fun selectOnTapEnablesSelectionOutsideSelectionMode() {
        assertEquals(
            true,
            shouldToggleGalleryMediaTap(
                selectionEnabled = true,
                isSelectionMode = false,
                selectOnTap = true
            )
        )
    }
}

package com.example.gallery.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPresentationModeTest {

    @Test
    fun uriRestoreUsesFullGridInsteadOfPaging() {
        assertTrue(
            shouldUseFlatGridPresentation(
                isFullGridPreparationEnabled = true,
                columnCount = 4,
                hasSimilarGroups = false,
                isPositionRestorePinned = true
            )
        )
    }

    @Test
    fun ordinaryFourColumnPagingCanRemainPaged() {
        assertFalse(
            shouldUseFlatGridPresentation(
                isFullGridPreparationEnabled = true,
                columnCount = 4,
                hasSimilarGroups = false,
                isPositionRestorePinned = false
            )
        )
    }
}

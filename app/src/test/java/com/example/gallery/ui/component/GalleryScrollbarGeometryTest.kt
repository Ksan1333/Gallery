package com.example.gallery.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryScrollbarGeometryTest {

    @Test
    fun railBottomTargetsLastItem() {
        val target = scrollbarTargetIndexForTouch(
            touchY = 900f,
            grabOffset = 0f,
            railHeight = 1_000f,
            thumbHeightRatio = 0.1f,
            totalItems = 100
        )

        assertEquals(99, target)
    }

    @Test
    fun thumbGrabOffsetStillClampsToLastItemAtRailBottom() {
        val target = scrollbarTargetIndexForTouch(
            touchY = 1_000f,
            grabOffset = 100f,
            railHeight = 1_000f,
            thumbHeightRatio = 0.1f,
            totalItems = 100
        )

        assertEquals(99, target)
    }

    @Test
    fun emptyOrSingleItemListTargetsZero() {
        assertEquals(0, scrollbarTargetIndexForTouch(100f, 0f, 500f, 0.1f, 0))
        assertEquals(0, scrollbarTargetIndexForTouch(100f, 0f, 500f, 0.1f, 1))
    }
}

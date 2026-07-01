package com.example.gallery.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapZoneLayoutTest {
    @Test
    fun tapZoneCountForLayoutSupportsConfiguredLayouts() {
        assertEquals(11, tapZoneCountForLayout("ELEVEN"))
        assertEquals(7, tapZoneCountForLayout("SEVEN"))
        assertEquals(5, tapZoneCountForLayout("FIVE"))
        assertEquals(4, tapZoneCountForLayout("FOUR"))
        assertEquals(3, tapZoneCountForLayout("THREE"))
        assertEquals(5, tapZoneCountForLayout("UNKNOWN", fallback = 5))
    }

    @Test
    fun elevenSplitUsesCenterAndSurroundingAreas() {
        val specs = tapZoneSpecs(11)

        assertEquals(11, specs.size)
        assertTrue(specs.any { it.id == "center" && it.rowSpan == 3 && it.columnSpan == 3 })
        assertTrue(specs.any { it.id == "top_center" })
        assertTrue(specs.any { it.id == "bottom_center" })
        assertTrue(specs.any { it.id == "left_upper" })
        assertTrue(specs.any { it.id == "right_upper" })
    }

    @Test
    fun tapZoneIndexAtReturnsCenterForCenterCoordinates() {
        val specs = tapZoneSpecs(5)
        val centerIndex = specs.indexOfFirst { it.id == "center" }

        assertEquals(centerIndex, tapZoneIndexAt(5, width = 1000f, height = 1000f, x = 500f, y = 500f))
    }

    @Test
    fun tapZoneIndexAtReturnsSideAreasForEdges() {
        assertEquals(0, tapZoneIndexAt(3, width = 1000f, height = 1000f, x = 10f, y = 500f))
        assertEquals(2, tapZoneIndexAt(3, width = 1000f, height = 1000f, x = 990f, y = 500f))
    }
}

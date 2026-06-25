package com.example.gallery.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun floatArrayRoundTripPreservesValues() {
        val source = floatArrayOf(0f, 1.25f, -3.5f, Float.NaN, Float.POSITIVE_INFINITY)

        val bytes = converters.fromFloatArray(source)
        val restored = converters.toFloatArray(bytes)

        assertEquals(source.size * 4, bytes?.size)
        assertArrayEquals(source, restored, 0f)
    }

    @Test
    fun emptyFloatArrayRoundTripPreservesEmptyArray() {
        val bytes = converters.fromFloatArray(floatArrayOf())
        val restored = converters.toFloatArray(bytes)

        assertEquals(0, bytes?.size)
        assertEquals(0, restored?.size)
    }

    @Test
    fun nullFloatArrayRoundTripPreservesNull() {
        assertNull(converters.fromFloatArray(null))
        assertNull(converters.toFloatArray(null))
    }
}

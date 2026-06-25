package com.example.gallery.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaVectorTest {
    @Test
    fun equalsUsesVectorContentInsteadOfArrayReference() {
        val first = MediaVector("content://media/1", floatArrayOf(1f, 2f, 3f))
        val second = MediaVector("content://media/1", floatArrayOf(1f, 2f, 3f))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun equalsDetectsUriAndVectorDifferences() {
        val base = MediaVector("content://media/1", floatArrayOf(1f, 2f, 3f))

        assertNotEquals(base, MediaVector("content://media/2", floatArrayOf(1f, 2f, 3f)))
        assertNotEquals(base, MediaVector("content://media/1", floatArrayOf(1f, 2f, 4f)))
    }
}

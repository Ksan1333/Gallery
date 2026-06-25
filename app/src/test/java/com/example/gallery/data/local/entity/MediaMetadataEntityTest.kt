package com.example.gallery.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaMetadataEntityTest {
    @Test
    fun equalsUsesFeatureVectorContentInsteadOfArrayReference() {
        val first = MediaMetadataEntity(
            uri = "content://media/1",
            featureVector = floatArrayOf(0.1f, 0.2f, 0.3f)
        )
        val second = MediaMetadataEntity(
            uri = "content://media/1",
            featureVector = floatArrayOf(0.1f, 0.2f, 0.3f)
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun equalsDetectsDifferentFeatureVectorContent() {
        val first = MediaMetadataEntity(
            uri = "content://media/1",
            featureVector = floatArrayOf(0.1f, 0.2f)
        )
        val second = MediaMetadataEntity(
            uri = "content://media/1",
            featureVector = floatArrayOf(0.1f, 0.9f)
        )

        assertNotEquals(first, second)
    }

    @Test
    fun equalsIncludesFolderAndTrashState() {
        val base = MediaMetadataEntity(
            uri = "content://media/1",
            folderName = "DCIM/A",
            isDeleted = false,
            deletedDate = null
        )

        assertNotEquals(base, base.copy(folderName = "DCIM/B"))
        assertNotEquals(base, base.copy(isDeleted = true, deletedDate = 123L))
    }
}

package com.example.gallery.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderGroupCodecTest {
    @Test
    fun roundTripsNamesAndFolderIds() {
        val groups = listOf(
            FolderGroupDefinition("group-1", "旅行 2026", listOf("Camera", "家族/写真", "GIF"))
        )

        assertEquals(groups, FolderGroupCodec.decode(FolderGroupCodec.encode(groups)))
    }

    @Test
    fun ignoresBrokenAndSingleFolderGroups() {
        val raw = "v1\nonly-id\ttitle\tone-folder\nbroken%ZZ\ttitle\ta\tb"

        assertTrue(FolderGroupCodec.decode(raw).isEmpty())
    }
}

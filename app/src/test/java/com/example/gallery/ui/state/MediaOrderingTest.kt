package com.example.gallery.ui.state

import com.example.gallery.data.model.MediaData
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaOrderingTest {
    private val media = listOf(
        MediaData("content://3", dateAdded = 30, fileName = "zeta.jpg", fileSize = 30),
        MediaData("content://1", dateAdded = 10, fileName = "02.jpg", fileSize = 10),
        MediaData("content://2", dateAdded = 20, fileName = "01.jpg", fileSize = 20)
    )

    @Test
    fun nameOrderMatchesGalleryAndViewerDirection() {
        assertEquals(
            listOf("01.jpg", "02.jpg", "zeta.jpg"),
            sortMediaForGallery(media, SortMode.NAME, ascending = true).map { it.fileName }
        )
        assertEquals(
            listOf("zeta.jpg", "02.jpg", "01.jpg"),
            sortMediaForGallery(media, SortMode.NAME, ascending = false).map { it.fileName }
        )
    }

    @Test
    fun equalSortKeysHaveStableUriTieBreakers() {
        val sameName = media.map { it.copy(fileName = "same.jpg") }
        assertEquals(
            listOf("content://1", "content://2", "content://3"),
            sortMediaForGallery(sameName, SortMode.NAME, ascending = true).map { it.uri }
        )
    }
}

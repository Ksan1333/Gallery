package com.example.gallery.ui.screen

import com.example.gallery.util.FolderGroupDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderGroupPresentationTest {
    @Test
    fun placesGroupAtFirstMemberAndKeepsMemberOrder() {
        val camera = category("Camera", 4)
        val screenshots = category("Screenshots", 7)
        val downloads = category("Download", 2)

        val displayed = buildFolderGroupCategories(
            categories = listOf(camera, screenshots, downloads),
            definitions = listOf(FolderGroupDefinition("group-1", "資料", listOf("Camera", "Screenshots")))
        )

        assertEquals(listOf("folder-group:group-1", "Download"), displayed.map { it.id })
        assertEquals(listOf("Camera", "Screenshots"), displayed.first().groupMembers.map { it.id })
        assertEquals(11, displayed.first().count)
    }

    @Test
    fun ignoresOverlappingOrIncompleteLaterGroups() {
        val displayed = buildFolderGroupCategories(
            categories = listOf(category("A", 1), category("B", 1), category("C", 1)),
            definitions = listOf(
                FolderGroupDefinition("first", "先", listOf("A", "B")),
                FolderGroupDefinition("overlap", "重複", listOf("B", "C"))
            )
        )

        assertEquals(listOf("folder-group:first", "C"), displayed.map { it.id })
        assertNull(displayed.last().groupId)
    }

    private fun category(id: String, count: Int) = CategoryData(
        id = id,
        title = id,
        count = count,
        thumbnail = null
    )
}

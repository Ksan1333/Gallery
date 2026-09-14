package com.example.gallery.ui.state

import com.example.gallery.data.model.MediaData

/**
 * Returns media in the same deterministic order used by gallery and viewer lists.
 *
 * The paging query and the in-memory full-list path used to have subtly different
 * ordering. Keeping the viewer's source list here makes a tap from a sorted grid
 * continue to the adjacent item users actually see.
 */
fun sortMediaForGallery(
    media: List<MediaData>,
    sortMode: SortMode,
    ascending: Boolean
): List<MediaData> {
    val sorted = when (sortMode) {
        SortMode.DATE_ADDED -> media.sortedWith(compareBy<MediaData> { it.galleryDateMillis }.thenBy { it.uri })
        SortMode.SIZE -> media.sortedWith(compareBy<MediaData> { it.fileSize }.thenBy { it.uri })
        SortMode.NAME -> media.sortedWith(
            compareBy<MediaData>(
                { item -> item.fileName.firstOrNull()?.isDigit() != true },
                { item -> item.fileName },
                { item -> item.uri }
            )
        )
    }
    return if (ascending) sorted else sorted.asReversed()
}

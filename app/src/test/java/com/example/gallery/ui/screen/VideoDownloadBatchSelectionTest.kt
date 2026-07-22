package com.example.gallery.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloadBatchSelectionTest {
    @Test
    fun selectsOneHighestQualityVariantForEveryMediaItem() {
        val candidates = listOf(
            candidate("a-low.mp4", "media-a", 128_000),
            candidate("a-high.mp4", "media-a", 512_000),
            candidate("b-low.mp4", "media-b", 256_000),
            candidate("b-high.mp4", "media-b", 768_000)
        )

        val selections = buildMediaDownloadSelections(candidates, GifSaveFormat.MP4)

        assertEquals(listOf("a-high.mp4", "b-high.mp4"), selections.map { it.media.url })
    }

    @Test
    fun respectsSelectedQualityForEachMediaItem() {
        val candidates = listOf(
            candidate("a-low.mp4", "media-a", 128_000),
            candidate("a-high.mp4", "media-a", 512_000)
        )

        val selections = buildMediaDownloadSelections(
            candidates,
            GifSaveFormat.MP4,
            selectedUrlByMediaKey = mapOf("media-a" to "a-low.mp4")
        )

        assertEquals("a-low.mp4", selections.single().media.url)
    }

    @Test
    fun downloadsOnlyCheckedMediaItems() {
        val candidates = listOf(
            candidate("a.mp4", "media-a", 256_000),
            candidate("b.mp4", "media-b", 512_000)
        )

        val selections = buildMediaDownloadSelections(
            candidates = candidates,
            gifSaveFormat = GifSaveFormat.MP4,
            selectedMediaKeys = setOf("media-b")
        )

        assertEquals(listOf("b.mp4"), selections.map { it.media.url })
    }

    @Test
    fun includesStillImagesInDownloadSelections() {
        val candidates = listOf(
            MediaUrlCandidate(
                url = "photo.jpg",
                contentType = "image/jpeg",
                mediaKey = "photo-a"
            )
        )

        val selection = buildMediaDownloadSelections(candidates, GifSaveFormat.MP4).single()

        assertEquals("photo.jpg", selection.media.url)
        assertEquals("Image", selection.qualityLabel)
    }

    @Test
    fun excludesVideoPreviewImagesFromVideoDownloadChoices() {
        val candidates = listOf(
            candidate("video.mp4", "video-a", 512_000),
            MediaUrlCandidate(
                url = "video-preview.jpg",
                contentType = "image/jpeg",
                mediaKey = "video-a"
            )
        )

        val group = groupDownloadCandidates(candidates).single()

        assertEquals(listOf("video.mp4"), group.map { it.url })
    }

    @Test
    fun directGifIsPreferredWithoutTranscoding() {
        val candidates = listOf(
            MediaUrlCandidate("animation.gif", contentType = "image/gif", isGifSource = true, mediaKey = "gif-a"),
            candidate("animation.mp4", "gif-a", 512_000, isGifSource = true)
        )

        val selection = buildMediaDownloadSelections(candidates, GifSaveFormat.GIF).single()

        assertEquals("animation.gif", selection.media.url)
        assertFalse(selection.transcodeToGif)
    }

    @Test
    fun onlyGifSourcesAreTranscodedInMixedPost() {
        val candidates = listOf(
            candidate("animated.mp4", "gif-a", 512_000, isGifSource = true),
            candidate("video.mp4", "video-b", 768_000, isGifSource = false)
        )

        val selections = buildMediaDownloadSelections(candidates, GifSaveFormat.GIF)

        assertTrue(selections.first { it.media.mediaKey == "gif-a" }.transcodeToGif)
        assertFalse(selections.first { it.media.mediaKey == "video-b" }.transcodeToGif)
    }

    @Test
    fun selectsEveryGifInAMultiGifPost() {
        val candidates = (1..4).flatMap { index ->
            listOf(
                candidate("animation-$index-low.mp4", "gif-$index", 128_000, isGifSource = true),
                candidate("animation-$index-high.mp4", "gif-$index", 512_000, isGifSource = true)
            )
        }

        val selections = buildMediaDownloadSelections(candidates, GifSaveFormat.GIF)

        assertEquals(4, selections.size)
        assertEquals((1..4).map { "gif-$it" }, selections.map { it.media.mediaKey })
        assertTrue(selections.all { it.transcodeToGif })
    }

    private fun candidate(
        url: String,
        mediaKey: String,
        bitrate: Int,
        isGifSource: Boolean = false
    ) = MediaUrlCandidate(
        url = url,
        bitrate = bitrate,
        contentType = "video/mp4",
        isGifSource = isGifSource,
        mediaKey = mediaKey
    )
}

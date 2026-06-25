package com.example.gallery.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDownloadUrlUtilsTest {
    @Test
    fun xStatusUrlRecognizesXAndTwitterStatusUrls() {
        assertTrue(VideoDownloadUrlUtils.isXStatusUrl("https://x.com/user/status/1234567890?s=20"))
        assertTrue(VideoDownloadUrlUtils.isXStatusUrl("https://twitter.com/user/status/1234567890"))
        assertTrue(VideoDownloadUrlUtils.isXStatusUrl("x.com/user/status/1234567890"))
        assertFalse(VideoDownloadUrlUtils.isXStatusUrl("https://x.com/user"))
        assertFalse(VideoDownloadUrlUtils.isXStatusUrl("https://example.com/status/1234567890"))
    }

    @Test
    fun buildXApiUrlsCreatesHostReplacementsAndStatusFallbacks() {
        val urls = VideoDownloadUrlUtils.buildXApiUrls("https://x.com/user/status/1234567890")

        assertEquals(
            listOf(
                "https://api.fxtwitter.com/user/status/1234567890",
                "https://api.vxtwitter.com/user/status/1234567890",
                "https://api.fixupx.com/user/status/1234567890",
                "https://api.fxtwitter.com/status/1234567890",
                "https://api.vxtwitter.com/status/1234567890",
                "https://api.fixupx.com/status/1234567890"
            ),
            urls
        )
    }

    @Test
    fun directMediaUrlRecognizesSupportedImagesAndVideos() {
        assertTrue(VideoDownloadUrlUtils.isDirectMediaUrl("https://cdn.example.com/video.mp4?tag=1"))
        assertTrue(VideoDownloadUrlUtils.isDirectMediaUrl("https://pbs.twimg.com/media/abc?format=gif&name=orig"))
        assertTrue(VideoDownloadUrlUtils.isDirectMediaUrl("https://pbs.twimg.com/media/abc?format=webp"))
        assertTrue(VideoDownloadUrlUtils.isDirectMediaUrl("https://cdn.example.com/image.PNG"))
        assertFalse(VideoDownloadUrlUtils.isDirectMediaUrl("https://cdn.example.com/playlist.m3u8"))
        assertFalse(VideoDownloadUrlUtils.isDirectMediaUrl("https://x.com/user/status/1234567890"))
    }

    @Test
    fun gifUrlRecognizesExtensionAndFormatQuery() {
        assertTrue(VideoDownloadUrlUtils.isGifUrl("https://cdn.example.com/image.gif"))
        assertTrue(VideoDownloadUrlUtils.isGifUrl("https://pbs.twimg.com/media/abc?format=gif&name=orig"))
        assertFalse(VideoDownloadUrlUtils.isGifUrl("https://cdn.example.com/video.mp4"))
    }

    @Test
    fun detectMediaTypeUsesContentTypeBeforeFallbacks() {
        assertEquals("gif" to "image/gif", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/video.mp4", "image/gif; charset=binary"))
        assertEquals("webp" to "image/webp", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/file", "image/webp"))
        assertEquals("png" to "image/png", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/file", "image/png"))
        assertEquals("jpg" to "image/jpeg", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/file", "image/jpeg"))
        assertEquals("mp4" to "video/mp4", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/file", "video/mp4"))
    }

    @Test
    fun detectMediaTypeFallsBackToUrlAndDefaultsToMp4() {
        assertEquals("gif" to "image/gif", VideoDownloadUrlUtils.detectMediaType("https://pbs.twimg.com/media/abc?format=gif", null))
        assertEquals("webp" to "image/webp", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/image.webp", null))
        assertEquals("jpg" to "image/jpeg", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/image.jpeg", null))
        assertEquals("mp4" to "video/mp4", VideoDownloadUrlUtils.detectMediaType("https://cdn.example.com/unknown.bin", null))
    }
}

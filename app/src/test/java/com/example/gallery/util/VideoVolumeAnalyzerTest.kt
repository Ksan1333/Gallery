package com.example.gallery.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoVolumeAnalyzerTest {
    @Test
    fun reducesTrackWithClearlyHighAverageLevel() {
        assertTrue(VideoVolumeAnalyzer.shouldReduce(rms = 0.24f, peak = 0.80f))
    }

    @Test
    fun reducesNearClippedTrackWithHighAverageLevel() {
        assertTrue(VideoVolumeAnalyzer.shouldReduce(rms = 0.13f, peak = 0.99f))
    }

    @Test
    fun keepsTypicalTrackUnchanged() {
        assertFalse(VideoVolumeAnalyzer.shouldReduce(rms = 0.14f, peak = 0.95f))
    }
}

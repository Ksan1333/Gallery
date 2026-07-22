package com.example.gallery.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun newerMajorVersionRequiresAnUpdate() {
        assertTrue(AppUpdateManager.isMandatoryUpdate("2.0.0", "1.9.9"))
        assertTrue(AppUpdateManager.isMandatoryUpdate("v10.0.0", "9.8.7"))
    }

    @Test
    fun minorAndPatchVersionsRemainOptional() {
        assertFalse(AppUpdateManager.isMandatoryUpdate("1.5.0", "1.4.9"))
        assertFalse(AppUpdateManager.isMandatoryUpdate("1.4.10", "1.4.9"))
        assertFalse(AppUpdateManager.isMandatoryUpdate("1.9.0", "2.0.0"))
    }

    @Test
    fun malformedVersionsAreNotMandatory() {
        assertFalse(AppUpdateManager.isMandatoryUpdate("latest", "1.0.0"))
    }
}

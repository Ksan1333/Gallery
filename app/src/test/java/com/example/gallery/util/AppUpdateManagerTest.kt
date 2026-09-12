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

    @Test
    fun sameOrRotatedSigningCertificateIsAccepted() {
        assertTrue(
            AppUpdateManager.areSigningCertificatesCompatible(
                installedCurrent = setOf("old"),
                candidateCurrent = setOf("old"),
                candidateHistory = setOf("old"),
                candidateHasMultipleSigners = false
            )
        )
        assertTrue(
            AppUpdateManager.areSigningCertificatesCompatible(
                installedCurrent = setOf("old"),
                candidateCurrent = setOf("new"),
                candidateHistory = setOf("old", "new"),
                candidateHasMultipleSigners = false
            )
        )
    }

    @Test
    fun unrelatedSigningCertificateIsRejected() {
        assertFalse(
            AppUpdateManager.areSigningCertificatesCompatible(
                installedCurrent = setOf("debug"),
                candidateCurrent = setOf("release"),
                candidateHistory = setOf("release"),
                candidateHasMultipleSigners = false
            )
        )
    }

    @Test
    fun multipleSignersRequireAnExactCurrentSet() {
        assertTrue(
            AppUpdateManager.areSigningCertificatesCompatible(
                installedCurrent = setOf("a", "b"),
                candidateCurrent = setOf("a", "b"),
                candidateHistory = setOf("a", "b"),
                candidateHasMultipleSigners = true
            )
        )
        assertFalse(
            AppUpdateManager.areSigningCertificatesCompatible(
                installedCurrent = setOf("a", "b"),
                candidateCurrent = setOf("a", "c"),
                candidateHistory = setOf("a", "b", "c"),
                candidateHasMultipleSigners = true
            )
        )
    }
}

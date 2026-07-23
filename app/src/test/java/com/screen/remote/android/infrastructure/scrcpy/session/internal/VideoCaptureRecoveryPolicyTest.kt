package com.screen.remote.android.infrastructure.scrcpy.session.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCaptureRecoveryPolicyTest {
    @Test
    fun nativeResolutionFallsBackOneVisibleTier() {
        assertEquals(1920, nextVideoRecoveryMaxSize(0))
    }

    @Test
    fun configuredSizesFollowVisibleTiersAndStopAt720() {
        assertEquals(1080, nextVideoRecoveryMaxSize(1920))
        assertEquals(1080, nextVideoRecoveryMaxSize(1281))
        assertEquals(720, nextVideoRecoveryMaxSize(1080))
        assertNull(nextVideoRecoveryMaxSize(720))
    }

    @Test
    fun decoderFailureUsesTierStrictlyBelowFailedLongEdge() {
        assertEquals(1920, nextVideoRecoveryMaxSize(currentMaxSize = 0, failedLongEdge = 2560))
        assertEquals(1080, nextVideoRecoveryMaxSize(currentMaxSize = 0, failedLongEdge = 1920))
        assertEquals(720, nextVideoRecoveryMaxSize(currentMaxSize = 0, failedLongEdge = 1080))
        assertNull(nextVideoRecoveryMaxSize(currentMaxSize = 0, failedLongEdge = 720))
    }
}

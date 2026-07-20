package com.screen.remote.android.feature.remote.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModeRuntimePolicyTest {
    @Test
    fun gameSessionCanShowFloatingMenu() {
        assertTrue(shouldShowFloatingMenu(videoAvailable = true, showFloatingBall = true))
    }

    @Test
    fun sessionCanHideFloatingMenu() {
        assertFalse(shouldShowFloatingMenu(videoAvailable = true, showFloatingBall = false))
        assertFalse(shouldShowFloatingMenu(videoAvailable = false, showFloatingBall = true))
    }
}

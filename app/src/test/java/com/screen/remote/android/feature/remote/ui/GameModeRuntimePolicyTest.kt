package com.screen.remote.android.feature.remote.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModeRuntimePolicyTest {
    @Test
    fun gameSessionDoesNotShowFloatingMenu() {
        assertFalse(shouldShowFloatingMenu(videoAvailable = true, gameMode = true, showFloatingBall = true))
        assertTrue(shouldShowFloatingMenu(videoAvailable = true, gameMode = false, showFloatingBall = true))
    }

    @Test
    fun sessionCanHideFloatingMenu() {
        assertFalse(shouldShowFloatingMenu(videoAvailable = true, gameMode = false, showFloatingBall = false))
        assertFalse(shouldShowFloatingMenu(videoAvailable = false, gameMode = false, showFloatingBall = true))
    }
}

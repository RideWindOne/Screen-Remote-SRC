package com.screen.remote.android.feature.session.ui.component

import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.core.domain.model.ScreenRotationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDialogCompatibilityModeTest {
    @Test
    fun enablingCompatibilityModeClearsScrcpyOnlyOptions() {
        val state =
            SessionDialogState().apply {
                updateConfig {
                    copy(
                        useFullScreen = true,
                        enableHardwareDecoding = true,
                        tunnelMode = ScrcpyTunnelMode.ADB_FORWARD,
                        enableAudio = true,
                        clipboardSync = true,
                        turnScreenOff = true,
                        powerOffOnClose = true,
                        cleanupOnDisconnect = true,
                        stayAwake = true,
                        ignoreVideoEncoderConstraints = true,
                        newDisplayEnabled = true,
                        showTouches = true,
                        showFloatingBall = true,
                        screenRotationPolicy = ScreenRotationPolicy.TARGET,
                        keepDeviceAwake = true,
                    )
                }
            }

        state.updateCompatibilityMode(true)

        assertFalse(state.config.useFullScreen)
        assertFalse(state.config.enableHardwareDecoding)
        assertEquals(ScrcpyTunnelMode.DIRECT_ADB, state.config.tunnelMode)
        assertFalse(state.config.enableAudio)
        assertFalse(state.config.clipboardSync)
        assertFalse(state.config.turnScreenOff)
        assertFalse(state.config.powerOffOnClose)
        assertFalse(state.config.cleanupOnDisconnect)
        assertFalse(state.config.stayAwake)
        assertFalse(state.config.ignoreVideoEncoderConstraints)
        assertFalse(state.config.newDisplayEnabled)
        assertFalse(state.config.showTouches)
        assertTrue(state.config.showFloatingBall)
        assertEquals(ScreenRotationPolicy.TARGET, state.config.screenRotationPolicy)
        assertTrue(state.config.keepDeviceAwake)
    }
}

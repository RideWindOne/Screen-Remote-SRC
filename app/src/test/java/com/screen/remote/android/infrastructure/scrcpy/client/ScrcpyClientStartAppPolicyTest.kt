package com.screen.remote.android.infrastructure.scrcpy.client

import com.screen.remote.android.core.domain.model.ScrcpyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrcpyClientStartAppPolicyTest {
    @Test
    fun resolvesStartAppForPhysicalAndVirtualDisplays() {
        listOf(false, true).forEach { newDisplayEnabled ->
            val config =
                ScrcpyConfig(
                    newDisplayEnabled = newDisplayEnabled,
                    startApp = "  com.example.app  ",
                )

            assertEquals("com.example.app", resolveControlStartApp(config))
        }
    }

    @Test
    fun ignoresBlankStartApp() {
        assertNull(resolveControlStartApp(ScrcpyConfig(startApp = "   ")))
    }

    @Test
    fun leavesCompatibilityModeLaunchToCompatibilityController() {
        val config = ScrcpyConfig(compatibilityMode = true, startApp = "com.example.app")

        assertNull(resolveControlStartApp(config))
    }
}

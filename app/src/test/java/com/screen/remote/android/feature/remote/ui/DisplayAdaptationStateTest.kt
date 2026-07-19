package com.screen.remote.android.feature.remote.ui

import com.screen.remote.android.core.common.util.LocalDisplaySpec
import com.screen.remote.android.infrastructure.adb.connection.AdbDisplayInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAdaptationStateTest {
    private val local = LocalDisplaySpec(width = 1920, height = 1080, densityDpi = 400)

    @Test
    fun `adapted when all current values match local display`() {
        val target =
            AdbDisplayInfo(
                currentWidth = 1920,
                currentHeight = 1080,
                currentDensityDpi = 400,
            )

        assertTrue(isDisplayAdaptedToLocalDevice(target, local))
        assertFalse(isDisplayAdaptedToLocalDevice(target.copy(currentDensityDpi = 420), local))
        assertFalse(isDisplayAdaptedToLocalDevice(target.copy(currentWidth = 1080, currentHeight = 2400), local))
    }
}

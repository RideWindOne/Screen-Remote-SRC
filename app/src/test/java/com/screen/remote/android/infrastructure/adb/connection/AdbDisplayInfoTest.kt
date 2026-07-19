package com.screen.remote.android.infrastructure.adb.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbDisplayInfoTest {
    @Test
    fun `display command selects currently effective values`() {
        assertEquals(
            "wm size | grep -E 'Physical|Override' | tail -n 1; " +
                "wm density | grep -E 'Physical|Override' | tail -n 1",
            READ_ADB_DISPLAY_INFO_COMMAND,
        )
    }

    @Test
    fun `parses physical and override display values`() {
        val displayInfo =
            parseAdbDisplayInfo(
                """
                Physical size: 1080x2400
                Override size: 1920x1080
                Physical density: 420
                Override density: 400
                """.trimIndent(),
            )

        assertEquals(1920, displayInfo.currentWidth)
        assertEquals(1080, displayInfo.currentHeight)
        assertEquals(400, displayInfo.currentDensityDpi)
    }

    @Test
    fun `uses physical values when no override exists`() {
        val displayInfo =
            parseAdbDisplayInfo(
                """
                Physical size: 1080x2400
                Physical density: 420
                """.trimIndent(),
            )

        assertEquals(1080, displayInfo.currentWidth)
        assertEquals(2400, displayInfo.currentHeight)
        assertEquals(420, displayInfo.currentDensityDpi)
    }
}

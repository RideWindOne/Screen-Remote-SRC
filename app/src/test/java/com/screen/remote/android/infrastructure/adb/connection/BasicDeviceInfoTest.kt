package com.screen.remote.android.infrastructure.adb.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicDeviceInfoTest {
    @Test
    fun `basic properties use one shell command`() {
        val command = buildBasicDeviceInfoCommand()

        assertEquals(4, Regex("getprop ").findAll(command).count())
        assertTrue(command.contains("ro.product.model"))
        assertTrue(command.contains("ro.product.manufacturer"))
        assertTrue(command.contains("ro.build.version.release"))
        assertTrue(command.contains("ro.serialno"))
    }

    @Test
    fun `parses basic properties from combined output`() {
        val info =
            parseBasicDeviceInfo(
                """
                __SCREEN_REMOTE_MODEL__
                Pixel 9
                __SCREEN_REMOTE_MANUFACTURER__
                Google
                __SCREEN_REMOTE_ANDROID__
                16
                __SCREEN_REMOTE_SERIAL__
                ABC123
                """.trimIndent(),
            )

        assertEquals("Pixel 9", info.model)
        assertEquals("Google", info.manufacturer)
        assertEquals("16", info.androidVersion)
        assertEquals("ABC123", info.serialNumber)
    }
}

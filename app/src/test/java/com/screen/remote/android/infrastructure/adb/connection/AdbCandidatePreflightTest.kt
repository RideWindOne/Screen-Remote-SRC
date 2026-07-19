package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbCandidatePreflightTest {
    @Test
    fun `preflight command reads fingerprint display and compatible server`() {
        val command = buildAdbCandidatePreflightCommand(AdbConnectionPurpose.SCRCPY_SESSION)

        assertTrue(command.contains("getprop ro.build.fingerprint"))
        assertTrue(command.contains(READ_ADB_DISPLAY_INFO_COMMAND))
        assertTrue(command.contains(AppConstants.SCRCPY_SERVER_PATH))
        assertTrue(command.contains(AppConstants.SCRCPY_SERVER_SHA256))
    }

    @Test
    fun `purpose controls candidate probes`() {
        val management = buildAdbCandidatePreflightCommand(AdbConnectionPurpose.MANAGEMENT)
        val codec = buildAdbCandidatePreflightCommand(AdbConnectionPurpose.CODEC_TEST)

        assertTrue(management.contains("getprop ro.build.fingerprint"))
        assertTrue(management.contains(READ_ADB_DISPLAY_INFO_COMMAND))
        assertFalse(management.contains("sha256sum"))
        assertFalse(codec.contains("getprop ro.build.fingerprint"))
        assertFalse(codec.contains(READ_ADB_DISPLAY_INFO_COMMAND))
        assertTrue(codec.contains("sha256sum"))
    }

    @Test
    fun `parses candidate preflight output`() {
        val preflight =
            parseAdbCandidatePreflight(
                """
                __SCREEN_REMOTE_FINGERPRINT__
                vendor/device/product:16/build/release-keys
                __SCREEN_REMOTE_DISPLAY__
                Physical size: 1080x2400
                Override size: 1920x1080
                Physical density: 420
                Override density: 400
                __SCREEN_REMOTE_SERVER__
                1
                """.trimIndent(),
            )

        assertEquals("vendor/device/product:16/build/release-keys", preflight.buildFingerprint)
        assertEquals(1920, preflight.displayInfo?.currentWidth)
        assertEquals(1080, preflight.displayInfo?.currentHeight)
        assertEquals(400, preflight.displayInfo?.currentDensityDpi)
        assertEquals(true, preflight.hasCompatibleScrcpyServer)
    }

    @Test
    fun `server mismatch is cached as unavailable`() {
        val preflight =
            parseAdbCandidatePreflight(
                """
                __SCREEN_REMOTE_FINGERPRINT__
                fingerprint
                __SCREEN_REMOTE_DISPLAY__
                Physical size: 1080x2400
                Physical density: 420
                __SCREEN_REMOTE_SERVER__
                0
                """.trimIndent(),
            )

        assertEquals(false, preflight.hasCompatibleScrcpyServer)
    }

    @Test
    fun `display parse failure does not discard other preflight values`() {
        val preflight =
            parseAdbCandidatePreflight(
                """
                __SCREEN_REMOTE_FINGERPRINT__
                fingerprint
                __SCREEN_REMOTE_DISPLAY__
                unsupported wm output
                __SCREEN_REMOTE_SERVER__
                1
                """.trimIndent(),
            )

        assertEquals("fingerprint", preflight.buildFingerprint)
        assertEquals(null, preflight.displayInfo)
        assertEquals(true, preflight.hasCompatibleScrcpyServer)
    }
}

package com.screen.remote.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertTrue

private const val MAX_SCREENSHOT_MAX_SIZE = 16384

class CompatibilityCaptureSettingsTest {
    @Test
    fun `native mode keeps full jpeg quality`() {
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 0, jpegQuality = 100),
            ScrcpyConfig(maxSize = 0).compatibilityCaptureSettings(),
        )
    }

    @Test
    fun `max size mapping follows the segmented quality curve`() {
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 10, jpegQuality = 10),
            ScrcpyConfig(maxSize = 10).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 20, jpegQuality = 20),
            ScrcpyConfig(maxSize = 20).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 30, jpegQuality = 30),
            ScrcpyConfig(maxSize = 30).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 40, jpegQuality = 40),
            ScrcpyConfig(maxSize = 40).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 50, jpegQuality = 50),
            ScrcpyConfig(maxSize = 50).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 60, jpegQuality = 60),
            ScrcpyConfig(maxSize = 60).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 70, jpegQuality = 70),
            ScrcpyConfig(maxSize = 70).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 80, jpegQuality = 80),
            ScrcpyConfig(maxSize = 80).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 90, jpegQuality = 90),
            ScrcpyConfig(maxSize = 90).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 100, jpegQuality = 100),
            ScrcpyConfig(maxSize = 100).compatibilityCaptureSettings(),
        )
    }

    @Test
    fun `quality remains in allowed range`() {
        listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100).forEach { maxSize ->
            val settings = ScrcpyConfig(maxSize = maxSize).compatibilityCaptureSettings()
            assertEquals(maxSize, settings.maxSize)
            assertTrue(settings.jpegQuality in 1..100)
        }
    }

    @Test
    fun `values above helper max are clamped to 8192`() {
        assertEquals(
            CompatibilityCaptureSettings(maxSize = MAX_SCREENSHOT_MAX_SIZE, jpegQuality = 100),
            ScrcpyConfig(maxSize = 9_999).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = MAX_SCREENSHOT_MAX_SIZE, jpegQuality = 100),
            ScrcpyConfig(maxSize = 20_000).compatibilityCaptureSettings(),
        )
    }
}

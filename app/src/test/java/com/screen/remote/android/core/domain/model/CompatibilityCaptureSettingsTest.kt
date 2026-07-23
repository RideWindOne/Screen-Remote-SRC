package com.screen.remote.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityCaptureSettingsTest {
    @Test
    fun `native size uses the highest jpeg quality`() {
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 0, jpegQuality = 70),
            ScrcpyConfig(maxSize = 0).compatibilityCaptureSettings(),
        )
    }

    @Test
    fun `preset sizes select progressively higher jpeg quality`() {
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 720, jpegQuality = 55),
            ScrcpyConfig(maxSize = 720).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 1080, jpegQuality = 60),
            ScrcpyConfig(maxSize = 1080).compatibilityCaptureSettings(),
        )
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 1920, jpegQuality = 65),
            ScrcpyConfig(maxSize = 1920).compatibilityCaptureSettings(),
        )
    }

    @Test
    fun `custom size is bounded for the device helper`() {
        assertEquals(
            CompatibilityCaptureSettings(maxSize = 8192, jpegQuality = 65),
            ScrcpyConfig(maxSize = 20_000).compatibilityCaptureSettings(),
        )
    }
}

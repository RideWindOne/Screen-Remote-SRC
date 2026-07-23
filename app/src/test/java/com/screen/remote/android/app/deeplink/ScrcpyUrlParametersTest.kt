package com.screen.remote.android.app.deeplink

import com.screen.remote.android.core.domain.model.ScrcpyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyUrlParametersTest {
    @Test
    fun `applies one-time scrcpy overrides`() {
        val result =
            ScrcpyConfig()
                .withUrlParameters(
                    mapOf(
                        "maxFps" to "120",
                        "videoBitRate" to "8M",
                        "audio" to "on",
                        "turnScreenOff" to "off",
                    ),
                ).getOrThrow()

        assertEquals(120, result.maxFps)
        assertEquals(8_000_000, result.videoBitRate)
        assertTrue(result.enableAudio)
        assertEquals(false, result.turnScreenOff)
    }

    @Test
    fun `rejects unknown parameters`() {
        assertTrue(ScrcpyConfig().withUrlParameters(mapOf("unknown" to "1")).isFailure)
    }

    @Test
    fun `overrides only parameters present in the URL`() {
        val original =
            ScrcpyConfig(
                maxFps = 60,
                clipboardSync = false,
                userVideoEncoder = "saved-encoder",
            )

        val overridden = original.withUrlParameters(mapOf("maxFps" to "120")).getOrThrow()

        assertEquals(120, overridden.maxFps)
        assertEquals(false, overridden.clipboardSync)
        assertEquals("saved-encoder", overridden.userVideoEncoder)
    }

    @Test
    fun `supports actual config field aliases and virtual display toggle`() {
        val overridden =
            ScrcpyConfig(newDisplayEnabled = true)
                .withUrlParameters(
                    mapOf(
                        "enableAudio" to "on",
                        "useFullScreen" to "on",
                        "showFloatingBall" to "off",
                        "enableHardwareDecoding" to "off",
                        "followRemoteOrientation" to "off",
                        "clipboardSync" to "off",
                        "newDisplayEnabled" to "off",
                    ),
                ).getOrThrow()

        assertEquals(true, overridden.enableAudio)
        assertEquals(true, overridden.useFullScreen)
        assertEquals(false, overridden.showFloatingBall)
        assertEquals(false, overridden.enableHardwareDecoding)
        assertEquals(false, overridden.followRemoteOrientation)
        assertEquals(false, overridden.clipboardSync)
        assertEquals(false, overridden.newDisplayEnabled)
    }

    @Test
    fun `compatibility mode disables unsupported streams regardless of URL parameter order`() {
        val compatibilityThenUnsupportedStreams =
            ScrcpyConfig()
                .withUrlParameters(
                    linkedMapOf(
                        "compatibilityMode" to "on",
                        "audio" to "on",
                        "clipboard" to "on",
                    ),
                ).getOrThrow()
        val unsupportedStreamsThenCompatibility =
            ScrcpyConfig()
                .withUrlParameters(
                    linkedMapOf(
                        "audio" to "on",
                        "clipboard" to "on",
                        "compatibilityMode" to "on",
                    ),
                ).getOrThrow()

        assertEquals(false, compatibilityThenUnsupportedStreams.enableAudio)
        assertEquals(false, compatibilityThenUnsupportedStreams.clipboardSync)
        assertEquals(false, unsupportedStreamsThenCompatibility.enableAudio)
        assertEquals(false, unsupportedStreamsThenCompatibility.clipboardSync)
    }

    @Test
    fun `parameter names are normalized while values remain case sensitive`() {
        val overridden =
            ScrcpyConfig()
                .withUrlParameters(
                    mapOf(
                        "MAXSIZE" to "1080",
                        "VIDEOENCODER" to "OMX.Example.Encoder",
                    ),
                ).getOrThrow()

        assertEquals(1080, overridden.maxSize)
        assertEquals("OMX.Example.Encoder", overridden.userVideoEncoder)
        assertTrue(ScrcpyConfig().withUrlParameters(mapOf("audio" to "ON")).isFailure)
        assertTrue(ScrcpyConfig().withUrlParameters(mapOf("tunnelMode" to "DIRECT")).isFailure)
    }
}

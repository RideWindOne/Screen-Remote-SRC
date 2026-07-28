package com.screen.remote.android.app.deeplink

import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.core.domain.model.ScreenRotationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyUrlParametersTest {
    @Test
    fun `serializes every scrcpy config field and restores the same config`() {
        val config =
            ScrcpyConfig(
                compatibilityMode = false,
                gameMode = true,
                useFullScreen = true,
                showFloatingBall = false,
                enableHardwareDecoding = false,
                screenRotationPolicy = ScreenRotationPolicy.LOCAL,
                tunnelMode = ScrcpyTunnelMode.ADB_FORWARD,
                maxSize = 1440,
                videoBitRate = 12_000_000,
                maxFps = 120,
                userVideoEncoder = "c2.vendor.encoder",
                userVideoDecoder = "c2.vendor.decoder",
                enableAudio = true,
                audioBitRate = 256_000,
                userAudioEncoder = "opus",
                userAudioDecoder = "c2.android.opus.decoder",
                clipboardSync = false,
                turnScreenOff = false,
                powerOffOnClose = true,
                cleanupOnDisconnect = true,
                keepDeviceAwake = true,
                stayAwake = true,
                ignoreVideoEncoderConstraints = true,
                newDisplayEnabled = true,
                virtualDisplaySystemDecorations = false,
                preserveVirtualDisplayContent = true,
                startApp = "com.example.app/.MainActivity",
                newDisplay = "1920x1080/420",
                displayId = 2,
                showTouches = true,
                codecOptions = "profile=1,level=2 & vendor=true",
            )

        val parameters = config.toUrlParameters()
        val url = ScreenRemoteDeepLink.ScrcpySession("living room", parameters).toUrl()
        val parsed = parseScreenRemoteDeepLink(url) as ScreenRemoteDeepLink.ScrcpySession

        assertEquals(31, parameters.size)
        assertEquals(config, ScrcpyConfig().withUrlParameters(parameters).getOrThrow())
        assertEquals(parameters, parsed.parameters)
        assertEquals(config, ScrcpyConfig().withUrlParameters(parsed.parameters).getOrThrow())
        assertEquals(
            ScrcpyConfig(),
            ScrcpyConfig().withUrlParameters(ScrcpyConfig().toUrlParameters()).getOrThrow(),
        )
    }

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
                        "screenRotationPolicy" to "target",
                        "clipboardSync" to "off",
                        "newDisplayEnabled" to "off",
                    ),
                ).getOrThrow()

        assertEquals(true, overridden.enableAudio)
        assertEquals(true, overridden.useFullScreen)
        assertEquals(false, overridden.showFloatingBall)
        assertEquals(false, overridden.enableHardwareDecoding)
        assertEquals(ScreenRotationPolicy.TARGET, overridden.screenRotationPolicy)
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

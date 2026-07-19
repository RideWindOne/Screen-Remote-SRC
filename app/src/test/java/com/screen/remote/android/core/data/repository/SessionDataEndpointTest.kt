package com.screen.remote.android.core.data.repository

import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.DeviceCapabilityCache
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class SessionDataEndpointTest {
    @Test
    fun `tunnel mode is shared by persistence and runtime options`() {
        val original = sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)
        val session = original.copy(config = original.config.copy(tunnelMode = ScrcpyTunnelMode.ADB_FORWARD))

        val options = session.toScrcpyOptions()
        val updated = session.fromScrcpyOptions(options)

        assertEquals(ScrcpyTunnelMode.ADB_FORWARD, options.config.tunnelMode)
        assertEquals(ScrcpyTunnelMode.ADB_FORWARD, updated.config.tunnelMode)
    }

    @Test
    fun `zero max size keeps native resolution through options updates`() {
        val session = sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)

        val options = session.toScrcpyOptions()
        val updated = session.fromScrcpyOptions(options)

        assertEquals(0, options.config.maxSize)
        assertEquals(0, updated.config.maxSize)
    }

    @Test
    fun `blank video bitrate uses four megabits per second`() {
        val session = sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)

        assertEquals(4_000_000, session.toScrcpyOptions().config.videoBitRate)
    }

    @Test
    fun `game mode survives options round trip`() {
        val session =
            sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)
                .let { it.copy(config = it.config.copy(gameMode = true)) }

        val options = session.toScrcpyOptions()
        val updated = session.fromScrcpyOptions(options)

        assertTrue(options.config.gameMode)
        assertTrue(updated.config.gameMode)
    }

    @Test
    fun `connection UI options survive options round trip`() {
        val session =
            sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP).let {
                it.copy(config = it.config.copy(useFullScreen = true, showFloatingBall = false))
            }

        val options = session.toScrcpyOptions()
        val updated = session.fromScrcpyOptions(options)

        assertTrue(options.config.useFullScreen)
        assertTrue(updated.config.useFullScreen)
        assertFalse(options.config.showFloatingBall)
        assertFalse(updated.config.showFloatingBall)
    }

    @Test
    fun `virtual display start app survives options round trip`() {
        val session =
            sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP).let {
                it.copy(
                    config =
                        it.config.copy(
                            newDisplayEnabled = true,
                            startApp = "com.example.remote",
                            virtualDisplaySystemDecorations = false,
                            preserveVirtualDisplayContent = true,
                        ),
                )
            }

        val options = session.toScrcpyOptions()
        val updated = session.fromScrcpyOptions(options)

        assertEquals("com.example.remote", options.config.startApp)
        assertEquals("com.example.remote", updated.config.startApp)
        assertFalse(options.config.virtualDisplaySystemDecorations)
        assertFalse(updated.config.virtualDisplaySystemDecorations)
        assertTrue(options.config.preserveVirtualDisplayContent)
        assertTrue(updated.config.preserveVirtualDisplayContent)
    }

    @Test
    fun `mdns session is not treated as usb`() {
        val session =
            sessionData(
                host = "10AEAG2YZS0020P",
                transport = ConnectionTransport.MDNS,
            )

        assertTrue(session.isMdnsConnection())
        assertFalse(session.isUsbConnection())
        assertEquals("mdns:10AEAG2YZS0020P", session.getDeviceIdentifier())
    }

    @Test
    fun `usb session marker still resolves as usb`() {
        val session = sessionData(host = "10AEAG2YZS0020P", transport = ConnectionTransport.USB)

        assertTrue(session.isUsbConnection())
        assertFalse(session.isMdnsConnection())
        assertEquals("usb:10AEAG2YZS0020P", session.getDeviceIdentifier())
    }

    @Test
    fun `clearing auto detected codec state resets every detected field and preserves user choices`() {
        val session =
            sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP).let {
                it.copy(
                    config =
                        ScrcpyConfig(
                            userVideoEncoder = "user-video-encoder",
                            userAudioEncoder = "user-audio-encoder",
                            userVideoDecoder = "user-video-decoder",
                            userAudioDecoder = "user-audio-decoder",
                        ),
                    capabilityCache =
                        DeviceCapabilityCache(
                            deviceSerial = "device-serial",
                            remoteVideoEncoders =
                                listOf(EncoderCapability("video-encoder", "h264", "video/avc", CodecMediaType.VIDEO)),
                            remoteAudioEncoders =
                                listOf(EncoderCapability("audio-encoder", "opus", "audio/opus", CodecMediaType.AUDIO)),
                            selectedVideoEncoder = "auto-video-encoder",
                            selectedAudioEncoder = "auto-audio-encoder",
                            selectedVideoDecoder = "auto-video-decoder",
                            selectedAudioDecoder = "auto-audio-decoder",
                        ),
                )
            }

        val refreshed = session.clearAutoDetectedCodecState()

        assertEquals("", refreshed.capabilityCache.deviceSerial)
        assertTrue(refreshed.capabilityCache.remoteVideoEncoders.isEmpty())
        assertTrue(refreshed.capabilityCache.remoteAudioEncoders.isEmpty())
        assertEquals("", refreshed.capabilityCache.selectedVideoEncoder)
        assertEquals("", refreshed.capabilityCache.selectedAudioEncoder)
        assertEquals("", refreshed.capabilityCache.selectedVideoDecoder)
        assertEquals("", refreshed.capabilityCache.selectedAudioDecoder)
        assertEquals("user-video-encoder", refreshed.config.userVideoEncoder)
        assertEquals("user-audio-encoder", refreshed.config.userAudioEncoder)
        assertEquals("user-video-decoder", refreshed.config.userVideoDecoder)
        assertEquals("user-audio-decoder", refreshed.config.userAudioDecoder)
    }

    @Test
    fun `serialized session stores addresses only in connection candidates`() {
        val root =
            Json.parseToJsonElement(
                Json.encodeToString(sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)),
            ).jsonObject
        val candidate = root.getValue("connectionCandidates").jsonArray.single().jsonObject

        assertFalse("host" in root)
        assertFalse("port" in root)
        assertFalse("peerIdentity" in candidate)
        assertEquals("192.168.1.2", candidate.getValue("host").toString().trim('"'))
    }

    private fun sessionData(
        host: String,
        transport: ConnectionTransport,
    ): SessionData =
        SessionData(
            id = "session",
            name = "Device",
            connectionCandidates =
                listOf(
                    ConnectionCandidateData(
                        transport = transport.name,
                        host = host,
                        port = if (transport == ConnectionTransport.TCP) 5555 else 0,
                    ),
                ),
            color = "BLUE",
        )
}

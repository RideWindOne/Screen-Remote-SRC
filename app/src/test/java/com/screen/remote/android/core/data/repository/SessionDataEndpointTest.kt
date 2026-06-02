package com.screen.remote.android.core.data.repository

import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.EncoderCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class SessionDataEndpointTest {
    @Test
    fun `blank max size keeps native resolution through options updates`() {
        val session = sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)

        val options = session.toScrcpyOptions()
        val updated = session.fromScrcpyOptions(options)

        assertEquals(0, options.maxSize)
        assertEquals("", updated.maxSize)
    }

    @Test
    fun `blank video bitrate uses four megabits per second`() {
        val session = sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP)

        assertEquals(4_000_000, session.toScrcpyOptions().videoBitRate)
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
            sessionData(host = "192.168.1.2", transport = ConnectionTransport.TCP).copy(
                deviceSerial = "device-serial",
                remoteVideoEncoders =
                    listOf(EncoderCapability("video-encoder", "h264", "video/avc", CodecMediaType.VIDEO)),
                remoteAudioEncoders =
                    listOf(EncoderCapability("audio-encoder", "opus", "audio/opus", CodecMediaType.AUDIO)),
                selectedVideoEncoder = "auto-video-encoder",
                selectedAudioEncoder = "auto-audio-encoder",
                selectedVideoDecoder = "auto-video-decoder",
                selectedAudioDecoder = "auto-audio-decoder",
                preferredVideoCodec = "h265",
                preferredAudioCodec = "aac",
                userVideoEncoder = "user-video-encoder",
                userAudioEncoder = "user-audio-encoder",
                userVideoDecoder = "user-video-decoder",
                userAudioDecoder = "user-audio-decoder",
            )

        val refreshed = session.clearAutoDetectedCodecState()

        assertEquals("", refreshed.deviceSerial)
        assertTrue(refreshed.remoteVideoEncoders.isEmpty())
        assertTrue(refreshed.remoteAudioEncoders.isEmpty())
        assertEquals("", refreshed.selectedVideoEncoder)
        assertEquals("", refreshed.selectedAudioEncoder)
        assertEquals("", refreshed.selectedVideoDecoder)
        assertEquals("", refreshed.selectedAudioDecoder)
        assertEquals("h265", refreshed.preferredVideoCodec)
        assertEquals("aac", refreshed.preferredAudioCodec)
        assertEquals("user-video-encoder", refreshed.userVideoEncoder)
        assertEquals("user-audio-encoder", refreshed.userAudioEncoder)
        assertEquals("user-video-decoder", refreshed.userVideoDecoder)
        assertEquals("user-audio-decoder", refreshed.userAudioDecoder)
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

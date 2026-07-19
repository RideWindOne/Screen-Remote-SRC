package com.screen.remote.android.infrastructure.scrcpy.stream

import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import dadb.AdbShellPacket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyStreamProtocolTest {
    @Test
    fun `audio stream disables read timeout after metadata handshake`() {
        val socket = Socket().apply { soTimeout = 10_000 }

        val stream = ScrcpyAudioStream(socket, ByteArrayInputStream(byteArrayOf()), "opus")

        assertEquals(0, socket.soTimeout)
        stream.close()
    }

    @Test
    fun `video stream disables read timeout after metadata handshake`() {
        val socket = Socket().apply { soTimeout = 10_000 }

        val stream =
            ScrcpySocketStream(
                socket = socket,
                inputStream = ByteArrayInputStream(byteArrayOf()),
                codec = "h264",
                onError = {},
            )

        assertEquals(0, socket.soTimeout)
        stream.close()
    }

    @Test
    fun `audio stream preserves protocol PTS config and key flags`() {
        val payload = ByteArray(12) { (it + 1).toByte() }
        val bytes = frameBytes(123_456L or ScrcpyProtocol.PACKET_FLAG_KEY_FRAME, payload)
        val stream = ScrcpyAudioStream(Socket(), ByteArrayInputStream(bytes), "opus")

        val packet = stream.read() as AdbShellPacket.StdOut
        val info = stream.currentFrameInfo()

        assertArrayEquals(payload, packet.payload)
        assertEquals(123_456L, info?.pts)
        assertFalse(info?.isConfig ?: true)
        assertTrue(info?.isKeyFrame == true)
        stream.close()
    }

    @Test
    fun `video stream consumes session metadata then returns the following frame`() {
        val payload = byteArrayOf(0, 0, 0, 1, 0x65)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeLong(ScrcpyProtocol.PACKET_FLAG_SESSION or 1_920L)
            data.writeInt(1_080)
            data.writeLong(99_000L or ScrcpyProtocol.PACKET_FLAG_KEY_FRAME)
            data.writeInt(payload.size)
            data.write(payload)
        }
        var callbackWidth = 0
        var callbackHeight = 0
        val stream =
            ScrcpySocketStream(
                socket = Socket(),
                inputStream = ByteArrayInputStream(output.toByteArray()),
                codec = "h264",
                onError = {},
                onVideoResolution = { width, height ->
                    callbackWidth = width
                    callbackHeight = height
                },
            )

        val packet = stream.read() as AdbShellPacket.StdOut
        val sessionInfo = stream.consumeSessionInfo()
        val frameInfo = stream.currentFrameInfo()

        assertArrayEquals(payload, packet.payload)
        assertNotNull(sessionInfo)
        assertEquals(1_920, sessionInfo?.width)
        assertEquals(1_080, sessionInfo?.height)
        assertEquals(1_920, callbackWidth)
        assertEquals(1_080, callbackHeight)
        assertEquals(99_000L, frameInfo?.pts)
        assertTrue(frameInfo?.isKeyFrame == true)
        stream.close()
    }

    private fun frameBytes(
        ptsAndFlags: Long,
        payload: ByteArray,
    ): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeLong(ptsAndFlags)
                data.writeInt(payload.size)
                data.write(payload)
            }
        }.toByteArray()
}

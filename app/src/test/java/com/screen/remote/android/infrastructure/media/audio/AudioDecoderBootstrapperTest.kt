package com.screen.remote.android.infrastructure.media.audio

import dadb.AdbShellPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioDecoderBootstrapperTest {
    @Test
    fun `aac media frame is preserved when protocol config flag is false`() {
        val payload = byteArrayOf(0x21, 0x10, 0x56, 0x33)
        val bootstrap =
            AudioDecoderBootstrapper(AudioFormatHandler()).readBootstrap(
                SinglePacketAudioStream(payload, AudioFrameInfo(pts = 42_000, isConfig = false, isKeyFrame = false)),
                "aac",
            )

        assertNull(bootstrap?.configData)
        assertArrayEquals(payload, bootstrap?.firstAudioPacket)
        assertEquals(42_000L, bootstrap?.firstAudioPts)
    }

    @Test
    fun `aac config frame is accepted only when protocol flag announces it`() {
        val audioSpecificConfig = byteArrayOf(0x11, 0x90.toByte())
        val bootstrap =
            AudioDecoderBootstrapper(AudioFormatHandler()).readBootstrap(
                SinglePacketAudioStream(
                    audioSpecificConfig,
                    AudioFrameInfo(pts = 0, isConfig = true, isKeyFrame = false),
                ),
                "aac",
            )

        assertArrayEquals(audioSpecificConfig, bootstrap?.configData)
        assertNull(bootstrap?.firstAudioPacket)
    }

    private class SinglePacketAudioStream(
        private val payload: ByteArray,
        private val frameInfo: AudioFrameInfo,
    ) : AudioStream {
        private var consumed = false

        override val codec: String = "aac"
        override val sampleRate: Int = 48_000
        override val channelCount: Int = 2

        override fun read(): AdbShellPacket =
            if (consumed) {
                AdbShellPacket.Exit(byteArrayOf(0))
            } else {
                consumed = true
                AdbShellPacket.StdOut(payload)
            }

        override fun currentFrameInfo(): AudioFrameInfo? = if (consumed) frameInfo else null

        override fun close() = Unit
    }
}

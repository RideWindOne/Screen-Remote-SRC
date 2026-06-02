package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.domain.model.CodecMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbEncoderOutputParserTest {
    @Test
    fun `uses explicit codec argument instead of guessing from implementation name`() {
        val result =
            AdbEncoderOutputParser.parse(
                """
                List of video encoders:
                    --video-codec=vp9 --video-encoder='vendor.opaque.encoder.42' (hw) [vendor]
                """.trimIndent(),
            )

        val encoder = result.videoEncoders.single()
        assertEquals("vendor.opaque.encoder.42", encoder.name)
        assertEquals("vp9", encoder.codec)
        assertEquals("video/x-vnd.on2.vp9", encoder.mimeType)
        assertEquals(CodecMediaType.VIDEO, encoder.mediaType)
        assertEquals(CodecAcceleration.HARDWARE, encoder.acceleration)
        assertTrue(encoder.isVendor)
    }

    @Test
    fun `parses every audio line after the audio section begins`() {
        val result =
            AdbEncoderOutputParser.parse(
                """
                List of video encoders:
                    --video-codec=h264 --video-encoder=c2.video.encoder (hybrid)
                List of audio encoders:
                    --audio-codec=opus --audio-encoder='opaque.audio.one' (sw)
                    --audio-codec=aac --audio-encoder='opaque.audio.two' (hw)
                    --audio-codec=flac --audio-encoder='opaque.audio.three'
                """.trimIndent(),
            )

        assertEquals(listOf("opus", "aac", "flac"), result.audioEncoders.map { it.codec })
        assertEquals(
            listOf("audio/opus", "audio/mp4a-latm", "audio/flac"),
            result.audioEncoders.map { it.mimeType },
        )
        assertEquals(
            listOf("opaque.audio.one", "opaque.audio.two", "opaque.audio.three"),
            result.audioEncoders.map { it.name },
        )
    }
}

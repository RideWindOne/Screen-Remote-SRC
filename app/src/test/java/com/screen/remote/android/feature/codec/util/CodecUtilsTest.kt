package com.screen.remote.android.feature.codec.util

import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecUtilsTest {
    @Test
    fun `save validation accepts a shared encoder decoder MIME`() {
        assertTrue(
            CodecUtils.hasCompatibleMimeType(
                encoders =
                    listOf(
                        encoder("multi.encoder", "h264", "video/avc"),
                        encoder("multi.encoder", "h265", "video/hevc"),
                    ),
                encoderName = "multi.encoder",
                decoderMimeTypes = listOf("video/hevc"),
                mediaType = CodecMediaType.VIDEO,
            ),
        )
    }

    @Test
    fun `save validation rejects encoder decoder without a shared MIME`() {
        assertFalse(
            CodecUtils.hasCompatibleMimeType(
                encoders = listOf(encoder("aac.encoder", "aac", "audio/mp4a-latm", CodecMediaType.AUDIO)),
                encoderName = "aac.encoder",
                decoderMimeTypes = listOf("audio/opus"),
                mediaType = CodecMediaType.AUDIO,
            ),
        )
    }

    @Test
    fun `unknown custom encoder remains eligible for connection time detection`() {
        assertTrue(
            CodecUtils.hasCompatibleMimeType(
                encoders = emptyList(),
                encoderName = "vendor.custom.encoder",
                decoderMimeTypes = listOf("video/avc"),
                mediaType = CodecMediaType.VIDEO,
            ),
        )
    }

    private fun encoder(
        name: String,
        codec: String,
        mimeType: String,
        mediaType: CodecMediaType = CodecMediaType.VIDEO,
    ) = EncoderCapability(
        name = name,
        codec = codec,
        mimeType = mimeType,
        mediaType = mediaType,
    )
}

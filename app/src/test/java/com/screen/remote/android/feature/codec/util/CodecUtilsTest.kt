package com.screen.remote.android.feature.codec.util

import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecUtilsTest {
    @Test
    fun `same implementation name is resolved by selected codec identity`() {
        val encoders =
            listOf(
                encoder(codec = "h264", mimeType = "video/avc"),
                encoder(codec = "h265", mimeType = "video/hevc"),
            )

        assertEquals(
            "video/hevc",
            CodecUtils.resolveEncoderMimeType(
                encoders = encoders,
                encoderName = "vendor.multi.encoder",
                preferredCodec = "h265",
                type = CodecUtils.CodecType.VIDEO,
            ),
        )
    }

    @Test
    fun `ambiguous implementation does not invent unsupported preferred mime`() {
        val encoders =
            listOf(
                encoder(codec = "h264", mimeType = "video/avc"),
                encoder(codec = "h265", mimeType = "video/hevc"),
            )

        assertNull(
            CodecUtils.resolveEncoderMimeType(
                encoders = encoders,
                encoderName = "vendor.multi.encoder",
                preferredCodec = "vp9",
                type = CodecUtils.CodecType.VIDEO,
            ),
        )
    }

    private fun encoder(
        codec: String,
        mimeType: String,
    ) = EncoderCapability(
        name = "vendor.multi.encoder",
        codec = codec,
        mimeType = mimeType,
        mediaType = CodecMediaType.VIDEO,
    )
}

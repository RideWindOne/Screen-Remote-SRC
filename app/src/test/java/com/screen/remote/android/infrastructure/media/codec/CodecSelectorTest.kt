package com.screen.remote.android.infrastructure.media.codec

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.DecoderCapability
import com.screen.remote.android.core.domain.model.EncoderCapability
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecSelectorTest {
    @Test
    fun `structured MIME matches even when implementation names contain no codec token`() {
        val result =
            select(
                mediaType = CodecMediaType.VIDEO,
                remoteEncoders = listOf(encoder("vendor.remote.encoder.42", "h265", "video/hevc")),
                localDecoders = listOf(decoder("vendor.local.decoder.73", "VIDEO/HEVC")),
            )

        assertEquals(
            CodecSelectionResult(
                encoder = "vendor.remote.encoder.42",
                decoder = "vendor.local.decoder.73",
                codec = "h265",
                mimeType = "video/hevc",
            ),
            result,
        )
    }

    @Test
    fun `automatic selection uses the first compatible catalog format`() {
        val result =
            select(
                mediaType = CodecMediaType.VIDEO,
                remoteEncoders = listOf(encoder("opaque.encoder", "h264", "video/avc")),
                localDecoders = listOf(decoder("opaque.decoder", "video/avc")),
            )

        assertEquals("h264", result?.codec)
        assertEquals("video/avc", result?.mimeType)
    }

    @Test
    fun `raw audio is passthrough and does not require MediaCodec implementations`() {
        val result =
            select(
                mediaType = CodecMediaType.AUDIO,
                remoteEncoders = emptyList(),
                localDecoders = emptyList(),
            )

        assertEquals(
            CodecSelectionResult(encoder = "", decoder = "", codec = "raw", mimeType = "audio/raw"),
            result,
        )
    }

    @Test
    fun `compressed audio falls back to raw when no encoder decoder pair exists`() {
        val result =
            select(
                mediaType = CodecMediaType.AUDIO,
                remoteEncoders = emptyList(),
                localDecoders = emptyList(),
            )

        assertEquals(
            CodecSelectionResult(encoder = "", decoder = "", codec = "raw", mimeType = "audio/raw"),
            result,
        )
    }

    @Test
    fun `user decoder constrains selection to a compatible format`() {
        val result =
            select(
                mediaType = CodecMediaType.VIDEO,
                remoteEncoders =
                    listOf(
                        encoder("remote.hevc", "h265", "video/hevc"),
                        encoder("remote.avc", "h264", "video/avc"),
                    ),
                localDecoders = listOf(decoder("user.decoder", "video/avc")),
                userDecoder = "user.decoder",
            )

        assertEquals("h264", result?.codec)
        assertEquals("remote.avc", result?.encoder)
        assertEquals("user.decoder", result?.decoder)
    }

    @Test
    fun `software-only policy excludes hardware decoder`() {
        val result =
            CodecSelector.selectBestCodec(
                mediaType = CodecMediaType.VIDEO,
                remoteEncoders = listOf(encoder("remote.avc", "h264", "video/avc")),
                localDecoders =
                    listOf(
                        decoder("hardware.decoder", "video/avc"),
                        DecoderCapability(
                            name = "software.decoder",
                            mimeTypes = listOf("video/avc"),
                            acceleration = CodecAcceleration.SOFTWARE,
                        ),
                    ),
                userEncoder = null,
                userDecoder = null,
                logTag = LogTags.VIDEO_DECODER,
                allowHardwareDecoders = false,
            )

        assertEquals("software.decoder", result?.decoder)
    }

    @Test
    fun `low latency decoder wins among equivalent hardware candidates`() {
        val result =
            select(
                mediaType = CodecMediaType.VIDEO,
                remoteEncoders = listOf(encoder("remote.avc", "h264", "video/avc")),
                localDecoders =
                    listOf(
                        decoder("ordinary.decoder", "video/avc"),
                        DecoderCapability(
                            name = "low.latency.decoder",
                            mimeTypes = listOf("video/avc"),
                            acceleration = CodecAcceleration.HARDWARE,
                            lowLatencyMimeTypes = listOf("video/avc"),
                        ),
                    ),
            )

        assertEquals("low.latency.decoder", result?.decoder)
    }

    @Test
    fun `incompatible manual pair is ignored and automatic pair is selected`() {
        val result =
            select(
                mediaType = CodecMediaType.VIDEO,
                remoteEncoders =
                    listOf(
                        encoder("manual.hevc.encoder", "h265", "video/hevc"),
                        encoder("auto.avc.encoder", "h264", "video/avc"),
                    ),
                localDecoders =
                    listOf(
                        decoder("manual.avc.decoder", "video/avc"),
                        decoder("auto.avc.decoder", "video/avc"),
                    ),
                userEncoder = "manual.hevc.encoder",
                userDecoder = "manual.avc.decoder",
            )

        assertEquals("h264", result?.codec)
        assertEquals("auto.avc.encoder", result?.encoder)
        assertEquals(true, result?.ignoredUserSelection)
    }

    @Test
    fun `incompatible manual audio pair is ignored and opus pair is selected`() {
        val result =
            select(
                mediaType = CodecMediaType.AUDIO,
                remoteEncoders =
                    listOf(
                        encoder("manual.aac.encoder", "aac", "audio/mp4a-latm"),
                        encoder("auto.opus.encoder", "opus", "audio/opus"),
                    ),
                localDecoders =
                    listOf(
                        decoder("manual.opus.decoder", "audio/opus"),
                        decoder("auto.opus.decoder", "audio/opus"),
                    ),
                userEncoder = "manual.aac.encoder",
                userDecoder = "manual.opus.decoder",
            )

        assertEquals("opus", result?.codec)
        assertEquals("auto.opus.encoder", result?.encoder)
        assertEquals(true, result?.ignoredUserSelection)
    }

    private fun select(
        mediaType: CodecMediaType,
        remoteEncoders: List<EncoderCapability>,
        localDecoders: List<DecoderCapability>,
        userEncoder: String? = null,
        userDecoder: String? = null,
    ): CodecSelectionResult? =
        CodecSelector.selectBestCodec(
            mediaType = mediaType,
            remoteEncoders = remoteEncoders,
            localDecoders = localDecoders,
            userEncoder = userEncoder,
            userDecoder = userDecoder,
            // This category is disabled by default, keeping the pure selection tests independent
            // from the Android Log stub used by local JVM tests.
            logTag = LogTags.VIDEO_DECODER,
        )

    private fun encoder(
        name: String,
        codec: String,
        mimeType: String,
    ) = EncoderCapability(
        name = name,
        codec = codec,
        mimeType = mimeType,
        mediaType = if (mimeType.startsWith("video/")) CodecMediaType.VIDEO else CodecMediaType.AUDIO,
        acceleration = CodecAcceleration.HARDWARE,
    )

    private fun decoder(
        name: String,
        vararg mimeTypes: String,
    ) = DecoderCapability(
        name = name,
        mimeTypes = mimeTypes.toList(),
        acceleration = CodecAcceleration.HARDWARE,
    )
}

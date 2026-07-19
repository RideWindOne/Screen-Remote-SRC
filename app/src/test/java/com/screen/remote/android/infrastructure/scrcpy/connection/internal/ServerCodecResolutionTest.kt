package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerCodecResolutionTest {
    @Test
    fun `resolved video codec wins when one encoder implementation supports several MIME types`() {
        val encoderName = "vendor.multi.encoder"
        val options =
            options().copy(
                capabilityCache =
                    options().capabilityCache.copy(
                        selectedVideoCodec = "h264",
                        selectedVideoEncoder = encoderName,
                        remoteVideoEncoders =
                            listOf(
                                encoder(encoderName, "h265", "video/hevc", CodecMediaType.VIDEO),
                                encoder(encoderName, "h264", "video/avc", CodecMediaType.VIDEO),
                            ),
                    ),
            )

        assertEquals("h264", resolveVideoCodec(options, options.getFinalVideoEncoder()))
    }

    @Test
    fun `resolved audio codec wins when one encoder implementation supports several MIME types`() {
        val encoderName = "vendor.multi.audio.encoder"
        val options =
            options().copy(
                capabilityCache =
                    options().capabilityCache.copy(
                        selectedAudioCodec = "aac",
                        selectedAudioEncoder = encoderName,
                        remoteAudioEncoders =
                            listOf(
                                encoder(encoderName, "opus", "audio/opus", CodecMediaType.AUDIO),
                                encoder(encoderName, "aac", "audio/mp4a-latm", CodecMediaType.AUDIO),
                            ),
                    ),
            )

        assertEquals("aac", resolveAudioCodec(options, options.getFinalAudioEncoder()))
    }

    @Test
    fun `resolved raw audio needs neither encoder nor decoder`() {
        val original = options()
        val options = original.copy(capabilityCache = original.capabilityCache.copy(selectedAudioCodec = "raw"))

        assertEquals("raw", resolveAudioCodec(options, ""))
    }

    private fun options() =
        ScrcpyOptions(
            sessionId = "session",
            connectionCandidates = listOf(ConnectionCandidate(ConnectionTransport.TCP, "device", 5555)),
        )

    private fun encoder(
        name: String,
        codec: String,
        mimeType: String,
        mediaType: CodecMediaType,
    ) = EncoderCapability(
        name = name,
        codec = codec,
        mimeType = mimeType,
        mediaType = mediaType,
    )
}

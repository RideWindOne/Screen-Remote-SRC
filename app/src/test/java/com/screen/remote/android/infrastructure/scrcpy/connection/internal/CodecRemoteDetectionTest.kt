package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.DeviceCapabilityCache
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecRemoteDetectionTest {
    @Test
    fun `video-only detection reuses video cache without audio cache`() {
        val options = options(video = listOf(videoEncoder()))

        assertTrue(hasRequiredRemoteEncoderCache(options, needVideo = true, needAudio = false))
    }

    @Test
    fun `missing required side does not count as complete cache`() {
        val videoOnly = options(video = listOf(videoEncoder()))
        val audioOnly = options(audio = listOf(audioEncoder()))

        assertFalse(hasRequiredRemoteEncoderCache(videoOnly, needVideo = false, needAudio = true))
        assertFalse(hasRequiredRemoteEncoderCache(audioOnly, needVideo = true, needAudio = false))
    }

    @Test
    fun `both required sides must be cached`() {
        assertFalse(
            hasRequiredRemoteEncoderCache(
                options(video = listOf(videoEncoder())),
                needVideo = true,
                needAudio = true,
            ),
        )
        assertTrue(
            hasRequiredRemoteEncoderCache(
                options(
                    video = listOf(videoEncoder()),
                    audio = listOf(audioEncoder()),
                ),
                needVideo = true,
                needAudio = true,
            ),
        )
    }

    private fun options(
        video: List<EncoderCapability> = emptyList(),
        audio: List<EncoderCapability> = emptyList(),
    ): ScrcpyOptions =
        ScrcpyOptions(
            sessionId = "session",
            connectionCandidates = listOf(ConnectionCandidate(ConnectionTransport.TCP, "device", 5555)),
            capabilityCache = DeviceCapabilityCache(remoteVideoEncoders = video, remoteAudioEncoders = audio),
        )

    private fun videoEncoder() =
        EncoderCapability(
            name = "c2.qti.avc.encoder",
            codec = CodecCatalog.DEFAULT_VIDEO_CODEC,
            mimeType = "video/avc",
            mediaType = CodecMediaType.VIDEO,
        )

    private fun audioEncoder() =
        EncoderCapability(
            name = "c2.android.opus.encoder",
            codec = CodecCatalog.DEFAULT_AUDIO_CODEC,
            mimeType = "audio/opus",
            mediaType = CodecMediaType.AUDIO,
        )
}

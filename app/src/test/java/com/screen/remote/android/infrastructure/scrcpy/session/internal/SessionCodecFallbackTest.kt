package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCodecFallbackTest {
    @Test
    fun `ignored video selection clears both manual video fields only`() {
        val options =
            options().copy(
                config =
                    options().config.copy(
                        userVideoEncoder = "bad-video-encoder",
                        userVideoDecoder = "bad-video-decoder",
                        userAudioEncoder = "valid-audio-encoder",
                        userAudioDecoder = "valid-audio-decoder",
                    ),
            )

        val cleared = options.clearIgnoredUserCodecSelections(clearVideo = true, clearAudio = false)

        assertEquals("", cleared.config.userVideoEncoder)
        assertEquals("", cleared.config.userVideoDecoder)
        assertEquals("valid-audio-encoder", cleared.config.userAudioEncoder)
        assertEquals("valid-audio-decoder", cleared.config.userAudioDecoder)
    }

    @Test
    fun `ignored audio selection clears both manual audio fields only`() {
        val options =
            options().copy(
                config =
                    options().config.copy(
                        userVideoEncoder = "valid-video-encoder",
                        userVideoDecoder = "valid-video-decoder",
                        userAudioEncoder = "bad-audio-encoder",
                        userAudioDecoder = "bad-audio-decoder",
                    ),
            )

        val cleared = options.clearIgnoredUserCodecSelections(clearVideo = false, clearAudio = true)

        assertEquals("valid-video-encoder", cleared.config.userVideoEncoder)
        assertEquals("valid-video-decoder", cleared.config.userVideoDecoder)
        assertEquals("", cleared.config.userAudioEncoder)
        assertEquals("", cleared.config.userAudioDecoder)
    }

    private fun options() =
        ScrcpyOptions(
            sessionId = "session",
            connectionCandidates = listOf(ConnectionCandidate(ConnectionTransport.TCP, "device", 5555)),
        )
}

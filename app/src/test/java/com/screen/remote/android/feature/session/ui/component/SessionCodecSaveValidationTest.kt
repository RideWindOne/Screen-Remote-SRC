package com.screen.remote.android.feature.session.ui.component

import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecSaveValidationTest {
    @Test
    fun `video encoder format mismatch is rejected at save validation`() {
        val state = SessionDialogState().apply {
            preferredVideoCodec = "h265"
            userVideoEncoder = "vendor.avc.encoder"
            remoteVideoEncoders =
                listOf(
                    EncoderCapability("vendor.avc.encoder", "h264", "video/avc", CodecMediaType.VIDEO),
                )
        }

        assertFalse(isVideoCodecSelectionCompatible(state))
    }

    @Test
    fun `matching video encoder format passes save validation`() {
        val state = SessionDialogState().apply {
            preferredVideoCodec = "h264"
            userVideoEncoder = "vendor.avc.encoder"
            remoteVideoEncoders =
                listOf(
                    EncoderCapability("vendor.avc.encoder", "h264", "video/avc", CodecMediaType.VIDEO),
                )
        }

        assertTrue(isVideoCodecSelectionCompatible(state))
    }

    @Test
    fun `disabled audio skips audio codec save validation`() {
        val state = SessionDialogState().apply {
            enableAudio = false
            preferredAudioCodec = "aac"
            userAudioEncoder = "vendor.opus.encoder"
            remoteAudioEncoders =
                listOf(
                    EncoderCapability("vendor.opus.encoder", "opus", "audio/opus", CodecMediaType.AUDIO),
                )
        }

        assertTrue(isAudioCodecSelectionCompatible(state))
    }

    @Test
    fun `enabled audio encoder format mismatch is rejected at save validation`() {
        val state = SessionDialogState().apply {
            enableAudio = true
            preferredAudioCodec = "aac"
            userAudioEncoder = "vendor.opus.encoder"
            remoteAudioEncoders =
                listOf(
                    EncoderCapability("vendor.opus.encoder", "opus", "audio/opus", CodecMediaType.AUDIO),
                )
        }

        assertFalse(isAudioCodecSelectionCompatible(state))
    }
}

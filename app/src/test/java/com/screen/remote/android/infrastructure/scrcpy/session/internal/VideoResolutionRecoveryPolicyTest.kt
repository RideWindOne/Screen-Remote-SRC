package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoResolutionRecoveryPolicyTest {
    @Test
    fun `recovery descends through intermediate sizes to 540`() {
        assertEquals(1920, nextVideoRecoveryMaxSize(currentMaxSize = 0))
        assertEquals(1600, nextVideoRecoveryMaxSize(currentMaxSize = 1920))
        assertEquals(1280, nextVideoRecoveryMaxSize(currentMaxSize = 1600))
        assertEquals(1080, nextVideoRecoveryMaxSize(currentMaxSize = 1280))
        assertEquals(720, nextVideoRecoveryMaxSize(currentMaxSize = 1080))
        assertEquals(540, nextVideoRecoveryMaxSize(currentMaxSize = 720))
        assertNull(nextVideoRecoveryMaxSize(currentMaxSize = 540))
    }

    @Test
    fun `terminal recovery failure exposes user guidance without localizing log detail`() {
        val state =
            SessionState.Failed(
                SessionIssue(
                    kind = SessionIssueKind.RuntimeFailure,
                    detail = "Video encoder runtime failure: encoder=encoder.test",
                    userMessage = "Choose another video encoder or enable compatibility mode.",
                ),
            )

        assertEquals("Choose another video encoder or enable compatibility mode.", state.reason)
        assertEquals("Video encoder runtime failure: encoder=encoder.test", state.issue.message)
    }

    @Test
    fun `runtime dequeue failure remains eligible for size recovery`() {
        assertEquals(
            false,
            isDefinitiveVideoEncoderFailure(
                "Capture/encoding error: java.lang.IllegalStateException at MediaCodec.native_dequeueOutputBuffer",
            ),
        )
    }

    @Test
    fun `missing encoder bypasses size recovery`() {
        assertEquals(
            true,
            isDefinitiveVideoEncoderFailure("Video encoder not found: OMX.vendor.h264.encoder"),
        )
    }
}

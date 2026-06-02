package com.screen.remote.android.infrastructure.media.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPresentationTimestampTest {
    @Test
    fun scrcpyMicrosecondsArePassedToMediaCodecWithoutRescaling() {
        assertEquals(1_234_567L, mediaCodecPresentationTimeUs(1_234_567L))
    }
}

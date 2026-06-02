package com.screen.remote.android.infrastructure.media.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDecoderSizePolicyTest {
    @Test
    fun portraitSizeIsScaledToEvenDimensionsWithoutChangingOrientation() {
        assertEquals(
            ScaledVideoSize(width = 920, height = 2048),
            scaleVideoSizeToLongEdge(width = 1080, height = 2400, maxLongEdge = 2048),
        )
    }

    @Test
    fun landscapeSizeUsesTheSameAspectRatioPolicy() {
        assertEquals(
            ScaledVideoSize(width = 1920, height = 1080),
            scaleVideoSizeToLongEdge(width = 2560, height = 1440, maxLongEdge = 1920),
        )
    }

    @Test
    fun sizeWithinLimitIsNotUpscaled() {
        assertEquals(
            ScaledVideoSize(width = 1280, height = 720),
            scaleVideoSizeToLongEdge(width = 1280, height = 720, maxLongEdge = 1920),
        )
    }

    @Test
    fun fallbackCandidatesAreStrictlyBelowCurrentLongEdge() {
        assertEquals(
            listOf(2048, 1920, 1600, 1280, 1080, 720),
            decoderFallbackLongEdges(currentLongEdge = 2400),
        )
    }
}

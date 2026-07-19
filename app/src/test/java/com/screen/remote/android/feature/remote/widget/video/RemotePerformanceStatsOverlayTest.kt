package com.screen.remote.android.feature.remote.widget.video

import com.screen.remote.android.infrastructure.media.video.VideoPerformanceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemotePerformanceStatsOverlayTest {
    @Test
    fun `sample calculates frame and bit rates over elapsed time`() {
        val sample =
            calculateRemotePerformanceSample(
                previousVideo = VideoPerformanceSnapshot(1_000, 10, 8),
                currentVideo = VideoPerformanceSnapshot(501_000, 130, 108),
                previousTxBytes = 10_000,
                currentTxBytes = 35_000,
                previousRxBytes = 50_000,
                currentRxBytes = 1_050_000,
                elapsedSeconds = 2.0,
            )

        assertEquals(60.0, sample.decodedFps, 0.001)
        assertEquals(50.0, sample.renderedFps, 0.001)
        assertEquals(2_000_000.0, sample.videoBitsPerSecond, 0.001)
        assertEquals(100_000.0, sample.networkTxBitsPerSecond ?: 0.0, 0.001)
        assertEquals(4_000_000.0, sample.networkRxBitsPerSecond ?: 0.0, 0.001)
    }

    @Test
    fun `sample tolerates reset counters and unsupported network stats`() {
        val sample =
            calculateRemotePerformanceSample(
                previousVideo = VideoPerformanceSnapshot(10_000, 100, 100),
                currentVideo = VideoPerformanceSnapshot(1_000, 5, 4),
                previousTxBytes = null,
                currentTxBytes = null,
                previousRxBytes = null,
                currentRxBytes = null,
                elapsedSeconds = 1.0,
            )

        assertEquals(0.0, sample.decodedFps, 0.001)
        assertEquals(0.0, sample.renderedFps, 0.001)
        assertEquals(0.0, sample.videoBitsPerSecond, 0.001)
        assertNull(sample.networkTxBitsPerSecond)
        assertNull(sample.networkRxBitsPerSecond)
    }
}

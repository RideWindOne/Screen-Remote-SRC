package com.screen.remote.android.infrastructure.media.video

import java.util.concurrent.atomic.AtomicLong

/**
 * Lock-free cumulative counters sampled by the UI once per second.
 * Keeping the hot decode path free of StateFlow updates avoids recomposing at video frame rate.
 */
class VideoPerformanceCounters {
    private val receivedBytes = AtomicLong()
    private val decodedFrames = AtomicLong()
    private val renderedFrames = AtomicLong()

    fun recordPacket(byteCount: Int) {
        if (byteCount > 0) {
            receivedBytes.addAndGet(byteCount.toLong())
        }
    }

    fun recordDecodedFrame(rendered: Boolean) {
        decodedFrames.incrementAndGet()
        if (rendered) {
            renderedFrames.incrementAndGet()
        }
    }

    fun snapshot(): VideoPerformanceSnapshot =
        VideoPerformanceSnapshot(
            receivedBytes = receivedBytes.get(),
            decodedFrames = decodedFrames.get(),
            renderedFrames = renderedFrames.get(),
        )

    fun reset() {
        receivedBytes.set(0)
        decodedFrames.set(0)
        renderedFrames.set(0)
    }
}

data class VideoPerformanceSnapshot(
    val receivedBytes: Long,
    val decodedFrames: Long,
    val renderedFrames: Long,
)

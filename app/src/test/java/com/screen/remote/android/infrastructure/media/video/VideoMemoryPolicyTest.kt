package com.screen.remote.android.infrastructure.media.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMemoryPolicyTest {
    @Test
    fun `low memory heap limits individual packet and cache allocations`() {
        val heapBytes = 48L * 1024L * 1024L

        assertEquals(6 * 1024 * 1024, VideoMemoryPolicy.maxPacketBytes(heapBytes))
        assertEquals(6 * 1024 * 1024, VideoMemoryPolicy.maxBootstrapCacheBytes(heapBytes))
    }

    @Test
    fun `large heap retains existing protocol packet ceiling`() {
        val heapBytes = 512L * 1024L * 1024L

        assertEquals(32 * 1024 * 1024, VideoMemoryPolicy.maxPacketBytes(heapBytes))
        assertEquals(16 * 1024 * 1024, VideoMemoryPolicy.maxBootstrapCacheBytes(heapBytes))
    }
}

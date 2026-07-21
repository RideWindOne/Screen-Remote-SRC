package com.screen.remote.android.infrastructure.media.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDecoderBootstrapCacheTest {
    @Test
    fun `snapshot contains config and complete latest gop`() {
        val cache = VideoDecoderBootstrapCache(maxBytes = 32)

        cache.record(bytes(1, 2), ptsUs = 0, isConfig = true, isKeyFrame = false)
        cache.record(bytes(3, 4, 5), ptsUs = 10, isConfig = false, isKeyFrame = true)
        cache.record(bytes(6, 7), ptsUs = 20, isConfig = false, isKeyFrame = false)

        val snapshot = cache.snapshot()
        assertTrue(snapshot.isReplayable)
        assertArrayEquals(bytes(1, 2), snapshot.config?.data)
        assertEquals(listOf(10L, 20L), snapshot.frames.map { it.ptsUs })
    }

    @Test
    fun `new key frame replaces the previous gop`() {
        val cache = VideoDecoderBootstrapCache(maxBytes = 32)

        cache.record(bytes(1), ptsUs = 10, isConfig = false, isKeyFrame = true)
        cache.record(bytes(2), ptsUs = 20, isConfig = false, isKeyFrame = false)
        cache.record(bytes(3), ptsUs = 30, isConfig = false, isKeyFrame = true)
        cache.record(bytes(4), ptsUs = 40, isConfig = false, isKeyFrame = false)

        assertEquals(listOf(30L, 40L), cache.snapshot().frames.map { it.ptsUs })
    }

    @Test
    fun `overflow invalidates the entire gop until another key frame`() {
        val cache = VideoDecoderBootstrapCache(maxBytes = 6)

        cache.record(bytes(1, 2), ptsUs = 0, isConfig = true, isKeyFrame = false)
        cache.record(bytes(3, 4), ptsUs = 10, isConfig = false, isKeyFrame = true)
        cache.record(bytes(5, 6, 7), ptsUs = 20, isConfig = false, isKeyFrame = false)
        cache.record(bytes(8), ptsUs = 30, isConfig = false, isKeyFrame = false)

        val overflowed = cache.snapshot()
        assertFalse(overflowed.isReplayable)
        assertTrue(overflowed.frames.isEmpty())
        assertArrayEquals(bytes(1, 2), overflowed.config?.data)

        cache.record(bytes(9, 10), ptsUs = 40, isConfig = false, isKeyFrame = true)
        assertTrue(cache.snapshot().isReplayable)
    }

    @Test
    fun `new config invalidates frames from the previous generation`() {
        val cache = VideoDecoderBootstrapCache(maxBytes = 32)

        cache.record(bytes(1), ptsUs = 10, isConfig = false, isKeyFrame = true)
        cache.record(bytes(2), ptsUs = 20, isConfig = false, isKeyFrame = false)
        cache.record(bytes(9), ptsUs = 30, isConfig = true, isKeyFrame = false)

        val snapshot = cache.snapshot()
        assertFalse(snapshot.isReplayable)
        assertTrue(snapshot.frames.isEmpty())
        assertArrayEquals(bytes(9), snapshot.config?.data)
    }

    @Test
    fun `snapshot owns copies of packet data`() {
        val cache = VideoDecoderBootstrapCache(maxBytes = 32)
        val keyFrame = bytes(1, 2)
        cache.record(keyFrame, ptsUs = 10, isConfig = false, isKeyFrame = true)
        keyFrame[0] = 9

        val firstSnapshot = cache.snapshot()
        firstSnapshot.frames.single().data[1] = 8

        assertArrayEquals(bytes(1, 2), cache.snapshot().frames.single().data)
        assertNull(cache.snapshot().config)
    }

    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
}

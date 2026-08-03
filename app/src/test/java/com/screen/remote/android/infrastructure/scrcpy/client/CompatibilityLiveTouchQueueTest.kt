package com.screen.remote.android.infrastructure.scrcpy.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompatibilityLiveTouchQueueTest {
    @Test
    fun `down is preserved while pending moves are coalesced`() {
        val queue = CompatibilityLiveTouchQueue()

        queue.offer(event(action = 0, x = 10))
        queue.offer(event(action = 2, x = 20))
        queue.offer(event(action = 2, x = 30))

        assertEquals(event(action = 0, x = 10), queue.poll())
        assertEquals(event(action = 2, x = 30), queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `up preserves the final move needed to establish a drag`() {
        val queue = CompatibilityLiveTouchQueue()

        queue.offer(event(action = 2, x = 20))
        queue.offer(event(action = 1, x = 40))

        assertEquals(event(action = 2, x = 20), queue.poll())
        assertEquals(event(action = 1, x = 40), queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `cancel remains ordered after the final move`() {
        val queue = CompatibilityLiveTouchQueue()

        queue.offer(event(action = 2, x = 20))
        queue.offer(event(action = 3, x = 50))

        assertEquals(event(action = 2, x = 20), queue.poll())
        assertEquals(event(action = 3, x = 50), queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `overflow drops oldest move event and keeps pointer lifecycle events`() {
        val queue = CompatibilityLiveTouchQueue()

        queue.offer(event(action = 0, x = 10))
        queue.offer(event(action = 2, x = 20))
        queue.offer(event(action = 1, x = 30))
        queue.offer(event(action = 0, x = 40))

        assertEquals(event(action = 0, x = 10), queue.poll())
        assertEquals(event(action = 1, x = 30), queue.poll())
        assertEquals(event(action = 0, x = 40), queue.poll())
        assertNull(queue.poll())
    }

    private fun event(
        action: Int,
        x: Int,
    ) = CompatibilityLiveTouchEvent(
        action = action,
        pointerId = 7L,
        x = x,
        y = 100,
    )
}

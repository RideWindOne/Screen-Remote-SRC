package com.screen.remote.android.infrastructure.scrcpy.controller

import com.screen.remote.android.infrastructure.scrcpy.connection.TouchAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchTransportTimingTest {
    @Test
    fun `game mode enables pacing and disabling it clears timing state`() {
        var nowNanos = 500_000_000L
        val timing = TouchTransportTiming(nanoTime = { nowNanos })

        timing.configureGameMode(enabled = true)
        timing.onTouchSent(TouchAction.ACTION_DOWN, pointerId = 4L)
        timing.onTouchSent(TouchAction.ACTION_MOVE, pointerId = 4L)

        assertEquals(20_000_000L, timing.remainingHoldDelayNanos(pointerId = 4L))
        assertEquals(5_000_000L, timing.remainingMoveDelayNanos())

        nowNanos += 1_000_000L
        timing.configureGameMode(enabled = false)

        assertEquals(0L, timing.remainingHoldDelayNanos(pointerId = 4L))
        assertEquals(0L, timing.remainingMoveDelayNanos())
    }

    @Test
    fun `up is delayed until remote pointer has been down for minimum duration`() {
        var nowNanos = 1_000_000_000L
        val timing =
            TouchTransportTiming(
                minimumHoldDurationNanos = 20_000_000L,
                moveIntervalNanos = 8_000_000L,
                nanoTime = { nowNanos },
            )

        timing.onTouchSent(TouchAction.ACTION_DOWN, pointerId = 7L)
        assertEquals(20_000_000L, timing.remainingHoldDelayNanos(pointerId = 7L))

        nowNanos += 6_000_000L
        assertEquals(14_000_000L, timing.remainingHoldDelayNanos(pointerId = 7L))

        nowNanos += 14_000_000L
        assertEquals(0L, timing.remainingHoldDelayNanos(pointerId = 7L))
    }

    @Test
    fun `hold timing is independent for simultaneous pointers and clears on up`() {
        var nowNanos = 5_000_000_000L
        val timing =
            TouchTransportTiming(
                minimumHoldDurationNanos = 20_000_000L,
                moveIntervalNanos = 8_000_000L,
                nanoTime = { nowNanos },
            )

        timing.onTouchSent(TouchAction.ACTION_DOWN, pointerId = 1L)
        nowNanos += 12_000_000L
        timing.onTouchSent(TouchAction.ACTION_DOWN, pointerId = 2L)

        assertEquals(8_000_000L, timing.remainingHoldDelayNanos(pointerId = 1L))
        assertEquals(20_000_000L, timing.remainingHoldDelayNanos(pointerId = 2L))

        timing.onTouchSent(TouchAction.ACTION_UP, pointerId = 1L)
        assertEquals(0L, timing.remainingHoldDelayNanos(pointerId = 1L))
        assertEquals(20_000_000L, timing.remainingHoldDelayNanos(pointerId = 2L))
    }

    @Test
    fun `ordinary moves are globally rate limited`() {
        var nowNanos = 9_000_000_000L
        val timing =
            TouchTransportTiming(
                minimumHoldDurationNanos = 20_000_000L,
                moveIntervalNanos = 8_000_000L,
                nanoTime = { nowNanos },
            )

        assertEquals(0L, timing.remainingMoveDelayNanos())
        timing.onTouchSent(TouchAction.ACTION_MOVE, pointerId = 1L)
        assertEquals(8_000_000L, timing.remainingMoveDelayNanos())

        nowNanos += 3_000_000L
        assertEquals(5_000_000L, timing.remainingMoveDelayNanos())

        nowNanos += 5_000_000L
        assertEquals(0L, timing.remainingMoveDelayNanos())
    }

    @Test
    fun `clear removes hold and move timing from previous session`() {
        var nowNanos = 11_000_000_000L
        val timing =
            TouchTransportTiming(
                minimumHoldDurationNanos = 20_000_000L,
                moveIntervalNanos = 8_000_000L,
                nanoTime = { nowNanos },
            )

        timing.onTouchSent(TouchAction.ACTION_DOWN, pointerId = 3L)
        timing.onTouchSent(TouchAction.ACTION_MOVE, pointerId = 3L)
        timing.clear()

        assertEquals(0L, timing.remainingHoldDelayNanos(pointerId = 3L))
        assertEquals(0L, timing.remainingMoveDelayNanos())
    }
}

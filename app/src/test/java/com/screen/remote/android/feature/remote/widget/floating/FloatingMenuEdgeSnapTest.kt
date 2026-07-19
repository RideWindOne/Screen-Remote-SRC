package com.screen.remote.android.feature.remote.widget.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingMenuEdgeSnapTest {
    @Test
    fun `right snap hides three fifths of ball A and keeps centers aligned`() {
        val result =
            calculateFloatingMenuSnapCoordinates(
                edge = FloatingMenuGestureState.Edge.RIGHT,
                displayWidth = 800,
                displayHeight = 400,
                ballAWidth = 48,
                ballAHeight = 48,
                ballBWidth = 40,
                ballBHeight = 40,
                currentAX = 500,
                currentAY = 100,
            )

        assertEquals(752, result.targetAX)
        assertEquals(19, calculateFloatingBallVisibleSize(48, 48))
        assertEquals(result.targetAX + 24, result.targetBX + 20)
        assertEquals(result.targetAY + 24, result.targetBY + 20)
    }

    @Test
    fun `top snap hides three fifths and keeps centers aligned`() {
        val result =
            calculateFloatingMenuSnapCoordinates(
                edge = FloatingMenuGestureState.Edge.TOP,
                displayWidth = 800,
                displayHeight = 400,
                ballAWidth = 48,
                ballAHeight = 48,
                ballBWidth = 40,
                ballBHeight = 40,
                currentAX = 500,
                currentAY = 100,
            )

        assertEquals(0, result.targetAY)
        assertEquals(19, calculateFloatingBallVisibleSize(48, 48))
        assertEquals(result.targetAX + 24, result.targetBX + 20)
        assertEquals(result.targetAY + 24, result.targetBY + 20)
    }

    @Test
    fun `revealing a right snapped ball moves it fully inside and keeps centers aligned`() {
        val result =
            calculateFloatingMenuRevealCoordinates(
                edge = FloatingMenuGestureState.Edge.RIGHT,
                displayWidth = 800,
                displayHeight = 400,
                ballAWidth = 48,
                ballAHeight = 48,
                ballBWidth = 40,
                ballBHeight = 40,
                currentAX = 781,
                currentAY = 100,
            )

        assertEquals(752, result.targetAX)
        assertEquals(100, result.targetAY)
        assertEquals(result.targetAX + 24, result.targetBX + 20)
        assertEquals(result.targetAY + 24, result.targetBY + 20)
    }

    @Test
    fun `bottom snap uses safe bottom instead of physical display bottom`() {
        val result =
            calculateFloatingMenuSnapCoordinates(
                edge = FloatingMenuGestureState.Edge.BOTTOM,
                displayWidth = 400,
                displayHeight = 760,
                ballAWidth = 48,
                ballAHeight = 48,
                ballBWidth = 40,
                ballBHeight = 40,
                currentAX = 100,
                currentAY = 700,
            )

        assertEquals(712, result.targetAY)
        assertEquals(19, calculateFloatingBallVisibleSize(48, 48))
        assertEquals(result.targetAY + 24, result.targetBY + 20)
    }

    @Test
    fun `left snap respects a nonzero safe left inset`() {
        val result =
            calculateFloatingMenuSnapCoordinates(
                edge = FloatingMenuGestureState.Edge.LEFT,
                displayWidth = 800,
                displayHeight = 400,
                ballAWidth = 48,
                ballAHeight = 48,
                ballBWidth = 40,
                ballBHeight = 40,
                currentAX = 30,
                currentAY = 100,
                displayLeft = 30,
            )

        assertEquals(30, result.targetAX)
        assertEquals(19, calculateFloatingBallVisibleSize(48, 48))
        assertEquals(result.targetAX + 24, result.targetBX + 20)
    }

    @Test
    fun `opening menu above a top ball moves the ball down and preserves the gap`() {
        val result =
            calculateFloatingMenuVerticalPlacement(
                currentBallY = 0,
                ballHeight = 48,
                menuHeight = 41,
                gap = 16,
                boundsTop = 0,
                boundsBottom = 800,
            )

        assertEquals(57, result.ballY)
        assertEquals(0, result.menuY)
        assertEquals(16, result.ballY - (result.menuY + 41))
    }

    @Test
    fun `menu moves continuously from the shared top limit`() {
        val positions =
            (57..60).map { ballY ->
                calculateFloatingMenuWindowY(
                    ballY = ballY,
                    menuHeight = 41,
                    gap = 16,
                    boundsTop = 0,
                )
            }

        assertEquals(listOf(0, 1, 2, 3), positions)
    }

    @Test
    fun `application window top inset is included in portrait menu spacing`() {
        val result =
            calculateFloatingMenuVerticalPlacement(
                currentBallY = 172,
                ballHeight = 144,
                menuHeight = 124,
                gap = 48,
                boundsTop = 114,
                boundsBottom = 2400,
            )

        assertEquals(286, result.ballY)
        assertEquals(410, result.ballY + 124)
        assertEquals(114, result.menuY)
        assertEquals(48, result.ballY - (result.menuY + 124))
    }

    @Test
    fun `portrait menu uses application top while landscape keeps physical top`() {
        val portraitTop =
            calculateFloatingMenuTop(
                windowBounds = FloatingMenuWindowBounds(left = 0, top = 0, right = 1080, bottom = 2400),
                applicationWindowTop = 114,
            )
        val landscapeBounds = FloatingMenuWindowBounds(left = 103, top = 0, right = 2400, bottom = 1080)
        val landscapeTop =
            calculateFloatingMenuTop(
                windowBounds = landscapeBounds,
                applicationWindowTop = 114,
            )
        val landscapePlacement =
            calculateFloatingMenuVerticalPlacement(
                currentBallY = 0,
                ballHeight = 144,
                menuHeight = 124,
                gap = 48,
                boundsTop = landscapeTop,
                boundsBottom = landscapeBounds.bottom,
            )

        assertEquals(114, portraitTop)
        assertEquals(0, landscapeTop)
        assertEquals(103, landscapeBounds.left)
        assertEquals(172, landscapePlacement.ballY)
        assertEquals(296, landscapePlacement.ballY + 124)
    }
}

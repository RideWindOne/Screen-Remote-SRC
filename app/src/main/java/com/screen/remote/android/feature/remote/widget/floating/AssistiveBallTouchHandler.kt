package com.screen.remote.android.feature.remote.widget.floating

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

internal class AssistiveBallTouchHandler(
    private val config: AssistiveBallConfig,
    private val onLongPress: () -> Unit,
    private val onMove: (dx: Float, dy: Float) -> Unit,
    private val onRelease: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var gestureState = AssistiveBallGestureState.IDLE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val longPressRunnable =
        Runnable {
            gestureState = AssistiveBallGestureState.LONG_PRESS
            onLongPress()
        }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleRelease()
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        gestureState = AssistiveBallGestureState.DRAGGING
        lastTouchX = event.rawX
        lastTouchY = event.rawY
        handler.postDelayed(longPressRunnable, config.longPressDurationMs)
    }

    private fun handleMove(event: MotionEvent) {
        val dx = event.rawX - lastTouchX
        val dy = event.rawY - lastTouchY
        onMove(dx, dy)
        lastTouchX = event.rawX
        lastTouchY = event.rawY
    }

    private fun handleRelease() {
        handler.removeCallbacks(longPressRunnable)
        if (gestureState == AssistiveBallGestureState.DRAGGING) {
            onRelease()
        }
        gestureState = AssistiveBallGestureState.IDLE
    }
}

internal enum class AssistiveBallGestureState {
    IDLE,
    DRAGGING,
    LONG_PRESS,
}

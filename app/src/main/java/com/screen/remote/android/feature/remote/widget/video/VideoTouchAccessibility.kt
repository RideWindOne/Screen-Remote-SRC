package com.screen.remote.android.feature.remote.widget.video

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration

internal class AccessibleVideoSurfaceView(
    context: Context,
) : SurfaceView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

internal class AccessibleVideoTextureView(
    context: Context,
) : TextureView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

internal fun View.setAccessibleOnTouchListener(
    onTouch: ((View, MotionEvent) -> Boolean)?,
) {
    if (onTouch == null) {
        setOnTouchListener(null)
        return
    }

    val clickTracker = ClickGestureTracker(context)
    setOnTouchListener { view, event ->
        val isClick = clickTracker.onTouchEvent(event)
        val handled = onTouch(view, event)
        if (handled && isClick) {
            view.performClick()
        }
        handled
    }
}

private class ClickGestureTracker(
    context: Context,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var clickCandidate = false

    fun onTouchEvent(event: MotionEvent): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                clickCandidate = true
                false
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
                reset()
                false
            }

            MotionEvent.ACTION_MOVE -> {
                updateClickCandidate(event)
                false
            }

            MotionEvent.ACTION_UP -> {
                updateClickCandidate(event)
                val isClick = clickCandidate && event.pointerCount == 1
                reset()
                isClick
            }

            else -> false
        }

    private fun updateClickCandidate(event: MotionEvent) {
        if (!clickCandidate) return
        val pointerIndex = event.findPointerIndex(pointerId)
        if (pointerIndex < 0) {
            clickCandidate = false
            return
        }
        val deltaX = event.getX(pointerIndex) - downX
        val deltaY = event.getY(pointerIndex) - downY
        if (deltaX * deltaX + deltaY * deltaY > touchSlop * touchSlop) {
            clickCandidate = false
        }
    }

    private fun reset() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        clickCandidate = false
    }
}

package com.screen.remote.android.feature.remote.widget.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class AssistiveBallView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        var windowManager: WindowManager? = null
        var layoutParams: WindowManager.LayoutParams? = null

        private val config = AssistiveBallConfig.fromContext(context)
        private val state = AssistiveBallState(menuButtons = config.menuAngles.size)
        private val layoutController =
            AssistiveBallLayoutController(
                config = config,
                state = state,
                invalidateView = ::invalidate,
            )
        private val windowController =
            AssistiveBallWindowController(
                view = this,
                config = config,
                state = state,
                updateAnchoredPositions = layoutController::updateAnchoredPositions,
                invalidateView = ::invalidate,
                windowManagerProvider = { windowManager },
                layoutParamsProvider = { layoutParams },
            )
        private val renderer =
            AssistiveBallRenderer(
                config = config,
                state = state,
            )
        private val touchHandler =
            AssistiveBallTouchHandler(
                config = config,
                onLongPress = layoutController::toggleMenu,
                onMove = windowController::moveBall,
                onRelease = windowController::snapToEdge,
            )

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val size = config.viewSizePx.toInt()
            setMeasuredDimension(size, size)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            layoutController.onSizeChanged(w = w, h = h)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            renderer.draw(canvas)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = touchHandler.onTouchEvent(event)

        fun toggleSmallBall() {
            layoutController.toggleSmallBall()
        }
    }

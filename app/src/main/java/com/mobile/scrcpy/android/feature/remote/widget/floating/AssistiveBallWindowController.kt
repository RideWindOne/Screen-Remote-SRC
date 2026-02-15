package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator

internal class AssistiveBallWindowController(
    private val view: View,
    private val config: AssistiveBallConfig,
    private val state: AssistiveBallState,
    private val updateAnchoredPositions: () -> Unit,
    private val invalidateView: () -> Unit,
    private val windowManagerProvider: () -> WindowManager?,
    private val layoutParamsProvider: () -> WindowManager.LayoutParams?,
) {
    fun moveBall(
        dx: Float,
        dy: Float,
    ) {
        val params = layoutParamsProvider() ?: return
        val windowManager = windowManagerProvider() ?: return
        val displayMetrics = view.resources.displayMetrics

        val newX = (params.x + dx).toInt()
        val newY = (params.y + dy).toInt()

        params.x = newX.coerceIn(0, displayMetrics.widthPixels - view.measuredWidth)
        params.y = newY.coerceIn(0, displayMetrics.heightPixels - view.measuredHeight)

        updateViewLayout(windowManager, params)
    }

    fun snapToEdge() {
        val params = layoutParamsProvider() ?: return
        val windowManager = windowManagerProvider() ?: return
        val displayWidth = view.resources.displayMetrics.widthPixels
        val currentCenterX = params.x + view.measuredWidth / 2f
        val targetX =
            if (currentCenterX < displayWidth / 2f) {
                (-view.measuredWidth * (1f - config.halfHideOffset)).toInt()
            } else {
                (displayWidth - view.measuredWidth * config.halfHideOffset).toInt()
            }

        state.halfHidden = true
        val startX = params.x

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator(0.5f)
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                updateViewLayout(windowManager, params)
            }
            start()
        }
    }

    private fun updateViewLayout(
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            return
        }

        updateAnchoredPositions()
        invalidateView()
    }
}

package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.animation.ValueAnimator
import kotlin.math.cos
import kotlin.math.sin

internal class AssistiveBallLayoutController(
    private val config: AssistiveBallConfig,
    private val state: AssistiveBallState,
    private val invalidateView: () -> Unit,
) {
    fun onSizeChanged(
        w: Int,
        h: Int,
    ) {
        state.centerX = w / 2f
        state.centerY = h / 2f
        updateAnchoredPositions()
    }

    fun updateAnchoredPositions() {
        updateSmallBallPosition()
        updateMenuPosition()
    }

    fun toggleSmallBall() {
        state.smallBallAngle = if (state.smallBallAngle > 90f) 0f else 180f
        animateSmallBall()
    }

    fun toggleMenu() {
        val startFraction = if (state.isMenuOpen) 1f else 0f
        val endFraction = if (state.isMenuOpen) 0f else 1f
        ValueAnimator.ofFloat(startFraction, endFraction).apply {
            duration = 300
            addUpdateListener { animation ->
                updateMenuPositions(animation.animatedValue as Float)
                invalidateView()
            }
            start()
        }
        state.isMenuOpen = !state.isMenuOpen
    }

    private fun animateSmallBall() {
        val startX = state.smallBallX
        val startY = state.smallBallY
        val targetAngle = state.smallBallAngle

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val rad = Math.toRadians(targetAngle.toDouble())
                state.smallBallX =
                    startX + fraction * (state.centerX + config.orbitRadius * cos(rad).toFloat() - startX)
                state.smallBallY =
                    startY + fraction * (state.centerY + config.orbitRadius * sin(rad).toFloat() * 0.35f - startY)
                invalidateView()
            }
            start()
        }
    }

    private fun updateSmallBallPosition() {
        val rad = Math.toRadians(state.smallBallAngle.toDouble())
        state.smallBallX = state.centerX + config.orbitRadius * cos(rad).toFloat()
        state.smallBallY = state.centerY + config.orbitRadius * sin(rad).toFloat() * 0.35f
    }

    private fun updateMenuPosition() {
        if (!state.isMenuOpen) {
            return
        }
        updateMenuPositions(1f)
    }

    private fun updateMenuPositions(fraction: Float) {
        config.menuAngles.forEachIndexed { index, angle ->
            val rad = Math.toRadians(angle.toDouble())
            state.menuPositions[index] =
                state.centerX + config.menuRadius * fraction * cos(rad).toFloat() to
                    state.centerY + config.menuRadius * fraction * sin(rad).toFloat()
        }
    }
}

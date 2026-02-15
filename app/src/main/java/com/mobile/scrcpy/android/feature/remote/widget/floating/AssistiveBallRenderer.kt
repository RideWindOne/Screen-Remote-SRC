package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.graphics.Canvas
import androidx.core.graphics.withScale

internal class AssistiveBallRenderer(
    private val config: AssistiveBallConfig,
    private val state: AssistiveBallState,
) {
    fun draw(canvas: Canvas) {
        val scale = if (state.halfHidden) config.halfHideOffset else 1f
        val alpha = if (state.halfHidden) 180 else 255

        drawBigBall(canvas, scale = scale, alpha = alpha)
        drawSmallBall(canvas, scale = scale, alpha = alpha)
        drawMenu(canvas)
    }

    private fun drawBigBall(
        canvas: Canvas,
        scale: Float,
        alpha: Int,
    ) {
        canvas.withScale(scale, scale, state.centerX, state.centerY) {
            config.bigBallPaints.forEachIndexed { index, paint ->
                val offset = (index - 1.5f) * 8 * config.density
                drawCircle(
                    state.centerX + offset,
                    state.centerY + offset,
                    config.ballRadius,
                    paint.apply { this.alpha = alpha },
                )
            }
        }
    }

    private fun drawSmallBall(
        canvas: Canvas,
        scale: Float,
        alpha: Int,
    ) {
        canvas.withScale(scale, scale, state.smallBallX, state.smallBallY) {
            config.smallBallPaints.forEachIndexed { index, paint ->
                val offset = (index - 1.5f) * 6 * config.density
                drawCircle(
                    state.smallBallX + offset,
                    state.smallBallY + offset,
                    config.smallBallRadius,
                    paint.apply { this.alpha = alpha },
                )
            }
        }
    }

    private fun drawMenu(canvas: Canvas) {
        if (!state.isMenuOpen) {
            return
        }

        state.menuPositions.forEach { (menuX, menuY) ->
            canvas.drawCircle(menuX, menuY, 24f * config.density, config.menuPaint)
        }
    }
}

package com.screen.remote.android.feature.remote.widget.floating

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.toColorInt

internal data class AssistiveBallConfig(
    val density: Float,
    val ballRadius: Float,
    val smallBallRadius: Float,
    val orbitRadius: Float,
    val menuRadius: Float,
    val halfHideOffset: Float,
    val longPressDurationMs: Long,
    val menuAngles: FloatArray,
    val bigBallPaints: Array<Paint>,
    val smallBallPaints: Array<Paint>,
    val menuPaint: Paint,
) {
    val viewSizePx: Float
        get() = (ballRadius + orbitRadius + smallBallRadius) * 2f

    companion object {
        fun fromContext(context: Context): AssistiveBallConfig {
            val density = context.resources.displayMetrics.density
            return AssistiveBallConfig(
                density = density,
                ballRadius = 30f * density,
                smallBallRadius = 18f * density,
                orbitRadius = 50f * density,
                menuRadius = 120f * density,
                halfHideOffset = 0.35f,
                longPressDurationMs = 500L,
                menuAngles = floatArrayOf(225f, 270f, 315f, 0f),
                bigBallPaints =
                    arrayOf(
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#3A3A3C".toColorInt() },
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#2C2C2E".toColorInt() },
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#1C1C1E".toColorInt() },
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#48484A".toColorInt() },
                    ),
                smallBallPaints =
                    arrayOf(
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#007AFF".toColorInt() },
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#34C759".toColorInt() },
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FF9500".toColorInt() },
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#AF52DE".toColorInt() },
                    ),
                menuPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        alpha = 200
                    },
            )
        }
    }
}

package com.screen.remote.android.feature.remote.widget.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withScale
import kotlin.math.cos
import kotlin.math.sin

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

internal class AssistiveBallState(
    menuButtons: Int,
) {
    var centerX = 0f
    var centerY = 0f
    var smallBallAngle = 180f
    var smallBallX = 0f
    var smallBallY = 0f
    var halfHidden = false
    var isMenuOpen = false
    val menuPositions = Array(menuButtons) { 0f to 0f }
}

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

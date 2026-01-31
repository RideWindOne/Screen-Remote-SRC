package com.mobile.scrcpy.android.feature.remote.components.floating

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

class AssistiveBallView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        // ====== 配置 ======
        private val density = resources.displayMetrics.density

        private val BALL_RADIUS = 30f * density // TODO
        private val SMALL_BALL_RADIUS = 18f * density // TODO
        private val ORBIT_RADIUS = 50f * density // TODO
        private val MENU_RADIUS = 120f * density // TODO
        private val HALF_HIDE_OFFSET = 0.35f // 半隐藏缩放比例 // TODO
        private val LONG_PRESS_DURATION = 500L // TODO

        // ====== WindowManager 相关 ======
        var windowManager: WindowManager? = null
        var layoutParams: WindowManager.LayoutParams? = null

        // ====== 绘制 ======
        private val bigBallPaints =
            arrayOf(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#3A3A3C".toColorInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#2C2C2E".toColorInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#1C1C1E".toColorInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#48484A".toColorInt() },
            )

        private val smallBallPaints =
            arrayOf(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#007AFF".toColorInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#34C759".toColorInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FF9500".toColorInt() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#AF52DE".toColorInt() },
            )

        private val menuPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 200
            }

        // ====== 位置 ======
        // cx 和 cy 是相对于 View 内部的坐标（View 中心）
        private var cx = 0f
        private var cy = 0f

        private var smallBallAngle = 180f
        private var smallBallX = 0f
        private var smallBallY = 0f

        // ====== 拖动状态 ======
        private var state = State.IDLE
        private var lastTouchX = 0f
        private var lastTouchY = 0f

        // ====== 半隐藏 ======
        private var halfHidden = false

        // ====== 菜单 ======
        private var isMenuOpen = false
        private val menuButtons = 4
        private val menuAngles = arrayOf(225f, 270f, 315f, 0f)
        private val menuPositions = Array(menuButtons) { Pair(0f, 0f) }

        // ====== 长按处理 ======
        private val handler = Handler(Looper.getMainLooper())
        private val longPressRunnable =
            Runnable {
                state = State.LONG_PRESS
                toggleMenu()
            }

        // ====== 枚举 ======
        private enum class State { IDLE, DRAGGING, LONG_PRESS, CLICKED }

        // ====== 测量 ======
        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val size = ((BALL_RADIUS + ORBIT_RADIUS + SMALL_BALL_RADIUS) * 2).toInt()
            setMeasuredDimension(size, size)
        }

        // ====== 尺寸变化 ======
        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            // 更新中心点
            cx = w / 2f
            cy = h / 2f
            updateSmallBallPosition()
        }

        // ====== 绘制 ======
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            var scale = 1f
            var alpha = 255
            if (halfHidden) {
                scale = HALF_HIDE_OFFSET
                alpha = 180
            }

            // 大球
            canvas.withScale(scale, scale, cx, cy) {
                for (i in bigBallPaints.indices) {
                    val offset = (i - 1.5f) * 8 * density
                    drawCircle(
                        cx + offset,
                        cy + offset,
                        BALL_RADIUS,
                        bigBallPaints[i].apply { this.alpha = alpha },
                    )
                }
            }

            // 小球
            canvas.withScale(scale, scale, smallBallX, smallBallY) {
                for (i in smallBallPaints.indices) {
                    val offset = (i - 1.5f) * 6 * density
                    drawCircle(
                        smallBallX + offset,
                        smallBallY + offset,
                        SMALL_BALL_RADIUS,
                        smallBallPaints[i].apply { this.alpha = alpha },
                    )
                }
            }

            // 菜单
            if (isMenuOpen) {
                for (i in 0 until menuButtons) {
                    val (mx, my) = menuPositions[i]
                    canvas.drawCircle(mx, my, 24f * density, menuPaint)
                }
            }
        }

        // ====== 拖动事件 ======
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    state = State.DRAGGING
                    // 使用 rawX/rawY 获取屏幕坐标
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DURATION)
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    moveBall(dx, dy)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (state == State.DRAGGING) snapToEdge()
                    state = State.IDLE
                }
            }
            return true
        }

        // ====== 移动大球 ======
        private fun moveBall(
            dx: Float,
            dy: Float,
        ) {
            val params = layoutParams ?: return
            val wm = windowManager ?: return

            // 更新 WindowManager 位置
            val newX = (params.x + dx).toInt()
            val newY = (params.y + dy).toInt()

            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels

            // 限制在屏幕范围内
            params.x = newX.coerceIn(0, width - measuredWidth)
            params.y = newY.coerceIn(0, height - measuredHeight)

            try {
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
                // 忽略更新错误
            }

            updateSmallBallPosition()
            updateMenuPosition()
            invalidate()
        }

        // ====== 小球轨道 ======
        private fun updateSmallBallPosition() {
            val rad = Math.toRadians(smallBallAngle.toDouble())
            smallBallX = cx + ORBIT_RADIUS * cos(rad).toFloat()
            smallBallY = cy + ORBIT_RADIUS * sin(rad).toFloat() * 0.35f
        }

        fun toggleSmallBall() {
            smallBallAngle = if (smallBallAngle > 90f) 0f else 180f
            animateSmallBall()
        }

        private fun animateSmallBall() {
            val startX = smallBallX
            val startY = smallBallY
            val targetAngle = smallBallAngle
            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 300
            animator.addUpdateListener {
                val fraction = it.animatedValue as Float
                val rad = Math.toRadians(targetAngle.toDouble())
                smallBallX = startX + fraction * (cx + ORBIT_RADIUS * cos(rad).toFloat() - startX)
                smallBallY =
                    startY + fraction * (cy + ORBIT_RADIUS * sin(rad).toFloat() * 0.35f - startY)
                invalidate()
            }
            animator.start()
        }

        // ====== 菜单 ======
        private fun toggleMenu() {
            val startFraction = if (isMenuOpen) 1f else 0f
            val endFraction = if (isMenuOpen) 0f else 1f
            val animator = ValueAnimator.ofFloat(startFraction, endFraction)
            animator.duration = 300
            animator.addUpdateListener {
                val fraction = it.animatedValue as Float
                for (i in 0 until menuButtons) {
                    val rad = Math.toRadians(menuAngles[i].toDouble())
                    menuPositions[i] =
                        Pair(
                            cx + MENU_RADIUS * fraction * cos(rad).toFloat(),
                            cy + MENU_RADIUS * fraction * sin(rad).toFloat(),
                        )
                }
                invalidate()
            }
            animator.start()
            isMenuOpen = !isMenuOpen
        }

        private fun updateMenuPosition() {
            if (isMenuOpen) {
                for (i in 0 until menuButtons) {
                    val rad = Math.toRadians(menuAngles[i].toDouble())
                    menuPositions[i] =
                        Pair(
                            cx + MENU_RADIUS * cos(rad).toFloat(),
                            cy + MENU_RADIUS * sin(rad).toFloat(),
                        )
                }
            }
        }

        // ====== 吸边 + 弹性 ======
        private fun snapToEdge() {
            val params = layoutParams ?: return
            val wm = windowManager ?: return

            val width = resources.displayMetrics.widthPixels
            val currentX = params.x + measuredWidth / 2f // View 中心在屏幕上的 X 坐标

            // 判断贴左边还是右边
            val targetX =
                if (currentX < width / 2) {
                    // 贴左边，隐藏大部分，只露出一点
                    (-measuredWidth * (1 - HALF_HIDE_OFFSET)).toInt()
                } else {
                    // 贴右边
                    (width - measuredWidth * HALF_HIDE_OFFSET).toInt()
                }

            halfHidden = true

            // 使用 ValueAnimator 实现弹性动画
            val startX = params.x
            val animator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 300
                    interpolator = OvershootInterpolator(0.5f)
                    addUpdateListener { anim ->
                        val fraction = anim.animatedValue as Float
                        params.x = (startX + (targetX - startX) * fraction).toInt()
                        try {
                            wm.updateViewLayout(this@AssistiveBallView, params)
                        } catch (e: Exception) {
                            // 忽略更新错误
                        }
                        updateSmallBallPosition()
                        updateMenuPosition()
                        invalidate()
                    }
                }
            animator.start()
        }
    }

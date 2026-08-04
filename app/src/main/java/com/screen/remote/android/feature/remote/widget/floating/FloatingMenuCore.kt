package com.screen.remote.android.feature.remote.widget.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import kotlinx.coroutines.launch

/**
 * 球体系统引用类型别名
 * 包含：(ballA, ballB, windowManager, gestureHandler)
 */
typealias BallSystemReference = Tuple4<View, View, WindowManager, FloatingMenuGestureHandler>

/**
 * 辅助数据类
 */
data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

/**
 * 显示双球体系统：A（小球）+ B（大球），都用 WindowManager 实现
 * @param actions 悬浮菜单动作边界
 * @param scope CoroutineScope 用于异步操作
 * @return 返回 (ballA, ballB, windowManager, gestureHandler) 的引用，用于后续移除
 */
fun showDualBallSystem(
    context: Context,
    actions: FloatingMenuActions,
    scope: kotlinx.coroutines.CoroutineScope,
): BallSystemReference {
    // 读取触感反馈开关状态（只读取一次）
    val hapticEnabled = actions.hapticEnabled

    // 仅在开关开启时初始化触感反馈
    if (hapticEnabled) {
        HapticHelper.init(context)
    }

    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val density = context.resources.displayMetrics.density
    val windowBounds = FloatingMenuWindowBoundsProvider(context).current()

    // 判断屏幕方向
    val isLandscape = windowBounds.width > windowBounds.height

    // 计算初始位置
    val ballBX: Float
    val ballBY: Float

    if (isLandscape) {
        // 横屏：右侧上下居中，距离右边缘 20dp
        ballBX = windowBounds.right - FLOATING_BALL_INITIAL_RIGHT_MARGIN_DP * density - BALL_B_SIZE_DP * density
        ballBY = windowBounds.top + (windowBounds.height - BALL_B_SIZE_DP * density) / 2f
    } else {
        // 竖屏：底部左右居中，距离底部 85dp
        ballBX = windowBounds.left + (windowBounds.width - BALL_B_SIZE_DP * density) / 2f
        ballBY = windowBounds.bottom - FLOATING_BALL_INITIAL_BOTTOM_MARGIN_DP * density - BALL_B_SIZE_DP * density
    }

    // 小球A的位置（中心对齐大球B）
    val ballACenterOffsetX = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f
    val ballACenterOffsetY = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f
    val ballAX = ballBX + ballACenterOffsetX
    val ballAY = ballBY + ballACenterOffsetY

    // 创建大球 B（底层）
    val ballB = createBall(context, sizeDp = BALL_B_SIZE_DP)
    val paramsB = createWindowParams(context, sizeDp = BALL_B_SIZE_DP, isFocusable = false)
    paramsB.x = ballBX.toInt()
    paramsB.y = ballBY.toInt()
    windowManager.addView(ballB, paramsB)

    // 创建小球 A（顶层，可触摸）
    val ballA = createBall(context, sizeDp = BALL_A_SIZE_DP)
    val paramsA = createWindowParams(context, sizeDp = BALL_A_SIZE_DP, isFocusable = true)
    paramsA.x = ballAX.toInt()
    paramsA.y = ballAY.toInt()
    windowManager.addView(ballA, paramsA)

    // 设置触摸事件
    val gestureHandler =
        FloatingMenuGestureHandler(
            context = context,
            ballA = ballA,
            ballB = ballB,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            actions = actions,
            scope = scope,
            hapticEnabled = hapticEnabled, // 传递触感开关状态
        )
    ballA.setOnTouchListener(gestureHandler)

    // 设置按键监听，拦截返回键并发送到远程设备
    ballA.setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                scope.launch {
                    val result = actions.controlViewModel.sendKeyEvent(4) // KEYCODE_BACK
                    if (result.isFailure) {
                        LogManager.e(
                            LogTags.FLOATING_CONTROLLER,
                            "Failed to send return key: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            }
            true // 消费事件
        } else {
            false // 不消费其他按键
        }
    }

    return Tuple4(ballA, ballB, windowManager, gestureHandler)
}

/**
 * 隐藏双球体系统
 */
fun hideDualBallSystem(reference: BallSystemReference?) {
    reference?.let { (ballA, ballB, windowManager, gestureHandler) ->
        try {
            // 先清理菜单
            gestureHandler.cleanup()

            // 移除所有球体（检查是否已附加到窗口）
            if (ballA.isAttachedToWindow) {
                windowManager.removeView(ballA)
            }
            if (ballB.isAttachedToWindow) {
                windowManager.removeView(ballB)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.FLOATING_CONTROLLER, "Failed to remove sphere: ${e.message}")
        }
    }
}

/**
 * 创建球体 View
 */
internal fun createBall(
    context: Context,
    sizeDp: Int,
): View {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val radius = sizePx / 2f

    // 球颜色（使用iOS经典灰色）
    val ballColorsNormal =
        arrayOf(
            Color.argb(153, 58, 58, 60), // 外层 60%
            Color.argb(102, 44, 44, 46), // 第二层 40%
            Color.argb(64, 28, 28, 30), // 第三层 25%
            Color.argb(100, 255, 255, 255), // 25% 白色
        )

    val layerFactors = floatArrayOf(1.0f, 0.75f, 0.60f, 0.40f) // 让每层更小，创造更明显的立体效果

    // 预分配 Paint 对象以避免在 onDraw 中重复创建
    val paints =
        ballColorsNormal.map { color ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
            }
        }

    return FloatingBallView(
        context = context,
        diameterPx = sizePx,
        radius = radius,
        paints = paints,
        layerFactors = layerFactors,
    ).apply {
        layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
        // ✅ 关键：启用触觉反馈
        isHapticFeedbackEnabled = true
    }
}

internal class FloatingBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val diameterPx: Int = 0,
    private val radius: Float = 0f,
    private val paints: List<Paint> = emptyList(),
    private val layerFactors: FloatArray = floatArrayOf(),
) : View(context, attrs, defStyleAttr) {
    var hiddenEdge: FloatingMenuGestureState.Edge? = null
        private set
    var hiddenOffsetPx: Float = 0f
        private set

    fun setEdgeHidden(
        edge: FloatingMenuGestureState.Edge,
        offsetPx: Float,
    ) {
        hiddenEdge = edge
        hiddenOffsetPx = offsetPx.coerceIn(0f, diameterPx.toFloat())
        invalidate()
    }

    fun showFully() {
        hiddenEdge = null
        hiddenOffsetPx = 0f
        invalidate()
    }

    fun containsTouch(
        x: Float,
        y: Float,
    ): Boolean {
        val (centerX, centerY) = drawingCenter()
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (centerX, centerY) = drawingCenter()
        paints.forEach { paint ->
            layerFactors.forEach { factor ->
                canvas.drawCircle(centerX, centerY, radius * factor, paint)
            }
        }
    }

    private fun drawingCenter(): Pair<Float, Float> {
        var centerX = diameterPx / 2f
        var centerY = diameterPx / 2f
        when (hiddenEdge) {
            FloatingMenuGestureState.Edge.LEFT -> centerX -= hiddenOffsetPx
            FloatingMenuGestureState.Edge.RIGHT -> centerX += hiddenOffsetPx
            FloatingMenuGestureState.Edge.TOP -> centerY -= hiddenOffsetPx
            FloatingMenuGestureState.Edge.BOTTOM -> centerY += hiddenOffsetPx
            null -> Unit
        }
        return centerX to centerY
    }
}

/**
 * 创建 WindowManager 参数
 */
internal fun createWindowParams(
    context: Context,
    sizeDp: Int,
    isFocusable: Boolean,
): WindowManager.LayoutParams {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()

    return WindowManager.LayoutParams().apply {
        // 应用内悬浮窗使用 TYPE_APPLICATION
        type = WindowManager.LayoutParams.TYPE_APPLICATION
        format = PixelFormat.TRANSLUCENT
        flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                if (isFocusable) {
                    // 可触摸，但不抢占 Activity 焦点，否则会影响本机输入法弹出
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                } else {
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                }
        width = sizePx
        height = sizePx
        gravity = Gravity.TOP or Gravity.START
        useWholeWindowCoordinateSpace()
    }
}

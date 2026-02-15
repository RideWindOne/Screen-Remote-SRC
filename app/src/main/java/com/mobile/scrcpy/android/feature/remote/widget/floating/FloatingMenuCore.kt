package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags
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
        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "触感反馈已启用")
    } else {
        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔕 触感反馈已禁用")
    }

    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val density = context.resources.displayMetrics.density
    val displayMetrics = context.resources.displayMetrics

    // 判断屏幕方向
    val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels

    // 计算初始位置
    val ballBX: Float
    val ballBY: Float

    if (isLandscape) {
        // 横屏：右侧上下居中，距离右边缘 20dp
        ballBX = displayMetrics.widthPixels - FLOATING_BALL_INITIAL_RIGHT_MARGIN_DP * density - BALL_B_SIZE_DP * density
        ballBY = (displayMetrics.heightPixels - BALL_B_SIZE_DP * density) / 2f
    } else {
        // 竖屏：底部左右居中，距离底部 85dp
        ballBX = (displayMetrics.widthPixels - BALL_B_SIZE_DP * density) / 2f
        ballBY = displayMetrics.heightPixels - FLOATING_BALL_INITIAL_BOTTOM_MARGIN_DP * density - BALL_B_SIZE_DP * density
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
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                scope.launch {
                    val result = actions.controlViewModel.sendKeyEvent(4) // KEYCODE_BACK
                    if (result.isFailure) {
                        Log.e(LogTags.FLOATING_CONTROLLER, "发送返回键失败: ${result.exceptionOrNull()?.message}")
                    } else {
                        Log.d(LogTags.FLOATING_CONTROLLER, "返回键已发送到远程设备")
                    }
                }
            }
            true // 消费事件
        } else {
            false // 不消费其他按键
        }
    }

    Log.d(LogTags.FLOATING_CONTROLLER_MSG, "双球体系统已创建（${if (isLandscape) "横屏" else "竖屏"}）")
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
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "双球体系统已移除")
        } catch (e: Exception) {
            Log.e(LogTags.FLOATING_CONTROLLER, "移除球体失败: ${e.message}")
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
            android.graphics.Color.argb(153, 58, 58, 60), // 外层 60%
            android.graphics.Color.argb(102, 44, 44, 46), // 第二层 40%
            android.graphics.Color.argb(64, 28, 28, 30), // 第三层 25%
            android.graphics.Color.argb(100, 255, 255, 255), // 25% 白色
        )

    val layerFactors = floatArrayOf(1.0f, 0.75f, 0.60f, 0.40f) // 让每层更小，创造更明显的立体效果

    // 预分配 Paint 对象以避免在 onDraw 中重复创建
    val paints =
        ballColorsNormal.map { color ->
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
            }
        }

    return object : View(context) {
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            for (i in ballColorsNormal.indices) {
                val paint = paints[i]
                for (j in 0..3) {
                    canvas.drawCircle(centerX, centerY, radius * layerFactors[j], paint)
                }
            }
        }
    }.apply {
        layoutParams = android.view.ViewGroup.LayoutParams(sizePx, sizePx)
        // ✅ 关键：启用触觉反馈
        isHapticFeedbackEnabled = true
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
            if (isFocusable) {
                // 可触摸，但不抢占 Activity 焦点，否则会影响本机输入法弹出
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        width = sizePx
        height = sizePx
        gravity = Gravity.TOP or Gravity.START
    }
}

package com.mobile.scrcpy.android.feature.remote.components.floating

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * 手势识别处理器（纯 WindowManager 实现）
 *
 * 手势类型：
 * 1. 点击：按下 -> 松开（时间 < 300ms，移动 < 阈值）
 * 2. 拖动：按下 -> 移动（未长按）-> 松开（B 跟随 A）
 * 3. 长按：按下 -> 等待（> 500ms）-> 移动（A 围绕 B 转圈）
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingMenuGestureHandler(
    private val context: Context,
    private val ballA: View,
    private val ballB: View,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    private val viewModel: MainViewModel,
    private val scope: CoroutineScope,
    private val hapticEnabled: Boolean,
) : View.OnTouchListener {
    // ==================== 模块组件 ====================

    private val state = FloatingMenuGestureState()
    private val detector = FloatingMenuGestureDetector(context, state, hapticEnabled)
    private val menuManager =
        FloatingMenuViewManager(
            context,
            windowManager,
            paramsA,
            ballA,
            ballB,
            viewModel,
            scope,
            state,
            hapticEnabled,
        )
    private val edgeSnap =
        FloatingMenuEdgeSnap(
            context,
            ballA,
            ballB,
            windowManager,
            paramsA,
            paramsB,
            state,
            menuManager,
            hapticEnabled,
        )
    private val ballMovement =
        FloatingMenuBallMovement(
            context,
            ballA,
            ballB,
            windowManager,
            paramsA,
            paramsB,
            state,
            edgeSnap,
            menuManager,
        )

    private val density = context.resources.displayMetrics.density

    override fun onTouch(
        v: View,
        event: MotionEvent,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 检查触摸点是否在圆形区域内
                if (!isTouchInsideCircle(v, event)) {
                    Log.d(LogTags.FLOATING_CONTROLLER, "❌ 触摸点在圆外")
                    return false
                }
                handleDown(event)
            }

            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
            }

            MotionEvent.ACTION_UP -> {
                handleUp()
            }

            MotionEvent.ACTION_CANCEL -> {
                handleCancel()
            }
        }
        return true
    }

    /**
     * 检查触摸点是否在圆形区域内
     */
    private fun isTouchInsideCircle(
        v: View,
        event: MotionEvent,
    ): Boolean {
        val touchX = event.x
        val touchY = event.y
        val centerX = v.width / 2f
        val centerY = v.height / 2f
        val radius = v.width / 2f
        val distance = hypot((touchX - centerX).toDouble(), (touchY - centerY).toDouble())
        return distance <= radius
    }

    /**
     * 按下：记录初始状态
     */
    private fun handleDown(event: MotionEvent) {
        // 取消正在进行的归位动画
        edgeSnap.cancelAnimation()

        // 初始化 Handler 和取消之前的长按延迟任务
        state.cancelLongPressCallbacks()
        state.initHandlers()

        // 记录初始状态
        state.downTime = System.currentTimeMillis()
        state.downRawX = event.rawX
        state.downRawY = event.rawY
        state.lastRawX = event.rawX
        state.lastRawY = event.rawY
        state.hasMoved = false
        state.isLongPress = false
        state.canEnterLongPress = false

        // 启动长按延迟任务
        setupLongPressCallbacks()

        // 记录 B 球中心位置
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        // 计算 A 球中心位置和偏移量
        val ballACenterX = paramsA.x + ballA.width / 2f
        val ballACenterY = paramsA.y + ballA.height / 2f
        state.downOffsetX = event.rawX - ballACenterX
        state.downOffsetY = event.rawY - ballACenterY

        Log.d(
            LogTags.FLOATING_CONTROLLER,
            "⬇️ 按下 at (${event.rawX}, ${event.rawY}), " +
                "B中心=(${state.ballBCenterX}, ${state.ballBCenterY}), " +
                "A中心=($ballACenterX, $ballACenterY), " +
                "A左上角=(${paramsA.x}, ${paramsA.y}), " +
                "偏移=(${state.downOffsetX}, ${state.downOffsetY})",
        )
    }

    /**
     * 设置长按延迟任务
     */
    private fun setupLongPressCallbacks() {
        // 300ms延迟任务：允许进入长按模式
        state.longPressRunnable =
            Runnable {
                if (!state.hasMoved) {
                    state.canEnterLongPress = true
                    if (hapticEnabled) {
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                    }
                    Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 按住300ms未移动，可以进入长按模式")
                }
            }

        // 800ms延迟任务：预留功能
        state.reservedFunctionRunnable =
            Runnable {
                if (!state.hasMoved && state.canEnterLongPress) {
                    if (hapticEnabled) {
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                    }
                    Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 按住800ms未移动，预留功能触发")
                }
            }

        state.longPressHandler?.postDelayed(state.longPressRunnable!!, LONG_PRESS_TIME_MS)
        state.reservedFunctionHandler?.postDelayed(state.reservedFunctionRunnable!!, RESERVED_FUNCTION_TIME_MS)
    }

    /**
     * 移动：判断手势类型并执行相应动作
     */
    private fun handleMove(event: MotionEvent) {
        val dx = event.rawX - state.downRawX
        val dy = event.rawY - state.downRawY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val duration = System.currentTimeMillis() - state.downTime

        // 检测是否进入长按模式
        detector.checkLongPressTransition(distance, duration)

        // 判断是否超过移动阈值
        if (detector.checkMovementThreshold(dx, dy)) {
            if (state.isLongPress) {
                // 长按 + 移动：A 球跟随手指移动，B 球不动
                ballMovement.moveAAroundB(event, detector)
            } else {
                // 普通拖动：A 和 B 一起移动
                ballMovement.moveAAndBTogether(event)
            }
        }
    }

    /**
     * 松开：判断是否为点击，长按后归位，普通拖动后贴边
     */
    private fun handleUp() {
        val duration = System.currentTimeMillis() - state.downTime

        // 如果是长按拖动，根据松手时的位置判断方向
        val finalDirection =
            if (state.isLongPress && state.hasMoved) {
                val ballACenterX = paramsA.x + ballA.width / 2f
                val ballACenterY = paramsA.y + ballA.height / 2f
                val dx = ballACenterX - state.ballBCenterX
                val dy = ballACenterY - state.ballBCenterY
                detector.getFinalDirection(dx, dy)
            } else {
                null
            }

        // 构建日志信息
        val directionInfo =
            when {
                finalDirection != null -> "$finalDirection (${finalDirection.actionName})"
                state.canEnterLongPress && !state.hasMoved -> "未移动 (预留功能)"
                else -> "null"
            }

        Log.d(
            LogTags.FLOATING_CONTROLLER,
            "⬆️ 松开 - 时长: ${duration}ms, 移动: ${state.hasMoved}, 长按: ${state.isLongPress}, 可长按: ${state.canEnterLongPress}, 方向: $directionInfo",
        )

        when {
            detector.isClick(duration) -> handleClick()
            state.canEnterLongPress && !state.hasMoved -> handleReservedFunction()
            state.isLongPress && state.hasMoved -> handleLongPressDrag(finalDirection)
            state.hasMoved && !state.isLongPress -> handleNormalDrag()
        }

        // 取消长按延迟任务并重置状态
        state.cancelLongPressCallbacks()
        state.reset()
    }

    /**
     * 处理点击事件
     */
    private fun handleClick() {
        if (hapticEnabled) {
            performHapticFeedbackCompat(HapticFeedbackConstants.CLOCK_TICK)
        }

        if (state.isMenuShown) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 点击！隐藏菜单")
            menuManager.hideMenu()
        } else {
            if (hapticEnabled) {
                performHapticFeedbackCompat(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 点击！显示菜单")
            menuManager.showMenu()
        }
    }

    /**
     * 处理预留功能
     */
    private fun handleReservedFunction() {
        Log.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "长按超过${LONG_PRESS_TIME_MS}ms但未移动 → 预留功能",
        )
    }

    /**
     * 处理长按拖动
     */
    private fun handleLongPressDrag(direction: FloatingMenuGestureState.Direction?) {
        if (direction != null) {
            Log.d(
                LogTags.FLOATING_CONTROLLER_MSG,
                "手势完成: ${direction.actionName} ($direction)",
            )

            // 发送控制信号
            scope.launch {
                when (direction) {
                    FloatingMenuGestureState.Direction.LEFT -> {
                        val result = viewModel.sendKeyEvent(4) // KEYCODE_BACK
                        if (result.isFailure) {
                            Log.e(
                                LogTags.FLOATING_CONTROLLER_MSG,
                                "手势返回键失败: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }

                    FloatingMenuGestureState.Direction.RIGHT -> {
                        val result = viewModel.sendKeyEvent(187) // KEYCODE_APP_SWITCH
                        if (result.isFailure) {
                            Log.e(
                                LogTags.FLOATING_CONTROLLER_MSG,
                                "手势最近任务键失败: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }

                    FloatingMenuGestureState.Direction.UP -> {
                        val result = viewModel.sendKeyEvent(3) // KEYCODE_HOME
                        if (result.isFailure) {
                            Log.e(
                                LogTags.FLOATING_CONTROLLER_MSG,
                                "手势主页键失败: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }
                    FloatingMenuGestureState.Direction.DOWN -> {
                        viewModel.executeShellCommand("cmd statusbar expand-notifications")
                        Log.d(
                            LogTags.FLOATING_CONTROLLER_MSG,
                            "📱 下拉通知栏: 执行命令 'cmd statusbar expand-notifications'",
                        )
                    }
                }
            }
        } else {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "长按拖动但未识别方向 → 预留功能")
        }
        edgeSnap.resetAPosition()
    }

    /**
     * 处理普通拖动
     */
    private fun handleNormalDrag() {
        ballMovement.alignBalls()
        edgeSnap.snapToEdge()
    }

    /**
     * 取消：重置状态
     */
    private fun handleCancel() {
        Log.d(LogTags.FLOATING_CONTROLLER, "❌ 手势取消")
        state.cancelLongPressCallbacks()
        state.reset()
    }

    /**
     * 清理资源：移除菜单、取消动画
     */
    fun cleanup() {
        edgeSnap.cleanup()
        state.cleanup()
        menuManager.cleanup()

        // 移除悬浮球本体
        try {
            windowManager.removeViewImmediate(ballA)
        } catch (_: Exception) {
        }

        try {
            windowManager.removeViewImmediate(ballB)
        } catch (_: Exception) {
        }
    }
}

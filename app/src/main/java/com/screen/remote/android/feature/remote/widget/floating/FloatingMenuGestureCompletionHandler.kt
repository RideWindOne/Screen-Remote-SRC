package com.screen.remote.android.feature.remote.widget.floating

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FloatingMenuGestureCompletionHandler(
    private val ballA: View,
    private val paramsA: WindowManager.LayoutParams,
    private val actions: FloatingMenuActions,
    private val scope: CoroutineScope,
    private val state: FloatingMenuGestureState,
    private val detector: FloatingMenuGestureDetector,
    private val edgeSnap: FloatingMenuEdgeSnap,
    private val ballMovement: FloatingMenuBallMovement,
    private val menuManager: FloatingMenuViewManager,
    private val hapticEnabled: Boolean,
) {
    fun handleUp() {
        val duration = System.currentTimeMillis() - state.downTime
        val finalDirection = resolveFinalDirection()
        val directionInfo =
            when {
                finalDirection != null -> "$finalDirection (${finalDirection.actionName})"
                state.canEnterLongPress && !state.hasMoved -> "未移动 (预留功能)"
                else -> "null"
            }

        FloatingDebugLog.d(
            LogTags.FLOATING_CONTROLLER,
            "⬆️ 松开 - 时长: ${duration}ms, 移动: ${state.hasMoved}, 长按: ${state.isLongPress}, 可长按: ${state.canEnterLongPress}, 方向: $directionInfo",
        )

        when {
            detector.isClick(duration) -> handleClick()
            state.isSecondStageLongPress && !state.hasMoved -> handleSecondStageLongPress()
            state.canEnterLongPress && !state.hasMoved -> handleReservedFunction()
            state.isLongPress && state.hasMoved -> handleLongPressDrag(finalDirection)
            state.hasMoved && !state.isLongPress -> handleNormalDrag()
        }

        state.cancelLongPressCallbacks()
        state.reset()
    }

    private fun resolveFinalDirection(): FloatingMenuGestureState.Direction? {
        if (!state.isLongPress || !state.hasMoved) {
            return null
        }

        val ballACenterX = paramsA.x + ballA.width / 2f
        val ballACenterY = paramsA.y + ballA.height / 2f
        val dx = ballACenterX - state.ballBCenterX
        val dy = ballACenterY - state.ballBCenterY
        return detector.getFinalDirection(dx, dy)
    }

    private fun handleClick() {
        if (hapticEnabled) {
            performHapticFeedbackCompat(HapticFeedbackConstants.CLOCK_TICK)
        }

        if (state.isMenuShown) {
            FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 点击！隐藏菜单")
            menuManager.hideMenu()
            return
        }

        if (hapticEnabled) {
            performHapticFeedbackCompat(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 点击！显示菜单")
        menuManager.showMenu()
    }

    private fun handleReservedFunction() {
        FloatingDebugLog.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "长按超过${LONG_PRESS_TIME_MS}ms但未移动 → 保持无动作",
        )
    }

    private fun handleSecondStageLongPress() {
        FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "二段长按松手 → 目标设备截图")
        scope.launch {
            val result = actions.captureTargetDeviceScreenshot()
            if (result.isFailure) {
                LogManager.e(
                    LogTags.FLOATING_CONTROLLER_MSG,
                    "目标设备截图失败: ${result.exceptionOrNull()?.message}",
                )
            }
        }
        edgeSnap.resetAPosition()
    }

    private fun handleLongPressDrag(direction: FloatingMenuGestureState.Direction?) {
        if (direction == null) {
            FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "长按拖动但未识别方向 → 预留功能")
            edgeSnap.resetAPosition()
            return
        }

        FloatingDebugLog.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "手势完成: ${direction.actionName} ($direction)",
        )

        scope.launch {
            when (direction) {
                FloatingMenuGestureState.Direction.LEFT -> dispatchKeyEvent(4, "手势返回键失败")
                FloatingMenuGestureState.Direction.RIGHT -> dispatchKeyEvent(187, "手势最近任务键失败")
                FloatingMenuGestureState.Direction.UP -> dispatchKeyEvent(3, "手势主页键失败")
                FloatingMenuGestureState.Direction.DOWN -> {
                    actions.controlViewModel.executeShellCommand("cmd statusbar expand-notifications")
                    FloatingDebugLog.d(
                        LogTags.FLOATING_CONTROLLER_MSG,
                        "📱 下拉通知栏: 执行命令 'cmd statusbar expand-notifications'",
                    )
                }
            }
        }

        edgeSnap.resetAPosition()
    }

    private suspend fun dispatchKeyEvent(
        keyCode: Int,
        failureLog: String,
    ) {
        val result = actions.controlViewModel.sendKeyEvent(keyCode)
        if (result.isFailure) {
            LogManager.e(
                LogTags.FLOATING_CONTROLLER_MSG,
                "$failureLog: ${result.exceptionOrNull()?.message}",
            )
        }
    }

    private fun handleNormalDrag() {
        ballMovement.alignBalls()
        edgeSnap.snapToEdge()
    }
}

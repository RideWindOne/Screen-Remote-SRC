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

        if (edgeSnap.revealFromEdge()) {
            return
        }

        if (state.isMenuShown) {
            menuManager.hideMenu()
            return
        }

        if (hapticEnabled) {
            performHapticFeedbackCompat(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        menuManager.showMenu()
    }

    private fun handleReservedFunction() {
    }

    private fun handleSecondStageLongPress() {
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
            edgeSnap.resetAPosition()
            return
        }

        scope.launch {
            when (direction) {
                FloatingMenuGestureState.Direction.LEFT -> dispatchKeyEvent(4, "手势返回键失败")
                FloatingMenuGestureState.Direction.RIGHT -> dispatchKeyEvent(187, "手势最近任务键失败")
                FloatingMenuGestureState.Direction.UP -> dispatchKeyEvent(3, "手势主页键失败")
                FloatingMenuGestureState.Direction.DOWN -> {
                    actions.controlViewModel.executeShellCommand("cmd statusbar expand-notifications")
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

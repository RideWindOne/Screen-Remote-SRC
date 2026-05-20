package com.screen.remote.android.feature.remote.widget.floating

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.screen.remote.android.core.common.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlin.math.hypot

/**
 * 手势识别处理器（纯 WindowManager 实现）
 *
 * 入口职责只保留：
 * 1. 触摸命中判断
 * 2. 手势状态记录
 * 3. 将长按调度、移动逻辑、松手动作分发给协作对象
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingMenuGestureHandler(
    context: Context,
    private val ballA: View,
    private val ballB: View,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    actions: FloatingMenuActions,
    scope: CoroutineScope,
    hapticEnabled: Boolean,
) : View.OnTouchListener {
    private val state = FloatingMenuGestureState()
    private val detector = FloatingMenuGestureDetector(context, state, hapticEnabled)
    private val menuManager =
        FloatingMenuViewManager(
            context = context,
            windowManager = windowManager,
            paramsA = paramsA,
            ballA = ballA,
            ballB = this.ballB,
            actions = actions,
            scope = scope,
            state = state,
            hapticEnabled = hapticEnabled,
        )
    private val edgeSnap =
        FloatingMenuEdgeSnap(
            context = context,
            ballA = ballA,
            ballB = this.ballB,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            state = state,
            menuManager = menuManager,
            hapticEnabled = hapticEnabled,
        )
    private val ballMovement =
        FloatingMenuBallMovement(
            context = context,
            ballA = ballA,
            ballB = this.ballB,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            state = state,
            edgeSnap = edgeSnap,
            menuManager = menuManager,
        )
    private val longPressScheduler =
        FloatingMenuLongPressScheduler(
            state = state,
            hapticEnabled = hapticEnabled,
        )
    private val completionHandler =
        FloatingMenuGestureCompletionHandler(
            ballA = ballA,
            paramsA = paramsA,
            actions = actions,
            scope = scope,
            state = state,
            detector = detector,
            edgeSnap = edgeSnap,
            ballMovement = ballMovement,
            menuManager = menuManager,
            hapticEnabled = hapticEnabled,
        )

    override fun onTouch(
        v: View,
        event: MotionEvent,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchInsideCircle(v, event)) {
                    FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER, "❌ 触摸点在圆外")
                    return false
                }
                handleDown(event)
            }

            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> completionHandler.handleUp()
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    private fun isTouchInsideCircle(
        view: View,
        event: MotionEvent,
    ): Boolean {
        val radius = view.width / 2f
        val centerX = view.width / 2f
        val centerY = view.height / 2f
        val distance = hypot((event.x - centerX).toDouble(), (event.y - centerY).toDouble())
        return distance <= radius
    }

    private fun handleDown(event: MotionEvent) {
        edgeSnap.cancelAnimation()
        state.cancelLongPressCallbacks()
        state.initHandlers()

        state.downTime = System.currentTimeMillis()
        state.downRawX = event.rawX
        state.downRawY = event.rawY
        state.lastRawX = event.rawX
        state.lastRawY = event.rawY
        state.hasMoved = false
        state.isLongPress = false
        state.canEnterLongPress = false
        state.isSecondStageLongPress = false

        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        val ballACenterX = paramsA.x + ballA.width / 2f
        val ballACenterY = paramsA.y + ballA.height / 2f
        state.downOffsetX = event.rawX - ballACenterX
        state.downOffsetY = event.rawY - ballACenterY

        longPressScheduler.schedule()

        FloatingDebugLog.d(
            LogTags.FLOATING_CONTROLLER,
            "⬇️ 按下 at (${event.rawX}, ${event.rawY}), " +
                "B中心=(${state.ballBCenterX}, ${state.ballBCenterY}), " +
                "A中心=($ballACenterX, $ballACenterY), " +
                "A左上角=(${paramsA.x}, ${paramsA.y}), " +
                "偏移=(${state.downOffsetX}, ${state.downOffsetY})",
        )
    }

    private fun handleMove(event: MotionEvent) {
        val dx = event.rawX - state.downRawX
        val dy = event.rawY - state.downRawY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val duration = System.currentTimeMillis() - state.downTime

        detector.checkLongPressTransition(distance, duration)

        if (!detector.checkMovementThreshold(dx, dy)) {
            return
        }

        if (state.isLongPress) {
            ballMovement.moveAAroundB(event, detector)
        } else {
            ballMovement.moveAAndBTogether(event)
        }
    }

    private fun handleCancel() {
        FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER, "❌ 手势取消")
        state.cancelLongPressCallbacks()
        state.reset()
    }

    fun cleanup() {
        edgeSnap.cleanup()
        state.cleanup()
        menuManager.cleanup()

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

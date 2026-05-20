package com.screen.remote.android.feature.remote.widget.floating

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.screen.remote.android.core.common.LogTags

/**
 * 贴边逻辑控制器
 * 对外保留统一门面，内部拆为边缘判定与动画执行。
 */
internal class FloatingMenuEdgeSnap(
    context: Context,
    ballA: View,
    ballB: View,
    windowManager: WindowManager,
    paramsA: WindowManager.LayoutParams,
    paramsB: WindowManager.LayoutParams,
    private val state: FloatingMenuGestureState,
    private val menuManager: FloatingMenuViewManager,
    hapticEnabled: Boolean,
) {
    private val density = context.resources.displayMetrics.density
    private val analyzer =
        FloatingMenuEdgeAnalyzer(
            context = context,
            state = state,
            hapticEnabled = hapticEnabled,
        )
    private val animator =
        FloatingMenuEdgeAnimator(
            ballA = ballA,
            ballB = ballB,
            windowManager = windowManager,
            paramsA = paramsA,
            paramsB = paramsB,
            state = state,
            menuManager = menuManager,
        )

    fun checkDragOut(
        deltaX: Float,
        deltaY: Float,
    ) {
        analyzer.checkDragOut(deltaX, deltaY) {
            state.isSnappedToEdge = false
            state.snappedEdge = null
            FloatingDebugLog.d(LogTags.FLOATING_CONTROLLER_MSG, "🔓 拖出贴边")
            menuManager.centerMenuHorizontally()
        }
    }

    fun checkEdgeHaptic(
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        analyzer.checkEdgeHaptic(centerX, centerY, radius)
    }

    fun snapToEdge() {
        val target = analyzer.resolveSnapTarget(animator.paramsA, animator.paramsB, animator.ballA, animator.ballB)
        if (target == null) {
            return
        }

        state.isSnappedToEdge = true
        state.snappedEdge = target.edge

        FloatingDebugLog.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "🧲 贴边${target.edge.name}: 从(${animator.paramsA.x}, ${animator.paramsA.y}) → (${target.targetX}, ${target.targetY}), " +
                "目标露出=${EDGE_VISIBLE_WIDTH_DP}dp(${(EDGE_VISIBLE_WIDTH_DP * density).toInt()}px), " +
                "实际露出=${target.actualVisibleWidth}px, 小球大小=${animator.ballA.width}px",
        )

        animator.animateToEdge(target)
    }

    fun resetAPosition() {
        animator.resetAPosition()
    }

    fun cancelAnimation() {
        animator.cancelAnimation()
    }

    fun cleanup() {
        animator.cleanup()
    }
}

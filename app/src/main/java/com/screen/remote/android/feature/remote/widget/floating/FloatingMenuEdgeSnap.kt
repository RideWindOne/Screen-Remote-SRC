package com.screen.remote.android.feature.remote.widget.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager

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

internal class FloatingMenuEdgeAnalyzer(
    context: Context,
    private val state: FloatingMenuGestureState,
    private val hapticEnabled: Boolean,
) {
    private val density = context.resources.displayMetrics.density
    private val displayMetrics = context.resources.displayMetrics

    fun checkDragOut(
        deltaX: Float,
        deltaY: Float,
        onDraggedOut: () -> Unit,
    ) {
        val snappedEdge = state.snappedEdge ?: return
        if (!state.isSnappedToEdge) return

        val dragOutThreshold = EDGE_DRAG_OUT_THRESHOLD_DP * density
        val shouldDragOut =
            when (snappedEdge) {
                FloatingMenuGestureState.Edge.LEFT -> deltaX > dragOutThreshold
                FloatingMenuGestureState.Edge.RIGHT -> deltaX < -dragOutThreshold
                FloatingMenuGestureState.Edge.TOP -> deltaY > dragOutThreshold
                FloatingMenuGestureState.Edge.BOTTOM -> deltaY < -dragOutThreshold
            }

        if (shouldDragOut) {
            onDraggedOut()
        }
    }

    fun checkEdgeHaptic(
        centerX: Float,
        centerY: Float,
        radius: Float,
    ) {
        if (state.isSnappedToEdge || !hapticEnabled) return

        val snapThreshold = EDGE_SNAP_THRESHOLD_DP * density
        val distToLeft = centerX - radius
        val distToRight = displayMetrics.widthPixels - (centerX + radius)
        val distToTop = centerY - radius
        val distToBottom = displayMetrics.heightPixels - (centerY + radius)

        val reachedEdgeInfo =
            when {
                distToLeft < snapThreshold -> FloatingMenuGestureState.Edge.LEFT to distToLeft
                distToRight < snapThreshold -> FloatingMenuGestureState.Edge.RIGHT to distToRight
                distToTop < snapThreshold -> FloatingMenuGestureState.Edge.TOP to distToTop
                distToBottom < snapThreshold -> FloatingMenuGestureState.Edge.BOTTOM to distToBottom
                else -> null
            }

        val distanceToNearestEdge =
            reachedEdgeInfo?.second ?: minOf(distToLeft, distToRight, distToTop, distToBottom)
        val currentEdge = reachedEdgeInfo?.first

        if (currentEdge != null &&
            !state.hasTriggeredEdgeHaptic &&
            (currentEdge == FloatingMenuGestureState.Edge.LEFT || currentEdge == FloatingMenuGestureState.Edge.RIGHT)
        ) {
            performHapticFeedbackCompat(HapticFeedbackConstants.VIRTUAL_KEY)
            state.hasTriggeredEdgeHaptic = true
            FloatingDebugLog.d(
                LogTags.FLOATING_CONTROLLER_MSG,
                "🧲 进入边缘区域: ${currentEdge.name}, 距离=${distanceToNearestEdge.toInt()}px",
            )
        }

        val hapticResetThreshold = EDGE_HAPTIC_RESET_DISTANCE_DP * density
        if (state.hasTriggeredEdgeHaptic && distanceToNearestEdge > hapticResetThreshold) {
            state.hasTriggeredEdgeHaptic = false
            FloatingDebugLog.d(
                LogTags.FLOATING_CONTROLLER_MSG,
                "↩️ 离开边缘${distanceToNearestEdge.toInt()}px（阈值${hapticResetThreshold.toInt()}px），重置触感状态",
            )
        }
    }

    fun resolveSnapTarget(
        paramsA: WindowManager.LayoutParams,
        paramsB: WindowManager.LayoutParams,
        ballA: View,
        ballB: View,
    ): FloatingMenuSnapTarget? {
        val ballLeftEdge = paramsA.x.toFloat()
        val ballRightEdge = paramsA.x + ballA.width
        val ballTopEdge = paramsA.y.toFloat()
        val ballBottomEdge = paramsA.y + ballA.height
        val distanceToRight = (displayMetrics.widthPixels - ballRightEdge).toFloat()
        val distanceToBottom = (displayMetrics.heightPixels - ballBottomEdge).toFloat()

        val nearest =
            listOf(
                ballLeftEdge to FloatingMenuGestureState.Edge.LEFT,
                distanceToRight to FloatingMenuGestureState.Edge.RIGHT,
                ballTopEdge to FloatingMenuGestureState.Edge.TOP,
                distanceToBottom to FloatingMenuGestureState.Edge.BOTTOM,
            ).minByOrNull { (distance, _) -> distance } ?: return null

        val minDistance = nearest.first
        val edge = nearest.second
        val snapThreshold = EDGE_SNAP_THRESHOLD_DP * density
        if (minDistance > snapThreshold) {
            FloatingDebugLog.d(
                LogTags.FLOATING_CONTROLLER_MSG,
                "🚫 距离边缘${minDistance.toInt()}px，不贴边（阈值${snapThreshold.toInt()}px）",
            )
            return null
        }

        val visibleWidth = EDGE_VISIBLE_WIDTH_DP * density
        val (targetX, targetY, targetBX, targetBY) =
            when (edge) {
                FloatingMenuGestureState.Edge.LEFT ->
                    listOf(
                        (visibleWidth - ballA.width).toInt(),
                        paramsA.y,
                        (visibleWidth - ballB.width).toInt(),
                        paramsB.y,
                    )

                FloatingMenuGestureState.Edge.RIGHT ->
                    listOf(
                        (displayMetrics.widthPixels - visibleWidth).toInt(),
                        paramsA.y,
                        (displayMetrics.widthPixels - visibleWidth).toInt(),
                        paramsB.y,
                    )

                FloatingMenuGestureState.Edge.TOP ->
                    listOf(
                        paramsA.x,
                        (visibleWidth - ballA.height).toInt(),
                        paramsB.x,
                        (visibleWidth - ballB.height).toInt(),
                    )

                FloatingMenuGestureState.Edge.BOTTOM ->
                    listOf(
                        paramsA.x,
                        (displayMetrics.heightPixels - visibleWidth).toInt(),
                        paramsB.x,
                        (displayMetrics.heightPixels - visibleWidth).toInt(),
                    )
            }

        val actualVisibleWidth =
            when (edge) {
                FloatingMenuGestureState.Edge.LEFT -> targetX + ballA.width
                FloatingMenuGestureState.Edge.RIGHT -> displayMetrics.widthPixels - targetX
                FloatingMenuGestureState.Edge.TOP -> targetY + ballA.height
                FloatingMenuGestureState.Edge.BOTTOM -> displayMetrics.heightPixels - targetY
            }

        return FloatingMenuSnapTarget(
            edge = edge,
            targetX = targetX,
            targetY = targetY,
            targetBX = targetBX,
            targetBY = targetBY,
            actualVisibleWidth = actualVisibleWidth,
        )
    }
}

internal data class FloatingMenuSnapTarget(
    val edge: FloatingMenuGestureState.Edge,
    val targetX: Int,
    val targetY: Int,
    val targetBX: Int,
    val targetBY: Int,
    val actualVisibleWidth: Int,
)

internal class FloatingMenuEdgeAnimator(
    internal val ballA: View,
    internal val ballB: View,
    private val windowManager: WindowManager,
    internal val paramsA: WindowManager.LayoutParams,
    internal val paramsB: WindowManager.LayoutParams,
    private val state: FloatingMenuGestureState,
    private val menuManager: FloatingMenuViewManager,
) {
    private var resetAnimator: ValueAnimator? = null

    fun animateToEdge(target: FloatingMenuSnapTarget) {
        val startAX = paramsA.x
        val startAY = paramsA.y
        val startBX = paramsB.x
        val startBY = paramsB.y
        val startMenuX = menuManager.getMenuX()
        val startMenuY = menuManager.getMenuY()

        resetAnimator?.cancel()
        resetAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float

                    paramsA.x = (startAX + (target.targetX - startAX) * fraction).toInt()
                    paramsA.y = (startAY + (target.targetY - startAY) * fraction).toInt()
                    paramsB.x = (startBX + (target.targetBX - startBX) * fraction).toInt()
                    paramsB.y = (startBY + (target.targetBY - startBY) * fraction).toInt()

                    state.ballBCenterX = paramsB.x + ballB.width / 2f
                    state.ballBCenterY = paramsB.y + ballB.height / 2f

                    try {
                        windowManager.updateViewLayout(ballA, paramsA)
                        windowManager.updateViewLayout(ballB, paramsB)
                        menuManager.animateMenuWithSnap(
                            startMenuX = startMenuX,
                            startMenuY = startMenuY,
                            deltaX = target.targetX - startAX,
                            deltaY = target.targetY - startAY,
                            fraction = fraction,
                        )
                    } catch (e: Exception) {
                        LogManager.e(LogTags.FLOATING_CONTROLLER, "贴边动画更新失败: ${e.message}")
                        cancel()
                    }
                }
                start()
            }
    }

    fun resetAPosition() {
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        val targetX = (state.ballBCenterX - ballA.width / 2f).toInt()
        val targetY = (state.ballBCenterY - ballA.height / 2f).toInt()
        val targetACenterX = targetX + ballA.width / 2f
        val targetACenterY = targetY + ballA.height / 2f

        FloatingDebugLog.d(
            LogTags.FLOATING_CONTROLLER_MSG,
            "🎯 开始归位: A从(${paramsA.x}, ${paramsA.y}) → ($targetX, $targetY), " +
                "B中心=(${state.ballBCenterX}, ${state.ballBCenterY}), 归位后A中心=($targetACenterX, $targetACenterY)",
        )

        val startX = paramsA.x
        val startY = paramsA.y

        resetAnimator?.cancel()
        resetAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = RESET_ANIMATION_DURATION_MS
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    paramsA.x = (startX + (targetX - startX) * fraction).toInt()
                    paramsA.y = (startY + (targetY - startY) * fraction).toInt()
                    try {
                        windowManager.updateViewLayout(ballA, paramsA)
                    } catch (e: Exception) {
                        LogManager.e(LogTags.FLOATING_CONTROLLER, "归位动画更新失败: ${e.message}")
                        cancel()
                    }
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            val finalACenterX = paramsA.x + ballA.width / 2f
                            val finalACenterY = paramsA.y + ballA.height / 2f
                            FloatingDebugLog.d(
                                LogTags.FLOATING_CONTROLLER_MSG,
                                "归位完成: A左上角=(${paramsA.x}, ${paramsA.y}), " +
                                    "A中心=($finalACenterX, $finalACenterY), B中心=(${state.ballBCenterX}, ${state.ballBCenterY})",
                            )
                        }
                    },
                )
                start()
            }
    }

    fun cancelAnimation() {
        resetAnimator?.cancel()
        resetAnimator = null
    }

    fun cleanup() {
        cancelAnimation()
    }
}

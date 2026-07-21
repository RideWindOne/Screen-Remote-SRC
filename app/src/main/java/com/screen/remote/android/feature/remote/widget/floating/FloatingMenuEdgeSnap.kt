package com.screen.remote.android.feature.remote.widget.floating

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
            animator.showFully()
            state.isSnappedToEdge = false
            state.snappedEdge = null
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

        menuManager.hideMenu()
        animator.animateToEdge(target)
    }

    fun revealFromEdge(): Boolean {
        val target =
            analyzer.resolveRevealTarget(
                paramsA = animator.paramsA,
                ballA = animator.ballA,
                ballB = animator.ballB,
            ) ?: return false

        state.isSnappedToEdge = false
        state.snappedEdge = null
        animator.animateToEdge(target)
        return true
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
    private val windowBoundsProvider = FloatingMenuWindowBoundsProvider(context)

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
        val windowBounds = windowBoundsProvider.current()
        val distToLeft = centerX - radius - windowBounds.left
        val distToRight = windowBounds.right - (centerX + radius)
        val distToTop = centerY - radius - windowBounds.top
        val distToBottom = windowBounds.bottom - (centerY + radius)

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
        }

        val hapticResetThreshold = EDGE_HAPTIC_RESET_DISTANCE_DP * density
        if (state.hasTriggeredEdgeHaptic && distanceToNearestEdge > hapticResetThreshold) {
            state.hasTriggeredEdgeHaptic = false
        }
    }

    fun resolveSnapTarget(
        paramsA: WindowManager.LayoutParams,
        paramsB: WindowManager.LayoutParams,
        ballA: View,
        ballB: View,
    ): FloatingMenuSnapTarget? {
        val windowBounds = windowBoundsProvider.current()
        val ballLeftEdge = (paramsA.x - windowBounds.left).toFloat()
        val ballRightEdge = paramsA.x + ballA.width
        val ballTopEdge = (paramsA.y - windowBounds.top).toFloat()
        val ballBottomEdge = paramsA.y + ballA.height
        val distanceToRight = (windowBounds.right - ballRightEdge).toFloat()
        val distanceToBottom = (windowBounds.bottom - ballBottomEdge).toFloat()

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
            return null
        }

        val coordinates =
            calculateFloatingMenuSnapCoordinates(
                edge = edge,
                displayWidth = windowBounds.right,
                displayHeight = windowBounds.bottom,
                ballAWidth = ballA.width,
                ballAHeight = ballA.height,
                ballBWidth = ballB.width,
                ballBHeight = ballB.height,
                currentAX = paramsA.x,
                currentAY = paramsA.y,
                displayLeft = windowBounds.left,
                displayTop = windowBounds.top,
            )
        val targetX = coordinates.targetAX
        val targetY = coordinates.targetAY
        val targetBX = coordinates.targetBX
        val targetBY = coordinates.targetBY

        return FloatingMenuSnapTarget(
            edge = edge,
            targetX = targetX,
            targetY = targetY,
            targetBX = targetBX,
            targetBY = targetBY,
            actualVisibleWidth = calculateFloatingBallVisibleSize(ballA.width, ballA.height),
            hiddenOffsetPx = calculateFloatingBallHiddenSize(ballA.width, ballA.height).toFloat(),
        )
    }

    fun resolveRevealTarget(
        paramsA: WindowManager.LayoutParams,
        ballA: View,
        ballB: View,
    ): FloatingMenuSnapTarget? {
        val edge = state.snappedEdge ?: return null
        if (!state.isSnappedToEdge) return null
        val windowBounds = windowBoundsProvider.current()

        val coordinates =
            calculateFloatingMenuRevealCoordinates(
                edge = edge,
                displayWidth = windowBounds.right,
                displayHeight = windowBounds.bottom,
                ballAWidth = ballA.width,
                ballAHeight = ballA.height,
                ballBWidth = ballB.width,
                ballBHeight = ballB.height,
                currentAX = paramsA.x,
                currentAY = paramsA.y,
                displayLeft = windowBounds.left,
                displayTop = windowBounds.top,
            )
        return FloatingMenuSnapTarget(
            edge = edge,
            targetX = coordinates.targetAX,
            targetY = coordinates.targetAY,
            targetBX = coordinates.targetBX,
            targetBY = coordinates.targetBY,
            actualVisibleWidth = minOf(ballA.width, ballA.height),
            hiddenOffsetPx = 0f,
        )
    }
}

internal data class FloatingMenuSnapCoordinates(
    val targetAX: Int,
    val targetAY: Int,
    val targetBX: Int,
    val targetBY: Int,
)

internal fun calculateFloatingMenuSnapCoordinates(
    edge: FloatingMenuGestureState.Edge,
    displayWidth: Int,
    displayHeight: Int,
    ballAWidth: Int,
    ballAHeight: Int,
    ballBWidth: Int,
    ballBHeight: Int,
    currentAX: Int,
    currentAY: Int,
    displayLeft: Int = 0,
    displayTop: Int = 0,
): FloatingMenuSnapCoordinates {
    val centerOffsetX = (ballAWidth - ballBWidth) / 2f
    val centerOffsetY = (ballAHeight - ballBHeight) / 2f
    val targetAX =
        when (edge) {
            FloatingMenuGestureState.Edge.LEFT -> displayLeft
            FloatingMenuGestureState.Edge.RIGHT -> displayWidth - ballAWidth
            else -> currentAX
        }
    val targetAY =
        when (edge) {
            FloatingMenuGestureState.Edge.TOP -> displayTop
            FloatingMenuGestureState.Edge.BOTTOM -> displayHeight - ballAHeight
            else -> currentAY
        }
    return FloatingMenuSnapCoordinates(
        targetAX = targetAX,
        targetAY = targetAY,
        targetBX = (targetAX + centerOffsetX).toInt(),
        targetBY = (targetAY + centerOffsetY).toInt(),
    )
}

internal fun calculateFloatingBallVisibleSize(
    width: Int,
    height: Int,
): Int = minOf(width, height) * EDGE_VISIBLE_NUMERATOR / EDGE_VISIBLE_DENOMINATOR

private fun calculateFloatingBallHiddenSize(
    width: Int,
    height: Int,
): Int = minOf(width, height) - calculateFloatingBallVisibleSize(width, height)

internal fun calculateFloatingMenuRevealCoordinates(
    edge: FloatingMenuGestureState.Edge,
    displayWidth: Int,
    displayHeight: Int,
    ballAWidth: Int,
    ballAHeight: Int,
    ballBWidth: Int,
    ballBHeight: Int,
    currentAX: Int,
    currentAY: Int,
    displayLeft: Int = 0,
    displayTop: Int = 0,
): FloatingMenuSnapCoordinates {
    val centerOffsetX = (ballAWidth - ballBWidth) / 2f
    val centerOffsetY = (ballAHeight - ballBHeight) / 2f
    val targetAX =
        when (edge) {
            FloatingMenuGestureState.Edge.LEFT -> displayLeft
            FloatingMenuGestureState.Edge.RIGHT -> displayWidth - ballAWidth
            else -> currentAX
        }
    val targetAY =
        when (edge) {
            FloatingMenuGestureState.Edge.TOP -> displayTop
            FloatingMenuGestureState.Edge.BOTTOM -> displayHeight - ballAHeight
            else -> currentAY
        }
    return FloatingMenuSnapCoordinates(
        targetAX = targetAX,
        targetAY = targetAY,
        targetBX = (targetAX + centerOffsetX).toInt(),
        targetBY = (targetAY + centerOffsetY).toInt(),
    )
}

internal data class FloatingMenuSnapTarget(
    val edge: FloatingMenuGestureState.Edge,
    val targetX: Int,
    val targetY: Int,
    val targetBX: Int,
    val targetBY: Int,
    val actualVisibleWidth: Int,
    val hiddenOffsetPx: Float,
)

private const val EDGE_VISIBLE_NUMERATOR = 2
private const val EDGE_VISIBLE_DENOMINATOR = 5

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
        val startHiddenOffset = (ballA as? FloatingBallView)?.hiddenOffsetPx ?: 0f

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
                    val hiddenOffset = startHiddenOffset + (target.hiddenOffsetPx - startHiddenOffset) * fraction
                    if (hiddenOffset <= 0.5f) {
                        showFully()
                    } else {
                        (ballA as? FloatingBallView)?.setEdgeHidden(target.edge, hiddenOffset)
                        (ballB as? FloatingBallView)?.setEdgeHidden(target.edge, hiddenOffset)
                    }

                    state.ballBCenterX = paramsB.x + ballB.width / 2f
                    state.ballBCenterY = paramsB.y + ballB.height / 2f

                    try {
                        windowManager.updateViewLayout(ballA, paramsA)
                        windowManager.updateViewLayout(ballB, paramsB)
                        menuManager.syncMenuToBall()
                    } catch (e: Exception) {
                        LogManager.e(LogTags.FLOATING_CONTROLLER, "Welt animation update failed: ${e.message}")
                        cancel()
                    }
                }
                start()
            }
    }

    fun showFully() {
        (ballA as? FloatingBallView)?.showFully()
        (ballB as? FloatingBallView)?.showFully()
    }

    fun resetAPosition() {
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f

        val targetX = (state.ballBCenterX - ballA.width / 2f).toInt()
        val targetY = (state.ballBCenterY - ballA.height / 2f).toInt()
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
                        LogManager.e(LogTags.FLOATING_CONTROLLER, "Home animation update failed: ${e.message}")
                        cancel()
                    }
                }
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

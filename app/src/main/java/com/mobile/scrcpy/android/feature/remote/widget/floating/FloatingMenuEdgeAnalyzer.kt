package com.mobile.scrcpy.android.feature.remote.widget.floating

import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags

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
            Log.d(
                LogTags.FLOATING_CONTROLLER_MSG,
                "🧲 进入边缘区域: ${currentEdge.name}, 距离=${distanceToNearestEdge.toInt()}px",
            )
        }

        val hapticResetThreshold = EDGE_HAPTIC_RESET_DISTANCE_DP * density
        if (state.hasTriggeredEdgeHaptic && distanceToNearestEdge > hapticResetThreshold) {
            state.hasTriggeredEdgeHaptic = false
            Log.d(
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
            Log.d(
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

package com.mobile.scrcpy.android.feature.remote.components.floating

import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import com.mobile.scrcpy.android.core.common.LogTags

/**
 * 贴边逻辑控制器
 * 负责处理球体贴边、拖出贴边、边缘触感反馈
 */
internal class FloatingMenuEdgeSnap(
    private val context: Context,
    private val ballA: View,
    private val ballB: View,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    private val state: FloatingMenuGestureState,
    private val menuManager: FloatingMenuViewManager,
    private val hapticEnabled: Boolean
) {
    
    private val density = context.resources.displayMetrics.density
    private val displayMetrics = context.resources.displayMetrics
    private var resetAnimator: ValueAnimator? = null
    
    /**
     * 检测是否拖出贴边状态
     */
    fun checkDragOut(deltaX: Float, deltaY: Float) {
        if (!state.isSnappedToEdge || state.snappedEdge == null) return
        
        val dragOutThreshold = EDGE_DRAG_OUT_THRESHOLD_DP * density
        val shouldDragOut = when (state.snappedEdge!!) {
            FloatingMenuGestureState.Edge.LEFT -> deltaX > dragOutThreshold
            FloatingMenuGestureState.Edge.RIGHT -> deltaX < -dragOutThreshold
            FloatingMenuGestureState.Edge.TOP -> deltaY > dragOutThreshold
            FloatingMenuGestureState.Edge.BOTTOM -> deltaY < -dragOutThreshold
        }
        
        if (shouldDragOut) {
            state.isSnappedToEdge = false
            state.snappedEdge = null
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔓 拖出贴边")
            
            // 菜单居中对齐
            menuManager.centerMenuHorizontally()
        }
    }
    
    /**
     * 检测边缘触感反馈
     */
    fun checkEdgeHaptic(centerX: Float, centerY: Float, radius: Float) {
        if (state.isSnappedToEdge || !hapticEnabled) return
        
        val snapThreshold = EDGE_SNAP_THRESHOLD_DP * density
        
        // 计算到各边缘的距离
        val distToLeft = centerX - radius
        val distToRight = displayMetrics.widthPixels - (centerX + radius)
        val distToTop = centerY - radius
        val distToBottom = displayMetrics.heightPixels - (centerY + radius)
        
        var reachedEdge = false
        var currentEdge: FloatingMenuGestureState.Edge? = null
        var distanceToNearestEdge = Float.MAX_VALUE
        
        // 检测到达哪个边缘
        when {
            distToLeft < snapThreshold -> {
                reachedEdge = true
                currentEdge = FloatingMenuGestureState.Edge.LEFT
                distanceToNearestEdge = distToLeft
            }
            distToRight < snapThreshold -> {
                reachedEdge = true
                currentEdge = FloatingMenuGestureState.Edge.RIGHT
                distanceToNearestEdge = distToRight
            }
            distToTop < snapThreshold -> {
                reachedEdge = true
                currentEdge = FloatingMenuGestureState.Edge.TOP
                distanceToNearestEdge = distToTop
            }
            distToBottom < snapThreshold -> {
                reachedEdge = true
                currentEdge = FloatingMenuGestureState.Edge.BOTTOM
                distanceToNearestEdge = distToBottom
            }
            else -> {
                distanceToNearestEdge = minOf(distToLeft, distToRight, distToTop, distToBottom)
            }
        }
        
        // 触发边缘触感（只有左右边缘）
        if (reachedEdge && !state.hasTriggeredEdgeHaptic) {
            if (currentEdge == FloatingMenuGestureState.Edge.LEFT || 
                currentEdge == FloatingMenuGestureState.Edge.RIGHT) {
                performHapticFeedbackCompat(HapticFeedbackConstants.VIRTUAL_KEY)
                state.hasTriggeredEdgeHaptic = true
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, 
                    "🧲 进入边缘区域: ${currentEdge.name}, 距离=${distanceToNearestEdge.toInt()}px")
            }
        }
        
        // 重置触感状态
        val hapticResetThreshold = EDGE_HAPTIC_RESET_DISTANCE_DP * density
        if (state.hasTriggeredEdgeHaptic && distanceToNearestEdge > hapticResetThreshold) {
            state.hasTriggeredEdgeHaptic = false
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, 
                "↩️ 离开边缘${distanceToNearestEdge.toInt()}px（阈值${hapticResetThreshold.toInt()}px），重置触感状态")
        }
    }
    
    /**
     * 贴边隐藏：手动拖到屏幕边缘时才贴边，隐藏2/3，露出1/3
     */
    fun snapToEdge() {
        // 计算小球边缘到屏幕边缘的距离
        val ballLeftEdge = paramsA.x.toFloat()
        val ballRightEdge = paramsA.x + ballA.width
        val ballTopEdge = paramsA.y.toFloat()
        val ballBottomEdge = paramsA.y + ballA.height
        
        val distanceToLeft = ballLeftEdge
        val distanceToRight = (displayMetrics.widthPixels - ballRightEdge).toFloat()
        val distanceToBottom = (displayMetrics.heightPixels - ballBottomEdge).toFloat()
        
        // 找到最近的边
        val distances = listOf(
            distanceToLeft to FloatingMenuGestureState.Edge.LEFT,
            distanceToRight to FloatingMenuGestureState.Edge.RIGHT,
            ballTopEdge to FloatingMenuGestureState.Edge.TOP,
            distanceToBottom to FloatingMenuGestureState.Edge.BOTTOM
        )
        val minPair = distances.minByOrNull { (distance, _) -> distance } ?: return
        val minDistance: Float = minPair.first
        val edge: FloatingMenuGestureState.Edge = minPair.second
        
        // 贴边阈值
        val snapThreshold = EDGE_SNAP_THRESHOLD_DP * density
        if (minDistance > snapThreshold) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, 
                "🚫 距离边缘${minDistance.toInt()}px，不贴边（阈值${snapThreshold.toInt()}px）")
            return
        }
        
        // 计算目标位置
        val visibleWidth = EDGE_VISIBLE_WIDTH_DP * density
        val (targetX, targetY, targetBX, targetBY) = calculateSnapTargets(edge, visibleWidth)
        
        // 标记为已贴边
        state.isSnappedToEdge = true
        state.snappedEdge = edge
        
        val actualVisibleWidth = when (edge) {
            FloatingMenuGestureState.Edge.LEFT -> targetX + ballA.width
            FloatingMenuGestureState.Edge.RIGHT -> displayMetrics.widthPixels - targetX
            FloatingMenuGestureState.Edge.TOP -> targetY + ballA.height
            FloatingMenuGestureState.Edge.BOTTOM -> displayMetrics.heightPixels - targetY
        }
        
        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🧲 贴边${edge.name}: 从(${paramsA.x}, ${paramsA.y}) → ($targetX, $targetY), " +
                    "目标露出=${EDGE_VISIBLE_WIDTH_DP}dp(${(EDGE_VISIBLE_WIDTH_DP * density).toInt()}px), " +
                    "实际露出=${actualVisibleWidth}px, 小球大小=${ballA.width}px")
        
        // 动画移动到边缘
        animateToEdge(targetX, targetY, targetBX, targetBY)
    }
    
    /**
     * 计算贴边目标位置
     */
    private fun calculateSnapTargets(
        edge: FloatingMenuGestureState.Edge, 
        visibleWidth: Float
    ): List<Int> {
        return when (edge) {
            FloatingMenuGestureState.Edge.LEFT -> listOf(
                (visibleWidth - ballA.width).toInt(),
                paramsA.y,
                (visibleWidth - ballB.width).toInt(),
                paramsB.y
            )
            FloatingMenuGestureState.Edge.RIGHT -> listOf(
                (displayMetrics.widthPixels - visibleWidth).toInt(),
                paramsA.y,
                (displayMetrics.widthPixels - visibleWidth).toInt(),
                paramsB.y
            )
            FloatingMenuGestureState.Edge.TOP -> listOf(
                paramsA.x,
                (visibleWidth - ballA.height).toInt(),
                paramsB.x,
                (visibleWidth - ballB.height).toInt()
            )
            FloatingMenuGestureState.Edge.BOTTOM -> listOf(
                paramsA.x,
                (displayMetrics.heightPixels - visibleWidth).toInt(),
                paramsB.x,
                (displayMetrics.heightPixels - visibleWidth).toInt()
            )
        }
    }
    
    /**
     * 动画移动到边缘
     */
    private fun animateToEdge(targetX: Int, targetY: Int, targetBX: Int, targetBY: Int) {
        val startAX = paramsA.x
        val startAY = paramsA.y
        val startBX = paramsB.x
        val startBY = paramsB.y
        
        val startMenuX = menuManager.getMenuX()
        val startMenuY = menuManager.getMenuY()
        
        resetAnimator?.cancel()
        resetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                
                paramsA.x = (startAX + (targetX - startAX) * fraction).toInt()
                paramsA.y = (startAY + (targetY - startAY) * fraction).toInt()
                paramsB.x = (startBX + (targetBX - startBX) * fraction).toInt()
                paramsB.y = (startBY + (targetBY - startBY) * fraction).toInt()
                
                // 更新 B 球中心位置
                state.ballBCenterX = paramsB.x + ballB.width / 2f
                state.ballBCenterY = paramsB.y + ballB.height / 2f
                
                try {
                    windowManager.updateViewLayout(ballA, paramsA)
                    windowManager.updateViewLayout(ballB, paramsB)
                    
                    // 菜单跟随贴边
                    menuManager.animateMenuWithSnap(
                        startMenuX, startMenuY,
                        targetX - startAX, targetY - startAY,
                        fraction
                    )
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "贴边动画更新失败: ${e.message}")
                    cancel()
                }
            }
            start()
        }
    }
    
    /**
     * 归位：A 球回到 B 球中心
     */
    fun resetAPosition() {
        // 重新计算 B 球中心
        state.ballBCenterX = paramsB.x + ballB.width / 2f
        state.ballBCenterY = paramsB.y + ballB.height / 2f
        
        val targetX = (state.ballBCenterX - ballA.width / 2f).toInt()
        val targetY = (state.ballBCenterY - ballA.height / 2f).toInt()
        
        val targetACenterX = targetX + ballA.width / 2f
        val targetACenterY = targetY + ballA.height / 2f
        
        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🎯 开始归位: A从(${paramsA.x}, ${paramsA.y}) → ($targetX, $targetY), " +
                    "B中心=(${state.ballBCenterX}, ${state.ballBCenterY}), 归位后A中心=($targetACenterX, $targetACenterY)")
        
        val startX = paramsA.x
        val startY = paramsA.y
        
        resetAnimator?.cancel()
        resetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RESET_ANIMATION_DURATION_MS
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                paramsA.x = (startX + (targetX - startX) * fraction).toInt()
                paramsA.y = (startY + (targetY - startY) * fraction).toInt()
                try {
                    windowManager.updateViewLayout(ballA, paramsA)
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "归位动画更新失败: ${e.message}")
                    cancel()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val finalACenterX = paramsA.x + ballA.width / 2f
                    val finalACenterY = paramsA.y + ballA.height / 2f
                    Log.d(
                        LogTags.FLOATING_CONTROLLER_MSG,
                        "✅ 归位完成: A左上角=(${paramsA.x}, ${paramsA.y}), " +
                                "A中心=($finalACenterX, $finalACenterY), B中心=(${state.ballBCenterX}, ${state.ballBCenterY})")
                }
            })
            start()
        }
    }
    
    /**
     * 取消动画
     */
    fun cancelAnimation() {
        resetAnimator?.cancel()
        resetAnimator = null
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        cancelAnimation()
    }
}

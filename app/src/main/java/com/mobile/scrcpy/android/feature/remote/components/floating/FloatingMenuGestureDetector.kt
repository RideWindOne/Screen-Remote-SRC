package com.mobile.scrcpy.android.feature.remote.components.floating

import android.content.Context
import android.util.Log
import android.view.HapticFeedbackConstants
import com.mobile.scrcpy.android.core.common.LogTags
import kotlin.math.hypot

/**
 * 手势识别器
 * 负责识别点击、拖动、长按等手势类型
 */
internal class FloatingMenuGestureDetector(
    private val context: Context,
    private val state: FloatingMenuGestureState,
    private val hapticEnabled: Boolean
) {
    
    private val density = context.resources.displayMetrics.density
    private val moveSlopPx = MOVE_SLOP_DP * density
    private val longPressCancelSlopPx = LONG_PRESS_CANCEL_SLOP_DP * density
    private val directionThresholdPx = DIRECTION_THRESHOLD_DP * density
    
    /**
     * 检测是否为点击手势
     */
    fun isClick(duration: Long): Boolean {
        return !state.hasMoved && duration < CLICK_TIME_MS
    }
    
    /**
     * 检测是否应该进入长按模式
     * @return true 表示进入长按模式，false 表示普通拖动
     */
    fun checkLongPressTransition(distance: Float, duration: Long): Boolean? {
        // 一旦检测到移动超过小阈值，判断是否进入长按模式
        if (distance > longPressCancelSlopPx && !state.hasMoved) {
            state.cancelLongPressCallbacks()
            
            return if (state.canEnterLongPress) {
                // 300ms内没有移动，现在开始移动 → 长按模式
                state.isLongPress = true
                Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 长按触发！按住300ms后开始移动，切换到转圈模式")
                true
            } else {
                // 300ms内已经移动了 → 普通拖动
                state.isLongPress = false
                Log.d(LogTags.FLOATING_CONTROLLER, "📱 普通拖动模式（按住${duration}ms后开始移动，距离=${distance.toInt()}px）")
                false
            }
        }
        return null
    }
    
    /**
     * 检测是否超过移动阈值
     */
    fun checkMovementThreshold(dx: Float, dy: Float): Boolean {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance > moveSlopPx) {
            state.hasMoved = true
            return true
        }
        return false
    }
    
    /**
     * 检测方向：基于 X 形划分（45°线作为分界）
     *
     * 区域划分（以45°线为边界的扇形区域）：
     * - 上（UP）：左上到中心到右上，即 -135° ~ -45°
     * - 右（RIGHT）：右上到中心到右下，即 -45° ~ 45°
     * - 下（DOWN）：右下到中心到左下，即 45° ~ 135°
     * - 左（LEFT）：左下到中心到左上，即 135° ~ -135°（跨越±180°）
     *
     * @param dx X方向偏移（相对于B球中心，向右为正）
     * @param dy Y方向偏移（相对于B球中心，向下为正）
     * @return 识别的方向
     */
    fun detectDirection(dx: Float, dy: Float): FloatingMenuGestureState.Direction? {
        if (dx == 0f && dy == 0f) return null
        
        // 计算角度（-180° ~ 180°）
        val angleRad = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
        val angleDeg = Math.toDegrees(angleRad)
        
        // 以45°线为边界划分四个扇形区域
        return when {
            angleDeg >= -45 && angleDeg < 45 -> FloatingMenuGestureState.Direction.RIGHT
            angleDeg in 45.0..<135.0 -> FloatingMenuGestureState.Direction.DOWN
            angleDeg >= 135 || angleDeg < -135 -> FloatingMenuGestureState.Direction.LEFT
            else -> FloatingMenuGestureState.Direction.UP
        }
    }
    
    /**
     * 处理扇形区域触感反馈
     * @return true 表示触发了触感
     */
    fun handleDirectionHaptic(dx: Float, dy: Float, distance: Float): Boolean {
        if (!hapticEnabled || distance <= directionThresholdPx) {
            // 距离太近，重置扇形触感状态
            if (state.lastHapticDirection != null) {
                state.lastHapticDirection = null
                state.directionEnterTime = 0L
                state.hasTriggeredHapticInCurrentDirection = false
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, 
                    "↩️ 回到圆心附近（距离=${distance.toInt()}px < ${directionThresholdPx.toInt()}px），重置扇形触感状态")
            }
            return false
        }
        
        val direction = detectDirection(dx, dy) ?: return false
        
        // 切换到不同的扇形区域
        if (direction != state.lastHapticDirection) {
            state.directionEnterTime = System.currentTimeMillis()
            state.lastHapticDirection = direction
            state.hasTriggeredHapticInCurrentDirection = false
            
            val angleRad = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
            val angleDeg = Math.toDegrees(angleRad).toInt()
            Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                "🎯 进入扇形区域: $direction → ${direction.actionName} (dx=${dx.toInt()}, dy=${dy.toInt()}, 角度=${angleDeg}°, 距离=${distance.toInt()}px)")
            return false
        }
        
        // 在同一扇形区域内，检查是否需要触发延迟触感
        if (!state.hasTriggeredHapticInCurrentDirection) {
            val timeInDirection = System.currentTimeMillis() - state.directionEnterTime
            if (timeInDirection >= DIRECTION_HAPTIC_DELAY_MS) {
                performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                state.hasTriggeredHapticInCurrentDirection = true
                Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                    "🔔 扇形区域触感触发: $direction → ${direction.actionName} (停留${timeInDirection}ms)")
                return true
            }
        }
        
        // 更新检测到的方向
        if (direction != state.detectedDirection) {
            state.detectedDirection = direction
        }
        
        return false
    }
    
    /**
     * 获取最终方向（松手时调用）
     */
    fun getFinalDirection(dx: Float, dy: Float): FloatingMenuGestureState.Direction? {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        return if (distance > directionThresholdPx) {
            detectDirection(dx, dy)
        } else {
            null
        }
    }
}

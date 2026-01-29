package com.mobile.scrcpy.android.feature.remote.components.floating

import android.os.Handler
import android.os.Looper

/**
 * 手势状态管理
 * 负责管理手势识别过程中的所有状态变量
 */
internal class FloatingMenuGestureState {
    // ==================== 手势基础状态 ====================

    var downTime = 0L
    var downRawX = 0f
    var downRawY = 0f
    var lastRawX = 0f
    var lastRawY = 0f
    var hasMoved = false
    var isLongPress = false
    var canEnterLongPress = false

    // ==================== 长按检测 ====================

    var longPressHandler: Handler? = null
    var longPressRunnable: Runnable? = null
    var reservedFunctionHandler: Handler? = null
    var reservedFunctionRunnable: Runnable? = null

    // ==================== B 球位置（长按转圈中心） ====================

    var ballBCenterX = 0f
    var ballBCenterY = 0f

    // ==================== 角度记录（转圈功能） ====================

    var lastAngle: Double? = null

    // ==================== 按下偏移量（保持跟手） ====================

    var downOffsetX = 0f
    var downOffsetY = 0f

    // ==================== 贴边状态 ====================

    var isSnappedToEdge = false
    var snappedEdge: Edge? = null

    enum class Edge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
    }

    // ==================== 边缘触感状态 ====================

    var hasTriggeredEdgeHaptic = false

    // ==================== 方向识别 ====================

    var detectedDirection: Direction? = null
    var directionLocked = false // 已废弃，保留兼容

    enum class Direction(
        val actionName: String,
    ) {
        UP("桌面"),
        DOWN("通知栏"),
        LEFT("返回"),
        RIGHT("后台任务"),
    }

    // ==================== 扇形区域触感状态 ====================

    var lastHapticDirection: Direction? = null
    var directionEnterTime = 0L
    var hasTriggeredHapticInCurrentDirection = false

    // ==================== 菜单状态 ====================

    var isMenuShown = false

    /**
     * 重置手势状态（松开或取消时调用）
     */
    fun reset() {
        hasMoved = false
        isLongPress = false
        canEnterLongPress = false
        lastAngle = null
        downOffsetX = 0f
        downOffsetY = 0f
        detectedDirection = null
        directionLocked = false
        hasTriggeredEdgeHaptic = false
        lastHapticDirection = null
        directionEnterTime = 0L
        hasTriggeredHapticInCurrentDirection = false
    }

    /**
     * 取消长按延迟任务
     */
    fun cancelLongPressCallbacks() {
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
        reservedFunctionRunnable?.let { reservedFunctionHandler?.removeCallbacks(it) }
    }

    /**
     * 初始化 Handler
     */
    fun initHandlers() {
        longPressHandler = Handler(Looper.getMainLooper())
        reservedFunctionHandler = Handler(Looper.getMainLooper())
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        cancelLongPressCallbacks()
        longPressHandler = null
        reservedFunctionHandler = null
        longPressRunnable = null
        reservedFunctionRunnable = null
    }
}

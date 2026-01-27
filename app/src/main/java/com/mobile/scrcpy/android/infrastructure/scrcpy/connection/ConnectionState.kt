package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnecting : ConnectionState()
    object Reconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 触摸动作（对应 Android MotionEvent）
 */
object TouchAction {
    const val ACTION_DOWN = 0           // 第一个手指按下
    const val ACTION_UP = 1             // 最后一个手指抬起
    const val ACTION_MOVE = 2           // 手指移动
    const val ACTION_CANCEL = 3         // 取消
    const val ACTION_POINTER_DOWN = 5   // 额外手指按下（多指触摸）
    const val ACTION_POINTER_UP = 6     // 额外手指抬起（多指触摸）
}

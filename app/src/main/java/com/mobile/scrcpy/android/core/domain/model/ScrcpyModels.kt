package com.mobile.scrcpy.android.core.domain.model

/**
 * Scrcpy 连接状态
 */
enum class ScrcpyStatus {
    DISCONNECTED,       // 断开
    CONNECTING,         // 连接中
    CONNECTED,          // 已连接
    CONNECTION_FAILED,  // 连接失败
}

/**
 * Scrcpy 事件类型（对应 SDL 自定义事件）
 */
enum class ScrcpyEventType(val code: Int) {
    DEVICE_DISCONNECTED(0),         // 设备断开
    SERVER_CONNECTION_FAILED(1),    // 服务器连接失败
    SERVER_CONNECTED(2),            // 服务器连接成功
    DEMUXER_ERROR(3),              // 解复用器错误
    CONTROLLER_ERROR(4),           // 控制器错误
    RECORDER_ERROR(5),             // 录制器错误
    ;

    companion object {
        fun fromCode(code: Int): ScrcpyEventType? = entries.find { it.code == code }
    }
}

/**
 * Scrcpy 状态变化事件
 */
data class ScrcpyStatusEvent(
    val status: ScrcpyStatus,
    val deviceId: String? = null,
    val errorMessage: String? = null,
)

/**
 * Scrcpy 错误事件
 */
data class ScrcpyErrorEvent(
    val eventType: ScrcpyEventType,
    val deviceId: String? = null,
    val errorMessage: String? = null,
)

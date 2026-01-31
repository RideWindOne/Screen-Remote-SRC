package com.mobile.scrcpy.android.core.i18n

/**
 * 事件总线相关文本
 */
object EventBusTexts {
    // 状态描述
    val STATE_CONNECTED = TextPair("已连接", "Connected")
    val STATE_DISCONNECTED = TextPair("未连接", "Disconnected")
    val STATE_SCREEN_ON = TextPair("亮屏", "Screen On")
    val STATE_SCREEN_OFF = TextPair("息屏", "Screen Off")
    val STATE_LOCKED = TextPair("锁屏", "Locked")
    val STATE_UNLOCKED = TextPair("解锁", "Unlocked")
    val STATE_VIDEO_ACTIVE = TextPair("活跃", "Active")
    val STATE_VIDEO_STALLED = TextPair("停滞", "Stalled")

    // 异常类型
    val EXCEPTION_SOCKET = TextPair("Socket 错误", "Socket Error")
    val EXCEPTION_DECODER = TextPair("解码器错误", "Decoder Error")
    val EXCEPTION_ADB = TextPair("ADB 错误", "ADB Error")
    val EXCEPTION_SERVER = TextPair("Server 错误", "Server Error")
    val EXCEPTION_NETWORK = TextPair("网络错误", "Network Error")
    val EXCEPTION_UNKNOWN = TextPair("未知错误", "Unknown Error")

    // 日志消息
    val LOG_EVENT_BUS_STARTED = TextPair("事件总线已启动", "Event bus started")
    val LOG_EVENT_BUS_STOPPED = TextPair("事件总线已停止", "Event bus stopped")
    val LOG_SCREEN_LOCKED = TextPair("设备锁屏", "Device screen locked")
    val LOG_SCREEN_UNLOCKED = TextPair("设备解锁", "Device screen unlocked")
    val LOG_SCREEN_OFF = TextPair("设备息屏", "Device screen off")
    val LOG_SCREEN_ON = TextPair("设备亮屏", "Device screen on")
    val LOG_CONNECTION_ESTABLISHED = TextPair("连接建立", "Connection established")
    val LOG_CONNECTION_LOST = TextPair("连接丢失", "Connection lost")

    // 异常检测
    val ANOMALY_VIDEO_AFTER_LOCK = TextPair("异常：锁屏后仍有视频输出", "Anomaly: Video output after screen lock")
    val ANOMALY_NO_VIDEO_DATA = TextPair("异常：连接后无视频数据", "Anomaly: No video data after connection")
    val ANOMALY_SOCKET_IDLE = TextPair("异常：Socket 长时间空闲", "Anomaly: Socket idle for too long")

    // 统计摘要
    val SUMMARY_TITLE = TextPair("状态摘要", "State Summary")
    val SUMMARY_CONNECTION = TextPair("连接状态", "Connection")
    val SUMMARY_SCREEN = TextPair("屏幕状态", "Screen")
    val SUMMARY_VIDEO = TextPair("视频", "Video")
    val SUMMARY_AUDIO = TextPair("音频", "Audio")
    val SUMMARY_SERVER_LOG = TextPair("Server 日志", "Server Log")
    val SUMMARY_SOCKET_STATS = TextPair("Socket 统计", "Socket Stats")
    val SUMMARY_RECENT_EXCEPTIONS = TextPair("最近异常", "Recent Exceptions")
    val SUMMARY_FRAMES = TextPair("帧", "frames")
    val SUMMARY_PACKETS = TextPair("包", "packets")
    val SUMMARY_RECEIVED = TextPair("收", "Received")
    val SUMMARY_SENT = TextPair("发", "Sent")
}

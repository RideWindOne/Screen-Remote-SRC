package com.screen.remote.android.core.common.event

/**
 * 监控事件 - Socket 数据
 */

data class SocketDataReceived(
    val deviceId: String,
    val socketType: String,
    val bytesCount: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Socket[$socketType] 接收: ${bytesCount}B"

    override fun needsSampling() = true
}

data class SocketDataSent(
    val deviceId: String,
    val socketType: String,
    val bytesCount: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Socket[$socketType] 发送: ${bytesCount}B"

    override fun needsSampling() = true
}

data class SocketIdle(
    val deviceId: String,
    val socketType: String,
    val idleDurationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Socket[$socketType] 空闲 ${idleDurationMs}ms"
}

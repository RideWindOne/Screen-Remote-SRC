package com.mobile.scrcpy.android.core.common.event

/**
 * 监控事件 - Server 日志
 */

data class ServerLog(
    val deviceId: String,
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Server: $message"
}

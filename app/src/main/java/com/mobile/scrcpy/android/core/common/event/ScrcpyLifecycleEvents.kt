package com.mobile.scrcpy.android.core.common.event

import com.mobile.scrcpy.android.core.domain.model.ScrcpyErrorEvent
import com.mobile.scrcpy.android.core.domain.model.ScrcpyStatusEvent

/**
 * 生命周期事件 - 连接、断开、启动、停止
 */

/**
 * 退出事件
 */
object Quit : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "应用退出"
}

/**
 * 服务器连接成功
 */
object ServerConnected : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "服务器连接成功"
}

/**
 * 服务器连接失败
 */
object ServerConnectionFailed : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "服务器连接失败"
}

/**
 * 设备断开连接
 */
object DeviceDisconnected : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "设备断开连接"
}

/**
 * USB 设备断开
 */
object UsbDeviceDisconnected : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "USB 设备断开"
}

/**
 * 连接建立
 */
data class ConnectionEstablished(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "[$deviceId] 连接建立"
}

/**
 * 连接丢失
 */
data class ConnectionLost(
    val deviceId: String,
    val reason: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "[$deviceId] 连接丢失: $reason"
}

/**
 * 状态变化事件（从 Native 层触发）
 */
data class StatusChanged(
    val event: ScrcpyStatusEvent,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "状态变化: ${event.status}"
}

/**
 * 错误事件（从 Native 层触发）
 */
data class ScrcpyError(
    val event: ScrcpyErrorEvent,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "错误: ${event.errorMessage}"
}

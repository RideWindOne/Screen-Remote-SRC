package com.screen.remote.android.core.common.event

/**
 * 监控事件 - 设备状态与异常
 */

data class DeviceScreenLocked(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备锁屏"
}

data class DeviceScreenUnlocked(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备解锁"
}

data class DeviceScreenOff(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备息屏"
}

data class DeviceScreenOn(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备亮屏"
}

data class MonitorException(
    val deviceId: String,
    val type: String,
    val message: String,
    val throwable: Throwable? = null,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 异常[$type]: $message"
}

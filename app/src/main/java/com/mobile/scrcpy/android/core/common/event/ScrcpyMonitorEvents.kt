package com.mobile.scrcpy.android.core.common.event

/**
 * 监控事件 - ADB 操作、Server 日志、Socket 数据、设备状态
 */

// ============================================================
// ADB 操作
// ============================================================

/**
 * Shell 命令执行成功
 */
data class ShellCommandExecuted(
    val deviceId: String,
    val command: String,
    val output: String,
    val durationMs: Long,
    val success: Boolean,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Shell 执行: $command (${durationMs}ms)"
}

/**
 * Shell 命令执行失败
 */
data class ShellCommandFailed(
    val deviceId: String,
    val command: String,
    val error: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Shell 失败: $command - $error (${durationMs}ms)"
}

/**
 * Forward 设置
 */
data class ForwardSetup(
    val deviceId: String,
    val localPort: Int,
    val remoteSocket: String,
    val durationMs: Long,
    val success: Boolean,
    val error: String? = null,
) : ScrcpyEvent() {
    override fun getLogLevel() = if (success) LogLevel.INFO else LogLevel.ERROR

    override fun getCategory() = Category.MONITOR

    override fun getDescription() =
        "[$deviceId] Forward ${if (success) "成功" else "失败"}: $localPort -> $remoteSocket (${durationMs}ms)"
}

/**
 * Forward 移除
 */
data class ForwardRemoved(
    val deviceId: String,
    val localPort: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Forward 移除: $localPort"
}

/**
 * 文件推送成功
 */
data class FilePushSuccess(
    val deviceId: String,
    val localPath: String,
    val remotePath: String,
    val fileSize: Long,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() =
        "[$deviceId] 文件推送成功: $localPath -> $remotePath (${fileSize / 1024}KB, ${durationMs}ms)"
}

/**
 * 文件推送失败
 */
data class FilePushFailed(
    val deviceId: String,
    val localPath: String,
    val remotePath: String,
    val error: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 文件推送失败: $localPath -> $remotePath - $error (${durationMs}ms)"
}

/**
 * ADB 授权验证中
 */
data class AdbVerifying(
    val deviceId: String,
    val deviceName: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] ADB 授权验证中: $deviceName"
}

/**
 * ADB 授权验证成功
 */
data class AdbVerifySuccess(
    val deviceId: String,
    val deviceName: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] ADB 授权验证成功: $deviceName (${durationMs}ms)"
}

/**
 * ADB 授权验证失败
 */
data class AdbVerifyFailed(
    val deviceId: String,
    val error: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] ADB 授权验证失败: $error (${durationMs}ms)"
}

// ============================================================
// Server 日志
// ============================================================

/**
 * Server 日志
 */
data class ServerLog(
    val deviceId: String,
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Server: $message"
}

// ============================================================
// Socket 数据
// ============================================================

/**
 * Socket 数据接收
 */
data class SocketDataReceived(
    val deviceId: String,
    val socketType: String, // "video", "audio", "control"
    val bytesCount: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Socket[$socketType] 接收: ${bytesCount}B"

    override fun needsSampling() = true
}

/**
 * Socket 数据发送
 */
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

/**
 * Socket 空闲
 */
data class SocketIdle(
    val deviceId: String,
    val socketType: String,
    val idleDurationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] Socket[$socketType] 空闲 ${idleDurationMs}ms"
}

// ============================================================
// 设备状态
// ============================================================

/**
 * 设备锁屏
 */
data class DeviceScreenLocked(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备锁屏"
}

/**
 * 设备解锁
 */
data class DeviceScreenUnlocked(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备解锁"
}

/**
 * 设备息屏
 */
data class DeviceScreenOff(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备息屏"
}

/**
 * 设备亮屏
 */
data class DeviceScreenOn(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 设备亮屏"
}

/**
 * 监控异常
 */
data class MonitorException(
    val deviceId: String,
    val type: String, // "socket", "decoder", "adb", "server", "network"
    val message: String,
    val throwable: Throwable? = null,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 异常[$type]: $message"
}

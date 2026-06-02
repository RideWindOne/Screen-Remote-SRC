package com.screen.remote.android.core.common.event

data class DeviceMonitorState(
    val deviceId: String,
    var isConnected: Boolean = false,
    var connectionTime: Long = 0,
    var disconnectionTime: Long = 0,
    var disconnectionReason: String? = null,
    var isScreenOn: Boolean = true,
    var isScreenLocked: Boolean = false,
    var screenOnTime: Long = 0,
    var screenOffTime: Long = 0,
    var screenLockTime: Long = 0,
    var screenUnlockTime: Long = 0,
    var videoFrameCount: Long = 0,
    var lastVideoFrameTime: Long = 0,
    var isVideoActive: Boolean = false,
    var videoStallCount: Int = 0,
    var audioFrameCount: Long = 0,
    var lastAudioFrameTime: Long = 0,
    var isAudioActive: Boolean = false,
    var audioStallCount: Int = 0,
    var serverLogCount: Long = 0,
    var lastServerLog: String? = null,
    var lastServerLogTime: Long = 0,
    var shellCommandCount: Long = 0,
    var shellCommandFailCount: Long = 0,
    var lastShellCommand: String? = null,
    var lastShellCommandTime: Long = 0,
    var forwardSetupCount: Long = 0,
    var forwardSetupFailCount: Long = 0,
    var forwardRemoveCount: Long = 0,
    var filePushCount: Long = 0,
    var filePushFailCount: Long = 0,
    var filePushTotalBytes: Long = 0,
    var lastFilePushPath: String? = null,
    var lastFilePushTime: Long = 0,
    var adbVerifyCount: Long = 0,
    var adbVerifyFailCount: Long = 0,
    var lastAdbVerifyTime: Long = 0,
    val socketStats: MutableMap<String, SocketStats> = mutableMapOf(),
    val recentExceptions: MutableList<ExceptionRecord> = mutableListOf(),
)

data class SocketStats(
    var bytesReceived: Long = 0,
    var bytesSent: Long = 0,
    var packetsReceived: Long = 0,
    var packetsSent: Long = 0,
    var lastActivityTime: Long = 0,
    var idleCount: Int = 0,
)

data class ExceptionRecord(
    val type: String,
    val message: String,
)

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

data class ForwardRemoved(
    val deviceId: String,
    val localPort: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Forward 移除: $localPort"
}

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

data class AdbVerifying(
    val deviceId: String,
    val deviceName: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] ADB 授权验证中: $deviceName"
}

data class AdbVerifySuccess(
    val deviceId: String,
    val deviceName: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] ADB 授权验证成功: $deviceName (${durationMs}ms)"
}

data class AdbVerifyFailed(
    val deviceId: String,
    val error: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] ADB 授权验证失败: $error (${durationMs}ms)"
}

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

data class ServerLog(
    val deviceId: String,
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Server: $message"
}

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

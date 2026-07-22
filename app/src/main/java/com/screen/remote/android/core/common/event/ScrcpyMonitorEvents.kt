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
    override fun getDescription() =
        "[$deviceId] Shell ${if (success) "succeeded" else "failed"}: $command " +
            "(${durationMs}ms, outputChars=${output.length})"
}

data class ShellCommandFailed(
    val deviceId: String,
    val command: String,
    val error: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Shell failed: $command - $error (${durationMs}ms)"
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
        "[$deviceId] Forward ${if (success) "succeeded" else "failed"}: $localPort -> $remoteSocket " +
            "(${durationMs}ms)${error?.let { ", error=$it" }.orEmpty()}"
}

data class ForwardRemoved(
    val deviceId: String,
    val localPort: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Forward removed: $localPort"
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
        "[$deviceId] File push succeeded: $localPath -> $remotePath (${fileSize / 1024}KB, ${durationMs}ms)"
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
    override fun getDescription() = "[$deviceId] File push failed: $localPath -> $remotePath - $error (${durationMs}ms)"
}

data class AdbVerifying(
    val deviceId: String,
    val deviceName: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Verifying ADB authorization: $deviceName"
}

data class AdbVerifySuccess(
    val deviceId: String,
    val deviceName: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] ADB authorization verified: $deviceName (${durationMs}ms)"
}

data class AdbVerifyFailed(
    val deviceId: String,
    val error: String,
    val durationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] ADB authorization verification failed: $error (${durationMs}ms)"
}

data class DeviceScreenLocked(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Device screen locked"
}

data class DeviceScreenUnlocked(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Device screen unlocked"
}

data class DeviceScreenOff(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Device screen turned off"
}

data class DeviceScreenOn(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Device screen turned on"
}

data class MonitorException(
    val deviceId: String,
    val type: String,
    val message: String,
    val throwable: Throwable? = null,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR
    override fun getCategory() = Category.MONITOR
    override fun getDescription() =
        "[$deviceId] Exception[$type]: $message" +
            throwable?.let { " (${it.javaClass.simpleName}: ${it.message ?: "no message"})" }.orEmpty()
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
    override fun getDescription() = "[$deviceId] Socket[$socketType] received: ${bytesCount}B"
    override fun needsSampling() = true
}

data class SocketDataSent(
    val deviceId: String,
    val socketType: String,
    val bytesCount: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Socket[$socketType] sent: ${bytesCount}B"
    override fun needsSampling() = true
}

data class SocketIdle(
    val deviceId: String,
    val socketType: String,
    val idleDurationMs: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN
    override fun getCategory() = Category.MONITOR
    override fun getDescription() = "[$deviceId] Socket[$socketType] idle for ${idleDurationMs}ms"
}

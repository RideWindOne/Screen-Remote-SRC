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


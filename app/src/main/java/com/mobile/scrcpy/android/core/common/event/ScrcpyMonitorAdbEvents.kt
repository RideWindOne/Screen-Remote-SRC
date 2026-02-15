package com.mobile.scrcpy.android.core.common.event

/**
 * 监控事件 - ADB 操作
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

package com.mobile.scrcpy.android.core.common.event

/**
 * 设备监控状态
 * 
 * 存储单个设备的运行时监控数据
 * 由 ScrcpyEventBus 管理，通过 deviceId 区分不同设备
 */
data class DeviceMonitorState(
    val deviceId: String,
    // 连接状态
    var isConnected: Boolean = false,
    var connectionTime: Long = 0,
    var disconnectionTime: Long = 0,
    var disconnectionReason: String? = null,
    // 设备状态
    var isScreenOn: Boolean = true,
    var isScreenLocked: Boolean = false,
    var screenOnTime: Long = 0,
    var screenOffTime: Long = 0,
    var screenLockTime: Long = 0,
    var screenUnlockTime: Long = 0,
    // 视频状态
    var videoFrameCount: Long = 0,
    var lastVideoFrameTime: Long = 0,
    var isVideoActive: Boolean = false,
    var videoStallCount: Int = 0,
    // 音频状态
    var audioFrameCount: Long = 0,
    var lastAudioFrameTime: Long = 0,
    var isAudioActive: Boolean = false,
    var audioStallCount: Int = 0,
    // Server 日志
    var serverLogCount: Long = 0,
    var lastServerLog: String? = null,
    var lastServerLogTime: Long = 0,
    // Shell 命令统计
    var shellCommandCount: Long = 0,
    var shellCommandFailCount: Long = 0,
    var lastShellCommand: String? = null,
    var lastShellCommandTime: Long = 0,
    // Forward 统计
    var forwardSetupCount: Long = 0,
    var forwardSetupFailCount: Long = 0,
    var forwardRemoveCount: Long = 0,
    // 文件推送统计
    var filePushCount: Long = 0,
    var filePushFailCount: Long = 0,
    var filePushTotalBytes: Long = 0,
    var lastFilePushPath: String? = null,
    var lastFilePushTime: Long = 0,
    // ADB 授权统计
    var adbVerifyCount: Long = 0,
    var adbVerifyFailCount: Long = 0,
    var lastAdbVerifyTime: Long = 0,
    // Socket 统计
    val socketStats: MutableMap<String, SocketStats> = mutableMapOf(),
    // 异常记录
    val recentExceptions: MutableList<ExceptionRecord> = mutableListOf(),
)

/**
 * Socket 统计
 */
data class SocketStats(
    var bytesReceived: Long = 0,
    var bytesSent: Long = 0,
    var packetsReceived: Long = 0,
    var packetsSent: Long = 0,
    var lastActivityTime: Long = 0,
    var idleCount: Int = 0,
)

/**
 * 异常记录
 */
data class ExceptionRecord(
    val type: String,
    val message: String,
)

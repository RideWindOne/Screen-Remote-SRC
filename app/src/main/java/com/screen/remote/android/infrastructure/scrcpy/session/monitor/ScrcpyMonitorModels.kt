package com.screen.remote.android.infrastructure.scrcpy.session.monitor

import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketType

/**
 * Scrcpy 监控事件定义
 */
sealed class ScrcpyMonitorEvent {
    // ============ Server 日志 ============
    data class ServerLog(
        val message: String,
    ) : ScrcpyMonitorEvent()

    // ============ Socket 数据 ============
    data class SocketDataReceived(
        val socketType: SocketType,
        val bytesCount: Long,
    ) : ScrcpyMonitorEvent()

    data class SocketDataSent(
        val socketType: SocketType,
        val bytesCount: Long,
    ) : ScrcpyMonitorEvent()

    data class SocketIdle(
        val socketType: SocketType,
        val idleDurationMs: Long,
    ) : ScrcpyMonitorEvent()

    // ============ Codec 数据 ============
    data class VideoFrameDecoded(
        val width: Int,
        val height: Int,
        val pts: Long,
    ) : ScrcpyMonitorEvent()

    data class AudioFrameDecoded(
        val sampleRate: Int,
        val channels: Int,
    ) : ScrcpyMonitorEvent()

    // ============ 设备状态 ============
    data object DeviceScreenLocked : ScrcpyMonitorEvent()

    data object DeviceScreenUnlocked : ScrcpyMonitorEvent()

    data object DeviceScreenOff : ScrcpyMonitorEvent()

    data object DeviceScreenOn : ScrcpyMonitorEvent()

    // ============ 连接状态 ============
    data object ConnectionEstablished : ScrcpyMonitorEvent()

    data class ConnectionLost(
        val reason: String,
    ) : ScrcpyMonitorEvent()

    // ============ 异常 ============
    data class Exception(
        val type: ExceptionType,
        val message: String,
        val throwable: Throwable? = null,
    ) : ScrcpyMonitorEvent()
}

/**
 * 全局 Scrcpy 状态
 */
data class SessionMonitorState(
    // 连接状态
    val isConnected: Boolean = false,
    val connectionTime: Long = 0,
    val disconnectionTime: Long = 0,
    val disconnectionReason: String? = null,
    // 设备状态
    val isScreenOn: Boolean = true,
    val isScreenLocked: Boolean = false,
    val screenOnTime: Long = 0,
    val screenOffTime: Long = 0,
    val screenLockTime: Long = 0,
    val screenUnlockTime: Long = 0,
    // 视频状态
    val videoFrameCount: Long = 0,
    val lastVideoFrameTime: Long = 0,
    val isVideoActive: Boolean = false,
    // 音频状态
    val audioFrameCount: Long = 0,
    val lastAudioFrameTime: Long = 0,
    val isAudioActive: Boolean = false,
    // Server 日志
    val serverLogCount: Long = 0,
    val lastServerLog: String? = null,
    val lastServerLogTime: Long = 0,
    // Socket 统计
    val socketStats: Map<SocketType, SocketStatistics> = emptyMap(),
    // 异常记录
    val recentExceptions: List<ExceptionRecord> = emptyList(),
)

/**
 * Socket 统计信息
 */
data class SocketStatistics(
    var bytesReceived: Long = 0,
    var bytesSent: Long = 0,
    var packetsReceived: Long = 0,
    var packetsSent: Long = 0,
    var lastActivityTime: Long = System.currentTimeMillis(),
    var idleCount: Int = 0,
)

/**
 * 事件统计信息
 */
data class EventStatistics(
    var count: Long = 0,
    var lastTimestamp: Long = 0,
)

/**
 * 异常记录
 */
data class ExceptionRecord(
    val type: ExceptionType,
    val message: String,
)

/**
 * 异常类型
 */
enum class ExceptionType {
    SOCKET_ERROR,
    DECODER_ERROR,
    ADB_ERROR,
    SERVER_ERROR,
    NETWORK_ERROR,
    UNKNOWN,
}

internal fun buildScrcpyMonitorSummary(
    deviceId: String,
    state: SessionMonitorState,
): String =
    buildString {
        appendLine("=== Scrcpy 状态摘要 [$deviceId] ===")
        appendLine("连接状态: ${if (state.isConnected) "已连接" else "未连接"}")
        appendLine("屏幕状态: ${if (state.isScreenOn) "亮屏" else "息屏"} / ${if (state.isScreenLocked) "锁屏" else "解锁"}")
        appendLine("视频: ${state.videoFrameCount} 帧, ${if (state.isVideoActive) "活跃" else "停滞"}")
        appendLine("音频: ${state.audioFrameCount} 帧, ${if (state.isAudioActive) "活跃" else "停滞"}")
        appendLine("Server 日志: ${state.serverLogCount} 条")
        appendLine("Socket 统计:")
        state.socketStats.forEach { (type, stats) ->
            appendLine(
                "  [$type] 收: ${stats.packetsReceived}包/${stats.bytesReceived / 1024}KB, 发: ${stats.packetsSent}包/${stats.bytesSent / 1024}KB",
            )
        }
        if (state.recentExceptions.isNotEmpty()) {
            appendLine("最近异常: ${state.recentExceptions.size} 条")
            state.recentExceptions.takeLast(3).forEach {
                appendLine("  [${it.type}] ${it.message}")
            }
        }
    }

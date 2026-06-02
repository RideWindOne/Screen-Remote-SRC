package com.screen.remote.android.infrastructure.scrcpy.session.monitor

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketType

internal class ScrcpyMonitorStateReducer {
    fun reduce(
        currentState: GlobalScrcpyState,
        event: ScrcpyMonitorEvent,
        now: Long,
    ): GlobalScrcpyState =
        when (event) {
            is ScrcpyMonitorEvent.ServerLog -> {
                currentState.copy(
                    serverLogCount = currentState.serverLogCount + 1,
                    lastServerLog = event.message,
                    lastServerLogTime = now,
                )
            }

            is ScrcpyMonitorEvent.SocketDataReceived -> {
                currentState.copy(
                    socketStats =
                        currentState.socketStats.updated(event.socketType) { stats ->
                            stats.copy(
                                bytesReceived = stats.bytesReceived + event.bytesCount,
                                packetsReceived = stats.packetsReceived + 1,
                                lastActivityTime = now,
                            )
                        },
                )
            }

            is ScrcpyMonitorEvent.SocketDataSent -> {
                currentState.copy(
                    socketStats =
                        currentState.socketStats.updated(event.socketType) { stats ->
                            stats.copy(
                                bytesSent = stats.bytesSent + event.bytesCount,
                                packetsSent = stats.packetsSent + 1,
                                lastActivityTime = now,
                            )
                        },
                )
            }

            is ScrcpyMonitorEvent.SocketIdle -> {
                currentState.copy(
                    socketStats =
                        currentState.socketStats.updated(event.socketType) { stats ->
                            stats.copy(idleCount = stats.idleCount + 1)
                        },
                )
            }

            is ScrcpyMonitorEvent.VideoFrameDecoded -> {
                currentState.copy(
                    videoFrameCount = currentState.videoFrameCount + 1,
                    lastVideoFrameTime = now,
                    isVideoActive = true,
                )
            }

            is ScrcpyMonitorEvent.AudioFrameDecoded -> {
                currentState.copy(
                    audioFrameCount = currentState.audioFrameCount + 1,
                    lastAudioFrameTime = now,
                    isAudioActive = true,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenLocked -> {
                currentState.copy(
                    isScreenLocked = true,
                    screenLockTime = now,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenUnlocked -> {
                currentState.copy(
                    isScreenLocked = false,
                    screenUnlockTime = now,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenOff -> {
                currentState.copy(
                    isScreenOn = false,
                    screenOffTime = now,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenOn -> {
                currentState.copy(
                    isScreenOn = true,
                    screenOnTime = now,
                )
            }

            is ScrcpyMonitorEvent.ConnectionEstablished -> {
                currentState.copy(
                    isConnected = true,
                    connectionTime = now,
                )
            }

            is ScrcpyMonitorEvent.ConnectionLost -> {
                currentState.copy(
                    isConnected = false,
                    disconnectionTime = now,
                    disconnectionReason = event.reason,
                )
            }

            is ScrcpyMonitorEvent.Exception -> {
                currentState.copy(
                    recentExceptions = appendException(currentState.recentExceptions, event.type, event.message),
                )
            }
        }

    private fun Map<SocketType, SocketStatistics>.updated(
        socketType: SocketType,
        transform: (SocketStatistics) -> SocketStatistics,
    ): Map<SocketType, SocketStatistics> {
        val current = get(socketType) ?: SocketStatistics()
        return toMutableMap().apply {
            this[socketType] = transform(current)
        }
    }

    private fun appendException(
        exceptions: List<ExceptionRecord>,
        type: ExceptionType,
        message: String,
    ): List<ExceptionRecord> {
        val newExceptions = exceptions.toMutableList()
        newExceptions.add(ExceptionRecord(type = type, message = message))
        while (newExceptions.size > 20) {
            newExceptions.removeAt(0)
        }
        return newExceptions
    }
}

internal class ScrcpyMonitorEventLogger(
    private val deviceId: String,
) {
    fun log(
        event: ScrcpyMonitorEvent,
        state: GlobalScrcpyState,
    ) {
        when (event) {
            is ScrcpyMonitorEvent.ServerLog -> {
                LogManager.d(LogTags.SCRCPY_SERVER, "[$deviceId] ${event.message}")
            }

            is ScrcpyMonitorEvent.SocketDataReceived -> {
                val stats = state.socketStats[event.socketType]
                if (stats != null && stats.packetsReceived % 100 == 0L) {
                    LogManager.d(
                        LogTags.SCRCPY_EVENT_BUS,
                        "[$deviceId] Socket[${event.socketType}] 接收: ${stats.packetsReceived} 包, ${stats.bytesReceived / 1024} KB",
                    )
                }
            }

            is ScrcpyMonitorEvent.SocketDataSent -> {
                val stats = state.socketStats[event.socketType]
                if (stats != null && stats.packetsSent % 100 == 0L) {
                    LogManager.d(
                        LogTags.SCRCPY_EVENT_BUS,
                        "[$deviceId] Socket[${event.socketType}] 发送: ${stats.packetsSent} 包, ${stats.bytesSent / 1024} KB",
                    )
                }
            }

            is ScrcpyMonitorEvent.SocketIdle -> {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] Socket[${event.socketType}] 空闲超过 ${event.idleDurationMs}ms",
                )
            }

            is ScrcpyMonitorEvent.VideoFrameDecoded -> {
                val count = state.videoFrameCount
                if (count % 100 == 0L) {
                    LogManager.d(
                        LogTags.VIDEO_DECODER,
                        "[$deviceId] 视频帧: $count, 分辨率: ${event.width}x${event.height}",
                    )
                }
            }

            is ScrcpyMonitorEvent.AudioFrameDecoded -> {
                val count = state.audioFrameCount
                if (count % 100 == 0L) {
                    LogManager.d(LogTags.AUDIO_DECODER, "[$deviceId] 音频帧: $count")
                }
            }

            is ScrcpyMonitorEvent.DeviceScreenLocked -> {
                LogManager.i(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] 🔒 设备锁屏")
            }

            is ScrcpyMonitorEvent.DeviceScreenUnlocked -> {
                LogManager.i(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] 🔓 设备解锁")
            }

            is ScrcpyMonitorEvent.DeviceScreenOff -> {
                LogManager.i(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] 📴 设备息屏")
            }

            is ScrcpyMonitorEvent.DeviceScreenOn -> {
                LogManager.i(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] 📱 设备亮屏")
            }

            is ScrcpyMonitorEvent.ConnectionEstablished -> {
                LogManager.i(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] ✅ 连接建立")
            }

            is ScrcpyMonitorEvent.ConnectionLost -> {
                LogManager.w(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] ❌ 连接丢失: ${event.reason}")
            }

            is ScrcpyMonitorEvent.Exception -> {
                LogManager.e(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] ⚠️ 异常[${event.type}]: ${event.message}",
                )
            }
        }
    }
}

internal class ScrcpyMonitorAnomalyDetector(
    private val deviceId: String,
) {
    fun detect(
        state: GlobalScrcpyState,
        now: Long,
    ) {
        if (state.isScreenLocked && state.isVideoActive) {
            val timeSinceLock = now - state.screenLockTime
            if (timeSinceLock > 5000) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：锁屏后仍有视频输出（${timeSinceLock}ms）",
                )
            }
        }

        if (state.isConnected) {
            val timeSinceConnection = now - state.connectionTime
            val timeSinceLastVideo = now - state.lastVideoFrameTime

            if (timeSinceConnection > 10000 && timeSinceLastVideo > 10000 && state.videoFrameCount == 0L) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：连接后 ${timeSinceConnection}ms 无视频数据",
                )
            }
        }

        state.socketStats.forEach { (type, stats) ->
            val idleTime = now - stats.lastActivityTime
            if (idleTime > 30000 && state.isConnected) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：Socket[$type] 空闲 ${idleTime}ms",
                )
            }
        }
    }
}

package com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

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

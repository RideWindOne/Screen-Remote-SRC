package com.screen.remote.android.infrastructure.scrcpy.session.monitor

internal fun buildScrcpyMonitorSummary(
    deviceId: String,
    state: GlobalScrcpyState,
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

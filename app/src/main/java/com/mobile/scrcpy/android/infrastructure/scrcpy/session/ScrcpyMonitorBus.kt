package com.mobile.scrcpy.android.infrastructure.scrcpy.session

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Scrcpy 监控总线
 *
 * 整合所有事件源的中央调度器：
 * 1. Scrcpy Server 日志输出
 * 2. Socket 数据变化
 * 3. Codec 数据变化
 * 4. 锁屏状态
 * 5. 连接状态
 * 6. 异常处理
 */
class ScrcpyMonitorBus(
    private val deviceId: String,
) {
    // 事件通道
    private val eventChannel = Channel<ScrcpyMonitorEvent>(Channel.UNLIMITED)

    // 全局状态
    private val _globalState = MutableStateFlow(GlobalScrcpyState())
    val globalState: StateFlow<GlobalScrcpyState> = _globalState.asStateFlow()

    // 监控任务
    private var monitorJob: Job? = null

    // 事件统计
    private val eventStats = mutableMapOf<String, EventStatistics>()

    /**
     * 启动事件总线
     */
    fun start() {
        if (monitorJob?.isActive == true) {
            LogManager.w(LogTags.SCRCPY_EVENT_BUS, "事件总线已在运行: $deviceId")
            return
        }

        monitorJob =
            CoroutineScope(Dispatchers.IO).launch {
                for (event in eventChannel) {
                    if (!isActive) break

                    try {
                        handleEvent(event)
                    } catch (e: Exception) {
                        LogManager.e(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] 处理事件异常: ${e.message}", e)
                    }
                }
            }
    }

    /**
     * 停止事件总线
     */
    fun stop() {
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, "[$deviceId] 停止事件总线")
        monitorJob?.cancel()
        monitorJob = null
        eventChannel.close()
        eventStats.clear()
        _globalState.value = GlobalScrcpyState()
    }

    /**
     * 推送事件
     */
    fun pushEvent(event: ScrcpyMonitorEvent) {
        eventChannel.trySend(event)
    }

    /**
     * 处理事件
     */
    private fun handleEvent(event: ScrcpyMonitorEvent) {
        // 更新统计
        updateStatistics(event)

        // 更新全局状态
        updateGlobalState(event)

        // 输出调试日志
        logEvent(event)

        // 检测异常情况
        detectAnomalies()
    }

    /**
     * 更新统计信息
     */
    private fun updateStatistics(event: ScrcpyMonitorEvent) {
        val eventType = event::class.simpleName ?: "Unknown"
        val stats = eventStats.getOrPut(eventType) { EventStatistics() }
        stats.count++
        stats.lastTimestamp = System.currentTimeMillis()
    }

    /**
     * 更新全局状态
     */
    private fun updateGlobalState(event: ScrcpyMonitorEvent) {
        val currentState = _globalState.value

        val newState =
            when (event) {
                // Server 日志
                is ScrcpyMonitorEvent.ServerLog -> {
                    currentState.copy(
                        serverLogCount = currentState.serverLogCount + 1,
                        lastServerLog = event.message,
                        lastServerLogTime = System.currentTimeMillis(),
                    )
                }

                // Socket 数据
                is ScrcpyMonitorEvent.SocketDataReceived -> {
                    val newSocketStats =
                        currentState.socketStats.toMutableMap().apply {
                            val stats = getOrPut(event.socketType) { SocketStatistics() }
                            stats.bytesReceived += event.bytesCount
                            stats.packetsReceived++
                            stats.lastActivityTime = System.currentTimeMillis()
                        }
                    currentState.copy(socketStats = newSocketStats)
                }

                is ScrcpyMonitorEvent.SocketDataSent -> {
                    val newSocketStats =
                        currentState.socketStats.toMutableMap().apply {
                            val stats = getOrPut(event.socketType) { SocketStatistics() }
                            stats.bytesSent += event.bytesCount
                            stats.packetsSent++
                            stats.lastActivityTime = System.currentTimeMillis()
                        }
                    currentState.copy(socketStats = newSocketStats)
                }

                is ScrcpyMonitorEvent.SocketIdle -> {
                    val newSocketStats =
                        currentState.socketStats.toMutableMap().apply {
                            val stats = getOrPut(event.socketType) { SocketStatistics() }
                            stats.idleCount++
                        }
                    currentState.copy(socketStats = newSocketStats)
                }

                // Codec 数据
                is ScrcpyMonitorEvent.VideoFrameDecoded -> {
                    currentState.copy(
                        videoFrameCount = currentState.videoFrameCount + 1,
                        lastVideoFrameTime = System.currentTimeMillis(),
                        isVideoActive = true,
                    )
                }

                is ScrcpyMonitorEvent.AudioFrameDecoded -> {
                    currentState.copy(
                        audioFrameCount = currentState.audioFrameCount + 1,
                        lastAudioFrameTime = System.currentTimeMillis(),
                        isAudioActive = true,
                    )
                }

                is ScrcpyMonitorEvent.VideoDecoderStalled -> {
                    currentState.copy(
                        isVideoActive = false,
                        videoStallCount = currentState.videoStallCount + 1,
                    )
                }

                is ScrcpyMonitorEvent.AudioDecoderStalled -> {
                    currentState.copy(
                        isAudioActive = false,
                        audioStallCount = currentState.audioStallCount + 1,
                    )
                }

                // 设备状态
                is ScrcpyMonitorEvent.DeviceScreenLocked -> {
                    currentState.copy(
                        isScreenLocked = true,
                        screenLockTime = System.currentTimeMillis(),
                    )
                }

                is ScrcpyMonitorEvent.DeviceScreenUnlocked -> {
                    currentState.copy(
                        isScreenLocked = false,
                        screenUnlockTime = System.currentTimeMillis(),
                    )
                }

                is ScrcpyMonitorEvent.DeviceScreenOff -> {
                    currentState.copy(
                        isScreenOn = false,
                        screenOffTime = System.currentTimeMillis(),
                    )
                }

                is ScrcpyMonitorEvent.DeviceScreenOn -> {
                    currentState.copy(
                        isScreenOn = true,
                        screenOnTime = System.currentTimeMillis(),
                    )
                }

                // 连接状态
                is ScrcpyMonitorEvent.ConnectionEstablished -> {
                    currentState.copy(
                        isConnected = true,
                        connectionTime = System.currentTimeMillis(),
                    )
                }

                is ScrcpyMonitorEvent.ConnectionLost -> {
                    currentState.copy(
                        isConnected = false,
                        disconnectionTime = System.currentTimeMillis(),
                        disconnectionReason = event.reason,
                    )
                }

                // 异常
                is ScrcpyMonitorEvent.Exception -> {
                    val newExceptions = currentState.recentExceptions.toMutableList()
                    newExceptions.add(
                        ExceptionRecord(
                            type = event.type,
                            message = event.message,
                        ),
                    )
                    // 只保留最近 20 条
                    if (newExceptions.size > 20) {
                        newExceptions.removeAt(0)
                    }
                    currentState.copy(recentExceptions = newExceptions)
                }

                else -> { // TODO
                    currentState
                }
            }

        _globalState.value = newState
    }

    /**
     * 输出调试日志
     */
    private fun logEvent(event: ScrcpyMonitorEvent) {
        when (event) {
            // Server 日志 - 直接输出
            is ScrcpyMonitorEvent.ServerLog -> {
                LogManager.d(LogTags.SCRCPY_SERVER, "[$deviceId] ${event.message}")
            }

            // Socket 数据 - 采样输出（避免日志过多）
            is ScrcpyMonitorEvent.SocketDataReceived -> {
                val stats = _globalState.value.socketStats[event.socketType]
                if (stats != null && stats.packetsReceived % 100 == 0L) {
                    LogManager.d(
                        LogTags.SCRCPY_EVENT_BUS,
                        "[$deviceId] Socket[${event.socketType}] 接收: ${stats.packetsReceived} 包, ${stats.bytesReceived / 1024} KB",
                    )
                }
            }

            is ScrcpyMonitorEvent.SocketDataSent -> {
                val stats = _globalState.value.socketStats[event.socketType]
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

            // Codec 数据 - 采样输出
            is ScrcpyMonitorEvent.VideoFrameDecoded -> {
                val count = _globalState.value.videoFrameCount
                if (count % 100 == 0L) {
                    LogManager.d(
                        LogTags.VIDEO_DECODER,
                        "[$deviceId] 视频帧: $count, 分辨率: ${event.width}x${event.height}",
                    )
                }
            }

            is ScrcpyMonitorEvent.AudioFrameDecoded -> {
                val count = _globalState.value.audioFrameCount
                if (count % 100 == 0L) {
                    LogManager.d(
                        LogTags.AUDIO_DECODER,
                        "[$deviceId] 音频帧: $count",
                    )
                }
            }

            is ScrcpyMonitorEvent.VideoDecoderStalled -> {
                LogManager.w(
                    LogTags.VIDEO_DECODER,
                    "[$deviceId] 视频解码器停滞: ${event.reason}",
                )
            }

            is ScrcpyMonitorEvent.AudioDecoderStalled -> {
                LogManager.w(
                    LogTags.AUDIO_DECODER,
                    "[$deviceId] 音频解码器停滞: ${event.reason}",
                )
            }

            // 设备状态 - 重要事件
            is ScrcpyMonitorEvent.DeviceScreenLocked -> {
                LogManager.i(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 🔒 设备锁屏",
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenUnlocked -> {
                LogManager.i(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 🔓 设备解锁",
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenOff -> {
                LogManager.i(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 📴 设备息屏",
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenOn -> {
                LogManager.i(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 📱 设备亮屏",
                )
            }

            // 连接状态
            is ScrcpyMonitorEvent.ConnectionEstablished -> {
                LogManager.i(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] ✅ 连接建立",
                )
            }

            is ScrcpyMonitorEvent.ConnectionLost -> {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] ❌ 连接丢失: ${event.reason}",
                )
            }

            // 异常
            is ScrcpyMonitorEvent.Exception -> {
                LogManager.e(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] ⚠️ 异常[${event.type}]: ${event.message}",
                )
            }
        }
    }

    /**
     * 检测异常情况
     */
    private fun detectAnomalies() {
        val state = _globalState.value

        // 检测：锁屏后无视频输出
        if (state.isScreenLocked && state.isVideoActive) {
            val timeSinceLock = System.currentTimeMillis() - state.screenLockTime
            if (timeSinceLock > 5000) { // 锁屏 5 秒后仍有视频
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：锁屏后仍有视频输出（${timeSinceLock}ms）",
                )
            }
        }

        // 检测：连接后长时间无数据
        if (state.isConnected) {
            val timeSinceConnection = System.currentTimeMillis() - state.connectionTime
            val timeSinceLastVideo = System.currentTimeMillis() - state.lastVideoFrameTime

            if (timeSinceConnection > 10000 && timeSinceLastVideo > 10000 && state.videoFrameCount == 0L) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：连接后 ${timeSinceConnection}ms 无视频数据",
                )
            }
        }

        // 检测：Socket 长时间空闲
        state.socketStats.forEach { (type, stats) ->
            val idleTime = System.currentTimeMillis() - stats.lastActivityTime
            if (idleTime > 30000 && state.isConnected) { // 30 秒无活动
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：Socket[$type] 空闲 ${idleTime}ms",
                )
            }
        }
    }

    /**
     * 获取状态摘要（用于调试）
     */
    fun getStateSummary(): String {
        val state = _globalState.value
        return buildString {
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
    }
}

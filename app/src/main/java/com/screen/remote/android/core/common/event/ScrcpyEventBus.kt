package com.screen.remote.android.core.common.event

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyErrorEvent
import com.screen.remote.android.core.domain.model.ScrcpyEventType
import com.screen.remote.android.core.domain.model.ScrcpyStatus
import com.screen.remote.android.core.domain.model.ScrcpyStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scrcpy 事件总线（单例）
 *
 * 会话级事件循环管理器，提供统一的事件推送接口
 *
 * 作用域：连接会话内的全局事件总线，非应用级全局
 * 生命周期：随 Scrcpy 连接会话启动/停止
 * 关系定位：与 ADB 保活服务平级，各自独立管理自己的生命周期
 * 支持多设备：虽然当前只连接一个设备，但架构支持多设备状态管理（通过 deviceId 区分）
 *
 * 使用示例：
 * ```kotlin
 * // 启动事件循环（Application.onCreate）
 * ScrcpyEventBus.start()
 * ScrcpyEventMonitor.start()
 *
 * // 推送事件（任意线程）
 * ScrcpyEventBus.pushEvent(ScrcpyEvent.ServerLog(deviceId, "log"))
 * ScrcpyEventBus.pushEvent(ScrcpyEvent.VideoFrameDecoded(deviceId, w, h, pts))
 *
 * // 查询状态
 * val state = ScrcpyEventBus.getDeviceState(deviceId)
 * val summary = ScrcpyEventBus.getStateSummary(deviceId)
 *
 * // 清理（断开连接时）
 * ScrcpyEventBus.clearDeviceState(deviceId)
 * ```
 */
object ScrcpyEventBus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val eventLoop = ScrcpyEventLoop(scope)

    /**
     * 注册事件处理器
     */
    inline fun <reified T : ScrcpyEvent> on(noinline handler: (T) -> Unit) {
        eventLoop.on(handler)
    }

    /**
     * 推送事件（线程安全）
     */
    fun pushEvent(event: ScrcpyEvent): Boolean = eventLoop.pushEvent(event)

    /**
     * 在主线程执行任务
     */
    fun postToMainThread(task: () -> Unit): Boolean = eventLoop.postToMainThread(task)

    /**
     * 启动事件循环
     */
    fun start() {
        eventLoop.start()
    }

    /**
     * 停止事件循环
     */
    fun stop() {
        eventLoop.stop()
    }

    /**
     * 清理事件总线（断开连接时调用）
     */
    fun cleanup() {
        // 清理所有设备状态
        deviceStates.clear()

        // 停止事件循环
        eventLoop.stop()

        LogManager.d(LogTags.SDL, "Event bus cleared")
    }

    /**
     * 检查事件循环是否运行中
     */
    fun isRunning(): Boolean = eventLoop.isRunning()

    // ============ 监控状态管理 ============

    // 每个设备的监控状态（支持多设备，但当前只用一个）
    private val deviceStates = mutableMapOf<String, DeviceMonitorState>()

    /**
     * 获取设备监控状态
     */
    fun getDeviceState(deviceId: String): DeviceMonitorState =
        deviceStates.getOrPut(deviceId) { DeviceMonitorState(deviceId) }

    /**
     * 清除设备监控状态
     */
    fun clearDeviceState(deviceId: String) {
        deviceStates.remove(deviceId)
    }

    /**
     * 获取状态摘要
     */
    fun getStateSummary(deviceId: String): String {
        val state = getDeviceState(deviceId)
        return buildString {
            appendLine("=== Scrcpy Status Summary [$deviceId] ===")
            appendLine("Connection: ${if (state.isConnected) "connected" else "disconnected"}")
            appendLine("Screen: ${if (state.isScreenOn) "on" else "off"} / ${if (state.isScreenLocked) "locked" else "unlocked"}")
            appendLine("Video: ${state.videoFrameCount} frames, ${if (state.isVideoActive) "active" else "stalled"}")
            appendLine("Audio: ${state.audioFrameCount} frames, ${if (state.isAudioActive) "active" else "stalled"}")
            appendLine("Server logs: ${state.serverLogCount}")
            state.socketStats.forEach { (type, stats) ->
                appendLine(
                    "  [$type] received: ${stats.packetsReceived} packets/${stats.bytesReceived / 1024}KB, sent: ${stats.packetsSent} packets/${stats.bytesSent / 1024}KB",
                )
            }
            if (state.recentExceptions.isNotEmpty()) {
                appendLine("Recent exceptions: ${state.recentExceptions.size}")
            }
        }
    }

    // ============ JNI 回调接口 ============

    /**
     * 从 Native 层接收状态变化事件
     * 由 scrcpy_bridge_jni.cpp 调用
     */
    @JvmStatic
    fun emitStatusFromNative(
        status: Int,
        deviceId: String?,
        errorMessage: String?,
    ) {
        val scrcpyStatus =
            ScrcpyStatus.entries.getOrNull(status) ?: run {
                LogManager.e(LogTags.SCRCPY_EVENT_BUS, "Invalid status code: $status")
                return
            }

        val event =
            ScrcpyStatusEvent(
                status = scrcpyStatus,
                deviceId = deviceId,
                errorMessage = errorMessage,
            )

        LogManager.d(
            LogTags.SCRCPY_EVENT_BUS,
            "Received Native status event: status=$scrcpyStatus, deviceId=$deviceId",
        )

        pushEvent(StatusChanged(event))
    }

    /**
     * 从 Native 层接收错误事件
     * 由 scrcpy_bridge_jni.cpp 调用
     */
    @JvmStatic
    fun emitErrorFromNative(
        eventType: Int,
        deviceId: String?,
        errorMessage: String?,
    ) {
        val scrcpyEventType =
            ScrcpyEventType.fromCode(eventType) ?: run {
                LogManager.e(LogTags.SCRCPY_EVENT_BUS, "Invalid event type code: $eventType")
                return
            }

        val event =
            ScrcpyErrorEvent(
                eventType = scrcpyEventType,
                deviceId = deviceId,
                errorMessage = errorMessage,
            )

        LogManager.d(
            LogTags.SCRCPY_EVENT_BUS,
            "Received Native error event: eventType=$scrcpyEventType, deviceId=$deviceId, message=$errorMessage",
        )

        pushEvent(ScrcpyError(event))
    }
}

class ScrcpyEventLoop(
    private val scope: CoroutineScope,
) {
    private val eventChannel = Channel<ScrcpyEvent>(Channel.UNLIMITED)
    private var loopJob: Job? = null
    private var isRunning = false

    val eventHandlers = mutableMapOf<Class<out ScrcpyEvent>, (ScrcpyEvent) -> Unit>()

    companion object {
        private const val TAG = LogTags.SCRCPY_EVENT_BUS
    }

    inline fun <reified T : ScrcpyEvent> on(noinline handler: (T) -> Unit) {
        eventHandlers[T::class.java] = { event ->
            @Suppress("UNCHECKED_CAST")
            handler(event as T)
        }
    }

    fun pushEvent(event: ScrcpyEvent): Boolean = eventChannel.trySend(event).isSuccess

    fun start() {
        if (isRunning) {
            LogManager.w(TAG, "Event loop already running")
            return
        }

        isRunning = true
        loopJob =
            scope.launch {
                LogManager.d(TAG, "Event loop started")

                try {
                    for (event in eventChannel) {
                        handleEvent(event)

                        if (event is Quit) {
                            LogManager.d(TAG, "Quit event received, stopping loop")
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogManager.e(TAG, "Event loop error", e)
                } finally {
                    isRunning = false
                    LogManager.d(TAG, "Event loop stopped")
                }
            }
    }

    fun stop() {
        if (!isRunning) return

        pushEvent(Quit)
        loopJob?.cancel()
        loopJob = null
        isRunning = false
    }

    private suspend fun handleEvent(event: ScrcpyEvent) {
        ScrcpyEventLogger.logEvent(event)

        if (event is RunOnMainThread) {
            withContext(Dispatchers.Main) {
                try {
                    event.task()
                } catch (e: Exception) {
                    LogManager.e(TAG, "Error running task on main thread", e)
                }
            }
            return
        }

        val handler = eventHandlers[event::class.java]
        if (handler != null) {
            try {
                handler(event)
            } catch (e: Exception) {
                LogManager.e(TAG, "Error handling event: ${event::class.simpleName}", e)
            }
        } else {
            LogManager.v(TAG, "No handler for event: ${event::class.simpleName}")
        }
    }

    fun postToMainThread(task: () -> Unit): Boolean = pushEvent(RunOnMainThread(task))

    fun isRunning(): Boolean = isRunning
}

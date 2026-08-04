package com.screen.remote.android.core.common.event

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
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
     * 清除设备监控状态
     */
    fun clearDeviceState(deviceId: String) {
        deviceStates.remove(deviceId)
    }

    // ============ JNI 回调接口 ============

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

    fun isRunning(): Boolean = isRunning
}

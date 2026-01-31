package com.mobile.scrcpy.android.core.common.event

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyErrorEvent
import com.mobile.scrcpy.android.core.domain.model.ScrcpyEventType
import com.mobile.scrcpy.android.core.domain.model.ScrcpyStatus
import com.mobile.scrcpy.android.core.domain.model.ScrcpyStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

        LogManager.d(LogTags.SDL, "事件总线已清理")
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
            appendLine("=== Scrcpy 状态摘要 [$deviceId] ===")
            appendLine("连接: ${if (state.isConnected) "已连接" else "未连接"}")
            appendLine("屏幕: ${if (state.isScreenOn) "亮屏" else "息屏"} / ${if (state.isScreenLocked) "锁屏" else "解锁"}")
            appendLine("视频: ${state.videoFrameCount} 帧, ${if (state.isVideoActive) "活跃" else "停滞"}")
            appendLine("音频: ${state.audioFrameCount} 帧, ${if (state.isAudioActive) "活跃" else "停滞"}")
            appendLine("Server 日志: ${state.serverLogCount} 条")
            state.socketStats.forEach { (type, stats) ->
                appendLine(
                    "  [$type] 收: ${stats.packetsReceived}包/${stats.bytesReceived / 1024}KB, 发: ${stats.packetsSent}包/${stats.bytesSent / 1024}KB",
                )
            }
            if (state.recentExceptions.isNotEmpty()) {
                appendLine("最近异常: ${state.recentExceptions.size} 条")
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
                LogManager.e(LogTags.SCRCPY_EVENT_BUS, "无效的状态码: $status")
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
            "收到 Native 状态事件: status=$scrcpyStatus, deviceId=$deviceId",
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
                LogManager.e(LogTags.SCRCPY_EVENT_BUS, "无效的事件类型码: $eventType")
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
            "收到 Native 错误事件: eventType=$scrcpyEventType, deviceId=$deviceId, message=$errorMessage",
        )

        pushEvent(ScrcpyError(event))
    }
}

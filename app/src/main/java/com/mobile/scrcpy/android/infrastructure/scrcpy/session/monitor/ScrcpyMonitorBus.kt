package com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionState
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
    private val eventChannel = Channel<ScrcpyMonitorEvent>(Channel.UNLIMITED)
    private val _globalState = MutableStateFlow(GlobalScrcpyState())
    val globalState: StateFlow<GlobalScrcpyState> = _globalState.asStateFlow()
    private var monitorJob: Job? = null
    private val eventStats = mutableMapOf<String, EventStatistics>()
    private val stateReducer = ScrcpyMonitorStateReducer()
    private val eventLogger = ScrcpyMonitorEventLogger(deviceId)
    private val anomalyDetector = ScrcpyMonitorAnomalyDetector(deviceId)

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

    fun consumeSessionEvent(
        event: SessionEvent,
        resultingState: SessionState,
    ) {
        SessionMonitorEventProjector
            .project(event, resultingState)
            .forEach(::pushEvent)
    }

    /**
     * 处理事件
     */
    private fun handleEvent(event: ScrcpyMonitorEvent) {
        updateStatistics(event)
        val now = System.currentTimeMillis()
        val newState = stateReducer.reduce(_globalState.value, event, now)
        _globalState.value = newState
        eventLogger.log(event, newState)
        anomalyDetector.detect(newState, now)
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

    fun getStateSummary(): String {
        return buildScrcpyMonitorSummary(deviceId, _globalState.value)
    }
}

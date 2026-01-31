package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.ScrcpyMonitorBus
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.Session

/**
 * 会话监控逻辑
 *
 * 职责：
 * - 创建和管理监控总线
 * - 初始化监控器
 * - 停止监控器
 *
 * 使用扩展函数模式，避免暴露内部实现细节
 */

/**
 * 创建监控总线
 */
fun Session.createMonitorBus() {
    try {
        monitorBus?.stop()
    } catch (e: Exception) {
        LogManager.w(LogTags.SDL, "停止旧 MonitorBus 失败: ${e.message}")
    }
    monitorBus = ScrcpyMonitorBus(deviceIdentifier).apply { start() }
}

/**
 * 初始化监控器
 */
fun Session.initMonitor(
    stateMachine: ConnectionStateMachine,
    onReconnect: () -> Unit,
) {
    setStateMachineInternal(stateMachine)
    setReconnectCallbackInternal(onReconnect)
    LogManager.d(LogTags.SCRCPY_CLIENT, "初始化会话监控器")
}

/**
 * 停止监控器
 */
fun Session.stopMonitor() {
    setStateMachineInternal(null)
    setReconnectCallbackInternal(null)
    clearComponentStates()
    resetReconnectAttempts()
    LogManager.d(LogTags.SCRCPY_CLIENT, "停止会话监控器: $deviceIdentifier")
}

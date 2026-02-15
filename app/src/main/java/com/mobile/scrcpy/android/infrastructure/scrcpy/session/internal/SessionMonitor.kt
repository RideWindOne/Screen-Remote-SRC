package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionStateMachine
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
    resources.replaceMonitorBus(deviceIdentifier)
}

/**
 * 初始化监控器
 */
fun Session.initMonitor(
    stateMachine: ConnectionStateMachine,
    onReconnect: () -> Unit,
) {
    runtime.bind(stateMachine, onReconnect)
    LogManager.d(LogTags.SCRCPY_CLIENT, "初始化会话监控器")
}

/**
 * 停止监控器
 */
fun Session.stopMonitor() {
    runtime.bind(stateMachine = null, reconnectCallback = null)
    runtime.clearComponentStates()
    runtime.resetReconnectAttempts()
    LogManager.d(LogTags.SCRCPY_CLIENT, "停止会话监控器: $deviceIdentifier")
}

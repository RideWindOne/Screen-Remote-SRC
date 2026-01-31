package com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scrcpy 客户端重连逻辑
 */
internal class ScrcpyClientReconnect(
    private val adbConnectionManager: AdbConnectionManager,
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val getCurrentSessionId: () -> String?,
    private val getCurrentDeviceId: () -> String?,
    private val connect: suspend (String, String, Int, ScrcpyOptions, Boolean) -> Result<Boolean>,
) {
    private var reconnectAttempts: Int = 0
    private var isReconnecting: Boolean = false

    /**
     * 触发重连（由 ScrcpySessionMonitor 调用）
     */
    fun triggerReconnect() {
        val sessionId = getCurrentSessionId()
        if (sessionId == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "无法重连：会话 ID 为空")
            connectionState.value = ConnectionState.Error("会话未连接")
            return
        }

        val deviceId = getCurrentDeviceId()
        if (deviceId == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "无法重连：设备 ID 为空")
            connectionState.value = ConnectionState.Error("设备未连接")
            return
        }

        if (isReconnecting) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "重连正在进行中，跳过本次重连请求")
            return
        }

        isReconnecting = true
        reconnectAttempts++

        LogManager.d(
            LogTags.SCRCPY_CLIENT,
            "========== 执行重连 (尝试 $reconnectAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}) ==========",
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 检查 ADB 连接状态
                LogManager.d(LogTags.SCRCPY_CLIENT, "检查 ADB 连接状态...")
                val conn = adbConnectionManager.getConnection(deviceId)
                if (conn == null) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ADB 连接不存在")
                    handleReconnectFailure(deviceId, "ADB 会话已断开，请重新连接设备")
                    return@launch
                }

                val testResult = conn.executeShell("echo test", retryOnFailure = false)
                if (testResult.isFailure) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ADB 连接不可用: ${testResult.exceptionOrNull()?.message}")
                    handleReconnectFailure(deviceId, "ADB 会话已断开，请重新连接设备")
                    return@launch
                }
                LogManager.d(LogTags.SCRCPY_CLIENT, "ADB 连接正常")

                // 尝试重新连接
                LogManager.d(LogTags.SCRCPY_CLIENT, "尝试重新连接...")
                withContext(Dispatchers.Main) {
                    connectionState.value = ConnectionState.Connecting
                }

                // 获取会话配置
                val session = CurrentSession.currentOrNull
                if (session == null) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ 会话不存在")
                    handleReconnectFailure(deviceId, "会话配置丢失")
                    return@launch
                }

                val reconnectResult =
                    connect(
                        sessionId,
                        deviceId.substringBefore(":"),
                        deviceId.substringAfter(":", NetworkConstants.DEFAULT_ADB_PORT_INT.toString()).toIntOrNull()
                            ?: NetworkConstants.DEFAULT_ADB_PORT_INT,
                        session.options,
                        true,
                    )

                if (reconnectResult.isSuccess) {
                    LogManager.d(LogTags.SCRCPY_CLIENT, "========== 重连成功 (尝试 $reconnectAttempts 次) ==========")
                    isReconnecting = false
                } else {
                    val errorMsg = reconnectResult.exceptionOrNull()?.message ?: "未知错误"
                    LogManager.e(
                        LogTags.SCRCPY_CLIENT,
                        "========== 重连失败 (尝试 $reconnectAttempts 次): $errorMsg ==========",
                    )
                    handleReconnectFailure(deviceId, errorMsg)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "========== 重连过程出错: ${e.message} ==========", e)
                handleReconnectFailure(deviceId, e.message ?: "未知错误")
            }
        }
    }

    /**
     * 处理重连失败
     */
    private suspend fun handleReconnectFailure(
        deviceId: String,
        errorMessage: String,
    ) {
        isReconnecting = false

        // 推送重连失败事件，由 ScrcpySessionMonitor 决定是否继续重试
        if (reconnectAttempts < ScrcpyConstants.MAX_RECONNECT_ATTEMPTS && !isPermanentError(errorMessage)) {
            LogManager.d(LogTags.SCRCPY_CLIENT, "将继续重试...")
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.RequestReconnect(errorMessage))
        } else {
            LogManager.e(LogTags.SCRCPY_CLIENT, "重连失败，停止重试")
            withContext(Dispatchers.Main) {
                connectionState.value = ConnectionState.Error(errorMessage)
            }
            reconnectAttempts = 0
        }
    }

    /**
     * 判断是否是永久性错误（不应重试的错误）
     */
    private fun isPermanentError(errorMessage: String): Boolean {
        val permanentErrorKeywords =
            listOf(
                "设备未连接",
                "设备连接已断开",
                "ADB 会话已断开",
                "未授权",
                "权限被拒绝",
                "不支持",
                "无效的参数",
            )

        return permanentErrorKeywords.any { errorMessage.contains(it, ignoreCase = true) }
    }

    /**
     * 重置重连状态
     */
    fun reset() {
        reconnectAttempts = 0
        isReconnecting = false
    }

    /**
     * 获取重连状态
     */
    fun isReconnecting() = isReconnecting
}

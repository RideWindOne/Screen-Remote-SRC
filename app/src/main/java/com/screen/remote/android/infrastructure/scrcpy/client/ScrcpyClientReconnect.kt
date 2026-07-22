package com.screen.remote.android.infrastructure.scrcpy.client

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import com.screen.remote.android.infrastructure.scrcpy.session.SessionManager
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Scrcpy 客户端重连逻辑
 */
internal class ScrcpyClientReconnect(
    private val adbConnectionManager: AdbConnectionManager,
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val getCurrentDeviceId: () -> String?,
    private val connect: suspend (String, ScrcpyOptions, Boolean) -> Result<Boolean>,
    private val sessionManager: SessionManager,
) {
    private companion object {
        private const val MAX_RECONNECT_BACKOFF_MS = 3000L
    }

    private var isReconnecting: Boolean = false
    private val reconnectLock = Any()
    private val reconnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    /**
     * 触发重连（由 ScrcpySessionMonitor 调用）
     */
    fun triggerReconnect() {
        val session = sessionManager.currentOrNull
        if (session == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Unable to reconnect: session ID is empty")
            connectionState.value = ConnectionState.Error("Session is not connected")
            return
        }
        val sessionId = session.sessionId

        val deviceId = getCurrentDeviceId()
        if (deviceId == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Unable to reconnect: Device ID is empty")
            connectionState.value = ConnectionState.Error("Device is not connected")
            return
        }

        val attempt = (session.sessionState.value as? SessionState.Reconnecting)?.attempt ?: 1
        synchronized(reconnectLock) {
            if (isReconnecting) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "Reconnection is in progress, skip this reconnection request")
                return
            }

            isReconnecting = true
        }

        LogManager.d(
            LogTags.SCRCPY_CLIENT,
            "========== Perform reconnection (try $attempt/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}) ==========",
        )

        val retryDelayMs = computeReconnectDelay(attempt)
        if (retryDelayMs > 0) {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "Wait for ${retryDelayMs}ms before reconnecting (try #$attempt)",
            )
        }

        reconnectJob?.cancel()
        reconnectJob =
            reconnectScope.launch {
                try {
                    if (retryDelayMs > 0) {
                        delay(retryDelayMs.milliseconds)
                    }

                    // 不在这里重复执行 shell 验证。正式 connect 流程会复用、验证或重建 ADB，
                    // 缓存连接缺失也不应阻止使用会话候选地址恢复连接。
                    val hasCachedAdbConnection = adbConnectionManager.getConnection(deviceId) != null
                    LogManager.d(
                        LogTags.SCRCPY_CLIENT,
                        if (hasCachedAdbConnection) "Reuse existing ADB connection to enter reconnection" else "ADB cache connection does not exist, rebuild according to session address",
                    )

                    // 尝试重新连接
                    LogManager.d(LogTags.SCRCPY_CLIENT, "Try reconnecting...")
                    withContext(Dispatchers.Main) {
                        connectionState.value = ConnectionState.Connecting
                    }

                    // 获取会话配置
                    val currentSession = sessionManager.currentOrNull
                    if (currentSession == null || currentSession !== session) {
                        LogManager.e(LogTags.SCRCPY_CLIENT, "✗ Session does not exist")
                        handleReconnectFailure("Session configuration is missing")
                        return@launch
                    }

                    val reconnectResult =
                        connect(
                            sessionId,
                            currentSession.options,
                            true,
                        )

                    if (reconnectResult.isSuccess) {
                        LogManager.d(
                            LogTags.SCRCPY_CLIENT,
                            "========== Reconnection successful (try $attempt times) ==========",
                        )
                        synchronized(reconnectLock) {
                            isReconnecting = false
                        }
                    } else {
                        val errorMsg = reconnectResult.exceptionOrNull()?.message ?: "Unknown error"
                        LogManager.e(
                            LogTags.SCRCPY_CLIENT,
                            "========== Reconnection failed (attempted $attempt times): $errorMsg ==========",
                        )
                        handleReconnectFailure(errorMsg)
                    }
                } catch (e: CancellationException) {
                    synchronized(reconnectLock) {
                        isReconnecting = false
                    }
                    throw e
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.SCRCPY_CLIENT,
                        "========== Error during reconnection: ${e.message} ==========",
                        e
                    )
                    handleReconnectFailure(e.message ?: "Unknown error")
                }
            }
    }

    /**
     * 处理重连失败
     */
    private suspend fun handleReconnectFailure(errorMessage: String) {
        synchronized(reconnectLock) {
            isReconnecting = false
        }

        // 重连次数只由 SessionRuntimeState 管理；这里仅报告本次结果。
        if (!isPermanentError(errorMessage)) {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "This reconnection fails, and the session state machine decides whether to continue retrying.",
            )
            sessionManager.currentOrNull?.handleEvent(
                SessionEvent.RequestReconnect(
                    ReconnectIssue(
                        kind = ReconnectIssueKind.ReconnectFailure,
                        detail = errorMessage,
                    ),
                ),
            )
        } else {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Permanent error detected, stop retrying")
            withContext(Dispatchers.Main) {
                connectionState.value = ConnectionState.Error(errorMessage)
            }
            sessionManager.currentOrNull?.handleEvent(
                SessionEvent.SessionError(
                    SessionIssue(
                        kind = SessionIssueKind.RuntimeFailure,
                        detail = errorMessage,
                    ),
                ),
            )
        }
    }

    /**
     * 判断是否是永久性错误（不应重试的错误）
     */
    private fun isPermanentError(errorMessage: String): Boolean {
        val permanentErrorKeywords =
            listOf(
                "device is not connected",
                "device connection disconnected",
                "ADB session disconnected",
                "unauthorized",
                "permission denied",
                "unsupported",
                "invalid argument",
            )

        return permanentErrorKeywords.any { errorMessage.contains(it, ignoreCase = true) }
    }

    /**
     * 重置重连状态
     */
    fun reset() {
        synchronized(reconnectLock) {
            isReconnecting = false
        }
    }

    fun cancelPending() {
        reconnectJob?.cancel()
        reconnectJob = null
        reset()
    }

    private fun computeReconnectDelay(attempt: Int): Long =
        if (attempt <= 1) {
            0L
        } else {
            val exponent = (attempt - 2).coerceAtLeast(0)
            ((ScrcpyConstants.DEFAULT_RECONNECT_DELAY / 2L) * (1L shl exponent)).coerceAtMost(
                MAX_RECONNECT_BACKOFF_MS,
            )
        }
}

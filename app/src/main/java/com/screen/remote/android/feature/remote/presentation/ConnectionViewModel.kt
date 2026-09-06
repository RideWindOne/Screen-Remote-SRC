package com.screen.remote.android.feature.remote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.SessionRepository
import com.screen.remote.android.core.domain.model.ConnectionProgress
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.client.ScrcpyClient
import com.screen.remote.android.infrastructure.scrcpy.session.internal.rememberResolvedAudioDecoder
import com.screen.remote.android.infrastructure.scrcpy.session.internal.rememberResolvedVideoDecoder
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderResolutionRecoveryRequest
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ConnectStatus {
    object Idle : ConnectStatus()

    data class Connecting(
        val sessionId: String,
    ) : ConnectStatus()

    data object Connected : ConnectStatus()

    data object Failed : ConnectStatus()

}

/**
 * 连接管理 ViewModel
 * 职责：连接/断开/重连、连接状态、进度跟踪
 */
class ConnectionViewModel(
    private val scrcpyClient: ScrcpyClient,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    // 当前连接任务的 Job，用于取消正在进行的连接
    private var connectJob: Job? = null

    @Volatile
    private var disconnectRequested = false

    // ============ 连接状态 ============

    private val _connectStatus = MutableStateFlow<ConnectStatus>(ConnectStatus.Idle)
    val connectStatus: StateFlow<ConnectStatus> = _connectStatus.asStateFlow()

    private val _connectedSessionId = MutableStateFlow<String?>(null)
    val connectedSessionId: StateFlow<String?> = _connectedSessionId.asStateFlow()
    private val _activeSessionData = MutableStateFlow<SessionData?>(null)
    val activeSessionData: StateFlow<SessionData?> = _activeSessionData.asStateFlow()
    val decoderResolutionRecoveryRequest: StateFlow<DecoderResolutionRecoveryRequest?> =
        scrcpyClient.decoderResolutionRecoveryRequest

    // 连接进度状态
    val connectionProgress: StateFlow<List<ConnectionProgress>> =
        scrcpyClient.connectionProgress
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    // ============ 连接操作 ============

    fun connectSession(sessionId: String) {
        launchConnection { sessionRepository.getSessionData(sessionId) }
    }

    fun connectSession(sessionData: SessionData) {
        launchConnection { sessionData }
    }

    fun reconnectActiveSession() {
        val sessionData = _activeSessionData.value
        if (sessionData != null) {
            connectSession(sessionData)
        } else {
            _connectedSessionId.value?.let(::connectSession)
        }
    }

    fun confirmDecoderResolutionRecovery() = scrcpyClient.confirmDecoderResolutionRecovery()

    fun dismissDecoderResolutionRecovery() = scrcpyClient.dismissDecoderResolutionRecovery()

    private fun launchConnection(resolveSession: suspend () -> SessionData?) {
        // 取消之前的连接任务
        connectJob?.cancel()

        connectJob =
            viewModelScope.launch(Dispatchers.IO) {
                val sessionData = resolveSession()
                if (sessionData == null) {
                    withContext(Dispatchers.Main) {
                        _connectStatus.value = ConnectStatus.Failed
                        _connectedSessionId.value = null
                    }
                    return@launch
                }
                val sessionId = sessionData.id

                // 判断是否为重连（已经有 connectedSessionId）
                val isReconnecting = _connectedSessionId.value != null

                // 立即设置 connectedSessionId，让 RemoteDisplayScreen 显示（即使连接失败也能看到进度）
                withContext(Dispatchers.Main) {
                    _connectedSessionId.value = sessionId
                    _activeSessionData.value = sessionData
                    _connectStatus.value =
                        ConnectStatus.Connecting(sessionId)
                }

                try {
                    val options = sessionData.toScrcpyOptions()
                    val result =
                        scrcpyClient.connect(
                            sessionId = sessionId,
                            options = options,
                            isReconnecting = isReconnecting,
                        )

                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            _connectStatus.value = ConnectStatus.Connected
                        } else {
                            _connectStatus.value = ConnectStatus.Failed
                        }
                    }
                } catch (cancelled: CancellationException) {
                    LogManager.d(LogTags.CONNECTION_VM, "Connection task canceled: $sessionId")
                    throw cancelled
                } catch (e: Exception) {
                    LogManager.e(LogTags.CONNECTION_VM, "Connection session exception: ${e.message}")
                    withContext(Dispatchers.Main) {
                        _connectStatus.value = ConnectStatus.Failed
                    }
                }
            }
    }

    fun cancelConnect() {
        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.cancelConnect()
                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Idle
                    _connectedSessionId.value = null
                    _activeSessionData.value = null
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "Connection cancellation exception: ${e.message}", e)
            }
        }
    }

    fun clearConnectStatus() {
        _connectStatus.value = ConnectStatus.Idle
        _connectedSessionId.value = null
        _activeSessionData.value = null
    }

    /**
     * 关闭被控端屏幕常亮（充电时保持唤醒）
     */
    suspend fun disableStayAwake(): Result<Boolean> {
        return try {
            LogManager.d(LogTags.CONNECTION_VM, "Disabling stay awake...")

            // 先查询当前值（用于调试）
            val queryResult = scrcpyClient.executeShellCommand("settings get global stay_on_while_plugged_in")
            LogManager.d(LogTags.CONNECTION_VM, "Current stay_on_while_plugged_in (global): ${queryResult.getOrNull()}")

            val querySystemResult = scrcpyClient.executeShellCommand("settings get system stay_on_while_plugged_in")
            LogManager.d(LogTags.CONNECTION_VM, "Current stay_on_while_plugged_in (system): ${querySystemResult.getOrNull()}")

            // 设置 global
            val setGlobalResult = scrcpyClient.executeShellCommand("settings put global stay_on_while_plugged_in 0")
            LogManager.d(LogTags.CONNECTION_VM, "Set global result: success=${setGlobalResult.isSuccess}, value=${setGlobalResult.getOrNull()}")

            // 同时设置 system（兼容旧版本）
            val setSystemResult = scrcpyClient.executeShellCommand("settings put system stay_on_while_plugged_in 0")
            LogManager.d(LogTags.CONNECTION_VM, "Set system result: success=${setSystemResult.isSuccess}, value=${setSystemResult.getOrNull()}")

            // 验证设置是否生效
            val verifyResult = scrcpyClient.executeShellCommand("settings get global stay_on_while_plugged_in")
            LogManager.d(LogTags.CONNECTION_VM, "Verify stay_on_while_plugged_in after set: ${verifyResult.getOrNull()}")

            if (setGlobalResult.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(setGlobalResult.exceptionOrNull() ?: Exception("Failed to set global stay_on_while_plugged_in"))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.CONNECTION_VM, "disableStayAwake exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun disconnectFromDevice() {
        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.CONNECTION_VM, "The user actively ends the session...")
                disconnectRequested = true

                // 1. 断开 scrcpy 连接
                scrcpyClient.disconnect()

                // 2. 保留 ADB 保活（不移除前台服务保护）
                LogManager.d(LogTags.CONNECTION_VM, "scrcpy is disconnected, ADB connection remains alive")

                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Idle
                    _connectedSessionId.value = null
                    _activeSessionData.value = null
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "End session exception: ${e.message}", e)
            } finally {
                disconnectRequested = false
            }
        }
    }

    /**
     * 处理连接丢失（Socket closed / Stream closed）
     * 只断开 scrcpy 连接，保留 ADB 连接和前台服务保活
     * 不清除 connectedSessionId，让用户停留在 RemoteDisplayScreen 看到重连进度
     */
    fun handleConnectionLost() {
        if (disconnectRequested) {
            LogManager.d(
                LogTags.CONNECTION_VM,
                "Ignore connection loss: currently in user active disconnection process"
            )
            return
        }

        LogManager.w(
            LogTags.CONNECTION_VM,
            "Handle connection loss: leave it to session lifecycle for unified reconnection"
        )
        createSessionContext().emit(
            SessionEvent.RequestReconnect(
                ReconnectIssue(
                    kind = ReconnectIssueKind.SocketDisconnected,
                    detail = "Media stream ended by remote peer",
                ),
            ),
        )
    }

    // ============ 状态访问 ============

    fun getConnectionState() = scrcpyClient.connectionState

    fun getVideoStream() = scrcpyClient.videoStreamState

    fun getAudioStream() = scrcpyClient.audioStreamState

    fun getVideoResolution() = scrcpyClient.videoResolution

    fun getCompatibilityFrame() = scrcpyClient.compatibilityFrame

    fun getCurrentSessionOptions(): ScrcpyOptions? = scrcpyClient.getCurrentSessionOptions()

    fun createSessionContext(): SessionContext = scrcpyClient.createSessionContext()

    fun rememberResolvedVideoDecoder(
        decoderName: String,
        expectedDeviceSerial: String,
        expectedCodec: String,
    ) {
        createSessionContext().currentSession()
            ?.rememberResolvedVideoDecoder(decoderName, expectedDeviceSerial, expectedCodec)
    }

    fun rememberResolvedAudioDecoder(
        decoderName: String,
        expectedDeviceSerial: String,
        expectedCodec: String,
    ) {
        createSessionContext().currentSession()
            ?.rememberResolvedAudioDecoder(decoderName, expectedDeviceSerial, expectedCodec)
    }

    fun runtimeRejectedDecoders(key: String): Set<String> =
        createSessionContext().currentSession()?.runtimeRejectedDecoders(key).orEmpty()

    fun rememberRuntimeRejectedDecoder(
        key: String,
        decoderName: String,
    ) {
        createSessionContext().currentSession()?.rememberRuntimeRejectedDecoder(key, decoderName)
    }

    suspend fun wakeUpScreen() = scrcpyClient.wakeUpScreen()

    // ============ Factory ============

    companion object {
        fun provideFactory(
            scrcpyClient: ScrcpyClient,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ConnectionViewModel(scrcpyClient, sessionRepository) as T
            }
    }
}

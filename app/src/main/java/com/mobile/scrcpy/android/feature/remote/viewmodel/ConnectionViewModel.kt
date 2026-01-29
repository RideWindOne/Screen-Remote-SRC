package com.mobile.scrcpy.android.feature.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ConnectionProgress
import com.mobile.scrcpy.android.core.domain.model.parseMaxSize
import com.mobile.scrcpy.android.feature.session.data.repository.SessionRepository
import com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy.ScrcpyClient
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
        val message: String,
    ) : ConnectStatus()

    data class Connected(
        val sessionId: String,
    ) : ConnectStatus()

    data class Failed(
        val sessionId: String,
        val error: String,
    ) : ConnectStatus()

    data class Unauthorized(
        val sessionId: String,
    ) : ConnectStatus()
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

    // ============ 连接状态 ============

    private val _connectStatus = MutableStateFlow<ConnectStatus>(ConnectStatus.Idle)
    val connectStatus: StateFlow<ConnectStatus> = _connectStatus.asStateFlow()

    private val _connectedSessionId = MutableStateFlow<String?>(null)
    val connectedSessionId: StateFlow<String?> = _connectedSessionId.asStateFlow()

    // 连接进度状态
    val connectionProgress: StateFlow<List<ConnectionProgress>> =
        scrcpyClient.connectionProgress
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    // ============ 连接操作 ============

    fun connectToDevice(
        host: String,
        port: Int = NetworkConstants.DEFAULT_ADB_PORT_INT,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = scrcpyClient.connect(host, port)
                if (result.isFailure) {
                    LogManager.e(LogTags.CONNECTION_VM, "连接失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "连接异常: ${e.message}", e)
            }
        }
    }

    fun connectSession(sessionId: String) {
        // 取消之前的连接任务
        connectJob?.cancel()

        connectJob =
            viewModelScope.launch(Dispatchers.IO) {
                val sessionData = sessionRepository.getSessionData(sessionId)
                if (sessionData == null) {
                    withContext(Dispatchers.Main) {
                        _connectStatus.value = ConnectStatus.Failed(sessionId, "会话不存在")
                        _connectedSessionId.value = null
                    }
                    return@launch
                }

                // 判断是否为重连（已经有 connectedSessionId）
                val isReconnecting = _connectedSessionId.value != null

                // 立即设置 connectedSessionId，让 RemoteDisplayScreen 显示（即使连接失败也能看到进度）
                withContext(Dispatchers.Main) {
                    _connectedSessionId.value = sessionId
                    _connectStatus.value =
                        ConnectStatus.Connecting(
                            sessionId,
                            if (isReconnecting) "Reconnecting..." else "Connecting to ADB...",
                        )
                }

                try {
                    val port = sessionData.port.toIntOrNull() ?: NetworkConstants.DEFAULT_ADB_PORT_INT
                    val maxSize = sessionData.maxSize.parseMaxSize()
                    val bitrate = sessionData.bitrate.toIntOrNull() ?: ScrcpyConstants.DEFAULT_BITRATE_INT
                    val maxFps = sessionData.maxFps.toIntOrNull() ?: ScrcpyConstants.DEFAULT_MAX_FPS

                    val result =
                        scrcpyClient.connect(
                            host = sessionData.host,
                            port = port,
                            maxSize = maxSize,
                            bitRate = bitrate,
                            maxFps = maxFps,
                            videoCodec = sessionData.videoCodec,
                            videoEncoder = sessionData.videoEncoder,
                            enableAudio = sessionData.enableAudio,
                            audioCodec = sessionData.audioCodec,
                            audioEncoder = sessionData.audioEncoder,
                            keyFrameInterval = sessionData.keyFrameInterval,
                            stayAwake = sessionData.stayAwake,
                            turnScreenOff = sessionData.turnScreenOff,
                            powerOffOnClose = sessionData.powerOffOnClose,
                        )

                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            _connectStatus.value = ConnectStatus.Connected(sessionId)
                        } else {
                            _connectStatus.value =
                                ConnectStatus.Failed(
                                    sessionId,
                                    result.exceptionOrNull()?.message ?: "连接失败",
                                )
                        }
                    }
                } catch (e: Exception) {
                    LogManager.e(LogTags.CONNECTION_VM, "连接会话异常: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        _connectStatus.value = ConnectStatus.Failed(sessionId, e.message ?: "连接失败")
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
                scrcpyClient.disconnect()
                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Idle
                    _connectedSessionId.value = null
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "取消连接异常: ${e.message}", e)
            }
        }
    }

    fun clearConnectStatus() {
        _connectStatus.value = ConnectStatus.Idle
        _connectedSessionId.value = null
    }

    fun disconnectFromDevice() {
        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.CONNECTION_VM, "用户主动结束会话...")

                // 1. 断开 scrcpy 连接
                scrcpyClient.disconnect()

                // 2. 保留 ADB 保活（不移除前台服务保护）
                LogManager.d(LogTags.CONNECTION_VM, "scrcpy 已断开，ADB 连接保持保活")

                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Idle
                    _connectedSessionId.value = null
                }
                LogManager.d(LogTags.CONNECTION_VM, "结束会话完成")
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "结束会话异常: ${e.message}", e)
            }
        }
    }

    /**
     * 处理连接丢失（Socket closed / Stream closed）
     * 只断开 scrcpy 连接，保留 ADB 连接和前台服务保活
     * 不清除 connectedSessionId，让用户停留在 RemoteDisplayScreen 看到重连进度
     */
    fun handleConnectionLost() {
        LogManager.w(LogTags.CONNECTION_VM, "处理连接丢失：断开 scrcpy，保留 ADB 保活")

        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 断开 scrcpy 连接（保留 ADB 连接）
                scrcpyClient.disconnect()

                LogManager.d(LogTags.CONNECTION_VM, "scrcpy 已断开，ADB 连接保持保活")

                // 2. 更新 UI 状态（保持 connectedSessionId，让用户停留在 RemoteDisplayScreen）
                withContext(Dispatchers.Main) {
                    val sessionId = _connectedSessionId.value
                    if (sessionId != null) {
                        _connectStatus.value =
                            ConnectStatus.Connecting(
                                sessionId,
                                "Connection lost, preparing to reconnect...",
                            )
                    } else {
                        _connectStatus.value = ConnectStatus.Idle
                    }
                }

                LogManager.d(LogTags.CONNECTION_VM, "连接丢失处理完成")
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "处理连接丢失异常: ${e.message}", e)
            }
        }
    }

    /**
     * 临时断开连接（后台时使用），不改变连接状态
     */
    fun pauseConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.CONNECTION_VM, "暂停连接...")
                scrcpyClient.disconnect()
            } catch (e: Exception) {
                LogManager.e(LogTags.CONNECTION_VM, "暂停连接异常: ${e.message}", e)
            }
        }
    }

    // ============ 状态访问 ============

    fun getConnectionState() = scrcpyClient.connectionState

    fun getVideoStream() = scrcpyClient.videoStreamState

    fun getAudioStream() = scrcpyClient.audioStreamState

    fun getVideoResolution() = scrcpyClient.videoResolution

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

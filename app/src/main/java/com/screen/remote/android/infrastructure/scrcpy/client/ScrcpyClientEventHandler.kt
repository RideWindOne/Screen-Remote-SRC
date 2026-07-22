package com.screen.remote.android.infrastructure.scrcpy.client

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyErrorEvent
import com.screen.remote.android.core.domain.model.ScrcpyEventType
import com.screen.remote.android.core.domain.model.ScrcpyStatus
import com.screen.remote.android.core.domain.model.ScrcpyStatusEvent
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scrcpy 客户端事件处理
 */
internal class ScrcpyClientEventHandler(
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val getCurrentSessionId: () -> String?,
    private val getCurrentDeviceId: () -> String?,
    private val updateConnectionStateOnError: (String) -> Unit,
) {
    /**
     * 处理 Native 层状态变化事件
     */
    fun handleNativeStatusChange(event: ScrcpyStatusEvent) {
        val sessionId = event.deviceId ?: getCurrentSessionId()

        LogManager.d(
            LogTags.SCRCPY_CLIENT,
            "Native status changes: status=${event.status}, sessionId=$sessionId, error=${event.errorMessage}",
        )

        when (event.status) {
            ScrcpyStatus.CONNECTING -> {
                if (connectionState.value !is ConnectionState.Connecting &&
                    connectionState.value !is ConnectionState.Reconnecting
                ) {
                    connectionState.value = ConnectionState.Connecting
                }
            }

            ScrcpyStatus.CONNECTED -> {
                if (connectionState.value !is ConnectionState.Connected) {
                    connectionState.value = ConnectionState.Connected
                }
            }

            ScrcpyStatus.DISCONNECTED -> {
                if (connectionState.value is ConnectionState.Connected) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "Native layer detects disconnection")
                    updateConnectionStateOnError(event.errorMessage ?: "Device disconnected")
                }
            }

            ScrcpyStatus.CONNECTION_FAILED -> {
                val errorMsg = event.errorMessage ?: "Connection failed"
                LogManager.e(LogTags.SCRCPY_CLIENT, "Native layer connection failed: $errorMsg")
                connectionState.value = ConnectionState.Error(errorMsg)
            }
        }
    }

    /**
     * 处理 Native 层错误事件
     */
    fun handleNativeError(event: ScrcpyErrorEvent) {
        val sessionId = event.deviceId ?: getCurrentSessionId()
        val errorMsg = event.errorMessage ?: event.eventType.name

        LogManager.e(
            LogTags.SCRCPY_CLIENT,
            "Native error event: type=${event.eventType}, sessionId=$sessionId, error=$errorMsg",
        )

        when (event.eventType) {
            ScrcpyEventType.DEVICE_DISCONNECTED -> {
                if (connectionState.value is ConnectionState.Connected) {
                    updateConnectionStateOnError("Device disconnected: $errorMsg")
                }
            }

            ScrcpyEventType.SERVER_CONNECTION_FAILED -> {
                connectionState.value = ConnectionState.Error("Server connection failed: $errorMsg")
            }

            ScrcpyEventType.DEMUXER_ERROR -> {
                if (connectionState.value is ConnectionState.Connected) {
                    updateConnectionStateOnError("Demuxer error: $errorMsg")
                }
            }

            ScrcpyEventType.CONTROLLER_ERROR -> {
                LogManager.w(LogTags.SCRCPY_CLIENT, "Controller error: $errorMsg")
            }

            ScrcpyEventType.RECORDER_ERROR -> {
                LogManager.w(LogTags.SCRCPY_CLIENT, "Recorder error: $errorMsg")
            }

            ScrcpyEventType.SERVER_CONNECTED -> {
                LogManager.d(LogTags.SCRCPY_CLIENT, "Server connection successful")
            }
        }
    }
}

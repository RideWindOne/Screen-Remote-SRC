package com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyErrorEvent
import com.mobile.scrcpy.android.core.domain.model.ScrcpyEventType
import com.mobile.scrcpy.android.core.domain.model.ScrcpyStatus
import com.mobile.scrcpy.android.core.domain.model.ScrcpyStatusEvent
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
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
            "Native 状态变化: status=${event.status}, sessionId=$sessionId, error=${event.errorMessage}",
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
                    LogManager.w(LogTags.SCRCPY_CLIENT, "Native 层检测到断开连接")
                    updateConnectionStateOnError(event.errorMessage ?: "设备断开连接")
                }
            }

            ScrcpyStatus.CONNECTION_FAILED -> {
                val errorMsg = event.errorMessage ?: "连接失败"
                LogManager.e(LogTags.SCRCPY_CLIENT, "Native 层连接失败: $errorMsg")
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
            "Native 错误事件: type=${event.eventType}, sessionId=$sessionId, error=$errorMsg",
        )

        when (event.eventType) {
            ScrcpyEventType.DEVICE_DISCONNECTED -> {
                if (connectionState.value is ConnectionState.Connected) {
                    updateConnectionStateOnError("设备断开连接: $errorMsg")
                }
            }

            ScrcpyEventType.SERVER_CONNECTION_FAILED -> {
                connectionState.value = ConnectionState.Error("服务器连接失败: $errorMsg")
            }

            ScrcpyEventType.DEMUXER_ERROR -> {
                if (connectionState.value is ConnectionState.Connected) {
                    updateConnectionStateOnError("解复用器错误: $errorMsg")
                }
            }

            ScrcpyEventType.CONTROLLER_ERROR -> {
                LogManager.w(LogTags.SCRCPY_CLIENT, "控制器错误: $errorMsg")
            }

            ScrcpyEventType.RECORDER_ERROR -> {
                LogManager.w(LogTags.SCRCPY_CLIENT, "录制器错误: $errorMsg")
            }

            ScrcpyEventType.SERVER_CONNECTED -> {
                LogManager.d(LogTags.SCRCPY_CLIENT, "服务器连接成功")
            }
        }
    }
}

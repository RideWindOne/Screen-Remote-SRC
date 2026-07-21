package com.screen.remote.android.infrastructure.scrcpy.client

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.SessionManager
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class ScrcpyClientStateCoordinator(
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val sessionManager: SessionManager,
    private val reconnectManager: ScrcpyClientReconnect,
    private val getCurrentDeviceId: () -> String?,
) {
    fun updateConnectionStateOnError(message: String) {
        if (connectionState.value is ConnectionState.Connected) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Connection error: $message")
            getCurrentDeviceId()?.let {
                sessionManager.currentOrNull?.handleEvent(
                    SessionEvent.RequestReconnect(
                        ReconnectIssue(
                            kind = ReconnectIssueKind.RuntimeError,
                            detail = message,
                        ),
                    ),
                )
            }
        }
    }

    fun handleSessionStateChange(state: SessionState) {
        LogManager.d(LogTags.SDL, "Session status change: $state")

        when (state) {
            is SessionState.Connected -> {
                if (connectionState.value !is ConnectionState.Connected) {
                    CoroutineScope(Dispatchers.Main).launch {
                        connectionState.value = ConnectionState.Connected
                    }
                }
            }

            is SessionState.Reconnecting -> {
                CoroutineScope(Dispatchers.Main).launch {
                    connectionState.value = ConnectionState.Reconnecting
                }
            }

            is SessionState.Failed -> {
                CoroutineScope(Dispatchers.Main).launch {
                    connectionState.value = ConnectionState.Error(state.reason)
                }
                reconnectManager.reset()
            }

            is SessionState.AdbConnected,
            is SessionState.AdbDisconnected,
            is SessionState.ServerStarting,
            is SessionState.ServerStarted,
            is SessionState.ServerFailed,
            is SessionState.Idle,
            -> {
                // 这些状态已由运行时链路自身处理
            }

            else -> {}
        }
    }
}

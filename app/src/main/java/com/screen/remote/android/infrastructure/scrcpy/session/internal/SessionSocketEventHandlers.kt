package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionStep
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.model.ComponentState
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketConnectContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketConnectingContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketDisconnectContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketType
import com.screen.remote.android.infrastructure.scrcpy.session.model.reconnectDetail
import com.screen.remote.android.infrastructure.scrcpy.session.model.summary

internal fun Session.handleSocketConnecting(context: SocketConnectingContext) {
    runtime.updateSocketExpectation(
        expectedSocketCount = context.expectedSocketCount,
        audioEnabled = context.audioEnabled,
    )
    runtime.updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.RUNNING, RemoteTexts.REMOTE_CONNECTING_SOCKET.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "开始连接 socket: ${context.summary()}")
}

internal fun Session.handleSocketConnected(
    socketType: SocketType,
    context: SocketConnectContext,
) {
    val componentSnapshot = runtime.updateComponentState(socketType.toComponent(), ComponentState.Connected)
    LogManager.d(LogTags.SCRCPY_CLIENT, "Socket 已连接: ${context.summary(socketType)}")

    val socketConnections = componentSnapshot.socketConnections

    if (socketConnections.allRequiredSocketsConnected) {
        runtime.updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.SUCCESS, RemoteTexts.REMOTE_SOCKET_CONNECTED.get())
        runtime.updateSessionState(
            SessionState.Connected(
                socketConnections.toConnectedContext(
                    localPort = context.localPort,
                    dummyByteConfirmed = context.dummyByteConfirmed,
                ),
            ),
        )
    }
}

internal fun Session.handleSocketDisconnected(
    socketType: SocketType,
    context: SocketDisconnectContext,
) {
    runtime.updateComponentState(socketType.toComponent(), ComponentState.Disconnected)

    if (runtime.sessionState.value is SessionState.Connected) {
        handleRequestReconnect(
            ReconnectIssue(
                kind = ReconnectIssueKind.SocketDisconnected,
                detail = context.reconnectDetail(socketType),
            ),
        )
    }
}

internal fun Session.handleSocketError(issue: SocketIssue) {
    runtime.updateProgress(
        ConnectionStep.CONNECT_SOCKET,
        StepStatus.FAILED,
        issue.progressMessage(),
    )
    LogManager.e(LogTags.SCRCPY_CLIENT, "Socket 错误: ${issue.summary()}")
}

internal fun SocketType.toComponent(): SessionComponent =
    when (this) {
        SocketType.Video -> SessionComponent.VideoSocket
        SocketType.Audio -> SessionComponent.AudioSocket
        SocketType.Control -> SessionComponent.ControlSocket
    }

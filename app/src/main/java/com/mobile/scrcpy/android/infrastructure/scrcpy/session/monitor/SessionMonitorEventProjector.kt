package com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor

import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.DecoderIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.summary

internal object SessionMonitorEventProjector {
    fun project(
        event: SessionEvent,
        resultingState: SessionState,
    ): List<ScrcpyMonitorEvent> =
        buildList {
            when (event) {
                is SessionEvent.AdbDisconnected -> {
                    add(exception(ExceptionType.ADB_ERROR, event.issue.message))
                }

                is SessionEvent.ServerPushFailed -> {
                    add(exception(ExceptionType.SERVER_ERROR, event.issue.message))
                }

                is SessionEvent.ServerFailed -> {
                    add(exception(event.issue.kind.toExceptionType(), event.issue.message))
                }

                is SessionEvent.ForwardFailed -> {
                    add(exception(event.issue.kind.toExceptionType(), "forward failed: ${event.issue.summary()}"))
                }

                is SessionEvent.SocketConnected -> {
                    if (resultingState is SessionState.Connected) {
                        add(ScrcpyMonitorEvent.ConnectionEstablished)
                    }
                }

                is SessionEvent.SocketError -> {
                    add(exception(event.issue.kind.toExceptionType(), "socket error: ${event.issue.summary()}"))
                }

                is SessionEvent.DecoderError -> {
                    add(exception(event.issue.kind.toExceptionType(), "decoder error: ${event.issue.summary()}"))
                }

                is SessionEvent.VideoEncoderDetectFailed -> {
                    add(exception(event.issue.kind.toExceptionType(), "video encoder detect failed: ${event.issue.message}"))
                }

                is SessionEvent.VideoEncoderError -> {
                    add(exception(event.issue.kind.toExceptionType(), "video encoder error: ${event.issue.message}"))
                }

                is SessionEvent.AudioEncoderError -> {
                    add(exception(event.issue.kind.toExceptionType(), "audio encoder error: ${event.issue.message}"))
                }

                is SessionEvent.RequestReconnect -> {
                    add(ScrcpyMonitorEvent.ConnectionLost(event.issue.message))
                    add(exception(event.issue.kind.toExceptionType(), event.issue.message))
                }

                is SessionEvent.RequestCleanup -> {
                    // Explicit cleanup is not a transport fault. Do not project it as ConnectionLost,
                    // otherwise user-initiated teardown pollutes diagnostics and monitor state.
                }

                is SessionEvent.SessionError -> {
                    add(exception(event.issue.kind.toExceptionType(), event.issue.message))
                }

                else -> Unit
            }
        }

    private fun exception(
        type: ExceptionType,
        message: String,
    ): ScrcpyMonitorEvent.Exception = ScrcpyMonitorEvent.Exception(type, message)

    private fun ServerIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            ServerIssueKind.CodecSelectionFailed -> ExceptionType.SERVER_ERROR
            ServerIssueKind.ConnectionFailure -> ExceptionType.NETWORK_ERROR
            ServerIssueKind.PushFailed,
            ServerIssueKind.StartFailed,
            ServerIssueKind.StartupTimeout,
            ServerIssueKind.StartupStdErr,
            ServerIssueKind.RuntimeStdOut,
            ServerIssueKind.RuntimeStdErr,
            ServerIssueKind.ProcessExited,
            ServerIssueKind.MonitorException,
            ServerIssueKind.Unknown,
            -> ExceptionType.SERVER_ERROR
        }

    private fun SessionIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            SessionIssueKind.RuntimeFailure -> ExceptionType.UNKNOWN
            SessionIssueKind.SessionNotFound,
            SessionIssueKind.Unknown,
            -> ExceptionType.UNKNOWN
        }

    private fun ForwardIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            ForwardIssueKind.SetupFailed,
            ForwardIssueKind.Unknown,
            -> ExceptionType.ADB_ERROR
        }

    private fun SocketIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            SocketIssueKind.ConnectFailed,
            SocketIssueKind.ConnectionLost,
            SocketIssueKind.HealthCheckFailed,
            SocketIssueKind.Unknown,
            -> ExceptionType.SOCKET_ERROR
        }

    private fun DecoderIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            DecoderIssueKind.CreateFailed,
            DecoderIssueKind.ConnectionLost,
            DecoderIssueKind.RuntimeError,
            DecoderIssueKind.Unknown,
            -> ExceptionType.DECODER_ERROR
        }

    private fun ReconnectIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            ReconnectIssueKind.SocketDisconnected -> ExceptionType.SOCKET_ERROR
            ReconnectIssueKind.DecoderError -> ExceptionType.DECODER_ERROR
            ReconnectIssueKind.RuntimeError,
            ReconnectIssueKind.ReconnectFailure,
            ReconnectIssueKind.Unknown,
            -> ExceptionType.NETWORK_ERROR
        }

    private fun CodecIssueKind.toExceptionType(): ExceptionType =
        when (this) {
            CodecIssueKind.DetectionFailed,
            CodecIssueKind.NoEncodersFound,
            CodecIssueKind.RuntimeError,
            CodecIssueKind.Unknown,
            -> ExceptionType.UNKNOWN
        }
}

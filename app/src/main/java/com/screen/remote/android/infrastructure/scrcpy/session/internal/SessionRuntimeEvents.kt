package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionStep
import com.screen.remote.android.core.domain.model.StepStatus
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbConnectionContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.CleanupTrigger
import com.screen.remote.android.infrastructure.scrcpy.session.model.CodecDetectionContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.CodecDetectionSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.CodecIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ComponentState
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderResolutionRecoveryRequest
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderType
import com.screen.remote.android.infrastructure.scrcpy.session.model.VideoResolutionRecoverySource
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardSetupContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectStateContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerPushContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerStartContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.monitor.ScrcpyMonitorBus
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketConnectContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketConnectingContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketDisconnectContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketType
import com.screen.remote.android.infrastructure.scrcpy.session.model.completedSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.logSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.reconnectDetail
import com.screen.remote.android.infrastructure.scrcpy.session.model.startedSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.summary
import com.screen.remote.android.infrastructure.scrcpy.session.model.targetSummary

internal fun Session.processEvent(event: SessionEvent) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "Handle event: $event")

    when (event) {
        is SessionEvent.AdbConnecting -> handleAdbConnecting()
        is SessionEvent.AdbVerifying -> handleAdbVerifying()
        is SessionEvent.AdbConnected -> handleAdbConnected(event.context)
        is SessionEvent.AdbDisconnected -> handleAdbDisconnected(event.issue)
        is SessionEvent.ServerPushing -> handleServerPushing(event.context)
        is SessionEvent.ServerPushed -> handleServerPushed(event.context)
        is SessionEvent.ServerPushFailed -> handleServerPushFailed(event.issue)
        is SessionEvent.ServerStarting -> handleServerStarting()
        is SessionEvent.ServerStarted -> handleServerStarted(event.context)
        is SessionEvent.ServerFailed -> handleServerFailed(event.issue)
        is SessionEvent.ForwardSetting -> handleForwardSetting()
        is SessionEvent.ForwardSetup -> handleForwardSetup(event.localPort, event.remoteSocket, event.context)
        is SessionEvent.ForwardRemoved -> handleForwardRemoved(event.localPort, event.context)
        is SessionEvent.ForwardFailed -> handleForwardFailed(event.issue)
        is SessionEvent.SocketConnecting -> handleSocketConnecting(event.context)
        is SessionEvent.SocketConnected -> handleSocketConnected(event.socketType, event.context)
        is SessionEvent.SocketDisconnected -> handleSocketDisconnected(event.socketType, event.context)
        is SessionEvent.SocketError -> handleSocketError(event.issue)
        is SessionEvent.DecoderStarted -> handleDecoderStarted(event.decoderType)
        is SessionEvent.DecoderStopped -> handleDecoderStopped(event.decoderType)
        is SessionEvent.DecoderError -> handleDecoderError(event.issue)
        is SessionEvent.ConfirmDecoderResolutionRecovery -> handleConfirmDecoderResolutionRecovery()
        is SessionEvent.DismissDecoderResolutionRecovery -> handleDismissDecoderResolutionRecovery()
        is SessionEvent.ServerVideoCaptureError -> handleServerVideoCaptureError(event.detail)
        is SessionEvent.RequestReconnect -> handleRequestReconnect(event.issue)
        is SessionEvent.RequestCleanup -> handleRequestCleanup(event.trigger)
        is SessionEvent.VideoEncoderDetecting -> handleVideoEncoderDetecting(event.context)
        is SessionEvent.VideoEncoderDetected -> handleVideoEncoderDetected(event.summary)
        is SessionEvent.VideoEncoderDetectFailed -> handleVideoEncoderDetectFailed(event.issue)
        is SessionEvent.VideoEncoderError -> handleVideoEncoderError(event.issue)
        is SessionEvent.AudioEncoderDetecting -> handleAudioEncoderDetecting(event.context)
        is SessionEvent.AudioEncoderDetected -> handleAudioEncoderDetected(event.summary)
        is SessionEvent.AudioEncoderError -> handleAudioEncoderError(event.issue)
        is SessionEvent.SessionError -> handleSessionError(event.issue)
    }

    monitorBus?.consumeSessionEvent(event, runtime.sessionState.value)
}

fun Session.createMonitorBus(deviceIdentifier: String = this.deviceIdentifier) {
    if (monitorBus?.deviceId == deviceIdentifier) return
    try {
        monitorBus?.stop()
    } catch (e: Exception) {
        LogManager.w(LogTags.SDL, "Failed to stop old MonitorBus: ${e.message}")
    }
    monitorBus = ScrcpyMonitorBus(deviceIdentifier).apply { start() }
}

fun Session.initMonitor(
    stateMachine: ConnectionStateMachine,
    onReconnect: () -> Unit,
) {
    runtime.bind(stateMachine, onReconnect)
    LogManager.d(LogTags.SCRCPY_CLIENT, "Initialize session monitor")
}

fun Session.stopMonitor() {
    runtime.bind(stateMachine = null, reconnectCallback = null)
    runtime.clearComponentStates()
    runtime.resetReconnectAttempts()
    LogManager.d(LogTags.SCRCPY_CLIENT, "Stop session monitor: $deviceIdentifier")
}

internal fun Session.handleAdbConnecting() {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.RUNNING, AdbTexts.ADB_CONNECTING.get())
    runtime.updateSessionState(SessionState.AdbConnecting)
}

internal fun Session.handleAdbVerifying() {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.RUNNING, AdbTexts.ADB_VERIFYING.get())
}

internal fun Session.handleAdbConnected(context: AdbConnectionContext) {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.SUCCESS, AdbTexts.ADB_CONNECTED.get())
    runtime.updateSessionState(SessionState.AdbConnected(context))
    runtime.updateComponentState(SessionComponent.AdbConnection, ComponentState.Connected)
    LogManager.d(LogTags.SCRCPY_CLIENT, "ADB connected: deviceId=${context.deviceId}, serial=${context.serial}")
}

internal fun Session.handleAdbDisconnected(issue: AdbIssue) {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.FAILED, issue.progressMessage())
    runtime.updateSessionState(SessionState.AdbDisconnected(issue))
    runtime.updateComponentState(SessionComponent.AdbConnection, ComponentState.Disconnected)
}

internal fun Session.handleServerPushing(context: ServerPushContext) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_PUSHING_SERVER.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "Pushing scrcpy-server: ${context.startedSummary()}")
}

internal fun Session.handleServerPushed(context: ServerPushContext) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_PUSHED.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "scrcpy-server push completed: ${context.completedSummary()}")
}

internal fun Session.handleServerPushFailed(issue: ServerIssue) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.FAILED, issue.pushFailedProgressMessage())
    runtime.updateSessionState(SessionState.ServerFailed(issue))
}

internal fun Session.handleServerStarting() {
    runtime.updateProgress(ConnectionStep.START_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_STARTING_SERVER.get())
    runtime.updateSessionState(SessionState.ServerStarting)
}

internal fun Session.handleServerStarted(context: ServerStartContext) {
    runtime.updateProgress(ConnectionStep.START_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_STARTED.get())
    runtime.updateSessionState(SessionState.ServerStarted(context))
    runtime.updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Running)
    LogManager.d(LogTags.SCRCPY_CLIENT, "scrcpy-server started: scid=${context.scid}")
}

internal fun Session.handleServerFailed(issue: ServerIssue) {
    if (hasPendingDecoderResolutionRecovery() && issue.kind == ServerIssueKind.ProcessExited) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "scrcpy-server exited while waiting for video size recovery confirmation")
        return
    }
    runtime.updateProgress(ConnectionStep.START_SERVER, StepStatus.FAILED, issue.startFailedProgressMessage())
    runtime.updateSessionState(SessionState.ServerFailed(issue))
    runtime.updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Error)
}

internal fun Session.handleForwardSetting() {
    runtime.updateProgress(ConnectionStep.ADB_FORWARD, StepStatus.RUNNING, RemoteTexts.REMOTE_SETTING_FORWARD.get())
}

internal fun Session.handleForwardSetup(
    localPort: Int,
    remoteSocket: String,
    context: ForwardSetupContext,
) {
    runtime.updateProgress(
        ConnectionStep.ADB_FORWARD,
        StepStatus.SUCCESS,
        context.progressMessage(localPort, remoteSocket),
    )
    LogManager.d(LogTags.SCRCPY_CLIENT, "Forward has been created: ${context.logSummary(localPort, remoteSocket)}")
}

internal fun handleForwardRemoved(
    localPort: Int,
    context: ForwardRemovalContext,
) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "Forward Removed: ${context.summary(localPort)}")
}

internal fun Session.handleForwardFailed(issue: ForwardIssue) {
    runtime.updateProgress(ConnectionStep.ADB_FORWARD, StepStatus.FAILED, issue.progressMessage())
    LogManager.e(LogTags.SCRCPY_CLIENT, "Forward creation failed: ${issue.summary()}")
}

internal fun Session.handleSessionError(issue: SessionIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "Session error: ${issue.message}")
    runtime.updateSessionState(SessionState.Failed(issue))
}

internal fun Session.handleRequestReconnect(issue: ReconnectIssue) {
    val reason = issue.message
    if (hasPendingDecoderResolutionRecovery()) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "Automatic reconnect is paused while waiting for video size recovery confirmation: $reason")
        return
    }
    when (runtime.sessionState.value) {
        is SessionState.Reconnecting -> {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "The reconnection request already exists, ignore the repeated trigger: $reason",
            )
            return
        }
        is SessionState.Failed -> {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "Session failed, reconnection request ignored: $reason",
            )
            return
        }
        else -> {}
    }

    val currentAttempts = runtime.reconnectAttempts()
    if (currentAttempts >= ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "The number of reconnections has reached the upper limit, stop reconnecting.")
        runtime.updateSessionState(
            SessionState.Failed(
                SessionIssue(
                    kind = SessionIssueKind.RuntimeFailure,
                    detail = reason,
                ),
            ),
        )
        return
    }

    runtime.incrementReconnectAttempts()
    val newAttempts = runtime.reconnectAttempts()
    runtime.updateSessionState(
        SessionState.Reconnecting(
            ReconnectStateContext(
                attempt = newAttempts,
                issue = issue,
            ),
        ),
    )
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "Request to reconnect (try $newAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}): $reason",
    )

    runtime.invokeReconnectCallback()
}

internal fun Session.handleRequestCleanup(trigger: CleanupTrigger) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "Request to clear session: trigger=${trigger.logLabel}")
    runtime.updateSessionState(SessionState.Idle)
    runtime.updateSocketExpectation(
        expectedSocketCount = 3,
        audioEnabled = true,
    )
    runtime.clearComponentStates()
    runtime.resetReconnectAttempts()
}

internal fun Session.handleDecoderStarted(decoderType: DecoderType) {
    if (decoderType == DecoderType.Video) {
        clearDecoderResolutionRecovery()
    }
    runtime.updateComponentState(decoderType.toComponent(), ComponentState.Running)
    LogManager.d(LogTags.SCRCPY_CLIENT, "Decoder started: ${decoderType.name}")
}

internal fun Session.handleDecoderStopped(decoderType: DecoderType) {
    runtime.updateComponentState(decoderType.toComponent(), ComponentState.Stopped)
    LogManager.d(LogTags.SCRCPY_CLIENT, "Decoder stopped: ${decoderType.name}")
}

internal fun Session.handleDecoderError(issue: DecoderIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, issue.logMessage())

    if (issue.kind == DecoderIssueKind.UnsupportedSize) {
        val failedLongEdge =
            listOfNotNull(issue.width, issue.height)
                .filter { it > 0 }
                .maxOrNull()
        val suggestedMaxSize =
            nextVideoRecoveryMaxSize(
                currentMaxSize = options.config.maxSize,
                failedLongEdge = failedLongEdge,
            )
        if (suggestedMaxSize == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Decoding size is not supported and no recovery size is available")
            return
        }

        val currentOptions = options
        if (currentOptions.config.maxSize in 1..suggestedMaxSize) {
            LogManager.e(
                LogTags.SCRCPY_CLIENT,
                "The recommended downgrade size is not lower than the current maxSize, stop retrying: current=${currentOptions.config.maxSize} suggested=$suggestedMaxSize",
            )
            return
        }
        publishDecoderResolutionRecovery(
            DecoderResolutionRecoveryRequest(
                width = issue.width ?: 0,
                height = issue.height ?: 0,
                suggestedMaxSize = suggestedMaxSize,
                decoderName = currentOptions.getFinalVideoDecoder(),
            ),
        )
        LogManager.w(
            LogTags.SCRCPY_CLIENT,
            "Decoder size recovery requires user confirmation: decoder=${currentOptions.getFinalVideoDecoder()} size=${issue.width}x${issue.height} suggestedMaxSize=$suggestedMaxSize",
        )
        return
    }

    if (runtime.sessionState.value is SessionState.Connected) {
        handleRequestReconnect(
            ReconnectIssue(
                kind = ReconnectIssueKind.DecoderError,
                detail = issue.reconnectDetail(),
            ),
        )
    }

    LogManager.e(LogTags.SCRCPY_CLIENT, "Decoder error details: ${issue.summary()}")
}

internal fun Session.handleConfirmDecoderResolutionRecovery() {
    val request = consumeDecoderResolutionRecovery() ?: return
    if (!runtime.tryConsumeDecoderRecoveryAttempt(MAX_DECODER_RECOVERY_ATTEMPTS)) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "Decoder size recovery has reached the retry limit")
        return
    }

    val currentOptions = options
    if (currentOptions.config.maxSize in 1..request.suggestedMaxSize) {
        LogManager.w(
            LogTags.SCRCPY_CLIENT,
            "Ignore stale decoder size recovery confirmation: current=${currentOptions.config.maxSize} suggested=${request.suggestedMaxSize}",
        )
        return
    }

    setOptions(currentOptions.copy(config = currentOptions.config.copy(maxSize = request.suggestedMaxSize)))
    LogManager.w(
        LogTags.SCRCPY_CLIENT,
        "User confirmed decoder size recovery: decoder=${request.decoderName} size=${request.width}x${request.height} maxSize=${request.suggestedMaxSize}; reconnecting without changing persistent configuration",
    )
    handleRequestReconnect(
        ReconnectIssue(
            kind = ReconnectIssueKind.DecoderError,
            detail = "User approved decoder size recovery to maxSize=${request.suggestedMaxSize}",
        ),
    )
}

internal fun Session.handleDismissDecoderResolutionRecovery() {
    val request = consumeDecoderResolutionRecovery() ?: return
    LogManager.i(
        LogTags.SCRCPY_CLIENT,
        "User dismissed decoder size recovery: decoder=${request.decoderName} size=${request.width}x${request.height} suggestedMaxSize=${request.suggestedMaxSize}",
    )
    if (request.source == VideoResolutionRecoverySource.ServerCapture) {
        handleSessionError(
            SessionIssue(
                kind = SessionIssueKind.RuntimeFailure,
                detail = request.detail,
            ),
        )
    }
}

internal fun Session.handleServerVideoCaptureError(detail: String) {
    val suggestedMaxSize = nextVideoRecoveryMaxSize(options.config.maxSize)
    if (suggestedMaxSize == null) {
        handleServerFailed(
            ServerIssue(
                kind = ServerIssueKind.RuntimeStdOut,
                detail = detail,
            ),
        )
        return
    }

    publishDecoderResolutionRecovery(
        DecoderResolutionRecoveryRequest(
            width = 0,
            height = 0,
            suggestedMaxSize = suggestedMaxSize,
            decoderName = "",
            source = VideoResolutionRecoverySource.ServerCapture,
            detail = detail,
        ),
    )
    LogManager.w(
        LogTags.SCRCPY_CLIENT,
        "Video capture size recovery requires user confirmation: currentMaxSize=${options.config.maxSize} suggestedMaxSize=$suggestedMaxSize",
    )
}

internal fun nextVideoRecoveryMaxSize(
    currentMaxSize: Int,
    failedLongEdge: Int? = null,
): Int? {
    val upperExclusive =
        when {
            currentMaxSize > 0 -> currentMaxSize
            failedLongEdge != null && failedLongEdge > 0 -> failedLongEdge
            else -> Int.MAX_VALUE
        }
    return VIDEO_RECOVERY_MAX_SIZE_TIERS.firstOrNull { it < upperExclusive }
}

private const val MAX_DECODER_RECOVERY_ATTEMPTS = 3
private val VIDEO_RECOVERY_MAX_SIZE_TIERS = listOf(1920, 1080, 720)

internal fun handleVideoEncoderDetecting(context: CodecDetectionContext) {
    val source = if (context.reusedUploadedServer) "reused uploaded server" else "pushed server again"
    LogManager.d(LogTags.SCRCPY_CLIENT, "Detecting video encoder... source=$source")
}

internal fun handleVideoEncoderDetected(summary: CodecDetectionSummary) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "Video encoder detection completed: count=${summary.totalCount}, sample=${summary.sampleNames.joinToString()}, reusedServer=${summary.reusedUploadedServer}",
    )
}

internal fun handleVideoEncoderDetectFailed(issue: CodecIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "Video encoder detection failed: ${issue.message}")
}

internal fun handleVideoEncoderError(issue: CodecIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "Video encoder error: ${issue.message}")
}

internal fun handleAudioEncoderDetecting(context: CodecDetectionContext) {
    val source = if (context.reusedUploadedServer) "reused uploaded server" else "pushed server again"
    LogManager.d(LogTags.SCRCPY_CLIENT, "Detecting audio encoder... source=$source")
}

internal fun handleAudioEncoderDetected(summary: CodecDetectionSummary) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "Audio encoder detection completed: count=${summary.totalCount}, sample=${summary.sampleNames.joinToString()}, reusedServer=${summary.reusedUploadedServer}",
    )
}

internal fun handleAudioEncoderError(issue: CodecIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "Audio encoder error: ${issue.message}")
}

internal fun DecoderType.toComponent(): SessionComponent =
    when (this) {
        DecoderType.Video -> SessionComponent.VideoDecoder
        DecoderType.Audio -> SessionComponent.AudioDecoder
    }

internal fun Session.handleSocketConnecting(context: SocketConnectingContext) {
    runtime.updateSocketExpectation(
        expectedSocketCount = context.expectedSocketCount,
        audioEnabled = context.audioEnabled,
    )
    runtime.updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.RUNNING, RemoteTexts.REMOTE_CONNECTING_SOCKET.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "Start connecting socket: ${context.summary()}")
}

internal fun Session.handleSocketConnected(
    socketType: SocketType,
    context: SocketConnectContext,
) {
    val componentSnapshot = runtime.updateComponentState(socketType.toComponent(), ComponentState.Connected)
    LogManager.d(LogTags.SCRCPY_CLIENT, "Socket connected: ${context.summary(socketType)}")

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
    runtime.updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.FAILED, issue.progressMessage())
    LogManager.e(LogTags.SCRCPY_CLIENT, "Socket error: ${issue.summary()}")
}

internal fun SocketType.toComponent(): SessionComponent =
    when (this) {
        SocketType.Video -> SessionComponent.VideoSocket
        SocketType.Audio -> SessionComponent.AudioSocket
        SocketType.Control -> SessionComponent.ControlSocket
    }

internal fun AdbIssue.progressMessage(): String = "${AdbTexts.ADB_DISCONNECTED.get()}: $message"

internal fun ServerIssue.pushFailedProgressMessage(): String = "${RemoteTexts.REMOTE_PUSH_FAILED.get()}: $message"

internal fun ServerIssue.startFailedProgressMessage(): String = "${RemoteTexts.REMOTE_START_FAILED.get()}: $message"

internal fun ForwardSetupContext.progressMessage(
    localPort: Int,
    remoteSocket: String,
): String = "${RemoteTexts.REMOTE_FORWARD_SETUP.get()}: ${targetSummary(localPort, remoteSocket)}"

internal fun ForwardIssue.progressMessage(): String =
    "${RemoteTexts.REMOTE_FORWARD_FAILED.get()}: ${targetSummary()}: $message"

internal fun SocketIssue.progressMessage(): String =
    "${RemoteTexts.REMOTE_SOCKET_ERROR.get()}: $socketType - $message"

internal fun DecoderIssue.logMessage(): String = "Decoder error[${decoderType.name}]: $message"

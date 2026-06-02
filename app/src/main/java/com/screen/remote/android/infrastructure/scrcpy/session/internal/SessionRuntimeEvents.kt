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
import com.screen.remote.android.infrastructure.scrcpy.session.model.CleanupContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.CodecDetectionContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.CodecDetectionSummary
import com.screen.remote.android.infrastructure.scrcpy.session.model.CodecIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ComponentState
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderType
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardSetupContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectStateContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerPushContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerStartContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
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

internal suspend fun Session.processEvent(event: SessionEvent) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "处理事件: $event")

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
        is SessionEvent.RequestReconnect -> handleRequestReconnect(event.issue)
        is SessionEvent.RequestCleanup -> handleRequestCleanup(event.context)
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

fun Session.createMonitorBus() {
    resources.replaceMonitorBus(deviceIdentifier)
}

fun Session.initMonitor(
    stateMachine: ConnectionStateMachine,
    onReconnect: () -> Unit,
) {
    runtime.bind(stateMachine, onReconnect)
    LogManager.d(LogTags.SCRCPY_CLIENT, "初始化会话监控器")
}

fun Session.stopMonitor() {
    runtime.bind(stateMachine = null, reconnectCallback = null)
    runtime.clearComponentStates()
    runtime.resetReconnectAttempts()
    LogManager.d(LogTags.SCRCPY_CLIENT, "停止会话监控器: $deviceIdentifier")
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
    LogManager.d(LogTags.SCRCPY_CLIENT, "ADB 已连接: deviceId=${context.deviceId}, serial=${context.serial}")
}

internal fun Session.handleAdbDisconnected(issue: AdbIssue) {
    runtime.updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.FAILED, issue.progressMessage())
    runtime.updateSessionState(SessionState.AdbDisconnected(issue))
    runtime.updateComponentState(SessionComponent.AdbConnection, ComponentState.Disconnected)
}

internal fun Session.handleServerPushing(context: ServerPushContext) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_PUSHING_SERVER.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在推送 scrcpy-server: ${context.startedSummary()}")
}

internal fun Session.handleServerPushed(context: ServerPushContext) {
    runtime.updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_PUSHED.get())
    LogManager.d(LogTags.SCRCPY_CLIENT, "scrcpy-server 推送完成: ${context.completedSummary()}")
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
    LogManager.d(LogTags.SCRCPY_CLIENT, "scrcpy-server 已启动: scid=${context.scid}")
}

internal fun Session.handleServerFailed(issue: ServerIssue) {
    runtime.updateProgress(ConnectionStep.START_SERVER, StepStatus.FAILED, issue.startFailedProgressMessage())
    runtime.updateSessionState(SessionState.ServerFailed(issue))
    runtime.updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Error(issue.message))
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
    LogManager.d(LogTags.SCRCPY_CLIENT, "Forward 已建立: ${context.logSummary(localPort, remoteSocket)}")
}

internal fun Session.handleForwardRemoved(
    localPort: Int,
    context: ForwardRemovalContext,
) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "Forward 已移除: ${context.summary(localPort)}")
}

internal fun Session.handleForwardFailed(issue: ForwardIssue) {
    runtime.updateProgress(ConnectionStep.ADB_FORWARD, StepStatus.FAILED, issue.progressMessage())
    LogManager.e(LogTags.SCRCPY_CLIENT, "Forward 建立失败: ${issue.summary()}")
}

internal fun Session.handleSessionError(issue: SessionIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "会话错误: ${issue.message}")
    runtime.updateSessionState(SessionState.Failed(issue))
}

internal fun Session.handleRequestReconnect(issue: ReconnectIssue) {
    val reason = issue.message
    when (runtime.sessionState.value) {
        is SessionState.Reconnecting -> {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "重连请求已存在，忽略重复触发: $reason",
            )
            return
        }
        is SessionState.Failed -> {
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "会话已失败，忽略重连请求: $reason",
            )
            return
        }
        else -> {}
    }

    val currentAttempts = runtime.reconnectAttempts()
    if (currentAttempts >= ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "重连次数已达上限，停止重连")
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
        "请求重连 (尝试 $newAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}): $reason",
    )

    runtime.invokeReconnectCallback()
}

internal fun Session.handleRequestCleanup(context: CleanupContext) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "请求清理会话: ${context.summary()}")
    runtime.updateSessionState(SessionState.Idle)
    runtime.updateSocketExpectation(
        expectedSocketCount = 3,
        audioEnabled = true,
    )
    runtime.clearComponentStates()
    runtime.resetReconnectAttempts()
}

internal fun Session.handleDecoderStarted(decoderType: DecoderType) {
    runtime.updateComponentState(decoderType.toComponent(), ComponentState.Running)
    LogManager.d(LogTags.SCRCPY_CLIENT, "解码器已启动: ${decoderType.name}")
}

internal fun Session.handleDecoderStopped(decoderType: DecoderType) {
    runtime.updateComponentState(decoderType.toComponent(), ComponentState.Stopped)
    LogManager.d(LogTags.SCRCPY_CLIENT, "解码器已停止: ${decoderType.name}")
}

internal suspend fun Session.handleDecoderError(issue: DecoderIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, issue.logMessage())

    if (issue.kind == DecoderIssueKind.UnsupportedSize) {
        val suggestedMaxSize = issue.suggestedMaxSize
        if (suggestedMaxSize == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "解码尺寸不受支持，且没有可用的自动降级尺寸")
            return
        }
        if (!runtime.tryConsumeDecoderRecoveryAttempt(MAX_DECODER_RECOVERY_ATTEMPTS)) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "解码自动降级已达到上限，停止重试")
            return
        }

        val currentOptions = options
        if (currentOptions.maxSize in 1..suggestedMaxSize) {
            LogManager.e(
                LogTags.SCRCPY_CLIENT,
                "建议降级尺寸未低于当前 maxSize，停止重试: current=${currentOptions.maxSize} suggested=$suggestedMaxSize",
            )
            return
        }
        setOptions(currentOptions.copy(maxSize = suggestedMaxSize))
        LogManager.w(
            LogTags.SCRCPY_CLIENT,
            "解码器不支持 ${issue.width}x${issue.height}，本次运行降级 maxSize=$suggestedMaxSize 后重连（不写入持久化配置）",
        )
    }

    if (runtime.sessionState.value is SessionState.Connected) {
        handleRequestReconnect(
            ReconnectIssue(
                kind = ReconnectIssueKind.DecoderError,
                detail = issue.reconnectDetail(),
            ),
        )
    }

    LogManager.e(LogTags.SCRCPY_CLIENT, "解码器错误详情: ${issue.summary()}")
}

private const val MAX_DECODER_RECOVERY_ATTEMPTS = 2

internal fun Session.handleVideoEncoderDetecting(context: CodecDetectionContext) {
    val source = if (context.reusedUploadedServer) "复用已上传 server" else "重新 push server"
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在检测视频编码器... source=$source")
}

internal fun Session.handleVideoEncoderDetected(summary: CodecDetectionSummary) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "视频编码器检测完成: count=${summary.totalCount}, sample=${summary.sampleNames.joinToString()}, reusedServer=${summary.reusedUploadedServer}",
    )
}

internal fun Session.handleVideoEncoderDetectFailed(issue: CodecIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "视频编码器检测失败: ${issue.message}")
}

internal fun Session.handleVideoEncoderError(issue: CodecIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "视频编码器错误: ${issue.message}")
}

internal fun Session.handleAudioEncoderDetecting(context: CodecDetectionContext) {
    val source = if (context.reusedUploadedServer) "复用已上传 server" else "重新 push server"
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在检测音频编码器... source=$source")
}

internal fun Session.handleAudioEncoderDetected(summary: CodecDetectionSummary) {
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "音频编码器检测完成: count=${summary.totalCount}, sample=${summary.sampleNames.joinToString()}, reusedServer=${summary.reusedUploadedServer}",
    )
}

internal fun Session.handleAudioEncoderError(issue: CodecIssue) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "音频编码器错误: ${issue.message}")
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
    runtime.updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.FAILED, issue.progressMessage())
    LogManager.e(LogTags.SCRCPY_CLIENT, "Socket 错误: ${issue.summary()}")
}

internal fun SocketType.toComponent(): SessionComponent =
    when (this) {
        SocketType.Video -> SessionComponent.VideoSocket
        SocketType.Audio -> SessionComponent.AudioSocket
        SocketType.Control -> SessionComponent.ControlSocket
    }

internal fun AdbIssue.progressMessage(): String = "${AdbTexts.ADB_DISCONNECTED.get()}: ${message}"

internal fun ServerIssue.pushFailedProgressMessage(): String = "${RemoteTexts.REMOTE_PUSH_FAILED.get()}: ${message}"

internal fun ServerIssue.startFailedProgressMessage(): String = "${RemoteTexts.REMOTE_START_FAILED.get()}: ${message}"

internal fun ForwardSetupContext.progressMessage(
    localPort: Int,
    remoteSocket: String,
): String = "${RemoteTexts.REMOTE_FORWARD_SETUP.get()}: ${targetSummary(localPort, remoteSocket)}"

internal fun ForwardIssue.progressMessage(): String =
    "${RemoteTexts.REMOTE_FORWARD_FAILED.get()}: ${targetSummary()}: $message"

internal fun SocketIssue.progressMessage(): String =
    "${RemoteTexts.REMOTE_SOCKET_ERROR.get()}: ${socketType} - $message"

internal fun DecoderIssue.logMessage(): String = "解码器错误[${decoderType.name}]: $message"

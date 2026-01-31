/**
 * 会话事件处理器 - 内部实现
 *
 * 职责：
 * - 处理所有会话事件（ADB、Server、Socket、Decoder 等）
 * - 更新会话状态和组件状态
 * - 管理重连逻辑
 *
 * 设计模式：
 * - 使用扩展函数模式，保持 Session 类的简洁
 * - 所有方法标记为 internal，仅供 Session 内部使用
 * - 事件处理逻辑集中管理，便于维护
 */
package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ConnectionStep
import com.mobile.scrcpy.android.core.domain.model.StepStatus
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.ComponentState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.DecoderType
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.Session
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionComponent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SocketType

/**
 * 处理事件（内部实现）
 */
internal suspend fun Session.processEvent(event: SessionEvent) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "处理事件: $event")

    when (event) {
        // ADB 事件
        is SessionEvent.AdbConnecting -> handleAdbConnecting()

        is SessionEvent.AdbVerifying -> handleAdbVerifying()

        is SessionEvent.AdbConnected -> handleAdbConnected()

        is SessionEvent.AdbDisconnected -> handleAdbDisconnected(event.message)

        // Server 事件
        is SessionEvent.ServerPushing -> handleServerPushing()

        is SessionEvent.ServerPushed -> handleServerPushed()

        is SessionEvent.ServerPushFailed -> handleServerPushFailed(event.message)

        is SessionEvent.ServerStarting -> handleServerStarting()

        is SessionEvent.ServerStarted -> handleServerStarted()

        is SessionEvent.ServerFailed -> handleServerFailed(event.message)

        // Forward 事件
        is SessionEvent.ForwardSetting -> handleForwardSetting()

        is SessionEvent.ForwardSetup -> handleForwardSetup(event.message)

        is SessionEvent.ForwardRemoved -> handleForwardRemoved(event.message)

        is SessionEvent.ForwardFailed -> handleForwardFailed(event.message)

        // Socket 事件
        is SessionEvent.SocketConnecting -> handleSocketConnecting()

        is SessionEvent.SocketConnected -> handleSocketConnected(event.message)

        is SessionEvent.SocketDisconnected -> handleSocketDisconnected(event.message)

        is SessionEvent.SocketError -> handleSocketError(event.message)

        // 解码器事件
        is SessionEvent.DecoderStarted -> handleDecoderStarted(event.message)

        is SessionEvent.DecoderStopped -> handleDecoderStopped(event.message)

        is SessionEvent.DecoderError -> handleDecoderError(event.message)

        // 控制事件
        is SessionEvent.RequestReconnect -> handleRequestReconnect(event.message)

        is SessionEvent.RequestCleanup -> handleRequestCleanup()

        // Codec 事件
        is SessionEvent.VideoEncoderDetecting -> handleVideoEncoderDetecting()

        is SessionEvent.VideoEncoderDetected -> handleVideoEncoderDetected()

        is SessionEvent.VideoEncoderDetectFailed -> handleVideoEncoderDetectFailed(event.message)

        is SessionEvent.VideoEncoderError -> handleVideoEncoderError(event.message)

        is SessionEvent.AudioEncoderDetecting -> handleAudioEncoderDetecting()

        is SessionEvent.AudioEncoderDetected -> handleAudioEncoderDetected()

        is SessionEvent.AudioEncoderError -> handleAudioEncoderError(event.message)

        // Session 事件
        is SessionEvent.SessionError -> handleSessionError(event.message)
    }
}

// ========== ADB 事件处理 ==========

internal fun Session.handleAdbConnecting() {
    updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.RUNNING, AdbTexts.ADB_CONNECTING.get())
    updateSessionState(SessionState.AdbConnecting)
}

internal fun Session.handleAdbVerifying() {
    updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.RUNNING, AdbTexts.ADB_VERIFYING.get())
}

internal fun Session.handleAdbConnected() {
    updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.SUCCESS, AdbTexts.ADB_CONNECTED.get())
    updateSessionState(SessionState.AdbConnected)
    updateComponentState(SessionComponent.AdbConnection, ComponentState.Connected)
}

internal fun Session.handleAdbDisconnected(message: String) {
    updateProgress(ConnectionStep.ADB_CONNECT, StepStatus.FAILED, "${AdbTexts.ADB_DISCONNECTED.get()}: $message")
    updateSessionState(SessionState.AdbDisconnected(message))
    updateComponentState(SessionComponent.AdbConnection, ComponentState.Disconnected)
}

// ========== Server 事件处理 ==========

internal fun Session.handleServerPushing() {
    updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_PUSHING_SERVER.get())
}

internal fun Session.handleServerPushed() {
    updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_PUSHED.get())
}

internal fun Session.handleServerPushFailed(message: String) {
    updateProgress(ConnectionStep.PUSH_SERVER, StepStatus.FAILED, "${RemoteTexts.REMOTE_PUSH_FAILED.get()}: $message")
    updateSessionState(SessionState.ServerFailed(message))
}

internal fun Session.handleServerStarting() {
    updateProgress(ConnectionStep.START_SERVER, StepStatus.RUNNING, RemoteTexts.REMOTE_STARTING_SERVER.get())
    updateSessionState(SessionState.ServerStarting)
}

internal fun Session.handleServerStarted() {
    updateProgress(ConnectionStep.START_SERVER, StepStatus.SUCCESS, RemoteTexts.REMOTE_SERVER_STARTED.get())
    updateSessionState(SessionState.ServerStarted)
    updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Running)
}

internal fun Session.handleServerFailed(message: String) {
    updateProgress(
        ConnectionStep.START_SERVER,
        StepStatus.FAILED,
        "${RemoteTexts.REMOTE_START_FAILED.get()}: $message",
    )
    updateSessionState(SessionState.ServerFailed(message))
    updateComponentState(SessionComponent.ScrcpyServer, ComponentState.Error(message))
}

// ========== Forward 事件处理 ==========

internal fun Session.handleForwardSetting() {
    updateProgress(ConnectionStep.ADB_FORWARD, StepStatus.RUNNING, RemoteTexts.REMOTE_SETTING_FORWARD.get())
}

internal fun Session.handleForwardSetup(message: String) {
    updateProgress(
        ConnectionStep.ADB_FORWARD,
        StepStatus.SUCCESS,
        "${RemoteTexts.REMOTE_FORWARD_SETUP.get()}: $message",
    )
}

internal fun Session.handleForwardRemoved(message: String) {
    LogManager.d(LogTags.SCRCPY_CLIENT, "Forward 已移除: $message")
}

internal fun Session.handleForwardFailed(message: String) {
    updateProgress(
        ConnectionStep.ADB_FORWARD,
        StepStatus.FAILED,
        "${RemoteTexts.REMOTE_FORWARD_FAILED.get()}: $message",
    )
}

// ========== Socket 事件处理 ==========

internal fun Session.handleSocketConnecting() {
    updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.RUNNING, RemoteTexts.REMOTE_CONNECTING_SOCKET.get())
}

internal fun Session.handleSocketConnected(message: String) {
    // 从消息中解析 socket 类型（格式：Video/Audio/Control）
    val socketType =
        when {
            message.contains("Video", ignoreCase = true) -> SocketType.Video
            message.contains("Audio", ignoreCase = true) -> SocketType.Audio
            message.contains("Control", ignoreCase = true) -> SocketType.Control
            else -> null
        }

    socketType?.let {
        val component =
            when (it) {
                SocketType.Video -> SessionComponent.VideoSocket
                SocketType.Audio -> SessionComponent.AudioSocket
                SocketType.Control -> SessionComponent.ControlSocket
            }
        updateComponentState(component, ComponentState.Connected)
    }

    // 检查是否所有 Socket 都已连接
    val allConnected =
        listOf(
            SessionComponent.VideoSocket,
            SessionComponent.AudioSocket,
            SessionComponent.ControlSocket,
        ).all { getComponentState(it) == ComponentState.Connected }

    if (allConnected) {
        updateProgress(ConnectionStep.CONNECT_SOCKET, StepStatus.SUCCESS, RemoteTexts.REMOTE_SOCKET_CONNECTED.get())
        updateSessionState(SessionState.Connected)
    }
}

internal fun Session.handleSocketDisconnected(message: String) {
    // 从消息中解析 socket 类型
    val socketType =
        when {
            message.contains("Video", ignoreCase = true) -> SocketType.Video
            message.contains("Audio", ignoreCase = true) -> SocketType.Audio
            message.contains("Control", ignoreCase = true) -> SocketType.Control
            else -> null
        }

    socketType?.let {
        val component =
            when (it) {
                SocketType.Video -> SessionComponent.VideoSocket
                SocketType.Audio -> SessionComponent.AudioSocket
                SocketType.Control -> SessionComponent.ControlSocket
            }
        updateComponentState(component, ComponentState.Disconnected)
    }

    // Socket 断开时触发重连
    if (getCurrentState() is SessionState.Connected) {
        handleRequestReconnect(message)
    }
}

internal fun Session.handleSocketError(message: String) {
    updateProgress(
        ConnectionStep.CONNECT_SOCKET,
        StepStatus.FAILED,
        "${RemoteTexts.REMOTE_SOCKET_ERROR.get()}: $message",
    )
}

// ========== 解码器事件处理 ==========

internal fun Session.handleDecoderStarted(message: String) {
    // 从消息中解析解码器类型
    val decoderType =
        when {
            message.contains("Video", ignoreCase = true) -> DecoderType.Video
            message.contains("Audio", ignoreCase = true) -> DecoderType.Audio
            else -> null
        }

    decoderType?.let {
        val component =
            when (it) {
                DecoderType.Video -> SessionComponent.VideoDecoder
                DecoderType.Audio -> SessionComponent.AudioDecoder
            }
        updateComponentState(component, ComponentState.Running)
    }
    LogManager.d(LogTags.SCRCPY_CLIENT, "解码器已启动: $message")
}

internal fun Session.handleDecoderStopped(message: String) {
    // 从消息中解析解码器类型
    val decoderType =
        when {
            message.contains("Video", ignoreCase = true) -> DecoderType.Video
            message.contains("Audio", ignoreCase = true) -> DecoderType.Audio
            else -> null
        }

    decoderType?.let {
        val component =
            when (it) {
                DecoderType.Video -> SessionComponent.VideoDecoder
                DecoderType.Audio -> SessionComponent.AudioDecoder
            }
        updateComponentState(component, ComponentState.Stopped)
    }
    LogManager.d(LogTags.SCRCPY_CLIENT, "解码器已停止: $message")
}

internal fun Session.handleDecoderError(message: String) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "解码器错误: $message")

    // 解码器错误时触发重连
    if (getCurrentState() is SessionState.Connected) {
        handleRequestReconnect(message)
    }
}

// ========== Codec 事件处理 ==========

internal fun Session.handleVideoEncoderDetecting() {
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在检测视频编码器...")
}

internal fun Session.handleVideoEncoderDetected() {
    LogManager.d(LogTags.SCRCPY_CLIENT, "视频编码器检测完成")
}

internal fun Session.handleVideoEncoderDetectFailed(message: String) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "视频编码器检测失败: $message")
}

internal fun Session.handleVideoEncoderError(message: String) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "视频编码器错误: $message")
}

internal fun Session.handleAudioEncoderDetecting() {
    LogManager.d(LogTags.SCRCPY_CLIENT, "正在检测音频编码器...")
}

internal fun Session.handleAudioEncoderDetected() {
    LogManager.d(LogTags.SCRCPY_CLIENT, "音频编码器检测完成")
}

internal fun Session.handleAudioEncoderError(message: String) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "音频编码器错误: $message")
}

// ========== Session 事件处理 ==========

internal fun Session.handleSessionError(message: String) {
    LogManager.e(LogTags.SCRCPY_CLIENT, "会话错误: $message")
    updateSessionState(SessionState.Failed(message))
}

// ========== 控制事件处理 ==========

internal fun Session.handleRequestReconnect(reason: String) {
    val currentAttempts = getReconnectAttempts()
    if (currentAttempts >= ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "重连次数已达上限，停止重连")
        updateSessionState(SessionState.Failed(reason))
        return
    }

    incrementReconnectAttempts()
    val newAttempts = getReconnectAttempts()
    updateSessionState(SessionState.Reconnecting(newAttempts))
    LogManager.d(
        LogTags.SCRCPY_CLIENT,
        "请求重连 (尝试 $newAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}): $reason",
    )

    // 调用重连回调
    invokeReconnectCallback()
}

internal fun Session.handleRequestCleanup() {
    LogManager.d(LogTags.SCRCPY_CLIENT, "请求清理会话")
    updateSessionState(SessionState.Idle)
    clearComponentStates()
    resetReconnectAttempts()
}

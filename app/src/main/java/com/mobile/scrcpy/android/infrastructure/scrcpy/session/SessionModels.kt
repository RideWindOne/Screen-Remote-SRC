package com.mobile.scrcpy.android.infrastructure.scrcpy.session

/**
 * 会话事件（纯文本类型）
 */
sealed class SessionEvent {
    // ADB 事件
    data object AdbConnecting : SessionEvent()
    data object AdbVerifying : SessionEvent()
    data object AdbConnected : SessionEvent()
    data class AdbDisconnected(val message: String) : SessionEvent()

    // Server 事件
    data object ServerPushing : SessionEvent()
    data object ServerPushed : SessionEvent()
    data class ServerPushFailed(val message: String) : SessionEvent()
    data object ServerStarting : SessionEvent()
    data object ServerStarted : SessionEvent()
    data class ServerFailed(val message: String) : SessionEvent()

    // Forward 事件
    data object ForwardSetting : SessionEvent()
    data class ForwardSetup(val message: String) : SessionEvent()
    data class ForwardRemoved(val message: String) : SessionEvent()
    data class ForwardFailed(val message: String) : SessionEvent()

    // Socket 事件
    data object SocketConnecting : SessionEvent()
    data class SocketConnected(val message: String) : SessionEvent()
    data class SocketDisconnected(val message: String) : SessionEvent()
    data class SocketError(val message: String) : SessionEvent()

    // 解码器事件
    data class DecoderStarted(val message: String) : SessionEvent()
    data class DecoderStopped(val message: String) : SessionEvent()
    data class DecoderError(val message: String) : SessionEvent()

    // 控制事件
    data class RequestReconnect(val message: String) : SessionEvent()
    data object RequestCleanup : SessionEvent()

    // Codec 事件
    data object VideoEncoderDetecting : SessionEvent()
    data object VideoEncoderDetected : SessionEvent()
    data class VideoEncoderDetectFailed(val message: String) : SessionEvent()
    data class VideoEncoderError(val message: String) : SessionEvent()
    data object AudioEncoderDetecting : SessionEvent()
    data object AudioEncoderDetected : SessionEvent()
    data class AudioEncoderError(val message: String) : SessionEvent()

    // Session 事件
    data class SessionError(val message: String) : SessionEvent()
}

/**
 * 会话状态
 */
sealed class SessionState {
    data object Idle : SessionState()

    data object AdbConnecting : SessionState()

    data object AdbConnected : SessionState()

    data class AdbDisconnected(
        val reason: String,
    ) : SessionState()

    data object ServerStarting : SessionState()

    data object ServerStarted : SessionState()

    data class ServerFailed(
        val error: String,
    ) : SessionState()

    data object Connected : SessionState()

    data class Reconnecting(
        val attempt: Int,
    ) : SessionState()

    data class Failed(
        val reason: String,
    ) : SessionState()
}

/**
 * 会话组件
 */
enum class SessionComponent {
    AdbConnection,
    ScrcpyServer,
    VideoSocket,
    AudioSocket,
    ControlSocket,
    VideoDecoder,
    AudioDecoder,
}

/**
 * 组件状态
 */
sealed class ComponentState {
    data object Idle : ComponentState()

    data object Starting : ComponentState()

    data object Running : ComponentState()

    data object Connected : ComponentState()

    data object Stopped : ComponentState()

    data object Disconnected : ComponentState()

    data class Error(
        val message: String,
    ) : ComponentState()
}

/**
 * Socket 类型
 */
enum class SocketType {
    Video,
    Audio,
    Control,
}

/**
 * 解码器类型
 */
enum class DecoderType {
    Video,
    Audio,
}

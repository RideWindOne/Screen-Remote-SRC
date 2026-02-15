package com.mobile.scrcpy.android.infrastructure.scrcpy.session.model

sealed class SessionEvent {
    // ADB 事件
    data object AdbConnecting : SessionEvent()
    data object AdbVerifying : SessionEvent()
    data class AdbConnected(
        val context: AdbConnectionContext,
    ) : SessionEvent()
    data class AdbDisconnected(
        val issue: AdbIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }

    // Server 事件
    data class ServerPushing(
        val context: ServerPushContext,
    ) : SessionEvent()
    data class ServerPushed(
        val context: ServerPushContext,
    ) : SessionEvent()
    data class ServerPushFailed(
        val issue: ServerIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }
    data object ServerStarting : SessionEvent()
    data class ServerStarted(
        val context: ServerStartContext,
    ) : SessionEvent()
    data class ServerFailed(
        val issue: ServerIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }

    // Forward 事件
    data object ForwardSetting : SessionEvent()
    data class ForwardSetup(
        val localPort: Int,
        val remoteSocket: String,
        val context: ForwardSetupContext,
    ) : SessionEvent()
    data class ForwardRemoved(
        val localPort: Int,
        val context: ForwardRemovalContext,
    ) : SessionEvent()
    data class ForwardFailed(
        val issue: ForwardIssue,
    ) : SessionEvent() {
        val localPort: Int
            get() = issue.localPort

        val remoteSocket: String?
            get() = issue.remoteSocket

        val reason: String
            get() = issue.message
    }

    // Socket 事件
    data class SocketConnecting(
        val context: SocketConnectingContext,
    ) : SessionEvent()
    data class SocketConnected(
        val socketType: SocketType,
        val context: SocketConnectContext,
    ) : SessionEvent()
    data class SocketDisconnected(
        val socketType: SocketType,
        val context: SocketDisconnectContext,
    ) : SessionEvent() {
        val reason: String
            get() = context.message
    }
    data class SocketError(
        val issue: SocketIssue,
    ) : SessionEvent() {
        val socketType: SocketType
            get() = issue.socketType

        val reason: String
            get() = issue.message
    }

    // 解码器事件
    data class DecoderStarted(
        val decoderType: DecoderType,
    ) : SessionEvent()
    data class DecoderStopped(
        val decoderType: DecoderType,
    ) : SessionEvent()
    data class DecoderError(
        val issue: DecoderIssue,
    ) : SessionEvent() {
        val decoderType: DecoderType
            get() = issue.decoderType

        val reason: String
            get() = issue.message
    }

    // 控制事件
    data class RequestReconnect(
        val issue: ReconnectIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }
    data class RequestCleanup(
        val context: CleanupContext,
    ) : SessionEvent()

    // Codec 事件
    data class VideoEncoderDetecting(
        val context: CodecDetectionContext,
    ) : SessionEvent()
    data class VideoEncoderDetected(
        val summary: CodecDetectionSummary,
    ) : SessionEvent()
    data class VideoEncoderDetectFailed(
        val issue: CodecIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }
    data class VideoEncoderError(
        val issue: CodecIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }
    data class AudioEncoderDetecting(
        val context: CodecDetectionContext,
    ) : SessionEvent()
    data class AudioEncoderDetected(
        val summary: CodecDetectionSummary,
    ) : SessionEvent()
    data class AudioEncoderError(
        val issue: CodecIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }

    // Session 事件
    data class SessionError(
        val issue: SessionIssue,
    ) : SessionEvent() {
        val message: String
            get() = issue.message
    }
}

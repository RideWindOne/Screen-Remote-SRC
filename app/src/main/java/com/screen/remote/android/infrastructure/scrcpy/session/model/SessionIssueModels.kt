package com.screen.remote.android.infrastructure.scrcpy.session.model

data class AdbIssue(
    val kind: AdbIssueKind,
    val detail: String,
) {
    val message: String
        get() = detail
}

enum class AdbIssueKind {
    VerifyTimeout,
    ConnectionDisconnected,
    HandshakeFailed,
    ConnectionUnavailable,
    ForwardSetupFailed,
    CommandFailed,
    Unknown,
}

data class ServerIssue(
    val kind: ServerIssueKind,
    val detail: String,
    val exitCode: Int? = null,
) {
    val message: String
        get() =
            when {
                exitCode != null -> "$detail (exitCode=$exitCode)"
                else -> detail
            }
}

enum class ServerIssueKind {
    PushFailed,
    StartFailed,
    StartupTimeout,
    StartupStdErr,
    RuntimeStdOut,
    RuntimeStdErr,
    ProcessExited,
    MonitorException,
    CodecSelectionFailed,
    ConnectionFailure,
    Unknown,
}

data class ForwardIssue(
    val kind: ForwardIssueKind,
    val localPort: Int,
    val remoteSocket: String? = null,
    val detail: String,
) {
    val message: String
        get() = detail
}

enum class ForwardIssueKind {
    SetupFailed,
    Unknown,
}

data class SocketIssue(
    val kind: SocketIssueKind,
    val socketType: SocketType,
    val detail: String,
) {
    val message: String
        get() = detail
}

enum class SocketIssueKind {
    ConnectFailed,
    ConnectionLost,
    HealthCheckFailed,
    Unknown,
}

data class DecoderIssue(
    val kind: DecoderIssueKind,
    val decoderType: DecoderType,
    val detail: String,
    val width: Int? = null,
    val height: Int? = null,
    val suggestedMaxSize: Int? = null,
) {
    val message: String
        get() = detail
}

enum class DecoderIssueKind {
    CreateFailed,
    UnsupportedSize,
    ConnectionLost,
    RuntimeError,
    Unknown,
}

data class SessionIssue(
    val kind: SessionIssueKind,
    val detail: String,
) {
    val message: String
        get() = detail
}

enum class SessionIssueKind {
    SessionNotFound,
    RuntimeFailure,
    Unknown,
}

data class ReconnectIssue(
    val kind: ReconnectIssueKind,
    val detail: String,
) {
    val message: String
        get() = detail
}

enum class ReconnectIssueKind {
    SocketDisconnected,
    DecoderError,
    RuntimeError,
    ReconnectFailure,
    Unknown,
}

data class CodecIssue(
    val kind: CodecIssueKind,
    val detail: String,
) {
    val message: String
        get() = detail
}

enum class CodecIssueKind {
    DetectionFailed,
    NoEncodersFound,
    RuntimeError,
    Unknown,
}

data class CodecDetectionContext(
    val reusedUploadedServer: Boolean,
)

data class CodecDetectionSummary(
    val totalCount: Int,
    val sampleNames: List<String>,
    val reusedUploadedServer: Boolean,
)

data class AdbConnectionContext(
    val deviceId: String,
    val serial: String,
)

data class ServerStartContext(
    val scid: Int,
)

data class ConnectedContext(
    val localPort: Int,
    val connectedSockets: Set<SocketType>,
    val dummyByteConfirmed: Boolean,
    val audioEnabled: Boolean,
) {
    val socketCount: Int
        get() = connectedSockets.size
}

enum class CleanupTrigger(
    val logLabel: String,
) {
    UserDisconnect("user_disconnect"),
    CancelConnect("cancel_connect"),
}

data class SocketDisconnectContext(
    val kind: SocketDisconnectKind,
    val detail: String? = null,
) {
    val message: String
        get() = detail ?: kind.name
}

enum class SocketDisconnectKind {
    LocalClose,
    Cleanup,
    RemoteClose,
    Unknown,
}

data class ForwardRemovalContext(
    val remoteSocket: String? = null,
    val trigger: ForwardRemovalTrigger,
)

enum class ForwardRemovalTrigger {
    Disconnect,
    CleanupOldResources,
    ReplaceExisting,
    Unknown,
}

data class ForwardSetupContext(
    val durationMs: Long,
)

data class SocketConnectContext(
    val localPort: Int,
    val dummyByteConfirmed: Boolean,
)

data class ServerPushContext(
    val targetPath: String,
    val durationMs: Long? = null,
)

data class SocketConnectingContext(
    val localPort: Int,
    val expectedSocketCount: Int,
    val audioEnabled: Boolean,
)

data class ReconnectStateContext(
    val attempt: Int,
    val issue: ReconnectIssue,
)

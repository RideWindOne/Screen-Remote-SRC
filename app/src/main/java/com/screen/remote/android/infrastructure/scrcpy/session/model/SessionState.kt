package com.screen.remote.android.infrastructure.scrcpy.session.model

sealed class SessionState {
    data object Idle : SessionState()

    data object AdbConnecting : SessionState()

    data class AdbConnected(
        val context: AdbConnectionContext,
    ) : SessionState() {
        val deviceId: String
            get() = context.deviceId

        val serial: String
            get() = context.serial
    }

    data class AdbDisconnected(
        val issue: AdbIssue,
    ) : SessionState() {
        val reason: String
            get() = issue.message
    }

    data object ServerStarting : SessionState()

    data class ServerStarted(
        val context: ServerStartContext,
    ) : SessionState() {
        val scid: Int
            get() = context.scid
    }

    data class ServerFailed(
        val issue: ServerIssue,
    ) : SessionState() {
        val error: String
            get() = issue.message
    }

    data class Connected(
        val context: ConnectedContext,
    ) : SessionState() {
        val localPort: Int
            get() = context.localPort

        val connectedSockets: Set<SocketType>
            get() = context.connectedSockets

        val audioEnabled: Boolean
            get() = context.audioEnabled

        val dummyByteConfirmed: Boolean
            get() = context.dummyByteConfirmed
    }

    data class Reconnecting(
        val context: ReconnectStateContext,
    ) : SessionState() {
        val attempt: Int
            get() = context.attempt
    }

    data class Failed(
        val issue: SessionIssue,
    ) : SessionState() {
        val reason: String
            get() = issue.userMessage ?: issue.message
    }
}

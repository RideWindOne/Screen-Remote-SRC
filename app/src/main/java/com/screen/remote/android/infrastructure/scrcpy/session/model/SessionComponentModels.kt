package com.screen.remote.android.infrastructure.scrcpy.session.model

enum class SessionComponent {
    AdbConnection,
    ScrcpyServer,
    VideoSocket,
    AudioSocket,
    ControlSocket,
    VideoDecoder,
    AudioDecoder,
}

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

enum class SocketType {
    Video,
    Audio,
    Control,
}

enum class DecoderType {
    Video,
    Audio,
}

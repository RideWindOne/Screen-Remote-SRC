package com.screen.remote.android.infrastructure.scrcpy.session.model

data class SocketConnectionReadModel(
    val connectedSockets: Set<SocketType>,
    val expectedSocketCount: Int,
    val audioEnabled: Boolean,
) {
    val allRequiredSocketsConnected: Boolean
        get() = connectedSockets.size >= expectedSocketCount

    val isVideoSocketConnected: Boolean
        get() = SocketType.Video in connectedSockets

    fun toConnectedContext(
        localPort: Int,
        dummyByteConfirmed: Boolean,
    ): ConnectedContext =
        ConnectedContext(
            localPort = localPort,
            connectedSockets = connectedSockets,
            dummyByteConfirmed = dummyByteConfirmed || isVideoSocketConnected,
            audioEnabled = audioEnabled,
        )
}

data class InfrastructureConnectionReadModel(
    val adbState: ComponentState?,
    val serverState: ComponentState?,
) {
    val isAdbConnected: Boolean
        get() = adbState == ComponentState.Connected

    val isServerRunning: Boolean
        get() = serverState == ComponentState.Running

    val isReadyForSocketConnect: Boolean
        get() = isAdbConnected && isServerRunning
}

data class DecoderConnectionReadModel(
    val runningDecoders: Set<DecoderType>,
    val stoppedDecoders: Set<DecoderType>,
)

data class SessionComponentStateSnapshot(
    val componentStates: Map<SessionComponent, ComponentState>,
    val socketConnections: SocketConnectionReadModel,
    val infrastructureConnection: InfrastructureConnectionReadModel,
    val decoderConnection: DecoderConnectionReadModel,
)

fun Map<SessionComponent, ComponentState>.socketConnectionReadModel(
    expectedSocketCount: Int = 3,
    audioEnabled: Boolean = true,
): SocketConnectionReadModel =
    SocketConnectionReadModel(
        connectedSockets =
            buildSet {
                if (this@socketConnectionReadModel[SessionComponent.VideoSocket] == ComponentState.Connected) {
                    add(SocketType.Video)
                }
                if (this@socketConnectionReadModel[SessionComponent.AudioSocket] == ComponentState.Connected) {
                    add(SocketType.Audio)
                }
                if (this@socketConnectionReadModel[SessionComponent.ControlSocket] == ComponentState.Connected) {
                    add(SocketType.Control)
                }
            },
        expectedSocketCount = expectedSocketCount,
        audioEnabled = audioEnabled,
    )

fun Map<SessionComponent, ComponentState>.infrastructureConnectionReadModel(): InfrastructureConnectionReadModel =
    InfrastructureConnectionReadModel(
        adbState = this[SessionComponent.AdbConnection],
        serverState = this[SessionComponent.ScrcpyServer],
    )

fun Map<SessionComponent, ComponentState>.decoderConnectionReadModel(): DecoderConnectionReadModel =
    DecoderConnectionReadModel(
        runningDecoders =
            buildSet {
                if (this@decoderConnectionReadModel[SessionComponent.VideoDecoder] == ComponentState.Running) {
                    add(DecoderType.Video)
                }
                if (this@decoderConnectionReadModel[SessionComponent.AudioDecoder] == ComponentState.Running) {
                    add(DecoderType.Audio)
                }
            },
        stoppedDecoders =
            buildSet {
                if (this@decoderConnectionReadModel[SessionComponent.VideoDecoder] == ComponentState.Stopped) {
                    add(DecoderType.Video)
                }
                if (this@decoderConnectionReadModel[SessionComponent.AudioDecoder] == ComponentState.Stopped) {
                    add(DecoderType.Audio)
                }
            },
    )

fun Map<SessionComponent, ComponentState>.toSessionComponentStateSnapshot(
    expectedSocketCount: Int = 3,
    audioEnabled: Boolean = true,
): SessionComponentStateSnapshot =
    SessionComponentStateSnapshot(
        componentStates = this,
        socketConnections = socketConnectionReadModel(expectedSocketCount, audioEnabled),
        infrastructureConnection = infrastructureConnectionReadModel(),
        decoderConnection = decoderConnectionReadModel(),
    )

fun InfrastructureConnectionReadModel.infrastructureSummary(): String =
    "adb=$adbState server=$serverState readyForSocket=$isReadyForSocketConnect"

fun SocketConnectionReadModel.socketSummary(): String =
    "connected=${connectedSockets.joinToString()} expected=$expectedSocketCount audioEnabled=$audioEnabled " +
        "allRequired=$allRequiredSocketsConnected videoReady=$isVideoSocketConnected"

fun DecoderConnectionReadModel.decoderSummary(): String =
    "running=${runningDecoders.joinToString()} stopped=${stoppedDecoders.joinToString()}"

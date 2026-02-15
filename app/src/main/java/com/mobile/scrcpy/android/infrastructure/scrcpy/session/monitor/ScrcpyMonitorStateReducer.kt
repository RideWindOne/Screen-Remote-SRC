package com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor

internal class ScrcpyMonitorStateReducer {
    fun reduce(
        currentState: GlobalScrcpyState,
        event: ScrcpyMonitorEvent,
        now: Long,
    ): GlobalScrcpyState =
        when (event) {
            is ScrcpyMonitorEvent.ServerLog -> {
                currentState.copy(
                    serverLogCount = currentState.serverLogCount + 1,
                    lastServerLog = event.message,
                    lastServerLogTime = now,
                )
            }

            is ScrcpyMonitorEvent.SocketDataReceived -> {
                currentState.copy(
                    socketStats =
                        currentState.socketStats.updated(event.socketType) { stats ->
                            stats.copy(
                                bytesReceived = stats.bytesReceived + event.bytesCount,
                                packetsReceived = stats.packetsReceived + 1,
                                lastActivityTime = now,
                            )
                        },
                )
            }

            is ScrcpyMonitorEvent.SocketDataSent -> {
                currentState.copy(
                    socketStats =
                        currentState.socketStats.updated(event.socketType) { stats ->
                            stats.copy(
                                bytesSent = stats.bytesSent + event.bytesCount,
                                packetsSent = stats.packetsSent + 1,
                                lastActivityTime = now,
                            )
                        },
                )
            }

            is ScrcpyMonitorEvent.SocketIdle -> {
                currentState.copy(
                    socketStats =
                        currentState.socketStats.updated(event.socketType) { stats ->
                            stats.copy(idleCount = stats.idleCount + 1)
                        },
                )
            }

            is ScrcpyMonitorEvent.VideoFrameDecoded -> {
                currentState.copy(
                    videoFrameCount = currentState.videoFrameCount + 1,
                    lastVideoFrameTime = now,
                    isVideoActive = true,
                )
            }

            is ScrcpyMonitorEvent.AudioFrameDecoded -> {
                currentState.copy(
                    audioFrameCount = currentState.audioFrameCount + 1,
                    lastAudioFrameTime = now,
                    isAudioActive = true,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenLocked -> {
                currentState.copy(
                    isScreenLocked = true,
                    screenLockTime = now,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenUnlocked -> {
                currentState.copy(
                    isScreenLocked = false,
                    screenUnlockTime = now,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenOff -> {
                currentState.copy(
                    isScreenOn = false,
                    screenOffTime = now,
                )
            }

            is ScrcpyMonitorEvent.DeviceScreenOn -> {
                currentState.copy(
                    isScreenOn = true,
                    screenOnTime = now,
                )
            }

            is ScrcpyMonitorEvent.ConnectionEstablished -> {
                currentState.copy(
                    isConnected = true,
                    connectionTime = now,
                )
            }

            is ScrcpyMonitorEvent.ConnectionLost -> {
                currentState.copy(
                    isConnected = false,
                    disconnectionTime = now,
                    disconnectionReason = event.reason,
                )
            }

            is ScrcpyMonitorEvent.Exception -> {
                currentState.copy(
                    recentExceptions = appendException(currentState.recentExceptions, event.type, event.message),
                )
            }
        }

    private fun Map<com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketType, SocketStatistics>.updated(
        socketType: com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketType,
        transform: (SocketStatistics) -> SocketStatistics,
    ): Map<com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketType, SocketStatistics> {
        val current = get(socketType) ?: SocketStatistics()
        return toMutableMap().apply {
            this[socketType] = transform(current)
        }
    }

    private fun appendException(
        exceptions: List<ExceptionRecord>,
        type: ExceptionType,
        message: String,
    ): List<ExceptionRecord> {
        val newExceptions = exceptions.toMutableList()
        newExceptions.add(ExceptionRecord(type = type, message = message))
        while (newExceptions.size > 20) {
            newExceptions.removeAt(0)
        }
        return newExceptions
    }
}

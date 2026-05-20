package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent

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

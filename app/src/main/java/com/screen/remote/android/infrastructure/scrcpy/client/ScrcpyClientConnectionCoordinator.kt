package com.screen.remote.android.infrastructure.scrcpy.client

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionHealthMonitor
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.screen.remote.android.infrastructure.scrcpy.controller.ScrcpyController
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.session.model.CleanupTrigger
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ScrcpyClientConnectionCoordinator(
    private val stateMachine: ConnectionStateMachine,
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val videoStreamState: MutableStateFlow<VideoStream?>,
    private val audioStreamState: MutableStateFlow<AudioStream?>,
    private val videoResolution: MutableStateFlow<Pair<Int, Int>?>,
    private val lifecycle: ConnectionLifecycle,
    private val controller: ScrcpyController,
    private val healthMonitor: ConnectionHealthMonitor,
    private val sessionRuntime: ScrcpyClientSessionRuntime,
    private val reconnectManager: ScrcpyClientReconnect,
    private val issueTracker: SessionIssueTracker,
    private val getCurrentDeviceId: () -> String?,
    private val onSessionStateChanged: (SessionState) -> Unit,
    private val startForegroundService: (String, ScrcpyOptions) -> Unit,
) {
    private val observerScope = CoroutineScope(Dispatchers.Main)
    private val operationMutex = Mutex()

    suspend fun connect(
        sessionId: String,
        options: ScrcpyOptions,
        isReconnecting: Boolean = false,
    ): Result<Boolean> =
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                stateMachine.clearProgress()
                connectionState.value = ConnectionState.Connecting

                val deviceId = prepareConnection(sessionId, options, isReconnecting)

                sessionRuntime.ensureSession(
                    sessionId = sessionId,
                    options = options,
                    onVideoResolution = { width, height ->
                        videoResolution.value = Pair(width, height)
                        LogManager.d(LogTags.SCRCPY_CLIENT, "Video resolution is set: ${width}x$height")
                    },
                )

                sessionRuntime.ensureMonitor(
                    stateMachine = stateMachine,
                    onReconnect = { reconnectManager.triggerReconnect() },
                    observerScope = observerScope,
                    onSessionStateChanged = onSessionStateChanged,
                )

                val connectionResult = lifecycle.connect()
                if (connectionResult.isFailure) {
                    handleConnectionFailure(connectionResult.exceptionOrNull())
                    return@withContext Result.failure(
                        connectionResult.exceptionOrNull() ?: Exception("Unknown error"),
                    )
                }

                val activeDeviceId = lifecycle.activeDeviceId ?: deviceId
                if (activeDeviceId != deviceId) {
                    LogManager.d(
                        LogTags.SCRCPY_CLIENT,
                        "The actual connection candidate has been updated: prepared=$deviceId active=$activeDeviceId",
                    )
                }
                issueTracker.updateDeviceId(activeDeviceId)

                if (!controller.isRunning()) {
                    controller.start(activeDeviceId, gameMode = options.config.gameMode)
                }

                options.config.startApp.trim().takeIf(String::isNotEmpty)?.let { startApp ->
                    controller.startApp(startApp)
                        .onFailure { error ->
                            LogManager.w(LogTags.SCRCPY_CLIENT, "Request to launch app on virtual display failed: ${error.message}")
                        }
                }

                if (options.config.turnScreenOff) {
                    controller.setDisplayPower(on = false)
                        .onFailure { error ->
                            LogManager.w(LogTags.SCRCPY_CLIENT, "Request to close device screen failed: ${error.message}")
                        }
                }

                val resolution = videoResolution.value
                if (resolution != null) {
                    startForegroundService(activeDeviceId, options)
                }

                withContext(Dispatchers.Main) {
                    connectionState.value = ConnectionState.Connected
                }
                Result.success(true)
            }
        }

    suspend fun disconnect(): Result<Boolean> = cleanup(CleanupTrigger.UserDisconnect)

    suspend fun cancelConnect(): Result<Boolean> = cleanup(CleanupTrigger.CancelConnect)

    private suspend fun cleanup(trigger: CleanupTrigger): Result<Boolean> {
        reconnectManager.cancelPending()
        return operationMutex.withLock {
            // 一旦获得生命周期锁，清理必须完整执行，不能随页面协程销毁而中断一半。
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    connectionState.value = ConnectionState.Disconnecting
                    sessionRuntime.sessionManager.currentOrNull?.handleEventAndWait(
                        SessionEvent.RequestCleanup(trigger),
                    )
                    cleanupSessionRuntime()
                    sessionRuntime.clearMonitor()
                    connectionState.value = ConnectionState.Disconnected
                    reconnectManager.reset()
                    issueTracker.clear(trigger.logLabel)

                    if (trigger == CleanupTrigger.CancelConnect) {
                        LogManager.d(LogTags.SCRCPY_CLIENT, "Connection canceled")
                    }
                    Result.success(true)
                } catch (error: Exception) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "${trigger.logLabel} Cleanup failed: ${error.message}", error)
                    Result.failure(error)
                }
            }
        }
    }

    private suspend fun cleanupSessionRuntime() {
        // 先停止健康检查，避免主动关闭流和 Socket 被解释为连接丢失。
        healthMonitor.stopMonitoring()
        videoStreamState.value?.close()
        audioStreamState.value?.close()
        videoStreamState.value = null
        audioStreamState.value = null
        delay(50)

        lifecycle.disconnect()
        controller.stop()

        // 即使 ADB 尚未胜出，初始连接取消也必须销毁 Session。
        val deviceId = sessionRuntime.sessionManager.currentOrNull?.deviceIdentifier
        sessionRuntime.sessionManager.stop()
        deviceId?.let(ScrcpyEventBus::clearDeviceState)
        videoResolution.value = null
    }

    private fun prepareConnection(
        sessionId: String,
        options: ScrcpyOptions,
        isReconnecting: Boolean,
    ): String {
        val deviceId =
            if (isReconnecting) {
                getCurrentDeviceId() ?: options.getDeviceIdentifier()
            } else {
                options.getDeviceIdentifier()
            }
        issueTracker.begin(sessionId, deviceId, isReconnecting)
        return deviceId
    }

    private fun handleConnectionFailure(error: Throwable?) {
        val errorMsg = error?.message ?: "Unknown error"
        connectionState.value = ConnectionState.Error(errorMsg)
        sessionRuntime.sessionManager.currentOrNull?.handleEvent(
            SessionEvent.ServerFailed(
                ServerIssue(
                    kind = ServerIssueKind.ConnectionFailure,
                    detail = errorMsg,
                ),
            ),
        )
        AdbBridge.clearConnection()
    }
}

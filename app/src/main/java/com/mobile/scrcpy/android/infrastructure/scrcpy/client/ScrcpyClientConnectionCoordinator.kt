package com.mobile.scrcpy.android.infrastructure.scrcpy.client
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.SessionIssueTracker
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionHealthMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionShellMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.mobile.scrcpy.android.infrastructure.scrcpy.controller.ScrcpyController
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CleanupContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CleanupTrigger
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

internal class ScrcpyClientConnectionCoordinator(
    private val stateMachine: ConnectionStateMachine,
    private val connectionState: MutableStateFlow<ConnectionState>,
    private val videoStreamState: MutableStateFlow<VideoStream?>,
    private val audioStreamState: MutableStateFlow<AudioStream?>,
    private val videoResolution: MutableStateFlow<Pair<Int, Int>?>,
    private val lifecycle: ConnectionLifecycle,
    private val controller: ScrcpyController,
    private val shellMonitor: ConnectionShellMonitor,
    private val healthMonitor: ConnectionHealthMonitor,
    private val sessionRuntime: ScrcpyClientSessionRuntime,
    private val reconnectManager: ScrcpyClientReconnect,
    private val getCurrentDeviceId: () -> String?,
    private val setCurrentIds: (String, String) -> Unit,
    private val onSessionStateChanged: (SessionState) -> Unit,
    private val startForegroundService: (String, ScrcpyOptions) -> Unit,
) {
    private val observerScope = CoroutineScope(Dispatchers.Main)

    suspend fun connect(
        sessionId: String,
        options: ScrcpyOptions,
        isReconnecting: Boolean = false,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            stateMachine.clearProgress()
            connectionState.value = ConnectionState.Connecting

            val deviceId = prepareConnection(sessionId, options, isReconnecting)

            sessionRuntime.ensureSession(
                sessionId = sessionId,
                options = options,
                onVideoResolution = { width, height ->
                    videoResolution.value = Pair(width, height)
                    LogManager.d(LogTags.SCRCPY_CLIENT, "视频分辨率已设置: ${width}x$height")
                },
            )

            sessionRuntime.ensureMonitor(
                stateMachine = stateMachine,
                onReconnect = { reconnectManager.triggerReconnect() },
                observerScope = observerScope,
                onSessionStateChanged = onSessionStateChanged,
            )

            if (!controller.isRunning()) {
                controller.start(deviceId)
            }

            val connectionResult = lifecycle.connect()
            if (connectionResult.isFailure) {
                handleConnectionFailure(connectionResult.exceptionOrNull())
                return@withContext Result.failure(
                    connectionResult.exceptionOrNull() ?: Exception("Unknown error"),
                )
            }

            if (options.turnScreenOff) {
                controller.setDisplayPower(on = false)
                    .onFailure { error ->
                        LogManager.w(LogTags.SCRCPY_CLIENT, "请求关闭设备屏幕失败: ${error.message}")
                    }
            }

            val resolution = videoResolution.value
            if (resolution != null) {
                startForegroundService(deviceId, options)
            }

            withContext(Dispatchers.Main) {
                connectionState.value = ConnectionState.Connected
            }
            Result.success(true)
        }

    suspend fun disconnect(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                connectionState.value = ConnectionState.Disconnecting

                getCurrentDeviceId()?.let {
                    sessionRuntime.sessionManager.currentOrNull?.handleEvent(
                        SessionEvent.RequestCleanup(
                            CleanupContext(
                                trigger = CleanupTrigger.UserDisconnect,
                                preserveAdbConnection = true,
                            ),
                        ),
                    )
                }

                ScrcpyClientCleanup.cleanupSessionRuntime(
                    videoStreamState = videoStreamState,
                    audioStreamState = audioStreamState,
                    lifecycle = lifecycle,
                    controller = controller,
                    shellMonitor = shellMonitor,
                    healthMonitor = healthMonitor,
                    videoResolution = videoResolution,
                    deviceId = getCurrentDeviceId(),
                    sessionManager = sessionRuntime.sessionManager,
                )

                sessionRuntime.clearMonitor()
                connectionState.value = ConnectionState.Disconnected
                reconnectManager.reset()
                SessionIssueTracker.clear("disconnect")

                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "断开连接失败: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun cancelConnect(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                connectionState.value = ConnectionState.Disconnecting

                getCurrentDeviceId()?.let {
                    sessionRuntime.sessionManager.currentOrNull?.handleEvent(
                        SessionEvent.RequestCleanup(
                            CleanupContext(
                                trigger = CleanupTrigger.CancelConnect,
                                preserveAdbConnection = true,
                            ),
                        ),
                    )
                }

                ScrcpyClientCleanup.cleanupSessionRuntime(
                    videoStreamState = videoStreamState,
                    audioStreamState = audioStreamState,
                    lifecycle = lifecycle,
                    controller = controller,
                    shellMonitor = shellMonitor,
                    healthMonitor = healthMonitor,
                    videoResolution = videoResolution,
                    deviceId = getCurrentDeviceId(),
                    sessionManager = sessionRuntime.sessionManager,
                )

                sessionRuntime.clearMonitor()
                connectionState.value = ConnectionState.Disconnected
                reconnectManager.reset()
                SessionIssueTracker.clear("cancel_connect")

                LogManager.d(LogTags.SCRCPY_CLIENT, "连接已取消")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "取消连接失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    private fun prepareConnection(
        sessionId: String,
        options: ScrcpyOptions,
        isReconnecting: Boolean,
    ): String {
        val deviceId = options.getDeviceIdentifier()
        setCurrentIds(sessionId, deviceId)
        SessionIssueTracker.begin(sessionId, deviceId, isReconnecting)
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

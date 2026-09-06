package com.screen.remote.android.infrastructure.scrcpy.client

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyError
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.event.StatusChanged
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.domain.model.ConnectionProgress
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.compatibilityCaptureSettings
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionMetadataReader
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionShellMonitor
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionSocketManager
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.screen.remote.android.infrastructure.scrcpy.controller.ScrcpyController
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderResolutionRecoveryRequest
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import com.screen.remote.android.service.ScrcpyForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scrcpy 客户端 - 主入口类
 * 职责：状态管理、连接协调、重连逻辑
 */
class ScrcpyClient(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
) {
    private val _decoderResolutionRecoveryRequest = MutableStateFlow<DecoderResolutionRecoveryRequest?>(null)
    val decoderResolutionRecoveryRequest: StateFlow<DecoderResolutionRecoveryRequest?> =
        _decoderResolutionRecoveryRequest

    private val sessionRuntime = ScrcpyClientSessionRuntime(context) { request ->
        _decoderResolutionRecoveryRequest.value = request
    }
    val sessionManager = sessionRuntime.sessionManager
    private val issueTracker = SessionIssueTracker()

    private var protectedDeviceId: String? = null

    private val sessionContext: SessionContext = sessionRuntime.sessionContext

    init {
        ScrcpyDiagnosticsRegistry.register(this)
    }

    // 连接组件
    private val stateMachine = ConnectionStateMachine()
    private val socketManager = ConnectionSocketManager(sessionContext)
    private val metadataReader = ConnectionMetadataReader(socketManager, issueTracker)
    private val shellMonitor = ConnectionShellMonitor(sessionContext, issueTracker)

    // 控制器
    private val controller = ScrcpyController(
        getDeviceId = ::getCurrentDeviceId,
        getControlSocket = { socketManager.controlSocket },
        clearControlSocket = { socketManager.dropControlSocket() },
        localPort = 27183,
        onClipboardReceived = ::updateLocalClipboard,
        issueTracker = issueTracker,
    )

    private val lifecycle = ConnectionLifecycle(
        context,
        adbConnectionManager,
        stateMachine,
        sessionContext,
        socketManager,
        metadataReader,
        shellMonitor,
        issueTracker,
        onVideoStreamReady = { _videoStreamState.value = it },
        onAudioStreamReady = { _audioStreamState.value = it },
    )

    // 状态流
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    val connectionProgress: StateFlow<List<ConnectionProgress>> = stateMachine.connectionProgress

    private val _videoStreamState = MutableStateFlow<VideoStream?>(null)
    val videoStreamState: StateFlow<VideoStream?> = _videoStreamState

    private val _audioStreamState = MutableStateFlow<AudioStream?>(null)
    val audioStreamState: StateFlow<AudioStream?> = _audioStreamState

    private val _videoResolution = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoResolution: StateFlow<Pair<Int, Int>?> = _videoResolution

    private val _compatibilityFrame = MutableStateFlow<Bitmap?>(null)
    val compatibilityFrame: StateFlow<Bitmap?> = _compatibilityFrame

    private val compatibilityModeController = CompatibilityModeController(
        context = context,
        adbConnectionManager = adbConnectionManager,
        getDeviceId = ::getCurrentDeviceId,
        getCaptureSettings = {
            getCurrentSessionOptions()?.config?.compatibilityCaptureSettings()
        },
        onFrame = { frame -> _compatibilityFrame.value = frame },
        onResolution = { width, height -> _videoResolution.value = width to height },
        onConnectionLost = { message ->
            stateCoordinator.updateConnectionStateOnError(message)
        },
        onCaptureFailure = { message ->
            _connectionState.value = ConnectionState.Error(message)
        },
    )

    // 事件处理器
    private val eventHandler = ScrcpyClientEventHandler(
        connectionState = _connectionState,
        getCurrentSessionId = ::getCurrentSessionId,
        updateConnectionStateOnError = { message -> stateCoordinator.updateConnectionStateOnError(message) },
    )

    // 重连管理器
    private val reconnectManager = ScrcpyClientReconnect(
        adbConnectionManager = adbConnectionManager,
        connectionState = _connectionState,
        getCurrentDeviceId = ::getCurrentDeviceId,
        connect = ::connect,
        sessionManager = sessionManager,
    )

    private val stateCoordinator = ScrcpyClientStateCoordinator(
        connectionState = _connectionState,
        sessionManager = sessionManager,
        reconnectManager = reconnectManager,
        getCurrentDeviceId = ::getCurrentDeviceId,
    )

    private val connectionCoordinator = ScrcpyClientConnectionCoordinator(
        stateMachine = stateMachine,
        connectionState = _connectionState,
        videoStreamState = _videoStreamState,
        audioStreamState = _audioStreamState,
        videoResolution = _videoResolution,
        lifecycle = lifecycle,
        controller = controller,
        healthMonitor = lifecycle.healthMonitor,
        sessionRuntime = sessionRuntime,
        reconnectManager = reconnectManager,
        issueTracker = issueTracker,
        getCurrentDeviceId = ::getCurrentDeviceId,
        onSessionStateChanged = { state -> stateCoordinator.handleSessionStateChange(state) },
        startForegroundService = ::startForegroundService,
    )

    init {
        // 注册 Native 层状态事件监听
        ScrcpyEventBus.on<StatusChanged> { event ->
            eventHandler.handleNativeStatusChange(event.event)
        }

        // 注册 Native 层错误事件监听
        ScrcpyEventBus.on<ScrcpyError> { event ->
            eventHandler.handleNativeError(event.event)
        }
    }

    /**
     * 连接到设备（统揽全局）
     */
    suspend fun connect(
        sessionId: String,
        options: ScrcpyOptions,
        isReconnecting: Boolean = false,
    ): Result<Boolean> {
        compatibilityModeController.stop()
        val result = connectionCoordinator.connect(sessionId, options, isReconnecting)
        if (result.isSuccess && options.config.compatibilityMode) {
            val compatibilityResult = compatibilityModeController.start()
            if (compatibilityResult.isFailure) {
                connectionCoordinator.disconnect()
                return compatibilityResult
            }

            options.config.startApp.trim().takeIf(String::isNotEmpty)?.let { startApp ->
                compatibilityModeController.startApp(startApp).onFailure { error ->
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "Compatibility mode request to launch app failed: ${error.message}",
                    )
                }
            }
        }
        return result
    }

    /**
     * 断开连接（完整清理）
     */
    suspend fun disconnect(): Result<Boolean> {
        compatibilityModeController.stop()
        // 断开连接前先移除前台服务保护，避免状态栏通知残留
        protectedDeviceId?.let { deviceId ->
            try {
                ScrcpyForegroundService.unprotectDevice(context, deviceId)
                LogManager.d(LogTags.SCRCPY_CLIENT, "Removed device from keepalive list: $deviceId")
            } catch (e: Exception) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "Failed to unprotect device: ${e.message}")
            }
            protectedDeviceId = null
        }
        return connectionCoordinator.disconnect()
    }

    /**
     * 取消连接（部分清理）
     */
    suspend fun cancelConnect(): Result<Boolean> {
        compatibilityModeController.stop()
        return connectionCoordinator.cancelConnect()
    }

    fun confirmDecoderResolutionRecovery() {
        sessionManager.currentOrNull?.handleEvent(SessionEvent.ConfirmDecoderResolutionRecovery)
    }

    fun dismissDecoderResolutionRecovery() {
        sessionManager.currentOrNull?.handleEvent(SessionEvent.DismissDecoderResolutionRecovery)
            ?: run { _decoderResolutionRecoveryRequest.value = null }
    }

    /**
     * 控制方法委托
     */
    fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f,
    ): Result<Boolean> = if (isCompatibilityMode()) {
        compatibilityModeController.sendTouchEvent(action, pointerId, x, y)
    } else {
        controller.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)
    }

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1,
        repeat: Int = 0,
        metaState: Int = 0,
    ): Result<Boolean> = if (isCompatibilityMode()) {
        compatibilityModeController.sendKeyEvent(keyCode, action)
    } else {
        controller.sendKeyEvent(keyCode, action, repeat, metaState)
    }

    suspend fun rotateDevice(): Result<Boolean> = if (isCompatibilityMode()) {
        Result.failure(UnsupportedOperationException("Target rotation is unavailable in compatibility mode"))
    } else {
        controller.rotateDevice()
    }

    suspend fun sendText(text: String): Result<Boolean> = if (isCompatibilityMode()) {
        compatibilityModeController.sendText(text)
    } else {
        controller.sendText(text)
    }

    suspend fun setClipboardAndPaste(text: String): Result<Boolean> = if (isCompatibilityMode()) {
        compatibilityModeController.sendText(text)
    } else {
        controller.setClipboardAndPaste(text)
    }

    private fun updateLocalClipboard(text: String) {
        Handler(Looper.getMainLooper()).post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Screen Remote", text))
        }
    }

    suspend fun wakeUpScreen(): Result<Boolean> {
        if (isCompatibilityMode()) {
            return compatibilityModeController.wakeUpScreen()
        }
        val resolution = videoResolution.value
        return if (resolution != null) {
            val (width, height) = resolution
            controller.wakeUpScreen(width, height)
        } else {
            controller.wakeUpScreen()
        }
    }

    /**
     * 启动前台服务（首次连接或添加设备）
     */
    private fun startForegroundService(
        deviceName: String,
    ) {
        try {
            val deviceId = getCurrentDeviceId() ?: return
            protectedDeviceId?.takeIf { it != deviceId }?.let { previousDeviceId ->
                ScrcpyForegroundService.unprotectDevice(context, previousDeviceId)
            }
            ScrcpyForegroundService.protectDevice(
                context = context,
                deviceId = deviceId,
                deviceName = deviceName,
                shellPassword = getCurrentSessionOptions()?.config?.shellPassword.orEmpty(),
            )
            protectedDeviceId = deviceId

            LogManager.d(LogTags.SCRCPY_CLIENT, "Device added to keepalive list: $deviceName")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to add device to keepalive list: ${e.message}", e)
        }
    }

    /**
     * 获取当前会话 ID
     */
    fun getCurrentSessionId(): String? = sessionManager.currentOrNull?.sessionId

    /**
     * 获取当前设备 ID
     */
    fun getCurrentDeviceId(): String? =
        lifecycle.activeDeviceId ?: sessionManager.currentOrNull?.adbConnection?.deviceId

    fun getCurrentSessionOptions(): ScrcpyOptions? = sessionManager.currentOrNull?.options

    private fun isCompatibilityMode(): Boolean = getCurrentSessionOptions()?.config?.compatibilityMode == true

    fun createSessionContext(): SessionContext = sessionRuntime.createBoundContext()
}

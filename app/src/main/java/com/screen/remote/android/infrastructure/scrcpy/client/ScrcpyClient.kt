package com.screen.remote.android.infrastructure.scrcpy.client

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyError
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.event.StatusChanged
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.ServiceApiCompat
import com.screen.remote.android.core.domain.model.ConnectionProgress
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.i18n.RemoteTexts
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
    private val sessionRuntime = ScrcpyClientSessionRuntime(context)
    val sessionManager = sessionRuntime.sessionManager
    private val issueTracker = SessionIssueTracker()

    private var protectedDeviceId: String? = null

    private val sessionContext: SessionContext = sessionRuntime.sessionContext

    init {
        // 加载 Native 库
        try {
            System.loadLibrary("scrcpy_adb_bridge")
        } catch (e: UnsatisfiedLinkError) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_NATIVE_LIB_LOAD_FAILED.get()}: ${e.message}", e)
        }
    }

    // 连接组件
    private val stateMachine = ConnectionStateMachine()
    private val socketManager = ConnectionSocketManager(sessionContext)
    private val metadataReader = ConnectionMetadataReader(socketManager, issueTracker)
    private val shellMonitor = ConnectionShellMonitor(sessionContext, issueTracker)

    // 控制器
    private val controller =
        ScrcpyController(
            getDeviceId = ::getCurrentDeviceId,
            getControlSocket = { socketManager.controlSocket },
            clearControlSocket = { socketManager.dropControlSocket() },
            localPort = 27183,
            onClipboardReceived = ::updateLocalClipboard,
            issueTracker = issueTracker,
        )

    private val lifecycle =
        ConnectionLifecycle(
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

    // 事件处理器
    private val eventHandler =
        ScrcpyClientEventHandler(
            connectionState = _connectionState,
            getCurrentSessionId = ::getCurrentSessionId,
            getCurrentDeviceId = ::getCurrentDeviceId,
            updateConnectionStateOnError = { message -> stateCoordinator.updateConnectionStateOnError(message) },
        )

    // 重连管理器
    private val reconnectManager =
        ScrcpyClientReconnect(
            adbConnectionManager = adbConnectionManager,
            connectionState = _connectionState,
            getCurrentDeviceId = ::getCurrentDeviceId,
            connect = ::connect,
            sessionManager = sessionManager,
        )

    private val stateCoordinator =
        ScrcpyClientStateCoordinator(
            connectionState = _connectionState,
            sessionManager = sessionManager,
            reconnectManager = reconnectManager,
            getCurrentDeviceId = ::getCurrentDeviceId,
        )

    private val connectionCoordinator =
        ScrcpyClientConnectionCoordinator(
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
    ): Result<Boolean> = connectionCoordinator.connect(sessionId, options, isReconnecting)

    /**
     * 断开连接（完整清理）
     */
    suspend fun disconnect(): Result<Boolean> = connectionCoordinator.disconnect()

    /**
     * 取消连接（部分清理）
     */
    suspend fun cancelConnect(): Result<Boolean> = connectionCoordinator.cancelConnect()

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
    ): Result<Boolean> = controller.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1,
        repeat: Int = 0,
        metaState: Int = 0,
    ): Result<Boolean> = controller.sendKeyEvent(keyCode, action, repeat, metaState)

    suspend fun sendText(text: String): Result<Boolean> = controller.sendText(text)

    suspend fun setClipboardAndPaste(text: String): Result<Boolean> = controller.setClipboardAndPaste(text)

    private fun updateLocalClipboard(text: String) {
        Handler(Looper.getMainLooper()).post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Screen Remote", text))
        }
    }

    suspend fun wakeUpScreen(): Result<Boolean> {
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
        options: ScrcpyOptions,
    ) {
        try {
            val deviceId = getCurrentDeviceId() ?: return
            protectedDeviceId
                ?.takeIf { it != deviceId }
                ?.let { previousDeviceId ->
                    ScrcpyForegroundService.unprotectDevice(context, previousDeviceId)
                }
            ScrcpyForegroundService.protectDevice(
                context = context,
                deviceId = deviceId,
                deviceName = deviceName,
            )
            protectedDeviceId = deviceId

            LogManager.d(LogTags.SCRCPY_CLIENT, "已添加设备到保活列表: $deviceName")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "添加设备到保活列表失败: ${e.message}", e)
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

    fun createSessionContext(): SessionContext = sessionRuntime.createBoundContext()
}

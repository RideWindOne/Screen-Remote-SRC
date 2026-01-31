package com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy

import android.content.Context
import android.content.Intent
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.event.ScrcpyError
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
import com.mobile.scrcpy.android.core.common.event.StatusChanged
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.core.data.storage.SessionStorage
import com.mobile.scrcpy.android.core.domain.model.ConnectionProgress
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionHealthMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionMetadataReader
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionShellMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionSocketManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.mobile.scrcpy.android.infrastructure.scrcpy.controller.feature.scrcpy.ScrcpyController
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.createMonitorBus
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.initMonitor
import com.mobile.scrcpy.android.service.ScrcpyForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scrcpy 客户端 - 主入口类
 * 职责：状态管理、连接协调、重连逻辑
 */
class ScrcpyClient(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
) {
    // 当前会话 ID（UUID）
    private var currentSessionId: String? = null

    // 当前设备 ID（host:port 或 usb:serial）
    private var currentDeviceId: String? = null

    // 会话监控标记
    private var sessionMonitor: Any? = null

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
    private val socketManager = ConnectionSocketManager()
    private val metadataReader = ConnectionMetadataReader(socketManager)
    private val shellMonitor = ConnectionShellMonitor()
    private val healthMonitor = ConnectionHealthMonitor()

    // 控制器
    private val controller =
        ScrcpyController(
            adbConnectionManager = adbConnectionManager,
            getDeviceId = { currentDeviceId },
            getControlSocket = { socketManager.controlSocket },
            clearControlSocket = { /* controlSocket 由 socketManager 管理，无需手动清除 */ },
            localPort = 27183,
        )

    private val lifecycle =
        ConnectionLifecycle(
            context,
            adbConnectionManager,
            stateMachine,
            socketManager,
            metadataReader,
            shellMonitor,
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
            getCurrentSessionId = { currentSessionId },
            getCurrentDeviceId = { currentDeviceId },
            updateConnectionStateOnError = ::updateConnectionStateOnError,
        )

    // 重连管理器
    private val reconnectManager =
        ScrcpyClientReconnect(
            adbConnectionManager = adbConnectionManager,
            connectionState = _connectionState,
            getCurrentSessionId = { currentSessionId },
            getCurrentDeviceId = { currentDeviceId },
            connect = ::connect,
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
        host: String,
        port: Int = NetworkConstants.DEFAULT_ADB_PORT_INT,
        options: ScrcpyOptions,
        isReconnecting: Boolean = false,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            stateMachine.clearProgress()
            _connectionState.value = ConnectionState.Connecting

            // 1. 准备连接参数
            val deviceId = prepareConnection(sessionId, host, port, options, isReconnecting)

            // 2. 初始化会话（必须在最前面，用于记录所有错误）
            val storage = SessionStorage(context)
            val sessionOptions = storage.getOptions(sessionId) ?: options
            val session =
                CurrentSession.currentOrNull ?: CurrentSession.start(
                    options = sessionOptions,
                    storage = storage,
                    onVideoResolution = { width, height ->
                        _videoResolution.value = Pair(width, height)
                        LogManager.d(LogTags.SCRCPY_CLIENT, "视频分辨率已设置: ${width}x$height")
                    },
                )
            session.createMonitorBus()

            // 3. 创建会话监控器（仅首次连接，必须等待完成）
            createSessionMonitorIfNeeded()

            // 4. 启动控制消息发送线程（必须在会话监控器创建后）
            if (!controller.isRunning()) {
                controller.start(deviceId)
            }

            // 5. 建立 Scrcpy 连接（包含所有细节：ADB、Server、Socket、监控、服务）
            val connectionResult = lifecycle.connect()

            if (connectionResult.isFailure) {
                handleConnectionFailure(connectionResult.exceptionOrNull())
                return@withContext Result.failure(
                    connectionResult.exceptionOrNull() ?: Exception("Unknown error"),
                )
            }

            // 6. 启动前台服务
            val resolution = _videoResolution.value
            if (resolution != null) {
                startForegroundService(deviceName = deviceId)
            }

            withContext(Dispatchers.Main) {
                _connectionState.value = ConnectionState.Connected
            }
            Result.success(true)
        }

    /**
     * 准备连接参数
     */
    private fun prepareConnection(
        sessionId: String,
        host: String,
        port: Int,
        options: ScrcpyOptions,
        isReconnecting: Boolean,
    ): String {
        val deviceId = if (host.startsWith("usb:")) host else "$host:$port"
        currentSessionId = sessionId
        currentDeviceId = deviceId
        return deviceId
    }

    /**
     * 创建会话监控器（仅首次连接时创建）
     */
    private fun createSessionMonitorIfNeeded() {
        if (sessionMonitor != null) return

        val session = CurrentSession.current
        session.initMonitor(
            stateMachine = stateMachine,
            onReconnect = { reconnectManager.triggerReconnect() },
        )

        // 监听状态变化
        CoroutineScope(Dispatchers.Main).launch {
            session.sessionState.collect { state ->
                handleSessionStateChange(state)
            }
        }

        sessionMonitor = session // 标记已初始化
    }

    /**
     * 处理连接失败
     */
    private fun handleConnectionFailure(error: Throwable?) {
        val errorMsg = error?.message ?: "Unknown error"
        _connectionState.value = ConnectionState.Error(errorMsg)
        CurrentSession.currentOrNull?.handleEvent(
            SessionEvent.ServerFailed(errorMsg),
        )
        AdbBridge.clearConnection()
    }

    /**
     * 断开连接（完整清理）
     */
    suspend fun disconnect(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.Disconnecting

                // 推送清理事件
                currentDeviceId?.let {
                    CurrentSession.currentOrNull?.handleEvent(SessionEvent.RequestCleanup)
                }

                // 完整清理
                ScrcpyClientCleanup.cleanupAll(
                    videoStreamState = _videoStreamState,
                    audioStreamState = _audioStreamState,
                    lifecycle = lifecycle,
                    controller = controller,
                    shellMonitor = shellMonitor,
                    healthMonitor = healthMonitor,
                    videoResolution = _videoResolution,
                    deviceId = currentDeviceId,
                )

                // 清理会话监控器引用
                sessionMonitor = null

                _connectionState.value = ConnectionState.Disconnected
                reconnectManager.reset()

                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "断开连接失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 取消连接（部分清理）
     */
    suspend fun cancelConnect(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.Disconnecting

                // 部分清理
                ScrcpyClientCleanup.cleanupConnectionOnly(
                    videoStreamState = _videoStreamState,
                    audioStreamState = _audioStreamState,
                    lifecycle = lifecycle,
                    controller = controller,
                    shellMonitor = shellMonitor,
                    healthMonitor = healthMonitor,
                    videoResolution = _videoResolution,
                    deviceId = currentDeviceId,
                )

                // 清理会话监控器引用
                sessionMonitor = null

                _connectionState.value = ConnectionState.Disconnected
                reconnectManager.reset()

                LogManager.d(LogTags.SCRCPY_CLIENT, "连接已取消")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "取消连接失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 控制方法委托
     */
    suspend fun sendTouchEvent(
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
     * 当视频流出现错误时更新连接状态并触发重连
     */
    private fun updateConnectionStateOnError(message: String) {
        if (_connectionState.value is ConnectionState.Connected) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "连接错误: $message")
            currentDeviceId?.let {
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.RequestReconnect(message))
            }
        }
    }

    /**
     * 启动前台服务（首次连接或添加设备）
     */
    private fun startForegroundService(deviceName: String) {
        try {
            val deviceId = currentDeviceId ?: return

            val intent =
                Intent(context, ScrcpyForegroundService::class.java).apply {
                    action = ScrcpyForegroundService.ACTION_ADD_DEVICE
                    putExtra(ScrcpyForegroundService.EXTRA_DEVICE_ID, deviceId)
                    putExtra(ScrcpyForegroundService.EXTRA_DEVICE_NAME, deviceName)
                }

            ApiCompatHelper.startForegroundServiceCompat(context, intent)

            LogManager.d(LogTags.SCRCPY_CLIENT, "已添加设备到保活列表: $deviceName")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "添加设备到保活列表失败: ${e.message}", e)
        }
    }

    /**
     * 处理会话状态变化（来自 SessionMonitor）
     */
    private fun handleSessionStateChange(state: SessionState) {
        LogManager.d(LogTags.SDL, "会话状态变化: $state")

        when (state) {
            is SessionState.Connected -> {
                if (_connectionState.value !is ConnectionState.Connected) {
                    CoroutineScope(Dispatchers.Main).launch {
                        _connectionState.value = ConnectionState.Connected
                    }
                }
                reconnectManager.reset()
            }

            is SessionState.Reconnecting -> {
                CoroutineScope(Dispatchers.Main).launch {
                    _connectionState.value = ConnectionState.Reconnecting
                }
                reconnectManager.triggerReconnect()
            }

            is SessionState.Failed -> {
                CoroutineScope(Dispatchers.Main).launch {
                    _connectionState.value = ConnectionState.Error(state.reason)
                }
                reconnectManager.reset()
            }

            is SessionState.AdbConnected,
            is SessionState.AdbDisconnected,
            is SessionState.ServerStarting,
            is SessionState.ServerStarted,
            is SessionState.ServerFailed,
            is SessionState.Idle,
            -> {
                // 这些状态已由 ScrcpySessionMonitor 处理
            }

            else -> {}
        }
    }

    /**
     * 获取当前会话 ID
     */
    fun getCurrentSessionId(): String? = currentSessionId

    /**
     * 获取当前设备 ID
     */
    fun getCurrentDeviceId(): String? = currentDeviceId
}

package com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy

import android.content.Context
import android.content.Intent
import com.mobile.scrcpy.android.core.domain.model.ConnectionProgress
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionState
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.feature.scrcpy.ScrcpyConnection
import com.mobile.scrcpy.android.infrastructure.scrcpy.controller.feature.scrcpy.ScrcpyController
import com.mobile.scrcpy.android.service.ScrcpyForegroundService

import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
/**
 * Scrcpy 客户端 - 主入口类
 * 职责：状态管理、连接协调、重连逻辑
 */
class ScrcpyClient(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager
) {
    // 当前使用的设备 ID
    private var currentDeviceId: String? = null

    init {
        // 加载 Native 库
        try {
            System.loadLibrary("scrcpy_adb_bridge")
        } catch (e: UnsatisfiedLinkError) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_NATIVE_LIB_LOAD_FAILED.get()}: ${e.message}", e)
        }
    }

    // 连接管理器
    private val connection = ScrcpyConnection(context, adbConnectionManager)

    // 控制器
    private val controller = ScrcpyController(
        adbConnectionManager = adbConnectionManager,
        getDeviceId = { currentDeviceId },
        getControlSocket = { connection.controlSocket },
        clearControlSocket = { /* controlSocket 由 connection 管理，无需手动清除 */ },
        localPort = 27183
    )

    // 状态流
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    val connectionProgress: StateFlow<List<ConnectionProgress>> = connection.connectionProgress

    private val _videoStreamState = MutableStateFlow<VideoStream?>(null)
    val videoStreamState: StateFlow<VideoStream?> = _videoStreamState

    private val _audioStreamState = MutableStateFlow<AudioStream?>(null)
    val audioStreamState: StateFlow<AudioStream?> = _audioStreamState

    private val _videoResolution = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoResolution: StateFlow<Pair<Int, Int>?> = _videoResolution

    // 连接参数缓存（用于重连）
    private var lastMaxSize: Int? = null
    private var lastBitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT
    private var lastMaxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS
    private var lastVideoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC
    private var lastEnableAudio: Boolean = false
    private var lastStayAwake: Boolean = false
    private var lastTurnScreenOff: Boolean = false
    private var lastPowerOffOnClose: Boolean = false

    // 重连状态
    private var reconnectAttempts: Int = 0
    private var isReconnecting: Boolean = false

    /**
     * 通过设备 ID 连接 Scrcpy（异步版本，带进度反馈）
     */
    suspend fun connectByDeviceId(
        deviceId: String,
        maxSize: Int? = null,
        bitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT,
        maxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS,
        videoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
        videoEncoder: String = "",
        enableAudio: Boolean = false,
        audioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
        audioEncoder: String = "",
        stayAwake: Boolean = false,
        turnScreenOff: Boolean = false,
        powerOffOnClose: Boolean = false,
        skipAdbConnect: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!skipAdbConnect) {
                connection.clearProgress()
                _connectionState.value = ConnectionState.Connecting
            } else {
                _connectionState.value = ConnectionState.Connecting
            }

            // 保存连接参数
            currentDeviceId = deviceId
            lastMaxSize = maxSize
            lastBitRate = bitRate
            lastMaxFps = maxFps
            lastVideoCodec = videoCodec
            lastEnableAudio = enableAudio
            lastStayAwake = stayAwake
            lastTurnScreenOff = turnScreenOff
            lastPowerOffOnClose = powerOffOnClose

            // 建立连接
            val result = connection.connect(
                deviceId = deviceId,
                maxSize = maxSize,
                bitRate = bitRate,
                maxFps = maxFps,
                videoCodec = videoCodec,
                videoEncoder = videoEncoder,
                enableAudio = enableAudio,
                audioCodec = audioCodec,
                audioEncoder = audioEncoder,
                stayAwake = stayAwake,
                turnScreenOff = turnScreenOff,
                powerOffOnClose = powerOffOnClose,
                skipAdbConnect = skipAdbConnect,
                onVideoResolution = { width, height ->
                    _videoResolution.value = Pair(width, height)
                }
            )

            if (result.isSuccess) {
                val (videoStream, audioStream) = result.getOrThrow()
                _videoStreamState.value = videoStream
                _audioStreamState.value = audioStream

                // 启动 shell 监控
                connection.startShellMonitor { error ->
                    updateConnectionStateOnError(error)
                }

                // 唤醒屏幕
                controller.wakeUpScreen()

                // 启动前台服务
                val resolution = _videoResolution.value
                if (resolution != null) {
                    startForegroundService(
                        deviceName = deviceId,
                        width = resolution.first,
                        height = resolution.second
                    )
                }

                _connectionState.value = ConnectionState.Connected
                Result.success(true)
            } else {
                _connectionState.value = ConnectionState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                AdbBridge.clearConnection()
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }

        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_CONNECTION_FAILED.get()}: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            AdbBridge.clearConnection()
            Result.failure(e)
        }
    }

    /**
     * 直接通过 host:port 连接（会自动创建 ADB 连接）
     */
    suspend fun connect(
        host: String,
        port: Int = NetworkConstants.DEFAULT_ADB_PORT_INT,
        maxSize: Int? = null,
        bitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT,
        maxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS,
        videoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
        videoEncoder: String = "",
        enableAudio: Boolean = false,
        audioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
        audioEncoder: String = "",
        stayAwake: Boolean = false,
        turnScreenOff: Boolean = false,
        powerOffOnClose: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            connection.clearProgress()
            _connectionState.value = ConnectionState.Connecting

            val isUsbConnection = host.startsWith("usb:")
            val deviceId: String

            if (isUsbConnection) {
                deviceId = host
                connection.updateProgress(
                    com.mobile.scrcpy.android.core.domain.model.ConnectionStep.ADB_CONNECT,
                    com.mobile.scrcpy.android.core.domain.model.StepStatus.RUNNING,
                    "${AdbTexts.PROGRESS_VERIFYING_ADB.get()} ($deviceId)"
                )

                val conn = adbConnectionManager.getConnection(deviceId)
                if (conn == null) {
                    val errorMsg = "${AdbTexts.USB_CONNECT_FAILED.get()}: ${AdbTexts.ADB_DEVICE_NOT_CONNECTED.get()}"
                    connection.updateProgress(
                        com.mobile.scrcpy.android.core.domain.model.ConnectionStep.ADB_CONNECT,
                        com.mobile.scrcpy.android.core.domain.model.StepStatus.FAILED,
                        error = errorMsg
                    )
                    _connectionState.value = ConnectionState.Error(errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } else {
                deviceId = "$host:$port"
                connection.updateProgress(
                    com.mobile.scrcpy.android.core.domain.model.ConnectionStep.ADB_CONNECT,
                    com.mobile.scrcpy.android.core.domain.model.StepStatus.RUNNING,
                    "${AdbTexts.PROGRESS_VERIFYING_ADB.get()} ($host:$port)"
                )

                val connectResult = adbConnectionManager.connectDevice(host, port)
                if (connectResult.isFailure) {
                    val errorMsg = connectResult.exceptionOrNull()?.message ?: CommonTexts.ERROR_CONNECTION_FAILED.get()
                    connection.updateProgress(
                        com.mobile.scrcpy.android.core.domain.model.ConnectionStep.ADB_CONNECT,
                        com.mobile.scrcpy.android.core.domain.model.StepStatus.FAILED,
                        error = errorMsg
                    )
                    _connectionState.value = ConnectionState.Error(errorMsg)
                    return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception(errorMsg))
                }
            }

            val result = connectByDeviceId(
                deviceId = deviceId,
                maxSize = maxSize,
                bitRate = bitRate,
                maxFps = maxFps,
                videoCodec = videoCodec,
                videoEncoder = videoEncoder,
                enableAudio = enableAudio,
                audioCodec = audioCodec,
                audioEncoder = audioEncoder,
                stayAwake = stayAwake,
                turnScreenOff = turnScreenOff,
                powerOffOnClose = powerOffOnClose,
                skipAdbConnect = true
            )

            return@withContext result
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_CONNECTION_FAILED_DETAIL.get()}: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: CommonTexts.ERROR_CONNECTION_FAILED.get())
            Result.failure(e)
        }
    }

    /**
     * 断开连接
     */
    suspend fun disconnect(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Disconnecting

            // 重置重连状态
            reconnectAttempts = 0
            isReconnecting = false

            // 断开连接
            val result = connection.disconnect(currentDeviceId)

            // 清理状态
            _videoStreamState.value = null
            _audioStreamState.value = null
            _videoResolution.value = null
            _connectionState.value = ConnectionState.Disconnected

            LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${RemoteTexts.SCRCPY_DISCONNECTED_ADB_KEPT.get()}")
            result
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "断开连接失败: ${e.message}", e)
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
        pressure: Float = 1.0f
    ): Result<Boolean> = controller.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1,
        repeat: Int = 0,
        metaState: Int = 0
    ): Result<Boolean> = controller.sendKeyEvent(keyCode, action, repeat, metaState)

    suspend fun sendText(text: String): Result<Boolean> = controller.sendText(text)

    suspend fun setClipboardAndPaste(text: String): Result<Boolean> = controller.setClipboardAndPaste(text)

    suspend fun wakeUpScreen(): Result<Boolean> = controller.wakeUpScreen()

    /**
     * 当视频流出现错误时更新连接状态并触发重连
     */
    private fun updateConnectionStateOnError(message: String) {
        if (_connectionState.value is ConnectionState.Connected) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${CommonTexts.ERROR_CONNECTION_FAILED.get()}: $message")
            triggerReconnect()
        }
    }

    /**
     * 触发重连（带指数退避重试机制）
     */
    private fun triggerReconnect() {
        val deviceId = currentDeviceId
        if (deviceId == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "无法重连：设备 ID 为空")
            _connectionState.value = ConnectionState.Error("设备未连接")
            return
        }

        if (isReconnecting) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "重连正在进行中，跳过本次重连请求")
            return
        }

        if (reconnectAttempts >= ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "重连失败：已达最大重试次数 ${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}")
            _connectionState.value = ConnectionState.Error("重连失败：已达最大重试次数")
            reconnectAttempts = 0
            isReconnecting = false
            return
        }

        reconnectAttempts++
        isReconnecting = true

        LogManager.d(LogTags.SCRCPY_CLIENT, "========== 触发重连 (尝试 $reconnectAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}) ==========")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val delayMs = (1L shl (reconnectAttempts - 1)) * ScrcpyConstants.DEFAULT_RECONNECT_DELAY
                LogManager.d(LogTags.SCRCPY_CLIENT, "等待 ${delayMs}ms 后重连...")

                withContext(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Reconnecting
                }

                delay(delayMs)

                // 检查 ADB 连接状态
                LogManager.d(LogTags.SCRCPY_CLIENT, "检查 ADB 连接状态...")
                val conn = adbConnectionManager.getConnection(deviceId)
                if (conn == null) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ADB 连接不存在")
                    handleReconnectFailure("ADB 会话已断开，请重新连接设备")
                    return@launch
                }

                val testResult = conn.executeShell("echo test", retryOnFailure = false)
                if (testResult.isFailure) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ADB 连接不可用: ${testResult.exceptionOrNull()?.message}")
                    handleReconnectFailure("ADB 会话已断开，请重新连接设备")
                    return@launch
                }
                LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ADB 连接正常")

                // 尝试重新连接
                LogManager.d(LogTags.SCRCPY_CLIENT, "尝试重新连接...")
                withContext(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Connecting
                }

                val reconnectResult = connectByDeviceId(
                    deviceId = deviceId,
                    maxSize = lastMaxSize,
                    bitRate = lastBitRate,
                    maxFps = lastMaxFps,
                    videoCodec = lastVideoCodec,
                    enableAudio = lastEnableAudio,
                    stayAwake = lastStayAwake,
                    turnScreenOff = lastTurnScreenOff,
                    powerOffOnClose = lastPowerOffOnClose
                )

                if (reconnectResult.isSuccess) {
                    LogManager.d(LogTags.SCRCPY_CLIENT, "========== 重连成功 (尝试 $reconnectAttempts 次) ==========")
                    withContext(Dispatchers.Main) {
                        _connectionState.value = ConnectionState.Connected
                    }
                    isReconnecting = false
                } else {
                    val errorMsg = reconnectResult.exceptionOrNull()?.message ?: "未知错误"
                    LogManager.e(LogTags.SCRCPY_CLIENT, "========== 重连失败 (尝试 $reconnectAttempts 次) ==========")

                    if (isPermanentError(errorMsg)) {
                        LogManager.e(LogTags.SCRCPY_CLIENT, "检测到永久性错误，停止重试")
                        handleReconnectFailure("重连失败: $errorMsg")
                    } else if (reconnectAttempts < ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
                        LogManager.d(LogTags.SCRCPY_CLIENT, "将在延迟后再次尝试重连...")
                        isReconnecting = false
                        triggerReconnect()
                    } else {
                        handleReconnectFailure("重连失败: $errorMsg")
                    }
                }

                LogManager.d(LogTags.SCRCPY_CLIENT, "========== 重连流程结束 ==========")
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "========== 重连过程出错 ==========")
                LogManager.e(LogTags.SCRCPY_CLIENT, "错误: ${e.message}", e)

                if (reconnectAttempts < ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
                    isReconnecting = false
                    triggerReconnect()
                } else {
                    handleReconnectFailure("重连失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 处理重连失败
     */
    private suspend fun handleReconnectFailure(errorMessage: String) {
        withContext(Dispatchers.Main) {
            _connectionState.value = ConnectionState.Error(errorMessage)
        }
        reconnectAttempts = 0
        isReconnecting = false
    }

    /**
     * 判断是否是永久性错误（不应重试的错误）
     */
    private fun isPermanentError(errorMessage: String): Boolean {
        val permanentErrorKeywords = listOf(
            "设备未连接",
            "设备连接已断开",
            "ADB 会话已断开",
            "未授权",
            "权限被拒绝",
            "不支持",
            "无效的参数"
        )

        return permanentErrorKeywords.any { errorMessage.contains(it, ignoreCase = true) }
    }

    /**
     * 启动前台服务（首次连接或添加设备）
     */
    private fun startForegroundService(deviceName: String, width: Int, height: Int) {
        try {
            val deviceId = currentDeviceId ?: return

            val intent = Intent(context, ScrcpyForegroundService::class.java).apply {
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
     * 获取当前连接的设备 ID
     */
    fun getCurrentDeviceId(): String? = currentDeviceId
}

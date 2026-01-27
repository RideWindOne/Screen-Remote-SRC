package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.feature.scrcpy

import android.content.Context
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.core.domain.model.ConnectionProgress
import com.mobile.scrcpy.android.core.domain.model.ConnectionStep
import com.mobile.scrcpy.android.core.domain.model.StepStatus
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionMetadataReader
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionShellMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionSocketManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import kotlinx.coroutines.flow.StateFlow
import java.net.Socket

/**
 * Scrcpy 连接管理器 - 负责建立和管理 Scrcpy 连接
 * 
 * 这是一个协调器类，将连接管理的各个职责委托给专门的组件：
 * - ConnectionStateMachine: 状态管理和进度跟踪
 * - ConnectionSocketManager: Socket 连接管理
 * - ConnectionMetadataReader: 元数据读取和流创建
 * - ConnectionShellMonitor: Shell 输出监控
 * - ConnectionLifecycle: 连接/断开生命周期管理
 */
class ScrcpyConnection(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
    private val localPort: Int = 27183
) {
    // 组件
    private val stateMachine = ConnectionStateMachine()
    private val socketManager = ConnectionSocketManager(localPort)
    private val metadataReader = ConnectionMetadataReader(socketManager)
    private val shellMonitor = ConnectionShellMonitor()
    private val lifecycle = ConnectionLifecycle(
        context,
        adbConnectionManager,
        localPort,
        stateMachine,
        socketManager,
        metadataReader,
        shellMonitor
    )

    // 公开属性
    val videoSocket: Socket?
        get() = socketManager.videoSocket

    val audioSocket: Socket?
        get() = socketManager.audioSocket

    val controlSocket: Socket?
        get() = socketManager.controlSocket

    val currentScid: Int?
        get() = lifecycle.currentScid

    val connectionProgress: StateFlow<List<ConnectionProgress>>
        get() = stateMachine.connectionProgress

    /**
     * 更新连接进度
     */
    fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String = "",
        error: String? = null
    ) {
        stateMachine.updateProgress(step, status, message, error)
    }

    /**
     * 清空连接进度
     */
    fun clearProgress() {
        stateMachine.clearProgress()
    }

    /**
     * 建立连接
     */
    suspend fun connect(
        deviceId: String,
        maxSize: Int?,
        bitRate: Int,
        maxFps: Int,
        videoCodec: String,
        videoEncoder: String,
        enableAudio: Boolean,
        audioCodec: String,
        audioEncoder: String,
        stayAwake: Boolean,
        turnScreenOff: Boolean,
        powerOffOnClose: Boolean,
        skipAdbConnect: Boolean,
        onVideoResolution: (Int, Int) -> Unit
    ): Result<Pair<VideoStream?, AudioStream?>> {
        return lifecycle.connect(
            deviceId,
            maxSize,
            bitRate,
            maxFps,
            videoCodec,
            videoEncoder,
            enableAudio,
            audioCodec,
            audioEncoder,
            stayAwake,
            turnScreenOff,
            powerOffOnClose,
            skipAdbConnect,
            onVideoResolution
        )
    }

    /**
     * 监控 shell 输出
     */
    fun startShellMonitor(onError: (String) -> Unit) {
        shellMonitor.startMonitor(onError)
    }

    /**
     * 断开连接
     */
    suspend fun disconnect(deviceId: String?): Result<Boolean> {
        return lifecycle.disconnect(deviceId)
    }
}

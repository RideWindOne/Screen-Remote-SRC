package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.data.repository.SessionRepository
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager.killProcess
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal.cleanupOldResources
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal.connectSockets
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal.generateScid
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal.setupAdbConnection
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal.setupForwardAndPushServer
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal.startScrcpyServer
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 连接生命周期管理器 - 管理 Scrcpy 连接和断开的完整生命周期
 *
 * ## 职责
 * - 提供公开 API：connect() 和 disconnect() 方法
 * - 编排连接建立的完整流程（ADB → Server → Socket → 流创建）
 * - 编排断开连接的清理流程（Socket → Forward → Server → 资源清理）
 * - 管理连接健康监控和当前会话状态
 *
 * ## 拆分结构
 * 本文件保留核心流程编排逻辑，具体实现已拆分到以下内部文件：
 *
 * - **ConnectionLifecycle.kt** (本文件)
 *   - 类定义和公开方法（connect, disconnect）
 *   - 连接流程编排（步骤 1-7）
 *   - 断开流程编排（清理顺序控制）
 *   - 依赖注入和属性管理
 *
 * - **internal/AdbConnectionSetup.kt**
 *   - setupAdbConnection(): 建立 ADB 连接
 *   - verifyAndGetAdbConnection(): 验证并获取 ADB 连接
 *   - cleanupOldResources(): 清理旧的 Forward 和进程
 *
 * - **internal/ServerSetup.kt**
 *   - setupForwardAndPushServer(): 设置 Forward 并推送 Server
 *   - startScrcpyServer(): 启动 scrcpy-server 进程
 *   - buildScrcpyCommand(): 构建 Server 启动命令
 *
 * - **internal/CodecDetection.kt**
 *   - detectRemoteEncodersAfterPush(): 推送后检测远程编解码器
 *   - fetchRemoteEncoders(): 获取远程编解码器列表
 *   - processCodecSelection(): 处理编解码器选择逻辑
 *
 * - **internal/SocketSetup.kt**
 *   - connectSockets(): 连接视频、音频和控制 Socket
 *   - generateScid(): 生成会话 ID
 *   - findAvailablePort(): 查找可用端口
 *
 * ## 使用方式
 * ```kotlin
 * val lifecycle = ConnectionLifecycle(...)
 * val result = lifecycle.connect()  // 建立连接
 * lifecycle.disconnect()            // 断开连接
 * ```
 *
 * @see com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal
 */
class ConnectionLifecycle(
    internal val context: Context,
    internal val adbConnectionManager: AdbConnectionManager,
    private val stateMachine: ConnectionStateMachine,
    internal val socketManager: ConnectionSocketManager,
    private val metadataReader: ConnectionMetadataReader,
    internal val shellMonitor: ConnectionShellMonitor,
    private val onVideoStreamReady: (VideoStream?) -> Unit,
    private val onAudioStreamReady: (AudioStream?) -> Unit,
) {
    // SessionRepository 作为基础设施，可以直接使用
    private val sessionRepository =
        SessionRepository(context)
    internal var localPort: Int = 0
    var currentScid: Int? = null
        internal set
    val healthMonitor = ConnectionHealthMonitor()

    /**
     * 建立连接（从 CurrentSession 获取配置）
     */
    suspend fun connect(): Result<Pair<VideoStream?, AudioStream?>> =
        withContext(Dispatchers.IO) {
            try {
                val session = CurrentSession.current
                val options = session.options

                // 步骤 1: 建立/验证 ADB 连接并分配端口
                val connection = setupAdbConnection(options.host, options.port)

                // 步骤 2: 清理旧资源
                cleanupOldResources(connection)

                // 步骤 3: 生成 SCID 并设置 Forward
                val scid = generateScid()
                currentScid = scid
                val socketName = "scrcpy_%08x".format(scid)
                setupForwardAndPushServer(connection, socketName)
                
                // 步骤 3.5: 设置 Socket 管理器的本地端口
                socketManager.setLocalPort(localPort)

                // 步骤 4: 启动 scrcpy-server
                startScrcpyServer(connection, scid)

                // 步骤 5: 连接 Socket
                connectSockets(options)

                // 步骤 6: 启动健康监控
                healthMonitor.startMonitoring(
                    videoSocket = socketManager.videoSocket,
                    audioSocket = socketManager.audioSocket,
                    controlSocket = socketManager.controlSocket,
                    onConnectionLost = {
                        LogManager.w(LogTags.SCRCPY_CLIENT, "健康监控检测到连接丢失")
                        CurrentSession.currentOrNull?.handleEvent(
                            SessionEvent.SocketError("Video: Socket 连接丢失"),
                        )
                    },
                )

                // 步骤 7: 读取元数据并创建流
                val (videoStream, audioStream) =
                    metadataReader.readMetadataAndCreateStreams(
                        options.enableAudio,
                        options.keyFrameInterval,
                        session.onVideoResolution,
                    )

                // 通知流已就绪
                onVideoStreamReady(videoStream)
                onAudioStreamReady(audioStream)

                Result.success(Pair(videoStream, audioStream))
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "连接失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 断开连接
     * 清理顺序：Shell监控 → Socket → Forward → Server → 事件总线
     */
    suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            try {
                val session = CurrentSession.currentOrNull
                val options = session?.options

                // 1. 关闭所有 Socket（停止数据传输）
                socketManager.closeAllSockets()
                delay(50) // 等待 Socket 完全关闭

                // 2. 停止 Shell 监控（避免继续读取错误）
                shellMonitor.stopMonitor()
                shellMonitor.closeShellStream()

                // 3. 移除 ADB Forward
                if (options != null) {
                    val deviceId = if (options.isUsbConnection()) options.host else "${options.host}:${options.port}"
                    val connection = adbConnectionManager.getConnection(deviceId)
                    if (connection != null) {
                        try {
                            connection.removeAdbForward(localPort)
                            LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_REMOVED_ADB_FORWARD.get())
                        } catch (e: Exception) {
                            LogManager.w(
                                LogTags.SCRCPY_CLIENT,
                                "${RemoteTexts.SCRCPY_REMOVE_FORWARD_FAILED.get()}: ${e.message}",
                            )
                        }
                    }
                }

                // 4. 终止服务器进程
                if (options != null && currentScid != null) {
                    val deviceId = if (options.isUsbConnection()) options.host else "${options.host}:${options.port}"
                    val connection = adbConnectionManager.getConnection(deviceId)
                    if (connection != null) {
                        try {
                            val scidHex = String.format("%08x", currentScid)
                            killProcess(
                                connection,
                                "scrcpy.*scid=$scidHex",
                            )

                            LogManager.d(
                                LogTags.SCRCPY_CLIENT,
                                "${RemoteTexts.SCRCPY_TERMINATED_SERVER_PROCESS.get()} (scid=$scidHex)",
                            )
                        } catch (e: Exception) {
                            LogManager.w(
                                LogTags.SCRCPY_CLIENT,
                                "${RemoteTexts.SCRCPY_TERMINATE_SERVER_FAILED.get()}: ${e.message}",
                            )
                        }
                    }
                }

                stateMachine.clearProgress()
                currentScid = null

                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "断开连接失败: ${e.message}", e)
                Result.failure(e)
            }
        }
}

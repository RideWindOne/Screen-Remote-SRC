package com.screen.remote.android.infrastructure.scrcpy.connection

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager.killProcess
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.cleanupOldResources
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.completeScrcpyServerStartup
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.connectSockets
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.detectRemoteEncodersAfterPush
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.generateScid
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.setupAdbConnection
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.setupForwardAndPushServer
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.shouldDetectAudioCodec
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.shouldDetectVideoCodec
import com.screen.remote.android.infrastructure.scrcpy.connection.internal.startScrcpyServer
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.session.internal.rememberNegotiatedAudioCodec
import com.screen.remote.android.infrastructure.scrcpy.session.internal.rememberNegotiatedVideoCodec
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalTrigger
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketType
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

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
 * @see com.screen.remote.android.infrastructure.scrcpy.connection.internal
 */
class ConnectionLifecycle(
    internal val context: Context,
    internal val adbConnectionManager: AdbConnectionManager,
    private val stateMachine: ConnectionStateMachine,
    internal val sessionContext: SessionContext,
    internal val socketManager: ConnectionSocketManager,
    private val metadataReader: ConnectionMetadataReader,
    internal val shellMonitor: ConnectionShellMonitor,
    internal val issueTracker: SessionIssueTracker,
    private val onVideoStreamReady: (VideoStream?) -> Unit,
    private val onAudioStreamReady: (AudioStream?) -> Unit,
) {
    @Volatile
    private var activeConnection: ActiveScrcpyConnection? = null

    val activeDeviceId: String?
        get() = activeConnection?.deviceId

    val currentScid: Int?
        get() = activeConnection?.scid
    val healthMonitor = ConnectionHealthMonitor()
    internal val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private val adbRaceGeneration = AtomicLong(0)
    private var codecDetectionJob: Job? = null

    internal fun beginAdbRace(): Long = adbRaceGeneration.incrementAndGet()

    internal fun isCurrentAdbRace(generation: Long): Boolean = adbRaceGeneration.get() == generation

    /**
     * 建立连接（从 CurrentSession 获取配置）
     */
    suspend fun connect(): Result<Pair<VideoStream?, AudioStream?>> =
        lifecycleMutex.withLock { connectInternal() }

    private suspend fun connectInternal(): Result<Pair<VideoStream?, AudioStream?>> =
        withContext(Dispatchers.IO) {
            var codecDetectionStarted = false
            var ownsConnectionCleanup = false
            try {
                val session = sessionContext.currentSession() ?: throw IllegalStateException("Session does not exist")
                val initialOptions = session.options
                val previousConnection = activeConnection
                // 步骤 1: 建立/验证 ADB 连接并分配端口
                val prepared = setupAdbConnection(initialOptions, session)
                val connection = prepared.connection
                val options = session.options
                val needsCodecDetection = shouldRunRemoteCodecDetectionInBackground(options)
                codecDetectionStarted = needsCodecDetection

                // 步骤 2: 在创建新 forward 前完成旧资源清理，避免端口复用时误删新映射。
                ownsConnectionCleanup = true
                cleanupOldResources(previousConnection)

                // 步骤 3: 生成 SCID 并设置 Forward
                val scid = generateScid()
                val socketName = "scrcpy_%08x".format(scid)
                val attempt =
                    ActiveScrcpyConnection(
                        sessionId = session.sessionId,
                        adbConnection = connection,
                        localPort = prepared.localPort,
                        scid = scid,
                        socketName = socketName,
                        tunnelMode = options.config.tunnelMode,
                    )
                activeConnection = attempt
                setupForwardAndPushServer(
                    connection = connection,
                    socketName = socketName,
                    localPort = attempt.localPort,
                    tunnelMode = attempt.tunnelMode,
                    onServerAvailable =
                        if (needsCodecDetection) {
                            {
                                startRemoteCodecDetectionInBackground(
                                    connection = connection,
                                    expectedSessionId = session.sessionId,
                                )
                            }
                        } else {
                            null
                        },
                )

                // 自动选择结果必须在正式启动命令读取会话配置前完成。
                // 检测本身与端口等本地准备并行，但不能跨过 server 启动边界。
                if (codecDetectionStarted) {
                    codecDetectionJob?.join()
                }

                // 步骤 4: 启动 scrcpy-server
                val launchOptions = session.options
                val canProbeServerSocketDirectly = attempt.tunnelMode == ScrcpyTunnelMode.DIRECT_ADB
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    if (canProbeServerSocketDirectly) {
                        "Server startup mode: direct localabstract, skip log settle, detect the first video socket"
                    } else {
                        "Server startup mode: ADB forward, wait for the server to be ready and then build the link according to the protocol sequence"
                    },
                )
                startScrcpyServer(
                    connection = connection,
                    scid = scid,
                    options = launchOptions,
                    waitForReady = !canProbeServerSocketDirectly,
                )

                // 步骤 5: 连接 Socket
                connectSockets(
                    options = launchOptions,
                    connection = connection,
                    socketName = socketName,
                    localPort = attempt.localPort,
                    tunnelMode = attempt.tunnelMode,
                )

                if (canProbeServerSocketDirectly) {
                    completeScrcpyServerStartup(scid)
                }

                // 步骤 6: 先读取媒体头；远端可能用 audio codec id=0 明确关闭音频。
                val (videoStream, audioStream) =
                    metadataReader.readMetadataAndCreateStreams(
                        launchOptions.config.enableAudio,
                        session.onVideoResolution,
                    )
                videoStream?.let { session.rememberNegotiatedVideoCodec(it.codec) }
                audioStream?.let { session.rememberNegotiatedAudioCodec(it.codec) }

                // 步骤 7: 根据媒体头的最终结果启动健康监控。
                healthMonitor.startMonitoring(
                    videoSocket = socketManager.videoSocket,
                    audioSocket = socketManager.audioSocket.takeIf { audioStream != null },
                    controlSocket = socketManager.controlSocket,
                    onConnectionLostCallback = {
                        LogManager.w(LogTags.SCRCPY_CLIENT, "Health monitoring detects connection loss")
                        sessionContext.emit(
                            SessionEvent.SocketError(
                                SocketIssue(
                                    kind = SocketIssueKind.HealthCheckFailed,
                                    socketType = SocketType.Video,
                                    detail = "Socket connection lost",
                                ),
                            ),
                        )
                    },
                )

                // 通知流已就绪
                onVideoStreamReady(videoStream)
                onAudioStreamReady(audioStream)

                Result.success(Pair(videoStream, audioStream))
            } catch (cancelled: CancellationException) {
                // 用户取消必须保持协程取消语义，但已经创建的 scrcpy 资源仍需原子回收。
                withContext(NonCancellable) {
                    if (codecDetectionStarted) {
                        codecDetectionJob?.cancelAndJoin()
                        codecDetectionJob = null
                    }
                    if (ownsConnectionCleanup) {
                        disconnectInternal().onFailure { cleanupError ->
                            LogManager.w(
                                LogTags.SCRCPY_CLIENT,
                                "Incomplete resource cleanup after canceling connection: ${cleanupError.message}",
                            )
                        }
                    }
                }
                throw cancelled
            } catch (e: Exception) {
                if (codecDetectionStarted) {
                    codecDetectionJob?.cancelAndJoin()
                    codecDetectionJob = null
                }
                shellMonitor.dumpDiagnostics("connect-failed")
                LogManager.e(LogTags.SCRCPY_CLIENT, "Connection failed: ${e.message}")
                if (ownsConnectionCleanup) {
                    disconnectInternal().onFailure { cleanupError ->
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "Incomplete resource cleanup after connection failure: ${cleanupError.message}",
                        )
                    }
                }
                Result.failure(e)
            }
        }

    private suspend fun startRemoteCodecDetectionInBackground(
        connection: AdbConnection,
        expectedSessionId: String,
    ) {
        codecDetectionJob?.cancelAndJoin()
        codecDetectionJob =
            backgroundScope.launch {
                runCatching {
                    detectRemoteEncodersAfterPush(connection, expectedSessionId)
                }.onFailure { error ->
                    LogManager.w(LogTags.SCRCPY_CLIENT, "Background detection of remote codec failed: ${error.message}")
                }
            }
    }

    private fun shouldRunRemoteCodecDetectionInBackground(options: ScrcpyOptions): Boolean =
        shouldDetectVideoCodec(options) || shouldDetectAudioCodec(options)

    /**
     * 断开连接
     * 清理顺序：Shell监控 → Socket → Forward → Server → 事件总线
     */
    suspend fun disconnect(): Result<Boolean> = lifecycleMutex.withLock { disconnectInternal() }

    private suspend fun disconnectInternal(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val connectionSnapshot = activeConnection
            try {
                codecDetectionJob?.cancelAndJoin()
                codecDetectionJob = null
                healthMonitor.stopMonitoring()

                // 1. 关闭所有 Socket（停止数据传输）
                socketManager.closeAllSockets()
                delay(50.milliseconds) // 等待 Socket 完全关闭

                // 2. 停止 Shell 监控（避免继续读取错误）
                shellMonitor.stopMonitor()
                shellMonitor.closeShellStream()

                // 3. 移除 ADB Forward
                if (connectionSnapshot?.tunnelMode == ScrcpyTunnelMode.ADB_FORWARD) {
                    try {
                        connectionSnapshot.adbConnection
                            .removeAdbForward(connectionSnapshot.localPort, ForwardRemovalTrigger.Disconnect)
                        LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_REMOVED_ADB_FORWARD.english)
                    } catch (e: Exception) {
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "${RemoteTexts.SCRCPY_REMOVE_FORWARD_FAILED.english}: ${e.message}",
                        )
                    }
                }

                // 4. 终止服务器进程
                if (connectionSnapshot != null) {
                    try {
                        val scidHex = String.format("%08x", connectionSnapshot.scid)
                        killProcess(
                            connectionSnapshot.adbConnection,
                            "scrcpy.*scid=$scidHex",
                        )

                        LogManager.d(
                            LogTags.SCRCPY_CLIENT,
                            "${RemoteTexts.SCRCPY_TERMINATED_SERVER_PROCESS.english} (scid=$scidHex)",
                        )
                    } catch (e: Exception) {
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "${RemoteTexts.SCRCPY_TERMINATE_SERVER_FAILED.english}: ${e.message}",
                        )
                    }
                }

                stateMachine.clearProgress()

                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to disconnect: ${e.message}", e)
                Result.failure(e)
            } finally {
                if (activeConnection === connectionSnapshot) {
                    activeConnection = null
                }
            }
        }
}

package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.constants.ScrcpyConstants.SOCKET_READ_TIMEOUT
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketConnectContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketConnectingContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketDisconnectContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketDisconnectKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketType
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Socket 连接管理器
 * 负责管理视频、音频和控制 Socket 的连接和关闭
 */
class ConnectionSocketManager(
    private val sessionContext: SessionContext,
) {
    private companion object {
        private const val SOCKET_CONNECT_MAX_ATTEMPTS = 3
        private const val SOCKET_RETRY_DELAY_MS = 150L
        private const val SOCKET_ROLE_PROBE_TIMEOUT_MS = 250
        private const val SOCKET_ROLE_PROBE_ROUNDS = 6
    }

    private var localPort: Int = 0

    var videoSocket: Socket? = null
        private set

    var audioSocket: Socket? = null
        private set

    var controlSocket: Socket? = null
        private set

    /**
     * 设置本地端口
     */
    fun setLocalPort(port: Int) {
        localPort = port
    }

    /**
     * 连接所有需要的 Socket
     */
    suspend fun connectSockets(
        connection: AdbConnection,
        socketName: String,
        enableAudio: Boolean,
        useAdbForward: Boolean,
    ) = withContext(Dispatchers.IO) {
        try {
            sessionContext.emit(
                SessionEvent.SocketConnecting(
                    SocketConnectingContext(
                        localPort = localPort,
                        expectedSocketCount = if (enableAudio) 3 else 2,
                        audioEnabled = enableAudio,
                    ),
                ),
            )

            connectSocketsWithRetry(
                connection = connection,
                socketName = socketName,
                enableAudio = enableAudio,
                useAdbForward = useAdbForward,
            )
            LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_VIDEO_SOCKET_CONNECTED.get())
            sessionContext.emit(
                SessionEvent.SocketConnected(
                    socketType = SocketType.Video,
                    context =
                        SocketConnectContext(
                            localPort = localPort,
                            dummyByteConfirmed = true,
                        ),
                ),
            )

            // audio 和 control socket 不读取 dummy byte，直接标记为已连接
            if (enableAudio) {
                sessionContext.emit(
                    SessionEvent.SocketConnected(
                        socketType = SocketType.Audio,
                        context =
                            SocketConnectContext(
                                localPort = localPort,
                                dummyByteConfirmed = false,
                            ),
                    ),
                )
            }

            sessionContext.emit(
                SessionEvent.SocketConnected(
                    socketType = SocketType.Control,
                    context =
                        SocketConnectContext(
                            localPort = localPort,
                            dummyByteConfirmed = false,
                        ),
                ),
            )
        } catch (e: Exception) {
            // 推送 Socket 错误事件
            val socketName =
                when {
                    videoSocket == null -> "Video"
                    enableAudio && audioSocket == null -> "Audio"
                    controlSocket == null -> "Control"
                    else -> "Video"
                }
            val type =
                when (socketName) {
                    "Audio" -> SocketType.Audio
                    "Control" -> SocketType.Control
                    else -> SocketType.Video
                }
            sessionContext.emit(
                SessionEvent.SocketError(
                    SocketIssue(
                        kind = SocketIssueKind.ConnectFailed,
                        socketType = type,
                        detail = e.message ?: "Unknown error",
                    ),
                ),
            )
            closeAllSockets()
            throw IOException("${RemoteTexts.SCRCPY_SOCKET_CONNECTION_FAILED.get()} -> ${e.message}", e)
        }
    }

    private suspend fun connectSocketsWithRetry(
        connection: AdbConnection,
        socketName: String,
        enableAudio: Boolean,
        useAdbForward: Boolean,
    ) {
        var lastError: Exception? = null

        repeat(SOCKET_CONNECT_MAX_ATTEMPTS) { attemptIndex ->
            val attempt = attemptIndex + 1
            var videoCandidate: Socket? = null
            var audioCandidate: Socket? = null
            var controlCandidate: Socket? = null

            try {
                // 先连接所有流再读取数据，避免 server 端 accept 串行导致死锁。
                videoCandidate = createChannel(connection, socketName, "video", useAdbForward)
                LogManager.d(LogTags.SCRCPY_CLIENT, "Video socket connected")

                if (enableAudio) {
                    audioCandidate = createChannel(connection, socketName, "audio", useAdbForward)
                    LogManager.d(LogTags.SCRCPY_CLIENT, "Audio socket connected")
                }

                controlCandidate = createChannel(connection, socketName, "control", useAdbForward)
                LogManager.d(LogTags.SCRCPY_CLIENT, "Control socket connected")

                if (enableAudio) {
                    waitForDummyByte(videoCandidate, "video")
                    videoSocket = videoCandidate
                    audioSocket = audioCandidate
                    controlSocket = controlCandidate
                } else {
                    val resolvedSockets =
                        resolveVideoAndControlSocketsWithoutAudio(
                            firstSocket = videoCandidate,
                            secondSocket = controlCandidate,
                        )
                    videoSocket = resolvedSockets.videoSocket
                    controlSocket = resolvedSockets.controlSocket
                }

                return
            } catch (e: Exception) {
                lastError = e
                closeSocketSilently(videoCandidate)
                closeSocketSilently(audioCandidate)
                closeSocketSilently(controlCandidate)
                videoSocket = null
                audioSocket = null
                controlSocket = null

                if (attempt < SOCKET_CONNECT_MAX_ATTEMPTS) {
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "Socket 建链失败，准备重试 $attempt/$SOCKET_CONNECT_MAX_ATTEMPTS: ${e.message}",
                    )
                    Thread.sleep(SOCKET_RETRY_DELAY_MS)
                }
            }
        }

        throw IOException(lastError?.message ?: "Socket 建链失败")
    }

    private fun resolveVideoAndControlSocketsWithoutAudio(
        firstSocket: Socket,
        secondSocket: Socket,
    ): ResolvedVideoControlSockets {
        val firstResult =
            waitForDummyByteWithFallback(
                primarySocket = firstSocket,
                primaryLabel = "video",
                secondarySocket = secondSocket,
                secondaryLabel = "control",
            )

        return when (firstResult) {
            DummySocketResolution.Primary -> ResolvedVideoControlSockets(videoSocket = firstSocket, controlSocket = secondSocket)
            DummySocketResolution.Secondary -> {
                LogManager.w(
                    LogTags.SCRCPY_CLIENT,
                    "检测到 dummy byte 出现在 control socket，已自动交换 video/control 角色。",
                )
                ResolvedVideoControlSockets(videoSocket = secondSocket, controlSocket = firstSocket)
            }
        }
    }

    /**
     * 等待并验证 dummy byte（Server 准备就绪信号）
     * 参考：scrcpy Server 在 accept 后立即发送 dummy byte (0x00)
     */
    private fun waitForDummyByte(
        socket: Socket,
        socketType: String,
    ) {
        when (readDummyByte(socket, socketType, maxRetries = 3, retryDelayMs = 200L)) {
            is DummyReadResult.Success -> return
            is DummyReadResult.Closed ->
                throw IOException("$socketType socket -> Server 未发送 dummy byte（连接已关闭）")
            is DummyReadResult.Timeout ->
                throw IOException("$socketType socket -> 读取 dummy byte 超时")
        }
    }

    private fun waitForDummyByteWithFallback(
        primarySocket: Socket,
        primaryLabel: String,
        secondarySocket: Socket,
        secondaryLabel: String,
    ): DummySocketResolution {
        val primaryTimeout = primarySocket.soTimeout
        val secondaryTimeout = secondarySocket.soTimeout

        primarySocket.soTimeout = SOCKET_ROLE_PROBE_TIMEOUT_MS
        secondarySocket.soTimeout = SOCKET_ROLE_PROBE_TIMEOUT_MS

        try {
            repeat(SOCKET_ROLE_PROBE_ROUNDS) { round ->
                when (readDummyByte(primarySocket, primaryLabel, maxRetries = 1, retryDelayMs = 0L, logRetries = false)) {
                    is DummyReadResult.Success -> return DummySocketResolution.Primary
                    is DummyReadResult.Closed ->
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "$primaryLabel socket 在 dummy byte 探测阶段已关闭，继续尝试另一条连接。",
                        )
                    is DummyReadResult.Timeout -> Unit
                }

                when (readDummyByte(secondarySocket, secondaryLabel, maxRetries = 1, retryDelayMs = 0L, logRetries = false)) {
                    is DummyReadResult.Success -> return DummySocketResolution.Secondary
                    is DummyReadResult.Closed ->
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "$secondaryLabel socket 在 dummy byte 探测阶段已关闭。",
                        )
                    is DummyReadResult.Timeout -> Unit
                }

                if (round < SOCKET_ROLE_PROBE_ROUNDS - 1) {
                    Thread.sleep(SOCKET_RETRY_DELAY_MS)
                }
            }
        } finally {
            primarySocket.soTimeout = primaryTimeout
            secondarySocket.soTimeout = secondaryTimeout
        }

        throw IOException("video/control sockets 均未收到 dummy byte")
    }

    private fun readDummyByte(
        socket: Socket,
        socketType: String,
        maxRetries: Int,
        retryDelayMs: Long,
        logRetries: Boolean = true,
    ): DummyReadResult {
        val inputStream = socket.getInputStream()

        repeat(maxRetries) { retryIndex ->
            try {
                val dummyByte = inputStream.read()
                if (dummyByte == -1) {
                    if (logRetries && retryIndex < maxRetries - 1) {
                        LogManager.w(
                            LogTags.SCRCPY_CLIENT,
                            "$socketType socket: Server 未发送 dummy byte，重试 ${retryIndex + 1}/$maxRetries",
                        )
                    }
                    if (retryIndex < maxRetries - 1) {
                        Thread.sleep(retryDelayMs)
                        return@repeat
                    }
                    return DummyReadResult.Closed
                }

                if (dummyByte != 0x00) {
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "$socketType socket: 收到非预期的 dummy byte: 0x${dummyByte.toString(16).padStart(2, '0')}",
                    )
                }

                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "$socketType socket: Dummy byte 验证通过 (0x${dummyByte.toString(16).padStart(2, '0')})",
                )
                return DummyReadResult.Success(dummyByte)
            } catch (e: java.net.SocketTimeoutException) {
                if (logRetries && retryIndex < maxRetries - 1) {
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "$socketType socket: 读取 dummy byte 超时，重试 ${retryIndex + 1}/$maxRetries",
                    )
                }
                if (retryIndex < maxRetries - 1) {
                    Thread.sleep(retryDelayMs)
                    return@repeat
                }
                return DummyReadResult.Timeout
            }
        }

        return DummyReadResult.Timeout
    }

    /**
     * 创建并连接 Socket
     */
    private suspend fun createChannel(
        connection: AdbConnection,
        socketName: String,
        type: String,
        useAdbForward: Boolean,
    ): Socket =
        if (useAdbForward) {
            createAndConnectSocket(type)
        } else {
            connection.openLocalAbstractSocket(socketName).getOrElse { error ->
                throw IOException("Failed to open $type adb stream: ${error.message}", error)
            }.also { socket ->
                socket.tcpNoDelay = true
                socket.receiveBufferSize = NetworkConstants.SOCKET_RECEIVE_BUFFER_SIZE
                socket.sendBufferSize = NetworkConstants.SOCKET_SEND_BUFFER_SIZE
                socket.soTimeout = SOCKET_READ_TIMEOUT.toInt()
            }
        }

    private fun createAndConnectSocket(type: String): Socket {
        val socket = Socket()

        // TCP 优化：禁用 Nagle 算法，降低延迟（参考 scrcpy 原生对 control_socket 的优化）
        socket.tcpNoDelay = true

        // Socket 缓冲区优化（参考 adb-mobile-ios 的 CHUNK_SIZE 设置）
        socket.receiveBufferSize = NetworkConstants.SOCKET_RECEIVE_BUFFER_SIZE
        socket.sendBufferSize = NetworkConstants.SOCKET_SEND_BUFFER_SIZE

        // 读取超时：使用固定的 10 秒超时（用于 dummy byte 和元数据读取）
        socket.soTimeout = SOCKET_READ_TIMEOUT.toInt()

        try {
            socket.connect(
                InetSocketAddress(NetworkConstants.LOCALHOST, localPort),
                NetworkConstants.CONNECT_TIMEOUT_MS.toInt(),
            )
            return socket
        } catch (e: Exception) {
            socket.close()
            throw IOException("Failed to connect $type socket: ${e.message}", e)
        }
    }

    private fun closeSocketSilently(socket: Socket?) {
        if (socket == null) {
            return
        }
        runCatching { socket.close() }
    }

    /**
     * 关闭所有 Socket
     */
    fun closeAllSockets() {
        closeVideoSocket()
        closeAudioSocket()
        closeControlSocket()
    }

    fun closeVideoSocket() {
        val socket = videoSocket ?: return
        try {
            socket.close()
            sessionContext.emit(
                SessionEvent.SocketDisconnected(
                    socketType = SocketType.Video,
                    context =
                        SocketDisconnectContext(
                            kind = SocketDisconnectKind.LocalClose,
                            detail = "Video socket closed locally",
                        ),
                ),
            )
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close video socket: ${e.message}")
        } finally {
            videoSocket = null
        }
    }

    fun closeAudioSocket() {
        val socket = audioSocket ?: return
        try {
            socket.close()
            sessionContext.emit(
                SessionEvent.SocketDisconnected(
                    socketType = SocketType.Audio,
                    context =
                        SocketDisconnectContext(
                            kind = SocketDisconnectKind.LocalClose,
                            detail = "Audio socket closed locally",
                        ),
                ),
            )
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close audio socket: ${e.message}")
        } finally {
            audioSocket = null
        }
    }

    fun closeControlSocket() {
        closeControlSocket(notifySession = true)
    }

    fun dropControlSocket() {
        closeControlSocket(notifySession = false)
    }

    private fun closeControlSocket(notifySession: Boolean) {
        val socket = controlSocket ?: return
        try {
            socket.close()
            LogManager.d(LogTags.SCRCPY_CLIENT, "Control socket closed")
            if (notifySession) {
                sessionContext.emit(
                    SessionEvent.SocketDisconnected(
                        socketType = SocketType.Control,
                        context =
                            SocketDisconnectContext(
                                kind = SocketDisconnectKind.LocalClose,
                                detail = "Control socket closed locally",
                            ),
                    ),
                )
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close control socket: ${e.message}")
        } finally {
            controlSocket = null
        }
    }

    private data class ResolvedVideoControlSockets(
        val videoSocket: Socket,
        val controlSocket: Socket,
    )

    private enum class DummySocketResolution {
        Primary,
        Secondary,
    }

    private sealed interface DummyReadResult {
        data class Success(
            val value: Int,
        ) : DummyReadResult

        data object Closed : DummyReadResult

        data object Timeout : DummyReadResult
    }
}

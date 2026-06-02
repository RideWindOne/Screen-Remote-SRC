package com.screen.remote.android.infrastructure.scrcpy.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.NetworkConstants
import com.screen.remote.android.core.common.constants.ScrcpyConstants.SOCKET_READ_TIMEOUT
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketConnectContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketConnectingContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketDisconnectContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketDisconnectKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SocketType
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private const val DIRECT_VIDEO_CONNECT_TIMEOUT_MS = 10_000L
        private const val DIRECT_VIDEO_CONNECT_RETRY_DELAY_MS = 30L
        private const val SOCKET_DUMMY_BYTE_MAX_RETRIES = 2
        private const val SOCKET_DUMMY_BYTE_RETRY_DELAY_MS = 80L
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
        shouldAbortDirectProbe: () -> Boolean = { false },
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

            connectSocketsOnce(
                connection = connection,
                socketName = socketName,
                enableAudio = enableAudio,
                useAdbForward = useAdbForward,
                shouldAbortDirectProbe = shouldAbortDirectProbe,
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

    private suspend fun connectSocketsOnce(
        connection: AdbConnection,
        socketName: String,
        enableAudio: Boolean,
        useAdbForward: Boolean,
        shouldAbortDirectProbe: () -> Boolean,
    ) {
        var socketCandidates: Map<String, Socket>? = null
        try {
            socketCandidates = openSocketCandidatesInProtocolOrder(
                connection = connection,
                socketName = socketName,
                useAdbForward = useAdbForward,
                enableAudio = enableAudio,
                shouldAbortDirectProbe = shouldAbortDirectProbe,
            )

            val videoCandidate = socketCandidates.getValue("video")
            waitForDummyByte(videoCandidate, "video")

            videoSocket = videoCandidate
            audioSocket = socketCandidates["audio"]
            controlSocket = socketCandidates.getValue("control")
        } catch (e: Exception) {
            socketCandidates?.values?.forEach(::closeSocketSilently)
            videoSocket = null
            audioSocket = null
            controlSocket = null
            // scrcpy assigns roles to accepted sockets once. Reopening another trio against the
            // same server cannot restart its accept sequence; a retry requires a new server/SCID.
            throw e
        }
    }

    /**
     * scrcpy server 按 accept() 顺序将连接固定映射为 video -> audio -> control。
     * 协议没有客户端 socket 角色握手，所以建链顺序必须与 server 保持一致。
     */
    private suspend fun openSocketCandidatesInProtocolOrder(
        connection: AdbConnection,
        socketName: String,
        useAdbForward: Boolean,
        enableAudio: Boolean,
        shouldAbortDirectProbe: () -> Boolean,
    ): Map<String, Socket> {
        val sockets = linkedMapOf<String, Socket>()
        try {
            openScrcpyChannelsSequentially(enableAudio, sockets) { type ->
                val socket =
                    if (type == "video" && !useAdbForward) {
                        createDirectVideoChannelWhenReady(connection, socketName, shouldAbortDirectProbe)
                    } else {
                        createChannel(connection, socketName, type, useAdbForward)
                    }
                LogManager.d(LogTags.SCRCPY_CLIENT, "$type socket connected")
                socket
            }
            return sockets
        } catch (e: Exception) {
            sockets.values.forEach(::closeSocketSilently)
            throw e
        }
    }

    /**
     * localabstract 尚未创建时，ADB open 会直接失败且不会被 server accept，因此只重试
     * 第一条 video 通道是安全的。forward 模式不能使用此策略：本地 TCP accept 可能先成功，
     * 随后远端 open 才失败，从而破坏 scrcpy 的 socket 角色顺序。
     */
    private suspend fun createDirectVideoChannelWhenReady(
        connection: AdbConnection,
        socketName: String,
        shouldAbort: () -> Boolean,
    ): Socket {
        val startedAtNanos = System.nanoTime()
        val deadlineNanos = System.nanoTime() + DIRECT_VIDEO_CONNECT_TIMEOUT_MS * 1_000_000L
        var lastError: Throwable? = null
        var attemptCount = 0

        while (System.nanoTime() < deadlineNanos) {
            if (shouldAbort()) {
                throw IOException("scrcpy-server failed before video socket became ready", lastError)
            }

            attemptCount++
            connection.openLocalAbstractSocket(socketName)
                .onSuccess { socket ->
                    val durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                    LogManager.i(
                        LogTags.SCRCPY_CLIENT,
                        "Direct localabstract 已建立: $socketName channel=video attempts=$attemptCount durationMs=$durationMs",
                    )
                    return socket.also(::configureSocket)
                }.onFailure { error ->
                    lastError = error
                }

            delay(DIRECT_VIDEO_CONNECT_RETRY_DELAY_MS)
        }

        throw IOException("Failed to open video adb stream: ${lastError?.message ?: "server socket not ready"}", lastError)
    }

    /**
     * 等待并验证 dummy byte（Server 准备就绪信号）
     * 参考：scrcpy Server 在 accept 后立即发送 dummy byte (0x00)
     */
    private suspend fun waitForDummyByte(
        socket: Socket,
        socketType: String,
    ) {
        val result =
            readDummyByte(
                socket,
                socketType,
                maxRetries = SOCKET_DUMMY_BYTE_MAX_RETRIES,
                retryDelayMs = SOCKET_DUMMY_BYTE_RETRY_DELAY_MS,
            )

        when (result) {
            is DummyReadResult.Success -> return
            is DummyReadResult.Closed ->
                throw IOException("$socketType socket -> Server 未发送 dummy byte（连接已关闭）")
            is DummyReadResult.Timeout ->
                throw IOException("$socketType socket -> 读取 dummy byte 超时")
            is DummyReadResult.Invalid ->
                throw IOException(
                    "$socketType socket -> 收到非预期的 dummy byte: " +
                        "0x${result.value.toString(16).padStart(2, '0')}",
                )
        }
    }

    private suspend fun readDummyByte(
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
                    return DummyReadResult.Closed
                }

                if (dummyByte != 0x00) {
                    LogManager.w(
                        LogTags.SCRCPY_CLIENT,
                        "$socketType socket: 收到非预期的 dummy byte: 0x${dummyByte.toString(16).padStart(2, '0')}",
                    )
                    return DummyReadResult.Invalid(dummyByte)
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
                    delay(retryDelayMs)
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
            }.also(::configureSocket)
        }

    private fun configureSocket(socket: Socket) {
        socket.tcpNoDelay = true
        socket.receiveBufferSize = NetworkConstants.SOCKET_RECEIVE_BUFFER_SIZE
        socket.sendBufferSize = NetworkConstants.SOCKET_SEND_BUFFER_SIZE
        socket.soTimeout = SOCKET_READ_TIMEOUT.toInt()
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

    private sealed interface DummyReadResult {
        data class Success(
            val value: Int,
        ) : DummyReadResult

        data class Invalid(
            val value: Int,
        ) : DummyReadResult

        data object Closed : DummyReadResult

        data object Timeout : DummyReadResult
    }
}

/**
 * scrcpy 没有通道角色握手，server 只按 accept() 次序分配角色。
 * 这个 helper 故意逐个 await opener，禁止用 async/awaitAll 改写。
 */
internal suspend fun <T> openScrcpyChannelsSequentially(
    enableAudio: Boolean,
    destination: MutableMap<String, T>,
    opener: suspend (String) -> T,
) {
    val order = if (enableAudio) listOf("video", "audio", "control") else listOf("video", "control")
    for (type in order) {
        destination[type] = opener(type)
    }
}

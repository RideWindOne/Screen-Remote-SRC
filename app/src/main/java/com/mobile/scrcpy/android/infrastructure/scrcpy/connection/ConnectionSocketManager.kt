package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.constants.ScrcpyConstants.SOCKET_READ_TIMEOUT
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SocketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Socket 连接管理器
 * 负责管理视频、音频和控制 Socket 的连接和关闭
 */
class ConnectionSocketManager {
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
        enableAudio: Boolean,
        keyFrameInterval: Int,
    ) = withContext(Dispatchers.IO) {
        try {
            // 步骤 1: 先连接所有 Socket（不读取数据）
            // 重要：Server 是串行 accept 的（Video → Audio → Control），
            // 必须先连接所有 Socket，再读取 dummy byte，否则会死锁
            videoSocket = createAndConnectSocket("video")
            LogManager.d(LogTags.SCRCPY_CLIENT, "Video socket connected")

            if (enableAudio) {
                audioSocket = createAndConnectSocket("audio")
                LogManager.d(LogTags.SCRCPY_CLIENT, "Audio socket connected")
            }

            controlSocket = createAndConnectSocket("control")
            LogManager.d(LogTags.SCRCPY_CLIENT, "Control socket connected")

            // 步骤 2: 只从第一个 socket（video）读取 dummy byte
            // 参考 scrcpy server: sendDummyByte 标志在第一个 socket 发送后就设为 false
            waitForDummyByte(videoSocket!!, "video")
            LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_VIDEO_SOCKET_CONNECTED.get())
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketConnected("Video"))

            // audio 和 control socket 不读取 dummy byte，直接标记为已连接
            if (enableAudio) {
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketConnected("Audio"))
            }

            CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketConnected("Control"))
        } catch (e: Exception) {
            // 推送 Socket 错误事件
            val socketName = when {
                videoSocket == null -> "Video"
                enableAudio && audioSocket == null -> "Audio"
                controlSocket == null -> "Control"
                else -> "Video"
            }
            CurrentSession.currentOrNull?.handleEvent(
                SessionEvent.SocketError("$socketName: ${e.message ?: "Unknown error"}"),
            )
            closeAllSockets()
            throw IOException("${RemoteTexts.SCRCPY_SOCKET_CONNECTION_FAILED.get()} -> ${e.message}", e)
        }
    }    /**
     * 等待并验证 dummy byte（Server 准备就绪信号）
     * 参考：scrcpy Server 在 accept 后立即发送 dummy byte (0x00)
     */
    private fun waitForDummyByte(
        socket: Socket,
        socketType: String, // TODO
    ) {
        val inputStream = socket.getInputStream()
        val dummyByte = inputStream.read()

        if (dummyByte == -1) {
            throw IOException("$socketType socket -> Server 未发送 dummy byte（连接已关闭）")
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
    }

    /**
     * 创建并连接 Socket
     */
    private fun createAndConnectSocket(type: String): Socket {
        val socket = Socket()

        // TCP 优化：禁用 Nagle 算法，降低延迟（参考 scrcpy 原生对 control_socket 的优化）
        socket.tcpNoDelay = true

        // Socket 缓冲区优化（参考 adb-mobile-ios 的 CHUNK_SIZE 设置）
        socket.receiveBufferSize = NetworkConstants.SOCKET_RECEIVE_BUFFER_SIZE
        socket.sendBufferSize = NetworkConstants.SOCKET_SEND_BUFFER_SIZE

        // 读取超时：使用固定的 10 秒超时（用于 dummy byte 和元数据读取）
        // 注意：keyFrameInterval 是视频关键帧间隔，不应用作 Socket 超时
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

    /**
     * 关闭所有 Socket
     */
    fun closeAllSockets() {
        videoSocket?.let {
            try {
                it.close()
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketDisconnected("Video"))
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close video socket: ${e.message}")
            }
        }
        videoSocket = null

        audioSocket?.let {
            try {
                it.close()
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketDisconnected("Audio"))
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close audio socket: ${e.message}")
            }
        }
        audioSocket = null

        controlSocket?.let {
            try {
                it.close()
                LogManager.d(LogTags.SCRCPY_CLIENT, "Control socket closed")
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketDisconnected("Control"))
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close control socket: ${e.message}")
            }
        }
        controlSocket = null
    }
}

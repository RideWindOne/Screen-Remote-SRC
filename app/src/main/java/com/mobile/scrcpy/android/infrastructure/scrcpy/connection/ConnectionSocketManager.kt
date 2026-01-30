package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
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
    private val localPort: Int,
) {
    var videoSocket: Socket? = null
        private set

    var audioSocket: Socket? = null
        private set

    var controlSocket: Socket? = null
        private set

    /**
     * 连接所有需要的 Socket
     */
    suspend fun connectSockets(
        enableAudio: Boolean,
        keyFrameInterval: Int,
    ) = withContext(Dispatchers.IO) {
        try {
            // 连接视频 Socket
            videoSocket = createAndConnectSocket("video", keyFrameInterval)
            LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_VIDEO_SOCKET_CONNECTED.get())

            // 连接音频 Socket（如果启用）
            if (enableAudio) {
                audioSocket = createAndConnectSocket("audio", keyFrameInterval)
                LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_AUDIO_SOCKET_CONNECTED.get())
            }

            // 连接控制 Socket
            controlSocket = createAndConnectSocket("control", keyFrameInterval)
            LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_CONTROL_SOCKET_CONNECTED.get())
        } catch (e: Exception) {
            closeAllSockets()
            throw IOException("${RemoteTexts.SCRCPY_SOCKET_CONNECTION_FAILED.get()}: ${e.message}", e)
        }
    }

    /**
     * 创建并连接 Socket
     */
    private fun createAndConnectSocket(
        type: String,
        keyFrameInterval: Int,
    ): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true

        // 所有 Socket 都使用 keyFrameInterval 作为超时时间
        socket.soTimeout = keyFrameInterval * 1000

        try {
            socket.connect(InetSocketAddress("127.0.0.1", localPort), 5000)
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
                LogManager.d(LogTags.SCRCPY_CLIENT, "Video socket closed")
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close video socket: ${e.message}")
            }
        }
        videoSocket = null

        audioSocket?.let {
            try {
                it.close()
                LogManager.d(LogTags.SCRCPY_CLIENT, "Audio socket closed")
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close audio socket: ${e.message}")
            }
        }
        audioSocket = null

        controlSocket?.let {
            try {
                it.close()
                LogManager.d(LogTags.SCRCPY_CLIENT, "Control socket closed")
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to close control socket: ${e.message}")
            }
        }
        controlSocket = null
    }
}

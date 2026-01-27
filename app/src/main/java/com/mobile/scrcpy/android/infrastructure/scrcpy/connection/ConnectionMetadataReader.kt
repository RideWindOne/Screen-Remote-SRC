package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.stream.feature.scrcpy.ScrcpySocketStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.stream.feature.scrcpy.ScrcpyAudioStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

import com.mobile.scrcpy.android.core.i18n.RemoteTexts
/**
 * 元数据读取器
 * 负责从 Socket 读取 scrcpy 元数据并创建视频/音频流
 */
class ConnectionMetadataReader(
    private val socketManager: ConnectionSocketManager
) {
    /**
     * 读取元数据并创建流
     */
    suspend fun readMetadataAndCreateStreams(
        enableAudio: Boolean,
        onVideoResolution: (Int, Int) -> Unit
    ): Pair<VideoStream?, AudioStream?> = withContext(Dispatchers.IO) {
        var videoStream: VideoStream? = null
        var audioStream: AudioStream? = null
        
        try {
            // 读取视频元数据
            val videoSocket = socketManager.videoSocket
                ?: throw IOException(RemoteTexts.SCRCPY_VIDEO_SOCKET_NOT_CONNECTED.get())
            
            val videoMetadata = readVideoMetadata(videoSocket.getInputStream())
            val (width, height) = videoMetadata
            
            LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${RemoteTexts.SCRCPY_VIDEO_RESOLUTION.get()}: ${width}x${height}")
            onVideoResolution(width, height)
            
            // 创建视频流
            videoStream = ScrcpySocketStream(videoSocket) { error ->
                LogManager.e(LogTags.SCRCPY_CLIENT, "Video stream error: $error")
            }
            
            // 读取音频元数据（如果启用）
            if (enableAudio) {
                val audioSocket = socketManager.audioSocket
                if (audioSocket != null) {
                    val audioMetadata = readAudioMetadata(audioSocket.getInputStream())
                    LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${RemoteTexts.SCRCPY_AUDIO_METADATA_READ.get()}")
                    
                    // 创建音频流
                    audioStream = ScrcpyAudioStream(audioSocket)
                }
            }
            
            Pair(videoStream, audioStream)
            
        } catch (e: Exception) {
            videoStream?.close()
            audioStream?.close()
            throw IOException("${RemoteTexts.SCRCPY_METADATA_READ_FAILED.get()}: ${e.message}", e)
        }
    }
    
    /**
     * 读取视频元数据
     * 返回 (width, height)
     */
    private fun readVideoMetadata(inputStream: java.io.InputStream): Pair<Int, Int> {
        val dis = DataInputStream(inputStream)
        
        // 读取 dummy byte (scrcpy protocol)
        dis.readByte()
        
        // 读取宽度和高度 (4 bytes each, big-endian)
        val widthBytes = ByteArray(4)
        val heightBytes = ByteArray(4)
        
        dis.readFully(widthBytes)
        dis.readFully(heightBytes)
        
        val width = ByteBuffer.wrap(widthBytes).order(ByteOrder.BIG_ENDIAN).int
        val height = ByteBuffer.wrap(heightBytes).order(ByteOrder.BIG_ENDIAN).int
        
        if (width <= 0 || height <= 0) {
            throw IOException("Invalid video resolution: ${width}x${height}")
        }
        
        return Pair(width, height)
    }
    
    /**
     * 读取音频元数据
     */
    private fun readAudioMetadata(inputStream: java.io.InputStream): ByteArray {
        val dis = DataInputStream(inputStream)
        
        // 读取音频配置数据（具体格式取决于 scrcpy 版本）
        // 这里简化处理，只读取 dummy byte
        val metadata = ByteArray(1)
        dis.readFully(metadata)
        
        return metadata
    }
}

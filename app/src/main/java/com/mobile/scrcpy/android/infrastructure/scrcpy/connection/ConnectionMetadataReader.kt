package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.stream.feature.scrcpy.ScrcpyAudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.stream.feature.scrcpy.ScrcpySocketStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.IOException

/**
 * 元数据读取器
 * 负责从 Socket 读取 scrcpy 元数据并创建视频/音频流
 */
class ConnectionMetadataReader(
    private val socketManager: ConnectionSocketManager,
) {
    /**
     * 读取元数据并创建流
     */
    suspend fun readMetadataAndCreateStreams(
        enableAudio: Boolean,
        keyFrameInterval: Int,
        onVideoResolution: (Int, Int) -> Unit,
    ): Pair<VideoStream?, AudioStream?> =
        withContext(Dispatchers.IO) {
            var videoStream: VideoStream? = null
            var audioStream: AudioStream? = null

            try {
                // 读取视频元数据
                val videoSocket =
                    socketManager.videoSocket
                        ?: throw IOException(RemoteTexts.SCRCPY_VIDEO_SOCKET_NOT_CONNECTED.get())

                val videoMetadata = readVideoMetadata(videoSocket.getInputStream())
                val (width, height) = videoMetadata

                onVideoResolution(width, height)

                // 创建视频流
                videoStream =
                    ScrcpySocketStream(videoSocket, { error ->
                        LogManager.e(LogTags.SCRCPY_CLIENT, "Video stream error: $error")
                    }, keyFrameInterval)

                // 读取音频元数据（如果启用）
                if (enableAudio) {
                    val audioSocket = socketManager.audioSocket
                    if (audioSocket != null) {
                        val audioMetadata = readAudioMetadata(audioSocket.getInputStream())
                        LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_AUDIO_METADATA_READ.get())

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

        try {
            // scrcpy 协议：
            // 1. dummy byte (1 byte)
            val dummyByte = dis.readByte()
            LogManager.d(LogTags.SCRCPY_CLIENT, "Dummy byte: 0x${dummyByte.toString(16).padStart(2, '0')}")

            // 2. 设备名称 (64 bytes, null-terminated string)
            val deviceNameBytes = ByteArray(64)
            var totalRead = 0
            while (totalRead < 64) {
                val bytesRead = dis.read(deviceNameBytes, totalRead, 64 - totalRead)
                if (bytesRead == -1) {
                    throw IOException("设备名称读取失败: 流提前结束 (已读 $totalRead/64 字节)")
                }
                totalRead += bytesRead
            }
            
            val deviceName = String(deviceNameBytes, Charsets.UTF_8).trim('\u0000')
            LogManager.d(LogTags.SCRCPY_CLIENT, "设备名称: $deviceName")
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "设备名称原始字节 (前16字节): ${deviceNameBytes.take(16).joinToString(" ") { "0x%02x".format(it) }}",
            )

            // 3. codec metadata (12 bytes)
            // - codec_id (4 bytes, big-endian)
            // - width (4 bytes, big-endian)
            // - height (4 bytes, big-endian)
            val codecBytes = ByteArray(12)
            totalRead = 0
            while (totalRead < 12) {
                val bytesRead = dis.read(codecBytes, totalRead, 12 - totalRead)
                if (bytesRead == -1) {
                    throw IOException("Codec 元数据读取失败: 流提前结束 (已读 $totalRead/12 字节)")
                }
                totalRead += bytesRead
            }

            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "Codec 元数据原始字节: ${codecBytes.joinToString(" ") { "0x%02x".format(it) }}",
            )

            val codecId =
                ((codecBytes[0].toInt() and 0xFF) shl 24) or
                    ((codecBytes[1].toInt() and 0xFF) shl 16) or
                    ((codecBytes[2].toInt() and 0xFF) shl 8) or
                    (codecBytes[3].toInt() and 0xFF)

            val width =
                ((codecBytes[4].toInt() and 0xFF) shl 24) or
                    ((codecBytes[5].toInt() and 0xFF) shl 16) or
                    ((codecBytes[6].toInt() and 0xFF) shl 8) or
                    (codecBytes[7].toInt() and 0xFF)

            val height =
                ((codecBytes[8].toInt() and 0xFF) shl 24) or
                    ((codecBytes[9].toInt() and 0xFF) shl 16) or
                    ((codecBytes[10].toInt() and 0xFF) shl 8) or
                    (codecBytes[11].toInt() and 0xFF)

            LogManager.d(LogTags.SCRCPY_CLIENT, "Codec ID: 0x${codecId.toString(16).padStart(8, '0')}")
            LogManager.d(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_VIDEO_RESOLUTION.get()}: ${width}x$height")

            if (width <= 0 || height <= 0 || width > 10000 || height > 10000) {
                throw IOException("${RemoteTexts.REMOTE_INVALID_VIDEO_SIZE.get()}: ${width}x$height")
            }

            return Pair(width, height)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "读取视频元数据失败: ${e.message}", e)
            throw IOException("${RemoteTexts.SCRCPY_METADATA_READ_FAILED.get()}: ${e.message}", e)
        }
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

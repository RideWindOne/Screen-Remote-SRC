package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.media.video.VideoDebugLog
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.stream.ScrcpyAudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.stream.ScrcpySocketStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

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
                val videoInput = videoSocket.getInputStream().buffered()

                val videoMetadata = readVideoMetadata(videoInput)
                val (width, height) = videoMetadata

                onVideoResolution(width, height)

                // 创建视频流
                videoStream =
                    ScrcpySocketStream(
                        videoSocket,
                        videoInput,
                        { error ->
                            if (error.contains("流关闭") || error.contains("视频流已关闭")) {
                                VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "Video stream closed -> $error" }
                            } else {
                                LogManager.e(LogTags.SCRCPY_CLIENT, "Video stream error -> $error")
                            }
                        },
                        keyFrameInterval,
                    )

                // 音频 header 由 ScrcpyAudioStream 自己消费，避免双重读取导致流错位
                if (enableAudio) {
                    val audioSocket = socketManager.audioSocket
                    if (audioSocket != null) {
                        val audioInput = audioSocket.getInputStream().buffered()
                        audioStream = ScrcpyAudioStream(audioSocket, audioInput)
                        LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_AUDIO_METADATA_READ.get())
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
    private fun readVideoMetadata(inputStream: InputStream): Pair<Int, Int> {
        val dis = DataInputStream(inputStream)

        try {
            // scrcpy 协议：
            // 1. dummy byte (0x00) - 已在 connectSockets 时读取
            // 2. 第一个 socket 可能发送 device meta (64 bytes)
            // 3. video socket 总会发送 codec meta (12 bytes)
            //
            // 某些自定义 server 或旧资产可能关闭 device meta，因此先读 12 字节探测：
            // - 如果前 12 字节看起来像 codec meta，则按“无 device meta”处理
            // - 否则将它视为 device meta 前缀，再补齐剩余 52 字节
            val firstTwelveBytes = readExact(dis, 12, "video:first12")
            val codecBytes: ByteArray

            if (looksLikeVideoCodecMeta(firstTwelveBytes)) {
                LogManager.w(
                    LogTags.SCRCPY_PACKET,
                    "video metadata fallback: device meta missing, first12 treated as codec meta",
                )
                codecBytes = firstTwelveBytes
            } else {
                val remainingDeviceNameBytes = readExact(dis, DEVICE_NAME_FIELD_LENGTH - firstTwelveBytes.size, "video:device_name_tail")
                val deviceNameBytes = firstTwelveBytes + remainingDeviceNameBytes
                val deviceName = String(deviceNameBytes, Charsets.UTF_8).trim('\u0000')
                VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "设备名称: $deviceName" }
                VideoDebugLog.d(LogTags.SCRCPY_PACKET) { "video device meta: ${hex(deviceNameBytes, limit = 32)}" }
                codecBytes = readExact(dis, VIDEO_CODEC_META_LENGTH, "video:codec_meta")
            }

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

            VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "Codec ID: 0x${codecId.toString(16).padStart(8, '0')}" }
            VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "${RemoteTexts.SCRCPY_VIDEO_RESOLUTION.get()}: ${width}x$height" }
            VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                "video codec meta parsed: codec=0x${codecId.toString(16).padStart(8, '0')} size=${width}x$height"
            }

            // 验证数据合法性
            if (width <= 0 || height <= 0 || width > 10000 || height > 10000) {
                throw IOException("${RemoteTexts.REMOTE_INVALID_VIDEO_SIZE.get()}: ${width}x$height (可能是数据未就绪)")
            }

            // 验证 codec_id 合法性（常见值：0x68323634=h264, 0x68323635=h265）
            if (codecId == 0x5a5a5a5a || codecId == 0x00000000) {
                throw IOException("无效的 Codec ID: 0x${codecId.toString(16)} (数据未就绪，请重试)")
            }

            return Pair(width, height)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "读取视频元数据失败: ${e.message}", e)
            throw IOException("${RemoteTexts.SCRCPY_METADATA_READ_FAILED.get()}: ${e.message}", e)
        }
    }

    private fun readExact(
        inputStream: InputStream,
        size: Int,
        stage: String,
    ): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0

        while (offset < size) {
            try {
                val read = inputStream.read(buffer, offset, size - offset)
                if (read < 0) {
                    throw EOFException("$stage EOF after $offset/$size bytes")
                }
                offset += read
                VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                    "$stage chunk=$read total=$offset/$size data=${hex(buffer.copyOf(offset))}"
                }
            } catch (e: java.net.SocketTimeoutException) {
                LogManager.e(
                    LogTags.SCRCPY_PACKET,
                    "$stage timeout total=$offset/$size partial=${hex(buffer.copyOf(offset))}",
                    e,
                )
                throw e
            }
        }

        return buffer
    }

    private fun looksLikeVideoCodecMeta(bytes: ByteArray): Boolean {
        if (bytes.size != VIDEO_CODEC_META_LENGTH) {
            return false
        }

        val codecId =
            ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        val width =
            ((bytes[4].toInt() and 0xFF) shl 24) or
                ((bytes[5].toInt() and 0xFF) shl 16) or
                ((bytes[6].toInt() and 0xFF) shl 8) or
                (bytes[7].toInt() and 0xFF)
        val height =
            ((bytes[8].toInt() and 0xFF) shl 24) or
                ((bytes[9].toInt() and 0xFF) shl 16) or
                ((bytes[10].toInt() and 0xFF) shl 8) or
                (bytes[11].toInt() and 0xFF)

        val knownCodec =
            codecId == VIDEO_CODEC_H264 ||
                codecId == VIDEO_CODEC_H265 ||
                codecId == VIDEO_CODEC_AV1
        val saneSize = width in 1..10000 && height in 1..10000
        return knownCodec && saneSize
    }

    private fun hex(
        bytes: ByteArray,
        limit: Int = bytes.size,
    ): String {
        if (bytes.isEmpty()) {
            return "<empty>"
        }
        val preview = bytes.take(limit)
        val hex = preview.joinToString(" ") { "0x%02x".format(it.toInt() and 0xFF) }
        return if (bytes.size > limit) "$hex ...(${bytes.size} bytes)" else hex
    }

    private companion object {
        const val DEVICE_NAME_FIELD_LENGTH = 64
        const val VIDEO_CODEC_META_LENGTH = 12
        const val VIDEO_CODEC_H264 = 0x68323634
        const val VIDEO_CODEC_H265 = 0x68323635
        const val VIDEO_CODEC_AV1 = 0x00617631
    }
}

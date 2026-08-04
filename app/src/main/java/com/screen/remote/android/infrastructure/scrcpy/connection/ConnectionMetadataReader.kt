package com.screen.remote.android.infrastructure.scrcpy.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.media.audio.AudioStreamHeader
import com.screen.remote.android.infrastructure.media.audio.parseAudioStreamHeader
import com.screen.remote.android.infrastructure.media.video.VideoDebugLog
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.stream.ScrcpyAudioStream
import com.screen.remote.android.infrastructure.scrcpy.stream.ScrcpySocketStream
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
    private val issueTracker: SessionIssueTracker,
) {
    /**
     * 读取元数据并创建流
     */
    suspend fun readMetadataAndCreateStreams(
        enableAudio: Boolean,
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
                val width = videoMetadata.width
                val height = videoMetadata.height

                onVideoResolution(width, height)

                // 创建视频流
                videoStream =
                    ScrcpySocketStream(
                        videoSocket,
                        videoInput,
                        videoMetadata.codec,
                        { error ->
                            if (error.contains("stream closed", ignoreCase = true)) {
                                VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "Video stream closed -> $error" }
                            } else {
                                LogManager.e(LogTags.SCRCPY_CLIENT, "Video stream error -> $error")
                            }
                        },
                        onVideoResolution,
                        issueTracker,
                    )

                if (enableAudio) {
                    val audioSocket = socketManager.audioSocket
                    if (audioSocket != null) {
                        val audioInput = audioSocket.getInputStream().buffered()
                        val codecId = DataInputStream(audioInput).readInt()
                        when (val header = parseAudioStreamHeader(codecId)) {
                            AudioStreamHeader.Disabled -> {
                                LogManager.w(
                                    LogTags.SCRCPY_CLIENT,
                                    "The remote device has disabled audio, continue the video session"
                                )
                                audioSocket.close()
                            }

                            AudioStreamHeader.ConfigurationError ->
                                throw IOException("Remote audio encoder configuration failed")

                            is AudioStreamHeader.Unsupported ->
                                throw IOException("Unsupported audio codec ID: 0x${header.codecId.toString(16)}")

                            is AudioStreamHeader.Codec -> {
                                audioStream = ScrcpyAudioStream(audioSocket, audioInput, header.codec, issueTracker)
                                LogManager.d(
                                    LogTags.SCRCPY_CLIENT,
                                    "${RemoteTexts.SCRCPY_AUDIO_METADATA_READ.english}: codec=${header.codec}",
                                )
                            }
                        }
                    }
                }

                Pair(videoStream, audioStream)
            } catch (e: Exception) {
                videoStream?.close()
                audioStream?.close()
                socketManager.closeAllSockets()
                throw IOException("${RemoteTexts.SCRCPY_METADATA_READ_FAILED.get()}: ${e.message}", e)
            }
        }

    /**
     * 读取视频元数据
     * 返回 socket header 中的实际 codec 与初始尺寸。
     */
    private fun readVideoMetadata(inputStream: InputStream): VideoMetadata {
        val dis = DataInputStream(inputStream)

        try {
            // scrcpy 4.0 协议：
            // 1. dummy byte (0x00) - 已在 connectSockets 时读取
            // 2. first socket 发送 device meta (64 bytes)
            // 3. video socket 发送 codec id (4 bytes)
            // 4. video socket 发送 session meta (flags + width + height, 12 bytes)
            val deviceNameBytes = readDeviceName(dis)
            val deviceName = String(deviceNameBytes, Charsets.UTF_8).trim('\u0000')
            VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "Device name: $deviceName" }
            VideoDebugLog.d(LogTags.SCRCPY_PACKET) { "video device meta: ${hex(deviceNameBytes, limit = 32)}" }

            val codecId = dis.readInt().also {
                VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                    "video stream codec: codec=0x${it.toString(16).padStart(8, '0')}"
                }
            }

            val sessionFlags = dis.readInt()
            val width = dis.readInt()
            val height = dis.readInt()

            VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "Codec ID: 0x${codecId.toString(16).padStart(8, '0')}" }
            VideoDebugLog.d(LogTags.SCRCPY_CLIENT) { "${RemoteTexts.SCRCPY_VIDEO_RESOLUTION.english}: ${width}x$height" }
            VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                "video session meta parsed: codec=0x${
                    codecId.toString(16).padStart(8, '0')
                } flags=0x${sessionFlags.toString(16)} size=${width}x$height"
            }

            // 验证数据合法性
            if (width !in 1..10000 || height !in 1..10000) {
                throw IOException("${RemoteTexts.REMOTE_INVALID_VIDEO_SIZE.english}: ${width}x$height (data may not be ready)")
            }

            if (sessionFlags and SESSION_META_FLAG == 0) {
                throw IOException("Invalid session metadata flags: 0x${sessionFlags.toString(16)}")
            }

            val codec = videoCodecFromId(codecId)
                ?: throw IOException("Invalid codec ID: 0x${codecId.toString(16)} (data is not ready; retry)")

            return VideoMetadata(codec = codec, width = width, height = height)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Failed to read video metadata: ${e.message}", e)
            throw IOException("${RemoteTexts.SCRCPY_METADATA_READ_FAILED.get()}: ${e.message}", e)
        }
    }

    private fun readDeviceName(inputStream: InputStream): ByteArray {
        val buffer = ByteArray(DEVICE_NAME_FIELD_LENGTH)
        var offset = 0

        while (offset < DEVICE_NAME_FIELD_LENGTH) {
            try {
                val read = inputStream.read(buffer, offset, DEVICE_NAME_FIELD_LENGTH - offset)
                if (read < 0) {
                    throw EOFException("video:device_meta EOF after $offset/$DEVICE_NAME_FIELD_LENGTH bytes")
                }
                offset += read
                VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                    "video:device_meta chunk=$read total=$offset/$DEVICE_NAME_FIELD_LENGTH data=${
                        hex(
                            buffer.copyOf(
                                offset
                            )
                        )
                    }"
                }
            } catch (e: java.net.SocketTimeoutException) {
                LogManager.e(
                    LogTags.SCRCPY_PACKET,
                    "video:device_meta timeout total=$offset/$DEVICE_NAME_FIELD_LENGTH partial=${
                        hex(
                            buffer.copyOf(
                                offset
                            )
                        )
                    }",
                    e,
                )
                throw e
            }
        }

        return buffer
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
        const val SESSION_META_FLAG = 0x80000000.toInt()
    }
}

internal fun videoCodecFromId(codecId: Int): String? =
    when (codecId) {
        0x68323634 -> "h264"
        0x68323635 -> "h265"
        0x00617631 -> "av1"
        0x00767038 -> "vp8"
        0x00767039 -> "vp9"
        else -> null
    }

private data class VideoMetadata(
    val codec: String,
    val width: Int,
    val height: Int,
)

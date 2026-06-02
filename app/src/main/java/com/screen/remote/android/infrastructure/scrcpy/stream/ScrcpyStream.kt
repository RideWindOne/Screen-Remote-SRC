package com.screen.remote.android.infrastructure.scrcpy.stream

import com.screen.remote.android.core.common.constants.LogTags
import com.screen.remote.android.core.common.constants.ScrcpyConstants.SOCKET_READ_TIMEOUT
import com.screen.remote.android.core.common.event.DemuxerError
import com.screen.remote.android.core.common.event.DeviceDisconnected
import com.screen.remote.android.core.common.event.ScrcpyEvent
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.infrastructure.media.audio.AudioDebugLog
import com.screen.remote.android.infrastructure.media.audio.AudioFrameInfo
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.media.video.VideoDebugLog
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoFrameInfo
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoSessionInfo
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import dadb.AdbShellPacket
import java.io.IOException
import java.io.InputStream
import java.net.Socket

/**
 * Scrcpy Audio Stream 包装类
 * 流程：[codec(4)] + N × (pts(8) + len(4) + data)
 * 协议格式（大端序）：
 * - codec ID: 4 bytes (big-endian)
 * - 每个包: 12 bytes header (PTS 8 bytes + size 4 bytes, big-endian) + payload
 * - bit 63: session metadata flag（仅视频）
 * - bit 62: config packet flag
 * - bit 61: key frame flag
 *
 * 集成事件系统：
 * - 推送 DeviceDisconnected 事件（流结束）
 */
class ScrcpyAudioStream(
    private val socket: Socket,
    inputStream: InputStream = socket.inputStream,
    override val codec: String,
) : AudioStream {
    private val dataInputStream = java.io.DataInputStream(inputStream)

    override val sampleRate: Int = 48000 // scrcpy 固定 48000
    override val channelCount: Int = 2 // scrcpy 固定 2

    init {
        socket.soTimeout = 10000 // 10 秒超时

        AudioDebugLog.d("ScrcpyAudioStream") { "音频配置: codec=$codec, rate=$sampleRate, channels=$channelCount" }
    }

    private var packetCount = 0
    private var opusSilencePacketCount = 0
    private var frameInfo: AudioFrameInfo? = null

    @Throws(IOException::class)
    override fun read(): AdbShellPacket {
        try {
            // 2️⃣ 循环读包：pts(8) + size(4) + payload (全部大端序)
            val ptsAndFlags = dataInputStream.readLong() // uint64 pts (包含标志位, big-endian)
            val packetSize = dataInputStream.readInt() // uint32 size (big-endian)

            if (packetSize <= 0 || packetSize > 4 * 1024 * 1024) {
                LogManager.e("AudioDecoder", "音频包大小异常: $packetSize, pts=$ptsAndFlags")
                return AdbShellPacket.Exit(byteArrayOf(0))
            }

            // 3️⃣ 读 payload（裸编码帧）
            val packet = ByteArray(packetSize)
            dataInputStream.readFully(packet, 0, packetSize)

            packetCount++

            // 检查标志位
            val isConfig = (ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_CONFIG) != 0L
            val isKeyFrame = (ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_KEY_FRAME) != 0L
            val actualPts = ptsAndFlags and ScrcpyProtocol.PACKET_PTS_MASK
            frameInfo = AudioFrameInfo(pts = actualPts, isConfig = isConfig, isKeyFrame = isKeyFrame)
            val isOpusSilence = isOpusSilencePacket(codec, packet)

            if (isOpusSilence) {
                opusSilencePacketCount++
                if (opusSilencePacketCount == 1 || opusSilencePacketCount % 250 == 0) {
                    AudioDebugLog.d("AudioDecoder") {
                        "Opus 静音/DTX 短帧: count=$opusSilencePacketCount, pts=$actualPts"
                    }
                }
            }

            // 打印数据包信息（前10个包和每50个包打印一次）
            if (!isOpusSilence && (packetCount <= 10 || packetCount % 50 == 0)) {
                val flags =
                    buildString {
                        if (isConfig) append("CONFIG ")
                        if (isKeyFrame) append("KEY ")
                        if (isEmpty()) append("NORMAL")
                    }

                // 打印前16字节的十六进制数据
                val previewSize = minOf(16, packet.size)
                val hexPreview = packet.take(previewSize).joinToString(" ") { "%02X".format(it) }

                AudioDebugLog.d("AudioDecoder") {
                    "音频包 #$packetCount: size=$packetSize, pts=$actualPts, flags=[$flags], data=$hexPreview..."
                }

                // 未识别的短包仍保留诊断；Opus 静音短帧已在上方低频统计。
                if (packetSize <= 10) {
                    LogManager.w(
                        "AudioDecoder",
                        "未识别的短音频包 #$packetCount: codec=$codec, 完整数据=${packet.joinToString(" ") { "%02X".format(it) }}",
                    )
                }
            }

            if (isConfig) {
                AudioDebugLog.d("AudioDecoder") {
                    "收到配置包 #$packetCount: size=$packetSize, 完整数据=${packet.joinToString(" ") { "%02X".format(it) }}"
                }
            }

            return AdbShellPacket.StdOut(packet)
        } catch (_: java.net.SocketTimeoutException) {
            frameInfo = null
            return AdbShellPacket.StdOut(byteArrayOf())
        } catch (_: java.io.EOFException) {
            frameInfo = null
            AudioDebugLog.d("AudioDecoder") { "音频流结束，共接收 $packetCount 个包" }
            SessionIssueTracker.record("audio.eof", "Audio stream closed by peer")
            // 推送设备断开事件
            ScrcpyEventBus.pushEvent(DeviceDisconnected)
            return AdbShellPacket.Exit(byteArrayOf(0))
        } catch (e: IOException) {
            if (e.isExpectedClosed()) {
                LogManager.w("AudioDecoder", "音频流关闭: ${e.message}")
            } else {
                LogManager.e("AudioDecoder", "音频流读取错误: ${e.message}", e)
            }
            SessionIssueTracker.record("audio.io", e.message ?: "Audio stream IO error")
            // 推送解复用器错误事件
            ScrcpyEventBus.pushEvent(DemuxerError(e.message ?: "Audio stream error"))
            throw e
        }
    }

    override fun currentFrameInfo(): AudioFrameInfo? = frameInfo

    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            LogManager.w("ScrcpyAudioStream", "关闭 Socket 失败: ${e.message}")
        }
    }
}

internal fun isOpusSilencePacket(
    codec: String,
    packet: ByteArray,
): Boolean =
    codec == "opus" &&
        packet.size == 3 &&
        (packet[0].toInt() and 0xF8) == 0xF8 &&
        (packet[1].toInt() and 0xFF) == 0xFF &&
        (packet[2].toInt() and 0xFF) == 0xFE

/**
 * Scrcpy Socket Stream 包装类
 * 按照 scrcpy 4.0 协议：12字节 frame header + 数据包内容
 * Frame header 格式：
 * - PTS (8 bytes, 其中最高2位是标志位)
 * - packet size (4 bytes)
 *
 * 集成事件系统：
 * - 推送 DeviceDisconnected 事件（流结束）
 * - 推送 DemuxerError 事件（读取错误）
 *
 * 控制流检测：
 * - 当视频流超时时，检查控制流是否存活
 * - 如果控制流断开，说明连接真正断开，抛出异常
 * - 如果控制流正常，说明只是设备息屏或网络慢，继续等待
 */
class ScrcpySocketStream(
    private val socket: Socket,
    inputStream: InputStream = socket.inputStream,
    override val codec: String,
    private val onError: (String) -> Unit,
    private val onVideoResolution: (Int, Int) -> Unit = { _, _ -> },
) : VideoStream {
    private val dataInputStream = java.io.DataInputStream(inputStream)
    private var packetCount = 0
    private var frameInfo: VideoFrameInfo? = null
    private var pendingSessionInfo: VideoSessionInfo? = null

    init {
        socket.soTimeout = SOCKET_READ_TIMEOUT.toInt()
    }

    @Throws(IOException::class)
    override fun read(): AdbShellPacket {
        try {
            while (true) {
                // 读取 frame/session header（12字节）
                val ptsAndFlags = dataInputStream.readLong() // 8字节 PTS 或 session flags + width
                val packetSize = dataInputStream.readInt() // 4字节包大小或 session height

                if ((ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_SESSION) != 0L) {
                    val width = (ptsAndFlags and 0xFFFF_FFFFL).toInt()
                    val height = packetSize

                    if (width <= 0 || height <= 0 || width > 10000 || height > 10000) {
                        LogManager.e("ScrcpySocketStream", "Session meta 视频尺寸异常: ${width}x$height")
                        onError("Session meta 视频尺寸异常")
                        ScrcpyEventBus.pushEvent(DemuxerError("Invalid session size: ${width}x$height"))
                        return AdbShellPacket.Exit(byteArrayOf(0))
                    }

                    onVideoResolution(width, height)
                    pendingSessionInfo = VideoSessionInfo(width, height)
                    frameInfo = null
                    VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                        "video session meta: size=${width}x$height flags=0x${(ptsAndFlags ushr 32).toString(16)}"
                    }
                    continue
                }

                // 高分辨率关键帧可能显著大于 4 MiB；仍保留硬上限防止恶意分配。
                if (packetSize <= 0 || packetSize > MAX_VIDEO_PACKET_SIZE) {
                    LogManager.e("ScrcpySocketStream", "数据包大小异常: $packetSize")
                    onError("数据包大小异常")
                    // 推送解复用器错误事件
                    ScrcpyEventBus.pushEvent(DemuxerError("Invalid packet size: $packetSize"))
                    return AdbShellPacket.Exit(byteArrayOf(0))
                }

                // 读取完整数据包
                val packet = ByteArray(packetSize)
                dataInputStream.readFully(packet, 0, packetSize)
                packetCount++
                val isConfig = (ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_CONFIG) != 0L
                val isKeyFrame = (ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_KEY_FRAME) != 0L
                val pts = ptsAndFlags and ScrcpyProtocol.PACKET_PTS_MASK
                frameInfo = VideoFrameInfo(pts = pts, isConfig = isConfig, isKeyFrame = isKeyFrame)

                if (packetCount <= 8 || packetCount % 60 == 0) {
                    val preview = packet.take(minOf(16, packet.size)).joinToString(" ") { "%02X".format(it) }
                    VideoDebugLog.d(LogTags.SCRCPY_PACKET) {
                        "video packet#$packetCount size=$packetSize pts=$pts config=$isConfig key=$isKeyFrame data=$preview"
                    }
                }

                return AdbShellPacket.StdOut(packet)
            }
        } catch (_: java.net.SocketTimeoutException) {
            // 读取超时，返回空数据继续等待
            frameInfo = null
            VideoDebugLog.d("ScrcpySocketStream") { "💤 设备可能息屏，控制流正常，继续等待..." }
            return AdbShellPacket.StdOut(byteArrayOf())
        } catch (_: java.io.EOFException) {
            // 流结束
            SessionIssueTracker.record("video.eof", "Video stream closed by peer")
            onError("视频流已关闭")
            // 推送设备断开事件
            ScrcpyEventBus.pushEvent(DeviceDisconnected)
            return AdbShellPacket.Exit(byteArrayOf(0))
        } catch (e: IOException) {
            // 其他 IO 错误
            SessionIssueTracker.record("video.io", e.message ?: "Video stream IO error")
            if (e.isExpectedClosed()) {
                onError("流关闭 -> ${e.message}")
            } else {
                onError("读取失败 -> ${e.message}")
            }
            // 推送解复用器错误事件
            ScrcpyEventBus.pushEvent(DemuxerError(e.message ?: "Video stream error"))
            throw e
        }
    }

    override fun currentFrameInfo(): VideoFrameInfo? = frameInfo

    override fun consumeSessionInfo(): VideoSessionInfo? = pendingSessionInfo.also { pendingSessionInfo = null }

    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            LogManager.w("ScrcpySocketStream", "关闭 Socket 失败: ${e.message}")
        }
    }

    private companion object {
        const val MAX_VIDEO_PACKET_SIZE = 32 * 1024 * 1024
    }
}

private fun IOException.isExpectedClosed(): Boolean =
    message?.contains("Socket closed") == true || message?.contains("Stream closed") == true

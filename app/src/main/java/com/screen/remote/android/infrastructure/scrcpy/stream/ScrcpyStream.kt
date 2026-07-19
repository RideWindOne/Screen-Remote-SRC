package com.screen.remote.android.infrastructure.scrcpy.stream

import com.screen.remote.android.core.common.constants.LogTags
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
import com.screen.remote.android.infrastructure.scrcpy.connection.isExpectedConnectionClosure
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
    private val issueTracker: SessionIssueTracker = SessionIssueTracker(),
) : AudioStream {
    private val dataInputStream = java.io.DataInputStream(inputStream)

    override val sampleRate: Int = 48000 // scrcpy 固定 48000
    override val channelCount: Int = 2 // scrcpy 固定 2

    init {
        // 握手和流元数据已经读取完成。媒体包必须连续读取；如果超时发生在
        // header 或 payload 中间，继续读取会从包中间开始并永久破坏帧边界。
        // 停止或断连时通过关闭 socket 中断阻塞读取。
        socket.soTimeout = 0

        AudioDebugLog.d("ScrcpyAudioStream") { "音频配置: codec=$codec, rate=$sampleRate, channels=$channelCount" }
    }

    private var packetCount = 0
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
            return AdbShellPacket.StdOut(packet)
        } catch (_: java.io.EOFException) {
            frameInfo = null
            AudioDebugLog.d("AudioDecoder") { "音频流结束，共接收 $packetCount 个包" }
            issueTracker.record("audio.eof", "Audio stream closed by peer")
            // 推送设备断开事件
            ScrcpyEventBus.pushEvent(DeviceDisconnected)
            return AdbShellPacket.Exit(byteArrayOf(0))
        } catch (e: IOException) {
            if (!e.isExpectedConnectionClosure()) {
                LogManager.e("AudioDecoder", "音频流读取错误: ${e.message}", e)
            }
            issueTracker.record("audio.io", e.message ?: "Audio stream IO error")
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
 */
class ScrcpySocketStream(
    private val socket: Socket,
    inputStream: InputStream = socket.inputStream,
    override val codec: String,
    private val onError: (String) -> Unit,
    private val onVideoResolution: (Int, Int) -> Unit = { _, _ -> },
    private val issueTracker: SessionIssueTracker = SessionIssueTracker(),
) : VideoStream {
    private val dataInputStream = java.io.DataInputStream(inputStream)
    private var frameInfo: VideoFrameInfo? = null
    private var pendingSessionInfo: VideoSessionInfo? = null

    init {
        // 媒体读取阶段不能在部分 header/payload 已消费后把超时当作“暂无数据”，
        // 否则下一次读取会失去协议帧边界。关闭 socket 会唤醒阻塞中的读线程。
        socket.soTimeout = 0
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
                val isConfig = (ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_CONFIG) != 0L
                val isKeyFrame = (ptsAndFlags and ScrcpyProtocol.PACKET_FLAG_KEY_FRAME) != 0L
                val pts = ptsAndFlags and ScrcpyProtocol.PACKET_PTS_MASK
                frameInfo = VideoFrameInfo(pts = pts, isConfig = isConfig, isKeyFrame = isKeyFrame)

                return AdbShellPacket.StdOut(packet)
            }
        } catch (_: java.io.EOFException) {
            // 流结束
            issueTracker.record("video.eof", "Video stream closed by peer")
            onError("视频流已关闭")
            // 推送设备断开事件
            ScrcpyEventBus.pushEvent(DeviceDisconnected)
            return AdbShellPacket.Exit(byteArrayOf(0))
        } catch (e: IOException) {
            // 其他 IO 错误
            issueTracker.record("video.io", e.message ?: "Video stream IO error")
            if (e.isExpectedConnectionClosure()) {
                onError("连接已断开 -> ${e.message}")
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

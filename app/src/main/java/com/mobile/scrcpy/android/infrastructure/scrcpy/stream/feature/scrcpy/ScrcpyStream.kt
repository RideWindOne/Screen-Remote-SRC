package com.mobile.scrcpy.android.infrastructure.scrcpy.stream.feature.scrcpy

import com.mobile.scrcpy.android.core.common.event.DemuxerError
import com.mobile.scrcpy.android.core.common.event.DeviceDisconnected
import com.mobile.scrcpy.android.core.common.event.ScrcpyEvent
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import dadb.AdbShellPacket
import java.io.IOException
import java.net.Socket

/**
 * Scrcpy Audio Stream 包装类
 * 流程：[codec(4)] + N × (pts(8) + len(4) + data)
 * 协议格式（大端序）：
 * - codec ID: 4 bytes (big-endian)
 * - 每个包: 12 bytes header (PTS 8 bytes + size 4 bytes, big-endian) + payload
 * - PTS 最高位 (bit 63): config packet flag
 * - PTS 次高位 (bit 62): key frame flag
 *
 * 集成事件系统：
 * - 推送 DeviceDisconnected 事件（流结束）
 */
class ScrcpyAudioStream(
    private val socket: Socket,
) : AudioStream {
    private val dataInputStream = java.io.DataInputStream(socket.inputStream)

    override val codec: String
    override val sampleRate: Int = 48000 // scrcpy 固定 48000
    override val channelCount: Int = 2 // scrcpy 固定 2

    init {
        socket.soTimeout = 10000 // 10 秒超时

        // 1️⃣ 读 AudioHeader (4 bytes, big-endian)
        val codecId = dataInputStream.readInt() // uint32 codec (big-endian)

        codec =
            when (codecId) {
                0x6f707573 -> {
                    "opus"
                }

                // "opus" 的 ASCII
                0x00616163 -> {
                    "aac"
                }

                // "aac" 的 ASCII
                0x666c6163 -> {
                    "flac"
                }

                // "flac" 的 ASCII
                0x00726177 -> {
                    "raw"
                }

                // "raw" 的 ASCII
                else -> {
                    LogManager.w("ScrcpyAudioStream", "未知 codec ID: 0x${codecId.toString(16)}, 使用 opus")
                    "opus"
                }
            }

        LogManager.d("ScrcpyAudioStream", "音频配置: codec=$codec, rate=$sampleRate, channels=$channelCount")
    }

    private var packetCount = 0

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

            // 打印数据包信息（前10个包和每50个包打印一次）
            if (packetCount <= 10 || packetCount % 50 == 0) {
                val flags =
                    buildString {
                        if (isConfig) append("CONFIG ")
                        if (isKeyFrame) append("KEY ")
                        if (isEmpty()) append("NORMAL")
                    }

                // 打印前16字节的十六进制数据
                val previewSize = minOf(16, packet.size)
                val hexPreview = packet.take(previewSize).joinToString(" ") { "%02X".format(it) }

                LogManager.d(
                    "AudioDecoder",
                    "音频包 #$packetCount: size=$packetSize, pts=$actualPts, flags=[$flags], data=$hexPreview...",
                )

                // 如果是小包，打印完整数据
                if (packetSize <= 10) {
                    LogManager.w(
                        "AudioDecoder",
                        "异常小包 #$packetCount: 完整数据=${packet.joinToString(" ") { "%02X".format(it) }}",
                    )
                }
            }

            if (isConfig) {
                LogManager.d(
                    "AudioDecoder",
                    "收到配置包 #$packetCount: size=$packetSize, 完整数据=${packet.joinToString(" ") { "%02X".format(it) }}",
                )
            }

            return AdbShellPacket.StdOut(packet)
        } catch (_: java.net.SocketTimeoutException) {
            return AdbShellPacket.StdOut(byteArrayOf())
        } catch (_: java.io.EOFException) {
            LogManager.d("AudioDecoder", "音频流结束，共接收 $packetCount 个包")
            // 推送设备断开事件
            ScrcpyEventBus.pushEvent(DeviceDisconnected)
            return AdbShellPacket.Exit(byteArrayOf(0))
        } catch (e: IOException) {
            LogManager.e("AudioDecoder", "音频流读取错误: ${e.message}", e)
            // 推送解复用器错误事件
            ScrcpyEventBus.pushEvent(DemuxerError(e.message ?: "Audio stream error"))
            throw e
        }
    }

    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            LogManager.w("ScrcpyAudioStream", "关闭 Socket 失败: ${e.message}")
        }
    }
}

/**
 * Scrcpy Socket Stream 包装类
 * 按照 scrcpy 3.3.4 协议：12字节 frame header + 数据包内容
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
    private val onError: (String) -> Unit,
    keyFrameInterval: Int = 2,
) : VideoStream {
    private val dataInputStream = java.io.DataInputStream(socket.inputStream)

    init {
        socket.soTimeout = keyFrameInterval * 1000 // 关键帧间隔转毫秒
    }

    @Throws(IOException::class)
    override fun read(): AdbShellPacket {
        try {
            // 检查数据是否可用
            if (dataInputStream.available() <= 0) {
                // 没有数据，返回空包（避免阻塞）
                return AdbShellPacket.StdOut(byteArrayOf())
            }

            // 读取 frame header（12字节）
            dataInputStream.readLong() // 8字节 PTS（包含标志位）
            val packetSize = dataInputStream.readInt() // 4字节包大小

            // 检查包大小是否合理（最大4MB）
            if (packetSize <= 0 || packetSize > 4 * 1024 * 1024) {
                LogManager.e("ScrcpySocketStream", "数据包大小异常: $packetSize")
                onError("数据包大小异常")
                // 推送解复用器错误事件
                ScrcpyEventBus.pushEvent(DemuxerError("Invalid packet size: $packetSize"))
                return AdbShellPacket.Exit(byteArrayOf(0))
            }

            // 读取完整数据包
            val packet = ByteArray(packetSize)
            dataInputStream.readFully(packet, 0, packetSize)

            return AdbShellPacket.StdOut(packet)
        } catch (_: java.net.SocketTimeoutException) {
            // 读取超时，返回空数据继续等待
            LogManager.d("ScrcpySocketStream", "💤 设备可能息屏，控制流正常，继续等待...")
            return AdbShellPacket.StdOut(byteArrayOf())
        } catch (_: java.io.EOFException) {
            // 流结束
            onError("视频流已关闭")
            // 推送设备断开事件
            ScrcpyEventBus.pushEvent(DeviceDisconnected)
            return AdbShellPacket.Exit(byteArrayOf(0))
        } catch (e: IOException) {
            // 其他 IO 错误
            onError("读取失败 -> ${e.message}")
            // 推送解复用器错误事件
            ScrcpyEventBus.pushEvent(DemuxerError(e.message ?: "Video stream error"))
            throw e
        }
    }

    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            LogManager.w("ScrcpySocketStream", "关闭 Socket 失败: ${e.message}")
        }
    }
}

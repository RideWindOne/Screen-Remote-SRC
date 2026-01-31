package com.mobile.scrcpy.android.infrastructure.media.audio

import android.media.MediaCodec
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.event.DemuxerError
import com.mobile.scrcpy.android.core.common.event.DeviceDisconnected
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AudioDecoder - 音频解码器
 * 支持 Opus、AAC、FLAC、RAW 四种格式
 *
 * 职责：
 * - 协调格式处理器和 AudioTrack 管理器
 * - 管理解码循环和生命周期
 *
 * 集成事件系统：
 * - 推送 DeviceDisconnected 事件（连接丢失）
 * - 推送 DemuxerError 事件（解码错误）
 */
class AudioDecoder(
    volumeScale: Float = 1.0f,
) {
    private val decoderLock = Any()
    private val formatHandler = AudioFormatHandler()
    private val trackManager = AudioTrackManager(volumeScale)

    @Volatile private var decoder: MediaCodec? = null

    @Volatile private var isRunning = false

    @Volatile private var isStopped = false

    var onConnectionLost: (() -> Unit)? = null // 连接丢失回调

    suspend fun start(audioStream: AudioStream) =
        withContext(Dispatchers.IO) {
            try {
                val codec = audioStream.codec
                val sampleRate = audioStream.sampleRate
                val channelCount = audioStream.channelCount

                LogManager.d(LogTags.AUDIO_DECODER, "开始音频解码: codec=$codec, rate=$sampleRate, channels=$channelCount")

                isStopped = false
                isRunning = true

                // 推送解码器启动事件
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.DecoderStarted("Audio"))

                // RAW 格式直接播放，不需要解码
                if (codec.lowercase() == "raw") {
                    playRawAudio(audioStream, sampleRate, channelCount)
                } else {
                    decodeAndPlay(audioStream, codec, sampleRate, channelCount)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "音频解码失败: ${e.message}", e)
                // 检查是否是连接丢失
                if (e.message?.contains("Socket closed") == true ||
                    e.message?.contains("Stream closed") == true
                ) {
                    LogManager.w(LogTags.AUDIO_DECODER, "音频连接丢失，触发回调")
                    onConnectionLost?.invoke()
                    // 推送设备断开事件
                    ScrcpyEventBus.pushEvent(DeviceDisconnected)
                } else {
                    // 推送解复用器错误事件
                    ScrcpyEventBus.pushEvent(DemuxerError(e.message ?: "Audio decode error"))
                }

                // 推送解码器错误事件
                CurrentSession.currentOrNull?.handleEvent(
                    SessionEvent.DecoderError("Audio: ${e.message ?: "Unknown error"}"),
                )
            } finally {
                stop()
            }
        }

    fun stop() {
        synchronized(decoderLock) {
            if (isStopped) {
                LogManager.d(LogTags.AUDIO_DECODER, "stop() 被调用，但已经停止")
                return
            }

            LogManager.d(LogTags.AUDIO_DECODER, "stop() 被调用，开始停止解码器")

            isRunning = false
            isStopped = true

            trackManager.release()

            try {
                decoder?.stop()
                decoder?.release()

                // 推送解码器停止事件
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.DecoderStopped("Audio"))
            } catch (e: Exception) {
                // 忽略
            } finally {
                decoder = null
            }

            LogManager.d(LogTags.AUDIO_DECODER, "音频解码器已停止")
        }
    }

    /**
     * RAW 格式直接播放
     */
    private fun playRawAudio(
        audioStream: AudioStream,
        sampleRate: Int,
        channelCount: Int,
    ) {
        val track = trackManager.createAudioTrack(sampleRate, channelCount)
        if (track == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建 AudioTrack")
            return
        }

        trackManager.play()

        var packetCount = 0
        LogManager.d(LogTags.AUDIO_DECODER, "开始播放 RAW 音频")

        while (isRunning) {
            try {
                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) continue

                        packetCount++

                        val written = trackManager.writeRawData(packet.payload)

                        if (written < 0) {
                            LogManager.e(LogTags.AUDIO_DECODER, "AudioTrack 写入失败: $written")
                        } else if (packetCount <= 10 || packetCount % 100 == 0) {
                            LogManager.d(
                                LogTags.AUDIO_DECODER,
                                "RAW 音频包 #$packetCount: size=${packet.payload.size}, written=$written",
                            )
                        }
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        break
                    }

                    else -> {
                        continue
                    }
                }
            } catch (e: Exception) {
                if (isRunning && !isStopped) {
                    LogManager.e(LogTags.AUDIO_DECODER, "RAW 音频播放错误: ${e.message}", e)
                }
                break
            }
        }

        LogManager.d(LogTags.AUDIO_DECODER, "RAW 音频播放结束，共 $packetCount 包")
    }

    /**
     * 解码并播放（Opus/AAC/FLAC）
     */
    private fun decodeAndPlay(
        audioStream: AudioStream,
        codec: String,
        sampleRate: Int,
        channelCount: Int,
    ) {
        // 读取第一个包
        val firstPacket = audioStream.read()
        if (firstPacket !is dadb.AdbShellPacket.StdOut || firstPacket.payload.isEmpty()) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法读取第一个包")
            return
        }

        val firstData = firstPacket.payload
        LogManager.d(
            LogTags.AUDIO_DECODER,
            "第一个包: size=${firstData.size}, data=${firstData.take(16).joinToString(" ") { "%02X".format(it) }}...",
        )

        // 判断是否为配置包
        var configData: ByteArray? = null
        var firstAudioPacket: ByteArray? = null

        if (codec.lowercase() == "opus") {
            // Opus: 检查是否为 OpusHead（19字节）
            if (formatHandler.isOpusHead(firstData)) {
                LogManager.d(LogTags.AUDIO_DECODER, "检测到 OpusHead 配置包")
                configData = firstData
            } else {
                // 裸 Opus 帧，不需要配置包
                LogManager.d(LogTags.AUDIO_DECODER, "检测到裸 Opus 帧，跳过配置包")
                firstAudioPacket = firstData
            }
        } else {
            // AAC/FLAC: 验证配置包
            if (formatHandler.validateConfigPacket(codec, firstData)) {
                configData = firstData
            } else {
                LogManager.e(LogTags.AUDIO_DECODER, "配置包格式错误")
                return
            }
        }

        // 创建解码器
        val createdDecoder = formatHandler.createDecoder(codec, sampleRate, channelCount, configData)
        if (createdDecoder == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建解码器")
            return
        }
        decoder = createdDecoder

        // 创建 AudioTrack
        val track = trackManager.createAudioTrack(sampleRate, channelCount)
        if (track == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建 AudioTrack")
            decoder?.release()
            decoder = null
            return
        }
        trackManager.play()

        LogManager.d(LogTags.AUDIO_DECODER, "开始解码循环")
        decodeLoop(audioStream, firstAudioPacket)
    }

    /**
     * 解码循环
     */
    private fun decodeLoop(
        audioStream: AudioStream,
        firstAudioPacket: ByteArray? = null,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        var inputCount = 0
        var outputCount = 0
        var pts = 0L // 使用递增的时间戳

        LogManager.d(LogTags.AUDIO_DECODER, "解码循环开始")

        // 如果有第一个音频包，先处理它
        if (firstAudioPacket != null && firstAudioPacket.isNotEmpty()) {
            try {
                val currentDecoder = decoder
                if (currentDecoder != null && !isStopped) {
                    val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(firstAudioPacket)
                            currentDecoder.queueInputBuffer(
                                inputIndex,
                                0,
                                firstAudioPacket.size,
                                pts,
                                0,
                            )
                            pts += 20000
                            inputCount++
                            frameCount++
                            LogManager.d(LogTags.AUDIO_DECODER, "已处理第一个音频包: size=${firstAudioPacket.size}")
                        }
                    }
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "处理第一个音频包失败: ${e.message}", e)
            }
        }

        while (isRunning) {
            try {
                val currentDecoder = decoder
                if (currentDecoder == null || isStopped) break

                // 先尝试输出（避免缓冲区满）
                val drainedCount = drainOutputBuffers(bufferInfo)
                if (drainedCount > 0) {
                    outputCount += drainedCount
                    if (outputCount <= 10 || outputCount % 100 == 0) {
                        LogManager.d(LogTags.AUDIO_DECODER, "已输出 $outputCount 个音频缓冲区")
                    }
                }

                // 再输入数据
                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) continue

                        // 跳过静音包或无效包（Opus 有效帧至少 3 字节）
                        if (packet.payload.size < 3) {
                            if (frameCount < 10) {
                                LogManager.d(LogTags.AUDIO_DECODER, "跳过小包: size=${packet.payload.size}")
                            }
                            continue
                        }

                        frameCount++
                        if (frameCount <= 10 || frameCount % 100 == 0) {
                            LogManager.d(LogTags.AUDIO_DECODER, "音频帧 #$frameCount, size=${packet.payload.size}")
                        }

                        var shouldBreak = false
                        synchronized(decoderLock) {
                            if (decoder != currentDecoder || isStopped) {
                                shouldBreak = true
                            } else {
                                val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                                if (inputIndex >= 0) {
                                    val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                                    if (inputBuffer != null) {
                                        inputBuffer.clear()
                                        inputBuffer.put(packet.payload)

                                        // Opus 每帧 20ms，48000Hz 采样率 = 960 samples
                                        // PTS 单位是微秒
                                        currentDecoder.queueInputBuffer(
                                            inputIndex,
                                            0,
                                            packet.payload.size,
                                            pts,
                                            0,
                                        )
                                        pts += 20000 // 20ms = 20000us
                                        inputCount++

                                        if (inputCount <= 5 || inputCount % 100 == 0) {
                                            LogManager.d(
                                                LogTags.AUDIO_DECODER,
                                                "帧 #$frameCount 已送入解码器 (total=$inputCount, pts=${pts / 1000}ms)",
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (shouldBreak) break
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        break
                    }

                    else -> {
                        continue
                    }
                }
            } catch (e: IllegalStateException) {
                if (e.message?.contains("executing state") == true ||
                    e.message?.contains("Released state") == true
                ) {
                    break
                }
                throw e
            } catch (e: Exception) {
                if (isRunning && !isStopped) {
                    LogManager.e(LogTags.AUDIO_DECODER, "解码错误: ${e.message}", e)
                }
                break
            }
        }

        // 最后再输出一次
        var finalDrainCount = 0
        while (drainOutputBuffers(bufferInfo) > 0 && finalDrainCount < 50) {
            finalDrainCount++
        }
        LogManager.d(LogTags.AUDIO_DECODER, "解码结束，共 $frameCount 帧输入，$outputCount 个缓冲区输出")
    }

    /**
     * 输出解码后的数据
     * @return 输出的缓冲区数量
     */
    private fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo): Int {
        if (isStopped) return 0

        val codec = decoder
        if (codec == null) return 0

        var drainedCount = 0

        try {
            // 使用较长的超时时间（100ms）等待第一个输出
            val timeout = if (drainedCount == 0) 100000L else 0L
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeout)
            var loopCount = 0

            // 调试：第一次调用时打印结果
            if (drainedCount == 0 && loopCount == 0) {
                when (outputIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        LogManager.d(LogTags.AUDIO_DECODER, "⏳ 第一次 dequeue: INFO_TRY_AGAIN_LATER")
                    }

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        LogManager.d(LogTags.AUDIO_DECODER, "📋 第一次 dequeue: INFO_OUTPUT_FORMAT_CHANGED")
                    }

                    else -> {
                        if (outputIndex >= 0) {
                            LogManager.d(LogTags.AUDIO_DECODER, "第一次 dequeue: 有效 index=$outputIndex")
                        } else {
                            LogManager.d(LogTags.AUDIO_DECODER, "❓ 第一次 dequeue: 未知值=$outputIndex")
                        }
                    }
                }
            }

            while (!isStopped && outputIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                loopCount++

                when {
                    outputIndex >= 0 -> {
                        // 跳过配置缓冲区
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            LogManager.d(LogTags.AUDIO_DECODER, "跳过配置缓冲区")
                            codec.releaseOutputBuffer(outputIndex, false)
                            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                            continue
                        }

                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            drainedCount++

                            // 写入 AudioTrack（音量缩放在 trackManager 内部处理）
                            val written = trackManager.writeDecodedData(outputBuffer, bufferInfo.size)

                            if (written < 0) {
                                LogManager.e(LogTags.AUDIO_DECODER, "AudioTrack 写入失败: $written")
                            } else if (drainedCount <= 10 || drainedCount % 100 == 0) {
                                LogManager.d(
                                    LogTags.AUDIO_DECODER,
                                    "🔊 音频输出 #$drainedCount: size=${bufferInfo.size}, written=$written, pts=${bufferInfo.presentationTimeUs / 1000}ms",
                                )
                            }
                        } else {
                            LogManager.w(
                                LogTags.AUDIO_DECODER,
                                "输出缓冲区为空或大小为0: buffer=$outputBuffer, size=${bufferInfo.size}",
                            )
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        LogManager.d(LogTags.AUDIO_DECODER, "输出格式变化: $format")
                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    else -> {
                        break
                    }
                }

                if (loopCount > 100) {
                    LogManager.w(LogTags.AUDIO_DECODER, "drainOutputBuffers 循环过多，可能有问题")
                    break
                }
            }

            return drainedCount
        } catch (e: IllegalStateException) {
            if (e.message?.contains("executing state") == true ||
                e.message?.contains("Released state") == true
            ) {
                return 0
            }
            throw e
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "输出数据异常: ${e.message}", e)
            return 0
        }
    }
}

/**
 * 音频流接口
 */
interface AudioStream : AutoCloseable {
    val codec: String
    val sampleRate: Int
    val channelCount: Int

    fun read(): dadb.AdbShellPacket
}

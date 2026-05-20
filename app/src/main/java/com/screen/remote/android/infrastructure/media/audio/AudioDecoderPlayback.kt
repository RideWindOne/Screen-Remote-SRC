package com.screen.remote.android.infrastructure.media.audio

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager

internal class AudioDecoderPlayback(
    private val formatHandler: AudioFormatHandler,
    private val trackManager: AudioTrackManager,
    private val decoderLock: Any,
    private val getDecoder: () -> MediaCodec?,
    private val setDecoder: (MediaCodec?) -> Unit,
    private val isRunning: () -> Boolean,
    private val isStopped: () -> Boolean,
) {
    private val bootstrapper = AudioDecoderBootstrapper(formatHandler)
    private val outputDrainer =
        AudioDecoderOutputDrainer(
            trackManager = trackManager,
            getDecoder = getDecoder,
            isStopped = isStopped,
        )

    fun playRawAudio(
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
        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "开始播放 RAW 音频" }

        while (isRunning()) {
            try {
                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) {
                            continue
                        }

                        packetCount++
                        val written = trackManager.writeRawData(packet.payload)

                        if (written < 0) {
                            LogManager.e(LogTags.AUDIO_DECODER, "AudioTrack 写入失败: $written")
                        } else if (packetCount <= 10 || packetCount % 100 == 0) {
                            AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                                "RAW 音频包 #$packetCount: size=${packet.payload.size}, written=$written"
                            }
                        }
                    }

                    is dadb.AdbShellPacket.Exit -> break
                    else -> continue
                }
            } catch (e: Exception) {
                if (isRunning() && !isStopped()) {
                    LogManager.e(LogTags.AUDIO_DECODER, "RAW 音频播放错误: ${e.message}", e)
                }
                break
            }
        }

        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "RAW 音频播放结束，共 $packetCount 包" }
    }

    fun decodeAndPlay(
        audioStream: AudioStream,
        codec: String,
        sampleRate: Int,
        channelCount: Int,
    ) {
        val bootstrap = bootstrapper.readBootstrap(audioStream, codec) ?: return
        val resolvedFormat =
            formatHandler.resolvePlaybackFormat(
                codec = codec,
                sampleRate = sampleRate,
                channelCount = channelCount,
                configData = bootstrap.configData,
            )
        val createdDecoder =
            formatHandler.createDecoder(
                codec = codec,
                sampleRate = resolvedFormat.sampleRate,
                channelCount = resolvedFormat.channelCount,
                configData = bootstrap.configData,
            )
        if (createdDecoder == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建解码器")
            return
        }
        setDecoder(createdDecoder)

        val track =
            trackManager.createAudioTrack(
                sampleRate = resolvedFormat.sampleRate,
                channelCount = resolvedFormat.channelCount,
            )
        if (track == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建 AudioTrack")
            getDecoder()?.release()
            setDecoder(null)
            return
        }

        trackManager.play()

        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "开始解码循环" }
        decodeLoop(audioStream = audioStream, firstAudioPacket = bootstrap.firstAudioPacket)
    }

    private fun decodeLoop(
        audioStream: AudioStream,
        firstAudioPacket: ByteArray?,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        var inputCount = 0
        var outputCount = 0
        var pts = 0L

        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "解码循环开始" }

        inputCount += queueFirstAudioPacket(firstAudioPacket, pts)
        if (inputCount > 0) {
            pts += 20000
            frameCount++
        }

        while (isRunning()) {
            try {
                val currentDecoder = getDecoder()
                if (currentDecoder == null || isStopped()) {
                    break
                }

                val drainedCount = outputDrainer.drainOutputBuffers(bufferInfo)
                if (drainedCount > 0) {
                    outputCount += drainedCount
                }

                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) {
                            continue
                        }
                        if (packet.payload.size < 3) {
                            if (frameCount < 10) {
                                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "跳过小包: size=${packet.payload.size}" }
                            }
                            continue
                        }

                        frameCount++
                        if (frameCount <= INPUT_LOG_SAMPLES || frameCount % INPUT_LOG_INTERVAL == 0) {
                            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "音频帧 #$frameCount, size=${packet.payload.size}" }
                        }

                        when (val result = queuePacketIntoDecoder(currentDecoder, packet.payload, frameCount, inputCount, pts)) {
                            QueuePacketResult.Break -> {
                            break
                            }
                            QueuePacketResult.Queued -> {
                                inputCount++
                                pts += 20000
                            }
                            QueuePacketResult.Skipped -> Unit
                        }
                    }

                    is dadb.AdbShellPacket.Exit -> break
                    else -> continue
                }
            } catch (e: IllegalStateException) {
                if (e.message?.contains("executing state") == true ||
                    e.message?.contains("Released state") == true
                ) {
                    break
                }
                throw e
            } catch (e: Exception) {
                if (isRunning() && !isStopped()) {
                    if (e.isExpectedShutdown()) {
                        LogManager.w(LogTags.AUDIO_DECODER, "解码结束: ${e.message}")
                    } else {
                        LogManager.e(LogTags.AUDIO_DECODER, "解码错误: ${e.message}", e)
                    }
                }
                break
            }
        }

        var finalDrainCount = 0
        while (outputDrainer.drainOutputBuffers(bufferInfo) > 0 && finalDrainCount < 50) {
            finalDrainCount++
        }
        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "解码结束，共 $frameCount 帧输入，$outputCount 个缓冲区输出" }
    }

    private fun queueFirstAudioPacket(
        firstAudioPacket: ByteArray?,
        pts: Long,
    ): Int {
        val packet = firstAudioPacket ?: return 0
        if (packet.isEmpty()) {
            return 0
        }

        return try {
            val currentDecoder = getDecoder()
            if (currentDecoder != null && !isStopped()) {
                val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(packet)
                        currentDecoder.queueInputBuffer(
                            inputIndex,
                            0,
                            packet.size,
                            pts,
                            0,
                        )
                        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "已处理第一个音频包: size=${packet.size}" }
                        1
                    } else {
                        0
                    }
                } else {
                    0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "处理第一个音频包失败: ${e.message}", e)
            0
        }
    }

    private fun queuePacketIntoDecoder(
        currentDecoder: MediaCodec,
        payload: ByteArray,
        frameCount: Int,
        inputCount: Int,
        pts: Long,
    ): QueuePacketResult {
        var result = QueuePacketResult.Skipped
        synchronized(decoderLock) {
            if (getDecoder() != currentDecoder || isStopped()) {
                result = QueuePacketResult.Break
            } else {
                val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(payload)
                        currentDecoder.queueInputBuffer(
                            inputIndex,
                            0,
                            payload.size,
                            pts,
                            0,
                        )

                        val nextInputCount = inputCount + 1
                        if (nextInputCount <= INPUT_LOG_SAMPLES || nextInputCount % INPUT_LOG_INTERVAL == 0) {
                            AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                                "帧 #$frameCount 已送入解码器 (total=$nextInputCount, pts=${(pts + 20000) / 1000}ms)"
                            }
                        }
                        result = QueuePacketResult.Queued
                    }
                }
            }
        }
        return result
    }

    private enum class QueuePacketResult {
        Queued,
        Skipped,
        Break,
    }

    private fun Exception.isExpectedShutdown(): Boolean =
        message?.contains("Socket closed") == true ||
            message?.contains("Stream closed") == true ||
            message?.contains("Pending dequeue output buffer request cancelled") == true

    private companion object {
        const val INPUT_LOG_SAMPLES = 3
        const val INPUT_LOG_INTERVAL = 500
    }
}

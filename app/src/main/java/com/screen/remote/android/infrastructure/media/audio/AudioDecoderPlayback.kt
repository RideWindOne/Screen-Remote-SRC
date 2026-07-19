package com.screen.remote.android.infrastructure.media.audio

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.connection.isExpectedConnectionClosure

internal class AudioDecoderPlayback(
    private val formatHandler: AudioFormatHandler,
    private val trackManager: AudioTrackManager,
    private val decoderLock: Any,
    private val getDecoder: () -> MediaCodec?,
    private val setDecoder: (MediaCodec?) -> Unit,
    private val isRunning: () -> Boolean,
    private val isStopped: () -> Boolean,
    private val onPlaybackReady: () -> Unit,
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
            throw IllegalStateException("无法创建 RAW AudioTrack")
        }

        trackManager.play()
        onPlaybackReady()

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
                        }
                    }

                    is dadb.AdbShellPacket.Exit -> throw java.io.EOFException("Audio Stream closed by peer")
                    else -> continue
                }
            } catch (e: Exception) {
                if (!isRunning() || isStopped()) break
                LogManager.e(LogTags.AUDIO_DECODER, "RAW 音频播放错误: ${e.message}", e)
                throw e
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
        val bootstrap =
            bootstrapper.readBootstrap(audioStream, codec)
                ?: throw IllegalStateException("无法读取 $codec 音频初始化数据")
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
            throw IllegalStateException("无法创建 $codec 音频解码器")
        }
        setDecoder(createdDecoder)

        val track =
            trackManager.createAudioTrack(
                sampleRate = resolvedFormat.sampleRate,
                channelCount = resolvedFormat.channelCount,
            )
        if (track == null) {
            getDecoder()?.release()
            setDecoder(null)
            throw IllegalStateException("无法创建 AudioTrack")
        }

        trackManager.play()
        onPlaybackReady()

        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "开始解码循环" }
        var firstAudioPacket = bootstrap.firstAudioPacket
        var firstAudioPts = bootstrap.firstAudioPts
        while (isRunning() && !isStopped()) {
            try {
                decodeLoop(
                    audioStream = audioStream,
                    firstAudioPacket = firstAudioPacket,
                    firstAudioPts = firstAudioPts,
                )
                return
            } catch (error: AudioTrackPlaybackException) {
                // 输出设备故障与 MediaCodec 无关，不能因此拉黑并轮换解码器。
                throw error
            } catch (error: IllegalStateException) {
                val failedDecoder = getDecoder() ?: throw error
                if (!formatHandler.prepareRuntimeFallback(failedDecoder, error)) throw error

                synchronized(decoderLock) {
                    if (getDecoder() == failedDecoder) {
                        runCatching { failedDecoder.stop() }
                        runCatching { failedDecoder.release() }
                        setDecoder(null)
                    }
                }
                if (!isRunning() || isStopped()) return
                val fallbackDecoder =
                    formatHandler.createDecoder(
                        codec = codec,
                        sampleRate = resolvedFormat.sampleRate,
                        channelCount = resolvedFormat.channelCount,
                        configData = bootstrap.configData,
                    ) ?: throw IllegalStateException("所有 $codec 音频解码器均运行失败", error)
                synchronized(decoderLock) {
                    if (!isRunning() || isStopped()) {
                        runCatching { fallbackDecoder.stop() }
                        runCatching { fallbackDecoder.release() }
                        return
                    }
                    setDecoder(fallbackDecoder)
                }
                outputDrainer.resetAfterDecoderFallback()
                firstAudioPacket = null
                firstAudioPts = null
                LogManager.w(LogTags.AUDIO_DECODER, "音频解码器运行失败，已切换到 ${fallbackDecoder.name}")
            }
        }
    }

    private fun decodeLoop(
        audioStream: AudioStream,
        firstAudioPacket: ByteArray?,
        firstAudioPts: Long?,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        var inputCount = 0
        var outputCount = 0
        var lastPts = firstAudioPts ?: 0L
        var inputsWithoutOutput = 0
        var lastObservedOutputCount = outputDrainer.outputCount()

        AudioDebugLog.d(LogTags.AUDIO_DECODER) { "解码循环开始" }

        inputCount += queueFirstAudioPacket(firstAudioPacket, lastPts)
        if (inputCount > 0) {
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
                val observedOutputCount = outputDrainer.outputCount()
                if (observedOutputCount > lastObservedOutputCount) {
                    lastObservedOutputCount = observedOutputCount
                    inputsWithoutOutput = 0
                }

                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) {
                            continue
                        }
                        frameCount++
                        val frameInfo = audioStream.currentFrameInfo()
                        val packetPts = frameInfo?.pts ?: lastPts
                        val flags = if (frameInfo?.isConfig == true) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                        var result: QueuePacketResult
                        do {
                            result =
                                queuePacketIntoDecoder(
                                    currentDecoder,
                                    packet.payload,
                                    frameCount,
                                    inputCount,
                                    packetPts,
                                    flags,
                                )
                            if (result == QueuePacketResult.Skipped && isRunning() && !isStopped()) {
                                outputCount += outputDrainer.drainOutputBuffers(bufferInfo)
                            }
                        } while (result == QueuePacketResult.Skipped && isRunning() && !isStopped())

                        when (result) {
                            QueuePacketResult.Break -> {
                            break
                            }
                            QueuePacketResult.Queued -> {
                                inputCount++
                                inputsWithoutOutput++
                                lastPts = packetPts
                                if (inputsWithoutOutput >= MAX_INPUTS_WITHOUT_OUTPUT) {
                                    throw IllegalStateException(
                                        "音频解码器持续收到 $inputsWithoutOutput 个包但没有任何新输出",
                                    )
                                }
                            }
                            QueuePacketResult.Skipped -> Unit
                        }
                    }

                    is dadb.AdbShellPacket.Exit -> throw java.io.EOFException("Audio Stream closed by peer")
                    else -> continue
                }
            } catch (e: IllegalStateException) {
                if (!isRunning() || isStopped()) break
                if (e.message?.contains("executing state") == true ||
                    e.message?.contains("Released state") == true
                ) {
                    throw e
                }
                throw e
            } catch (e: Exception) {
                if (!isRunning() || isStopped()) break
                if (!e.isExpectedShutdown()) LogManager.e(LogTags.AUDIO_DECODER, "解码错误: ${e.message}", e)
                throw e
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
        if (packet.isEmpty()) return 0
        val currentDecoder = getDecoder() ?: throw IllegalStateException("音频解码器不存在")
        while (isRunning() && !isStopped()) {
            when (queuePacketIntoDecoder(currentDecoder, packet, 1, 0, pts, 0)) {
                QueuePacketResult.Queued -> {
                    AudioDebugLog.d(LogTags.AUDIO_DECODER) { "已处理第一个音频包: size=${packet.size}, pts=$pts" }
                    return 1
                }
                QueuePacketResult.Break -> return 0
                QueuePacketResult.Skipped -> Thread.yield()
            }
        }
        return 0
    }

    private fun queuePacketIntoDecoder(
        currentDecoder: MediaCodec,
        payload: ByteArray,
        frameCount: Int,
        inputCount: Int,
        pts: Long,
        flags: Int,
    ): QueuePacketResult {
        var result = QueuePacketResult.Skipped
        synchronized(decoderLock) {
            if (getDecoder() != currentDecoder || isStopped()) {
                result = QueuePacketResult.Break
            } else {
                val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("音频解码器未返回输入缓冲区")
                    inputBuffer.clear()
                    if (payload.size > inputBuffer.remaining()) {
                        throw IllegalStateException(
                            "音频包超过解码器输入容量: packet=${payload.size}, capacity=${inputBuffer.remaining()}",
                        )
                    }
                    inputBuffer.put(payload)
                    currentDecoder.queueInputBuffer(inputIndex, 0, payload.size, pts, flags)

                    result = QueuePacketResult.Queued
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
        isExpectedConnectionClosure() ||
            message?.contains("Pending dequeue output buffer request cancelled") == true

    private companion object {
        const val MAX_INPUTS_WITHOUT_OUTPUT = 150
    }
}

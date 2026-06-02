package com.screen.remote.android.infrastructure.media.audio

import android.media.AudioFormat
import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager

internal class AudioDecoderOutputDrainer(
    private val trackManager: AudioTrackManager,
    private val getDecoder: () -> MediaCodec?,
    private val isStopped: () -> Boolean,
) {
    private var totalOutputCount = 0
    private var firstOutputLogged = false

    fun outputCount(): Int = totalOutputCount

    fun resetAfterDecoderFallback() {
        totalOutputCount = 0
        firstOutputLogged = false
    }

    fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo): Int {
        if (isStopped()) {
            return 0
        }

        val codec = getDecoder() ?: return 0
        var drainedCount = 0

        try {
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            var loopCount = 0

            while (!isStopped() && outputIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                loopCount++

                when {
                    outputIndex >= 0 -> {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "跳过配置缓冲区" }
                            codec.releaseOutputBuffer(outputIndex, false)
                            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                            continue
                        }

                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val outputSlice =
                                outputBuffer
                                    .duplicate()
                                    .apply {
                                        position(bufferInfo.offset)
                                        limit(bufferInfo.offset + bufferInfo.size)
                                    }
                            drainedCount++
                            totalOutputCount++
                            val written =
                                try {
                                    trackManager.writeDecodedData(outputSlice, bufferInfo.size)
                                } catch (error: Exception) {
                                    throw AudioTrackPlaybackException("AudioTrack 写入异常: ${error.message}", error)
                                }

                            if (written < 0) {
                                throw AudioTrackPlaybackException("AudioTrack 写入失败: $written")
                            } else if (!firstOutputLogged) {
                                firstOutputLogged = true
                                AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                                    "首个音频输出: size=${bufferInfo.size}, written=$written, pts=${bufferInfo.presentationTimeUs / 1000}ms"
                                }
                            } else if (totalOutputCount % OUTPUT_LOG_INTERVAL == 0) {
                                AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                                    "音频输出累计 $totalOutputCount 个缓冲区，最近 size=${bufferInfo.size}, pts=${bufferInfo.presentationTimeUs / 1000}ms"
                                }
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
                        val outputFormat = codec.outputFormat
                        val sampleRate = if (outputFormat.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) outputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else -1
                        val channelCount = if (outputFormat.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) outputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else -1
                        val pcmEncoding = if (outputFormat.containsKey(android.media.MediaFormat.KEY_PCM_ENCODING)) outputFormat.getInteger(android.media.MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                            "输出格式变化: $outputFormat, rate=$sampleRate, channels=$channelCount, pcmEncoding=$pcmEncoding"
                        }
                        val trackReconfigured =
                            try {
                                trackManager.reconfigureFromOutputFormat(outputFormat)
                            } catch (error: Exception) {
                                throw AudioTrackPlaybackException("AudioTrack 输出格式重建异常: ${error.message}", error)
                            }
                        if (!trackReconfigured) {
                            throw AudioTrackPlaybackException("无法按解码输出格式重建 AudioTrack: $outputFormat")
                        }
                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    else -> break
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
            if (isStopped()) return 0
            throw e
        }
    }

    private companion object {
        const val OUTPUT_LOG_INTERVAL = 500
    }
}

internal class AudioTrackPlaybackException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/*
 * 音频编解码器测试工具
 *
 * 从 CodecTestUtils.kt 拆分而来
 * 职责：音频编解码器查询和测试
 */

package com.screen.remote.android.feature.codec.ui.test

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.compat.createAudioTrackCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 测试音频解码器
 */
suspend fun testAudioDecoder(
    mimeType: String,
    tts: TextToSpeech?,
    decoderName: String? = null,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val ttsResult = generateTTSAudio(tts)
        var pcmData: ByteArray
        var ttsSampleRate: Int
        var ttsChannelCount: Int

        if (ttsResult != null) {
            pcmData = ttsResult.first
            ttsSampleRate = ttsResult.second
            ttsChannelCount = ttsResult.third
        } else {
            ttsSampleRate = 48000
            ttsChannelCount = 2
            pcmData = generateBeep(sampleRate = ttsSampleRate, channels = ttsChannelCount)
        }

        val targetSampleRate =
            when (mimeType) {
                "audio/3gpp" -> 8000
                "audio/amr-wb" -> 16000
                else -> 48000
            }

        val targetChannelCount = if (mimeType.startsWith("audio/amr")) 1 else 2

        if (ttsSampleRate != targetSampleRate || ttsChannelCount != targetChannelCount) {
            pcmData = resamplePCM(pcmData, ttsSampleRate, ttsChannelCount, targetSampleRate, targetChannelCount)
        }

        if (mimeType == "audio/raw") {
            playRawAudio(pcmData, targetSampleRate, targetChannelCount)
            return@withContext true
        }

        val encoder = MediaCodec.createEncoderByType(mimeType)
        val encodedData = mutableListOf<ByteArray>()
        var csdData: ByteArray? = null
        try {
            val encoderFormat =
                MediaFormat.createAudioFormat(mimeType, targetSampleRate, targetChannelCount).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                    when (mimeType) {
                        MediaFormat.MIMETYPE_AUDIO_AAC -> {
                            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        }

                        MediaFormat.MIMETYPE_AUDIO_FLAC -> {
                            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
                        }
                    }
                }
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var inputDone = false
            var outputDone = false
            var inputOffset = 0

            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val remaining = pcmData.size - inputOffset
                            val size = minOf(remaining, inputBuffer.remaining())

                            if (size > 0) {
                                inputBuffer.put(pcmData, inputOffset, size)
                                encoder.queueInputBuffer(inputIndex, 0, size, 0, 0)
                                inputOffset += size
                            } else if (inputOffset >= pcmData.size) {
                                encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.get(data)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                csdData = data
                            } else {
                                encodedData.add(data)
                            }
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = encoder.outputFormat
                        if (csdData == null) {
                            val csd0 = format.getByteBuffer("csd-0")
                            if (csd0 != null) {
                                csdData = ByteArray(csd0.remaining())
                                csd0.get(csdData)
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
        }

        val decoder =
            if (decoderName.isNullOrBlank()) {
                MediaCodec.createDecoderByType(mimeType)
            } else {
                MediaCodec.createByCodecName(decoderName)
            }
        var audioTrack: AudioTrack? = null
        try {
            val decoderFormat = MediaFormat.createAudioFormat(mimeType, targetSampleRate, targetChannelCount)

            if (csdData != null) {
                decoderFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csdData))
            }

            decoder.configure(decoderFormat, null, null, 0)
            decoder.start()

            val channelConfig =
                if (targetChannelCount == 2) {
                    AudioFormat.CHANNEL_OUT_STEREO
                } else {
                    AudioFormat.CHANNEL_OUT_MONO
                }
            val bufferSize =
                AudioTrack.getMinBufferSize(targetSampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2
            val activeAudioTrack =
                createAudioTrackCompat(
                    sampleRate = targetSampleRate,
                    channelMask = channelConfig,
                    encoding = AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize = bufferSize,
                )
            audioTrack = activeAudioTrack

            activeAudioTrack.play()

            var frameIndex = 0
            var decoderInputDone = false
            var decoderOutputDone = false
            var presentationTimeUs = 0L
            val decoderBufferInfo = MediaCodec.BufferInfo()

            while (!decoderOutputDone) {
                if (!decoderInputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        if (frameIndex < encodedData.size) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                inputBuffer.put(encodedData[frameIndex])
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    encodedData[frameIndex].size,
                                    presentationTimeUs,
                                    0
                                )
                                presentationTimeUs += 20000L
                                frameIndex++
                            }
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decoderInputDone = true
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, 10000)
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && decoderBufferInfo.size > 0) {
                            activeAudioTrack.write(outputBuffer, decoderBufferInfo.size, AudioTrack.WRITE_BLOCKING)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)

                        if ((decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderOutputDone = true
                        }
                    }
                }
            }

            Thread.sleep(500)

            true
        } finally {
            runCatching { audioTrack?.stop() }
            runCatching { audioTrack?.release() }
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
        }
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "Test failed: $mimeType - ${e.message}", e)
        false
    }
}

/**
 * 直接测试音频解码器（不使用编码器，直接播放 PCM）
 */
suspend fun testAudioDecoderDirect(
    codecName: String,
    tts: TextToSpeech?,
): Boolean {
    val codecInfo =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .firstOrNull { !it.isEncoder && it.name == codecName }
            ?: return false
    val mimeType =
        codecInfo.supportedTypes.firstOrNull { type ->
            type.equals(MediaFormat.MIMETYPE_AUDIO_OPUS, true) ||
                type.equals(MediaFormat.MIMETYPE_AUDIO_AAC, true) ||
                type.equals(MediaFormat.MIMETYPE_AUDIO_FLAC, true)
        } ?: return false
    return testAudioDecoder(mimeType = mimeType, tts = tts, decoderName = codecName)
}

/*
 * 音频编解码器测试工具
 * 
 * 从 CodecTestUtils.kt 拆分而来
 * 职责：音频编解码器查询和测试
 */

package com.mobile.scrcpy.android.feature.codec.ui.test

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * 获取所有音频编解码器
 */
fun getAudioCodecs(
    txtSampleRate: String,
    txtMaxChannels: String,
    txtActual: String,
    txtNoDetails: String,
): List<CodecInfo> {
    val result = mutableListOf<CodecInfo>()

    try {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

        for (codecInfo in codecList.codecInfos) {
            val supportedTypes = codecInfo.supportedTypes

            for (type in supportedTypes) {
                if (type.startsWith("audio/")) {
                    val capabilities =
                        try {
                            val caps = codecInfo.getCapabilitiesForType(type)
                            buildString {
                                val audioC = caps.audioCapabilities
                                if (audioC != null) {
                                    append("$txtSampleRate: ")
                                    val rates = audioC.supportedSampleRateRanges
                                    if (rates != null && rates.isNotEmpty()) {
                                        val lower = rates.first().lower
                                        val upper = rates.first().upper

                                        if (type == "audio/opus" && lower == 8000 && upper == 8000) {
                                            append("8000-48000 Hz ($txtActual)")
                                        } else {
                                            append("$lower-$upper Hz")
                                        }
                                    }

                                    val maxChannels = audioC.maxInputChannelCount
                                    append(" | $txtMaxChannels: $maxChannels")
                                }
                            }
                        } catch (_: Exception) {
                            txtNoDetails
                        }

                    result.add(
                        CodecInfo(
                            name = codecInfo.name,
                            type = type,
                            isEncoder = codecInfo.isEncoder,
                            capabilities = capabilities,
                        ),
                    )
                }
            }
        }
        result.sortWith(compareBy({ it.type }, { it.name }))

        // 打印音频解码器信息
        val decoders = result.filter { !it.isEncoder }
        LogManager.i(LogTags.CODEC_TEST_SCREEN, "========== 音频编解码器 ==========")
        LogManager.i(LogTags.CODEC_TEST_SCREEN, "解码器: ${decoders.size} 个")

        // 重点关注 OPUS 解码器
        val opusDecoders = decoders.filter { it.type.contains("opus", ignoreCase = true) }
        if (opusDecoders.isNotEmpty()) {
            LogManager.i(LogTags.CODEC_TEST_SCREEN, "OPUS 解码器 (${opusDecoders.size} 个):")
            opusDecoders.forEach { codec ->
                val isHardware =
                    !codec.name.contains("google", ignoreCase = true) &&
                        !codec.name.contains("c2.android", ignoreCase = true)
                LogManager.i(
                    LogTags.CODEC_TEST_SCREEN,
                    "  ${if (isHardware) "✓ [硬件]" else "✗ [软件]"} ${codec.name}",
                )
            }
        }
        LogManager.i(LogTags.CODEC_TEST_SCREEN, "====================================\n")
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "获取音频编解码器列表失败: ${e.message}", e)
    }

    return result
}

/**
 * 测试音频解码器
 */
suspend fun testAudioDecoder(
    mimeType: String,
    tts: TextToSpeech?,
) = withContext(Dispatchers.IO) {
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
            return@withContext
        }

        val encoder = MediaCodec.createEncoderByType(mimeType)
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

        val encodedData = mutableListOf<ByteArray>()
        var csdData: ByteArray? = null
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
                        try {
                            val csd0 = format.getByteBuffer("csd-0")
                            if (csd0 != null) {
                                csdData = ByteArray(csd0.remaining())
                                csd0.get(csdData)
                            }
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }
                }
            }
        }

        encoder.stop()
        encoder.release()

        val decoder = MediaCodec.createDecoderByType(mimeType)
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
        val audioTrack =
            AudioTrack
                .Builder()
                .setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(targetSampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                ).setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        audioTrack.play()

        var frameIndex = 0
        inputDone = false
        outputDone = false
        var presentationTimeUs = 0L

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    if (frameIndex < encodedData.size) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(encodedData[frameIndex])
                            decoder.queueInputBuffer(inputIndex, 0, encodedData[frameIndex].size, presentationTimeUs, 0)
                            presentationTimeUs += 20000L
                            frameIndex++
                        }
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        audioTrack.write(outputBuffer, bufferInfo.size, AudioTrack.WRITE_BLOCKING)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }

        Thread.sleep(500)

        audioTrack.stop()
        audioTrack.release()
        decoder.stop()
        decoder.release()
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "测试失败: $mimeType - ${e.message}", e)
    }
}

/**
 * 直接测试音频解码器（不使用编码器，直接播放 PCM）
 */
suspend fun testAudioDecoderDirect(
    codecName: String,
    tts: TextToSpeech?,
) = withContext(Dispatchers.IO) {
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

        val targetSampleRate = 48000
        val targetChannelCount = 2

        if (ttsSampleRate != targetSampleRate || ttsChannelCount != targetChannelCount) {
            pcmData = resamplePCM(pcmData, ttsSampleRate, ttsChannelCount, targetSampleRate, targetChannelCount)
        }

        playRawAudio(pcmData, targetSampleRate, targetChannelCount)

        LogManager.i(LogTags.CODEC_TEST_SCREEN, "测试音频解码器成功: $codecName")
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "测试音频解码器失败: $codecName - ${e.message}", e)
    }
}

package com.mobile.scrcpy.android.feature.codec.ui

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.sin

data class CodecInfo(
    val name: String,
    val type: String,
    val isEncoder: Boolean,
    val capabilities: String
)

enum class FilterType {
    ALL, DECODER, ENCODER
}

enum class CodecTypeFilter {
    ALL, OPUS, AAC, FLAC, RAW
}

fun getAudioCodecs(
    txtSampleRate: String,
    txtMaxChannels: String,
    txtActual: String,
    txtNoDetails: String
): List<CodecInfo> {
    val result = mutableListOf<CodecInfo>()

    try {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

        for (codecInfo in codecList.codecInfos) {
            val supportedTypes = codecInfo.supportedTypes

            for (type in supportedTypes) {
                if (type.startsWith("audio/")) {
                    val capabilities = try {
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
                            capabilities = capabilities
                        )
                    )
                }
            }
        }
        result.sortWith(compareBy({ it.type }, { it.name }))

        for (codecInfo in result) {
            if (codecInfo.name.contains("opus", ignoreCase = true) && !codecInfo.isEncoder) {
                LogManager.d(LogTags.CODEC_TEST_SCREEN, "Opus 解码器: ${codecInfo.name}, 类型: ${codecInfo.type}, 能力: ${codecInfo.capabilities}")
            }
        }

        LogManager.d(LogTags.CODEC_TEST_SCREEN, "找到 ${result.size} 个音频编解码器")
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "获取编解码器列表失败: ${e.message}", e)
    }

    return result
}

suspend fun testAudioDecoder(mimeType: String, decoderName: String, tts: TextToSpeech?) = withContext(Dispatchers.IO) {
    try {
        LogManager.d(LogTags.CODEC_TEST_SCREEN, "开始测试解码器: $mimeType 解码器: $decoderName")

        val ttsResult = generateTTSAudio(tts)
        var pcmData: ByteArray
        var ttsSampleRate: Int
        var ttsChannelCount: Int

        if (ttsResult != null) {
            pcmData = ttsResult.first
            ttsSampleRate = ttsResult.second
            ttsChannelCount = ttsResult.third
            LogManager.d(LogTags.CODEC_TEST_SCREEN, "TTS 原始音频: $ttsSampleRate Hz, $ttsChannelCount 声道, ${pcmData.size} 字节")
        } else {
            LogManager.e(LogTags.CODEC_TEST_SCREEN, "TTS 生成失败，使用哔声")
            ttsSampleRate = 48000
            ttsChannelCount = 2
            pcmData = generateBeep(sampleRate = ttsSampleRate, channels = ttsChannelCount)
        }

        val targetSampleRate = when (mimeType) {
            "audio/3gpp" -> 8000
            "audio/amr-wb" -> 16000
            else -> 48000
        }

        val targetChannelCount = if (mimeType.startsWith("audio/amr")) 1 else 2

        if (ttsSampleRate != targetSampleRate || ttsChannelCount != targetChannelCount) {
            LogManager.d(LogTags.CODEC_TEST_SCREEN, "重采样: $ttsSampleRate Hz -> $targetSampleRate Hz, $ttsChannelCount -> $targetChannelCount 声道")
            pcmData = resamplePCM(pcmData, ttsSampleRate, ttsChannelCount, targetSampleRate, targetChannelCount)
        }

        LogManager.d(LogTags.CODEC_TEST_SCREEN, "目标格式: $targetSampleRate Hz, $targetChannelCount 声道, ${pcmData.size} 字节")

        if (mimeType == "audio/raw") {
            playRawAudio(pcmData, targetSampleRate, targetChannelCount)
            return@withContext
        }

        val encoder = MediaCodec.createEncoderByType(mimeType)
        val encoderFormat = MediaFormat.createAudioFormat(mimeType, targetSampleRate, targetChannelCount).apply {
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

        val channelConfig = if (targetChannelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val bufferSize = AudioTrack.getMinBufferSize(targetSampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2
        val audioTrack = AudioTrack.Builder()
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(targetSampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(bufferSize)
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

        LogManager.d(LogTags.CODEC_TEST_SCREEN, "✅ 测试成功: $mimeType")

    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "❌ 测试失败: $mimeType 解码器: $decoderName - ${e.message}", e)
    }
}

private fun playRawAudio(pcmData: ByteArray, sampleRate: Int, channelCount: Int) {
    val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2
    val audioTrack = AudioTrack.Builder()
        .setAudioFormat(AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build())
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    audioTrack.play()
    audioTrack.write(pcmData, 0, pcmData.size)
    Thread.sleep(1500)
    audioTrack.stop()
    audioTrack.release()

    LogManager.d(LogTags.CODEC_TEST_SCREEN, "✅ RAW 音频播放完成")
}

private suspend fun generateTTSAudio(tts: TextToSpeech?): Triple<ByteArray, Int, Int>? =
    withContext(Dispatchers.IO) {
        if (tts == null) return@withContext null

        val file = java.io.File.createTempFile("tts", ".wav")
        try {
            var done = false
            @Suppress("OVERRIDE_DEPRECATION")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { done = true }
                override fun onError(utteranceId: String?) { done = true }
            })

            val result = tts.synthesizeToFile("Hello World", null, file, "tts_gen")
            if (result == TextToSpeech.SUCCESS) {
                var waitCount = 0
                while (!done && waitCount < 50) {
                    delay(100)
                    waitCount++
                }

                if (file.exists() && file.length() > 44) {
                    val wavData = file.readBytes()

                    val buffer = ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    buffer.position(22)
                    val channels = buffer.short.toInt()
                    val sampleRate = buffer.int

                    val pcmData = wavData.copyOfRange(44, wavData.size)

                    val duration = pcmData.size / (sampleRate * channels * 2.0)
                    LogManager.d(LogTags.CODEC_TEST_SCREEN, "TTS 生成音频: $sampleRate Hz, $channels 声道, ${pcmData.size} 字节 (${"%.2f".format(duration)} 秒)")

                    return@withContext Triple(pcmData, sampleRate, channels)
                }
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.CODEC_TEST_SCREEN, "TTS 生成失败: ${e.message}", e)
        } finally {
            file.delete()
        }
        return@withContext null
    }

private fun generateBeep(frequency: Double = 800.0, sampleRate: Int, channels: Int, duration: Double = 0.5): ByteArray {
    val numSamples = (sampleRate * duration).toInt()
    val buffer = ByteBuffer.allocate(numSamples * channels * 2)

    for (i in 0 until numSamples) {
        val sample = (sin(2.0 * Math.PI * frequency * i / sampleRate) * 32767).toInt().toShort()
        for (ch in 0 until channels) {
            buffer.putShort(sample)
        }
    }

    return buffer.array()
}

private fun resamplePCM(
    input: ByteArray,
    srcRate: Int,
    srcChannels: Int,
    dstRate: Int,
    dstChannels: Int
): ByteArray {
    val srcSamples = input.size / (srcChannels * 2)
    val dstSamples = (srcSamples * dstRate.toDouble() / srcRate).toInt()
    val output = ByteBuffer.allocate(dstSamples * dstChannels * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val inputBuffer = ByteBuffer.wrap(input).order(java.nio.ByteOrder.LITTLE_ENDIAN)

    for (i in 0 until dstSamples) {
        val srcPos = i * srcRate.toDouble() / dstRate
        val srcIndex = srcPos.toInt()

        if (srcIndex < srcSamples - 1) {
            val frac = srcPos - srcIndex

            for (ch in 0 until dstChannels) {
                val srcCh = if (ch < srcChannels) ch else 0

                val pos1 = (srcIndex * srcChannels + srcCh) * 2
                val pos2 = ((srcIndex + 1) * srcChannels + srcCh) * 2

                val sample1 = inputBuffer.getShort(pos1).toInt()
                val sample2 = inputBuffer.getShort(pos2).toInt()

                val interpolated = (sample1 + (sample2 - sample1) * frac).toInt().toShort()
                output.putShort(interpolated)
            }
        } else {
            for (ch in 0 until dstChannels) {
                val srcCh = if (ch < srcChannels) ch else 0
                val pos = (srcIndex * srcChannels + srcCh) * 2
                output.putShort(inputBuffer.getShort(pos))
            }
        }
    }

    return output.array()
}

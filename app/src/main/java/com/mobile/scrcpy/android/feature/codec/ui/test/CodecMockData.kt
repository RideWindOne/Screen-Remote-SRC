/*
 * 编解码器测试数据生成工具
 * 
 * 从 CodecTestUtils.kt 拆分而来
 * 职责：生成测试音频数据（TTS、Beep、重采样）
 */

package com.mobile.scrcpy.android.feature.codec.ui.test

import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.sin

/**
 * 播放原始 PCM 音频
 */
internal fun playRawAudio(
    pcmData: ByteArray,
    sampleRate: Int,
    channelCount: Int,
) {
    val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2
    val audioTrack =
        AudioTrack
            .Builder()
            .setAudioFormat(
                AudioFormat
                    .Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            ).setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

    audioTrack.play()
    audioTrack.write(pcmData, 0, pcmData.size)
    Thread.sleep(1500)
    audioTrack.stop()
    audioTrack.release()
}

/**
 * 使用 TTS 生成音频数据
 */
internal suspend fun generateTTSAudio(tts: TextToSpeech?): Triple<ByteArray, Int, Int>? =
    withContext(Dispatchers.IO) {
        if (tts == null) {
            LogManager.w(LogTags.CODEC_TEST_SCREEN, "TTS 实例为 null")
            return@withContext null
        }

        val file = java.io.File.createTempFile("tts", ".wav")
        try {
            var done = false
            var hasError = false

            @Suppress("OVERRIDE_DEPRECATION")
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        LogManager.d(LogTags.CODEC_TEST_SCREEN, "TTS 开始合成")
                    }

                    override fun onDone(utteranceId: String?) {
                        LogManager.d(LogTags.CODEC_TEST_SCREEN, "TTS 合成完成")
                        done = true
                    }

                    override fun onError(utteranceId: String?) {
                        LogManager.e(LogTags.CODEC_TEST_SCREEN, "TTS 合成出错")
                        done = true
                        hasError = true
                    }
                },
            )

            val result = tts.synthesizeToFile("Hello World", null, file, "tts_gen")

            if (result == TextToSpeech.SUCCESS) {
                var waitCount = 0
                while (!done && waitCount < 50) {
                    delay(100)
                    waitCount++
                }

                if (!done || hasError || !file.exists() || file.length() <= 44) {
                    return@withContext null
                }

                val wavData = file.readBytes()
                val buffer = ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buffer.position(22)
                val channels = buffer.short.toInt()
                val sampleRate = buffer.int

                val pcmData = wavData.copyOfRange(44, wavData.size)
                return@withContext Triple(pcmData, sampleRate, channels)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.CODEC_TEST_SCREEN, "TTS 异常: ${e.message}", e)
        } finally {
            file.delete()
        }
        return@withContext null
    }

/**
 * 生成 Beep 音频数据
 */
internal fun generateBeep(
    frequency: Double = 800.0,
    sampleRate: Int,
    channels: Int,
    duration: Double = 0.5,
): ByteArray {
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

/**
 * PCM 重采样
 */
internal fun resamplePCM(
    input: ByteArray,
    srcRate: Int,
    srcChannels: Int,
    dstRate: Int,
    dstChannels: Int,
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

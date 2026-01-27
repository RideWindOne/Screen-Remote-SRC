package com.mobile.scrcpy.android.infrastructure.media.audio

import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

/**
 * AudioTrackManager - AudioTrack 管理器
 * 负责 AudioTrack 创建、音量控制和数据写入
 */
class AudioTrackManager(private val volumeScale: Float = 1.0f) {

    @Volatile private var audioTrack: AudioTrack? = null

    /**
     * 创建 AudioTrack
     */
    fun createAudioTrack(sampleRate: Int, channelCount: Int): AudioTrack? {
        return try {
            val channelConfig = if (channelCount == 2) {
                AudioFormat.CHANNEL_OUT_STEREO
            } else {
                AudioFormat.CHANNEL_OUT_MONO
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4

            val track = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track

            LogManager.d(
                LogTags.AUDIO_DECODER,
                "AudioTrack 创建成功: rate=$sampleRate, channels=$channelCount, bufferSize=$bufferSize"
            )
            track
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "创建 AudioTrack 失败: ${e.message}", e)
            null
        }
    }

    /**
     * 启动播放
     */
    fun play() {
        audioTrack?.play()
    }

    /**
     * 停止并释放
     */
    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // 忽略
        } finally {
            audioTrack = null
        }
    }

    /**
     * 写入 RAW 数据（ByteArray）
     */
    fun writeRawData(data: ByteArray): Int {
        val track = audioTrack ?: return -1

        val scaledData = if (volumeScale != 1.0f) {
            applyVolumeScale(data, volumeScale)
        } else {
            data
        }

        return track.write(scaledData, 0, scaledData.size)
    }

    /**
     * 写入解码后的数据（ByteBuffer）
     */
    fun writeDecodedData(buffer: ByteBuffer, size: Int): Int {
        val track = audioTrack ?: return -1

        if (volumeScale != 1.0f) {
            applyVolumeScaleToBuffer(buffer, size, volumeScale)
        }

        return track.write(buffer, size, AudioTrack.WRITE_BLOCKING)
    }

    /**
     * 应用音量缩放到 PCM 数据
     * @param data PCM 16-bit 数据
     * @param scale 音量缩放系数 (0.1 ~ 2.0)
     * @return 缩放后的数据
     */
    private fun applyVolumeScale(data: ByteArray, scale: Float): ByteArray {
        if (scale == 1.0f) return data

        val scaledData = ByteArray(data.size)

        // PCM 16-bit 数据，每 2 个字节是一个样本
        for (i in 0 until data.size step 2) {
            if (i + 1 >= data.size) break

            // 读取 16-bit 样本 (小端序)
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()

            // 应用音量缩放
            var scaledSample = (sample * scale).toInt()

            // 限制在 16-bit 范围内，避免溢出
            scaledSample = scaledSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            // 写回数据 (小端序)
            scaledData[i] = (scaledSample and 0xFF).toByte()
            scaledData[i + 1] = ((scaledSample shr 8) and 0xFF).toByte()
        }

        return scaledData
    }

    /**
     * 应用音量缩放到 ByteBuffer (PCM 16-bit)
     * @param buffer PCM 数据缓冲区
     * @param size 数据大小
     * @param scale 音量缩放系数 (0.1 ~ 2.0)
     */
    private fun applyVolumeScaleToBuffer(buffer: ByteBuffer, size: Int, scale: Float) {
        if (scale == 1.0f) return

        val position = buffer.position()

        // PCM 16-bit 数据，每 2 个字节是一个样本
        for (i in 0 until size step 2) {
            if (i + 1 >= size) break

            // 读取 16-bit 样本 (小端序)
            val byte1 = buffer.get(position + i).toInt() and 0xFF
            val byte2 = buffer.get(position + i + 1).toInt()
            val sample = ((byte2 shl 8) or byte1).toShort()

            // 应用音量缩放
            var scaledSample = (sample * scale).toInt()

            // 限制在 16-bit 范围内，避免溢出
            scaledSample = scaledSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            // 写回数据 (小端序)
            buffer.put(position + i, (scaledSample and 0xFF).toByte())
            buffer.put(position + i + 1, ((scaledSample shr 8) and 0xFF).toByte())
        }
    }

    /**
     * 获取当前 AudioTrack 实例
     */
    fun getAudioTrack(): AudioTrack? = audioTrack
}

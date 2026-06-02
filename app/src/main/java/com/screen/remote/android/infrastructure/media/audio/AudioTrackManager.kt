package com.screen.remote.android.infrastructure.media.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.MediaFormat
import android.media.AudioTrack
import android.os.Build
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import java.nio.ByteBuffer

/**
 * AudioTrackManager - AudioTrack 管理器
 * 负责 AudioTrack 创建、音量控制和数据写入
 */
class AudioTrackManager(
    private val volumeScale: Float = 1.0f,
) {
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var trackConfig: AudioTrackConfig? = null
    @Volatile private var nonPcm16ScalingWarningLogged = false

    /**
     * 创建 AudioTrack
     */
    fun createAudioTrack(
        sampleRate: Int,
        channelCount: Int,
        encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
    ): AudioTrack? =
        try {
            val config = AudioTrackConfig(sampleRate = sampleRate, channelCount = channelCount, encoding = encoding)
            val channelConfig = config.channelMask
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
            if (minBufferSize <= 0) {
                LogManager.e(
                    LogTags.AUDIO_DECODER,
                    "AudioTrack 最小缓冲区获取失败: rate=$sampleRate, channels=$channelCount, " +
                        "encoding=${encodingName(encoding)}, minBufferSize=$minBufferSize",
                )
                return null
            }
            val bufferSize = minBufferSize * 2

            val trackBuilder =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .setEncoding(encoding)
                            .build(),
                    ).setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val track = trackBuilder.build()

            audioTrack = track
            trackConfig = config
            nonPcm16ScalingWarningLogged = false

            AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                "AudioTrack 创建成功: rate=$sampleRate, channels=$channelCount, " +
                    "encoding=${encodingName(encoding)}, bufferSize=$bufferSize"
            }
            track
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "创建 AudioTrack 失败: ${e.message}", e)
            null
        }

    fun reconfigureFromOutputFormat(outputFormat: MediaFormat): Boolean {
        val sampleRate = outputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: return false
        val channelCount = outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: return false
        val encoding = outputFormat.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
        val newConfig = AudioTrackConfig(sampleRate = sampleRate, channelCount = channelCount, encoding = encoding)

        if (trackConfig == newConfig && audioTrack != null) {
            return true
        }

        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
            "按解码输出格式重建 AudioTrack: rate=$sampleRate, channels=$channelCount, encoding=${encodingName(encoding)}"
        }

        release()
        val recreated =
            createAudioTrack(
                sampleRate = sampleRate,
                channelCount = channelCount,
                encoding = encoding,
            )
        if (recreated != null) {
            recreated.play()
            return true
        }
        return false
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
            trackConfig = null
        }
    }

    /**
     * 写入 RAW 数据（ByteArray）
     */
    fun writeRawData(data: ByteArray): Int {
        val track = audioTrack ?: return -1

        val scaledData =
            if (volumeScale != 1.0f) {
                applyVolumeScale(data, volumeScale)
            } else {
                data
            }

        return writeFully(track, scaledData)
    }

    /**
     * 写入解码后的数据（ByteBuffer）
     */
    fun writeDecodedData(
        buffer: ByteBuffer,
        size: Int,
    ): Int {
        val track = audioTrack ?: return -1
        val config = trackConfig

        val bytes = ByteArray(size)
        buffer.duplicate().get(bytes)
        val output =
            if (volumeScale != 1.0f && config?.encoding == AudioFormat.ENCODING_PCM_16BIT) {
                applyVolumeScale(bytes, volumeScale)
            } else {
                bytes
            }

        if (volumeScale != 1.0f && config?.encoding != AudioFormat.ENCODING_PCM_16BIT &&
            config?.encoding != null && !nonPcm16ScalingWarningLogged
        ) {
            nonPcm16ScalingWarningLogged = true
            LogManager.w(
                LogTags.AUDIO_DECODER,
                "当前输出编码=${encodingName(config.encoding)}，跳过非 PCM16 的音量缩放",
            )
        }

        return writeFully(track, output)
    }

    /**
     * 应用音量缩放到 PCM 数据
     * @param data PCM 16-bit 数据
     * @param scale 音量缩放系数 (0.1 ~ 2.0)
     * @return 缩放后的数据
     */
    private fun applyVolumeScale(
        data: ByteArray,
        scale: Float,
    ): ByteArray {
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

    private fun writeFully(
        track: AudioTrack,
        data: ByteArray,
    ): Int {
        var offset = 0
        while (offset < data.size) {
            val written = track.write(data, offset, data.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) return written
            if (written == 0) continue
            offset += written
        }
        return offset
    }

    /**
     * 获取当前 AudioTrack 实例
     */
    fun getAudioTrack(): AudioTrack? = audioTrack

    private fun encodingName(encoding: Int): String =
        when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT"
            AudioFormat.ENCODING_PCM_8BIT -> "PCM_8BIT"
            AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM_24BIT_PACKED"
            AudioFormat.ENCODING_PCM_32BIT -> "PCM_32BIT"
            else -> "UNKNOWN($encoding)"
        }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null
}

private data class AudioTrackConfig(
    val sampleRate: Int,
    val channelCount: Int,
    val encoding: Int,
) {
    val channelMask: Int
        get() =
            if (channelCount <= 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
}

package com.mobile.scrcpy.android.infrastructure.media.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

/**
 * AudioFormatHandler - 音频格式处理器
 * 负责配置包验证、解码器创建和配置
 */
class AudioFormatHandler {

    /**
     * 验证配置包格式
     */
    fun validateConfigPacket(codec: String, data: ByteArray): Boolean {
        return when (codec.lowercase()) {
            "opus" -> validateOpusConfig(data)
            "aac" -> data.size == 2  // AudioSpecificConfig: 2 字节
            "flac" -> data.size == 34  // STREAMINFO: 34 字节
            else -> false
        }
    }

    /**
     * 验证 Opus 配置包
     */
    private fun validateOpusConfig(data: ByteArray): Boolean {
        // OpusHead: 19 字节，以 "OpusHead" 开头
        if (data.size != 19) {
            LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包大小错误: ${data.size}, 期望 19")
            return false
        }

        val header = String(data.copyOfRange(0, 8), Charsets.US_ASCII)
        if (header != "OpusHead") {
            LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包头错误: $header, 期望 OpusHead")
            return false
        }

        // 详细解析并打印
        val version = data[8].toInt() and 0xFF
        val channels = data[9].toInt() and 0xFF
        val preSkip = (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8)
        val sampleRate = (data[12].toInt() and 0xFF) or
                        ((data[13].toInt() and 0xFF) shl 8) or
                        ((data[14].toInt() and 0xFF) shl 16) or
                        ((data[15].toInt() and 0xFF) shl 24)
        val outputGain = (data[16].toInt() and 0xFF) or ((data[17].toInt() and 0xFF) shl 8)
        val channelMapping = data[18].toInt() and 0xFF

        LogManager.d(
            LogTags.AUDIO_DECODER,
            "OpusHead 详细: version=$version, channels=$channels, preSkip=$preSkip, " +
            "sampleRate=$sampleRate, outputGain=$outputGain, channelMapping=$channelMapping"
        )

        return true
    }

    /**
     * 检查是否为 OpusHead 配置包
     */
    fun isOpusHead(data: ByteArray): Boolean {
        return data.size == 19 && String(data.copyOfRange(0, 8), Charsets.US_ASCII) == "OpusHead"
    }

    /**
     * 创建解码器
     */
    fun createDecoder(
        codec: String,
        sampleRate: Int,
        channelCount: Int,
        configData: ByteArray?
    ): MediaCodec? {
        return try {
            val mime = getMediaMimeType(codec) ?: return null
            val format = createMediaFormat(mime, sampleRate, channelCount, configData)

            LogManager.d(LogTags.AUDIO_DECODER, "MediaFormat: $format")

            val mediaCodec = MediaCodec.createDecoderByType(mime)

            try {
                mediaCodec.configure(format, null, null, 0)
                mediaCodec.start()

                // 验证解码器状态
                if (!validateDecoderState(mediaCodec)) {
                    mediaCodec.release()
                    return null
                }

                LogManager.d(LogTags.AUDIO_DECODER, "解码器创建成功: ${mediaCodec.name}")
                return mediaCodec

            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "配置解码器失败: ${e.message}", e)
                try {
                    mediaCodec.release()
                } catch (ignored: Exception) {
                }
                return null
            }

        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "创建解码器失败: ${e.message}", e)
            null
        }
    }

    /**
     * 获取 MIME 类型
     */
    private fun getMediaMimeType(codec: String): String? {
        return when (codec.lowercase()) {
            "opus" -> MediaFormat.MIMETYPE_AUDIO_OPUS
            "aac" -> MediaFormat.MIMETYPE_AUDIO_AAC
            "flac" -> MediaFormat.MIMETYPE_AUDIO_FLAC
            else -> {
                LogManager.e(LogTags.AUDIO_DECODER, "不支持的编码格式: $codec")
                null
            }
        }
    }

    /**
     * 创建 MediaFormat
     */
    private fun createMediaFormat(
        mime: String,
        sampleRate: Int,
        channelCount: Int,
        configData: ByteArray?
    ): MediaFormat {
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount)

        // 设置配置数据（如果有）
        if (configData != null && configData.isNotEmpty()) {
            val csd0 = ByteBuffer.wrap(configData)
            format.setByteBuffer("csd-0", csd0)
            LogManager.d(LogTags.AUDIO_DECODER, "配置: csd-0=${configData.size}字节")
        } else {
            LogManager.d(LogTags.AUDIO_DECODER, "无配置数据，让解码器自动处理")
        }

        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        return format
    }

    /**
     * 验证解码器状态
     */
    private fun validateDecoderState(decoder: MediaCodec): Boolean {
        return try {
            val testIndex = decoder.dequeueInputBuffer(0)
            if (testIndex < 0 && testIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                LogManager.e(LogTags.AUDIO_DECODER, "解码器状态异常: $testIndex")
                return false
            }
            LogManager.d(LogTags.AUDIO_DECODER, "解码器状态验证成功")
            true
        } catch (e: IllegalStateException) {
            LogManager.e(LogTags.AUDIO_DECODER, "解码器状态验证失败: ${e.message}", e)
            false
        }
    }
}

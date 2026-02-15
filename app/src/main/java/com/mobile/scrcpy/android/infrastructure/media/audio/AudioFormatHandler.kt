package com.mobile.scrcpy.android.infrastructure.media.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import java.nio.ByteBuffer

/**
 * AudioFormatHandler - 音频格式处理器
 * 负责配置包验证、解码器创建和配置
 */
class AudioFormatHandler {
    internal fun resolvePlaybackFormat(
        codec: String,
        sampleRate: Int,
        channelCount: Int,
        configData: ByteArray?,
    ): ResolvedAudioFormat =
        when (codec.lowercase()) {
            "opus" -> {
                val opusConfig = configData?.let(OpusConfigParser::parse)
                if (opusConfig != null) {
                    val resolvedFormat =
                        ResolvedAudioFormat(
                            sampleRate = OpusConfigParser.OPUS_OUTPUT_SAMPLE_RATE,
                            channelCount = opusConfig.channelCount,
                        )
                    AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                        "Opus 输出格式解析: rate=${resolvedFormat.sampleRate}, channels=${resolvedFormat.channelCount}, " +
                            "preSkip=${opusConfig.preSkipSamples}, inputRate=${opusConfig.originalSampleRate}"
                    }
                    resolvedFormat
                } else {
                    ResolvedAudioFormat(sampleRate = sampleRate, channelCount = channelCount)
                }
            }

            "flac" -> {
                val flacConfig = configData?.let(FlacConfigParser::parseStreamInfo)
                if (flacConfig != null) {
                    val resolvedFormat =
                        ResolvedAudioFormat(
                            sampleRate = flacConfig.sampleRate,
                            channelCount = flacConfig.channelCount,
                        )
                    AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                        "FLAC 输出格式解析: rate=${resolvedFormat.sampleRate}, channels=${resolvedFormat.channelCount}, " +
                            "bitsPerSample=${flacConfig.bitsPerSample}, minBlock=${flacConfig.minBlockSize}, maxBlock=${flacConfig.maxBlockSize}"
                    }
                    resolvedFormat
                } else {
                    ResolvedAudioFormat(sampleRate = sampleRate, channelCount = channelCount)
                }
            }

            else -> ResolvedAudioFormat(sampleRate = sampleRate, channelCount = channelCount)
        }

    /**
     * 验证配置包格式
     */
    fun validateConfigPacket(
        codec: String,
        data: ByteArray,
    ): Boolean =
        when (codec.lowercase()) {
            "opus" -> validateOpusConfig(data)

            "aac" -> data.size == 2

            // AudioSpecificConfig: 2 字节
            "flac" -> validateFlacConfig(data)

            // STREAMINFO: 34 字节
            else -> false
        }

    /**
     * 验证 Opus 配置包
     */
    private fun validateOpusConfig(data: ByteArray): Boolean {
        if (data.size != OpusConfigParser.OPUS_HEADER_SIZE) {
            LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包大小错误: ${data.size}, 期望 19")
            return false
        }

        val opusConfig = OpusConfigParser.parse(data)
        if (opusConfig == null) {
            val header = String(data.copyOfRange(0, 8), Charsets.US_ASCII)
            LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包头错误: $header, 期望 OpusHead")
            return false
        }

        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
            "OpusHead 详细: version=${opusConfig.version}, channels=${opusConfig.channelCount}, " +
                "preSkip=${opusConfig.preSkipSamples}, sampleRate=${opusConfig.originalSampleRate}, " +
                "outputGain=${opusConfig.outputGain}, channelMapping=${opusConfig.channelMappingFamily}"
        }

        return true
    }

    private fun validateFlacConfig(data: ByteArray): Boolean {
        val streamInfo = FlacConfigParser.parseStreamInfo(data)
        if (streamInfo == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "FLAC 配置包错误: size=${data.size}, 无法解析 STREAMINFO")
            return false
        }

        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
            "FLAC STREAMINFO: rate=${streamInfo.sampleRate}, channels=${streamInfo.channelCount}, " +
                "bitsPerSample=${streamInfo.bitsPerSample}, totalSamples=${streamInfo.totalSamples}, " +
                "blockSize=${streamInfo.minBlockSize}-${streamInfo.maxBlockSize}"
        }
        return true
    }

    /**
     * 检查是否为 OpusHead 配置包
     */
    fun isOpusHead(data: ByteArray): Boolean = OpusConfigParser.isOpusHead(data)

    /**
     * 创建解码器
     */
    fun createDecoder(
        codec: String,
        sampleRate: Int,
        channelCount: Int,
        configData: ByteArray?,
    ): MediaCodec? {
        return try {
            val mime = getMediaMimeType(codec) ?: return null
            val resolvedFormat = resolvePlaybackFormat(codec, sampleRate, channelCount, configData)
            val format =
                createMediaFormat(
                    codec = codec,
                    mime = mime,
                    sampleRate = resolvedFormat.sampleRate,
                    channelCount = resolvedFormat.channelCount,
                    configData = configData,
                )

            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "MediaFormat: $format" }

            val mediaCodec = MediaCodec.createDecoderByType(mime)

            try {
                mediaCodec.configure(format, null, null, 0)
                mediaCodec.start()

                // 验证解码器状态
                if (!validateDecoderState(mediaCodec)) {
                    mediaCodec.release()
                    return null
                }

                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "解码器创建成功: ${mediaCodec.name}" }
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
    private fun getMediaMimeType(codec: String): String? =
        when (codec.lowercase()) {
            "opus" -> {
                MediaFormat.MIMETYPE_AUDIO_OPUS
            }

            "aac" -> {
                MediaFormat.MIMETYPE_AUDIO_AAC
            }

            "flac" -> {
                MediaFormat.MIMETYPE_AUDIO_FLAC
            }

            else -> {
                LogManager.e(LogTags.AUDIO_DECODER, "不支持的编码格式: $codec")
                null
            }
        }

    /**
     * 创建 MediaFormat
     */
    private fun createMediaFormat(
        codec: String,
        mime: String,
        sampleRate: Int,
        channelCount: Int,
        configData: ByteArray?,
    ): MediaFormat {
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount)

        applyCodecSpecificData(codec = codec, format = format, configData = configData)

        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        return format
    }

    private fun applyCodecSpecificData(
        codec: String,
        format: MediaFormat,
        configData: ByteArray?,
    ) {
        if (configData == null || configData.isEmpty()) {
            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "无配置数据，让解码器自动处理" }
            return
        }

        when (codec.lowercase()) {
            "opus" -> {
                val opusConfig = OpusConfigParser.parse(configData)
                if (opusConfig == null) {
                    LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包无效，无法设置初始化数据")
                    return
                }

                val initData = OpusConfigParser.buildInitializationData(opusConfig)
                format.setByteBuffer("csd-0", ByteBuffer.wrap(initData[0]))
                format.setByteBuffer("csd-1", ByteBuffer.wrap(initData[1]))
                format.setByteBuffer("csd-2", ByteBuffer.wrap(initData[2]))
                AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                    "配置 Opus 初始化数据: csd-0=${initData[0].size}字节, csd-1=${initData[1].size}字节, " +
                        "csd-2=${initData[2].size}字节"
                }
            }

            "flac" -> {
                val streamInfo = FlacConfigParser.parseStreamInfo(configData)
                if (streamInfo == null) {
                    LogManager.e(LogTags.AUDIO_DECODER, "FLAC 配置包无效，无法设置初始化数据")
                    return
                }

                val initData = FlacConfigParser.buildInitializationData(streamInfo.rawStreamInfo)
                format.setByteBuffer("csd-0", ByteBuffer.wrap(initData))
                AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                    "配置 FLAC 初始化数据: rawStreamInfo=${streamInfo.rawStreamInfo.size}字节, csd-0=${initData.size}字节"
                }
            }

            else -> {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(configData))
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "配置: csd-0=${configData.size}字节" }
            }
        }
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
            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "解码器状态验证成功" }
            true
        } catch (e: IllegalStateException) {
            LogManager.e(LogTags.AUDIO_DECODER, "解码器状态验证失败: ${e.message}", e)
            false
        }
    }
}

internal data class ResolvedAudioFormat(
    val sampleRate: Int,
    val channelCount: Int,
)

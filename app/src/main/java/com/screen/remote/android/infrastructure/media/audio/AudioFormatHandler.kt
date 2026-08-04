package com.screen.remote.android.infrastructure.media.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import java.nio.ByteBuffer

/**
 * AudioFormatHandler - 音频格式处理器
 * 负责配置包验证、解码器创建和配置
 */
class AudioFormatHandler(
    private val preferredDecoderName: String? = null,
    private val allowHardwareDecoders: Boolean = true,
    private val decoderSelectionPinned: Boolean = false,
    initialRejectedDecoderNames: Set<String> = emptySet(),
) {
    private val rejectedDecoderNames = linkedSetOf<String>().apply { addAll(initialRejectedDecoderNames) }
    var onDecoderSelected: ((String) -> Unit)? = null
    var onDecoderRejected: ((String) -> Unit)? = null

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
                        "Parsed Opus output format: rate=${resolvedFormat.sampleRate}, channels=${resolvedFormat.channelCount}, " +
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
                        "Parsed FLAC output format: rate=${resolvedFormat.sampleRate}, channels=${resolvedFormat.channelCount}, " +
                            "bitsPerSample=${flacConfig.bitsPerSample}, minBlock=${flacConfig.minBlockSize}, maxBlock=${flacConfig.maxBlockSize}"
                    }
                    resolvedFormat
                } else {
                    ResolvedAudioFormat(sampleRate = sampleRate, channelCount = channelCount)
                }
            }

            "aac" -> {
                val aacConfig = configData?.let(AacConfigParser::parse)
                if (aacConfig != null) {
                    ResolvedAudioFormat(sampleRate = aacConfig.sampleRate, channelCount = aacConfig.channelCount)
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

            // AudioSpecificConfig 最短 2 字节，也可能携带扩展字段。
            "aac" -> data.size >= 2
            "flac" -> validateFlacConfig(data)

            // STREAMINFO: 34 字节
            else -> false
        }

    /**
     * 验证 Opus 配置包
     */
    private fun validateOpusConfig(data: ByteArray): Boolean {
        if (data.size < OpusConfigParser.OPUS_HEADER_SIZE) {
            LogManager.e(
                LogTags.AUDIO_DECODER,
                "Opus configuration package size error: ${data.size}, at least 19 required"
            )
            return false
        }

        val opusConfig = OpusConfigParser.parse(data)
        if (opusConfig == null) {
            val header = String(data.copyOfRange(0, 8), Charsets.US_ASCII)
            LogManager.e(LogTags.AUDIO_DECODER, "Opus configuration header error: $header, expected OpusHead")
            return false
        }

        AudioDebugLog.d(LogTags.AUDIO_DECODER) {
            "OpusHead details: version=${opusConfig.version}, channels=${opusConfig.channelCount}, " +
                "preSkip=${opusConfig.preSkipSamples}, sampleRate=${opusConfig.originalSampleRate}, " +
                "outputGain=${opusConfig.outputGain}, channelMapping=${opusConfig.channelMappingFamily}"
        }

        return true
    }

    private fun validateFlacConfig(data: ByteArray): Boolean {
        val streamInfo = FlacConfigParser.parseStreamInfo(data)
        if (streamInfo == null) {
            LogManager.e(
                LogTags.AUDIO_DECODER,
                "FLAC configuration package error: size=${data.size}, cannot parse STREAMINFO"
            )
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

            val candidates = decoderCandidates(mime)
            for (info in candidates) {
                val mediaCodec = runCatching { MediaCodec.createByCodecName(info.name) }.getOrElse { error ->
                    LogManager.w(
                        LogTags.AUDIO_DECODER,
                        "Failed to create audio decoder: ${info.name}: ${error.message}"
                    )
                    continue
                }
                try {
                    mediaCodec.configure(format, null, null, 0)
                    mediaCodec.start()
                    onDecoderSelected?.invoke(mediaCodec.name)
                    AudioDebugLog.d(LogTags.AUDIO_DECODER) { "Audio decoder created successfully: ${mediaCodec.name}" }
                    return mediaCodec
                } catch (e: Exception) {
                    LogManager.w(
                        LogTags.AUDIO_DECODER,
                        "Audio decoder configuration failed, try next candidate: ${info.name}: ${e.message}"
                    )
                    runCatching { mediaCodec.stop() }
                    runCatching { mediaCodec.release() }
                }
            }
            LogManager.e(LogTags.AUDIO_DECODER, "No audio codec available: mime=$mime")
            null
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "Failed to create decoder: ${e.message}", e)
            null
        }
    }

    /**
     * 获取 MIME 类型
     */
    private fun getMediaMimeType(codec: String): String? =
        CodecCatalog.mimeType(CodecMediaType.AUDIO, codec).also { mime ->
            if (mime == null || mime == "audio/raw") {
                LogManager.e(LogTags.AUDIO_DECODER, "Unsupported compressed audio format: $codec")
            }
        }?.takeUnless { it == "audio/raw" }

    private fun decoderCandidates(mime: String): List<MediaCodecInfo> {
        val matching =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder &&
                    info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                    info.name !in rejectedDecoderNames &&
                    (allowHardwareDecoders || !ApiCompatHelper.isHardwareAccelerated(info))
            }
        if (decoderSelectionPinned) {
            return matching.filter { it.name == preferredDecoderName }
        }
        return matching.sortedWith(
            compareBy<MediaCodecInfo> { it.name != preferredDecoderName }
                .thenBy { if (allowHardwareDecoders) !ApiCompatHelper.isHardwareAccelerated(it) else false }
                .thenBy { it.name },
        )
    }

    internal fun prepareRuntimeFallback(
        decoder: MediaCodec,
        cause: Throwable,
    ): Boolean {
        if (decoderSelectionPinned) return false
        val decoderName = runCatching { decoder.name }.getOrNull() ?: return false
        rejectedDecoderNames += decoderName
        onDecoderRejected?.invoke(decoderName)
        LogManager.w(
            LogTags.AUDIO_DECODER,
            "Audio codec eliminated in this session: $decoderName, reason=${cause.message}",
        )
        return true
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

        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        return format
    }

    private fun applyCodecSpecificData(
        codec: String,
        format: MediaFormat,
        configData: ByteArray?,
    ) {
        if (configData == null || configData.isEmpty()) {
            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "No configuration data, let the decoder handle it automatically" }
            return
        }

        when (codec.lowercase()) {
            "opus" -> {
                val opusConfig = OpusConfigParser.parse(configData)
                if (opusConfig == null) {
                    LogManager.e(
                        LogTags.AUDIO_DECODER,
                        "The Opus configuration package is invalid and the initialization data cannot be set."
                    )
                    return
                }

                val initData = OpusConfigParser.buildInitializationData(opusConfig)
                format.setByteBuffer("csd-0", ByteBuffer.wrap(initData[0]))
                format.setByteBuffer("csd-1", ByteBuffer.wrap(initData[1]))
                format.setByteBuffer("csd-2", ByteBuffer.wrap(initData[2]))
                AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                    "Configured Opus initialization data: csd-0=${initData[0].size} bytes, csd-1=${initData[1].size} bytes, " +
                        "csd-2=${initData[2].size} bytes"
                }
            }

            "flac" -> {
                val streamInfo = FlacConfigParser.parseStreamInfo(configData)
                if (streamInfo == null) {
                    LogManager.e(
                        LogTags.AUDIO_DECODER,
                        "The FLAC configuration package is invalid and the initialization data cannot be set."
                    )
                    return
                }

                val initData = FlacConfigParser.buildInitializationData(streamInfo.rawStreamInfo)
                format.setByteBuffer("csd-0", ByteBuffer.wrap(initData))
                AudioDebugLog.d(LogTags.AUDIO_DECODER) {
                    "Configured FLAC initialization data: rawStreamInfo=${streamInfo.rawStreamInfo.size} bytes, csd-0=${initData.size} bytes"
                }
            }

            else -> {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(configData))
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "Configuration: csd-0=${configData.size} bytes" }
            }
        }
    }

}

internal data class ResolvedAudioFormat(
    val sampleRate: Int,
    val channelCount: Int,
)

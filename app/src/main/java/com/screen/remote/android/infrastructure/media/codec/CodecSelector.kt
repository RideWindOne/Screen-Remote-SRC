package com.screen.remote.android.infrastructure.media.codec

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.datastore.LocalDecoderCache
import com.screen.remote.android.core.domain.model.CodecAcceleration
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.CodecSpec
import com.screen.remote.android.core.domain.model.DecoderCapability
import com.screen.remote.android.core.domain.model.EncoderCapability

data class CodecSelectionResult(
    val encoder: String,
    val decoder: String,
    val codec: String,
    val mimeType: String = "",
)

/**
 * 以远端编码器的结构化格式信息和本地 decoder MIME 能力做交集选择。
 * 实现名称仅在用户手填且没有探测信息时作为最后兜底，自动路径不再猜格式。
 */
object CodecSelector {
    suspend fun selectBestVideoCodec(
        remoteEncoders: List<EncoderCapability>,
        userEncoder: String? = null,
        userDecoder: String? = null,
        preferredCodec: String? = CodecCatalog.DEFAULT_VIDEO_CODEC,
        allowHardwareDecoders: Boolean = true,
    ): CodecSelectionResult? =
        selectBestCodec(
            mediaType = CodecMediaType.VIDEO,
            remoteEncoders = remoteEncoders,
            localDecoders = LocalDecoderCache.getVideoDecoders(),
            userEncoder = userEncoder,
            userDecoder = userDecoder,
            preferredCodec = preferredCodec,
            allowHardwareDecoders = allowHardwareDecoders,
            logTag = LogTags.VIDEO_DECODER,
        )

    suspend fun selectBestAudioCodec(
        remoteEncoders: List<EncoderCapability>,
        userEncoder: String? = null,
        userDecoder: String? = null,
        preferredCodec: String? = CodecCatalog.DEFAULT_AUDIO_CODEC,
        allowHardwareDecoders: Boolean = true,
    ): CodecSelectionResult? =
        selectBestCodec(
            mediaType = CodecMediaType.AUDIO,
            remoteEncoders = remoteEncoders,
            localDecoders = LocalDecoderCache.getAudioDecoders(),
            userEncoder = userEncoder,
            userDecoder = userDecoder,
            preferredCodec = preferredCodec,
            allowHardwareDecoders = allowHardwareDecoders,
            logTag = LogTags.AUDIO_DECODER,
        )

    internal fun selectBestCodec(
        mediaType: CodecMediaType,
        remoteEncoders: List<EncoderCapability>,
        localDecoders: List<DecoderCapability>,
        userEncoder: String?,
        userDecoder: String?,
        preferredCodec: String?,
        logTag: String,
        allowHardwareDecoders: Boolean = true,
    ): CodecSelectionResult? {
        val preferredSpec = preferredCodec?.let { CodecCatalog.find(mediaType, it) }
        val requestedEncoder = userEncoder?.trim().orEmpty()
        val requestedDecoder = userDecoder?.trim().orEmpty()
        if (mediaType == CodecMediaType.AUDIO && preferredSpec?.name == "raw") {
            return CodecSelectionResult(
                encoder = "",
                decoder = "",
                codec = preferredSpec.name,
                mimeType = preferredSpec.mimeType,
            )
        }

        val remote = remoteEncoders.filter { it.mediaType == mediaType }
        val eligibleDecoders =
            if (allowHardwareDecoders) localDecoders else localDecoders.filter { it.acceleration == CodecAcceleration.SOFTWARE }
        if (remote.isEmpty() || eligibleDecoders.isEmpty()) {
            if (mediaType == CodecMediaType.AUDIO && requestedEncoder.isEmpty() && requestedDecoder.isEmpty()) {
                return rawAudioSelection()
            }
            LogManager.w(logTag, "编解码能力不完整: remote=${remote.size}, local=${eligibleDecoders.size}")
            return null
        }

        val orderedSpecs = CodecCatalog.orderedSpecs(mediaType, preferredCodec)
        val fixedDecoder =
            requestedDecoder.takeIf { it.isNotEmpty() }?.let { name ->
                eligibleDecoders.firstOrNull { it.name == name }
            }
        if (requestedDecoder.isNotEmpty() && fixedDecoder == null) {
            LogManager.w(logTag, "用户指定的解码器不存在: $requestedDecoder")
            return null
        }

        val fixedEncoderCapabilities =
            if (requestedEncoder.isEmpty()) {
                emptyList()
            } else {
                remote.filter { it.name == requestedEncoder }
            }

        val encoderFallbackSpec =
            if (requestedEncoder.isNotEmpty() && fixedEncoderCapabilities.isEmpty()) {
                CodecCatalog.inferFromImplementationName(mediaType, requestedEncoder)
            } else {
                null
            }

        for (spec in orderedSpecs) {
            if (spec.name == "raw" && mediaType == CodecMediaType.AUDIO) {
                if (requestedEncoder.isEmpty() && requestedDecoder.isEmpty()) {
                    return rawAudioSelection()
                }
                continue
            }
            if (fixedDecoder != null && !fixedDecoder.supports(spec.mimeType)) continue

            val encoder =
                when {
                    requestedEncoder.isEmpty() -> bestEncoder(remote, spec)
                    fixedEncoderCapabilities.isNotEmpty() -> bestEncoder(fixedEncoderCapabilities, spec)
                    encoderFallbackSpec?.name == spec.name ->
                        EncoderCapability(
                            name = requestedEncoder,
                            codec = spec.name,
                            mimeType = spec.mimeType,
                            mediaType = mediaType,
                        )
                    else -> null
                } ?: continue

            val decoder = fixedDecoder ?: bestDecoder(eligibleDecoders, spec) ?: continue
            val result =
                CodecSelectionResult(
                    encoder = encoder.name,
                    decoder = decoder.name,
                    codec = spec.name,
                    mimeType = spec.mimeType,
                )
            LogManager.i(
                logTag,
                "选择 ${spec.name}: encoder=${result.encoder}(${encoder.acceleration}), " +
                    "decoder=${result.decoder}(${decoder.acceleration})",
            )
            return result
        }

        LogManager.w(
            logTag,
            "未找到匹配组合: preferred=$preferredCodec encoder=${requestedEncoder.ifBlank { "auto" }} " +
                "decoder=${requestedDecoder.ifBlank { "auto" }}",
        )
        return null
    }

    private fun rawAudioSelection(): CodecSelectionResult {
        val raw = requireNotNull(CodecCatalog.find(CodecMediaType.AUDIO, "raw"))
        return CodecSelectionResult(
            encoder = "",
            decoder = "",
            codec = raw.name,
            mimeType = raw.mimeType,
        )
    }

    fun inferVideoCodecFromName(value: String): String = inferCodec(CodecMediaType.VIDEO, value)

    fun inferAudioCodecFromName(value: String): String = inferCodec(CodecMediaType.AUDIO, value)

    private fun inferCodec(
        mediaType: CodecMediaType,
        value: String,
    ): String =
        CodecCatalog.find(mediaType, value)?.name
            ?: CodecCatalog.inferFromImplementationName(mediaType, value)?.name
            .orEmpty()

    private fun bestEncoder(
        encoders: List<EncoderCapability>,
        spec: CodecSpec,
    ): EncoderCapability? =
        encoders
            .asSequence()
            .filter { it.codec == spec.name && it.mimeType.equals(spec.mimeType, ignoreCase = true) }
            .minWithOrNull(compareBy(::encoderRank, { it.name }))

    private fun bestDecoder(
        decoders: List<DecoderCapability>,
        spec: CodecSpec,
    ): DecoderCapability? =
        decoders
            .asSequence()
            .filter { it.supports(spec.mimeType) }
            .minWithOrNull(compareBy({ decoderRank(it, spec) }, { it.name }))

    private fun encoderRank(capability: EncoderCapability): Int =
        aliasPenalty(capability.isAlias) +
            when (capability.acceleration) {
                CodecAcceleration.HARDWARE -> 0
                CodecAcceleration.HYBRID -> 20
                CodecAcceleration.UNKNOWN -> 40
                CodecAcceleration.SOFTWARE -> 60
            }

    private fun decoderRank(
        capability: DecoderCapability,
        spec: CodecSpec,
    ): Int {
        val accelerationRank =
            when (capability.acceleration) {
                CodecAcceleration.HARDWARE -> 0
                CodecAcceleration.HYBRID -> 20
                CodecAcceleration.UNKNOWN -> 40
                CodecAcceleration.SOFTWARE -> 60
            }
        val lowLatencyBonus =
            if (spec.mediaType == CodecMediaType.VIDEO && capability.supportsLowLatency(spec.mimeType)) -10 else 0
        val softwareStabilityRank =
            when {
                capability.name.startsWith("OMX.google", ignoreCase = true) -> 0
                capability.name.startsWith("c2.android", ignoreCase = true) -> 4
                else -> 2
            }
        return aliasPenalty(capability.isAlias) + accelerationRank + lowLatencyBonus + softwareStabilityRank
    }

    private fun aliasPenalty(isAlias: Boolean): Int = if (isAlias) 100 else 0
}

package com.screen.remote.android.feature.codec.util

import android.media.MediaCodecList
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability

/**
 * 编解码器工具类
 */
object CodecUtils {
    enum class CodecType {
        VIDEO,
        AUDIO
    }

    /**
     * Resolve the negotiated MIME from structured remote capabilities. Implementation-name
     * inference is deliberately only a fallback for a manually entered encoder.
     */
    fun resolveEncoderMimeType(
        encoders: List<EncoderCapability>,
        encoderName: String,
        preferredCodec: String,
        type: CodecType,
    ): String? {
        val mediaType = type.toMediaType()
        val preferredSpec = CodecCatalog.find(mediaType, preferredCodec)
        val namedEncoders = encoders.filter { it.mediaType == mediaType && it.name == encoderName }
        if (namedEncoders.isNotEmpty()) {
            return namedEncoders.firstOrNull { it.codec == preferredSpec?.name }?.mimeType
                ?: namedEncoders.singleOrNull()?.mimeType
        }
        return preferredSpec?.mimeType
            ?: CodecCatalog.inferFromImplementationName(mediaType, encoderName)?.mimeType
    }

    /** Match by the decoder's advertised MediaCodec MIME types, never by its implementation name. */
    fun isDecoderCompatible(
        decoderName: String,
        mimeType: String?,
    ): Boolean {
        if (decoderName.isBlank() || mimeType.isNullOrBlank() || mimeType.equals("audio/raw", ignoreCase = true)) {
            return true
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .firstOrNull { !it.isEncoder && it.name == decoderName }
                ?.supportedTypes
                ?.any { it.equals(mimeType, ignoreCase = true) }
                ?: false
        }.getOrDefault(false)
    }

    private fun CodecType.toMediaType(): CodecMediaType =
        when (this) {
            CodecType.VIDEO -> CodecMediaType.VIDEO
            CodecType.AUDIO -> CodecMediaType.AUDIO
        }

}

package com.screen.remote.android.feature.codec.util

import android.media.MediaCodecList
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability

object CodecUtils {
    /**
     * 用户只选择了一侧时，另一侧可在连接阶段自动补齐，因此无需阻止保存。
     * 两侧都指定时，直接校验远端 encoder 与本地 decoder 的 MIME 交集。
     */
    fun isEncoderDecoderCompatible(
        encoders: List<EncoderCapability>,
        encoderName: String,
        decoderName: String,
        mediaType: CodecMediaType,
    ): Boolean {
        if (encoderName.isBlank() || decoderName.isBlank()) return true

        val decoderMimeTypes =
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS)
                    .codecInfos
                    .firstOrNull { !it.isEncoder && it.name == decoderName }
                    ?.supportedTypes
                    ?.toList()
            }.getOrNull() ?: return false

        return hasCompatibleMimeType(
            encoders = encoders,
            encoderName = encoderName,
            decoderMimeTypes = decoderMimeTypes,
            mediaType = mediaType,
        )
    }

    internal fun hasCompatibleMimeType(
        encoders: List<EncoderCapability>,
        encoderName: String,
        decoderMimeTypes: List<String>,
        mediaType: CodecMediaType,
    ): Boolean {
        val encoderMimeTypes =
            encoders
                .asSequence()
                .filter { it.mediaType == mediaType && it.name == encoderName }
                .map(EncoderCapability::mimeType)
                .toSet()
                .ifEmpty {
                    CodecCatalog
                        .inferFromImplementationName(mediaType, encoderName)
                        ?.mimeType
                        ?.let(::setOf)
                        .orEmpty()
                }

        // 自定义实现名无法可靠推断格式，交给连接阶段的能力检测与自动回退处理。
        if (encoderMimeTypes.isEmpty()) return true
        return encoderMimeTypes.any { encoderMime ->
            decoderMimeTypes.any { it.equals(encoderMime, ignoreCase = true) }
        }
    }
}

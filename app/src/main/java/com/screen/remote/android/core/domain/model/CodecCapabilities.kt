package com.screen.remote.android.core.domain.model

import kotlinx.serialization.Serializable

/**
 * scrcpy 与 Android MediaCodec 之间共享的结构化编解码能力模型。
 *
 * 编解码器实现名称并不保证包含格式名，因此格式匹配必须以 [codec] / [mimeType]
 * 为准，名称推断只允许用于用户手填的实现名称。
 */
@Serializable
enum class CodecMediaType {
    VIDEO,
    AUDIO,
}

@Serializable
enum class CodecAcceleration {
    HARDWARE,
    SOFTWARE,
    HYBRID,
    UNKNOWN,
}

@Serializable
data class EncoderCapability(
    val name: String,
    val codec: String,
    val mimeType: String,
    val mediaType: CodecMediaType,
    val acceleration: CodecAcceleration = CodecAcceleration.UNKNOWN,
    val isVendor: Boolean = false,
    val aliasOf: String? = null,
) {
    val isAlias: Boolean
        get() = !aliasOf.isNullOrBlank()
}

@Serializable
data class DecoderCapability(
    val name: String,
    val mimeTypes: List<String>,
    val acceleration: CodecAcceleration = CodecAcceleration.UNKNOWN,
    val isVendor: Boolean = false,
    val aliasOf: String? = null,
    val lowLatencyMimeTypes: List<String> = emptyList(),
) {
    fun supports(mimeType: String): Boolean = mimeTypes.any { it.equals(mimeType, ignoreCase = true) }

    fun supportsLowLatency(mimeType: String): Boolean =
        lowLatencyMimeTypes.any { it.equals(mimeType, ignoreCase = true) }

    val isAlias: Boolean
        get() = !aliasOf.isNullOrBlank()
}

data class CodecSpec(
    val name: String,
    val mimeType: String,
    val mediaType: CodecMediaType,
    val aliases: Set<String> = emptySet(),
)

/** scrcpy 4.1 当前可通过媒体 socket 协商的完整格式集合。 */
object CodecCatalog {
    const val DEFAULT_VIDEO_CODEC = "h264"
    const val DEFAULT_AUDIO_CODEC = "opus"

    val videoSpecs: List<CodecSpec> =
        listOf(
            CodecSpec("h264", "video/avc", CodecMediaType.VIDEO, setOf("avc")),
            CodecSpec("h265", "video/hevc", CodecMediaType.VIDEO, setOf("hevc")),
            CodecSpec("av1", "video/av01", CodecMediaType.VIDEO, setOf("av01")),
            CodecSpec("vp9", "video/x-vnd.on2.vp9", CodecMediaType.VIDEO),
            CodecSpec("vp8", "video/x-vnd.on2.vp8", CodecMediaType.VIDEO),
        )

    val audioSpecs: List<CodecSpec> =
        listOf(
            CodecSpec("opus", "audio/opus", CodecMediaType.AUDIO),
            CodecSpec("aac", "audio/mp4a-latm", CodecMediaType.AUDIO, setOf("mp4a")),
            CodecSpec("flac", "audio/flac", CodecMediaType.AUDIO),
            CodecSpec("raw", "audio/raw", CodecMediaType.AUDIO, setOf("pcm")),
        )

    val allSpecs: List<CodecSpec> = videoSpecs + audioSpecs

    fun find(
        mediaType: CodecMediaType,
        value: String,
    ): CodecSpec? {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return null
        return specs(mediaType).firstOrNull { spec ->
            normalized == spec.name ||
                normalized == spec.mimeType.lowercase() ||
                normalized in spec.aliases
        }
    }

    fun findByMimeType(
        mediaType: CodecMediaType,
        mimeType: String,
    ): CodecSpec? = specs(mediaType).firstOrNull { it.mimeType.equals(mimeType, ignoreCase = true) }

    fun mimeType(
        mediaType: CodecMediaType,
        codec: String,
    ): String? = find(mediaType, codec)?.mimeType

    fun normalizedName(
        mediaType: CodecMediaType,
        codec: String,
    ): String? = find(mediaType, codec)?.name

    fun orderedSpecs(mediaType: CodecMediaType): List<CodecSpec> = specs(mediaType)

    /**
     * 仅供用户手填实现名时兜底。自动探测和运行时主路径必须使用结构化能力。
     */
    fun inferFromImplementationName(
        mediaType: CodecMediaType,
        implementationName: String,
    ): CodecSpec? {
        val lower = implementationName.lowercase()
        return specs(mediaType).firstOrNull { spec ->
            val tokens = setOf(spec.name) + spec.aliases
            tokens.any { token -> lower.contains(token) }
        }
    }

    private fun specs(mediaType: CodecMediaType): List<CodecSpec> =
        when (mediaType) {
            CodecMediaType.VIDEO -> videoSpecs
            CodecMediaType.AUDIO -> audioSpecs
        }
}

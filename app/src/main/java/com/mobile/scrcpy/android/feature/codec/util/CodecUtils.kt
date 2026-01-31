package com.mobile.scrcpy.android.feature.codec.util

/**
 * 编解码器工具类
 */
object CodecUtils {
    enum class CodecType {
        VIDEO,
        AUDIO
    }

    /**
     * 检查两个编解码器是否协议匹配
     * 通过检查编解码器名称中是否包含相同的协议关键字
     * @param type 编解码器类型（VIDEO/AUDIO），用于限定协议范围
     */
    fun isCodecProtocolMatch(
        codec1: String,
        codec2: String,
        type: CodecType,
    ): Boolean {
        if (codec1.isBlank() || codec2.isBlank()) return true // 空值不校验

        val lower1 = codec1.lowercase()
        val lower2 = codec2.lowercase()

        val protocols = when (type) {
            CodecType.AUDIO -> listOf(
                "opus",
                "aac",
                "mp4a",
                "flac",
                "vorbis",
                "amr",
                "3gpp",
                "raw",
                "pcm",
            )
            CodecType.VIDEO -> listOf(
                "avc",
                "h264",
                "h.264",
                "hevc",
                "h265",
                "h.265",
                "av01",
                "av1",
                "vp8",
                "vp9",
                "mpeg4",
                "h263",
                "h.263",
            )
        }

        // 检查是否有共同的协议关键字
        for (protocol in protocols) {
            if (lower1.contains(protocol) && lower2.contains(protocol)) {
                return true
            }
        }

        return false
    }

    /**
     * 从编解码器名称中提取协议类型（用于显示等场景）
     */
    fun extractCodecProtocol(codecName: String): String? {
        if (codecName.isBlank()) return null

        val lowerName = codecName.lowercase()

        return when {
            // 音频协议
            lowerName.contains("opus") -> "opus"

            lowerName.contains("aac") || lowerName.contains("mp4a") -> "aac"

            lowerName.contains("flac") -> "flac"

            lowerName.contains("vorbis") -> "vorbis"

            lowerName.contains("amr") || lowerName.contains("3gpp") -> "amr"

            lowerName.contains("raw") || lowerName.contains("pcm") -> "raw"

            // 视频协议
            lowerName.contains("avc") || lowerName.contains("h264") || lowerName.contains("h.264") -> "h264"

            lowerName.contains("hevc") || lowerName.contains("h265") || lowerName.contains("h.265") -> "h265"

            lowerName.contains("av01") || lowerName.contains("av1") -> "av1"

            lowerName.contains("vp8") -> "vp8"

            lowerName.contains("vp9") -> "vp9"

            lowerName.contains("mpeg4") -> "mpeg4"

            lowerName.contains("h263") || lowerName.contains("h.263") -> "h263"

            else -> null
        }
    }
}

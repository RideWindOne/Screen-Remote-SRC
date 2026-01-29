package com.mobile.scrcpy.android.feature.codec.component

/**
 * 编码器和编码格式映射工具
 */
object CodecMapper {
    /**
     * 根据编码器名称或 MIME 类型推断编码格式
     */
    fun getCodecFromEncoder(encoderNameOrMime: String): String? {
        val lower = encoderNameOrMime.lowercase()
        return when {
            // 视频编码器
            lower.contains("avc") || lower.contains("h264") || lower.contains("264") -> "h264"

            lower.contains("hevc") || lower.contains("h265") || lower.contains("265") -> "h265"

            lower.contains("av1") || lower.contains("av01") -> "av1"

            lower.contains("vp8") -> "vp8"

            lower.contains("vp9") -> "vp9"

            // 音频编码器
            lower.contains("opus") -> "opus"

            lower.contains("aac") || lower.contains("mp4a") -> "aac"

            lower.contains("flac") -> "flac"

            lower.contains("raw") || lower.contains("pcm") -> "raw"

            else -> null
        }
    }

    /**
     * 检查编码器是否匹配指定的编码格式
     */
    fun isEncoderMatchCodec(
        encoderName: String,
        codec: String,
    ): Boolean {
        if (encoderName.isEmpty()) return true // 空编码器（默认）总是匹配
        val detectedCodec = getCodecFromEncoder(encoderName)
        return detectedCodec == codec.lowercase()
    }

    /**
     * 将编码格式转换为大写显示
     */
    fun toDisplayFormat(codec: String): String = codec.uppercase()
}

package com.screen.remote.android.feature.codec.model

import com.screen.remote.android.core.domain.model.CodecAcceleration

/**
 * 编解码器信息（统一音频和视频）
 */
data class CodecInfo(
    val name: String,
    val type: String,
    val isEncoder: Boolean,
    val capabilities: String,
    val acceleration: CodecAcceleration = CodecAcceleration.UNKNOWN,
    val mimeTypes: List<String> = emptyList(),
    val lowLatencyMimeTypes: List<String> = emptyList(),
)

/**
 * 编解码器过滤类型
 */
enum class CodecFilterType {
    ALL,
    DECODER,
    ENCODER,
}

/**
 * 音频编解码器类型过滤
 */
enum class AudioCodecTypeFilter {
    ALL,
    OPUS,
    AAC,
    FLAC,
    RAW,
}

/**
 * 视频编解码器类型过滤
 */
enum class VideoCodecTypeFilter {
    ALL,
    H264,
    H265,
    VP8,
    VP9,
    AV1,
}

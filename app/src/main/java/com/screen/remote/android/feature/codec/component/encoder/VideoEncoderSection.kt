package com.screen.remote.android.feature.codec.component.encoder

import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.codec.component.EncoderDialogConfig
import com.screen.remote.android.core.domain.model.EncoderCapability

/**
 * 视频编码器配置
 * 
 * 提取自 EncoderSelectionDialog.kt
 * 负责视频编码器的配置和筛选逻辑
 */

/**
 * 获取视频编码器对话框配置
 */
fun getVideoEncoderDialogConfig(detectedEncoders: List<EncoderCapability>): EncoderDialogConfig {
    // 动态提取视频编码器类型
    val types = mutableSetOf<String>()
    detectedEncoders.forEach { encoder ->
        when {
            encoder.mimeType.contains("avc", ignoreCase = true) -> types.add("H264")

            encoder.mimeType.contains("hevc", ignoreCase = true) -> types.add("H265")

            encoder.mimeType.contains("av01", ignoreCase = true) ||
                encoder.mimeType.contains("av1", ignoreCase = true) -> types.add("AV1")

            encoder.mimeType.contains("vp8", ignoreCase = true) -> types.add("VP8")

            encoder.mimeType.contains("vp9", ignoreCase = true) -> types.add("VP9")

        }
    }

    return EncoderDialogConfig(
        title = SessionTexts.DIALOG_SELECT_VIDEO_ENCODER.get(),
        sectionTitle = SessionTexts.SECTION_DETECTED_ENCODERS.get(),
        detectingStatus = SessionTexts.STATUS_DETECTING_VIDEO_ENCODERS.get(),
        noEncodersStatus = SessionTexts.STATUS_NO_ENCODERS_DETECTED.get(),
        filterOptions = listOf(CommonTexts.FILTER_ALL.get()) + types.sorted(),
        showCodecTest = false,
    )
}

/**
 * 检查视频编码器是否匹配筛选条件
 */
fun matchesVideoCodecFilter(
    mimeType: String,
    filter: String,
    allFilterOption: String,
): Boolean {
    if (filter == allFilterOption) return true

    return when (filter) {
        "H264" -> mimeType.contains("avc", ignoreCase = true)
        "H265" -> mimeType.contains("hevc", ignoreCase = true)
        "AV1" -> mimeType.contains("av01", ignoreCase = true) || mimeType.contains("av1", ignoreCase = true)
        "VP8" -> mimeType.contains("vp8", ignoreCase = true)
        "VP9" -> mimeType.contains("vp9", ignoreCase = true)
        else -> mimeType.contains(filter, ignoreCase = true)
    }
}

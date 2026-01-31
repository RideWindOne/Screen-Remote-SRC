package com.mobile.scrcpy.android.feature.codec.component.encoder

import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.EncoderDialogConfig
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderInfo

/**
 * 视频编码器配置
 * 
 * 提取自 EncoderSelectionDialog.kt
 * 负责视频编码器的配置和筛选逻辑
 */

/**
 * 获取视频编码器对话框配置
 */
fun getVideoEncoderDialogConfig(detectedEncoders: List<EncoderInfo>): EncoderDialogConfig {
    // 动态提取视频编码器类型
    val types = mutableSetOf<String>()
    detectedEncoders.forEach { encoder ->
        when {
            encoder.mimeType.contains("avc", ignoreCase = true) -> types.add("H.264")

            encoder.mimeType.contains("hevc", ignoreCase = true) -> types.add("H.265")

            encoder.mimeType.contains("av01", ignoreCase = true) ||
                encoder.mimeType.contains("av1", ignoreCase = true) -> types.add("AV1")

            encoder.mimeType.contains("vp8", ignoreCase = true) -> types.add("VP8")

            encoder.mimeType.contains("vp9", ignoreCase = true) -> types.add("VP9")

            encoder.mimeType.contains("mpeg4", ignoreCase = true) -> types.add("MPEG4")

            encoder.mimeType.contains("h263", ignoreCase = true) ||
                encoder.mimeType.contains("3gpp", ignoreCase = true) -> types.add("H.263")
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
        "H.264" -> mimeType.contains("avc", ignoreCase = true)
        "H.265" -> mimeType.contains("hevc", ignoreCase = true)
        "AV1" -> mimeType.contains("av01", ignoreCase = true) || mimeType.contains("av1", ignoreCase = true)
        "VP8" -> mimeType.contains("vp8", ignoreCase = true)
        "VP9" -> mimeType.contains("vp9", ignoreCase = true)
        "MPEG4" -> mimeType.contains("mpeg4", ignoreCase = true)
        "H.263" -> mimeType.contains("h263", ignoreCase = true) || mimeType.contains("3gpp", ignoreCase = true)
        else -> mimeType.contains(filter, ignoreCase = true)
    }
}

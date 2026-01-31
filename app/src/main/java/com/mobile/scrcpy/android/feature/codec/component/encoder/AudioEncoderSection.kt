package com.mobile.scrcpy.android.feature.codec.component.encoder

import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.codec.component.EncoderDialogConfig
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderInfo

/**
 * 音频编码器配置
 * 
 * 提取自 EncoderSelectionDialog.kt
 * 负责音频编码器的配置和筛选逻辑
 */

/**
 * 获取音频编码器对话框配置
 */
fun getAudioEncoderDialogConfig(detectedEncoders: List<EncoderInfo>): EncoderDialogConfig {
    // 动态提取音频编码器类型
    val types = mutableSetOf<String>()
    detectedEncoders.forEach { encoder ->
        when {
            encoder.mimeType.contains("opus", ignoreCase = true) -> types.add("OPUS")

            encoder.mimeType.contains("aac", ignoreCase = true) ||
                encoder.mimeType.contains("mp4a", ignoreCase = true) -> types.add("AAC")

            encoder.mimeType.contains("flac", ignoreCase = true) -> types.add("FLAC")

            encoder.mimeType.contains("vorbis", ignoreCase = true) -> types.add("Vorbis")

            encoder.mimeType.contains("amr", ignoreCase = true) ||
                encoder.mimeType.contains("3gpp", ignoreCase = true) -> types.add("AMR")

            encoder.mimeType.contains("raw", ignoreCase = true) -> types.add("RAW")
        }
    }

    return EncoderDialogConfig(
        title = SessionTexts.DIALOG_SELECT_AUDIO_ENCODER.get(),
        sectionTitle = SessionTexts.SECTION_DETECTED_AUDIO_ENCODERS.get(),
        detectingStatus = SessionTexts.STATUS_DETECTING_AUDIO_ENCODERS.get(),
        noEncodersStatus = SessionTexts.STATUS_NO_AUDIO_ENCODERS_DETECTED.get(),
        filterOptions = listOf(CommonTexts.FILTER_ALL.get()) + types.sorted(),
        showCodecTest = true,
    )
}

/**
 * 检查音频编码器是否匹配筛选条件
 */
fun matchesAudioCodecFilter(
    mimeType: String,
    filter: String,
    allFilterOption: String,
): Boolean {
    if (filter == allFilterOption) return true

    return when (filter) {
        "AAC" -> mimeType.contains("aac", ignoreCase = true) || mimeType.contains("mp4a-latm", ignoreCase = true)
        "OPUS" -> mimeType.contains("opus", ignoreCase = true)
        "FLAC" -> mimeType.contains("flac", ignoreCase = true)
        "Vorbis" -> mimeType.contains("vorbis", ignoreCase = true)
        "AMR" -> mimeType.contains("amr", ignoreCase = true) || mimeType.contains("3gpp", ignoreCase = true)
        "RAW" -> mimeType.contains("raw", ignoreCase = true)
        else -> mimeType.contains(filter, ignoreCase = true)
    }
}

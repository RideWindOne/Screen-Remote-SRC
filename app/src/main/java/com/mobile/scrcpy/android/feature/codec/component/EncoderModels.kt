package com.mobile.scrcpy.android.feature.codec.component

import com.mobile.scrcpy.android.core.i18n.SessionTexts
/**
 * 编码器信息接口
 * 用于统一视频和音频编码器的数据结构
 */
interface EncoderInfo {
    val name: String
    val mimeType: String
}

/**
 * 编码器类型
 */
enum class EncoderType {
    VIDEO,  // 视频编码器
    AUDIO   // 音频编码器
}

/**
 * 编码器对话框配置
 */
data class EncoderDialogConfig(
    val title: String,                      // 对话框标题
    val sectionTitle: String,               // 检测到的编码器区域标题
    val detectingStatus: String,            // 检测中状态文本
    val noEncodersStatus: String,           // 未检测到编码器状态文本
    val filterOptions: List<String>,        // 筛选选项列表
    val showCodecTest: Boolean              // 是否显示编解码器测试按钮
)

/**
 * 根据编码器类型获取对话框配置
 */
fun getEncoderDialogConfig(encoderType: EncoderType): EncoderDialogConfig {
    return when (encoderType) {
        EncoderType.VIDEO -> EncoderDialogConfig(
            title = SessionTexts.DIALOG_SELECT_VIDEO_ENCODER.get(),
            sectionTitle = SessionTexts.SECTION_DETECTED_ENCODERS.get(),
            detectingStatus = SessionTexts.STATUS_DETECTING_VIDEO_ENCODERS.get(),
            noEncodersStatus = SessionTexts.STATUS_NO_ENCODERS_DETECTED.get(),
            filterOptions = listOf(
                SessionTexts.ENCODER_FILTER_ALL.get(),
                "H.264",
                "H.265",
                "AV1"
            ),
            showCodecTest = false
        )
        EncoderType.AUDIO -> EncoderDialogConfig(
            title = SessionTexts.DIALOG_SELECT_AUDIO_ENCODER.get(),
            sectionTitle = SessionTexts.SECTION_DETECTED_AUDIO_ENCODERS.get(),
            detectingStatus = SessionTexts.STATUS_DETECTING_AUDIO_ENCODERS.get(),
            noEncodersStatus = SessionTexts.STATUS_NO_AUDIO_ENCODERS_DETECTED.get(),
            filterOptions = listOf(
                SessionTexts.ENCODER_FILTER_ALL.get(),
                "AAC",
                "Opus",
                "FLAC"
            ),
            showCodecTest = true
        )
    }
}

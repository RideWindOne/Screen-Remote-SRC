package com.mobile.scrcpy.android.feature.codec.component

import com.mobile.scrcpy.android.feature.codec.component.encoder.getAudioEncoderDialogConfig
import com.mobile.scrcpy.android.feature.codec.component.encoder.getVideoEncoderDialogConfig
import com.mobile.scrcpy.android.feature.codec.component.encoder.matchesAudioCodecFilter
import com.mobile.scrcpy.android.feature.codec.component.encoder.matchesVideoCodecFilter
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderInfo

/**
 * 编码器相关数据模型
 * 
 * 文件拆分说明：
 * - encoder/VideoEncoderSection.kt - 视频编码器配置逻辑
 * - encoder/AudioEncoderSection.kt - 音频编码器配置逻辑
 */

/**
 * 编码器类型
 */
enum class EncoderType {
    VIDEO, // 视频编码器
    AUDIO, // 音频编码器
}

/**
 * 编码器对话框配置
 */
data class EncoderDialogConfig(
    val title: String, // 对话框标题
    val sectionTitle: String, // 检测到的编码器区域标题
    val detectingStatus: String, // 检测中状态文本
    val noEncodersStatus: String, // 未检测到编码器状态文本
    val filterOptions: List<String>, // 筛选选项列表
    val showCodecTest: Boolean, // 是否显示编解码器测试按钮
)

/**
 * 根据编码器类型获取对话框配置
 */
fun getEncoderDialogConfig(
    encoderType: EncoderType,
    detectedEncoders: List<EncoderInfo>,
): EncoderDialogConfig =
    when (encoderType) {
        EncoderType.VIDEO -> getVideoEncoderDialogConfig(detectedEncoders)
        EncoderType.AUDIO -> getAudioEncoderDialogConfig(detectedEncoders)
    }

/**
 * 检查编码器是否匹配筛选条件
 */
fun matchesCodecFilter(
    mimeType: String,
    filter: String,
    allFilterOption: String,
): Boolean =
    matchesVideoCodecFilter(mimeType, filter, allFilterOption) ||
        matchesAudioCodecFilter(mimeType, filter, allFilterOption)


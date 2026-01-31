package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.Session

/**
 * 会话配置管理 - 内部实现
 *
 * 职责：
 * - 配置更新和持久化
 * - 设备序列号变化处理
 * - 编解码器配置保存
 *
 * 使用扩展函数模式，供 Session 类调用
 */

/**
 * 更新配置（自动保存）
 */
internal suspend fun Session.updateOptions(update: (ScrcpyOptions) -> ScrcpyOptions) {
    val updated = update(options)
    setOptions(updated)
    getStorage().saveOptions(updated)
    LogManager.d(LogTags.SCRCPY_CLIENT, "更新配置: sessionId=$sessionId")
}

/**
 * 更新设备序列号（如果序列号变化，清空设备能力）
 */
internal suspend fun Session.updateDeviceSerial(newSerial: String) {
    val current = options

    // 序列号相同，无需更新
    if (current.deviceSerial == newSerial) {
        return
    }

    // 如果当前序列号为空，只更新序列号，不清空设备能力（首次连接场景）
    if (current.deviceSerial.isBlank()) {
        LogManager.i(
            LogTags.SCRCPY_CLIENT,
            "首次设置设备序列号: $newSerial，保留已有设备能力",
        )
        updateOptions {
            it.copy(deviceSerial = newSerial)
        }
        return
    }

    // 序列号不同（且当前不为空），更新并清空设备能力（设备切换场景）
    LogManager.i(
        LogTags.SCRCPY_CLIENT,
        "设备序列号变化: ${current.deviceSerial} -> $newSerial，清空设备能力",
    )

    updateOptions {
        it.copy(
            deviceSerial = newSerial,
            remoteVideoEncoders = emptyList(),
            remoteAudioEncoders = emptyList(),
            selectedVideoEncoder = "",
            selectedAudioEncoder = "",
            selectedVideoDecoder = "",
            selectedAudioDecoder = "",
            preferredVideoCodec = "",
            preferredAudioCodec = "",
        )
    }
}

/**
 * 保存编解码器检测结果
 */
internal suspend fun Session.saveCodecDetectionResult(
    deviceSerial: String,
    remoteVideoEncoders: List<String>,
    remoteAudioEncoders: List<String>,
    selectedVideoEncoder: String,
    selectedAudioEncoder: String,
    selectedVideoDecoder: String,
    selectedAudioDecoder: String,
    preferredVideoCodec: String,
    preferredAudioCodec: String,
) {
    updateOptions {
        it.copy(
            deviceSerial = deviceSerial,
            remoteVideoEncoders = remoteVideoEncoders,
            remoteAudioEncoders = remoteAudioEncoders,
            selectedVideoEncoder = selectedVideoEncoder,
            selectedAudioEncoder = selectedAudioEncoder,
            selectedVideoDecoder = selectedVideoDecoder,
            selectedAudioDecoder = selectedAudioDecoder,
            preferredVideoCodec = preferredVideoCodec,
            preferredAudioCodec = preferredAudioCodec,
        )
    }
}

/**
 * 保存编解码器选择（UI 手动选择时调用）
 */
internal suspend fun Session.saveCodecSelection(
    videoEncoder: String,
    audioEncoder: String,
    videoDecoder: String,
    audioDecoder: String,
    preferredVideoCodec: String,
    preferredAudioCodec: String,
) {
    updateOptions {
        it.copy(
            selectedVideoEncoder = videoEncoder,
            selectedAudioEncoder = audioEncoder,
            selectedVideoDecoder = videoDecoder,
            selectedAudioDecoder = audioDecoder,
            preferredVideoCodec = preferredVideoCodec,
            preferredAudioCodec = preferredAudioCodec,
        )
    }
}

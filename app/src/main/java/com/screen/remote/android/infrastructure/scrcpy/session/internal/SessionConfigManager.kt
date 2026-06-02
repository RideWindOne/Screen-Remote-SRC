package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.scrcpy.session.Session

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
    updateOptionsInMemory(update)
    storage().updateOptions(sessionId, update)
    LogManager.d(LogTags.SCRCPY_CLIENT, "更新配置: sessionId=$sessionId")
}

/**
 * 保存远程检测到的编码器列表
 */
internal suspend fun Session.saveDiscoveredEncoders(
    remoteVideoEncoders: List<EncoderCapability>,
    remoteAudioEncoders: List<EncoderCapability>,
) {
    updateOptions {
        it.copy(
            remoteVideoEncoders = remoteVideoEncoders,
            remoteAudioEncoders = remoteAudioEncoders,
        )
    }
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

    // 身份字段为空时也不能信任预置能力；能力必须和本次真实设备指纹绑定。
    if (current.deviceSerial.isBlank()) {
        LogManager.i(
            LogTags.SCRCPY_CLIENT,
            "首次设置设备能力签名，清空未验证的能力缓存",
        )
        updateOptions {
            it.copy(
                deviceSerial = newSerial,
                remoteVideoEncoders = emptyList(),
                remoteAudioEncoders = emptyList(),
                selectedVideoCodec = "",
                selectedAudioCodec = "",
                selectedVideoEncoder = "",
                selectedAudioEncoder = "",
                selectedVideoDecoder = "",
                selectedAudioDecoder = "",
            )
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
            selectedVideoCodec = "",
            selectedAudioCodec = "",
            selectedVideoEncoder = "",
            selectedAudioEncoder = "",
            selectedVideoDecoder = "",
            selectedAudioDecoder = "",
        )
    }
}

/**
 * 保存编解码器检测结果
 */
internal fun Session.saveCodecDetectionResult(
    deviceSerial: String,
    remoteVideoEncoders: List<EncoderCapability>,
    remoteAudioEncoders: List<EncoderCapability>,
    selectedVideoCodec: String,
    selectedAudioCodec: String,
    selectedVideoEncoder: String,
    selectedAudioEncoder: String,
    selectedVideoDecoder: String,
    selectedAudioDecoder: String,
    preferredVideoCodec: String,
    preferredAudioCodec: String,
) {
    val applyDetectionResult: (ScrcpyOptions) -> ScrcpyOptions =
        { current ->
            if (current.deviceSerial != deviceSerial) {
                current
            } else {
                current.copy(
                    deviceSerial = deviceSerial,
                    remoteVideoEncoders = remoteVideoEncoders,
                    remoteAudioEncoders = remoteAudioEncoders,
                    selectedVideoCodec = selectedVideoCodec,
                    selectedAudioCodec = selectedAudioCodec,
                    selectedVideoEncoder = selectedVideoEncoder,
                    selectedAudioEncoder = selectedAudioEncoder,
                    selectedVideoDecoder = selectedVideoDecoder,
                    selectedAudioDecoder = selectedAudioDecoder,
                    preferredVideoCodec = preferredVideoCodec,
                    preferredAudioCodec = preferredAudioCodec,
                )
            }
        }

    updateOptionsInMemory(applyDetectionResult)
    persistOptionsInBackground(applyDetectionResult)
    LogManager.d(LogTags.SCRCPY_CLIENT, "编解码器检测结果已更新到内存，后台保存: sessionId=$sessionId")
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
            selectedVideoCodec = preferredVideoCodec,
            selectedAudioCodec = preferredAudioCodec,
            selectedVideoEncoder = videoEncoder,
            selectedAudioEncoder = audioEncoder,
            selectedVideoDecoder = videoDecoder,
            selectedAudioDecoder = audioDecoder,
            preferredVideoCodec = preferredVideoCodec,
            preferredAudioCodec = preferredAudioCodec,
        )
    }
}

internal fun Session.rememberResolvedVideoDecoder(
    decoderName: String,
    expectedDeviceSerial: String,
    expectedCodec: String,
) {
    val current = options
    if (decoderName.isBlank() || current.userVideoDecoder.isNotBlank()) return
    if (current.deviceSerial != expectedDeviceSerial || current.resolvedVideoCodec() != expectedCodec) return
    val update: (ScrcpyOptions) -> ScrcpyOptions = {
        if (it.deviceSerial == expectedDeviceSerial && it.resolvedVideoCodec() == expectedCodec) {
            it.copy(selectedVideoDecoder = decoderName)
        } else {
            it
        }
    }
    updateOptionsInMemory(update)
    persistOptionsInBackground(update)
}

internal fun Session.rememberResolvedAudioDecoder(
    decoderName: String,
    expectedDeviceSerial: String,
    expectedCodec: String,
) {
    val current = options
    if (decoderName.isBlank() || current.userAudioDecoder.isNotBlank()) return
    if (current.deviceSerial != expectedDeviceSerial || current.resolvedAudioCodec() != expectedCodec) return
    val update: (ScrcpyOptions) -> ScrcpyOptions = {
        if (it.deviceSerial == expectedDeviceSerial && it.resolvedAudioCodec() == expectedCodec) {
            it.copy(selectedAudioDecoder = decoderName)
        } else {
            it
        }
    }
    updateOptionsInMemory(update)
    persistOptionsInBackground(update)
}

internal fun Session.rememberNegotiatedVideoCodec(codec: String) {
    rememberNegotiatedCodec(
        codec = codec,
        mediaType = CodecMediaType.VIDEO,
        updateCodec = { options, normalized -> options.copy(selectedVideoCodec = normalized) },
    )
}

internal fun Session.rememberNegotiatedAudioCodec(codec: String) {
    rememberNegotiatedCodec(
        codec = codec,
        mediaType = CodecMediaType.AUDIO,
        updateCodec = { options, normalized -> options.copy(selectedAudioCodec = normalized) },
    )
}

private fun Session.rememberNegotiatedCodec(
    codec: String,
    mediaType: CodecMediaType,
    updateCodec: (ScrcpyOptions, String) -> ScrcpyOptions,
) {
    val normalized =
        CodecCatalog.normalizedName(mediaType, codec)
            ?: return
    val expectedDeviceSerial = options.deviceSerial
    val update: (ScrcpyOptions) -> ScrcpyOptions = {
        if (it.deviceSerial == expectedDeviceSerial) updateCodec(it, normalized) else it
    }
    updateOptionsInMemory(update)
    persistOptionsInBackground(update)
}

private fun ScrcpyOptions.resolvedVideoCodec(): String =
    CodecCatalog
        .normalizedName(
            CodecMediaType.VIDEO,
            selectedVideoCodec.ifBlank { preferredVideoCodec },
        ).orEmpty()

private fun ScrcpyOptions.resolvedAudioCodec(): String =
    CodecCatalog
        .normalizedName(
            CodecMediaType.AUDIO,
            selectedAudioCodec.ifBlank { preferredAudioCodec },
        ).orEmpty()

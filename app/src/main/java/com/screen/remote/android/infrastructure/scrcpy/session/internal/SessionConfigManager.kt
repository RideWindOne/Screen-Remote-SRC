package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.DeviceCapabilityCache
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.resetForDevice
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
    storage.updateOptions(sessionId, update)
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
            capabilityCache =
                it.capabilityCache.copy(
                    remoteVideoEncoders = remoteVideoEncoders,
                    remoteAudioEncoders = remoteAudioEncoders,
                ),
        )
    }
}

/**
 * 更新设备序列号（如果序列号变化，清空设备能力）
 */
internal suspend fun Session.updateDeviceSerial(newSerial: String) {
    val current = options

    // 序列号相同，无需更新
    if (current.capabilityCache.deviceSerial == newSerial) {
        return
    }

    val message =
        if (current.capabilityCache.deviceSerial.isBlank()) {
            "首次设置设备能力签名，清空未验证的能力缓存"
        } else {
            "设备序列号变化: ${current.capabilityCache.deviceSerial} -> $newSerial，清空设备能力"
        }
    LogManager.i(LogTags.SCRCPY_CLIENT, message)
    updateOptions { it.copy(capabilityCache = it.capabilityCache.resetForDevice(newSerial)) }
}

/**
 * 保存编解码器检测结果
 */
internal fun Session.saveCodecDetectionResult(
    detectedCapabilities: DeviceCapabilityCache,
    clearUserVideoSelection: Boolean,
    clearUserAudioSelection: Boolean,
) {
    val applyDetectionResult: (ScrcpyOptions) -> ScrcpyOptions =
        { current ->
            if (current.capabilityCache.deviceSerial != detectedCapabilities.deviceSerial) {
                current
            } else {
                current
                    .copy(
                        capabilityCache = detectedCapabilities,
                    ).clearIgnoredUserCodecSelections(
                        clearVideo = clearUserVideoSelection,
                        clearAudio = clearUserAudioSelection,
                    )
            }
        }

    updateOptionsInMemory(applyDetectionResult)
    persistOptionsInBackground(applyDetectionResult)
    LogManager.d(LogTags.SCRCPY_CLIENT, "编解码器检测结果已更新到内存，后台保存: sessionId=$sessionId")
}

internal fun ScrcpyOptions.clearIgnoredUserCodecSelections(
    clearVideo: Boolean,
    clearAudio: Boolean,
): ScrcpyOptions =
    copy(
        config =
            config.copy(
                userVideoEncoder = if (clearVideo) "" else config.userVideoEncoder,
                userVideoDecoder = if (clearVideo) "" else config.userVideoDecoder,
                userAudioEncoder = if (clearAudio) "" else config.userAudioEncoder,
                userAudioDecoder = if (clearAudio) "" else config.userAudioDecoder,
            ),
    )

internal fun Session.rememberResolvedVideoDecoder(
    decoderName: String,
    expectedDeviceSerial: String,
    expectedCodec: String,
) {
    val current = options
    if (decoderName.isBlank() || current.config.userVideoDecoder.isNotBlank()) return
    if (current.capabilityCache.deviceSerial != expectedDeviceSerial || current.resolvedVideoCodec() != expectedCodec) return
    val update: (ScrcpyOptions) -> ScrcpyOptions = {
        if (it.capabilityCache.deviceSerial == expectedDeviceSerial && it.resolvedVideoCodec() == expectedCodec) {
            it.copy(capabilityCache = it.capabilityCache.copy(selectedVideoDecoder = decoderName))
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
    if (decoderName.isBlank() || current.config.userAudioDecoder.isNotBlank()) return
    if (current.capabilityCache.deviceSerial != expectedDeviceSerial || current.resolvedAudioCodec() != expectedCodec) return
    val update: (ScrcpyOptions) -> ScrcpyOptions = {
        if (it.capabilityCache.deviceSerial == expectedDeviceSerial && it.resolvedAudioCodec() == expectedCodec) {
            it.copy(capabilityCache = it.capabilityCache.copy(selectedAudioDecoder = decoderName))
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
        updateCodec = { options, normalized ->
            options.copy(capabilityCache = options.capabilityCache.copy(selectedVideoCodec = normalized))
        },
    )
}

internal fun Session.rememberNegotiatedAudioCodec(codec: String) {
    rememberNegotiatedCodec(
        codec = codec,
        mediaType = CodecMediaType.AUDIO,
        updateCodec = { options, normalized ->
            options.copy(capabilityCache = options.capabilityCache.copy(selectedAudioCodec = normalized))
        },
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
    val expectedDeviceSerial = options.capabilityCache.deviceSerial
    val update: (ScrcpyOptions) -> ScrcpyOptions = {
        if (it.capabilityCache.deviceSerial == expectedDeviceSerial) updateCodec(it, normalized) else it
    }
    updateOptionsInMemory(update)
    persistOptionsInBackground(update)
}

private fun ScrcpyOptions.resolvedVideoCodec(): String =
    CodecCatalog
        .normalizedName(
            CodecMediaType.VIDEO,
            capabilityCache.selectedVideoCodec,
        ).orEmpty()

private fun ScrcpyOptions.resolvedAudioCodec(): String =
    CodecCatalog
        .normalizedName(
            CodecMediaType.AUDIO,
            capabilityCache.selectedAudioCodec,
        ).orEmpty()

package com.mobile.scrcpy.android.infrastructure.media.codec.internal

import android.media.MediaFormat
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelectionResult

/**
 * 视频编解码器选择器（内部实现）
 * 负责视频编解码器的选择逻辑
 */

// 视频解码器优先级（参考 Easycontrol 策略）：
// 1. 硬件 + low_latency + C2 - 最佳性能和最低延迟
// 2. 硬件 + low_latency + OMX - 低延迟但旧架构
// 3. 硬件 + C2 - 新架构但无低延迟优化
// 4. 硬件 + OMX - 旧架构
// 5. 软件解码器 - 兼容性保底
internal val VIDEO_DECODER_PRIORITY =
    listOf<(String) -> Boolean>(
        { name ->
            isHardwareCodec(name) &&
                name.contains("low_latency", ignoreCase = true) &&
                name.contains("c2", ignoreCase = true)
        },
        { name ->
            isHardwareCodec(name) &&
                name.contains("low_latency", ignoreCase = true)
        },
        { name ->
            isHardwareCodec(name) &&
                name.contains("c2", ignoreCase = true)
        },
        { name -> isHardwareCodec(name) },
        { _ -> true }, // 软件解码器（兼容性保底）
    )

/**
 * 选择视频编解码器（内部实现）
 */
internal fun selectVideoCodecInternal(
    remoteEncoders: List<String>,
    localDecoders: List<String>,
    userEncoder: String?,
    userDecoder: String?,
    videoCodecTypes: List<Triple<String, String, String>>,
): CodecSelectionResult? {
    // 如果用户指定了编码器和解码器，直接返回
    if (!userEncoder.isNullOrBlank() && !userDecoder.isNullOrBlank()) {
        return createResultFromUserChoice(userEncoder, userDecoder, ::inferVideoCodecFromName, LogTags.VIDEO_DECODER)
    }

    // 如果用户只指定了编码器，找匹配的解码器
    if (!userEncoder.isNullOrBlank()) {
        return selectVideoDecoderForUserEncoder(userEncoder, localDecoders)
    }

    // 如果用户只指定了解码器，找匹配的编码器
    if (!userDecoder.isNullOrBlank()) {
        return selectVideoEncoderForUserDecoder(userDecoder, remoteEncoders, videoCodecTypes)
    }

    // 用户都没指定，执行自动选择逻辑
    return autoSelectVideoCodec(remoteEncoders, localDecoders, videoCodecTypes)
}

/**
 * 为用户指定的编码器选择匹配的视频解码器
 */
internal fun selectVideoDecoderForUserEncoder(
    userEncoder: String,
    localDecoders: List<String>,
): CodecSelectionResult? {
    val codec = inferVideoCodecFromName(userEncoder)
    val mimeType = videoCodecToMimeType(codec)

    val typeDecoders =
        localDecoders.filter { decoderName ->
            val supportedTypes = getDecoderSupportedTypes(decoderName)
            mimeType in supportedTypes
        }

    if (typeDecoders.isNotEmpty()) {
        for (priority in VIDEO_DECODER_PRIORITY) {
            val decoder = typeDecoders.find(priority)
            if (decoder != null) {
                LogManager.i(LogTags.VIDEO_DECODER, "✓ 用户编码器=$userEncoder, 系统选择解码器=$decoder, 格式=$codec")
                return CodecSelectionResult(userEncoder, decoder, codec)
            }
        }
    }

    LogManager.w(LogTags.VIDEO_DECODER, "未找到匹配用户编码器 $userEncoder 的解码器")
    return null
}

/**
 * 为用户指定的解码器选择匹配的视频编码器
 */
internal fun selectVideoEncoderForUserDecoder(
    userDecoder: String,
    remoteEncoders: List<String>,
    videoCodecTypes: List<Triple<String, String, String>>,
): CodecSelectionResult? {
    val supportedTypes = getDecoderSupportedTypes(userDecoder)

    for ((codecName, mimeType, codecFormat) in videoCodecTypes) {
        if (mimeType in supportedTypes) {
            val encoder = findBestRemoteEncoder(remoteEncoders, codecName)
            if (encoder != null) {
                LogManager.i(
                    LogTags.VIDEO_DECODER,
                    "✓ 系统选择编码器=$encoder, 用户解码器=$userDecoder, 格式=$codecFormat",
                )
                return CodecSelectionResult(encoder, userDecoder, codecFormat)
            }
        }
    }

    LogManager.w(LogTags.VIDEO_DECODER, "未找到匹配用户解码器 $userDecoder 的编码器")
    return null
}

/**
 * 自动选择最佳视频编解码器组合
 */
internal fun autoSelectVideoCodec(
    remoteEncoders: List<String>,
    localDecoders: List<String>,
    videoCodecTypes: List<Triple<String, String, String>>,
): CodecSelectionResult? {
    for ((codecName, mimeType, codecFormat) in videoCodecTypes) {
        val encoder = findBestRemoteEncoder(remoteEncoders, codecName) ?: continue

        val typeDecoders =
            localDecoders.filter { decoderName ->
                val supportedTypes = getDecoderSupportedTypes(decoderName)
                mimeType in supportedTypes
            }

        if (typeDecoders.isNotEmpty()) {
            for (priority in VIDEO_DECODER_PRIORITY) {
                val decoder = typeDecoders.find(priority)
                if (decoder != null) {
                    val decoderType = getVideoDecoderType(decoder)
                    LogManager.i(
                        LogTags.VIDEO_DECODER,
                        "✓ 选择 ${codecName.uppercase()}: 编码器=$encoder, 解码器=$decoder ($decoderType)",
                    )
                    return CodecSelectionResult(encoder, decoder, codecFormat)
                }
            }
        }
    }

    LogManager.w(LogTags.VIDEO_DECODER, "未找到匹配的视频编解码器组合")
    return null
}

/**
 * 获取视频解码器类型描述（用于日志）
 */
internal fun getVideoDecoderType(decoder: String): String =
    when {
        decoder.contains("low_latency", ignoreCase = true) &&
            decoder.contains("c2", ignoreCase = true) -> "硬件+低延迟+C2"

        decoder.contains("low_latency", ignoreCase = true) -> "硬件+低延迟"
        decoder.contains("c2", ignoreCase = true) && isHardwareCodec(decoder) -> "硬件+C2"
        isHardwareCodec(decoder) -> "硬件"
        else -> "软件"
    }

/**
 * 从视频编解码器名称推断格式
 */
internal fun inferVideoCodecFromName(codecName: String): String =
    when {
        codecName.contains("hevc", ignoreCase = true) ||
            codecName.contains("h265", ignoreCase = true) -> "h265"

        codecName.contains("avc", ignoreCase = true) ||
            codecName.contains("h264", ignoreCase = true) -> "h264"

        codecName.contains("av01", ignoreCase = true) ||
            codecName.contains("av1", ignoreCase = true) -> "av1"

        codecName.contains("vp9", ignoreCase = true) -> "vp9"

        codecName.contains("vp8", ignoreCase = true) -> "vp8"

        else -> "h264" // 默认
    }

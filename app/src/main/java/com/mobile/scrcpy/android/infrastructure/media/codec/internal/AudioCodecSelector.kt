/**
 * 音频编解码器选择逻辑
 * 
 * 内部实现文件，包含音频编解码器的选择逻辑。
 * 参考 Easycontrol 策略：OPUS 使用名称匹配，其他格式优先硬件编解码器。
 */
package com.mobile.scrcpy.android.infrastructure.media.codec.internal

import android.media.MediaFormat
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelectionResult

/**
 * 音频格式优先级：OPUS > AAC > FLAC > RAW
 */
internal val AUDIO_CODEC_PRIORITIES =
    listOf(
        Triple("opus", MediaFormat.MIMETYPE_AUDIO_OPUS, "opus"),
        Triple("aac", MediaFormat.MIMETYPE_AUDIO_AAC, "aac"),
        Triple("flac", MediaFormat.MIMETYPE_AUDIO_FLAC, "flac"),
        Triple("raw", MediaFormat.MIMETYPE_AUDIO_RAW, "raw"),
    )

/**
 * 为用户指定的编码器选择匹配的音频解码器
 */
internal fun selectAudioDecoderForUserEncoder(
    userEncoder: String,
    localAudioDecoders: List<String>,
    inferAudioCodecFromName: (String) -> String,
): CodecSelectionResult? {
    val codec = inferAudioCodecFromName(userEncoder)

    val decoder =
        if (codec == "opus") {
            findOpusDecoder(localAudioDecoders)
        } else {
            findDecoderByMimeType(codec, localAudioDecoders, ::audioCodecToMimeType)
        }

    return if (decoder != null) {
        LogManager.i(LogTags.AUDIO_DECODER, "✓ 用户编码器=$userEncoder, 系统选择解码器=$decoder, 格式=$codec")
        CodecSelectionResult(userEncoder, decoder, codec)
    } else {
        LogManager.w(LogTags.AUDIO_DECODER, "未找到匹配用户编码器 $userEncoder 的解码器")
        null
    }
}

/**
 * 为用户指定的解码器选择匹配的音频编码器
 */
internal fun selectAudioEncoderForUserDecoder(
    userDecoder: String,
    remoteEncoders: List<String>,
    inferAudioCodecFromName: (String) -> String,
): CodecSelectionResult? {
    val inferredCodec = inferAudioCodecFromName(userDecoder)

    for ((codecName, mimeType, codecFormat) in AUDIO_CODEC_PRIORITIES) {
        if (inferredCodec == codecFormat || inferredCodec.isEmpty()) {
            val encoder =
                if (codecName == "opus") {
                    findOpusEncoderForDecoder(userDecoder, remoteEncoders)
                } else {
                    findEncoderByMimeType(userDecoder, codecName, mimeType, remoteEncoders)
                }

            if (encoder != null) {
                LogManager.i(
                    LogTags.AUDIO_DECODER,
                    "✓ 系统选择编码器=$encoder, 用户解码器=$userDecoder, 格式=$codecFormat",
                )
                return CodecSelectionResult(encoder, userDecoder, codecFormat)
            }
        }
    }

    LogManager.w(LogTags.AUDIO_DECODER, "未找到匹配用户解码器 $userDecoder 的编码器")
    return null
}

/**
 * 自动选择最佳音频编解码器组合
 */
internal fun autoSelectAudioCodec(
    remoteEncoders: List<String>,
    localAudioDecoders: List<String>,
): CodecSelectionResult? {
    for ((codecName, _, codecFormat) in AUDIO_CODEC_PRIORITIES) {
        val encoder = findBestRemoteEncoder(remoteEncoders, codecName) ?: continue

        val decoder =
            if (codecName == "opus") {
                findOpusDecoder(localAudioDecoders)
            } else {
                findDecoderByMimeType(codecFormat, localAudioDecoders, ::audioCodecToMimeType)
            }

        if (decoder != null) {
            val decoderType = if (isHardwareCodec(decoder)) "硬件" else "软件"
            val encoderType = if (isHardwareCodec(encoder)) "硬件" else "软件"
            LogManager.i(
                LogTags.AUDIO_DECODER,
                "✓ 选择 ${codecName.uppercase()}: 编码器=$encoder ($encoderType), 解码器=$decoder ($decoderType)",
            )
            return CodecSelectionResult(encoder, decoder, codecFormat)
        }
    }

    LogManager.w(LogTags.AUDIO_DECODER, "未找到匹配的音频编解码器组合")
    return null
}

/**
 * 为用户解码器查找 OPUS 编码器
 */
private fun findOpusEncoderForDecoder(
    userDecoder: String,
    remoteEncoders: List<String>,
): String? {
    if (!userDecoder.contains("opus", ignoreCase = true)) {
        return null
    }

    val hardwareEncoder =
        remoteEncoders.find {
            it.contains("opus", ignoreCase = true) && isHardwareCodec(it)
        }
    return hardwareEncoder ?: remoteEncoders.find { it.contains("opus", ignoreCase = true) }
}

/**
 * 编解码器通用工具方法
 * 包含验证、查找、MIME 类型转换等通用功能
 */
package com.mobile.scrcpy.android.infrastructure.media.codec.internal

import android.media.MediaCodecList
import android.media.MediaFormat
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelectionResult

/**
 * 验证输入参数
 */
internal fun validateInputs(
    remoteEncoders: List<String>,
    localDecoders: List<String>,
    logTag: String,
): Boolean {
    if (remoteEncoders.isEmpty()) {
        LogManager.e(logTag, "远程设备未返回任何编码器") // TODO 双语
        return false
    }
    if (localDecoders.isEmpty()) {
        LogManager.e(logTag, "设备未返回任何解码器") // TODO 双语
        return false
    }
    return true
}

/**
 * 从用户选择创建编解码器结果（通用）
 */
internal fun createResultFromUserChoice(
    userEncoder: String,
    userDecoder: String,
    inferCodec: (String) -> String,
    logTag: String,
): CodecSelectionResult {
    val codec = inferCodec(userEncoder)
    LogManager.i(logTag, "✓ 使用用户选择: 编码器=$userEncoder, 解码器=$userDecoder, 格式=$codec")
    return CodecSelectionResult(userEncoder, userDecoder, codec)
}

/**
 * 为用户指定的编码器选择匹配的解码器（通用）
 */
internal fun selectDecoderForUserEncoder(
    userEncoder: String,
    localDecoders: List<String>,
    decoderPriority: List<(String) -> Boolean>,
    inferCodec: (String) -> String,
    codecToMimeType: (String) -> String,
    logTag: String,
): CodecSelectionResult? {
    val codec = inferCodec(userEncoder)
    val mimeType = codecToMimeType(codec)

    val typeDecoders =
        localDecoders.filter { decoderName ->
            val supportedTypes = getDecoderSupportedTypes(decoderName)
            mimeType in supportedTypes
        }

    if (typeDecoders.isNotEmpty()) {
        for (priority in decoderPriority) {
            val decoder = typeDecoders.find(priority)
            if (decoder != null) {
                LogManager.i(logTag, "✓ 用户编码器=$userEncoder, 系统选择解码器=$decoder, 格式=$codec")
                return CodecSelectionResult(userEncoder, decoder, codec)
            }
        }
    }

    LogManager.w(logTag, "未找到匹配用户编码器 $userEncoder 的解码器")
    return null
}

/**
 * 为用户指定的解码器选择匹配的编码器（通用）
 */
internal fun selectEncoderForUserDecoder(
    userDecoder: String,
    remoteEncoders: List<String>,
    codecTypes: List<Triple<String, String, String>>,
    logTag: String,
): CodecSelectionResult? {
    val supportedTypes = getDecoderSupportedTypes(userDecoder)

    for ((codecName, mimeType, codecFormat) in codecTypes) {
        if (mimeType in supportedTypes) {
            val encoder = findBestRemoteEncoder(remoteEncoders, codecName)
            if (encoder != null) {
                LogManager.i(logTag, "✓ 系统选择编码器=$encoder, 用户解码器=$userDecoder, 格式=$codecFormat")
                return CodecSelectionResult(encoder, userDecoder, codecFormat)
            }
        }
    }

    LogManager.w(logTag, "未找到匹配用户解码器 $userDecoder 的编码器")
    return null
}

/**
 * 自动选择最佳编解码器组合（通用）
 */
internal fun autoSelectCodec(
    remoteEncoders: List<String>,
    localDecoders: List<String>,
    codecTypes: List<Triple<String, String, String>>,
    decoderPriority: List<(String) -> Boolean>,
    logTag: String,
    getDecoderType: ((String) -> String)? = null,
): CodecSelectionResult? {
    for ((codecName, mimeType, codecFormat) in codecTypes) {
        val encoder = findBestRemoteEncoder(remoteEncoders, codecName) ?: continue

        val typeDecoders =
            localDecoders.filter { decoderName ->
                val supportedTypes = getDecoderSupportedTypes(decoderName)
                mimeType in supportedTypes
            }

        if (typeDecoders.isNotEmpty()) {
            for (priority in decoderPriority) {
                val decoder = typeDecoders.find(priority)
                if (decoder != null) {
                    val decoderTypeStr = getDecoderType?.invoke(decoder) ?: "" // TODO 删除
                    val logMsg =
                        if (decoderTypeStr.isNotEmpty()) {
                            "✓ 选择 ${codecName.uppercase()}: 编码器=$encoder, 解码器=$decoder ($decoderTypeStr)"
                        } else {
                            "✓ 选择 ${codecName.uppercase()}: 编码器=$encoder, 解码器=$decoder"
                        }
                    LogManager.i(logTag, logMsg)
                    return CodecSelectionResult(encoder, decoder, codecFormat)
                }
            }
        }
    }

    LogManager.w(logTag, "未找到匹配的编解码器组合")
    return null
}

/**
 * 查找最佳远程编码器（优先硬件）
 */
internal fun findBestRemoteEncoder(
    remoteEncoders: List<String>,
    codecName: String,
): String? {
    val hardwareEncoder =
        remoteEncoders.find {
            it.contains(codecName, ignoreCase = true) && isHardwareCodec(it)
        }
    return hardwareEncoder ?: remoteEncoders.find { it.contains(codecName, ignoreCase = true) }
}

/**
 * 查找 OPUS 解码器（使用名称匹配）
 */
internal fun findOpusDecoder(localDecoders: List<String>): String? {
    val opusDecoders = localDecoders.filter { it.contains("opus", ignoreCase = true) }
    return opusDecoders.find { isHardwareCodec(it) } ?: opusDecoders.firstOrNull()
}

/**
 * 通过 MIME 类型查找解码器（通用）
 */
internal fun findDecoderByMimeType(
    codec: String,
    localDecoders: List<String>,
    codecToMimeType: (String) -> String,
): String? {
    val mimeType = codecToMimeType(codec)
    val typeDecoders =
        localDecoders.filter { decoderName ->
            val supportedTypes = getDecoderSupportedTypes(decoderName)
            mimeType in supportedTypes
        }
    return typeDecoders.find { isHardwareCodec(it) } ?: typeDecoders.firstOrNull()
}

/**
 * 通过 MIME 类型为用户解码器查找编码器
 */
internal fun findEncoderByMimeType(
    userDecoder: String,
    codecName: String,
    mimeType: String,
    remoteEncoders: List<String>,
): String? {
    val supportedTypes = getDecoderSupportedTypes(userDecoder)
    if (mimeType !in supportedTypes) {
        return null
    }
    return findBestRemoteEncoder(remoteEncoders, codecName)
}

/**
 * 获取解码器支持的 MIME 类型列表
 */
internal fun getDecoderSupportedTypes(decoderName: String): List<String> =
    try {
        MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos
            .find { it.name == decoderName && !it.isEncoder }
            ?.supportedTypes
            ?.toList()
            ?: emptyList()
    } catch (e: Exception) {
        LogManager.w(LogTags.SDL, "活见鬼了，getDecoderSupportedTypes 输出为空")
        emptyList()
    }

/**
 * 判断是否为硬件编解码器
 * 排除 Google 和 Android 的软件实现
 */
internal fun isHardwareCodec(codecName: String): Boolean =
    !codecName.startsWith("OMX.google", ignoreCase = true) &&
        !codecName.startsWith("c2.android", ignoreCase = true)

/**
 * 视频编码格式转 MIME 类型
 */
internal fun videoCodecToMimeType(codec: String): String =
    when (codec) {
        "h265" -> MediaFormat.MIMETYPE_VIDEO_HEVC
        "h264" -> MediaFormat.MIMETYPE_VIDEO_AVC
        "av1" -> "video/av01"
        "vp9" -> MediaFormat.MIMETYPE_VIDEO_VP9
        "vp8" -> MediaFormat.MIMETYPE_VIDEO_VP8
        else -> MediaFormat.MIMETYPE_VIDEO_AVC
    }

/**
 * 音频编码格式转 MIME 类型
 */
internal fun audioCodecToMimeType(codec: String): String =
    when (codec) {
        "opus" -> MediaFormat.MIMETYPE_AUDIO_OPUS
        "aac" -> MediaFormat.MIMETYPE_AUDIO_AAC
        "flac" -> MediaFormat.MIMETYPE_AUDIO_FLAC
        "raw" -> MediaFormat.MIMETYPE_AUDIO_RAW
        else -> MediaFormat.MIMETYPE_AUDIO_OPUS
    }

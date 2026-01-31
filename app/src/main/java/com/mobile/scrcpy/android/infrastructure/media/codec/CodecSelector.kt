package com.mobile.scrcpy.android.infrastructure.media.codec

import android.media.MediaFormat
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.data.datastore.LocalDecoderCache
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.autoSelectAudioCodec
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.createResultFromUserChoice
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.inferVideoCodecFromName
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.selectAudioDecoderForUserEncoder
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.selectAudioEncoderForUserDecoder
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.selectVideoCodecInternal
import com.mobile.scrcpy.android.infrastructure.media.codec.internal.validateInputs
import kotlinx.coroutines.runBlocking

/**
 * CodecSelector - 编解码器选择器（主入口）
 *
 * 本文件是编解码器选择模块的公开 API 入口，提供视频和音频编解码器的智能选择功能。
 * 为了提高代码可维护性，内部实现已拆分到 internal/ 目录下的多个文件：
 *
 * 文件结构：
 * - CodecSelector.kt (本文件)
 *   - 公开 API：selectBestVideoCodec(), selectBestAudioCodec()
 *   - 公开工具方法：inferVideoCodecFromName(), inferAudioCodecFromName()
 *   - 常量定义：VIDEO_CODEC_TYPES, AUDIO_CODEC_PRIORITIES
 *   - 数据模型：CodecSelectionResult
 *
 * - internal/VideoCodecSelector.kt
 *   - 视频编解码器选择的内部实现
 *   - 视频解码器优先级策略
 *   - 视频编解码器匹配逻辑
 *
 * - internal/AudioCodecSelector.kt
 *   - 音频编解码器选择的内部实现
 *   - 音频编解码器匹配逻辑
 *   - OPUS 特殊处理逻辑
 *
 * - internal/CodecUtils.kt
 *   - 通用工具方法（验证、查找、转换等）
 *   - MIME 类型转换
 *   - 硬件编解码器判断
 *
 * 设计原则：
 * 1. 保持公开 API 不变，确保向后兼容
 * 2. 使用 internal 修饰符隔离内部实现
 * 3. 通过扩展函数和内部函数实现功能拆分
 * 4. 保持单向依赖：internal 文件依赖公开文件
 */

/**
 * 编解码器选择结果
 */
data class CodecSelectionResult(
    val encoder: String,
    val decoder: String,
    val codec: String,
)

/**
 * 编解码器选择器
 * 参考 Easycontrol 的智能选择策略，优先选择硬件编解码器
 * 同时考虑远程编码器和本地解码器的匹配
 */
object CodecSelector {
    // ==================== 常量定义 ====================
    
    /**
     * 视频格式优先级：HEVC (H.265) > AVC (H.264) > AV1 > VP9 > VP8
     * 格式：(scrcpy 格式名, MIME 类型, 通用名称)
     */
    private val VIDEO_CODEC_TYPES =
        listOf(
            Triple("hevc", MediaFormat.MIMETYPE_VIDEO_HEVC, "h265"), // H.265 优先（更高压缩率）
            Triple("avc", MediaFormat.MIMETYPE_VIDEO_AVC, "h264"), // H.264（兼容性最好）
            Triple("av01", "video/av01", "av1"), // AV1（新一代编码）
            Triple("vp9", MediaFormat.MIMETYPE_VIDEO_VP9, "vp9"), // VP9
            Triple("vp8", MediaFormat.MIMETYPE_VIDEO_VP8, "vp8"), // VP8
        )

    /**
     * 音频格式优先级：OPUS > AAC > FLAC > RAW
     * 格式：(scrcpy 格式名, MIME 类型, 通用名称)
     */
    private val AUDIO_CODEC_PRIORITIES =
        listOf(
            Triple("opus", MediaFormat.MIMETYPE_AUDIO_OPUS, "opus"),
            Triple("aac", MediaFormat.MIMETYPE_AUDIO_AAC, "aac"),
            Triple("flac", MediaFormat.MIMETYPE_AUDIO_FLAC, "flac"),
            Triple("raw", MediaFormat.MIMETYPE_AUDIO_RAW, "raw"),
        )

    // ==================== 公开 API 方法 ====================

    /**
     * 选择最佳视频编解码器组合
     * 参考 Easycontrol 策略：优先硬件编解码器 + low_latency + C2 架构
     *
     * @param remoteEncoders 远程设备支持的编码器列表
     * @param userEncoder 用户手动选择的编码器（优先使用）
     * @param userDecoder 用户手动选择的解码器（优先使用）
     * @return CodecSelectionResult，如果失败返回 null
     */
    fun selectBestVideoCodec(
        remoteEncoders: List<String>,
        userEncoder: String? = null,
        userDecoder: String? = null,
    ): CodecSelectionResult? {
        val localDecoders = runBlocking { LocalDecoderCache.getVideoDecoders() }
        if (!validateInputs(remoteEncoders, localDecoders, LogTags.VIDEO_DECODER)) {
            return null
        }

        return selectVideoCodecInternal(
            remoteEncoders,
            localDecoders,
            userEncoder,
            userDecoder,
            VIDEO_CODEC_TYPES,
        )
    }

    /**
     * 选择最佳音频编解码器组合
     * 参考 Easycontrol 策略：OPUS 使用名称匹配，其他格式优先硬件编解码器
     * @param remoteEncoders 远程设备支持的编码器列表
     * @param userEncoder 用户手动选择的编码器（优先使用）
     * @param userDecoder 用户手动选择的解码器（优先使用）
     * @return CodecSelectionResult，如果失败返回 null
     */
    fun selectBestAudioCodec(
        remoteEncoders: List<String>,
        userEncoder: String? = null,
        userDecoder: String? = null,
    ): CodecSelectionResult? {
        val localDecoders = runBlocking { LocalDecoderCache.getAudioDecoders() }
        if (!validateInputs(remoteEncoders, localDecoders, LogTags.AUDIO_DECODER)) {
            return null
        }

        // 如果用户指定了编码器和解码器，直接返回
        if (!userEncoder.isNullOrBlank() && !userDecoder.isNullOrBlank()) {
            return createResultFromUserChoice(userEncoder, userDecoder, ::inferAudioCodecFromName, LogTags.AUDIO_DECODER)
        }

        // 如果用户只指定了编码器，找匹配的解码器
        if (!userEncoder.isNullOrBlank()) {
            return selectAudioDecoderForUserEncoder(userEncoder, localDecoders, ::inferAudioCodecFromName)
        }

        // 如果用户只指定了解码器，找匹配的编码器
        if (!userDecoder.isNullOrBlank()) {
            return selectAudioEncoderForUserDecoder(userDecoder, remoteEncoders, ::inferAudioCodecFromName)
        }

        // 用户都没指定，执行自动选择逻辑
        return autoSelectAudioCodec(remoteEncoders, localDecoders)
    }

    // ==================== 公开工具方法 ====================

    /**
     * 从视频编解码器名称推断格式（公开方法，供外部使用）
     */
    fun inferVideoCodecFromName(codecName: String): String =
        com.mobile.scrcpy.android.infrastructure.media.codec.internal.inferVideoCodecFromName(codecName)

    /**
     * 从音频编解码器名称推断格式（公开方法，供外部使用）
     */
    fun inferAudioCodecFromName(codecName: String): String =
        when {
            codecName.contains("opus", ignoreCase = true) -> "opus"
            codecName.contains("aac", ignoreCase = true) -> "aac"
            codecName.contains("flac", ignoreCase = true) -> "flac"
            codecName.contains("raw", ignoreCase = true) -> "raw"
            else -> "" // 无法推断
        }
}


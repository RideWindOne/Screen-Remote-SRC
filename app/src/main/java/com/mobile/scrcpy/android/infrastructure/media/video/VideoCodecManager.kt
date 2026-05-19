package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper

/**
 * VideoCodecManager - 视频编解码器管理
 * 负责解码器的创建、选择和缓存
 */
class VideoCodecManager(
    private val videoCodec: String,
    cachedDecoderName: String? = null,
) {
    private var selectedDecoderName: String? = cachedDecoderName

    var onDecoderSelected: ((decoderName: String) -> Unit)? = null

    val mimeType: String
        get() {
            val mime = ApiCompatHelper.getVideoMimeType(videoCodec.lowercase())
            return mime ?: MediaFormat.MIMETYPE_VIDEO_AVC
        }

    /**
     * 创建解码器 - 优先使用缓存，避免重复检测
     */
    fun createDecoder(
        width: Int,
        height: Int,
    ): MediaCodec? {
        try {
            val format = MediaFormat.createVideoFormat(mimeType, width, height)

            // 1. 优先使用缓存的解码器
            selectedDecoderName?.let { cachedName ->
                if (isGoldfishCodec(cachedName) || isAndroidSoftwareCodec(cachedName)) {
                    val reason = if (isGoldfishCodec(cachedName)) "goldfish" else "c2.android 软件解码器"
                    LogManager.w(LogTags.VIDEO_DECODER, "缓存解码器为 $reason，降级到重新选择: $cachedName")
                    selectedDecoderName = null
                } else {
                    try {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "使用缓存解码器: $cachedName" }
                        return MediaCodec.createByCodecName(cachedName)
                    } catch (_: Exception) {
                        LogManager.w(LogTags.VIDEO_DECODER, "缓存解码器失效: $cachedName, 重新检测")
                        selectedDecoderName = null
                    }
                }
            }

            // 2. 缓存失效或不存在，开始检测
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

            // 系统推荐
            codecList.findDecoderForFormat(format)?.let { name ->
                val info = codecList.codecInfos.firstOrNull { it.name == name }
                if (info != null && isLikelyHardware(info) && !isGoldfishCodec(name)) {
                    selectedDecoderName = name
                    onDecoderSelected?.invoke(name)
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "系统推荐: $name" }
                    return MediaCodec.createByCodecName(name)
                }
            }

            // 手动选择硬件解码器
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (!isLikelyHardware(info) || isGoldfishCodec(info.name)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "硬件解码: ${info.name}" }
                    return MediaCodec.createByCodecName(info.name)
                } catch (_: Exception) {
                }
            }

            // 软件解码器兜底：优先 OMX.google，其次其他非 c2.android 软件实现，最后才是 c2.android。
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (isLikelyHardware(info) || isGoldfishCodec(info.name) || !isGoogleSoftwareCodec(info.name)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "软件解码: ${info.name}" }
                    return MediaCodec.createByCodecName(info.name)
                } catch (_: Exception) {
                }
            }

            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (isLikelyHardware(info) || isGoldfishCodec(info.name) || isAndroidSoftwareCodec(info.name)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "软件解码: ${info.name}" }
                    return MediaCodec.createByCodecName(info.name)
                } catch (_: Exception) {
                }
            }

            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (isLikelyHardware(info) || isGoldfishCodec(info.name) || !isAndroidSoftwareCodec(info.name)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)
                    LogManager.w(LogTags.VIDEO_DECODER, "最后软件兜底使用 c2.android 解码器: ${info.name}")
                    return MediaCodec.createByCodecName(info.name)
                } catch (_: Exception) {
                }
            }

            // goldfish 只作为最后兜底。
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (!isGoldfishCodec(info.name)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)
                    LogManager.w(LogTags.VIDEO_DECODER, "最后兜底使用 goldfish 解码器: ${info.name}")
                    return MediaCodec.createByCodecName(info.name)
                } catch (_: Exception) {
                }
            }

            // 回退
            LogManager.w(LogTags.VIDEO_DECODER, "使用默认解码器")
            return MediaCodec.createDecoderByType(mimeType)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "创建解码器失败", e)
            return null
        }
    }

    private fun isLikelyHardware(info: MediaCodecInfo): Boolean = ApiCompatHelper.isHardwareAccelerated(info)

    private fun isGoldfishCodec(name: String): Boolean = name.contains("goldfish", ignoreCase = true)

    private fun isAndroidSoftwareCodec(name: String): Boolean = name.startsWith("c2.android", ignoreCase = true)

    private fun isGoogleSoftwareCodec(name: String): Boolean = name.startsWith("OMX.google", ignoreCase = true)
}

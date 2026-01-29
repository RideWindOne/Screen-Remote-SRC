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
                try {
                    LogManager.d(LogTags.VIDEO_DECODER, "使用缓存解码器: $cachedName")
                    return MediaCodec.createByCodecName(cachedName)
                } catch (_: Exception) {
                    LogManager.w(LogTags.VIDEO_DECODER, "缓存解码器失效: $cachedName, 重新检测")
                    selectedDecoderName = null
                }
            }

            // 2. 缓存失效或不存在，开始检测
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

            // 系统推荐
            codecList.findDecoderForFormat(format)?.let { name ->
                val info = codecList.codecInfos.firstOrNull { it.name == name }
                if (info != null && isLikelyHardware(info)) {
                    selectedDecoderName = name
                    onDecoderSelected?.invoke(name)
                    LogManager.d(LogTags.VIDEO_DECODER, "系统推荐: $name")
                    return MediaCodec.createByCodecName(name)
                }
            }

            // 手动选择硬件解码器
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (!isLikelyHardware(info) || info.name.contains("goldfish", true)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)
                    LogManager.d(LogTags.VIDEO_DECODER, "硬件解码: ${info.name}")
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
}

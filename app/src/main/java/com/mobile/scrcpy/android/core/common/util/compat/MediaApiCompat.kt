/*
 * 媒体 API 兼容性工具
 * 
 * 从 ApiCompatHelper.kt 拆分而来
 * 职责：MediaCodec、音视频编解码器相关 API 兼容
 */

package com.mobile.scrcpy.android.core.common.util.compat

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build

/**
 * 获取视频编解码器的 MIME 类型（兼容不同 API 级别）
 *
 * Android 10 (API 29) 引入了 MediaFormat.MIMETYPE_VIDEO_AV1
 */
fun getVideoMimeType(codecName: String): String? =
    when (codecName.lowercase()) {
        "h264", "avc" -> MediaFormat.MIMETYPE_VIDEO_AVC
        "h265", "hevc" -> MediaFormat.MIMETYPE_VIDEO_HEVC
        "av1" -> if (Build.VERSION.SDK_INT >= 29) MediaFormat.MIMETYPE_VIDEO_AV1 else null
        "vp8" -> MediaFormat.MIMETYPE_VIDEO_VP8
        "vp9" -> MediaFormat.MIMETYPE_VIDEO_VP9
        else -> null
    }

/**
 * 判断当前设备是否支持 AV1 编解码器
 */
fun isAV1Supported(): Boolean = Build.VERSION.SDK_INT >= 29

/**
 * 获取支持的视频编解码器列表（根据 API 级别过滤）
 */
fun getSupportedVideoCodecs(): List<String> {
    val codecs = mutableListOf("h264", "h265")
    if (isAV1Supported()) {
        codecs.add("av1")
    }
    return codecs
}

/**
 * 判断 MediaCodecInfo 是否为硬件加速编解码器
 */
fun isHardwareAccelerated(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= 29) {
        info.isHardwareAccelerated && !info.isSoftwareOnly
    } else {
        !info.name.startsWith("OMX.google", ignoreCase = true)
    }

/**
 * 安全地设置 MediaFormat 的 KEY_LOW_LATENCY
 */
fun setLowLatencyIfSupported(
    format: MediaFormat,
    lowLatency: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, lowLatency)
    }
}

/**
 * 安全地设置 MediaFormat 的 KEY_ALLOW_FRAME_DROP
 */
fun setAllowFrameDropIfSupported(
    format: MediaFormat,
    allowFrameDrop: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        format.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, allowFrameDrop)
    }
}

/**
 * 安全地从 MediaFormat 获取裁剪区域
 */
fun getCropRectIfSupported(format: MediaFormat): android.graphics.Rect? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            format.containsKey("crop-left")
        ) {
            val left = format.getInteger("crop-left")
            val right = format.getInteger("crop-right")
            val top = format.getInteger("crop-top")
            val bottom = format.getInteger("crop-bottom")
            android.graphics.Rect(left, top, right, bottom)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

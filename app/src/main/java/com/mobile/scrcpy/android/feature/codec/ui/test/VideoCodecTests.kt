/*
 * 视频编解码器测试工具
 * 
 * 从 CodecTestUtils.kt 拆分而来
 * 职责：视频编解码器查询和测试
 */

package com.mobile.scrcpy.android.feature.codec.ui.test

import android.media.MediaCodecList
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.feature.codec.model.CodecInfo

/**
 * 获取所有视频编解码器
 */
fun getVideoCodecs(): List<CodecInfo> {
    val result = mutableListOf<CodecInfo>()

    try {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

        for (codecInfo in codecList.codecInfos) {
            val supportedTypes = codecInfo.supportedTypes

            for (type in supportedTypes) {
                if (type.startsWith("video/")) {
                    val capabilities =
                        try {
                            val caps = codecInfo.getCapabilitiesForType(type)
                            buildString {
                                val videoC = caps.videoCapabilities
                                if (videoC != null) {
                                    val widthRange = videoC.supportedWidths
                                    val heightRange = videoC.supportedHeights
                                    append(
                                        "分辨率: ${widthRange.lower}x${heightRange.lower} " +
                                            "- ${widthRange.upper}x${heightRange.upper}",
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            "无详细信息"
                        }

                    result.add(
                        CodecInfo(
                            name = codecInfo.name,
                            type = type,
                            isEncoder = codecInfo.isEncoder,
                            capabilities = capabilities,
                        ),
                    )
                }
            }
        }
        result.sortWith(compareBy({ it.type }, { it.name }))

        // 打印视频解码器信息
        val decoders = result.filter { !it.isEncoder }
        LogManager.i(LogTags.CODEC_TEST_SCREEN, "========== 视频编解码器 ==========")
        LogManager.i(LogTags.CODEC_TEST_SCREEN, "解码器: ${decoders.size} 个")

        // 统计 low_latency 解码器
        val lowLatencyDecoders = decoders.filter { it.name.contains("low_latency", ignoreCase = true) }
        if (lowLatencyDecoders.isNotEmpty()) {
            LogManager.i(LogTags.CODEC_TEST_SCREEN, "⚡ 发现 ${lowLatencyDecoders.size} 个 LOW_LATENCY 解码器")
            lowLatencyDecoders.forEach { codec ->
                LogManager.i(LogTags.CODEC_TEST_SCREEN, "  ${codec.name} (${codec.type})")
            }
        } else {
            LogManager.i(LogTags.CODEC_TEST_SCREEN, "❌ 当前设备不支持 LOW_LATENCY 解码器")
        }
        LogManager.i(LogTags.CODEC_TEST_SCREEN, "====================================\n")
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "获取视频编解码器列表失败: ${e.message}", e)
    }

    return result
}

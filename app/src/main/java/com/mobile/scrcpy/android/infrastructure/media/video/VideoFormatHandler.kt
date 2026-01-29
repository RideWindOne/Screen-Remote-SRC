package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import java.nio.ByteBuffer

/**
 * VideoFormatHandler - 视频格式处理器
 * 负责不同编码格式的配置和重配置
 */
class VideoFormatHandler(
    private val codecManager: VideoCodecManager,
) {
    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)? = null

    /**
     * 配置 H.264 解码器
     */
    fun configureH264(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ) {
        try {
            val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))

            applyLowLatencyConfig(format)

            val initialSurface = surface?.takeIf { it.isValid } ?: dummySurface

            if (initialSurface == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置解码器：没有可用的 Surface")
                return
            }

            decoder.configure(format, initialSurface, null, 0)
            decoder.start()

            val isSurfaceBound = (surface != null && surface.isValid)
            if (isSurfaceBound) {
                LogManager.d(LogTags.VIDEO_DECODER, "解码器配置完成，Surface 已绑定")
            } else {
                LogManager.d(LogTags.VIDEO_DECODER, "解码器配置完成（使用 dummy Surface）")
            }

            // 从输出格式获取真实尺寸
            updateVideoSizeFromOutputFormat(decoder.outputFormat)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置解码器失败: ${e.message}", e)
        }
    }

    /**
     * 重新配置 H.264 解码器
     */
    fun reconfigureH264(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "重新配置解码器")

            oldDecoder?.stop()
            oldDecoder?.release()

            val newDecoder = codecManager.createDecoder(width, height)
            if (newDecoder == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建新解码器")
                return null
            }

            configureH264(newDecoder, width, height, sps, pps, surface, dummySurface)
            return newDecoder
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置解码器失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 配置 H.265 解码器
     */
    fun configureH265(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ) {
        try {
            val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(vps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(pps))

            applyLowLatencyConfig(format)

            val initialSurface = surface?.takeIf { it.isValid } ?: dummySurface

            if (initialSurface == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置 H.265 解码器：没有可用的 Surface")
                return
            }

            decoder.configure(format, initialSurface, null, 0)
            decoder.start()

            val isSurfaceBound = (surface != null && surface.isValid)
            if (isSurfaceBound) {
                LogManager.d(LogTags.VIDEO_DECODER, "H.265 解码器配置完成，Surface 已绑定")
            } else {
                LogManager.d(LogTags.VIDEO_DECODER, "H.265 解码器配置完成（使用 dummy Surface）")
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置 H.265 解码器失败: ${e.message}", e)
        }
    }

    /**
     * 重新配置 H.265 解码器
     */
    fun reconfigureH265(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "重新配置 H.265 解码器: ${width}x$height")

            oldDecoder?.stop()
            oldDecoder?.release()

            val newDecoder = codecManager.createDecoder(width, height)
            if (newDecoder == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建新解码器")
                return null
            }

            configureH265(newDecoder, width, height, vps, sps, pps, surface, dummySurface)
            LogManager.d(LogTags.VIDEO_DECODER, "H.265 解码器重新配置完成")
            return newDecoder
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置 H.265 解码器失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 配置 AV1 解码器
     */
    fun configureAV1(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ) {
        try {
            val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            applyLowLatencyConfig(format)

            val initialSurface = surface?.takeIf { it.isValid } ?: dummySurface

            if (initialSurface == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置 AV1 解码器：没有可用的 Surface")
                return
            }

            decoder.configure(format, initialSurface, null, 0)
            decoder.start()

            val isSurfaceBound = (surface != null && surface.isValid)
            if (isSurfaceBound) {
                LogManager.d(LogTags.VIDEO_DECODER, "AV1 解码器配置完成，Surface 已绑定")
            } else {
                LogManager.d(LogTags.VIDEO_DECODER, "AV1 解码器配置完成（使用 dummy Surface）")
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置 AV1 解码器失败: ${e.message}", e)
        }
    }

    /**
     * 重新配置 AV1 解码器
     */
    fun reconfigureAV1(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "重新配置 AV1 解码器: ${width}x$height")

            oldDecoder?.stop()
            oldDecoder?.release()

            val newDecoder = codecManager.createDecoder(width, height)
            if (newDecoder == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建新解码器")
                return null
            }

            configureAV1(newDecoder, width, height, surface, dummySurface)
            LogManager.d(LogTags.VIDEO_DECODER, "AV1 解码器重新配置完成")
            return newDecoder
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置 AV1 解码器失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 应用低延迟配置
     */
    private fun applyLowLatencyConfig(format: MediaFormat) {
        ApiCompatHelper.setLowLatencyIfSupported(format, 1)
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
        ApiCompatHelper.setAllowFrameDropIfSupported(format, 0)
    }

    /**
     * 从输出格式获取真实视频尺寸
     */
    fun updateVideoSizeFromOutputFormat(outputFormat: MediaFormat) {
        try {
            val cropRect = ApiCompatHelper.getCropRectIfSupported(outputFormat)

            val realWidth: Int
            val realHeight: Int

            if (cropRect != null) {
                realWidth = cropRect.right - cropRect.left + 1
                realHeight = cropRect.bottom - cropRect.top + 1
            } else {
                realWidth = outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                realHeight = outputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            }

            LogManager.d(LogTags.VIDEO_DECODER, "视频尺寸: ${realWidth}x$realHeight")

            val rotation = if (realWidth > realHeight) 90 else 0
            onVideoSizeChanged?.invoke(realWidth, realHeight, rotation)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "获取输出格式失败: ${e.message}")
        }
    }
}

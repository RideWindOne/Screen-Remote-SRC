package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import java.nio.ByteBuffer

internal class VideoDecoderConfigurator(
    private val codecManager: VideoCodecManager,
) {


    fun configureH264(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
        onConfigured: (MediaFormat) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        configureDecoder(
            decoder = decoder,
            format = format,
            surface = surface,
            dummySurface = dummySurface,
            codecLabel = "解码器",
            onConfigured = onConfigured,
        )    }

    fun reconfigureH264(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
        onConfigured: (MediaFormat) -> Unit,
    ): MediaCodec =
        recreateDecoder(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            reconfigureLabel = "解码器",
        ) { newDecoder ->
            configureH264(
                decoder = newDecoder,
                width = width,
                height = height,
                sps = sps,
                pps = pps,
                surface = surface,
                dummySurface = dummySurface,
                onConfigured = onConfigured,
            )
        }

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
        val standardFormat = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
        standardFormat.setByteBuffer("csd-0", ByteBuffer.wrap(vps))
        standardFormat.setByteBuffer("csd-1", ByteBuffer.wrap(sps))
        standardFormat.setByteBuffer("csd-2", ByteBuffer.wrap(pps))

        try {
            configureDecoder(
                decoder = decoder,
                format = standardFormat,
                surface = surface,
                dummySurface = dummySurface,
                codecLabel = "H.265 解码器",
                onConfigured = null,
            )
        } catch (standardError: VideoDecoderConfigurationException) {
            LogManager.w(
                LogTags.VIDEO_DECODER,
                "H.265 解码器标准 csd-0/1/2 配置失败，尝试单一 csd-0 兼容模式: ${standardError.message}",
            )
            runCatching { decoder.reset() }

            val combinedFormat = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            combinedFormat.setByteBuffer("csd-0", ByteBuffer.wrap(vps + sps + pps))
            configureDecoder(
                decoder = decoder,
                format = combinedFormat,
                surface = surface,
                dummySurface = dummySurface,
                codecLabel = "H.265 解码器(兼容模式)",
                onConfigured = null,
            )
        }
    }

    fun reconfigureH265(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        recreateDecoder(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            reconfigureLabel = "H.265 解码器",
        ) { newDecoder ->
            configureH265(
                decoder = newDecoder,
                width = width,
                height = height,
                vps = vps,
                sps = sps,
                pps = pps,
                surface = surface,
                dummySurface = dummySurface,
            )
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "H.265 解码器重新配置完成" }
        }

    fun configureAV1(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ) {
        val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
        configureDecoder(
            decoder = decoder,
            format = format,
            surface = surface,
            dummySurface = dummySurface,
            codecLabel = "AV1 解码器",
            onConfigured = null,
        )    }

    fun reconfigureAV1(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        recreateDecoder(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            reconfigureLabel = "AV1 解码器",
        ) { newDecoder ->
            configureAV1(
                decoder = newDecoder,
                width = width,
                height = height,
                surface = surface,
                dummySurface = dummySurface,
            )
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "AV1 解码器重新配置完成" }
        }

    private fun configureDecoder(
        decoder: MediaCodec,
        format: MediaFormat,
        surface: Surface?,
        dummySurface: Surface?,
        codecLabel: String,
        onConfigured: ((MediaFormat) -> Unit)?,
    ) {
        try {
            applyLowLatencyConfig(format)
            val initialSurface = resolveSurface(surface, dummySurface)
            if (initialSurface == null) {
                val reason = "没有可用的 Surface"
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置$codecLabel：$reason")
                throw VideoDecoderConfigurationException(codecLabel, reason)
            }

            decoder.configure(format, initialSurface, null, 0)
            decoder.start()

            if (surface != null && surface.isValid) {
                VideoDebugLog.d(LogTags.VIDEO_DECODER) { "$codecLabel 配置完成（使用真实 Surface）" }
            } else {
                VideoDebugLog.d(LogTags.VIDEO_DECODER) { "$codecLabel 配置完成（使用 dummy Surface）" }
            }

            onConfigured?.invoke(decoder.outputFormat)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置$codecLabel 失败: ${e.message}", e)
            if (e is VideoDecoderConfigurationException) {
                throw e
            }
            throw VideoDecoderConfigurationException(codecLabel, e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun recreateDecoder(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        reconfigureLabel: String,
        configure: (MediaCodec) -> Unit,
    ): MediaCodec =
        try {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "重新配置$reconfigureLabel: ${width}x$height" }
            oldDecoder?.stop()
            oldDecoder?.release()

            val newDecoder = codecManager.createDecoder(width, height)
            if (newDecoder == null) {
                val reason = "无法创建新解码器"
                LogManager.e(LogTags.VIDEO_DECODER, reason)
                throw VideoDecoderConfigurationException(reconfigureLabel, reason)
            } else {
                configure(newDecoder)
                newDecoder
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置$reconfigureLabel 失败: ${e.message}", e)
            if (e is VideoDecoderConfigurationException) {
                throw e
            }
            throw VideoDecoderConfigurationException(reconfigureLabel, e.message ?: e.javaClass.simpleName, e)
        }




    private fun resolveSurface(
        surface: Surface?,
        dummySurface: Surface?,
    ): Surface? =
        if (surface != null && surface.isValid) {
            surface
        } else {
            dummySurface
        }

    private fun applyLowLatencyConfig(format: MediaFormat) {
        try {
            ApiCompatHelper.setLowLatencyIfSupported(format, 1)
            format.setInteger(MediaFormat.KEY_PRIORITY, 0)

            try {
                format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            } catch (e: Exception) {
                LogManager.w(LogTags.VIDEO_DECODER, "设置 OPERATING_RATE 失败: ${e.message}")
            }

            ApiCompatHelper.setAllowFrameDropIfSupported(format, 0)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "应用低延迟配置失败: ${e.message}")
        }
    }
}

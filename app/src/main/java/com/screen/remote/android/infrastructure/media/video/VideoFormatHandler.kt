package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper

/**
 * Video format facade.
 *
 * Keeps the existing public API stable while delegating decoder configuration
 * and output format projection to smaller collaborators.
 */
class VideoFormatHandler(
    private val codecManager: VideoCodecManager,
) {
    private val configurator = VideoDecoderConfigurator(codecManager)
    private val outputReporter = VideoOutputFormatReporter()

    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)?
        get() = outputReporter.onVideoSizeChanged
        set(value) {
            outputReporter.onVideoSizeChanged = value
        }

    fun configureH264(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        configurator.configureH264(
            decoder = decoder,
            width = width,
            height = height,
            sps = sps,
            pps = pps,
            surface = surface,
            dummySurface = dummySurface,
            onConfigured = null,
        )

    fun reconfigureH264(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? =
        configurator.reconfigureH264(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            sps = sps,
            pps = pps,
            surface = surface,
            dummySurface = dummySurface,
            onConfigured = null,
        )

    fun configureH265(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        configurator.configureH265(
            decoder = decoder,
            width = width,
            height = height,
            vps = vps,
            sps = sps,
            pps = pps,
            surface = surface,
            dummySurface = dummySurface,
        )

    fun reconfigureH265(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        vps: ByteArray,
        sps: ByteArray,
        pps: ByteArray,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? =
        configurator.reconfigureH265(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            vps = vps,
            sps = sps,
            pps = pps,
            surface = surface,
            dummySurface = dummySurface,
        )

    fun configureAV1(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        configurator.configureAV1(
            decoder = decoder,
            width = width,
            height = height,
            surface = surface,
            dummySurface = dummySurface,
        )

    fun reconfigureAV1(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? =
        configurator.reconfigureAV1(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            surface = surface,
            dummySurface = dummySurface,
        )

    fun reconfigureVpx(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec? =
        configurator.reconfigureVpx(
            oldDecoder = oldDecoder,
            width = width,
            height = height,
            surface = surface,
            dummySurface = dummySurface,
        )

    fun configureVpx(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        configurator.configureVpx(
            decoder = decoder,
            width = width,
            height = height,
            surface = surface,
            dummySurface = dummySurface,
        )

    fun updateVideoSizeFromOutputFormat(outputFormat: MediaFormat) {
        outputReporter.updateVideoSizeFromOutputFormat(outputFormat)
    }

    fun prepareRuntimeFallback(
        decoder: MediaCodec,
        cause: Throwable,
    ): Boolean {
        if (codecManager.decoderSelectionPinned) return false
        val decoderName = runCatching { decoder.name }.getOrNull() ?: return false
        codecManager.rejectDecoder(decoderName, cause)
        return true
    }
}

internal class VideoDecoderConfigurationException(
    codecLabel: String,
    reason: String,
    cause: Throwable? = null,
) : IllegalStateException("$codecLabel configuration failed: $reason", cause)

internal class VideoOutputFormatReporter {
    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)? = null

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

            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Video size: ${realWidth}x$realHeight" }

            val rotation = if (realWidth > realHeight) 90 else 0
            onVideoSizeChanged?.invoke(realWidth, realHeight, rotation)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to get output format: ${e.message}")
        }
    }
}

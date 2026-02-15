package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

/**
 * Video format facade.
 *
 * Keeps the existing public API stable while delegating decoder configuration
 * and output format projection to smaller collaborators.
 */
class VideoFormatHandler(
    codecManager: VideoCodecManager,
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
    ) {
        configurator.configureH264(
            decoder = decoder,
            width = width,
            height = height,
            sps = sps,
            pps = pps,
            surface = surface,
            dummySurface = dummySurface,
            onConfigured = outputReporter::updateVideoSizeFromOutputFormat,
        )
    }

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
            onConfigured = outputReporter::updateVideoSizeFromOutputFormat,
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
    ) {
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
    ) {
        configurator.configureAV1(
            decoder = decoder,
            width = width,
            height = height,
            surface = surface,
            dummySurface = dummySurface,
        )
    }

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

    fun updateVideoSizeFromOutputFormat(outputFormat: MediaFormat) {
        outputReporter.updateVideoSizeFromOutputFormat(outputFormat)
    }
}

package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
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
        onConfigured: ((MediaFormat) -> Unit)?,
    ): MediaCodec =
        configureWithFallback(decoder, width, height, "H.264 decoder") { candidate ->
            val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
            configureDecoder(
                decoder = candidate,
                format = format,
                surface = surface,
                dummySurface = dummySurface,
                codecLabel = "H.264 decoder",
                onConfigured = onConfigured,
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
        onConfigured: ((MediaFormat) -> Unit)?,
    ): MediaCodec {
        releaseDecoder(oldDecoder)
        val decoder = codecManager.createDecoder(width, height) ?: throw createFailure("H.264 decoder")
        return configureH264(decoder, width, height, sps, pps, surface, dummySurface, onConfigured)
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
    ): MediaCodec =
        configureWithFallback(decoder, width, height, "H.265 decoder") { candidate ->
            val combinedFormat = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            combinedFormat.setByteBuffer("csd-0", ByteBuffer.wrap(vps + sps + pps))

            try {
                configureDecoder(
                    decoder = candidate,
                    format = combinedFormat,
                    surface = surface,
                    dummySurface = dummySurface,
                    codecLabel = "H.265 decoder",
                    onConfigured = null,
                )
            } catch (standardError: VideoDecoderConfigurationException) {
                LogManager.w(
                    LogTags.VIDEO_DECODER,
                    "H.265 decoder single csd-0 configuration failed, try to split csd-0/1/2: ${standardError.message}",
                )
                candidate.reset()

                val splitFormat = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
                splitFormat.setByteBuffer("csd-0", ByteBuffer.wrap(vps))
                splitFormat.setByteBuffer("csd-1", ByteBuffer.wrap(sps))
                splitFormat.setByteBuffer("csd-2", ByteBuffer.wrap(pps))
                configureDecoder(
                    decoder = candidate,
                    format = splitFormat,
                    surface = surface,
                    dummySurface = dummySurface,
                    codecLabel = "H.265 decoder (split CSD)",
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
    ): MediaCodec {
        releaseDecoder(oldDecoder)
        val decoder = codecManager.createDecoder(width, height) ?: throw createFailure("H.265 decoder")
        return configureH265(decoder, width, height, vps, sps, pps, surface, dummySurface)
    }

    fun configureAV1(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        configureWithFallback(decoder, width, height, "AV1 decoder") { candidate ->
            val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            configureDecoder(
                decoder = candidate,
                format = format,
                surface = surface,
                dummySurface = dummySurface,
                codecLabel = "AV1 decoder",
                onConfigured = null,
            )
        }

    fun reconfigureAV1(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec {
        releaseDecoder(oldDecoder)
        val decoder = codecManager.createDecoder(width, height) ?: throw createFailure("AV1 decoder")
        return configureAV1(decoder, width, height, surface, dummySurface)
    }

    fun configureVpx(
        decoder: MediaCodec,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec =
        configureWithFallback(decoder, width, height, "VPx decoder") { candidate ->
            val format = MediaFormat.createVideoFormat(codecManager.mimeType, width, height)
            configureDecoder(
                decoder = candidate,
                format = format,
                surface = surface,
                dummySurface = dummySurface,
                codecLabel = "VPx decoder",
                onConfigured = null,
            )
        }

    fun reconfigureVpx(
        oldDecoder: MediaCodec?,
        width: Int,
        height: Int,
        surface: Surface?,
        dummySurface: Surface?,
    ): MediaCodec {
        releaseDecoder(oldDecoder)
        val decoder = codecManager.createDecoder(width, height) ?: throw createFailure("VPx decoder")
        return configureVpx(decoder, width, height, surface, dummySurface)
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
                val reason = "No Surface is available"
                LogManager.e(LogTags.VIDEO_DECODER, "Unable to configure $codecLabel: $reason")
                throw VideoDecoderConfigurationException(codecLabel, reason)
            }

            decoder.configure(format, initialSurface, null, 0)
            decoder.start()

            if (surface != null && surface.isValid) {
                VideoDebugLog.d(LogTags.VIDEO_DECODER) { "$codecLabel configuration completed (using real Surface)" }
            } else {
                VideoDebugLog.d(LogTags.VIDEO_DECODER) { "$codecLabel configuration completed (using dummy Surface)" }
            }

            onConfigured?.invoke(decoder.outputFormat)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Configuring $codecLabel failed: ${e.message}", e)
            if (e is VideoDecoderConfigurationException) {
                throw e
            }
            throw VideoDecoderConfigurationException(codecLabel, e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun configureWithFallback(
        initialDecoder: MediaCodec,
        width: Int,
        height: Int,
        codecLabel: String,
        configure: (MediaCodec) -> Unit,
    ): MediaCodec {
        val attemptedNames = linkedSetOf<String>()
        var candidate = initialDecoder
        var lastFailure: Throwable? = null

        repeat(MAX_CONFIGURE_ATTEMPTS) {
            val candidateName = runCatching { candidate.name }.getOrDefault("unknown")
            if (!attemptedNames.add(candidateName)) {
                releaseDecoder(candidate)
                throw VideoDecoderConfigurationException(
                    codecLabel,
                    "Candidate decoder is duplicated and no fallback available: $candidateName",
                    lastFailure,
                )
            }

            try {
                configure(candidate)
                codecManager.reportDecoderSelected(candidateName)
                if (attemptedNames.size > 1) {
                    LogManager.w(LogTags.VIDEO_DECODER, "$codecLabel has fallen back to $candidateName")
                }
                return candidate
            } catch (error: Exception) {
                lastFailure = error
                codecManager.rejectDecoder(candidateName, error)
                releaseDecoder(candidate)
                if (codecManager.decoderSelectionPinned) {
                    throw VideoDecoderConfigurationException(
                        codecLabel,
                        "User fixed decoder configuration failure: $candidateName",
                        error,
                    )
                }
                candidate =
                    codecManager.createDecoder(width, height)
                        ?: throw VideoDecoderConfigurationException(
                            codecLabel,
                            "All candidate decoders failed to configure",
                            error,
                        )
            }
        }

        releaseDecoder(candidate)
        throw VideoDecoderConfigurationException(
            codecLabel,
            "Maximum number of candidate attempts exceeded",
            lastFailure
        )
    }

    private fun createFailure(codecLabel: String): VideoDecoderConfigurationException =
        VideoDecoderConfigurationException(codecLabel, "Unable to create new decoder")

    private fun releaseDecoder(decoder: MediaCodec?) {
        if (decoder == null) return
        runCatching { decoder.stop() }
        runCatching { decoder.release() }
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
                LogManager.w(LogTags.VIDEO_DECODER, "Failed to set OPERATING_RATE: ${e.message}")
            }

            // Remote control favors the newest frame. Let Surface decoders discard late output
            // instead of building an ever-growing presentation backlog.
            ApiCompatHelper.setAllowFrameDropIfSupported(format, 1)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to apply low latency configuration: ${e.message}")
        }
    }

    private companion object {
        const val MAX_CONFIGURE_ATTEMPTS = 8
    }
}

package com.screen.remote.android.infrastructure.media.video

import java.util.ArrayDeque

/**
 * Keeps the latest independently decodable GOP for rebuilding a decoder in the same stream.
 *
 * The codec configuration is retained separately from the GOP. A GOP is either complete from
 * its key frame or unavailable: exceeding the byte budget invalidates the whole GOP instead of
 * evicting its key frame and replaying an undecodable tail.
 */
internal class VideoDecoderBootstrapCache(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    private var latestConfig: VideoDecoderBootstrapPacket? = null
    private val frames = ArrayDeque<VideoDecoderBootstrapPacket>()
    private var frameBytes = 0
    private var replayable = false

    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    fun record(
        data: ByteArray,
        ptsUs: Long,
        isConfig: Boolean,
        isKeyFrame: Boolean,
    ) {
        val packet =
            VideoDecoderBootstrapPacket(
                data = data.copyOf(),
                ptsUs = ptsUs,
                isConfig = isConfig,
                isKeyFrame = isKeyFrame,
            )

        if (isConfig) {
            latestConfig = packet
            resetFrames()
            return
        }

        if (isKeyFrame) {
            resetFrames()
            if (packet.data.size <= availableFrameBytes()) {
                frames.addLast(packet)
                frameBytes = packet.data.size
                replayable = true
            }
            return
        }

        if (!replayable) return
        if (frameBytes + packet.data.size > availableFrameBytes()) {
            resetFrames()
            return
        }
        frames.addLast(packet)
        frameBytes += packet.data.size
    }

    fun snapshot(): VideoDecoderBootstrapSnapshot =
        VideoDecoderBootstrapSnapshot(
            config = latestConfig?.copyPacket(),
            frames = if (replayable) frames.map { it.copyPacket() } else emptyList(),
        )

    fun resetFrames() {
        frames.clear()
        frameBytes = 0
        replayable = false
    }

    private fun availableFrameBytes(): Int = maxBytes - (latestConfig?.data?.size ?: 0)

    private fun VideoDecoderBootstrapPacket.copyPacket(): VideoDecoderBootstrapPacket =
        copy(data = data.copyOf())

    private companion object {
        const val DEFAULT_MAX_BYTES = 16 * 1024 * 1024
    }
}

internal data class VideoDecoderBootstrapSnapshot(
    val config: VideoDecoderBootstrapPacket?,
    val frames: List<VideoDecoderBootstrapPacket>,
) {
    val isReplayable: Boolean
        get() = frames.firstOrNull()?.isKeyFrame == true
}

internal data class VideoDecoderBootstrapPacket(
    val data: ByteArray,
    val ptsUs: Long,
    val isConfig: Boolean,
    val isKeyFrame: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VideoDecoderBootstrapPacket) return false

        return data.contentEquals(other.data) &&
            ptsUs == other.ptsUs &&
            isConfig == other.isConfig &&
            isKeyFrame == other.isKeyFrame
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + ptsUs.hashCode()
        result = 31 * result + isConfig.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        return result
    }
}

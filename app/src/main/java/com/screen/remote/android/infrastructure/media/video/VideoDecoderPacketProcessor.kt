package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import java.nio.ByteBuffer

internal class VideoDecoderPacketProcessor(
    internal val videoCodec: String,
    private val runtimeState: VideoDecoderRuntimeState,
    private val surfaceController: VideoDecoderSurfaceController,
    private val nalParser: VideoNalParser,
    private val formatHandler: VideoFormatHandler,
    private val getDecoder: () -> MediaCodec?,
    private val setDecoder: (MediaCodec?) -> Unit,
    private val isStopped: () -> Boolean,
    private val drainDecoderOutput: () -> Unit,
    private val onVideoStateChanged: (width: Int, height: Int, rotation: Int) -> Unit,
) {
    private val bootstrapCache = VideoDecoderBootstrapCache()
    private var queuedFrameCount = 0
    private var observedPacketCount = 0
    private var consecutiveInputDrops = 0
    private var waitingForKeyFrameAfterFallback = false
    private var decoderConfigured = false
    private var lastH264Sps: ByteArray? = null
    private var lastH264Pps: ByteArray? = null
    private var pendingH264Sps: ByteArray? = null
    private var pendingH264Pps: ByteArray? = null
    private var lastH265Vps: ByteArray? = null
    private var lastH265Sps: ByteArray? = null
    private var lastH265Pps: ByteArray? = null
    private var pendingH265Vps: ByteArray? = null
    private var pendingH265Sps: ByteArray? = null
    private var pendingH265Pps: ByteArray? = null
    private var lastAv1Config: ByteArray? = null
    private var lastAv1ConfigPts = 0L

    fun handleSessionInfo(
        width: Int,
        height: Int,
        configured: Boolean,
    ): Boolean {
        if (width == runtimeState.currentWidth && height == runtimeState.currentHeight) return configured
        bootstrapCache.resetFrames()
        onVideoStateChanged(width, height, runtimeState.currentRotation)
        surfaceController.resizeDummySurface(width, height)
        if (!configured) return false

        val newDecoder =
            when (videoPacketCodecMode(videoCodec)) {
                VideoPacketCodecMode.AV1 ->
                    formatHandler.reconfigureAV1(
                        getDecoder(), width, height,
                        surfaceController.currentSurface(), surfaceController.currentDummySurface(),
                    )
                VideoPacketCodecMode.VPX ->
                    formatHandler.reconfigureVpx(
                        getDecoder(), width, height,
                        surfaceController.currentSurface(), surfaceController.currentDummySurface(),
                    )
                VideoPacketCodecMode.H264, VideoPacketCodecMode.H265 -> return true
                VideoPacketCodecMode.UNSUPPORTED ->
                    throw VideoDecoderConfigurationException(videoCodec, "不支持动态尺寸变化")
            } ?: throw VideoDecoderConfigurationException(videoCodec, "动态尺寸重配失败: ${width}x$height")
        setDecoder(newDecoder)
        decoderConfigured = true
        surfaceController.applyPendingSurface(newDecoder, isStopped())
        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "$videoCodec has been reconfigured to ${width}x$height according to session meta." }
        return true
    }

    fun processStdOutPacket(
        payload: ByteArray,
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
        packetIsConfig: Boolean,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        if (payload.isEmpty()) {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Empty video packet received: configured=${configured || decoderConfigured}" }
            return configured || decoderConfigured
        }

        val effectiveConfigured = configured || decoderConfigured
        observedPacketCount++
        bootstrapCache.record(payload, pts, packetIsConfig, packetIsKeyFrame)

        if (effectiveConfigured && !packetIsConfig) {
            if (waitingForKeyFrameAfterFallback && !packetIsKeyFrame) {
                return true
            }
            if (packetIsKeyFrame) {
                waitingForKeyFrameAfterFallback = false
            }
            decodeFrame(payload, pts, packetIsKeyFrame)
            return true
        }

        if (payload.size > nalBuffer.remaining()) {
            throw VideoDecoderConfigurationException(
                videoCodec,
                "编码包超过聚合缓冲区: payload=${payload.size}, remaining=${nalBuffer.remaining()}",
            )
        }
        nalBuffer.put(payload)

        val result =
            when (videoPacketCodecMode(videoCodec)) {
                VideoPacketCodecMode.H264 -> drainH264(nalBuffer, effectiveConfigured, frameCount, pts, packetIsKeyFrame)
                VideoPacketCodecMode.H265 -> drainH265(nalBuffer, effectiveConfigured, frameCount, pts, packetIsKeyFrame)
                VideoPacketCodecMode.AV1 ->
                    processAV1(nalBuffer, effectiveConfigured, pts, packetIsConfig, packetIsKeyFrame)
                VideoPacketCodecMode.VPX ->
                    processVpx(nalBuffer, effectiveConfigured, pts, packetIsConfig, packetIsKeyFrame)
                VideoPacketCodecMode.UNSUPPORTED ->
                    throw VideoDecoderConfigurationException(videoCodec, "不支持的视频格式")
            }
        decoderConfigured = decoderConfigured || result
        return decoderConfigured
    }

    fun recoverAfterRuntimeFailure(cause: IllegalStateException): Boolean {
        val oldDecoder = getDecoder() ?: return false
        val mode = videoPacketCodecMode(videoCodec)
        if (mode == VideoPacketCodecMode.H264 && (lastH264Sps == null || lastH264Pps == null)) return false
        if (mode == VideoPacketCodecMode.H265 &&
            (lastH265Vps == null || lastH265Sps == null || lastH265Pps == null)
        ) {
            return false
        }
        if (mode == VideoPacketCodecMode.AV1 && lastAv1Config == null) return false
        if (!formatHandler.prepareRuntimeFallback(oldDecoder, cause)) return false

        val newDecoder =
            when (mode) {
                VideoPacketCodecMode.H264 ->
                    formatHandler.reconfigureH264(
                        oldDecoder = oldDecoder,
                        width = runtimeState.currentWidth,
                        height = runtimeState.currentHeight,
                        sps = requireNotNull(lastH264Sps),
                        pps = requireNotNull(lastH264Pps),
                        surface = surfaceController.currentSurface(),
                        dummySurface = surfaceController.currentDummySurface(),
                    )
                VideoPacketCodecMode.H265 ->
                    formatHandler.reconfigureH265(
                        oldDecoder = oldDecoder,
                        width = runtimeState.currentWidth,
                        height = runtimeState.currentHeight,
                        vps = requireNotNull(lastH265Vps),
                        sps = requireNotNull(lastH265Sps),
                        pps = requireNotNull(lastH265Pps),
                        surface = surfaceController.currentSurface(),
                        dummySurface = surfaceController.currentDummySurface(),
                    )
                VideoPacketCodecMode.AV1 ->
                    formatHandler.reconfigureAV1(
                        oldDecoder,
                        runtimeState.currentWidth,
                        runtimeState.currentHeight,
                        surfaceController.currentSurface(),
                        surfaceController.currentDummySurface(),
                    )
                VideoPacketCodecMode.VPX ->
                    formatHandler.reconfigureVpx(
                        oldDecoder,
                        runtimeState.currentWidth,
                        runtimeState.currentHeight,
                        surfaceController.currentSurface(),
                        surfaceController.currentDummySurface(),
                    )
                VideoPacketCodecMode.UNSUPPORTED -> null
            } ?: return false

        setDecoder(newDecoder)
        decoderConfigured = true
        consecutiveInputDrops = 0
        if (mode == VideoPacketCodecMode.AV1) {
            if (!decodeFrame(
                    frameData = requireNotNull(lastAv1Config),
                    pts = lastAv1ConfigPts,
                    isKeyFrame = false,
                    isCodecConfig = true,
                )
            ) {
                throw IllegalStateException("AV1 fallback 解码器无法接收缓存配置")
            }
        }
        surfaceController.applyPendingSurface(newDecoder, isStopped())
        replayBootstrapAfterFallback(cause)
        return true
    }

    fun queuedFrameCount(): Int = queuedFrameCount

    private fun replayBootstrapAfterFallback(cause: IllegalStateException) {
        val snapshot = bootstrapCache.snapshot()
        if (!snapshot.isReplayable) {
            waitingForKeyFrameAfterFallback = true
            LogManager.w(
                LogTags.VIDEO_DECODER,
                "Video decoder failed, switched candidates and waiting for a key frame: ${cause.message}",
            )
            return
        }

        for ((index, packet) in snapshot.frames.withIndex()) {
            if (!decodeFrame(packet.data, packet.ptsUs, packet.isKeyFrame)) {
                waitingForKeyFrameAfterFallback = true
                LogManager.w(
                    LogTags.VIDEO_DECODER,
                    "Decoder bootstrap replay stalled at packet ${index + 1}/${snapshot.frames.size}; waiting for a key frame",
                )
                return
            }
            drainDecoderOutput()
        }

        waitingForKeyFrameAfterFallback = false
        LogManager.w(
            LogTags.VIDEO_DECODER,
            "Video decoder failed, switched candidates and replayed ${snapshot.frames.size} cached GOP packets: ${cause.message}",
        )
    }

    private fun drainH264(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        var currentConfigured = configured
        while (nalBuffer.position() >= 4) {
            val beforePosition = nalBuffer.position()
            val updated = processH264(nalBuffer, currentConfigured, frameCount, pts, packetIsKeyFrame)
            currentConfigured = currentConfigured || updated
            if (nalBuffer.position() == 0 || (updated == currentConfigured && nalBuffer.position() == beforePosition)) {
                break
            }
        }
        return currentConfigured
    }

    private fun drainH265(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        var currentConfigured = configured
        while (nalBuffer.position() >= 4) {
            val beforePosition = nalBuffer.position()
            val updated = processH265(nalBuffer, currentConfigured, frameCount, pts, packetIsKeyFrame)
            currentConfigured = currentConfigured || updated
            if (nalBuffer.position() == 0 || (updated == currentConfigured && nalBuffer.position() == beforePosition)) {
                break
            }
        }
        return currentConfigured
    }

    private fun processH264(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        val nalUnit = nalParser.extractNalUnit(nalBuffer) ?: return configured
        val nalType = nalParser.getH264NalType(nalUnit)

        return when (nalType) {
            VideoNalParser.H264_NAL_SPS -> {
                pendingH264Sps = nalUnit.copyOf()
                configureH264IfReady(configured)
            }
            VideoNalParser.H264_NAL_PPS -> {
                pendingH264Pps = nalUnit.copyOf()
                configureH264IfReady(configured)
            }
            else -> if (configured) {
                val isKeyFrame = packetIsKeyFrame || nalParser.isH264KeyFrame(nalType)
                decodeFrame(nalUnit, pts, isKeyFrame)
                true
            } else configured
        }
    }

    private fun configureH264IfReady(configured: Boolean): Boolean {
        val sps = pendingH264Sps ?: return configured
        val pps = pendingH264Pps ?: return configured
        pendingH264Sps = null
        pendingH264Pps = null
        val sameConfig = lastH264Sps?.contentEquals(sps) == true && lastH264Pps?.contentEquals(pps) == true
        if (configured && sameConfig) return true

        val newDecoder =
            if (configured) {
                formatHandler.reconfigureH264(
                    getDecoder(), runtimeState.currentWidth, runtimeState.currentHeight, sps, pps,
                    surfaceController.currentSurface(), surfaceController.currentDummySurface(),
                )
            } else {
                val decoder = getDecoder() ?: throw VideoDecoderConfigurationException("H.264", "解码器不存在")
                formatHandler.configureH264(
                    decoder, runtimeState.currentWidth, runtimeState.currentHeight, sps, pps,
                    surfaceController.currentSurface(), surfaceController.currentDummySurface(),
                )
            } ?: throw VideoDecoderConfigurationException("H.264", "无法配置解码器")
        setDecoder(newDecoder)
        decoderConfigured = true
        lastH264Sps = sps
        lastH264Pps = pps
        surfaceController.applyPendingSurface(newDecoder, isStopped())
        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "H264 CSD has been accumulated and configured: sps=${sps.size} pps=${pps.size}" }
        return true
    }

    private fun processH265(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        val nalUnit = nalParser.extractNalUnit(nalBuffer) ?: return configured
        val nalType = nalParser.getH265NalType(nalUnit)

        return when (nalType) {
            VideoNalParser.H265_NAL_VPS -> {
                pendingH265Vps = nalUnit.copyOf()
                configureH265IfReady(configured)
            }
            VideoNalParser.H265_NAL_SPS -> {
                pendingH265Sps = nalUnit.copyOf()
                configureH265IfReady(configured)
            }
            VideoNalParser.H265_NAL_PPS -> {
                pendingH265Pps = nalUnit.copyOf()
                configureH265IfReady(configured)
            }
            else -> if (configured) {
                val isKeyFrame = packetIsKeyFrame || nalParser.isH265KeyFrame(nalType)
                decodeFrame(nalUnit, pts, isKeyFrame)
                true
            } else configured
        }
    }

    private fun configureH265IfReady(configured: Boolean): Boolean {
        val vps = pendingH265Vps ?: return configured
        val sps = pendingH265Sps ?: return configured
        val pps = pendingH265Pps ?: return configured
        pendingH265Vps = null
        pendingH265Sps = null
        pendingH265Pps = null
        val sameConfig =
            lastH265Vps?.contentEquals(vps) == true &&
                lastH265Sps?.contentEquals(sps) == true &&
                lastH265Pps?.contentEquals(pps) == true
        if (configured && sameConfig) return true

        val newDecoder =
            if (configured) {
                formatHandler.reconfigureH265(
                    getDecoder(), runtimeState.currentWidth, runtimeState.currentHeight, vps, sps, pps,
                    surfaceController.currentSurface(), surfaceController.currentDummySurface(),
                )
            } else {
                val decoder = getDecoder() ?: throw VideoDecoderConfigurationException("H.265", "解码器不存在")
                formatHandler.configureH265(
                    decoder, runtimeState.currentWidth, runtimeState.currentHeight, vps, sps, pps,
                    surfaceController.currentSurface(), surfaceController.currentDummySurface(),
                )
            } ?: throw VideoDecoderConfigurationException("H.265", "无法配置解码器")
        setDecoder(newDecoder)
        decoderConfigured = true
        lastH265Vps = vps
        lastH265Sps = sps
        lastH265Pps = pps
        surfaceController.applyPendingSurface(newDecoder, isStopped())
        VideoDebugLog.d(LogTags.VIDEO_DECODER) {
            "H265 CSD 已累积并完成配置: vps=${vps.size} sps=${sps.size} pps=${pps.size}"
        }
        return true
    }

    private fun processAV1(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        pts: Long,
        packetIsConfig: Boolean,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        if (nalBuffer.position() <= 0) {
            return configured
        }

        nalBuffer.flip()
        val frameData = ByteArray(nalBuffer.remaining())
        nalBuffer.get(frameData)
        nalBuffer.clear()

        if (packetIsConfig) {
            lastAv1Config = frameData.copyOf()
            lastAv1ConfigPts = pts
        }

        if (!configured) {
            val newDecoder = getDecoder()?.let {
                formatHandler.configureAV1(
                    it,
                    runtimeState.currentWidth,
                    runtimeState.currentHeight,
                    surfaceController.currentSurface(),
                    surfaceController.currentDummySurface(),
                )
            }
            setDecoder(newDecoder)
            decoderConfigured = true
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "AV1 decoder has been configured" }
            surfaceController.applyPendingSurface(newDecoder, isStopped())
        }

        decodeFrame(frameData, pts, packetIsKeyFrame, packetIsConfig)
        return true
    }

    private fun processVpx(
        frameBuffer: ByteBuffer,
        configured: Boolean,
        pts: Long,
        packetIsConfig: Boolean,
        packetIsKeyFrame: Boolean,
    ): Boolean {
        if (frameBuffer.position() <= 0) return configured

        frameBuffer.flip()
        val frameData = ByteArray(frameBuffer.remaining())
        frameBuffer.get(frameData)
        frameBuffer.clear()

        if (!configured) {
            val newDecoder = getDecoder()?.let {
                formatHandler.configureVpx(
                    it,
                    runtimeState.currentWidth,
                    runtimeState.currentHeight,
                    surfaceController.currentSurface(),
                    surfaceController.currentDummySurface(),
                )
            }
            setDecoder(newDecoder)
            decoderConfigured = true
            surfaceController.applyPendingSurface(newDecoder, isStopped())
        }

        decodeFrame(frameData, pts, packetIsKeyFrame, packetIsConfig)
        return true
    }

    private fun decodeFrame(
        frameData: ByteArray,
        pts: Long,
        isKeyFrame: Boolean,
        isCodecConfig: Boolean = false,
    ): Boolean {
        val decoder = getDecoder()
        if (isStopped() || decoder == null) {
            return false
        }

        try {
            val isCritical = isKeyFrame || isCodecConfig
            var inputIndex = decoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex < 0 && isCritical) {
                drainDecoderOutput()
                inputIndex = decoder.dequeueInputBuffer(CRITICAL_INPUT_TIMEOUT_US)
            }
            if (inputIndex < 0) {
                consecutiveInputDrops++
                if (isCritical || consecutiveInputDrops >= MAX_CONSECUTIVE_INPUT_DROPS) {
                    throw IllegalStateException(
                        "视频解码器输入持续阻塞: drops=$consecutiveInputDrops keyFrame=$isKeyFrame",
                    )
                }
                return false
            }
            consecutiveInputDrops = 0

            val inputBuffer = decoder.getInputBuffer(inputIndex)
                ?: throw IllegalStateException("解码器未返回输入缓冲区")
            inputBuffer.clear()
            if (frameData.size > inputBuffer.remaining()) {
                throw IllegalStateException(
                    "视频帧超过解码器输入容量: frame=${frameData.size}, capacity=${inputBuffer.remaining()}",
                )
            }
            inputBuffer.put(frameData)

            val flags =
                (if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0) or
                    (if (isCodecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
            decoder.queueInputBuffer(inputIndex, 0, frameData.size, mediaCodecPresentationTimeUs(pts), flags)
            if (!isCodecConfig) {
                queuedFrameCount++
            }
            return true
        } catch (e: Exception) {
            if (isStopped()) return false
            LogManager.e(LogTags.VIDEO_DECODER, "Decoding frame failed: ${e.message}", e)
            throw e
        }
    }

    private companion object {
        const val INPUT_TIMEOUT_US = 10_000L
        const val CRITICAL_INPUT_TIMEOUT_US = 50_000L
        const val MAX_CONSECUTIVE_INPUT_DROPS = 30
    }
}

internal fun mediaCodecPresentationTimeUs(scrcpyPtsUs: Long): Long = scrcpyPtsUs

internal enum class VideoPacketCodecMode {
    H264,
    H265,
    AV1,
    VPX,
    UNSUPPORTED,
}

internal fun videoPacketCodecMode(codec: String): VideoPacketCodecMode =
    when (codec.trim().lowercase()) {
        "h264", "avc" -> VideoPacketCodecMode.H264
        "h265", "hevc" -> VideoPacketCodecMode.H265
        "av1" -> VideoPacketCodecMode.AV1
        "vp8", "vp9" -> VideoPacketCodecMode.VPX
        else -> VideoPacketCodecMode.UNSUPPORTED
    }

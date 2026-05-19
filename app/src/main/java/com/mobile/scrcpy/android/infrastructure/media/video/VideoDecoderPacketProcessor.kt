package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
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
    private val onVideoStateChanged: (width: Int, height: Int, rotation: Int) -> Unit,
) {
    private var queuedFrameCount = 0
    private var queuedConfigCount = 0
    private var observedPacketCount = 0
    private var decoderConfigured = false
    private var lastH264Sps: ByteArray? = null
    private var lastH264Pps: ByteArray? = null
    private var lastH265Vps: ByteArray? = null
    private var lastH265Sps: ByteArray? = null
    private var lastH265Pps: ByteArray? = null

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
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "收到空视频包: configured=${configured || decoderConfigured}" }
            return configured || decoderConfigured
        }

        val effectiveConfigured = configured || decoderConfigured
        observedPacketCount++
        if (observedPacketCount <= 12 || observedPacketCount % 60 == 0) {
            val preview = payload.take(minOf(16, payload.size)).joinToString(" ") { "%02X".format(it) }
            VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                "收到视频包 #$observedPacketCount: size=${payload.size} configured=$effectiveConfigured config=$packetIsConfig key=$packetIsKeyFrame data=$preview"
            }
        }

        if (payload.size in VideoNalParser.FRAME_META_MIN_SIZE..VideoNalParser.FRAME_META_MAX_SIZE &&
            !nalParser.isNalStartCode(payload)
        ) {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "收到 frame meta: size=${payload.size}" }
            handleFrameMeta(payload)
            return effectiveConfigured
        }

        if (effectiveConfigured && !packetIsConfig) {
            decodeFrame(payload, pts, packetIsKeyFrame)
            return true
        }

        nalBuffer.put(payload)
        if (observedPacketCount <= 12 || observedPacketCount % 60 == 0) {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                "缓存 Annex-B 数据: size=${payload.size} bufferPosition=${nalBuffer.position()} codec=$videoCodec"
            }
        }

        val result =
            when (videoCodec.lowercase()) {
                "h264" -> drainH264(nalBuffer, effectiveConfigured, frameCount, pts, packetIsKeyFrame)
                "h265", "hevc" -> drainH265(nalBuffer, effectiveConfigured, frameCount, pts, packetIsKeyFrame)
                "av1" -> processAV1(nalBuffer, effectiveConfigured, pts)
                else -> effectiveConfigured
            }
        decoderConfigured = decoderConfigured || result
        return decoderConfigured
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

        return when {
            nalType == VideoNalParser.H264_NAL_SPS -> {
                val ppsNal = nalParser.extractNalUnit(nalBuffer)
                if (ppsNal != null && nalParser.getH264NalType(ppsNal) == VideoNalParser.H264_NAL_PPS) {
                    val sameConfig =
                        lastH264Sps?.contentEquals(nalUnit) == true &&
                            lastH264Pps?.contentEquals(ppsNal) == true
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                        "检测到 H264 配置: sps=${nalUnit.size} pps=${ppsNal.size} configured=$configured same=$sameConfig"
                    }
                    if (configured && sameConfig) {
                        return true
                    }
                    val newDecoder = if (configured) {
                        formatHandler.reconfigureH264(
                            getDecoder(),
                            runtimeState.currentWidth,
                            runtimeState.currentHeight,
                            nalUnit,
                            ppsNal,
                            surfaceController.currentSurface(),
                            surfaceController.currentDummySurface(),
                        )
                    } else {
                        getDecoder()?.let {
                            formatHandler.configureH264(
                                it,
                                runtimeState.currentWidth,
                                runtimeState.currentHeight,
                                nalUnit,
                                ppsNal,
                                surfaceController.currentSurface(),
                                surfaceController.currentDummySurface(),
                            )
                            it // Return the configured decoder
                        }
                    }
                    setDecoder(newDecoder) // Ensure the new decoder is set
                    decoderConfigured = true
                    lastH264Sps = nalUnit.copyOf()
                    lastH264Pps = ppsNal.copyOf()
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "H264 解码器已完成配置" }
                    surfaceController.applyPendingSurface(newDecoder, isStopped())
                    newDecoder?.let {
                        queueConfigNalUnit(it, nalUnit, pts)
                        queueConfigNalUnit(it, ppsNal, pts)
                    }
                    true
                } else {
                    configured
                }
            }

            configured && nalType != VideoNalParser.H264_NAL_PPS -> {
                val isKeyFrame = packetIsKeyFrame || nalParser.isH264KeyFrame(nalType)
                if (isKeyFrame) {
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "🎯 收到关键帧 (IDR) #$frameCount" }
                }
                decodeFrame(nalUnit, pts, isKeyFrame)
                configured
            }

            else -> configured
        }
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

        return when {
            nalType == VideoNalParser.H265_NAL_VPS -> {
                val spsNal = nalParser.extractNalUnit(nalBuffer)
                val ppsNal = nalParser.extractNalUnit(nalBuffer)
                if (spsNal != null && ppsNal != null) {
                    val sameConfig =
                        lastH265Vps?.contentEquals(nalUnit) == true &&
                            lastH265Sps?.contentEquals(spsNal) == true &&
                            lastH265Pps?.contentEquals(ppsNal) == true
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                        "检测到 H265 配置: vps=${nalUnit.size} sps=${spsNal.size} pps=${ppsNal.size} configured=$configured same=$sameConfig"
                    }
                    if (configured && sameConfig) {
                        return true
                    }
                    val newDecoder = if (configured) {
                        formatHandler.reconfigureH265(
                            getDecoder(),
                            runtimeState.currentWidth,
                            runtimeState.currentHeight,
                            nalUnit,
                            spsNal,
                            ppsNal,
                            surfaceController.currentSurface(),
                            surfaceController.currentDummySurface(),
                        )
                    } else {
                        getDecoder()?.let {
                            formatHandler.configureH265(
                                it,
                                runtimeState.currentWidth,
                                runtimeState.currentHeight,
                                nalUnit,
                                spsNal,
                                ppsNal,
                                surfaceController.currentSurface(),
                                surfaceController.currentDummySurface(),
                            )
                            it // Return the configured decoder
                        }
                    }
                    setDecoder(newDecoder) // Ensure the new decoder is set
                    decoderConfigured = true
                    lastH265Vps = nalUnit.copyOf()
                    lastH265Sps = spsNal.copyOf()
                    lastH265Pps = ppsNal.copyOf()
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "H265 解码器已完成配置" }
                    surfaceController.applyPendingSurface(newDecoder, isStopped())
                    newDecoder?.let {
                        queueConfigNalUnit(it, nalUnit, pts)
                        queueConfigNalUnit(it, spsNal, pts)
                        queueConfigNalUnit(it, ppsNal, pts)
                    }
                    true
                } else {
                    configured
                }
            }

            configured && nalType !in listOf(VideoNalParser.H265_NAL_SPS, VideoNalParser.H265_NAL_PPS) -> {
                val isKeyFrame = packetIsKeyFrame || nalParser.isH265KeyFrame(nalType)
                if (isKeyFrame) {
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "🎯 收到关键帧 (H265 IDR) #$frameCount" }
                }
                decodeFrame(nalUnit, pts, isKeyFrame)
                configured
            }

            else -> configured
        }
    }

    private fun processAV1(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        pts: Long,
    ): Boolean {
        if (nalBuffer.position() <= 0) {
            return configured
        }

        nalBuffer.flip()
        val frameData = ByteArray(nalBuffer.remaining())
        nalBuffer.get(frameData)
        nalBuffer.clear()

        if (!configured) {
            val newDecoder =
                formatHandler.reconfigureAV1(
                    getDecoder(),
                    runtimeState.currentWidth,
                    runtimeState.currentHeight,
                    surfaceController.currentSurface(),
                    surfaceController.currentDummySurface(),
                )
            setDecoder(newDecoder)
            decoderConfigured = true
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "AV1 解码器已完成配置" }
            surfaceController.applyPendingSurface(newDecoder, isStopped())
            return true
        }

        decodeFrame(frameData, pts, false)
        return configured
    }

    private fun handleFrameMeta(data: ByteArray) {
        nalParser.parseFrameMeta(data)?.let { (width, height, rotation) ->
            if (width != runtimeState.currentWidth ||
                height != runtimeState.currentHeight ||
                rotation != runtimeState.currentRotation
            ) {
                VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                    "视频参数变化: ${runtimeState.currentWidth}x${runtimeState.currentHeight}@${runtimeState.currentRotation}° -> ${width}x$height@$rotation°"
                }
                onVideoStateChanged(width, height, rotation)
            }
        }
    }

    private fun decodeFrame(
        frameData: ByteArray,
        pts: Long,
        isKeyFrame: Boolean,
    ) {
        val decoder = getDecoder()
        if (isStopped() || decoder == null) {
            return
        }

        try {
            val timeoutUs = if (isKeyFrame) CRITICAL_INPUT_TIMEOUT_US else INPUT_TIMEOUT_US
            val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
            if (inputIndex < 0) {
                return
            }

            val inputBuffer = decoder.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(frameData)

            val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            decoder.queueInputBuffer(inputIndex, 0, frameData.size, pts / 1000, flags)
            queuedFrameCount++
            if (queuedFrameCount <= 8 || queuedFrameCount % 60 == 0) {
                VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                    "已送入视频帧 #$queuedFrameCount: size=${frameData.size} key=$isKeyFrame ptsUs=$pts"
                }
            }
        } catch (e: IllegalStateException) {
            if (!isStopped()) {
                LogManager.w(LogTags.VIDEO_DECODER, "解码器状态异常: ${e.message}")
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解码帧失败: ${e.message}", e)
        }
    }

    private fun queueConfigNalUnit(
        decoder: MediaCodec,
        nalUnit: ByteArray,
        pts: Long,
    ) {
        try {
            val inputIndex = decoder.dequeueInputBuffer(0)
            if (inputIndex < 0) {
                LogManager.w(LogTags.VIDEO_DECODER, "Config NAL unit queue failed: no input buffer")
                return
            }

            val inputBuffer = decoder.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(nalUnit)
            decoder.queueInputBuffer(inputIndex, 0, nalUnit.size, pts / 1000, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
            queuedConfigCount++
            VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                "已送入配置 NAL #$queuedConfigCount: size=${nalUnit.size} ptsUs=$pts"
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to queue config NAL unit: ${e.message}", e)
        }
    }

    private companion object {
        const val INPUT_TIMEOUT_US = 10_000L
        const val CRITICAL_INPUT_TIMEOUT_US = 50_000L
    }

}

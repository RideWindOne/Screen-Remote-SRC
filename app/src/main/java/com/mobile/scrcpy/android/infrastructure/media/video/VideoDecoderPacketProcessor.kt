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
    fun processStdOutPacket(
        payload: ByteArray,
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
    ): Boolean {
        if (payload.isEmpty()) {
            return configured
        }

        if (payload.size in VideoNalParser.FRAME_META_MIN_SIZE..VideoNalParser.FRAME_META_MAX_SIZE &&
            !nalParser.isNalStartCode(payload)
        ) {
            handleFrameMeta(payload)
            return configured
        }

        nalBuffer.put(payload)

        return when (videoCodec.lowercase()) {
            "h264" -> processH264(nalBuffer, configured, frameCount, pts)
            "h265", "hevc" -> processH265(nalBuffer, configured, frameCount, pts)
            "av1" -> processAV1(nalBuffer, configured, pts)
            else -> configured
        }
    }

    private fun processH264(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
    ): Boolean {
        val nalUnit = nalParser.extractNalUnit(nalBuffer) ?: return configured
        val nalType = nalParser.getH264NalType(nalUnit)

        return when {
            nalType == VideoNalParser.H264_NAL_SPS -> {
                val ppsNal = nalParser.extractNalUnit(nalBuffer)
                if (ppsNal != null && nalParser.getH264NalType(ppsNal) == VideoNalParser.H264_NAL_PPS) {
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

                    // Queue SPS and PPS as config data
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
                val isKeyFrame = nalParser.isH264KeyFrame(nalType)
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
    ): Boolean {
        val nalUnit = nalParser.extractNalUnit(nalBuffer) ?: return configured
        val nalType = nalParser.getH265NalType(nalUnit)

        return when {
            nalType == VideoNalParser.H265_NAL_VPS -> {
                val spsNal = nalParser.extractNalUnit(nalBuffer)
                val ppsNal = nalParser.extractNalUnit(nalBuffer)
                if (spsNal != null && ppsNal != null) {
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

                    // Queue VPS, SPS, and PPS as config data
                    newDecoder?.let {
                        queueConfigNalUnit(it, nalUnit, pts) // VPS
                        queueConfigNalUnit(it, spsNal, pts) // SPS
                        queueConfigNalUnit(it, ppsNal, pts) // PPS
                    }
                    true
                } else {
                    configured
                }
            }

            configured && nalType !in listOf(VideoNalParser.H265_NAL_SPS, VideoNalParser.H265_NAL_PPS) -> {
                val isKeyFrame = nalParser.isH265KeyFrame(nalType)
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
            setDecoder(
                formatHandler.reconfigureAV1(
                    getDecoder(),
                    runtimeState.currentWidth,
                    runtimeState.currentHeight,
                    surfaceController.currentSurface(),
                    surfaceController.currentDummySurface(),
                ),
            )
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
            val inputIndex = decoder.dequeueInputBuffer(0)
            if (inputIndex < 0) {
                return
            }

            val inputBuffer = decoder.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(frameData)

            val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            decoder.queueInputBuffer(inputIndex, 0, frameData.size, pts / 1000, flags)
        } catch (e: IllegalStateException) {
            if (!isStopped()) {
                LogManager.w(LogTags.VIDEO_DECODER, "解码器状态异常: ${e.message}")
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解码帧失败: ${e.message}", e)
        }
    }

    private fun queueConfigNalUnit(decoder: MediaCodec, nalUnit: ByteArray, pts: Long) {
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
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Queued config NAL unit (size=${nalUnit.size})" }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to queue config NAL unit: ${e.message}", e)
        }
    }
}


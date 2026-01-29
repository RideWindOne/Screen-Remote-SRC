package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import android.view.Surface
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * VideoDecoder - 视频解码器（重构版）
 * 职责：解码流程控制、Surface 管理、生命周期管理
 */
class VideoDecoder(
    private var surface: Surface?,
    private val videoCodec: String = "h264",
    cachedDecoderName: String? = null,
) {
    private var decoder: MediaCodec? = null
    private var isRunning = false
    private var isStopped = false
    private val surfaceLock = Any()
    private var isSurfaceBound = false

    // Dummy Surface 用于后台占位
    private var dummySurface: Surface? = null
    private var dummySurfaceTexture: android.graphics.SurfaceTexture? = null

    // 组件
    private val codecManager = VideoCodecManager(videoCodec, cachedDecoderName)
    private val nalParser = VideoNalParser()
    private val formatHandler = VideoFormatHandler(codecManager)

    // 回调
    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)? = null
        set(value) {
            field = value
            formatHandler.onVideoSizeChanged = value
        }
    var onDecoderSelected: ((decoderName: String) -> Unit)? = null
        set(value) {
            field = value
            codecManager.onDecoderSelected = value
        }
    var onConnectionLost: (() -> Unit)? = null

    private var currentWidth = 0
    private var currentHeight = 0
    private var currentRotation = 0

    private companion object {
        const val BUFFER_SIZE = 10 * 1024 * 1024
        const val FRAME_DURATION_US = 33333L
    }

    suspend fun start(
        videoStream: VideoStream,
        width: Int,
        height: Int,
    ) = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "开始解码 $videoCodec: ${width}x$height")

            createDummySurface()

            isStopped = false
            currentWidth = width
            currentHeight = height
            currentRotation = 0
            onVideoSizeChanged?.invoke(width, height, 0)

            decoder = codecManager.createDecoder(width, height) ?: run {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建解码器")
                return@withContext
            }
            LogManager.d(LogTags.VIDEO_DECODER, "解码器: ${decoder?.name}")

            isRunning = true
            decodeLoop(videoStream)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解码失败: ${e.message}", e)
        } finally {
            stop()
        }
    }

    fun stop() {
        if (isStopped) {
            LogManager.d(LogTags.VIDEO_DECODER, "解码器已停止，跳过")
            return
        }

        isRunning = false
        isStopped = true

        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "停止解码器失败: ${e.message}", e)
        }

        releaseDummySurface()
    }

    /**
     * 创建 dummy Surface（用于后台占位）
     */
    private fun createDummySurface() {
        try {
            dummySurfaceTexture =
                android.graphics.SurfaceTexture(0).apply {
                    setDefaultBufferSize(1, 1)
                }
            dummySurface = Surface(dummySurfaceTexture)
            LogManager.d(LogTags.VIDEO_DECODER, "Dummy Surface 已创建")
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "创建 dummy Surface 失败: ${e.message}")
        }
    }

    /**
     * 释放 dummy Surface
     */
    private fun releaseDummySurface() {
        try {
            dummySurface?.release()
            dummySurface = null
            dummySurfaceTexture?.release()
            dummySurfaceTexture = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "释放 dummy Surface 失败: ${e.message}")
        }
    }

    /**
     * 动态切换 Surface（支持后台时设置为 null）
     */
    fun setSurface(newSurface: Surface?) {
        synchronized(surfaceLock) {
            try {
                val codec = decoder
                if (codec == null || isStopped) {
                    LogManager.w(LogTags.VIDEO_DECODER, "解码器未运行，跳过 Surface 切换")
                    return
                }

                surface = newSurface
                val targetSurface = newSurface ?: dummySurface

                if (targetSurface != null) {
                    codec.setOutputSurface(targetSurface)
                    isSurfaceBound = (newSurface != null)

                    if (newSurface != null) {
                        LogManager.d(LogTags.VIDEO_DECODER, "Surface 已切换（恢复渲染）")
                    } else {
                        LogManager.d(LogTags.VIDEO_DECODER, "已切换到 dummy Surface（后台模式）")
                    }
                } else {
                    LogManager.e(LogTags.VIDEO_DECODER, "无法切换 Surface：dummy Surface 不可用")
                }
            } catch (e: IllegalStateException) {
                LogManager.w(LogTags.VIDEO_DECODER, "切换 Surface 失败（状态异常）: ${e.message}")
            } catch (e: Exception) {
                LogManager.e(LogTags.VIDEO_DECODER, "切换 Surface 失败: ${e.message}", e)
            }
        }
    }

    /**
     * 统一解码循环
     */
    private fun decodeLoop(videoStream: VideoStream) {
        val bufferInfo = MediaCodec.BufferInfo()
        var configured = false
        val nalBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        var frameCount = 0
        var pts = 0L

        LogManager.d(LogTags.VIDEO_DECODER, "解码循环开始: $videoCodec")

        while (isRunning) {
            try {
                if (configured) {
                    drainOutputBuffers(bufferInfo)
                }

                when (val packet = videoStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) continue

                        // 处理 Frame Meta
                        if (packet.payload.size in
                            VideoNalParser.FRAME_META_MIN_SIZE..VideoNalParser.FRAME_META_MAX_SIZE &&
                            !nalParser.isNalStartCode(packet.payload)
                        ) {
                            handleFrameMeta(packet.payload)
                            continue
                        }

                        nalBuffer.put(packet.payload)
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        break
                    }

                    else -> {
                        continue
                    }
                }

                // 根据编码格式处理
                when (videoCodec.lowercase()) {
                    "h264" -> configured = processH264(nalBuffer, configured, frameCount, pts)
                    "h265", "hevc" -> configured = processH265(nalBuffer, configured, frameCount, pts)
                    "av1" -> configured = processAV1(nalBuffer, configured, frameCount, pts)
                }

                if (configured) {
                    frameCount++
                    pts += FRAME_DURATION_US
                }
            } catch (e: Exception) {
                if (isRunning) {
                    handleDecodeError(e)
                }
                break
            }
        }

        LogManager.d(LogTags.VIDEO_DECODER, "解码结束，共 $frameCount 帧")
    }

    /**
     * 处理 H.264 NAL 单元
     */
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
                    if (configured) {
                        decoder =
                            formatHandler.reconfigureH264(
                                decoder,
                                currentWidth,
                                currentHeight,
                                nalUnit,
                                ppsNal,
                                surface,
                                dummySurface,
                            )
                    } else {
                        decoder?.let {
                            formatHandler.configureH264(
                                it,
                                currentWidth,
                                currentHeight,
                                nalUnit,
                                ppsNal,
                                surface,
                                dummySurface,
                            )
                        }
                    }
                    true
                } else {
                    configured
                }
            }

            configured && nalType != VideoNalParser.H264_NAL_PPS -> {
                if (nalParser.isH264KeyFrame(nalType)) {
                    LogManager.d(LogTags.VIDEO_DECODER, "🎯 收到关键帧 (IDR) #$frameCount")
                }
                decodeFrame(nalUnit, pts, nalParser.isH264KeyFrame(nalType))
                configured
            }

            else -> {
                configured
            }
        }
    }

    /**
     * 处理 H.265 NAL 单元
     */
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
                    if (configured) {
                        decoder =
                            formatHandler.reconfigureH265(
                                decoder,
                                currentWidth,
                                currentHeight,
                                nalUnit,
                                spsNal,
                                ppsNal,
                                surface,
                                dummySurface,
                            )
                    } else {
                        decoder?.let {
                            formatHandler.configureH265(
                                it,
                                currentWidth,
                                currentHeight,
                                nalUnit,
                                spsNal,
                                ppsNal,
                                surface,
                                dummySurface,
                            )
                        }
                    }
                    true
                } else {
                    configured
                }
            }

            configured && nalType !in listOf(VideoNalParser.H265_NAL_SPS, VideoNalParser.H265_NAL_PPS) -> {
                if (nalParser.isH265KeyFrame(nalType)) {
                    LogManager.d(LogTags.VIDEO_DECODER, "🎯 收到关键帧 (H265 IDR) #$frameCount")
                }
                decodeFrame(nalUnit, pts, nalParser.isH265KeyFrame(nalType))
                configured
            }

            else -> {
                configured
            }
        }
    }

    /**
     * 处理 AV1 帧
     */
    private fun processAV1(
        nalBuffer: ByteBuffer,
        configured: Boolean,
        frameCount: Int,
        pts: Long,
    ): Boolean {
        if (nalBuffer.position() > 0) {
            nalBuffer.flip()
            val frameData = ByteArray(nalBuffer.remaining())
            nalBuffer.get(frameData)
            nalBuffer.clear()

            if (!configured) {
                decoder =
                    formatHandler.reconfigureAV1(
                        decoder,
                        currentWidth,
                        currentHeight,
                        surface,
                        dummySurface,
                    )
                return true
            } else {
                decodeFrame(frameData, pts, false)
            }
        }
        return configured
    }

    /**
     * 处理 Frame Meta 消息
     */
    private fun handleFrameMeta(data: ByteArray) {
        nalParser.parseFrameMeta(data)?.let { (width, height, rotation) ->
            if (width != currentWidth || height != currentHeight || rotation != currentRotation) {
                LogManager.d(
                    LogTags.VIDEO_DECODER,
                    "视频参数变化: ${currentWidth}x$currentHeight@$currentRotation° -> ${width}x$height@$rotation°",
                )

                currentWidth = width
                currentHeight = height
                currentRotation = rotation

                onVideoSizeChanged?.invoke(width, height, rotation)
            }
        }
    }

    /**
     * 处理解码错误
     */
    private fun handleDecodeError(e: Exception) {
        when {
            e.message?.contains("Stream closed") == true -> {
                LogManager.w(LogTags.VIDEO_DECODER, "视频流已关闭，触发连接丢失处理")
                onConnectionLost?.invoke()
            }

            e.message?.contains("Socket closed") == true -> {
                LogManager.w(LogTags.VIDEO_DECODER, "Socket 已关闭，触发连接丢失处理")
                onConnectionLost?.invoke()
            }

            e.message?.contains("Read timed out") == true -> {
                LogManager.w(LogTags.VIDEO_DECODER, "视频流超时（设备息屏），继续等待...")
            }

            else -> {
                LogManager.e(LogTags.VIDEO_DECODER, "解码错误: ${e.message}", e)
                onConnectionLost?.invoke()
            }
        }
    }

    /**
     * 解码帧
     */
    private fun decodeFrame(
        frameData: ByteArray,
        pts: Long,
        isKeyFrame: Boolean,
    ) {
        if (isStopped || decoder == null) return

        try {
            val inputIndex = decoder?.dequeueInputBuffer(0) ?: -1
            if (inputIndex < 0) return

            val inputBuffer = decoder?.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(frameData)

            val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            decoder?.queueInputBuffer(inputIndex, 0, frameData.size, pts / 1000, flags)
        } catch (e: IllegalStateException) {
            if (!isStopped) {
                LogManager.w(LogTags.VIDEO_DECODER, "解码器状态异常: ${e.message}")
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解码帧失败: ${e.message}", e)
        }
    }

    /**
     * 输出缓冲区处理
     */
    private fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo) {
        if (isStopped) return

        try {
            val codec = decoder ?: return

            try {
                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)

                while (outputIndex >= 0) {
                    // 检查当前 Surface 是否有效
                    val shouldRender =
                        synchronized(surfaceLock) {
                            val currentSurface = surface
                            currentSurface != null && currentSurface.isValid
                        }

                    // 始终释放输出缓冲区，前台渲染，后台丢弃
                    codec.releaseOutputBuffer(outputIndex, shouldRender)

                    // 立即获取下一个缓冲区（不等待）
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    LogManager.d(LogTags.VIDEO_DECODER, "输出格式变化")
                    formatHandler.updateVideoSizeFromOutputFormat(codec.outputFormat)
                }
            } catch (e: IllegalStateException) {
                if (e.message?.contains("Uninitialized") == true ||
                    e.message?.contains("executing state") == true ||
                    e.message?.contains("flush") == true
                ) {
                    return
                }
                throw e
            }
        } catch (e: IllegalStateException) {
            if (!isStopped) {
                LogManager.w(LogTags.VIDEO_DECODER, "输出缓冲区处理异常: ${e.message}")
            }
        } catch (_: Exception) {
            // 忽略其他异常
        }
    }
}

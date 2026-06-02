package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.DemuxerError
import com.screen.remote.android.core.common.event.DeviceDisconnected
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import java.nio.ByteBuffer

internal class VideoDecoderPlayback(
    videoCodec: String,
    runtimeState: VideoDecoderRuntimeState,
    surfaceController: VideoDecoderSurfaceController,
    nalParser: VideoNalParser,
    formatHandler: VideoFormatHandler,
    getDecoder: () -> MediaCodec?,
    setDecoder: (MediaCodec?) -> Unit,
    private val isRunning: () -> Boolean,
    private val isStopped: () -> Boolean,
    private val shouldReportConnectionLost: () -> Boolean,
    onVideoStateChanged: (width: Int, height: Int, rotation: Int) -> Unit,
    private val onConnectionLost: () -> Unit,
) {
    private companion object {
        const val BUFFER_SIZE = 32 * 1024 * 1024
        const val FIRST_OUTPUT_WATCHDOG_INPUT_FRAMES = 120
    }

    private val packetProcessor =
        VideoDecoderPacketProcessor(
            videoCodec = videoCodec,
            runtimeState = runtimeState,
            surfaceController = surfaceController,
            nalParser = nalParser,
            formatHandler = formatHandler,
            getDecoder = getDecoder,
            setDecoder = setDecoder,
            isStopped = isStopped,
            onVideoStateChanged = onVideoStateChanged,
        )
    private val outputDrainer =
        VideoDecoderOutputDrainer(
            surfaceController = surfaceController,
            formatHandler = formatHandler,
            getDecoder = getDecoder,
            isStopped = isStopped,
        )

    fun decodeLoop(videoStream: VideoStream) {
        val bufferInfo = MediaCodec.BufferInfo()
        val nalBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        var configured = false
        var frameCount = 0
        var lastPts = 0L
        var inputsWithoutOutput = 0
        var lastRenderedFrameCount = 0

        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "解码循环开始: ${packetProcessor.videoCodec}" }

        while (isRunning()) {
            try {
                if (configured) {
                    outputDrainer.drainOutputBuffers(bufferInfo)
                    val renderedFrameCount = outputDrainer.renderedFrameCount()
                    if (renderedFrameCount > lastRenderedFrameCount) {
                        lastRenderedFrameCount = renderedFrameCount
                        inputsWithoutOutput = 0
                    }
                }

                val queuedFramesBeforePacket = packetProcessor.queuedFrameCount()
                when (val packet = videoStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        videoStream.consumeSessionInfo()?.let { sessionInfo ->
                            configured =
                                packetProcessor.handleSessionInfo(
                                    width = sessionInfo.width,
                                    height = sessionInfo.height,
                                    configured = configured,
                                )
                        }
                        val frameInfo = videoStream.currentFrameInfo()
                        val packetPts = frameInfo?.pts ?: lastPts
                        frameInfo?.let { lastPts = it.pts }
                        configured =
                            packetProcessor.processStdOutPacket(
                                payload = packet.payload,
                                nalBuffer = nalBuffer,
                                configured = configured,
                                frameCount = frameCount,
                                pts = packetPts,
                                packetIsConfig = frameInfo?.isConfig ?: false,
                                packetIsKeyFrame = frameInfo?.isKeyFrame ?: false,
                            )
                    }

                    is dadb.AdbShellPacket.Exit -> {
                        if (isRunning() && shouldReportConnectionLost()) {
                            LogManager.w(LogTags.VIDEO_DECODER, "视频流由远端结束，触发重连")
                            onConnectionLost()
                            ScrcpyEventBus.pushEvent(DeviceDisconnected)
                        }
                        break
                    }
                    else -> continue
                }

                val queuedFrameDelta = packetProcessor.queuedFrameCount() - queuedFramesBeforePacket
                if (configured && queuedFrameDelta > 0) {
                    frameCount += queuedFrameDelta
                    inputsWithoutOutput += queuedFrameDelta
                    if (inputsWithoutOutput >= FIRST_OUTPUT_WATCHDOG_INPUT_FRAMES) {
                        throw IllegalStateException("解码器持续接收 $inputsWithoutOutput 个视频帧但没有任何新输出")
                    }
                }
            } catch (e: Exception) {
                if (e is VideoDecoderConfigurationException) {
                    throw e
                }
                if (configured && e is IllegalStateException) {
                    val recovered =
                        runCatching { packetProcessor.recoverAfterRuntimeFailure(e) }
                            .onFailure { fallbackError ->
                                LogManager.e(LogTags.VIDEO_DECODER, "视频解码器运行时回退失败: ${fallbackError.message}", fallbackError)
                            }.getOrDefault(false)
                    if (recovered) {
                        outputDrainer.resetAfterDecoderFallback()
                        lastRenderedFrameCount = 0
                        inputsWithoutOutput = 0
                        configured = true
                        continue
                    }
                }
                if (isRunning()) {
                    handleDecodeError(e)
                }
                break
            }
        }

        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "解码结束，共 $frameCount 帧" }
    }

    private fun handleDecodeError(error: Exception) {
        when {
            error.message?.contains("Stream closed") == true -> {
                if (shouldReportConnectionLost()) {
                    LogManager.w(LogTags.VIDEO_DECODER, "视频流已关闭，触发连接丢失处理")
                    onConnectionLost()
                    ScrcpyEventBus.pushEvent(DeviceDisconnected)
                } else {
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "视频流在清理过程中关闭，忽略连接丢失回调" }
                }
            }

            error.message?.contains("Socket closed") == true -> {
                if (shouldReportConnectionLost()) {
                    LogManager.w(LogTags.VIDEO_DECODER, "Socket 已关闭，触发连接丢失处理")
                    onConnectionLost()
                    ScrcpyEventBus.pushEvent(DeviceDisconnected)
                } else {
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "视频 Socket 在清理过程中关闭，忽略连接丢失回调" }
                }
            }

            error.message?.contains("Read timed out") == true -> {
                LogManager.w(LogTags.VIDEO_DECODER, "视频流超时（设备息屏），继续等待...")
            }

            else -> {
                LogManager.e(LogTags.VIDEO_DECODER, "解码错误: ${error.message}", error)
                onConnectionLost()
                ScrcpyEventBus.pushEvent(DemuxerError(error.message ?: "Unknown error"))
            }
        }
    }
}

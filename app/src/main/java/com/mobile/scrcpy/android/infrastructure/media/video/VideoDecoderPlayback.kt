package com.mobile.scrcpy.android.infrastructure.media.video

import android.media.MediaCodec
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.event.DemuxerError
import com.mobile.scrcpy.android.core.common.event.DeviceDisconnected
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.VideoStream
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
        const val BUFFER_SIZE = 10 * 1024 * 1024
        const val FRAME_DURATION_US = 33333L
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
        var pts = 0L

        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "解码循环开始: ${packetProcessor.videoCodec}" }

        while (isRunning()) {
            try {
                if (configured) {
                    outputDrainer.drainOutputBuffers(bufferInfo)
                }

                when (val packet = videoStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        configured =
                            packetProcessor.processStdOutPacket(
                                payload = packet.payload,
                                nalBuffer = nalBuffer,
                                configured = configured,
                                frameCount = frameCount,
                                pts = pts,
                            )
                    }

                    is dadb.AdbShellPacket.Exit -> break
                    else -> continue
                }

                if (configured) {
                    frameCount++
                    pts += FRAME_DURATION_US
                }
            } catch (e: Exception) {
                if (e is VideoDecoderConfigurationException) {
                    throw e
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

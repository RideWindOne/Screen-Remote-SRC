package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.manager.LogManager

internal class VideoDecoderOutputDrainer(
    private val surfaceController: VideoDecoderSurfaceController,
    private val formatHandler: VideoFormatHandler,
    private val getDecoder: () -> MediaCodec?,
    private val isStopped: () -> Boolean,
    private val onOutputFrame: (rendered: Boolean) -> Unit,
    private val gameMode: Boolean,
) {
    private var renderedFrameCount = 0

    fun hasRenderedOutput(): Boolean = renderedFrameCount > 0

    fun renderedFrameCount(): Int = renderedFrameCount

    fun resetAfterDecoderFallback() {
        renderedFrameCount = 0
    }

    fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo) {
        if (isStopped()) {
            return
        }

        try {
            val codec = getDecoder() ?: return

            try {
                drainEveryOutput(codec, bufferInfo)
            } catch (e: IllegalStateException) {
                if (e.message?.contains("Uninitialized") == true ||
                    e.message?.contains("executing state") == true ||
                    e.message?.contains("flush") == true
                ) {
                    return
                }
                throw e
            }
        } catch (e: Exception) {
            if (isStopped()) return
            LogManager.e(LogTags.VIDEO_DECODER, "Output buffer handling exception: ${e.message}", e)
            throw e
        }
    }

    private fun drainEveryOutput(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        val initialTimeoutUs =
            if (gameMode) {
                ScrcpyConstants.GAME_VIDEO_OUTPUT_DEQUEUE_TIMEOUT_US
            } else {
                0L
            }
        var outputIndex = codec.dequeueOutputBuffer(bufferInfo, initialTimeoutUs)
        while (outputIndex >= 0) {
            releaseOutput(
                codec = codec,
                output = DecodedOutput(outputIndex, bufferInfo.size, bufferInfo.presentationTimeUs),
                render = surfaceController.shouldRender(),
            )
            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        }
        handleOutputStatus(codec, outputIndex)
    }

    private fun releaseOutput(
        codec: MediaCodec,
        output: DecodedOutput,
        render: Boolean,
    ) {
        codec.releaseOutputBuffer(output.index, render)
        renderedFrameCount++
        onOutputFrame(render)
        if (renderedFrameCount <= 8 || renderedFrameCount % 60 == 0) {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                "解码器输出帧 #$renderedFrameCount: size=${output.size} render=$render ptsUs=${output.presentationTimeUs}"
            }
        }
    }

    private fun handleOutputStatus(
        codec: MediaCodec,
        outputStatus: Int,
    ) {
        if (outputStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Output format changes" }
            formatHandler.updateVideoSizeFromOutputFormat(codec.outputFormat)
        }
    }

    private data class DecodedOutput(
        val index: Int,
        val size: Int,
        val presentationTimeUs: Long,
    )
}

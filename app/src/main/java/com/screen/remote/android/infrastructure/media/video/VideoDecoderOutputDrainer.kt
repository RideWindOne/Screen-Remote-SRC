package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager

internal class VideoDecoderOutputDrainer(
    private val surfaceController: VideoDecoderSurfaceController,
    private val formatHandler: VideoFormatHandler,
    private val getDecoder: () -> MediaCodec?,
    private val isStopped: () -> Boolean,
) {
    private var renderedFrameCount = 0

    fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo) {
        if (isStopped()) {
            return
        }

        try {
            val codec = getDecoder() ?: return

            try {
                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)

                while (outputIndex >= 0) {
                    val shouldRender = surfaceController.shouldRender()
                    codec.releaseOutputBuffer(outputIndex, shouldRender)
                    renderedFrameCount++
                    if (renderedFrameCount <= 8 || renderedFrameCount % 60 == 0) {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) {
                            "解码器输出帧 #$renderedFrameCount: size=${bufferInfo.size} render=$shouldRender ptsUs=${bufferInfo.presentationTimeUs}"
                        }
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "输出格式变化" }
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
            if (!isStopped()) {
                LogManager.w(LogTags.VIDEO_DECODER, "输出缓冲区处理异常: ${e.message}")
            }
        } catch (_: Exception) {
            // Ignore non-fatal drain failures during playback.
        }
    }
}

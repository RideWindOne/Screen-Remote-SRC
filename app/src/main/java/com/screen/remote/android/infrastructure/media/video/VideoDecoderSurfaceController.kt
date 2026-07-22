package com.screen.remote.android.infrastructure.media.video

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.view.Surface
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager

internal class VideoDecoderSurfaceController(
    initialSurface: Surface?,
) {
    private val surfaceLock = Any()
    private var surface: Surface? = initialSurface
    private var dummySurface: Surface? = null
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var pendingSurface: Surface? = null
    private var appliedSurface: Surface? = null

    fun currentSurface(): Surface? =
        synchronized(surfaceLock) {
            surface
        }

    fun currentDummySurface(): Surface? =
        synchronized(surfaceLock) {
            dummySurface
        }

    fun shouldRender(): Boolean =
        synchronized(surfaceLock) {
            surface?.isValid == true
        }

    fun createDummySurface(
        width: Int,
        height: Int,
    ) {
        try {
            dummySurfaceTexture =
                SurfaceTexture(0).apply {
                    setDefaultBufferSize(width, height)
                }
            dummySurface = Surface(dummySurfaceTexture)
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Dummy Surface Created: ${width}x$height" }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to create dummy Surface: ${e.message}")
        }
    }

    fun releaseDummySurface() {
        try {
            if (appliedSurface === dummySurface) {
                appliedSurface = null
            }
            dummySurface?.release()
            dummySurface = null
            dummySurfaceTexture?.release()
            dummySurfaceTexture = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to release dummy Surface: ${e.message}")
        }
    }

    fun resizeDummySurface(
        width: Int,
        height: Int,
    ) {
        synchronized(surfaceLock) {
            runCatching { dummySurfaceTexture?.setDefaultBufferSize(width, height) }
                .onFailure { error ->
                    LogManager.w(LogTags.VIDEO_DECODER, "Failed to resize dummy Surface: ${error.message}")
                }
        }
    }

    fun switchOutputSurface(
        decoder: MediaCodec?,
        isStopped: Boolean,
        newSurface: Surface?,
    ) {
        synchronized(surfaceLock) {
            surface = newSurface
            val targetSurface = newSurface ?: dummySurface
            try {
                if (decoder == null || isStopped) {
                    pendingSurface = targetSurface
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Codec not running, saved for application Surface" }
                    return
                }

                if (targetSurface != null) {
                    if (targetSurface === appliedSurface && pendingSurface == null) {
                        return
                    }
                    pendingSurface = targetSurface
                    decoder.setOutputSurface(targetSurface)
                    appliedSurface = targetSurface
                    pendingSurface = null

                    if (newSurface != null) {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Surface switched (resume rendering)" }
                    } else {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Switched to dummy Surface (background mode)" }
                    }
                } else {
                    pendingSurface = null
                    LogManager.e(LogTags.VIDEO_DECODER, "Unable to switch Surface: dummy Surface is unavailable")
                }
            } catch (e: IllegalStateException) {
                if (e.message?.contains("during start()") == true ||
                    e.message?.contains("not configured for an output surface") == true
                ) {
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "The decoder has not yet completed Surface configuration and will automatically use the new Surface later" }
                } else {
                    LogManager.w(LogTags.VIDEO_DECODER, "Failed to switch Surface (abnormal status): ${e.message}")
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.VIDEO_DECODER, "Failed to switch Surface: ${e.message}", e)
            }
        }
    }

    fun applyPendingSurface(
        decoder: MediaCodec?,
        isStopped: Boolean,
    ) {
        synchronized(surfaceLock) {
            val codec = decoder ?: return
            if (isStopped) {
                return
            }

            val targetSurface = pendingSurface ?: return
            runCatching {
                codec.setOutputSurface(targetSurface)
                appliedSurface = targetSurface
                pendingSurface = null
                VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Delayed Surface switching applied" }
            }.onFailure { error ->
                LogManager.w(LogTags.VIDEO_DECODER, "Application delay Surface switching failed: ${error.message}")
            }
        }
    }
}

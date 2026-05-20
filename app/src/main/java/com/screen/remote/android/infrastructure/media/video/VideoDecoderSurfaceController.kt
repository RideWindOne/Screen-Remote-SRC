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
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Dummy Surface 已创建: ${width}x$height" }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "创建 dummy Surface 失败: ${e.message}")
        }
    }

    fun releaseDummySurface() {
        try {
            dummySurface?.release()
            dummySurface = null
            dummySurfaceTexture?.release()
            dummySurfaceTexture = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "释放 dummy Surface 失败: ${e.message}")
        }
    }

    fun switchOutputSurface(
        decoder: MediaCodec?,
        isStopped: Boolean,
        newSurface: Surface?,
    ) {
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
                    pendingSurface = null

                    if (newSurface != null) {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Surface 已切换（恢复渲染）" }
                    } else {
                        VideoDebugLog.d(LogTags.VIDEO_DECODER) { "已切换到 dummy Surface（后台模式）" }
                    }
                } else {
                    LogManager.e(LogTags.VIDEO_DECODER, "无法切换 Surface：dummy Surface 不可用")
                }
            } catch (e: IllegalStateException) {
                if (e.message?.contains("during start()") == true ||
                    e.message?.contains("not configured for an output surface") == true
                ) {
                    pendingSurface = newSurface ?: dummySurface
                    VideoDebugLog.d(LogTags.VIDEO_DECODER) { "解码器尚未完成 Surface 配置，稍后会自动使用新 Surface" }
                } else {
                    LogManager.w(LogTags.VIDEO_DECODER, "切换 Surface 失败（状态异常）: ${e.message}")
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.VIDEO_DECODER, "切换 Surface 失败: ${e.message}", e)
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
                pendingSurface = null
                VideoDebugLog.d(LogTags.VIDEO_DECODER) { "已应用延迟的 Surface 切换" }
            }.onFailure { error ->
                LogManager.w(LogTags.VIDEO_DECODER, "应用延迟 Surface 切换失败: ${error.message}")
            }
        }
    }
}

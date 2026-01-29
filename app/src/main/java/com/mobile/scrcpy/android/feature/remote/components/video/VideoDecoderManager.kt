package com.mobile.scrcpy.android.feature.remote.components.video

import android.view.SurfaceHolder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectionViewModel
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import com.mobile.scrcpy.android.feature.session.viewmodel.SessionViewModel
import com.mobile.scrcpy.android.infrastructure.media.video.VideoDecoder
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 视频解码器管理器
 * 负责视频解码器的生命周期管理和 Surface 切换
 */
class VideoDecoderManager(
    private val connectionViewModel: ConnectionViewModel,
    private val sessionViewModel: SessionViewModel,
    private val sessionId: String,
    private val sessionData: SessionData?,
    private val onVideoSizeChanged: (width: Int, height: Int, aspectRatio: Float) -> Unit,
) {
    var videoDecoder: VideoDecoder? = null
        private set

    var currentStream: VideoStream? = null
        private set

    var isDecoderStarting: Boolean = false
        private set

    /**
     * 启动视频解码器
     */
    suspend fun startDecoder(
        stream: VideoStream,
        surfaceHolder: SurfaceHolder?,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        if (isDecoderStarting || videoDecoder != null) return

        try {
            val surface = surfaceHolder?.surface

            LogManager.d(
                LogTags.VIDEO_DECODER,
                "${RemoteTexts.REMOTE_PREPARE_VIDEO_DECODER.get()} (surface=${surface != null && surface.isValid})",
            )

            // 获取视频分辨率
            val resolution = connectionViewModel.getVideoResolution().value
            if (resolution == null) {
                LogManager.e(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_CANNOT_GET_VIDEO_RESOLUTION.get())
                return
            }
            val (width, height) = resolution

            LogManager.d(LogTags.VIDEO_DECODER, "${RemoteTexts.REMOTE_VIDEO_RESOLUTION.get()}: ${width}x$height")

            // 获取当前会话的视频编码格式
            val videoCodec = sessionData?.videoCodec ?: "h264"

            // 获取缓存的解码器名称（仅在用户选择"默认"编码器时使用）
            val cachedDecoderName =
                if (sessionData?.videoEncoder.isNullOrBlank()) {
                    // 检查缓存是否有效（7天内）
                    val cacheAge = System.currentTimeMillis() - (sessionData?.codecCacheTimestamp ?: 0L)
                    if (cacheAge < 7 * 24 * 60 * 60 * 1000L) {
                        sessionData?.cachedVideoDecoder
                    } else {
                        null
                    }
                } else {
                    null // 用户指定了编码器，不使用缓存
                }

            videoDecoder =
                VideoDecoder(surface, videoCodec, cachedDecoderName).apply {
                    onVideoSizeChanged = { w, h, rotation ->
                        if (w > 0 && h > 0) {
                            LogManager.d(
                                LogTags.VIDEO_DECODER,
                                "🎬 ${RemoteTexts.REMOTE_RECEIVED_VIDEO_SIZE.get()}: ${w}x$h, rotation=$rotation°",
                            )

                            // 直接计算宽高比（统一使用 w/h）
                            val aspectRatio = w.toFloat() / h.toFloat()
                            this@VideoDecoderManager.onVideoSizeChanged(w, h, aspectRatio)
                        } else {
                            LogManager.e(
                                LogTags.VIDEO_DECODER,
                                "${RemoteTexts.REMOTE_INVALID_VIDEO_SIZE.get()}: ${w}x$h",
                            )
                        }
                    }

                    // 当解码器选择完成后，保存到会话配置（仅在使用"默认"编码器时）
                    onDecoderSelected = { decoderName ->
                        if (sessionData?.videoEncoder.isNullOrBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    sessionViewModel.updateCodecCache(
                                        sessionId = sessionId,
                                        videoDecoder = decoderName,
                                        audioDecoder = null,
                                    )
                                    LogManager.d(
                                        LogTags.VIDEO_DECODER,
                                        "${RemoteTexts.REMOTE_CACHED_VIDEO_DECODER.get()}: $decoderName",
                                    )
                                } catch (e: Exception) {
                                    LogManager.e(
                                        LogTags.VIDEO_DECODER,
                                        "${RemoteTexts.REMOTE_SAVE_DECODER_CACHE_FAILED.get()}: ${e.message}",
                                    )
                                }
                            }
                        }
                    }

                    // 连接丢失回调 - 触发完整的资源清理和服务停止
                    onConnectionLost = {
                        LogManager.w(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_CONNECTION_LOST_CLEANUP.get())
                        scope.launch(Dispatchers.Main) {
                            connectionViewModel.handleConnectionLost()
                        }
                    }
                }

            // 使用独立协程启动视频解码器（不受 LaunchedEffect 取消影响）
            scope.launch {
                try {
                    videoDecoder?.start(stream, width, height)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    LogManager.d(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_DECODER_CANCELLED_UI_CLOSED.get())
                    stopDecoder()
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.VIDEO_DECODER,
                        "${RemoteTexts.REMOTE_DECODER_START_FAILED.get()}: ${e.message}",
                        e,
                    )
                    stopDecoder()
                }
            }

            currentStream = stream
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "${RemoteTexts.REMOTE_INIT_DECODER_FAILED.get()}: ${e.message}", e)
            videoDecoder = null
        }
    }

    /**
     * 停止视频解码器
     */
    fun stopDecoder() {
        videoDecoder?.stop()
        videoDecoder = null
        isDecoderStarting = false
    }

    /**
     * 切换 Surface（前台/后台）
     */
    suspend fun setSurface(
        surfaceHolder: SurfaceHolder?,
        lifecycleState: Lifecycle.Event,
    ) {
        val decoder = videoDecoder ?: return

        when (lifecycleState) {
            Lifecycle.Event.ON_PAUSE -> {
                // 切换到后台：使用 dummy Surface
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_SWITCH_TO_BACKGROUND.get())
                decoder.setSurface(null)
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_DECODER_CONTINUE_RUNNING.get())
            }

            Lifecycle.Event.ON_RESUME -> {
                // 恢复到前台：切换回真实 Surface
                val surface = surfaceHolder?.surface
                if (surface != null && surface.isValid) {
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_RESUME_TO_FOREGROUND.get())
                    decoder.setSurface(surface)
                    // 立即发送唤醒信号触发新帧
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            connectionViewModel.wakeUpScreen()
                        } catch (e: Exception) {
                            LogManager.w(LogTags.REMOTE_DISPLAY, "唤醒屏幕失败: ${e.message}")
                        }
                    }
                } else {
                    LogManager.w(
                        LogTags.REMOTE_DISPLAY,
                        RemoteTexts.REMOTE_FOREGROUND_RESUME_INVALID_SURFACE.get(),
                    )
                }
            }

            else -> {
                // 其他生命周期事件：检查 Surface 是否有效
                val surface = surfaceHolder?.surface
                if (surface != null && surface.isValid) {
                    decoder.setSurface(surface)
                }
            }
        }
    }

    /**
     * 直接切换 Surface（用于 Surface 回调）
     */
    fun setSurfaceImmediate(surfaceHolder: SurfaceHolder?) {
        val decoder = videoDecoder ?: return
        val surface = surfaceHolder?.surface
        if (surface != null && surface.isValid) {
            decoder.setSurface(surface)
        }
    }
}

/**
 * Composable 函数：管理视频解码器生命周期
 */
@Composable
fun rememberVideoDecoderManager(
    connectionViewModel: ConnectionViewModel,
    sessionViewModel: SessionViewModel,
    sessionId: String,
    sessionData: SessionData?,
    videoStream: VideoStream?,
    surfaceHolder: SurfaceHolder?,
    lifecycleState: Lifecycle.Event,
    onVideoSizeChanged: (width: Int, height: Int, aspectRatio: Float) -> Unit,
): VideoDecoderManager {
    val scope = rememberCoroutineScope()

    val manager =
        remember {
            VideoDecoderManager(connectionViewModel, sessionViewModel, sessionId, sessionData, onVideoSizeChanged)
        }

    // 监听 videoStream 变化
    LaunchedEffect(videoStream) {
        LogManager.d(
            LogTags.VIDEO_DECODER,
            "LaunchedEffect 触发: stream=${videoStream != null}, currentStream=${manager.currentStream != null}, videoDecoder=${manager.videoDecoder != null}",
        )

        // 如果 stream 变化，先停止旧的解码器
        if (videoStream != manager.currentStream && manager.videoDecoder != null) {
            LogManager.i(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_VIDEO_STREAM_CHANGED.get())
            manager.stopDecoder()
        }

        // 启动解码器
        if (videoStream != null && !manager.isDecoderStarting && manager.videoDecoder == null) {
            manager.startDecoder(videoStream, surfaceHolder, scope)
        } else if (videoStream == null && manager.videoDecoder != null) {
            manager.stopDecoder()
        }
    }

    // 处理 Surface 切换（前台/后台）
    DisposableEffect(surfaceHolder, lifecycleState) {
        scope.launch {
            manager.setSurface(surfaceHolder, lifecycleState)
        }
        onDispose { }
    }

    // 清理解码器（只在退出界面时触发）
    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                try {
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_START_CLEANUP_RESOURCES.get())
                    manager.stopDecoder()
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_CLEANUP_COMPLETE.get())
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.REMOTE_DISPLAY,
                        "${RemoteTexts.REMOTE_CLEANUP_EXCEPTION.get()}: ${e.message}",
                        e,
                    )
                }
            }
        }
    }

    return manager
}

package com.mobile.scrcpy.android.feature.remote.presentation

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
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelector
import com.mobile.scrcpy.android.infrastructure.media.video.VideoDecoder
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 视频播放协调器。
 *
 * 虽然历史命名仍为 manager，但职责属于 presentation/runtime 协调层，
 * 因此从 UI component 目录迁移到 presentation 包。
 */
class VideoDecoderManager(
    private val connectionViewModel: ConnectionViewModel,
    private val onVideoSizeChanged: (width: Int, height: Int, aspectRatio: Float) -> Unit,
) {
    var videoDecoder: VideoDecoder? = null
        private set

    var currentStream: VideoStream? = null
        private set

    var isDecoderStarting: Boolean = false
        private set

    fun startDecoder(
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

            val resolution = connectionViewModel.getVideoResolution().value
            if (resolution == null) {
                LogManager.e(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_CANNOT_GET_VIDEO_RESOLUTION.get())
                return
            }
            val (width, height) = resolution

            LogManager.d(LogTags.VIDEO_DECODER, "${RemoteTexts.REMOTE_VIDEO_RESOLUTION.get()}: ${width}x$height")

            val options = connectionViewModel.getCurrentSessionOptions()
            val videoCodec = options?.preferredVideoCodec?.ifBlank { "h264" } ?: "h264"

            val decoderName: String? =
                if (!options?.userVideoDecoder.isNullOrBlank()) {
                    pickCompatibleDecoder(videoCodec, options.userVideoDecoder, "用户指定")
                } else {
                    val selected = options?.selectedVideoDecoder
                    if (!selected.isNullOrBlank()) {
                        pickCompatibleDecoder(videoCodec, selected, "系统选择")
                    } else {
                        LogManager.w(LogTags.VIDEO_DECODER, "selectedVideoDecoder 为空，将使用系统默认解码器")
                        null
                    }
                }

            videoDecoder =
                VideoDecoder(
                    surface = surface,
                    videoCodec = videoCodec,
                    cachedDecoderName = decoderName,
                    sessionContext = connectionViewModel.createSessionContext(),
                ).apply {
                    onVideoSizeChanged = { w, h, rotation ->
                        if (w > 0 && h > 0) {
                            LogManager.d(
                                LogTags.VIDEO_DECODER,
                                "🎬 ${RemoteTexts.REMOTE_RECEIVED_VIDEO_SIZE.get()}: ${w}x$h, rotation=$rotation°",
                            )

                            val aspectRatio = w.toFloat() / h.toFloat()
                            this@VideoDecoderManager.onVideoSizeChanged(w, h, aspectRatio)
                        } else {
                            LogManager.e(
                                LogTags.VIDEO_DECODER,
                                "${RemoteTexts.REMOTE_INVALID_VIDEO_SIZE.get()}: ${w}x$h",
                            )
                        }
                    }

                    onConnectionLost = {
                        LogManager.w(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_CONNECTION_LOST_CLEANUP.get())
                        scope.launch(Dispatchers.Main) {
                            connectionViewModel.handleConnectionLost()
                        }
                    }
                }

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

    fun stopDecoder() {
        videoDecoder?.stop()
        videoDecoder = null
        isDecoderStarting = false
    }

    fun setSurface(
        surfaceHolder: SurfaceHolder?,
        lifecycleState: Lifecycle.Event,
    ) {
        val decoder = videoDecoder ?: return

        when (lifecycleState) {
            Lifecycle.Event.ON_PAUSE -> {
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_SWITCH_TO_BACKGROUND.get())
                decoder.setSurface(null)
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_DECODER_CONTINUE_RUNNING.get())
            }

            Lifecycle.Event.ON_RESUME -> {
                val surface = surfaceHolder?.surface
                if (surface != null && surface.isValid) {
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_RESUME_TO_FOREGROUND.get())
                    decoder.setSurface(surface)
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
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
                val surface = surfaceHolder?.surface
                if (surface != null && surface.isValid) {
                    decoder.setSurface(surface)
                }
            }
        }
    }

    fun setSurfaceImmediate(surfaceHolder: SurfaceHolder?) {
        val decoder = videoDecoder ?: return
        val surface = surfaceHolder?.surface
        if (surface != null && surface.isValid) {
            decoder.setSurface(surface)
        }
    }

    private fun pickCompatibleDecoder(
        videoCodec: String,
        decoderName: String,
        source: String,
    ): String? {
        val normalizedVideoCodec = CodecSelector.inferVideoCodecFromName(videoCodec)
        val decoderCodec = CodecSelector.inferVideoCodecFromName(decoderName)
        return if (decoderCodec == normalizedVideoCodec) {
            LogManager.d(LogTags.VIDEO_DECODER, "使用${source}的解码器: $decoderName")
            decoderName
        } else {
            LogManager.w(
                LogTags.VIDEO_DECODER,
                "${source}的解码器与当前视频格式不匹配，已忽略: decoder=$decoderName decoderCodec=$decoderCodec videoCodec=$normalizedVideoCodec",
            )
            null
        }
    }
}

@Composable
fun rememberVideoDecoderManager(
    connectionViewModel: ConnectionViewModel,
    videoStream: VideoStream?,
    surfaceHolder: SurfaceHolder?,
    lifecycleState: Lifecycle.Event,
    onVideoSizeChanged: (width: Int, height: Int, aspectRatio: Float) -> Unit,
): VideoDecoderManager {
    val scope = rememberCoroutineScope()

    val manager =
        remember {
            VideoDecoderManager(
                connectionViewModel,
                onVideoSizeChanged,
            )
        }

    LaunchedEffect(videoStream) {
        LogManager.d(
            LogTags.VIDEO_DECODER,
            "LaunchedEffect 触发: stream=${videoStream != null}, currentStream=${manager.currentStream != null}, videoDecoder=${manager.videoDecoder != null}",
        )

        if (videoStream != manager.currentStream && manager.videoDecoder != null) {
            LogManager.i(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_VIDEO_STREAM_CHANGED.get())
            manager.stopDecoder()
        }

        if (videoStream != null && !manager.isDecoderStarting && manager.videoDecoder == null) {
            manager.startDecoder(videoStream, surfaceHolder, scope)
        } else if (videoStream == null && manager.videoDecoder != null) {
            manager.stopDecoder()
        }
    }

    DisposableEffect(surfaceHolder, lifecycleState) {
        scope.launch {
            manager.setSurface(surfaceHolder, lifecycleState)
        }
        onDispose { }
    }

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

package com.screen.remote.android.feature.remote.presentation

import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.media.video.VideoDecoder
import com.screen.remote.android.infrastructure.media.video.VideoPerformanceCounters
import com.screen.remote.android.infrastructure.media.video.VideoPerformanceSnapshot
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
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
    private val performanceCounters = VideoPerformanceCounters()

    var videoDecoder: VideoDecoder? = null
        private set

    var currentStream: VideoStream? = null
        private set

    var isDecoderStarting: Boolean = false
        private set

    fun performanceSnapshot(): VideoPerformanceSnapshot = performanceCounters.snapshot()

    fun startDecoder(
        stream: VideoStream,
        surface: Surface?,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        if (isDecoderStarting || videoDecoder != null) return

        try {
            performanceCounters.reset()
            LogManager.d(
                LogTags.VIDEO_DECODER,
                "${RemoteTexts.REMOTE_PREPARE_VIDEO_DECODER.english} (surface=${surface != null && surface.isValid})",
            )

            val resolution = connectionViewModel.getVideoResolution().value
            if (resolution == null) {
                LogManager.e(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_CANNOT_GET_VIDEO_RESOLUTION.english)
                return
            }
            val (width, height) = resolution

            LogManager.d(LogTags.VIDEO_DECODER, "${RemoteTexts.REMOTE_VIDEO_RESOLUTION.english}: ${width}x$height")

            val options = connectionViewModel.getCurrentSessionOptions()
            val videoCodec = stream.codec
            val expectedDeviceSerial = options?.capabilityCache?.deviceSerial.orEmpty()
            val rejectionKey = "$expectedDeviceSerial|video:$videoCodec"
            val decoderName = options?.getFinalVideoDecoder()?.ifBlank { null }
            LogManager.d(
                LogTags.VIDEO_DECODER,
                "Video socket negotiation format: $videoCodec, preferred decoder: ${decoderName ?: "auto"}",
            )

            videoDecoder =
                VideoDecoder(
                    surface = surface,
                    videoCodec = videoCodec,
                    cachedDecoderName = decoderName,
                    allowHardwareDecoders = options?.config?.enableHardwareDecoding != false,
                    decoderSelectionPinned = options?.config?.userVideoDecoder?.isNotBlank() == true,
                    initialRejectedDecoderNames = connectionViewModel.runtimeRejectedDecoders(rejectionKey),
                    performanceCounters = performanceCounters,
                    sessionContext = connectionViewModel.createSessionContext(),
                    gameMode = options?.config?.gameMode == true,
                ).apply {
                    onDecoderSelected = { decoder ->
                        connectionViewModel.rememberResolvedVideoDecoder(
                            decoderName = decoder,
                            expectedDeviceSerial = expectedDeviceSerial,
                            expectedCodec = videoCodec,
                        )
                    }
                    onDecoderRejected = { decoder ->
                        connectionViewModel.rememberRuntimeRejectedDecoder(rejectionKey, decoder)
                    }
                    onVideoSizeChanged = { w, h, rotation ->
                        if (w > 0 && h > 0) {
                            LogManager.d(
                                LogTags.VIDEO_DECODER,
                                "🎬 ${RemoteTexts.REMOTE_RECEIVED_VIDEO_SIZE.english}: ${w}x$h, rotation=$rotation°",
                            )

                            val aspectRatio = w.toFloat() / h.toFloat()
                            this@VideoDecoderManager.onVideoSizeChanged(w, h, aspectRatio)
                        } else {
                            LogManager.e(
                                LogTags.VIDEO_DECODER,
                                "${RemoteTexts.REMOTE_INVALID_VIDEO_SIZE.english}: ${w}x$h",
                            )
                        }
                    }

                    onConnectionLost = {
                        LogManager.w(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_CONNECTION_LOST_CLEANUP.english)
                        scope.launch(Dispatchers.Main) {
                            connectionViewModel.handleConnectionLost()
                        }
                    }
                }

            scope.launch {
                try {
                    videoDecoder?.start(stream, width, height)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    LogManager.d(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_DECODER_CANCELLED_UI_CLOSED.english)
                    stopDecoder()
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.VIDEO_DECODER,
                        "${RemoteTexts.REMOTE_DECODER_START_FAILED.english}: ${e.message}",
                        e,
                    )
                    stopDecoder()
                }
            }

            currentStream = stream
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "${RemoteTexts.REMOTE_INIT_DECODER_FAILED.english}: ${e.message}", e)
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
        renderSurface: Surface?,
        usePersistentSurface: Boolean,
        lifecycleState: Lifecycle.Event,
    ) {
        val decoder = videoDecoder ?: return
        val activeSurface =
            if (usePersistentSurface) {
                renderSurface
            } else {
                surfaceHolder?.surface
            }

        when (lifecycleState) {
            Lifecycle.Event.ON_PAUSE -> {
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_SWITCH_TO_BACKGROUND.english)
                if (usePersistentSurface) {
                    if (activeSurface != null && activeSurface.isValid) {
                        decoder.setSurface(activeSurface)
                        LogManager.d(LogTags.REMOTE_DISPLAY, "Full screen mode keeps Texture Surface, skips dummy Surface switching")
                    } else {
                        LogManager.w(LogTags.REMOTE_DISPLAY, "Full screen mode Surface is not available, do not switch to dummy Surface yet")
                    }
                } else {
                    decoder.setSurface(null)
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_DECODER_CONTINUE_RUNNING.english)
                }
            }

            Lifecycle.Event.ON_RESUME -> {
                if (activeSurface != null && activeSurface.isValid) {
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_RESUME_TO_FOREGROUND.english)
                    decoder.setSurface(activeSurface)
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            connectionViewModel.wakeUpScreen()
                        } catch (e: Exception) {
                            LogManager.w(LogTags.REMOTE_DISPLAY, "Failed to wake up screen: ${e.message}")
                        }
                    }
                } else {
                    LogManager.w(
                        LogTags.REMOTE_DISPLAY,
                        RemoteTexts.REMOTE_FOREGROUND_RESUME_INVALID_SURFACE.english,
                    )
                }
            }

            else -> {
                if (activeSurface != null && activeSurface.isValid) {
                    decoder.setSurface(activeSurface)
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

    fun setSurfaceImmediate(surface: Surface?) {
        val decoder = videoDecoder ?: return
        decoder.setSurface(surface?.takeIf { it.isValid })
    }

}

@Composable
fun rememberVideoDecoderManager(
    connectionViewModel: ConnectionViewModel,
    videoStream: VideoStream?,
    surfaceHolder: SurfaceHolder?,
    renderSurface: Surface?,
    usePersistentSurface: Boolean,
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
            "LaunchedEffect trigger: stream=${videoStream != null}, currentStream=${manager.currentStream != null}, videoDecoder=${manager.videoDecoder != null}",
        )

        if (videoStream != manager.currentStream && manager.videoDecoder != null) {
            LogManager.i(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_VIDEO_STREAM_CHANGED.english)
            manager.stopDecoder()
        }

        if (videoStream != null && !manager.isDecoderStarting && manager.videoDecoder == null) {
            val initialSurface =
                if (usePersistentSurface) {
                    renderSurface
                } else {
                    surfaceHolder?.surface
                }
            manager.startDecoder(videoStream, initialSurface, scope)
        } else if (videoStream == null && manager.videoDecoder != null) {
            manager.stopDecoder()
        }
    }

    DisposableEffect(surfaceHolder, renderSurface, usePersistentSurface, lifecycleState) {
        scope.launch {
            manager.setSurface(surfaceHolder, renderSurface, usePersistentSurface, lifecycleState)
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_START_CLEANUP_RESOURCES.english)
                manager.stopDecoder()
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_CLEANUP_COMPLETE.english)
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.REMOTE_DISPLAY,
                    "${RemoteTexts.REMOTE_CLEANUP_EXCEPTION.english}: ${e.message}",
                    e,
                )
            }
        }
    }

    return manager
}

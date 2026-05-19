package com.mobile.scrcpy.android.feature.remote.presentation

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
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
        surface: Surface?,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        if (isDecoderStarting || videoDecoder != null) return

        try {
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
                if (options?.enableHardwareDecoding == false) {
                    pickSoftwareDecoder(videoCodec)
                } else if (!options?.userVideoDecoder.isNullOrBlank()) {
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
                LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_SWITCH_TO_BACKGROUND.get())
                if (usePersistentSurface) {
                    if (activeSurface != null && activeSurface.isValid) {
                        decoder.setSurface(activeSurface)
                        LogManager.d(LogTags.REMOTE_DISPLAY, "全屏模式保持 Texture Surface，跳过 dummy Surface 切换")
                    } else {
                        LogManager.w(LogTags.REMOTE_DISPLAY, "全屏模式 Surface 不可用，暂不切换到 dummy Surface")
                    }
                } else {
                    decoder.setSurface(null)
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_DECODER_CONTINUE_RUNNING.get())
                }
            }

            Lifecycle.Event.ON_RESUME -> {
                if (activeSurface != null && activeSurface.isValid) {
                    LogManager.d(LogTags.REMOTE_DISPLAY, RemoteTexts.REMOTE_RESUME_TO_FOREGROUND.get())
                    decoder.setSurface(activeSurface)
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

    private fun pickSoftwareDecoder(videoCodec: String): String? {
        val mimeType = videoCodecToMimeType(videoCodec) ?: return null
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val softwareDecoder =
            codecList.firstOrNull { info ->
                !info.isEncoder &&
                    info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
                    info.isSoftwareCodecCompat()
            }
        if (softwareDecoder == null) {
            LogManager.w(LogTags.VIDEO_DECODER, "未找到软件解码器，回退系统默认解码器: codec=$videoCodec mime=$mimeType")
        } else {
            LogManager.d(LogTags.VIDEO_DECODER, "硬件解码已关闭，使用软件解码器: ${softwareDecoder.name}")
        }
        return softwareDecoder?.name
    }

    private fun MediaCodecInfo.isSoftwareCodecCompat(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            isSoftwareOnly
        } else {
            val lowerName = name.lowercase()
            lowerName.startsWith("omx.google.") || lowerName.startsWith("c2.android.")
        }

    private fun videoCodecToMimeType(videoCodec: String): String? =
        when (CodecSelector.inferVideoCodecFromName(videoCodec)) {
            "h264" -> MediaFormat.MIMETYPE_VIDEO_AVC
            "h265" -> MediaFormat.MIMETYPE_VIDEO_HEVC
            "av1" -> "video/av01"
            "vp9" -> MediaFormat.MIMETYPE_VIDEO_VP9
            "vp8" -> MediaFormat.MIMETYPE_VIDEO_VP8
            else -> null
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
            "LaunchedEffect 触发: stream=${videoStream != null}, currentStream=${manager.currentStream != null}, videoDecoder=${manager.videoDecoder != null}",
        )

        if (videoStream != manager.currentStream && manager.videoDecoder != null) {
            LogManager.i(LogTags.VIDEO_DECODER, RemoteTexts.REMOTE_VIDEO_STREAM_CHANGED.get())
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

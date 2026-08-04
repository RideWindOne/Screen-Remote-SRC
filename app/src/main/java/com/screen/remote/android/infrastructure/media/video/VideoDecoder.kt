package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import android.os.Looper
import android.os.Process
import android.view.Surface
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.event.ScreenInitSize
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderType
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VideoDecoder - 视频解码器入口。
 *
 * 主类保留生命周期、Surface 切换和会话事件上报；
 * NAL 处理和解码循环下沉到协作对象。
 */
class VideoDecoder(
    surface: Surface?,
    private val videoCodec: String = "h264",
    cachedDecoderName: String? = null,
    allowHardwareDecoders: Boolean = true,
    decoderSelectionPinned: Boolean = false,
    initialRejectedDecoderNames: Set<String> = emptySet(),
    performanceCounters: VideoPerformanceCounters = VideoPerformanceCounters(),
    private val sessionContext: SessionContext,
    private val gameMode: Boolean = false,
) {
    private val decoderDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "scrcpy-video-$videoCodec").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    private val decoderDispatcherClosed = AtomicBoolean(false)
    private val runtimeState = VideoDecoderRuntimeState()
    private val surfaceController = VideoDecoderSurfaceController(surface)
    private val codecManager =
        VideoCodecManager(
            videoCodec,
            cachedDecoderName,
            allowHardwareDecoders,
            decoderSelectionPinned,
            initialRejectedDecoderNames,
        )
    private val nalParser = VideoNalParser()
    private val formatHandler = VideoFormatHandler(codecManager)
    private val playback =
        VideoDecoderPlayback(
            videoCodec = videoCodec,
            runtimeState = runtimeState,
            surfaceController = surfaceController,
            nalParser = nalParser,
            formatHandler = formatHandler,
            getDecoder = { decoder },
            setDecoder = { decoder = it },
            isRunning = { isRunning },
            isStopped = { isStopped },
            shouldReportConnectionLost = ::shouldReportConnectionLost,
            performanceCounters = performanceCounters,
            gameMode = gameMode,
            onVideoStateChanged = ::updateVideoState,
            onConnectionLost = { onConnectionLost?.invoke() },
        )

    @Volatile
    private var decoder: MediaCodec? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var isStopped = false
    private var lifecycleReportedStarted = false

    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)? = null
        set(value) {
            field = value
            formatHandler.onVideoSizeChanged = value
        }
    var onDecoderSelected: ((decoderName: String) -> Unit)? = null
        set(value) {
            field = value
            codecManager.onDecoderSelected = value
        }
    var onDecoderRejected: ((decoderName: String) -> Unit)? = null
        set(value) {
            field = value
            codecManager.onDecoderRejected = value
        }
    var onConnectionLost: (() -> Unit)? = null

    suspend fun start(
        videoStream: VideoStream,
        width: Int,
        height: Int,
    ) = withContext(decoderDispatcher) {
        if (Looper.myLooper() == null) {
            Looper.prepare()
            LogManager.d(LogTags.VIDEO_DECODER, "Preparing Looper for video decoding thread")
        }

        try {
            configureDecoderThreadPriority()
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Start decoding $videoCodec: ${width}x$height" }

            surfaceController.createDummySurface(width, height)
            markStarted(width, height)

            decoder = codecManager.createDecoder(width, height) ?: run {
                LogManager.e(LogTags.VIDEO_DECODER, "Unable to create decoder")
                val sizeFailure = codecManager.lastSizeFailure
                sessionContext.emit(
                    SessionEvent.DecoderError(
                        DecoderIssue(
                            kind = if (sizeFailure != null) DecoderIssueKind.UnsupportedSize else DecoderIssueKind.CreateFailed,
                            decoderType = DecoderType.Video,
                            detail =
                                if (sizeFailure != null) {
                                    "The native decoder does not support ${sizeFailure.width}x${sizeFailure.height} (${sizeFailure.mimeType})"
                                } else {
                                    "Unable to create decoder"
                                },
                            width = sizeFailure?.width,
                            height = sizeFailure?.height,
                            suggestedMaxSize = sizeFailure?.suggestedMaxSize,
                        ),
                    ),
                )
                return@withContext
            }

            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Decoder: ${decoder?.name}" }
            surfaceController.applyPendingSurface(decoder, isStopped)
            sessionContext.emit(SessionEvent.DecoderStarted(DecoderType.Video))
            lifecycleReportedStarted = true

            isRunning = true
            playback.decodeLoop(videoStream)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Decoding failed: ${e.message}", e)
            sessionContext.emit(
                SessionEvent.DecoderError(
                    DecoderIssue(
                        kind = DecoderIssueKind.RuntimeError,
                        decoderType = DecoderType.Video,
                        detail = e.message ?: "Unknown error",
                    ),
                ),
            )
        } finally {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        if (isStopped) {
            closeDecoderDispatcher()
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "Decoder stopped, skipping" }
            return
        }

        isRunning = false
        isStopped = true

        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "Failed to stop decoder: ${e.message}")
        }

        surfaceController.releaseDummySurface()
        if (lifecycleReportedStarted) {
            sessionContext.emit(SessionEvent.DecoderStopped(DecoderType.Video))
            lifecycleReportedStarted = false
        }
        closeDecoderDispatcher()
    }

    fun setSurface(newSurface: Surface?) {
        surfaceController.switchOutputSurface(decoder, isStopped, newSurface)
    }

    private fun shouldReportConnectionLost(): Boolean {
        return sessionContext.currentSession()?.sessionState?.value !is SessionState.Idle
    }

    private fun configureDecoderThreadPriority() {
        val priority =
            if (gameMode) {
                Process.THREAD_PRIORITY_DISPLAY
            } else {
                Process.THREAD_PRIORITY_DEFAULT
            }
        runCatching { Process.setThreadPriority(priority) }
            .onFailure { error ->
                LogManager.w(LogTags.VIDEO_DECODER, "Failed to set video decoding thread priority: ${error.message}")
            }
    }

    private fun closeDecoderDispatcher() {
        if (decoderDispatcherClosed.compareAndSet(false, true)) {
            decoderDispatcher.close()
        }
    }

    private fun markStarted(
        width: Int,
        height: Int,
    ) {
        isStopped = false
        runtimeState.currentWidth = width
        runtimeState.currentHeight = height
        runtimeState.currentRotation = 0
        updateVideoState(width, height, 0)
    }

    private fun updateVideoState(
        width: Int,
        height: Int,
        rotation: Int,
    ) {
        runtimeState.currentWidth = width
        runtimeState.currentHeight = height
        runtimeState.currentRotation = rotation
        onVideoSizeChanged?.invoke(width, height, rotation)
        ScrcpyEventBus.pushEvent(ScreenInitSize(width, height))
    }
}

internal class VideoDecoderRuntimeState(
    var currentWidth: Int = 0,
    var currentHeight: Int = 0,
    var currentRotation: Int = 0,
)

package com.screen.remote.android.infrastructure.media.video

import android.media.MediaCodec
import android.view.Surface
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.event.ScreenInitSize
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssueKind
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderType
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val sessionContext: SessionContext,
) {
    private val runtimeState = VideoDecoderRuntimeState()
    private val surfaceController = VideoDecoderSurfaceController(surface)
    private val codecManager = VideoCodecManager(videoCodec, cachedDecoderName)
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
            onVideoStateChanged = ::updateVideoState,
            onConnectionLost = { onConnectionLost?.invoke() },
        )

    private var decoder: MediaCodec? = null
    private var isRunning = false
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
    var onConnectionLost: (() -> Unit)? = null

    suspend fun start(
        videoStream: VideoStream,
        width: Int,
        height: Int,
    ) = withContext(Dispatchers.IO) {
        try {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "开始解码 $videoCodec: ${width}x$height" }

            surfaceController.createDummySurface(width, height)
            markStarted(width, height)

            decoder = codecManager.createDecoder(width, height) ?: run {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建解码器")
                sessionContext.emit(
                    SessionEvent.DecoderError(
                        DecoderIssue(
                            kind = DecoderIssueKind.CreateFailed,
                            decoderType = DecoderType.Video,
                            detail = "无法创建解码器",
                        ),
                    ),
                )
                return@withContext
            }

            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "解码器: ${decoder?.name}" }
            surfaceController.applyPendingSurface(decoder, isStopped)
            sessionContext.emit(SessionEvent.DecoderStarted(DecoderType.Video))
            lifecycleReportedStarted = true

            isRunning = true
            playback.decodeLoop(videoStream)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解码失败: ${e.message}", e)
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

    fun stop() {
        if (isStopped) {
            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "解码器已停止，跳过" }
            return
        }

        isRunning = false
        isStopped = true

        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "停止解码器失败: ${e.message}")
        }

        surfaceController.releaseDummySurface()
        if (lifecycleReportedStarted) {
            sessionContext.emit(SessionEvent.DecoderStopped(DecoderType.Video))
            lifecycleReportedStarted = false
        }
    }

    fun setSurface(newSurface: Surface?) {
        surfaceController.switchOutputSurface(decoder, isStopped, newSurface)
    }

    private fun shouldReportConnectionLost(): Boolean {
        return sessionContext.currentSession()?.sessionState?.value !is SessionState.Idle
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

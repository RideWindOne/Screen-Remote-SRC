package com.screen.remote.android.infrastructure.media.audio

import android.media.MediaCodec
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.DemuxerError
import com.screen.remote.android.core.common.event.DeviceDisconnected
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.infrastructure.scrcpy.connection.isExpectedConnectionClosure
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderType
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AudioDecoder - 音频解码器入口。
 *
 * 主类只保留生命周期和事件上报，具体播放循环已拆给协作对象。
 */
class AudioDecoder(
    volumeScale: Float = 1.0f,
    preferredDecoderName: String? = null,
    allowHardwareDecoders: Boolean = true,
    decoderSelectionPinned: Boolean = false,
    initialRejectedDecoderNames: Set<String> = emptySet(),
    private val sessionContext: SessionContext,
) {
    private val decoderLock = Any()
    private val formatHandler =
        AudioFormatHandler(
            preferredDecoderName,
            allowHardwareDecoders,
            decoderSelectionPinned,
            initialRejectedDecoderNames,
        )
    private val trackManager = AudioTrackManager(volumeScale)
    private val playback =
        AudioDecoderPlayback(
            formatHandler = formatHandler,
            trackManager = trackManager,
            decoderLock = decoderLock,
            getDecoder = { decoder },
            setDecoder = { decoder = it },
            isRunning = { isRunning },
            isStopped = { isStopped },
            onPlaybackReady = ::reportStarted,
        )

    @Volatile private var decoder: MediaCodec? = null
    @Volatile private var isRunning = false
    @Volatile private var isStopped = false
    @Volatile private var lifecycleReportedStarted = false

    var onConnectionLost: (() -> Unit)? = null
    var onDecoderSelected: ((String) -> Unit)?
        get() = formatHandler.onDecoderSelected
        set(value) {
            formatHandler.onDecoderSelected = value
        }
    var onDecoderRejected: ((String) -> Unit)?
        get() = formatHandler.onDecoderRejected
        set(value) {
            formatHandler.onDecoderRejected = value
        }

    suspend fun start(audioStream: AudioStream) =
        withContext(Dispatchers.IO) {
            try {
                val codec = audioStream.codec
                val sampleRate = audioStream.sampleRate
                val channelCount = audioStream.channelCount

                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "Start audio decoding: codec=$codec, rate=$sampleRate, channels=$channelCount" }

                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO) }

                prepareRunning()

                if (codec.lowercase() == "raw") {
                    playback.playRawAudio(
                        audioStream = audioStream,
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                    )
                } else {
                    playback.decodeAndPlay(
                        audioStream = audioStream,
                        codec = codec,
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                    )
                }
            } catch (e: Exception) {
                handleDecoderFailure(e)
            } finally {
                stop()
            }
        }

    fun stop() {
        synchronized(decoderLock) {
            if (isStopped) {
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "stop() is called, but is already stopped" }
                return
            }

            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "stop() is called to start stopping the decoder" }

            isRunning = false
            isStopped = true

            trackManager.release()
            releaseDecoder()

            AudioDebugLog.d(LogTags.AUDIO_DECODER) { "Audio decoder stopped" }
            if (lifecycleReportedStarted) {
                sessionContext.emit(SessionEvent.DecoderStopped(DecoderType.Audio))
                lifecycleReportedStarted = false
            }
        }
    }

    private fun prepareRunning() {
        isStopped = false
        isRunning = true
    }

    private fun reportStarted() {
        if (lifecycleReportedStarted) return
        sessionContext.emit(SessionEvent.DecoderStarted(DecoderType.Audio))
        lifecycleReportedStarted = true
    }

    private fun handleDecoderFailure(error: Exception) {
        when {
            error.isExpectedShutdown() -> Unit
            else -> {
                LogManager.e(LogTags.AUDIO_DECODER, "Audio decoding failed: ${error.message}", error)
            }
        }
        if (error.isConnectionLost()) {
            if (shouldReportConnectionLost()) {
                LogManager.w(LogTags.AUDIO_DECODER, "Audio connection lost, triggering callback")
                onConnectionLost?.invoke()
                ScrcpyEventBus.pushEvent(DeviceDisconnected)
            } else {
                AudioDebugLog.d(LogTags.AUDIO_DECODER) { "Audio stream closed during cleanup, connection loss callback ignored" }
            }
        } else {
            ScrcpyEventBus.pushEvent(DemuxerError(error.message ?: "Audio decode error"))
        }
        sessionContext.emit(
            SessionEvent.DecoderError(
                DecoderIssue(
                    kind = if (error.isConnectionLost()) DecoderIssueKind.ConnectionLost else DecoderIssueKind.RuntimeError,
                    decoderType = DecoderType.Audio,
                    detail = error.message ?: "Unknown error",
                ),
            ),
        )
    }

    private fun releaseDecoder() {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {
            // Ignore cleanup failures during teardown.
        } finally {
            decoder = null
        }
    }

    private fun Exception.isConnectionLost(): Boolean =
        isExpectedConnectionClosure()

    private fun Exception.isExpectedShutdown(): Boolean =
        isConnectionLost() || message?.contains("Pending dequeue output buffer request cancelled") == true

    private fun shouldReportConnectionLost(): Boolean {
        return sessionContext.currentSession()?.sessionState?.value !is SessionState.Idle
    }
}

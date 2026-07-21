package com.screen.remote.android.feature.remote.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.media.audio.AudioDecoder
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 音频播放协调器。
 *
 * 历史命名仍保留 manager，但职责属于 presentation/runtime 协调层。
 */
class AudioDecoderManager(
    private val connectionViewModel: ConnectionViewModel,
    private val audioVolume: Float,
) {
    var audioDecoder: AudioDecoder? = null
        private set

    var currentAudioStream: AudioStream? = null
        private set

    var isAudioDecoderStarting: Boolean = false
        private set

    fun startDecoder(
        stream: AudioStream,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        if (isAudioDecoderStarting || audioDecoder != null) return

        try {
            isAudioDecoderStarting = true
            val codec = stream.codec.lowercase()
            val options = connectionViewModel.getCurrentSessionOptions()
            val expectedDeviceSerial = options?.capabilityCache?.deviceSerial.orEmpty()
            val rejectionKey = "$expectedDeviceSerial|audio:$codec"
            LogManager.d(LogTags.AUDIO_DECODER, "${RemoteTexts.REMOTE_START_AUDIO_DECODER.english}: codec=$codec")

            val decoder =
                AudioDecoder(
                    volumeScale = audioVolume,
                    preferredDecoderName =
                        options?.getFinalAudioDecoder()
                            ?.ifBlank { null },
                    allowHardwareDecoders =
                        options?.config?.enableHardwareDecoding != false,
                    decoderSelectionPinned =
                        options?.config?.userAudioDecoder?.isNotBlank() == true,
                    initialRejectedDecoderNames = connectionViewModel.runtimeRejectedDecoders(rejectionKey),
                    sessionContext = connectionViewModel.createSessionContext(),
                ).apply {
                    onDecoderSelected = { decoder ->
                        connectionViewModel.rememberResolvedAudioDecoder(
                            decoderName = decoder,
                            expectedDeviceSerial = expectedDeviceSerial,
                            expectedCodec = codec,
                        )
                    }
                    onDecoderRejected = { decoder ->
                        connectionViewModel.rememberRuntimeRejectedDecoder(rejectionKey, decoder)
                    }
                    onConnectionLost = {
                        LogManager.w(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_CONNECTION_LOST.english)
                        scope.launch(Dispatchers.Main) {
                            connectionViewModel.handleConnectionLost()
                        }
                    }
                }
            audioDecoder = decoder

            scope.launch(Dispatchers.IO) {
                try {
                    decoder.start(stream)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    LogManager.d(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_DECODER_CANCELLED.english)
                    stopDecoder(decoder)
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.AUDIO_DECODER,
                        "${RemoteTexts.REMOTE_AUDIO_DECODER_FAILED.english}: ${e.message}",
                        e,
                    )
                    stopDecoder(decoder)
                } finally {
                    isAudioDecoderStarting = false
                }
            }

            currentAudioStream = stream
        } catch (e: Exception) {
            LogManager.e(
                LogTags.AUDIO_DECODER,
                "${RemoteTexts.REMOTE_INIT_AUDIO_DECODER_FAILED.english}: ${e.message}",
                e,
            )
            audioDecoder = null
            isAudioDecoderStarting = false
        }
    }

    private fun stopDecoder(decoder: AudioDecoder) {
        decoder.stop()
        if (audioDecoder == decoder) {
            audioDecoder = null
        }
    }

    fun stopCurrentDecoder() {
        audioDecoder?.stop()
        audioDecoder = null
        isAudioDecoderStarting = false
    }
}

@Composable
fun rememberAudioDecoderManager(
    connectionViewModel: ConnectionViewModel,
    audioStream: AudioStream?,
    audioVolume: Float,
): AudioDecoderManager {
    val scope = rememberCoroutineScope()

    val manager =
        remember(audioVolume) {
            AudioDecoderManager(connectionViewModel, audioVolume)
        }

    LaunchedEffect(audioStream) {
        if (audioStream == null) {
            if (manager.audioDecoder != null) {
                LogManager.d(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_STREAM_EMPTY.english)
                manager.stopCurrentDecoder()
            }
            return@LaunchedEffect
        }

        if (audioStream == manager.currentAudioStream) {
            return@LaunchedEffect
        }

        if (manager.audioDecoder != null) {
            LogManager.i(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_STREAM_CHANGED.english)
            manager.stopCurrentDecoder()
        }

        manager.startDecoder(audioStream, scope)
    }

    DisposableEffect(audioStream) {
        onDispose {
            try {
                manager.stopCurrentDecoder()
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.AUDIO_DECODER,
                    "${RemoteTexts.REMOTE_CLEANUP_EXCEPTION.english}: ${e.message}",
                    e,
                )
            }
        }
    }

    return manager
}

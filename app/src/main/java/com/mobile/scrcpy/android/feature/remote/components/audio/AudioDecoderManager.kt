package com.mobile.scrcpy.android.feature.remote.components.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.feature.remote.viewmodel.ConnectionViewModel
import com.mobile.scrcpy.android.feature.session.viewmodel.SessionViewModel
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioDecoder
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 音频解码器管理器
 * 负责音频解码器的生命周期管理
 */
class AudioDecoderManager(
    private val connectionViewModel: ConnectionViewModel,
    private val sessionViewModel: SessionViewModel,
    private val sessionId: String,
    private val audioVolume: Float,
) {
    var audioDecoder: AudioDecoder? = null
        private set

    var currentAudioStream: AudioStream? = null
        private set

    var isAudioDecoderStarting: Boolean = false
        private set

    /**
     * 启动音频解码器
     */
    suspend fun startDecoder(
        stream: AudioStream,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        if (isAudioDecoderStarting) return

        try {
            val codec = stream.codec.lowercase()
            LogManager.d(LogTags.AUDIO_DECODER, "${RemoteTexts.REMOTE_START_AUDIO_DECODER.get()}: codec=$codec")

            // 使用通用 AudioDecoder (支持 opus/raw/aac/flac)
            val decoder =
                AudioDecoder(volumeScale = audioVolume).apply {
                    // 连接丢失回调
                    onConnectionLost = {
                        LogManager.w(LogTags.AUDIO_DECODER, "${RemoteTexts.REMOTE_AUDIO_CONNECTION_LOST.get()}")
                        scope.launch(Dispatchers.Main) {
                            connectionViewModel.handleConnectionLost()
                        }
                    }
                }
            audioDecoder = decoder

            // 使用 Dispatchers.IO 的独立协程，不受 LaunchedEffect 取消影响
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    decoder.start(stream)
                } catch (_: kotlinx.coroutines.CancellationException) {
                    LogManager.d(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_DECODER_CANCELLED.get())
                    stopDecoder(decoder)
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.AUDIO_DECODER,
                        "${RemoteTexts.REMOTE_AUDIO_DECODER_FAILED.get()}: ${e.message}",
                        e,
                    )
                    stopDecoder(decoder)
                }
            }

            currentAudioStream = stream
        } catch (e: Exception) {
            LogManager.e(
                LogTags.AUDIO_DECODER,
                "${RemoteTexts.REMOTE_INIT_AUDIO_DECODER_FAILED.get()}: ${e.message}",
                e,
            )
            audioDecoder = null
        }
    }

    /**
     * 停止音频解码器
     */
    private fun stopDecoder(decoder: AudioDecoder) {
        decoder.stop()
        if (audioDecoder == decoder) {
            audioDecoder = null
        }
    }

    /**
     * 停止当前音频解码器
     */
    fun stopCurrentDecoder() {
        audioDecoder?.stop()
        audioDecoder = null
        isAudioDecoderStarting = false
    }
}

/**
 * Composable 函数：管理音频解码器生命周期
 */
@Composable
fun rememberAudioDecoderManager(
    connectionViewModel: ConnectionViewModel,
    sessionViewModel: SessionViewModel,
    sessionId: String,
    audioStream: AudioStream?,
    audioVolume: Float,
): AudioDecoderManager {
    val scope = rememberCoroutineScope()

    val manager =
        remember(audioVolume) {
            AudioDecoderManager(connectionViewModel, sessionViewModel, sessionId, audioVolume)
        }

    // 音频解码器启动 - 监听 audioStream 变化
    LaunchedEffect(audioStream) {
        val stream = audioStream

        // 如果 stream 为空，停止解码器
        if (stream == null) {
            if (manager.audioDecoder != null) {
                LogManager.d(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_STREAM_EMPTY.get())
                manager.stopCurrentDecoder()
            }
            return@LaunchedEffect
        }

        // 如果已经在处理相同的流，跳过
        if (stream == manager.currentAudioStream) {
            return@LaunchedEffect
        }

        // 如果有旧的解码器，先停止
        if (manager.audioDecoder != null) {
            LogManager.i(LogTags.AUDIO_DECODER, RemoteTexts.REMOTE_AUDIO_STREAM_CHANGED.get())
            manager.stopCurrentDecoder()
        }

        // 启动新的解码器
        manager.startDecoder(stream, scope)
    }

    // 清理解码器
    DisposableEffect(audioStream) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                try {
                    manager.stopCurrentDecoder()
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.AUDIO_DECODER,
                        "${RemoteTexts.REMOTE_CLEANUP_EXCEPTION.get()}: ${e.message}",
                        e,
                    )
                }
            }
        }
    }

    return manager
}

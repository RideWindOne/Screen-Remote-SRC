package com.screen.remote.android.infrastructure.media.audio

data class AudioFrameInfo(
    val pts: Long,
    val isConfig: Boolean,
    val isKeyFrame: Boolean,
)

sealed interface AudioStreamHeader {
    data object Disabled : AudioStreamHeader

    data object ConfigurationError : AudioStreamHeader

    data class Codec(val codec: String) : AudioStreamHeader

    data class Unsupported(val codecId: Int) : AudioStreamHeader
}

internal fun parseAudioStreamHeader(codecId: Int): AudioStreamHeader =
    when (codecId) {
        0 -> AudioStreamHeader.Disabled
        1 -> AudioStreamHeader.ConfigurationError
        0x6f707573 -> AudioStreamHeader.Codec("opus")
        0x00616163 -> AudioStreamHeader.Codec("aac")
        0x666c6163 -> AudioStreamHeader.Codec("flac")
        0x00726177 -> AudioStreamHeader.Codec("raw")
        else -> AudioStreamHeader.Unsupported(codecId)
    }

interface AudioStream : AutoCloseable {
    val codec: String
    val sampleRate: Int
    val channelCount: Int

    fun read(): dadb.AdbShellPacket

    fun currentFrameInfo(): AudioFrameInfo? = null
}

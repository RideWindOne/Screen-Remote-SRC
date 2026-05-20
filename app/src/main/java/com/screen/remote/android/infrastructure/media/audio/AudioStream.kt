package com.screen.remote.android.infrastructure.media.audio

interface AudioStream : AutoCloseable {
    val codec: String
    val sampleRate: Int
    val channelCount: Int

    fun read(): dadb.AdbShellPacket
}

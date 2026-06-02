package com.screen.remote.android.infrastructure.media.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStreamHeaderTest {
    @Test
    fun `known codec ids map to the negotiated scrcpy audio formats`() {
        assertEquals(AudioStreamHeader.Codec("opus"), parseAudioStreamHeader(0x6f707573))
        assertEquals(AudioStreamHeader.Codec("aac"), parseAudioStreamHeader(0x00616163))
        assertEquals(AudioStreamHeader.Codec("flac"), parseAudioStreamHeader(0x666c6163))
        assertEquals(AudioStreamHeader.Codec("raw"), parseAudioStreamHeader(0x00726177))
    }

    @Test
    fun `zero means audio is disabled and one means server configuration failed`() {
        assertTrue(parseAudioStreamHeader(0) === AudioStreamHeader.Disabled)
        assertTrue(parseAudioStreamHeader(1) === AudioStreamHeader.ConfigurationError)
    }

    @Test
    fun `unknown codec id is preserved for a useful protocol error`() {
        val codecId = 0x12345678

        assertEquals(AudioStreamHeader.Unsupported(codecId), parseAudioStreamHeader(codecId))
    }
}

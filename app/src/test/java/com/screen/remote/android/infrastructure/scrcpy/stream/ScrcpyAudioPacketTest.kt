package com.screen.remote.android.infrastructure.scrcpy.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyAudioPacketTest {
    @Test
    fun `recognizes common mono and stereo opus silence packets`() {
        assertTrue(isOpusSilencePacket("opus", byteArrayOf(0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte())))
        assertTrue(isOpusSilencePacket("opus", byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte())))
    }

    @Test
    fun `does not classify other short packets as opus silence`() {
        assertFalse(isOpusSilencePacket("flac", byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte())))
        assertFalse(isOpusSilencePacket("opus", byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFD.toByte())))
        assertFalse(isOpusSilencePacket("opus", byteArrayOf(0xFC.toByte(), 0xFF.toByte())))
    }
}

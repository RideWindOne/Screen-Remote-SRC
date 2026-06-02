package com.screen.remote.android.infrastructure.media.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AacConfigParserTest {
    @Test
    fun `parses common AAC LC 48 kHz stereo AudioSpecificConfig`() {
        // audioObjectType=2 (AAC-LC), samplingFrequencyIndex=3 (48 kHz), channelConfiguration=2.
        val audioSpecificConfig = byteArrayOf(0x11, 0x90.toByte())

        val config = AacConfigParser.parse(audioSpecificConfig)

        assertNotNull(config)
        assertEquals(2, config!!.audioObjectType)
        assertEquals(48_000, config.sampleRate)
        assertEquals(2, config.channelCount)
    }

    @Test
    fun `rejects truncated AudioSpecificConfig`() {
        assertNull(AacConfigParser.parse(byteArrayOf(0x11)))
    }

    @Test
    fun `channel configuration seven maps to eight output channels`() {
        val config = AacConfigParser.parse(byteArrayOf(0x11, 0xB8.toByte()))

        assertNotNull(config)
        assertEquals(8, config!!.channelCount)
    }

    @Test
    fun `parses explicit sampling frequency`() {
        val config = AacConfigParser.parse(packBits("00010" + "1111" + "000000001011101110000000" + "0010"))

        assertNotNull(config)
        assertEquals(48_000, config!!.sampleRate)
        assertEquals(2, config.channelCount)
    }

    @Test
    fun `rejects reserved audio object type zero`() {
        assertNull(AacConfigParser.parse(byteArrayOf(0x01, 0x90.toByte())))
    }

    private fun packBits(bits: String): ByteArray =
        bits.padEnd(((bits.length + 7) / 8) * 8, '0')
            .chunked(8)
            .map { it.toInt(2).toByte() }
            .toByteArray()
}

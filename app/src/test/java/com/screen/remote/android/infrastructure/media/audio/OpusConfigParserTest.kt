package com.screen.remote.android.infrastructure.media.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusConfigParserTest {
    @Test
    fun `parse extracts opus header fields`() {
        val header = buildOpusHead(channelCount = 1, preSkipSamples = 312, inputSampleRate = 48_000)

        val config = OpusConfigParser.parse(header)

        assertNotNull(config)
        assertEquals(1, config!!.version)
        assertEquals(1, config.channelCount)
        assertEquals(312, config.preSkipSamples)
        assertEquals(48_000, config.originalSampleRate)
        assertEquals(0, config.outputGain)
        assertEquals(0, config.channelMappingFamily)
    }

    @Test
    fun `buildInitializationData adds codec delay and seek preroll`() {
        val header = buildOpusHead(channelCount = 2, preSkipSamples = 960, inputSampleRate = 44_100)
        val config = OpusConfigParser.parse(header)!!

        val initData = OpusConfigParser.buildInitializationData(config)

        assertEquals(3, initData.size)
        assertArrayEquals(header, initData[0])
        assertEquals(20_000_000L, readNativeOrderLong(initData[1]))
        assertEquals(80_000_000L, readNativeOrderLong(initData[2]))
    }

    @Test
    fun `parse rejects non opus header`() {
        val invalid = ByteArray(OpusConfigParser.OPUS_HEADER_SIZE)

        assertNull(OpusConfigParser.parse(invalid))
    }

    private fun buildOpusHead(
        channelCount: Int,
        preSkipSamples: Int,
        inputSampleRate: Int,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(OpusConfigParser.OPUS_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("OpusHead".toByteArray(Charsets.US_ASCII))
        buffer.put(1)
        buffer.put(channelCount.toByte())
        buffer.putShort(preSkipSamples.toShort())
        buffer.putInt(inputSampleRate)
        buffer.putShort(0)
        buffer.put(0)
        return buffer.array()
    }

    private fun readNativeOrderLong(data: ByteArray): Long =
        ByteBuffer
            .wrap(data)
            .order(ByteOrder.nativeOrder())
            .long
}

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

    @Test
    fun `parse accepts complete extended channel mapping header`() {
        val mapping = byteArrayOf(0, 4, 1, 2, 3, 5)
        val header = buildMappedOpusHead(channelCount = 6, streamCount = 4, coupledStreamCount = 2, mapping = mapping)

        val config = OpusConfigParser.parse(header)

        assertNotNull(config)
        assertEquals(6, config!!.channelCount)
        assertEquals(1, config.channelMappingFamily)
        assertArrayEquals(header, config.header)
        assertArrayEquals(mapping, config.header.copyOfRange(21, config.header.size))
    }

    @Test
    fun `parse rejects truncated extended channel mapping header`() {
        val complete =
            buildMappedOpusHead(
                channelCount = 6,
                streamCount = 4,
                coupledStreamCount = 2,
                mapping = byteArrayOf(0, 4, 1, 2, 3, 5),
            )

        assertNull(OpusConfigParser.parse(complete.copyOf(complete.size - 1)))
    }

    @Test
    fun `parse rejects invalid stream and coupled stream counts`() {
        val invalid =
            buildMappedOpusHead(
                channelCount = 6,
                streamCount = 3,
                coupledStreamCount = 1,
                mapping = byteArrayOf(0, 1, 2, 3, 0, 1),
            )

        assertNull(OpusConfigParser.parse(invalid))
    }

    @Test
    fun `parse rejects future incompatible header version`() {
        val header = buildOpusHead(channelCount = 2, preSkipSamples = 312, inputSampleRate = 48_000)
        header[8] = 16

        assertNull(OpusConfigParser.parse(header))
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

    private fun buildMappedOpusHead(
        channelCount: Int,
        streamCount: Int,
        coupledStreamCount: Int,
        mapping: ByteArray,
    ): ByteArray {
        require(mapping.size == channelCount)
        val buffer =
            ByteBuffer
                .allocate(21 + channelCount)
                .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("OpusHead".toByteArray(Charsets.US_ASCII))
        buffer.put(1)
        buffer.put(channelCount.toByte())
        buffer.putShort(312.toShort())
        buffer.putInt(48_000)
        buffer.putShort(0)
        buffer.put(1) // channel mapping family
        buffer.put(streamCount.toByte())
        buffer.put(coupledStreamCount.toByte())
        buffer.put(mapping)
        return buffer.array()
    }

    private fun readNativeOrderLong(data: ByteArray): Long =
        ByteBuffer
            .wrap(data)
            .order(ByteOrder.nativeOrder())
            .long
}

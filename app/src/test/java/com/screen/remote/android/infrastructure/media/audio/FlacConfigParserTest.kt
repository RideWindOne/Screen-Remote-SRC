package com.screen.remote.android.infrastructure.media.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FlacConfigParserTest {
    @Test
    fun `parseStreamInfo extracts flac fields`() {
        val streamInfo =
            byteArrayOf(
                0x10, 0x00, 0x10, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x0B, 0xB8.toByte(), 0x02, 0xF0.toByte(),
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            )

        val parsed = FlacConfigParser.parseStreamInfo(streamInfo)

        assertNotNull(parsed)
        assertEquals(4096, parsed!!.minBlockSize)
        assertEquals(4096, parsed.maxBlockSize)
        assertEquals(48_000, parsed.sampleRate)
        assertEquals(2, parsed.channelCount)
        assertEquals(16, parsed.bitsPerSample)
        assertEquals(0L, parsed.totalSamples)
    }

    @Test
    fun `buildInitializationData prefixes marker and metadata header`() {
        val streamInfo = ByteArray(FlacConfigParser.STREAM_INFO_SIZE) { 0 }

        val initData = FlacConfigParser.buildInitializationData(streamInfo)

        assertEquals(42, initData.size)
        assertArrayEquals(byteArrayOf(0x66, 0x4C, 0x61, 0x43), initData.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x22), initData.copyOfRange(4, 8))
        assertArrayEquals(streamInfo, initData.copyOfRange(8, initData.size))
    }
}

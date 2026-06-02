package com.screen.remote.android.infrastructure.media.video

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class VideoNalParserTest {
    private val parser = VideoNalParser()

    @Test
    fun `extracts adjacent NAL units with three and four byte start codes`() {
        val first = byteArrayOf(0, 0, 1, 0x65, 0x11)
        val second = byteArrayOf(0, 0, 0, 1, 0x41, 0x22)
        val buffer = ByteBuffer.allocate(32).apply {
            put(first)
            put(second)
        }

        assertArrayEquals(first, parser.extractNalUnit(buffer))
        assertArrayEquals(second, parser.extractNalUnit(buffer))
    }

    @Test
    fun `recognizes both Annex B start code lengths`() {
        assertTrue(parser.isNalStartCode(byteArrayOf(0, 0, 1, 0x01)))
        assertTrue(parser.isNalStartCode(byteArrayOf(0, 0, 0, 1, 0x01)))
    }

    @Test
    fun `reads H264 NAL type after either start code`() {
        assertEquals(VideoNalParser.H264_NAL_IDR, parser.getH264NalType(byteArrayOf(0, 0, 1, 0x65)))
        assertEquals(VideoNalParser.H264_NAL_SPS, parser.getH264NalType(byteArrayOf(0, 0, 0, 1, 0x67)))
    }

    @Test
    fun `reads H265 NAL type after either start code`() {
        val idrHeader = (VideoNalParser.H265_NAL_IDR_W_RADL shl 1).toByte()
        val vpsHeader = (VideoNalParser.H265_NAL_VPS shl 1).toByte()

        assertEquals(
            VideoNalParser.H265_NAL_IDR_W_RADL,
            parser.getH265NalType(byteArrayOf(0, 0, 1, idrHeader)),
        )
        assertEquals(
            VideoNalParser.H265_NAL_VPS,
            parser.getH265NalType(byteArrayOf(0, 0, 0, 1, vpsHeader)),
        )
    }

    @Test
    fun `preserves split start code until following bytes arrive`() {
        val buffer = ByteBuffer.allocate(32).apply { put(byteArrayOf(0, 0)) }

        assertNull(parser.extractNalUnit(buffer))
        assertEquals(2, buffer.position())

        buffer.put(byteArrayOf(1, 0x67, 0x22))
        assertArrayEquals(byteArrayOf(0, 0, 1, 0x67, 0x22), parser.extractNalUnit(buffer))
    }

    @Test
    fun `preserves bytes when no Annex B start code exists`() {
        val payload = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val buffer = ByteBuffer.allocate(32).apply { put(payload) }

        assertNull(parser.extractNalUnit(buffer))
        assertEquals(payload.size, buffer.position())
        buffer.flip()
        val retained = ByteArray(buffer.remaining()).also(buffer::get)
        assertArrayEquals(payload, retained)
    }
}

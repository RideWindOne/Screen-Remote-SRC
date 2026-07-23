package com.screen.remote.android.infrastructure.media.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNalParserTest {
    @Test
    fun `extracts every NAL unit from one codec config packet`() {
        val packet =
            byteArrayOf(
                0, 0, 0, 1, 0x67, 1, 2,
                0, 0, 1, 0x68, 3,
            )

        val units = parser.extractNalUnits(packet)

        assertEquals(2, units.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x67, 1, 2), units[0])
        assertArrayEquals(byteArrayOf(0, 0, 1, 0x68, 3), units[1])
    }

    @Test
    fun `config packet without Annex-B start code returns no units`() {
        assertTrue(parser.extractNalUnits(byteArrayOf(1, 2, 3, 4)).isEmpty())
    }

    private val parser = VideoNalParser()

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

}

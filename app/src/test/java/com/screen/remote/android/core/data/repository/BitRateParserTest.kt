package com.screen.remote.android.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitRateParserTest {
    @Test
    fun `parseBitRate supports scrcpy style units`() {
        assertEquals(8_000_000, parseBitRate("8M"))
        assertEquals(4_500_000, parseBitRate("4.5m"))
        assertEquals(720_000, parseBitRate("720K"))
        assertEquals(128_000, parseBitRate("128k"))
        assertEquals(8000000, parseBitRate("8000000"))
    }

    @Test
    fun `parseBitRate accepts arbitrary numeric values instead of a fixed list`() {
        assertEquals(1_000_000, parseBitRate("1M"))
        assertEquals(2_250_000, parseBitRate("2.25M"))
        assertEquals(64_000, parseBitRate("64k"))
        assertEquals(333_333, parseBitRate("333333"))
    }

    @Test
    fun `parseBitRate rejects blank or malformed values`() {
        assertNull(parseBitRate(""))
        assertNull(parseBitRate(" "))
        assertNull(parseBitRate("fast"))
        assertNull(parseBitRate("8mbps"))
    }

    @Test
    fun `options round trip preserves the exact bitrate text when its value is unchanged`() {
        listOf("1M", "1m", "700k", "700K").forEach { text ->
            assertEquals(text, preserveBitRateText(text, parseBitRate(text)!!))
        }
    }

    @Test
    fun `options round trip replaces bitrate text when its value changes`() {
        assertEquals("2000000", preserveBitRateText("1M", 2_000_000))
    }
}

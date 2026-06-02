package com.screen.remote.android.infrastructure.scrcpy.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCodecHeaderTest {
    @Test
    fun `maps every scrcpy video wire id`() {
        assertEquals("h264", videoCodecFromId(0x68323634))
        assertEquals("h265", videoCodecFromId(0x68323635))
        assertEquals("av1", videoCodecFromId(0x00617631))
        assertEquals("vp8", videoCodecFromId(0x00767038))
        assertEquals("vp9", videoCodecFromId(0x00767039))
    }

    @Test
    fun `unknown video wire id is rejected`() {
        assertNull(videoCodecFromId(0x12345678))
    }
}

package com.screen.remote.android.infrastructure.media.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPacketCodecModeTest {
    @Test
    fun vp8AndVp9UseVpxPacketPath() {
        assertEquals(VideoPacketCodecMode.VPX, videoPacketCodecMode("vp8"))
        assertEquals(VideoPacketCodecMode.VPX, videoPacketCodecMode("VP9"))
    }

    @Test
    fun existingCodecAliasesKeepTheirPacketPaths() {
        assertEquals(VideoPacketCodecMode.H264, videoPacketCodecMode("avc"))
        assertEquals(VideoPacketCodecMode.H265, videoPacketCodecMode("hevc"))
        assertEquals(VideoPacketCodecMode.AV1, videoPacketCodecMode("av1"))
    }

    @Test
    fun unknownCodecIsRejectedByPacketRouter() {
        assertEquals(VideoPacketCodecMode.UNSUPPORTED, videoPacketCodecMode("unknown"))
    }
}

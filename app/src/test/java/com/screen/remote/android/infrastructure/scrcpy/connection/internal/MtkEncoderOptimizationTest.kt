package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MtkEncoderOptimizationTest {
    @Test
    fun `mtk avc encoder receives anti degradation and low latency options`() {
        val options = buildVideoCodecOptions("", "c2.mtk.avc.encoder", "h264")

        assertEquals(
            "profile=1,max-bframes=0,i-frame-interval=10,priority=0,bitrate-mode=1,video-qp-max=35",
            options,
        )
    }

    @Test
    fun `user options override mtk defaults including typed keys`() {
        val options =
            buildVideoCodecOptions(
                " video-qp-max:int=32, bitrate-mode=2,custom-key=7 ",
                "OMX.MTK.VIDEO.ENCODER.AVC",
                "H264",
            )

        assertTrue(options!!.contains("video-qp-max:int=32"))
        assertTrue(options.contains("bitrate-mode=2"))
        assertTrue(options.contains("custom-key=7"))
        assertFalse(options.contains("video-qp-max=35"))
        assertFalse(options.contains("bitrate-mode=1"))
    }

    @Test
    fun `mtk optimization is not applied to other codecs or vendors`() {
        assertNull(buildVideoCodecOptions("", "c2.mtk.hevc.encoder", "h265"))
        assertNull(buildVideoCodecOptions("", "c2.qti.avc.encoder", "h264"))
        assertEquals("priority=1", buildVideoCodecOptions("priority=1", "c2.qti.avc.encoder", "h264"))
    }

    @Test
    fun `mtk encoder detection only accepts known mtk implementation prefixes`() {
        assertTrue(isMtkAvcEncoder("c2.mtk.avc.encoder", "h264"))
        assertTrue(isMtkAvcEncoder("OMX.MTK.VIDEO.ENCODER.AVC", "h264"))
        assertFalse(isMtkAvcEncoder("vendor.example.mtk.avc.encoder", "h264"))
    }
}

package com.screen.remote.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecCatalogTest {
    @Test
    fun `catalog resolves every negotiated video and audio format by name and MIME`() {
        val expected =
            mapOf(
                CodecMediaType.VIDEO to
                    mapOf(
                        "h264" to "video/avc",
                        "h265" to "video/hevc",
                        "av1" to "video/av01",
                        "vp9" to "video/x-vnd.on2.vp9",
                        "vp8" to "video/x-vnd.on2.vp8",
                    ),
                CodecMediaType.AUDIO to
                    mapOf(
                        "opus" to "audio/opus",
                        "aac" to "audio/mp4a-latm",
                        "flac" to "audio/flac",
                        "raw" to "audio/raw",
                    ),
            )

        expected.forEach { (mediaType, formats) ->
            formats.forEach { (codec, mimeType) ->
                assertEquals(mimeType, CodecCatalog.find(mediaType, codec)?.mimeType)
                assertEquals(codec, CodecCatalog.find(mediaType, mimeType.uppercase())?.name)
                assertEquals(codec, CodecCatalog.findByMimeType(mediaType, mimeType.uppercase())?.name)
            }
        }
    }

    @Test
    fun `catalog recognizes protocol aliases but does not guess an unrelated implementation`() {
        assertEquals("h264", CodecCatalog.find(CodecMediaType.VIDEO, "avc")?.name)
        assertEquals("h265", CodecCatalog.find(CodecMediaType.VIDEO, "hevc")?.name)
        assertEquals("aac", CodecCatalog.find(CodecMediaType.AUDIO, "mp4a")?.name)
        assertEquals("raw", CodecCatalog.find(CodecMediaType.AUDIO, "pcm")?.name)
        assertNull(CodecCatalog.inferFromImplementationName(CodecMediaType.VIDEO, "vendor.decoder.42"))
    }

    @Test
    fun `preferred format is first and the remaining catalog order is stable`() {
        assertEquals(
            listOf("vp9", "h264", "h265", "av1", "vp8"),
            CodecCatalog.orderedSpecs(CodecMediaType.VIDEO, "vp9").map { it.name },
        )
    }
}

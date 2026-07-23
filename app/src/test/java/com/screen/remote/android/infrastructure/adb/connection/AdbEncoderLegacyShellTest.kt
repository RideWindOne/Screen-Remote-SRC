package com.screen.remote.android.infrastructure.adb.connection

import dadb.AdbShellProtocol
import dadb.AdbShellStream
import dadb.AdbStream
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbEncoderLegacyShellTest {
    @Test
    fun `encoder output reader accepts legacy shell output`() {
        val expectedOutput =
            """
            List of video encoders:
                --video-codec=h264 --video-encoder=OMX.google.h264.encoder (sw)
            """.trimIndent()
        val source = Buffer().writeUtf8(expectedOutput)
        val stream =
            AdbShellStream(
                stream =
                    object : AdbStream {
                        override val source = source
                        override val sink = Buffer()

                        override fun close() = Unit
                    },
                protocol = AdbShellProtocol.LEGACY,
            )

        assertEquals(expectedOutput, AdbEncoderShellStreamReader.read(stream))
    }
}

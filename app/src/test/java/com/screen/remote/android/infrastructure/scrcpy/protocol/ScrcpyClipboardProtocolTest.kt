package com.screen.remote.android.infrastructure.scrcpy.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class ScrcpyClipboardProtocolTest {
    @Test
    fun `Chinese text is encoded and decoded as UTF-8`() {
        val text = "中文剪贴板：你好，世界！繁體與简体"

        val decoded = decode(ScrcpyClipboardProtocol.encode(text, paste = true))

        assertEquals(text, decoded.text)
        assertTrue(decoded.paste)
        assertEquals(text.toByteArray(Charsets.UTF_8).size, decoded.length)
    }

    @Test
    fun `symbols emoji newlines quotes and null bytes survive unchanged`() {
        val text = "'\"`\\\$()[]{}<>|&;\n\r\t\u0000 © € ™ ✓ 中文 👨‍👩‍👧‍👦 e\u0301"

        val decoded = decode(ScrcpyClipboardProtocol.encode(text, paste = false))

        assertEquals(text, decoded.text)
        assertFalse(decoded.paste)
        assertArrayEquals(text.toByteArray(Charsets.UTF_8), decoded.rawText)
    }

    @Test
    fun `maximum size mixed Unicode clipboard message fits protocol packet`() {
        val max = ScrcpyProtocol.CLIPBOARD_TEXT_MAX_LENGTH
        val text = "中".repeat(max / 3) + "a".repeat(max % 3)

        val packet = ScrcpyClipboardProtocol.encode(text, paste = true)
        val decoded = decode(packet)

        assertEquals(ScrcpyProtocol.CONTROL_MESSAGE_MAX_SIZE, packet.size)
        assertEquals(max, decoded.length)
        assertEquals(text, decoded.text)
    }

    @Test
    fun `content over maximum UTF-8 byte length is rejected`() {
        val max = ScrcpyProtocol.CLIPBOARD_TEXT_MAX_LENGTH
        val text = "a".repeat(max) + "中"

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                ScrcpyClipboardProtocol.encode(text, paste = true)
            }

        assertTrue(error.message.orEmpty().contains(max.toString()))
    }

    private fun decode(packet: ByteArray): DecodedClipboard {
        val input = DataInputStream(ByteArrayInputStream(packet))
        assertEquals(ScrcpyProtocol.MSG_TYPE_SET_CLIPBOARD, input.readUnsignedByte())
        assertEquals(-1L, input.readLong())
        val paste = input.readUnsignedByte() != 0
        val length = input.readInt()
        val rawText = ByteArray(length).also(input::readFully)
        assertEquals(0, input.available())
        return DecodedClipboard(
            paste = paste,
            length = length,
            rawText = rawText,
            text = rawText.toString(Charsets.UTF_8),
        )
    }

    private data class DecodedClipboard(
        val paste: Boolean,
        val length: Int,
        val rawText: ByteArray,
        val text: String,
    )
}

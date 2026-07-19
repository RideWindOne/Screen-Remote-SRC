package com.screen.remote.android.infrastructure.scrcpy.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class ScrcpyDeviceMessageReaderTest {
    @Test
    fun `remote Chinese and special clipboard message decodes exactly`() {
        val text = "远端→本机：'\"\\\n\u0000 👨‍👩‍👧‍👦 e\u0301"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val packet = packet {
            writeByte(0)
            writeInt(bytes.size)
            write(bytes)
        }

        val message = ScrcpyDeviceMessageReader.read(input(packet)) as ScrcpyDeviceMessage.Clipboard

        assertEquals(text, message.text)
    }

    @Test
    fun `ack and uhid messages are fully consumed without corrupting stream`() {
        val packet = packet {
            writeByte(1)
            writeLong(42L)
            writeByte(2)
            writeShort(7)
            writeShort(3)
            write(byteArrayOf(1, 2, 3))
        }
        val input = input(packet)

        assertEquals(ScrcpyDeviceMessage.ClipboardAck(42L), ScrcpyDeviceMessageReader.read(input))
        val uhid = ScrcpyDeviceMessageReader.read(input) as ScrcpyDeviceMessage.UhidOutput
        assertEquals(7, uhid.id)
        assertArrayEquals(byteArrayOf(1, 2, 3), uhid.data)
        assertEquals(0, input.available())
    }

    @Test
    fun `invalid oversized remote clipboard length is rejected before allocation`() {
        val packet = packet {
            writeByte(0)
            writeInt(1 shl 18)
        }

        assertThrows(IOException::class.java) {
            ScrcpyDeviceMessageReader.read(input(packet))
        }
    }

    private fun packet(write: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().let { bytes ->
            DataOutputStream(bytes).use { output -> write(output) }
            bytes.toByteArray()
        }

    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))
}

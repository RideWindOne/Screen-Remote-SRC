package com.screen.remote.android.infrastructure.adb.usb

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbAdbPacketCodecTest {
    @Test
    fun `completePacketLength returns null until the full packet is buffered`() {
        val packet = AdbProtocol.generateWrite(localId = 1, remoteId = 2, data = "hello".toByteArray()).toByteArray()
        val pending = Buffer()

        pending.write(packet, 0, AdbProtocol.ADB_HEADER_LENGTH - 1)
        assertNull(UsbAdbPacketCodec.completePacketLength(pending))

        pending.write(packet, AdbProtocol.ADB_HEADER_LENGTH - 1, 1)
        assertNull(UsbAdbPacketCodec.completePacketLength(pending))

        pending.write(packet, AdbProtocol.ADB_HEADER_LENGTH, packet.size - AdbProtocol.ADB_HEADER_LENGTH)
        assertEquals(packet.size.toLong(), UsbAdbPacketCodec.completePacketLength(pending))
    }

    @Test
    fun `completePacketLength can drain concatenated packets one by one`() {
        val firstPacket = AdbProtocol.generateOpen(localId = 7, dest = "shell,v2,raw:echo one").toByteArray()
        val secondPacket = AdbProtocol.generateWrite(localId = 7, remoteId = 8, data = "two".toByteArray()).toByteArray()
        val pending =
            Buffer()
                .write(firstPacket)
                .write(secondPacket)

        val firstLength = UsbAdbPacketCodec.completePacketLength(pending)
        assertEquals(firstPacket.size.toLong(), firstLength)
        assertEquals(firstPacket.size.toLong(), pending.readByteArray(firstLength!!).size.toLong())

        val secondLength = UsbAdbPacketCodec.completePacketLength(pending)
        assertEquals(secondPacket.size.toLong(), secondLength)
    }

    @Test
    fun `parseHeader rejects invalid magic`() {
        val packet = AdbProtocol.generateOkay(localId = 11, remoteId = 22).toByteArray()
        val header = packet.copyOf(AdbProtocol.ADB_HEADER_LENGTH)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(20, 0)

        assertThrows(IOException::class.java) {
            UsbAdbPacketCodec.parseHeader(header)
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }
}

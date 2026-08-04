package com.screen.remote.android.infrastructure.adb.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NearbyAdbScannerTest {
    @Test
    fun scanRangesMatchTheConfiguredStrategy() {
        assertEquals(5550, NearbyAdbScanner.COMMON_ADB_PORTS.first)
        assertEquals(5560, NearbyAdbScanner.COMMON_ADB_PORTS.last)
        assertEquals(30000, NearbyAdbScanner.DYNAMIC_ADB_PORTS.first)
        assertEquals(65535, NearbyAdbScanner.DYNAMIC_ADB_PORTS.last)
        assertEquals(listOf(515, 548, 631, 9100, 62078), NearbyAdbScanner.NON_ANDROID_HINT_PORTS)
    }

    @Test
    fun connectPacketContainsAValidAdbHeader() {
        val packet = buildAdbConnectPacket()
        val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        header.int
        header.int
        val payloadLength = header.int
        val checksum = header.int
        val magic = header.int
        val payload = packet.copyOfRange(24, packet.size)

        assertEquals(0x4e584e43, command)
        assertEquals(command xor -1, magic)
        assertEquals(payload.size, payloadLength)
        assertEquals(payload.sumOf { it.toInt() and 0xff }, checksum)
        assertEquals("host::", payload.toString(Charsets.UTF_8))
    }

    @Test
    fun responseHeaderRecognizesLegacyAndTlsAdb() {
        assertEquals(NearbyAdbProtocol.TCP, parseAdbResponseHeader(responseHeader(0x48545541)))
        assertEquals(NearbyAdbProtocol.TCP, parseAdbResponseHeader(responseHeader(0x4e584e43)))
        assertEquals(NearbyAdbProtocol.TLS, parseAdbResponseHeader(responseHeader(0x534c5453)))
    }

    @Test
    fun responseHeaderRejectsUnknownOrInvalidPackets() {
        assertNull(parseAdbResponseHeader(responseHeader(0x12345678)))
        val invalidMagic = responseHeader(0x48545541)
        invalidMagic[20] = 0
        invalidMagic[21] = 0
        invalidMagic[22] = 0
        invalidMagic[23] = 0
        assertNull(parseAdbResponseHeader(invalidMagic))
        assertTrue(parseAdbResponseHeader(ByteArray(23)) == null)
    }

    private fun responseHeader(command: Int): ByteArray =
        ByteBuffer
            .allocate(24)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(command xor -1)
            .array()
}

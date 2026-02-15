package com.mobile.scrcpy.android.infrastructure.adb.usb

import okio.Buffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object UsbAdbPacketCodec {
    private const val MAX_ADB_PAYLOAD_LENGTH = 1024 * 1024

    data class Header(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payloadLength: Int,
        val checksum: Int,
        val magic: Int,
    )

    @Throws(IOException::class)
    fun parseHeader(headerBytes: ByteArray): Header {
        require(headerBytes.size == AdbProtocol.ADB_HEADER_LENGTH) {
            "ADB header must be ${AdbProtocol.ADB_HEADER_LENGTH} bytes, was ${headerBytes.size}"
        }

        val header =
            ByteBuffer.wrap(headerBytes)
                .order(ByteOrder.LITTLE_ENDIAN)

        val packetHeader =
            Header(
                command = header.getInt(),
                arg0 = header.getInt(),
                arg1 = header.getInt(),
                payloadLength = header.getInt(),
                checksum = header.getInt(),
                magic = header.getInt(),
            )

        validateHeader(packetHeader)
        return packetHeader
    }

    @Throws(IOException::class)
    fun completePacketLength(buffer: Buffer): Long? {
        if (buffer.size < AdbProtocol.ADB_HEADER_LENGTH) {
            return null
        }

        val headerBytes = buffer.copy().readByteArray(AdbProtocol.ADB_HEADER_LENGTH.toLong())
        val header = parseHeader(headerBytes)
        val packetLength = AdbProtocol.ADB_HEADER_LENGTH + header.payloadLength.toLong()
        return if (buffer.size >= packetLength) packetLength else null
    }

    @Throws(IOException::class)
    private fun validateHeader(header: Header) {
        if (header.command !in VALID_COMMANDS) {
            throw IOException("Unknown ADB command: 0x${header.command.toUInt().toString(16)}")
        }
        if (header.magic != (header.command xor -0x1)) {
            throw IOException(
                "Invalid ADB magic for command 0x${header.command.toUInt().toString(16)}: ${header.magic}",
            )
        }
        if (header.payloadLength < 0 || header.payloadLength > MAX_ADB_PAYLOAD_LENGTH) {
            throw IOException("Invalid ADB payload length: ${header.payloadLength}")
        }
    }

    private val VALID_COMMANDS =
        setOf(
            AdbProtocol.CMD_AUTH,
            AdbProtocol.CMD_CNXN,
            AdbProtocol.CMD_OPEN,
            AdbProtocol.CMD_OKAY,
            AdbProtocol.CMD_CLSE,
            AdbProtocol.CMD_WRTE,
        )
}

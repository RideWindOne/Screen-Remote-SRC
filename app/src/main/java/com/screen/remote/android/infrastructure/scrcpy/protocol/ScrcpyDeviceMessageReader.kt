package com.screen.remote.android.infrastructure.scrcpy.protocol

import java.io.DataInputStream
import java.io.IOException

internal sealed interface ScrcpyDeviceMessage {
    data class Clipboard(val text: String) : ScrcpyDeviceMessage
    data class ClipboardAck(val sequence: Long) : ScrcpyDeviceMessage
    data class UhidOutput(val id: Int, val data: ByteArray) : ScrcpyDeviceMessage {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UhidOutput) return false
            return id == other.id && data.contentEquals(other.data)
        }

        override fun hashCode(): Int = 31 * id + data.contentHashCode()
    }
}

internal object ScrcpyDeviceMessageReader {
    private const val TYPE_CLIPBOARD = 0
    private const val TYPE_ACK_CLIPBOARD = 1
    private const val TYPE_UHID_OUTPUT = 2
    private const val CLIPBOARD_TEXT_MAX_LENGTH = (1 shl 18) - 5

    fun read(input: DataInputStream): ScrcpyDeviceMessage =
        when (val type = input.readUnsignedByte()) {
            TYPE_CLIPBOARD -> {
                val length = input.readInt()
                if (length !in 0..CLIPBOARD_TEXT_MAX_LENGTH) {
                    throw IOException("Invalid device clipboard length: $length")
                }
                val bytes = ByteArray(length).also(input::readFully)
                ScrcpyDeviceMessage.Clipboard(bytes.toString(Charsets.UTF_8))
            }

            TYPE_ACK_CLIPBOARD -> ScrcpyDeviceMessage.ClipboardAck(input.readLong())
            TYPE_UHID_OUTPUT -> {
                val id = input.readUnsignedShort()
                val length = input.readUnsignedShort()
                ScrcpyDeviceMessage.UhidOutput(id, ByteArray(length).also(input::readFully))
            }

            else -> throw IOException("Unknown scrcpy device message type: $type")
        }
}

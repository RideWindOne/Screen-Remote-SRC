package com.screen.remote.android.infrastructure.scrcpy.protocol

import java.nio.ByteBuffer

/** Encodes scrcpy SET_CLIPBOARD control messages using their UTF-8 wire length. */
internal object ScrcpyClipboardProtocol {
    fun encode(
        text: String,
        paste: Boolean,
    ): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        require(textBytes.size <= ScrcpyProtocol.CLIPBOARD_TEXT_MAX_LENGTH) {
            "Clipboard text exceeds ${ScrcpyProtocol.CLIPBOARD_TEXT_MAX_LENGTH} UTF-8 bytes"
        }

        return ByteBuffer
            .allocate(CLIPBOARD_HEADER_SIZE + textBytes.size)
            .apply {
                put(ScrcpyProtocol.MSG_TYPE_SET_CLIPBOARD.toByte())
                // scrcpy's invalid sequence: no acknowledgement is needed for fire-and-paste.
                putLong(-1L)
                put((if (paste) 1 else 0).toByte())
                putInt(textBytes.size)
                put(textBytes)
            }.array()
    }

    private const val CLIPBOARD_HEADER_SIZE = 14
}

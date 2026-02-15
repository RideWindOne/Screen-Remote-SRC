package com.mobile.scrcpy.android.infrastructure.adb.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import dadb.AdbTransport
import dadb.AdbTransportFactory
import dadb.SourceSinkAdbTransport
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import java.nio.ByteBuffer

class UsbAdbTransportFactory(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice,
    private val deviceId: String,
) : AdbTransportFactory {
    override val description: String = deviceId

    override fun connect(): AdbTransport {
        val channel = UsbAdbChannel(usbManager, usbDevice)
        return SourceSinkAdbTransport(
            source = UsbAdbSource(channel),
            sink = UsbAdbSink(channel),
            description = description,
            connectMaxData = AdbProtocol.CONNECT_MAXDATA,
            closeable = AutoCloseable { channel.close() },
        )
    }
}

private class UsbAdbSource(
    private val channel: UsbAdbChannel,
) : Source {
    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        if (byteCount == 0L) {
            return 0L
        }

        val chunk = channel.readAtMost(minOf(byteCount, UsbConstants.USB_MAX_PACKET_SIZE.toLong()).toInt())
        sink.write(chunk)
        return chunk.size.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}

private class UsbAdbSink(
    private val channel: UsbAdbChannel,
) : Sink {
    private val pending = Buffer()

    override fun write(
        source: Buffer,
        byteCount: Long,
    ) {
        require(byteCount <= Int.MAX_VALUE) { "byteCount too large: $byteCount" }
        pending.write(source, byteCount)
        emitCompletePackets()
    }

    override fun flush() {
        emitCompletePackets()
        channel.flush()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit

    private fun emitCompletePackets() {
        while (true) {
            val packetLength = UsbAdbPacketCodec.completePacketLength(pending) ?: return
            if (pending.size < packetLength) {
                return
            }

            val packet = pending.readByteArray(packetLength)
            channel.write(ByteBuffer.wrap(packet))
        }
    }
}

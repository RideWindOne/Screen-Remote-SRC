/*
 * USB ADB 通道实现
 *
 * 通过 USB 接口实现 ADB 通道，用于 USB ADB 连接
 *
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 * - adblib: https://github.com/tananaev/adblib
 */

package com.screen.remote.android.infrastructure.adb.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * USB ADB 通道实现
 * 通过 USB 批量传输端点实现 ADB 通信
 */
class UsbAdbChannel(
    usbManager: UsbManager,
    private val usbDevice: UsbDevice,
) : AdbChannel {
    // 打开 USB 设备连接
    private val connection: UsbDeviceConnection =
        usbManager.openDevice(usbDevice)
            ?: throw IOException("Failed to open USB device: ${usbDevice.deviceName}")
    private val usbInterface: UsbInterface
    private val endpointIn: UsbEndpoint
    private val endpointOut: UsbEndpoint
    private val readBackgroundThread = Thread(::readBackground, "USB-ADB-Reader")
    private val incomingChunks = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var closed = false

    @Volatile
    private var readError: Throwable? = null

    private var currentChunk: ByteArray? = null
    private var currentChunkOffset = 0

    init {
        // 查找 ADB 接口（USB Class 255, Subclass 66, Protocol 1）
        usbInterface = findAdbInterface()
            ?: throw IOException("ADB interface not found on device: ${usbDevice.deviceName}")

        // 声明接口
        if (!connection.claimInterface(usbInterface, true)) {
            connection.close()
            throw IOException("Failed to claim USB interface")
        }

        // 查找批量传输端点
        val endpoints = findBulkEndpoints()
        endpointIn = endpoints.first
            ?: throw IOException("Bulk IN endpoint not found")
        endpointOut = endpoints.second
            ?: throw IOException("Bulk OUT endpoint not found")

        LogManager.d(
            LogTags.USB_CONNECTION,
            "USB ADB channel initialized: ${usbDevice.deviceName}, " +
                "interfaceId=${usbInterface.id}, " +
                "IN=${endpointIn.address}/max=${endpointIn.maxPacketSize}, " +
                "OUT=${endpointOut.address}/max=${endpointOut.maxPacketSize}",
        )

        readBackgroundThread.start()
    }

    /**
     * 查找 ADB 接口
     */
    private fun findAdbInterface(): UsbInterface? {
        for (i in 0 until usbDevice.interfaceCount) {
            val intf = usbDevice.getInterface(i)
            if (intf.interfaceClass == UsbConstants.ADB_CLASS &&
                intf.interfaceSubclass == UsbConstants.ADB_SUBCLASS &&
                intf.interfaceProtocol == UsbConstants.ADB_PROTOCOL
            ) {
                return intf
            }
        }
        return null
    }

    /**
     * 查找批量传输端点
     * @return Pair(IN endpoint, OUT endpoint)
     */
    private fun findBulkEndpoints(): Pair<UsbEndpoint?, UsbEndpoint?> {
        var endpointIn: UsbEndpoint? = null
        var endpointOut: UsbEndpoint? = null

        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)

            // 查找批量传输端点
            if (endpoint.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                    endpointIn = endpoint
                } else if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT) {
                    endpointOut = endpoint
                }
            }
        }

        return Pair(endpointIn, endpointOut)
    }

    @Synchronized
    override fun write(data: ByteBuffer) {
        val buffer = data.duplicate()
        while (buffer.remaining() > 0) {
            if (buffer.remaining() < AdbProtocol.ADB_HEADER_LENGTH) {
                throw IOException("Incomplete ADB packet header: remaining=${buffer.remaining()}")
            }

            val header = ByteArray(AdbProtocol.ADB_HEADER_LENGTH)
            buffer.get(header)
            val packetHeader = UsbAdbPacketCodec.parseHeader(header)
            LogManager.d(
                LogTags.USB_CONNECTION,
                "USB ADB packet out: command=0x${packetHeader.command.toUInt().toString(16)} " +
                    "arg0=${packetHeader.arg0} arg1=${packetHeader.arg1} payload=${packetHeader.payloadLength}",
            )
            transferOut(header)

            if (buffer.remaining() < packetHeader.payloadLength) {
                throw IOException(
                    "Incomplete ADB packet payload: required=${packetHeader.payloadLength}, remaining=${buffer.remaining()}",
                )
            }

            if (packetHeader.payloadLength > 0) {
                val payload = ByteArray(packetHeader.payloadLength)
                buffer.get(payload)
                transferOut(payload)
            }
        }
    }

    private fun transferOut(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(UsbConstants.USB_MAX_PACKET_SIZE, bytes.size - offset)
            val transferred =
                connection.bulkTransfer(
                    endpointOut,
                    bytes,
                    offset,
                    chunkSize,
                    UsbConstants.USB_WRITE_TIMEOUT,
                )

            if (transferred < 0) {
                throw IOException("USB bulk transfer failed: $transferred")
            }
            if (transferred == 0) {
                throw IOException("USB bulk transfer made no progress for ${usbDevice.deviceName}")
            }

            offset += transferred
        }
    }

    fun readAtMost(size: Int): ByteArray {
        require(size > 0) { "size must be > 0" }

        while (true) {
            val chunk = currentChunk
            if (chunk != null && currentChunkOffset < chunk.size) {
                val copyLength = minOf(size, chunk.size - currentChunkOffset)
                val result = chunk.copyOfRange(currentChunkOffset, currentChunkOffset + copyLength)
                currentChunkOffset += copyLength
                if (currentChunkOffset >= chunk.size) {
                    currentChunk = null
                    currentChunkOffset = 0
                }
                return result
            }

            val nextChunk = incomingChunks.take()

            if (nextChunk.isEmpty()) {
                val error = readError
                if (error != null) {
                    throw IOException("USB read failed: ${error.message}", error)
                }
                throw IOException("USB channel closed: ${usbDevice.deviceName}")
            }

            currentChunk = nextChunk
            currentChunkOffset = 0
        }
    }

    override fun read(size: Int): ByteBuffer {
        require(size >= 0) { "size must be >= 0" }
        if (size == 0) {
            return ByteBuffer.allocate(0)
        }

        val bytes = ByteArray(size)
        var offset = 0

        while (offset < size) {
            val chunk =
                readAtMost(size - offset)
            chunk.copyInto(bytes, destinationOffset = offset)
            offset += chunk.size
        }

        return ByteBuffer.wrap(bytes)
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        incomingChunks.offer(ByteArray(0))

        try {
            readBackgroundThread.interrupt()
        } catch (_: Exception) {
        }

        try {
            // Force the remote ADB USB transport to reset before releasing the interface.
            connection.bulkTransfer(endpointOut, ByteArray(100), 100, 100)
        } catch (_: Exception) {
        }

        try {
            connection.releaseInterface(usbInterface)
        } catch (e: Exception) {
            LogManager.w(LogTags.USB_CONNECTION, "Failed to release USB interface: ${e.message}")
        }

        try {
            connection.close()
        } catch (e: Exception) {
            LogManager.w(LogTags.USB_CONNECTION, "Failed to close USB connection: ${e.message}")
        }

        LogManager.d(LogTags.USB_CONNECTION, "USB ADB channel closed")
    }

    private fun readBackground() {
        try {
            while (!closed && !Thread.currentThread().isInterrupted) {
                val header = readExact(AdbProtocol.ADB_HEADER_LENGTH)
                val packetHeader = UsbAdbPacketCodec.parseHeader(header)
                LogManager.d(
                    LogTags.USB_CONNECTION,
                    "USB ADB packet in: command=0x${packetHeader.command.toUInt().toString(16)} " +
                        "arg0=${packetHeader.arg0} arg1=${packetHeader.arg1} payload=${packetHeader.payloadLength}",
                )
                incomingChunks.put(header)

                if (packetHeader.payloadLength > 0) {
                    incomingChunks.put(readExact(packetHeader.payloadLength))
                }
            }
        } catch (t: Throwable) {
            if (!closed) {
                readError = t
                LogManager.w(LogTags.USB_CONNECTION, "USB ADB read loop stopped: ${t.message}")
            }
        } finally {
            incomingChunks.offer(ByteArray(0))
        }
    }

    private fun readExact(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        var lastProgressAt = System.currentTimeMillis()
        var loggedWait = false

        while (offset < length) {
            if (closed || Thread.currentThread().isInterrupted) {
                throw IOException("USB channel closed while reading: ${usbDevice.deviceName}")
            }

            val transferred =
                connection.bulkTransfer(
                    endpointIn,
                    buffer,
                    offset,
                    length - offset,
                    UsbConstants.USB_READ_TIMEOUT,
                )

            if (transferred < 0) {
                val idleMs = System.currentTimeMillis() - lastProgressAt
                if (!loggedWait && offset == 0) {
                    LogManager.d(
                        LogTags.USB_CONNECTION,
                        "USB read waiting: device=${usbDevice.deviceName} length=$length timeout=${UsbConstants.USB_READ_TIMEOUT}",
                    )
                    loggedWait = true
                }
                if (idleMs >= UsbConstants.USB_READ_IDLE_TIMEOUT) {
                    throw IOException("USB read timed out waiting for ADB data from ${usbDevice.deviceName}")
                }
                continue
            }
            if (transferred == 0) {
                val idleMs = System.currentTimeMillis() - lastProgressAt
                if (!loggedWait && offset == 0) {
                    LogManager.d(
                        LogTags.USB_CONNECTION,
                        "USB read returned 0 bytes while waiting: device=${usbDevice.deviceName} length=$length",
                    )
                    loggedWait = true
                }
                if (idleMs >= UsbConstants.USB_READ_IDLE_TIMEOUT) {
                    throw IOException("USB read timed out waiting for ADB data from ${usbDevice.deviceName}")
                }
                continue
            }

            offset += transferred
            lastProgressAt = System.currentTimeMillis()
            if (offset == transferred) {
                LogManager.d(
                    LogTags.USB_CONNECTION,
                    "USB read progress: device=${usbDevice.deviceName} received=$transferred/$length",
                )
            }
        }

        return buffer
    }
}

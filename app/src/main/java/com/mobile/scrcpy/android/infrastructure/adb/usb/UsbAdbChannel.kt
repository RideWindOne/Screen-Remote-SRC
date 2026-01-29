/*
 * USB ADB 通道实现
 *
 * 通过 USB 接口实现 ADB 通道，用于 USB ADB 连接
 *
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 * - adblib: https://github.com/tananaev/adblib
 */

package com.mobile.scrcpy.android.infrastructure.adb.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import java.io.IOException
import java.nio.ByteBuffer

/**
 * USB ADB 通道实现
 * 通过 USB 批量传输端点实现 ADB 通信
 */
class UsbAdbChannel(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice,
) : AdbChannel {
    private val connection: UsbDeviceConnection
    private val usbInterface: UsbInterface
    private val endpointIn: UsbEndpoint
    private val endpointOut: UsbEndpoint

    init {
        // 打开 USB 设备连接
        connection = usbManager.openDevice(usbDevice)
            ?: throw IOException("Failed to open USB device: ${usbDevice.deviceName}")

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
                "IN=${endpointIn.address}, OUT=${endpointOut.address}",
        )
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

    override fun write(data: ByteBuffer) {
        val array = ByteArray(data.remaining())
        data.duplicate().get(array)

        var offset = 0
        while (offset < array.size) {
            val chunkSize = minOf(UsbConstants.USB_MAX_PACKET_SIZE, array.size - offset)
            val transferred =
                connection.bulkTransfer(
                    endpointOut,
                    array,
                    offset,
                    chunkSize,
                    UsbConstants.USB_TIMEOUT,
                )

            if (transferred < 0) {
                throw IOException("USB bulk transfer failed: $transferred")
            }

            offset += transferred
        }
    }

    override fun read(size: Int): ByteBuffer {
        val buffer = ByteArray(size)
        var offset = 0

        while (offset < size) {
            val remaining = size - offset
            val chunkSize = minOf(UsbConstants.USB_MAX_PACKET_SIZE, remaining)

            val transferred =
                connection.bulkTransfer(
                    endpointIn,
                    buffer,
                    offset,
                    chunkSize,
                    UsbConstants.USB_TIMEOUT,
                )

            if (transferred < 0) {
                throw IOException("USB bulk transfer failed: $transferred")
            }

            if (transferred == 0) {
                throw IOException("USB bulk transfer timeout")
            }

            offset += transferred
        }

        return ByteBuffer.wrap(buffer)
    }

    override fun flush() {
        // USB 批量传输不需要显式刷新
    }

    override fun close() {
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
}

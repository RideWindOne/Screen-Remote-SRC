package com.screen.remote.android.service

import com.screen.remote.android.core.domain.model.ConnectionTransport
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyServiceHeartbeatMonitorTest {
    @Test
    fun `stale heartbeat cannot remove a renewed protection with identical values`() {
        val stale = protectedDevice()
        val renewed = protectedDevice()
        val devices = ConcurrentHashMap<String, ProtectedAdbDevice>()
        devices[DEVICE_ID] = renewed

        assertFalse(removeProtectedDeviceIfCurrent(devices, DEVICE_ID, stale))
        assertSame(renewed, devices[DEVICE_ID])
    }

    @Test
    fun `current heartbeat snapshot may remove its own protection`() {
        val current = protectedDevice()
        val devices = ConcurrentHashMap<String, ProtectedAdbDevice>()
        devices[DEVICE_ID] = current

        assertTrue(removeProtectedDeviceIfCurrent(devices, DEVICE_ID, current))
        assertFalse(devices.containsKey(DEVICE_ID))
    }

    @Test
    fun `protected connection keeps exact transport and tcp port`() {
        val first = parseExactProtectedConnection("tcp:192.168.1.8:5555")
        val second = parseExactProtectedConnection("tcp:192.168.1.8:5556")
        val mdns = parseExactProtectedConnection("mdns:R5CW730QLKB")
        val usb = parseExactProtectedConnection("usb:R5CW730QLKB")

        assertEquals(ConnectionTransport.TCP, first?.transport)
        assertEquals(5555, first?.port)
        assertEquals(5556, second?.port)
        assertEquals(ConnectionTransport.MDNS, mdns?.transport)
        assertEquals(ConnectionTransport.USB, usb?.transport)
    }

    @Test
    fun `protected connection rejects non canonical or invalid keys`() {
        assertNull(parseExactProtectedConnection("192.168.1.8:5555"))
        assertNull(parseExactProtectedConnection("R5CW730QLKB"))
        assertNull(parseExactProtectedConnection("tcp:192.168.1.8"))
    }

    private fun protectedDevice() =
        ProtectedAdbDevice(
            deviceName = "device",
        )

    private companion object {
        const val DEVICE_ID = "tcp:192.168.1.8:5555"
    }
}

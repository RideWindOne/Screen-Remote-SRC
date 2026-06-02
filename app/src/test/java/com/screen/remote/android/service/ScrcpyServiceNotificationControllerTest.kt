package com.screen.remote.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrcpyServiceNotificationControllerTest {
    @Test
    fun removesTransportPrefixFromNotificationDeviceName() {
        assertEquals(
            "adb-device._adb-tls-connect._tcp",
            notificationDeviceName("mdns:adb-device._adb-tls-connect._tcp"),
        )
        assertEquals("192.168.1.8:5555", notificationDeviceName("tcp:192.168.1.8:5555"))
        assertEquals("10AEAG2YZS0020P", notificationDeviceName("usb:10AEAG2YZS0020P"))
    }

    @Test
    fun keepsRegularDeviceNameUnchanged() {
        assertEquals("Pixel 9 Pro", notificationDeviceName("Pixel 9 Pro"))
    }
}

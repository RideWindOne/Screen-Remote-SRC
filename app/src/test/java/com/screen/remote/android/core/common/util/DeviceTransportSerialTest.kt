package com.screen.remote.android.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTransportSerialTest {
    @Test
    fun mdnsInstanceNameRemovesTransportAndDnsServiceParts() {
        assertEquals("adb-device", DeviceTransportSerial.mdnsInstanceName("adb-device"))
        assertEquals(
            "adb-device",
            DeviceTransportSerial.mdnsInstanceName("mdns:adb-device._adb-tls-connect._tcp.local."),
        )
        assertEquals(
            "adb-device",
            DeviceTransportSerial.mdnsInstanceName("adb-device._adb-tls-pairing._tcp"),
        )
    }

    @Test
    fun mdnsDeviceSerialRemovesAdbPrefixAndDiscoveryNonce() {
        assertEquals(
            "R5CW730QLKB",
            DeviceTransportSerial.mdnsDeviceSerial("adb-R5CW730QLKB-xYgEcy._adb-tls-connect._tcp.local."),
        )
        assertEquals("R5CW730QLKB", DeviceTransportSerial.mdnsDeviceSerial("R5CW730QLKB"))
    }

    @Test
    fun mdnsDisplayNameKeepsNonceButRemovesAdbPrefix() {
        assertEquals(
            "R5CW730QLKB-xYgEcy",
            DeviceTransportSerial.mdnsDisplayName("adb-R5CW730QLKB-xYgEcy._adb-tls-connect._tcp"),
        )
    }
}

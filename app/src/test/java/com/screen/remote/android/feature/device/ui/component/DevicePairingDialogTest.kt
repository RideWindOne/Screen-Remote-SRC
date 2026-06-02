package com.screen.remote.android.feature.device.ui.component

import com.screen.remote.android.core.common.util.DeviceTransportSerial
import dadb.android.wireless.AdbMdnsService
import dadb.android.wireless.AdbMdnsServiceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePairingDialogTest {
    @Test
    fun mdnsDeviceKeyUsesStableSerialInsteadOfEndpoint() {
        val first =
            AdbMdnsService(
                name = "adb-R5CW730QLKB-firstNonce",
                host = "192.0.2.10",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_PAIRING,
            )
        val rebound =
            AdbMdnsService(
                name = "adb-R5CW730QLKB-secondNonce",
                host = "192.0.2.99",
                port = 40111,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )

        assertEquals("mdns:r5cw730qlkb", DeviceTransportSerial.mdnsDeviceKey(first.name))
        assertEquals(
            DeviceTransportSerial.mdnsDeviceKey(first.name),
            DeviceTransportSerial.mdnsDeviceKey(rebound.name),
        )
    }
}

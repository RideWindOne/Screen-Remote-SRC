package com.screen.remote.android.infrastructure.adb.pairing

import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AdbQrPairingTest {
    @Test
    fun generatesAndroidWirelessDebuggingPayloadWithoutExposingPasswordInToString() {
        var next = 0
        val credentials =
            AdbQrPairingCredentialsGenerator.create { bound ->
                (next++) % bound
            }

        assertEquals(
            "studio-ABCDEFGHIJ",
            credentials.serviceName,
        )
        assertEquals(
            "KLMNOPQRSTUV",
            credentials.password,
        )
        assertEquals(
            "WIFI:T:ADB;S:studio-ABCDEFGHIJ;P:KLMNOPQRSTUV;;",
            credentials.qrPayload,
        )
        assertFalse(credentials.toString().contains(credentials.password))
    }

    @Test
    fun matchesOnlyTheActivePairingServiceRequestedByTheQrCode() {
        val credentials = AdbQrPairingCredentials("studio-AbCd123456", "secret123456")
        val expected = pairingService(name = "studio-AbCd123456")

        assertSame(expected, listOf(expected).findQrPairingService(credentials))
        assertNull(listOf(pairingService(name = "studio-other12345")).findQrPairingService(credentials))
        assertNull(listOf(expected.copy(requiresPairing = false)).findQrPairingService(credentials))
        assertNull(listOf(expected.copy(confirming = true)).findQrPairingService(credentials))
        assertNull(listOf(expected.copy(host = "")).findQrPairingService(credentials))
        assertNull(listOf(expected.copy(port = 0)).findQrPairingService(credentials))
    }

    private fun pairingService(name: String): MdnsDiscoveredConnectService =
        MdnsDiscoveredConnectService(
            name = name,
            deviceSerial = name,
            host = "192.0.2.10",
            port = 37123,
            requiresPairing = true,
            previouslyPaired = false,
        )
}

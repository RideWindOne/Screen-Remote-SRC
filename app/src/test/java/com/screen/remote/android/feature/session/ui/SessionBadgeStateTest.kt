package com.screen.remote.android.feature.session.ui

import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.DeviceCapabilityCache
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionBadgeStateTest {
    @Test
    fun onlineUsbBackupWinsWhenMainTcpIsOffline() {
        val state =
            resolveSessionBadgeState(
                sessionData = session(),
                connectedAdbDeviceIds = emptySet(),
                discoveredDeviceIds = setOf("usb:usb-1"),
            )

        assertEquals(ConnectionTransport.USB, state.displayTransport)
        assertEquals(SessionEndpointStatus.DISCOVERED, state.status)
    }

    @Test
    fun offlineSessionUsesMainTransport() {
        val state =
            resolveSessionBadgeState(
                sessionData = session(mainTransport = "USB"),
                connectedAdbDeviceIds = emptySet(),
                discoveredDeviceIds = emptySet(),
            )

        assertEquals(ConnectionTransport.USB, state.displayTransport)
        assertEquals(SessionEndpointStatus.UNAVAILABLE, state.status)
    }

    @Test
    fun onlineMainWinsOverOnlineBackup() {
        val state =
            resolveSessionBadgeState(
                sessionData = session(mainTransport = "MDNS"),
                connectedAdbDeviceIds = emptySet(),
                discoveredDeviceIds = setOf("mdns:phone", "usb:usb-1"),
            )

        assertEquals(ConnectionTransport.MDNS, state.displayTransport)
        assertEquals(SessionEndpointStatus.DISCOVERED, state.status)
    }

    @Test
    fun adbConnectedBackupOverridesOnlineMain() {
        val state =
            resolveSessionBadgeState(
                sessionData = session(mainTransport = "MDNS"),
                connectedAdbDeviceIds = setOf("usb:usb-1"),
                discoveredDeviceIds = setOf("mdns:phone", "usb:usb-1"),
            )

        assertEquals(ConnectionTransport.USB, state.displayTransport)
        assertEquals(SessionEndpointStatus.ADB_CONNECTED, state.status)
    }

    @Test
    fun retainedTcpAdbConnectionStaysOnlineAfterScrcpyDisconnects() {
        val state =
            resolveSessionBadgeState(
                sessionData = session(),
                connectedAdbDeviceIds = setOf("tcp:192.168.1.2:5555"),
                discoveredDeviceIds = emptySet(),
            )

        assertEquals(ConnectionTransport.TCP, state.displayTransport)
        assertEquals(SessionEndpointStatus.ADB_CONNECTED, state.status)
    }

    @Test
    fun deviceSerialDoesNotMatchAnotherTransportConnection() {
        val state =
            resolveSessionBadgeState(
                sessionData =
                    singleCandidateSession(
                        transport = "MDNS",
                        host = "R5CW730QLKB",
                        deviceSerial = "R5CW730QLKB|fingerprint|4.1|codec-capability-v2",
                    ),
                connectedAdbDeviceIds = setOf("usb:R5CW730QLKB"),
                discoveredDeviceIds = setOf("usb:R5CW730QLKB"),
            )

        assertEquals(SessionEndpointStatus.UNAVAILABLE, state.status)
    }

    @Test
    fun exactTransportDeviceIdentifierMatchesPoolConnection() {
        val state =
            resolveSessionBadgeState(
                sessionData = singleCandidateSession("MDNS", "R5CW730QLKB"),
                connectedAdbDeviceIds = setOf("mdns:R5CW730QLKB"),
                discoveredDeviceIds = setOf("mdns:R5CW730QLKB"),
            )

        assertEquals(SessionEndpointStatus.ADB_CONNECTED, state.status)
        assertEquals(ConnectionTransport.MDNS, state.displayTransport)
    }

    @Test
    fun sameTcpHostWithDifferentPortDoesNotMatchPoolConnection() {
        val state =
            resolveSessionBadgeState(
                sessionData = singleCandidateSession("TCP", "192.168.1.2", 5555),
                connectedAdbDeviceIds = setOf("tcp:192.168.1.2:5556"),
                discoveredDeviceIds = emptySet(),
            )

        assertEquals(SessionEndpointStatus.UNAVAILABLE, state.status)
    }

    @Test
    fun unauthorizedUsbUsesNeutralDiscoveredStatus() {
        val state =
            resolveSessionBadgeState(
                sessionData = singleCandidateSession("USB", "R5CW730QLKB"),
                connectedAdbDeviceIds = emptySet(),
                discoveredDeviceIds = setOf("usb:R5CW730QLKB"),
            )

        assertEquals(SessionEndpointStatus.DISCOVERED, state.status)
        assertEquals(ConnectionTransport.USB, state.displayTransport)
    }

    private fun singleCandidateSession(
        transport: String,
        host: String,
        port: Int = 0,
        deviceSerial: String = "",
    ): SessionData =
        SessionData(
            id = "session-single",
            name = "Phone",
            connectionCandidates = listOf(ConnectionCandidateData(transport, host, port)),
            color = "BLUE",
            capabilityCache = DeviceCapabilityCache(deviceSerial = deviceSerial),
        )

    private fun session(
        mainTransport: String = "TCP",
        deviceSerial: String = "",
    ): SessionData {
        val main =
            when (mainTransport) {
                "USB" -> ConnectionCandidateData("USB", "usb-main", priority = 0)
                "MDNS" -> ConnectionCandidateData("MDNS", "phone._adb-tls-connect._tcp", priority = 0)
                else -> ConnectionCandidateData("TCP", "192.168.1.2", 5555, priority = 0)
            }
        val backups =
            listOf(
                ConnectionCandidateData("USB", "usb-1", priority = 1),
                ConnectionCandidateData("MDNS", "phone._adb-tls-connect._tcp", priority = 2),
            )
        return SessionData(
            id = "session-1",
            name = "Phone",
            connectionCandidates = listOf(main) + backups,
            color = "BLUE",
            capabilityCache = DeviceCapabilityCache(deviceSerial = deviceSerial),
        )
    }
}

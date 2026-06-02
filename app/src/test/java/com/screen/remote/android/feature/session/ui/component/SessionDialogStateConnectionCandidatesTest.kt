package com.screen.remote.android.feature.session.ui.component

import com.screen.remote.android.core.domain.model.ConnectionTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDialogStateConnectionCandidatesTest {
    @Test
    fun emptyTcpAddressDoesNotRenderDefaultPortByItself() {
        val state = SessionDialogState()

        state.selectDeviceType(SessionDeviceType.TCP)
        state.host = ""
        state.port = ""

        assertEquals("", state.sessionAddressPreview())
    }

    @Test
    fun sessionAddressPreviewDoesNotShowTransportPrefix() {
        val state = SessionDialogState()

        state.selectDeviceType(SessionDeviceType.TCP)
        state.host = "192.168.1.2"
        state.port = "5555"
        assertEquals("192.168.1.2:5555", state.sessionAddressPreview())

        state.selectDeviceType(SessionDeviceType.USB)
        state.updateUsbSerialNumber("usb:10AEAG2YZS0020P")
        assertEquals("10AEAG2YZS0020P", state.sessionAddressPreview())

        state.selectDeviceType(SessionDeviceType.MDNS)
        state.updateMdnsServiceName("mdns:adb-R5CW730QLKB-xYgEcy._adb-tls-connect._tcp")
        assertEquals("R5CW730QLKB", state.sessionAddressPreview())
    }

    @Test
    fun tcpSessionStoresPrimaryAndBackupEndpointsAsCompleteCandidates() {
        val state = SessionDialogState().apply {
            sessionName = "device"
            selectDeviceType(SessionDeviceType.TCP)
            host = "192.168.1.2"
            port = "5555"
            addBackupEndpoint()
            updateBackupEndpoint(0, "192.168.1.3:5556")
        }

        val candidates = state.toSessionData().toConnectionCandidates()

        assertEquals(ConnectionTransport.TCP, candidates[0].transport)
        assertEquals("192.168.1.2", candidates[0].host)
        assertEquals(5555, candidates[0].port)
        assertEquals("192.168.1.3", candidates[1].host)
        assertEquals(5556, candidates[1].port)
    }

    @Test
    fun codecDetectionUsesCandidatesFromTheSessionBeingEdited() {
        val state = SessionDialogState().apply {
            sessionName = "edited-device"
            selectDeviceType(SessionDeviceType.MDNS)
            updateMdnsServiceName("adb-edited-device-nonce._adb-tls-connect._tcp")
            addBackupEndpoint()
            updateBackupEndpoint(0, "192.168.5.1:30610")
        }

        val candidates = state.codecDetectionCandidates("edited-session")

        assertEquals(ConnectionTransport.MDNS, candidates[0].transport)
        assertEquals("edited-device", candidates[0].host)
        assertEquals(ConnectionTransport.TCP, candidates[1].transport)
        assertEquals("192.168.5.1", candidates[1].host)
        assertEquals(30610, candidates[1].port)
    }

    @Test
    fun backupEndpointsCanUseUsbAndMdnsSerials() {
        val state = SessionDialogState().apply {
            sessionName = "device"
            selectDeviceType(SessionDeviceType.TCP)
            host = "192.168.1.2"
            port = "5555"
            addBackupEndpoint()
            updateBackupEndpoint(0, "usb:10AEAG2YZS0020P")
            addBackupEndpoint()
            updateBackupEndpoint(1, "mdns:adb-R5CW730QLKB-xYgEcy._adb-tls-connect._tcp")
        }

        val candidates = state.toSessionData().toConnectionCandidates()

        assertEquals(ConnectionTransport.USB, candidates[1].transport)
        assertEquals("10AEAG2YZS0020P", candidates[1].host)
        assertEquals(ConnectionTransport.MDNS, candidates[2].transport)
        assertEquals("R5CW730QLKB", candidates[2].host)
    }

    @Test
    fun tcpBackupEndpointsCanUseTcpPrefix() {
        val state = SessionDialogState().apply {
            sessionName = "device"
            selectDeviceType(SessionDeviceType.TCP)
            host = "192.168.1.2"
            port = "5555"
            addBackupEndpoint()
            updateBackupEndpoint(0, "tcp:192.168.1.3:5556")
        }

        val candidates = state.toSessionData().toConnectionCandidates()

        assertEquals("192.168.1.3", candidates[1].host)
        assertEquals(5556, candidates[1].port)
        assertEquals(ConnectionTransport.TCP, candidates[1].transport)
    }
}

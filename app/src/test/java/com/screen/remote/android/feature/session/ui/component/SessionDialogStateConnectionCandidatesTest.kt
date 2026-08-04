package com.screen.remote.android.feature.session.ui.component

import com.screen.remote.android.app.deeplink.NewSessionPrefill
import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDialogStateConnectionCandidatesTest {
    @Test
    fun urlAddressPrefillsANewTcpSessionWithoutInventingAnId() {
        val state =
            SessionDialogState(
                initialPrefill =
                    NewSessionPrefill(
                        name = "Living room",
                        address = "192.168.1.20:5555",
                        color = "PURPLE",
                        backupAddresses = listOf("192.168.1.21:5555"),
                        groupIds = listOf("group-1"),
                        scrcpyParameters = mapOf("maxFps" to "120", "audio" to "on"),
                    ),
            )
        val saved = state.toSessionData()

        assertEquals("Living room", state.sessionName)
        assertEquals("PURPLE", saved.color)
        assertEquals("192.168.1.20", saved.toConnectionCandidates().first().host)
        assertEquals(5555, saved.toConnectionCandidates().first().port)
        assertEquals("192.168.1.21", saved.toConnectionCandidates()[1].host)
        assertEquals(listOf("group-1"), saved.groupIds)
        assertEquals(120, saved.config.maxFps)
        assertEquals(true, saved.config.enableAudio)
    }

    @Test
    fun editingPreservesConfigFieldsNotExposedByTheDialog() {
        val original =
            SessionData(
                id = "session",
                name = "Device",
                connectionCandidates = listOf(ConnectionCandidateData("TCP", "192.168.1.2", 5555)),
                color = "BLUE",
                config = ScrcpyConfig(displayId = 7, showTouches = true, codecOptions = "profile=high"),
            )

        val saved = SessionDialogState(original).toSessionData(original.id)

        assertEquals(7, saved.config.displayId)
        assertEquals(true, saved.config.showTouches)
        assertEquals("profile=high", saved.config.codecOptions)
    }

    @Test
    fun storedBitratesAreShownWithCompactEditorUnits() {
        val original =
            SessionData(
                id = "session",
                name = "Device",
                connectionCandidates = listOf(ConnectionCandidateData("TCP", "192.168.1.2", 5555)),
                color = "BLUE",
                config = ScrcpyConfig(videoBitRate = 2_000_000, audioBitRate = 128_000),
            )

        val state = SessionDialogState(original)

        assertEquals("2M", state.videoBitrate)
        assertEquals("128K", state.audioBitrate)
    }

    @Test
    fun compactEditorBitratesRoundTripToBitsPerSecond() {
        val state = SessionDialogState().apply {
            videoBitrate = "2M"
            audioBitrate = "192K"
        }

        val saved = state.toSessionData()

        assertEquals(2_000_000, saved.config.videoBitRate)
        assertEquals(192_000, saved.config.audioBitRate)
    }

    @Test
    fun floatingBallVisibilityIsSavedWithTheSession() {
        val state = SessionDialogState().apply { updateConfig { copy(showFloatingBall = false) } }

        assertEquals(false, state.toSessionData().config.showFloatingBall)
    }

    @Test
    fun gameModeIsSavedWithTheSession() {
        val state = SessionDialogState().apply {
            sessionName = "game-device"
            selectDeviceType(SessionDeviceType.TCP)
            host = "192.168.1.2"
            port = "5555"
            updateConfig { copy(gameMode = true) }
        }

        assertEquals(true, state.toSessionData().config.gameMode)
    }

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

    @Test
    fun compatibilityModeDisablesUnsupportedSessionFeatures() {
        val state =
            SessionDialogState(
                sessionData =
                    SessionData(
                        id = "session",
                        name = "Device",
                        connectionCandidates = listOf(ConnectionCandidateData("TCP", "192.168.1.2", 5555)),
                        color = "BLUE",
                        config =
                            ScrcpyConfig(
                                gameMode = true,
                                enableAudio = true,
                                turnScreenOff = true,
                            ),
                    ),
            )

        state.updateCompatibilityMode(true)
        state.updateConfig {
            copy(
                enableAudio = true,
                clipboardSync = true,
            )
        }
        val saved = state.toSessionData().config

        assertEquals(true, saved.compatibilityMode)
        assertEquals(false, saved.gameMode)
        assertEquals(false, state.config.enableAudio)
        assertEquals(true, state.config.clipboardSync)
        assertEquals(false, saved.enableAudio)
        assertEquals(true, saved.clipboardSync)
        assertEquals(false, saved.turnScreenOff)
    }

    @Test
    fun enablingGameModeLeavesCompatibilityMode() {
        val state = SessionDialogState()

        state.updateCompatibilityMode(true)
        state.updateGameMode(true)

        assertEquals(false, state.config.compatibilityMode)
        assertEquals(true, state.config.gameMode)
    }
}

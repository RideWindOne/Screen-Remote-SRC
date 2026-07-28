package com.screen.remote.android.feature.session.ui.component

import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredTcpService
import org.junit.Assert.assertEquals
import org.junit.Test

class MdnsServiceSelectionTest {
    @Test
    fun sameSerialSelectsOnlyConnectService() {
        val services =
            listOf(
                service(requiresPairing = false),
                service(requiresPairing = true),
            )

        assertEquals(0, selectedMdnsServiceIndex(services, "R5CW730QLKB"))
    }

    @Test
    fun pairingServiceIsSelectedWhenNoConnectServiceExists() {
        val services = listOf(service(requiresPairing = true))

        assertEquals(0, selectedMdnsServiceIndex(services, "R5CW730QLKB"))
    }

    @Test
    fun connectServiceDoesNotRequirePairingPromptWithoutLocalPairingRecord() {
        val service = service(requiresPairing = false, previouslyPaired = false)

        assertEquals(false, service.requiresPairingPrompt())
    }

    @Test
    fun pairingServiceRequiresPairingPrompt() {
        assertEquals(
            true,
            service(requiresPairing = true, previouslyPaired = false).requiresPairingPrompt(),
        )
    }

    @Test
    fun tcpServiceSelectionMatchesBothHostAndPort() {
        val services =
            listOf(
                MdnsDiscoveredTcpService(host = "192.168.5.13", port = 39517),
                MdnsDiscoveredTcpService(host = "192.168.5.13", port = 5555),
            )

        assertEquals(
            0,
            selectedTcpMdnsServiceIndex(
                services = services,
                selectedHost = "192.168.5.13",
                selectedPort = 39517,
            ),
        )
    }

    private fun service(
        requiresPairing: Boolean,
        previouslyPaired: Boolean = true,
    ) =
        MdnsDiscoveredConnectService(
            name = "R5CW730QLKB",
            deviceSerial = "R5CW730QLKB",
            requiresPairing = requiresPairing,
            previouslyPaired = previouslyPaired,
        )
}

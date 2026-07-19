package com.screen.remote.android.feature.session.ui.component

import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
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
    fun pairedConnectServiceDoesNotRequirePairingPrompt() {
        val service = service(requiresPairing = false, previouslyPaired = true)

        assertEquals(false, service.requiresPairingPrompt())
    }

    @Test
    fun unpairedOrPairingServiceRequiresPairingPrompt() {
        assertEquals(
            true,
            service(requiresPairing = false, previouslyPaired = false).requiresPairingPrompt(),
        )
        assertEquals(
            true,
            service(requiresPairing = true, previouslyPaired = true).requiresPairingPrompt(),
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

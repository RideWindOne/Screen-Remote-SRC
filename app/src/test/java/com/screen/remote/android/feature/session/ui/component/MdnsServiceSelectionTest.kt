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

    private fun service(requiresPairing: Boolean) =
        MdnsDiscoveredConnectService(
            name = "R5CW730QLKB",
            deviceSerial = "R5CW730QLKB",
            requiresPairing = requiresPairing,
            previouslyPaired = true,
        )
}

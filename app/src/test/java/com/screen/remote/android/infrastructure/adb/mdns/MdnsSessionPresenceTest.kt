package com.screen.remote.android.infrastructure.adb.mdns

import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import dadb.android.wireless.AdbMdnsService
import dadb.android.wireless.AdbMdnsServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MdnsSessionPresenceTest {
    @Test
    fun gameModePauseStopsDiscoveryWithoutDependingOnSavedSessions() {
        assertFalse(
            shouldMonitorMdnsDiscovery(
                gameModePaused = true,
                hasTrackedSessions = true,
                interactiveDiscoveryConsumers = 1,
            ),
        )
    }

    @Test
    fun discoveryResumesAfterGameModePauseEnds() {
        assertTrue(
            shouldMonitorMdnsDiscovery(
                gameModePaused = false,
                hasTrackedSessions = true,
                interactiveDiscoveryConsumers = 0,
            ),
        )
    }

    @Test
    fun buildsFullMdnsNameFromDiscoveredServiceInstance() {
        val service =
            AdbMdnsService(
                name = "adb-10AEAG2YZS0020P-kccdPt",
                host = "192.0.2.1",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )

        assertEquals(
            "adb-10AEAG2YZS0020P-kccdPt._adb-tls-connect._tcp",
            service.fullMdnsServiceName(),
        )
    }

    @Test
    fun keepsConnectAndPairingServicesWithTheSameDeviceSerial() {
        val connect =
            AdbMdnsService(
                name = "adb-R5CW730QLKB-connectNonce",
                host = "192.0.2.10",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )
        val pairing =
            AdbMdnsService(
                name = "adb-R5CW730QLKB-pairNonce",
                host = "192.0.2.10",
                port = 40111,
                serviceType = AdbMdnsServiceType.TLS_PAIRING,
            )

        val services =
            discoveredMdnsServices(
                connectServices = listOf(connect),
                pairingServices = listOf(pairing),
                pairedDeviceKeys = setOf("mdns:r5cw730qlkb"),
            )

        assertEquals(2, services.size)
        assertTrue(services[0].previouslyPaired)
        assertTrue(!services[0].requiresPairing)
        assertTrue(services[1].previouslyPaired)
        assertTrue(services[1].requiresPairing)
    }

    @Test
    fun canonicalSerialUsesMdnsIdentityWithoutEndpoint() {
        assertEquals(
            "mdns:r5cw730qlkb",
            canonicalMdnsSerial("mdns:adb-R5CW730QLKB-xYgEcy._adb-tls-connect._tcp.local."),
        )
        assertEquals("mdns:r5cw730qlkb", canonicalMdnsSerial("R5CW730QLKB"))
        assertEquals(
            "mdns:r5cw730qlkb",
            canonicalMdnsSerial("mdns:adb-R5CW730QLKB-xYgEcy._adb-tls-pairing._tcp"),
        )
    }

    @Test
    fun extractsMdnsCandidatesFromSavedSession() {
        val session =
            session(
                connectionCandidates =
                    listOf(
                        ConnectionCandidateData(
                            transport = "MDNS",
                            host = "R5CW730QLKB",
                            lastSuccessfulAtMillis = 42L,
                        ),
                    ),
            )

        val tracked = session.mdnsTrackedSessions()

        assertEquals(1, tracked.size)
        assertEquals("mdns:r5cw730qlkb", tracked.single().mdnsSerial)
    }

    @Test
    fun tracksConfiguredMdnsSessionThatHasNeverConnectedSuccessfully() {
        val session =
            session(
                connectionCandidates =
                    listOf(
                        ConnectionCandidateData(
                            transport = "MDNS",
                            host = "R5CW730QLKB",
                        ),
                    ),
            )

        val tracked = session.mdnsTrackedSessions()

        assertEquals(1, tracked.size)
        assertEquals("mdns:r5cw730qlkb", tracked.single().mdnsSerial)
    }

    @Test
    fun presenceMatchesByMdnsSerialOnly() {
        val tracked =
            MdnsTrackedSession(
                sessionId = "session-1",
                sessionName = "Phone",
                mdnsSerial = "mdns:r5cw730qlkb",
            )

        val online =
            onlineTrackedSessions(
                trackedSessions = listOf(tracked),
                discoveredSerials = setOf("mdns:r5cw730qlkb"),
            )

        assertTrue(online.single() === tracked)
    }

    private fun session(connectionCandidates: List<ConnectionCandidateData>): SessionData =
        SessionData(
            id = "session-1",
            name = "Phone",
            connectionCandidates = connectionCandidates,
            color = "BLUE",
        )
}

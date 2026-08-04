package com.screen.remote.android.infrastructure.adb.mdns

import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import dadb.android.wireless.AdbMdnsService
import dadb.android.wireless.AdbMdnsServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun endpointResolutionTemporarilyOverridesGameModePause() {
        assertTrue(
            shouldMonitorMdnsDiscovery(
                gameModePaused = true,
                hasTrackedSessions = true,
                interactiveDiscoveryConsumers = 0,
                resolutionConsumers = 1,
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
    fun exposesLegacyAdbMdnsAdvertisementAsTcpEndpoint() {
        val service =
            AdbMdnsService(
                name = "adb-10AEAG2YZS0020P",
                host = "192.168.5.13",
                port = 39517,
                serviceType = AdbMdnsServiceType.ADB,
            )

        val endpoint = discoveredMdnsTcpServices(connectServices = listOf(service)).single()

        assertEquals("192.168.5.13", endpoint.host)
        assertEquals(39517, endpoint.port)
        assertFalse(endpoint.confirming)
    }

    @Test
    fun refreshRetainsOldServiceAsConfirmingUntilItIsRediscovered() {
        val old =
            AdbMdnsService(
                name = "adb-old-device-nonce",
                host = "192.0.2.20",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )

        val services =
            discoveredMdnsServices(
                connectServices = emptyList(),
                pairingServices = emptyList(),
                pairedDeviceKeys = emptySet(),
                retainedServices = listOf(old),
                refreshing = true,
            )

        assertEquals(1, services.size)
        assertTrue(services.single().confirming)
        assertEquals("192.0.2.20", services.single().host)
        assertEquals(37123, services.single().port)
    }

    @Test
    fun rediscoveredServiceReplacesConfirmingSnapshot() {
        val old =
            AdbMdnsService(
                name = "adb-phone-nonce",
                host = "192.0.2.20",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )
        val current = old.copy(host = "192.0.2.21", port = 38123)

        val service =
            discoveredMdnsServices(
                connectServices = listOf(current),
                pairingServices = emptyList(),
                pairedDeviceKeys = emptySet(),
                retainedServices = listOf(old),
                refreshing = true,
            ).single()

        assertFalse(service.confirming)
        assertEquals("192.0.2.21", service.host)
        assertEquals(38123, service.port)
    }

    @Test
    fun rediscoveredDeviceWithNewInstanceNonceReplacesOldSnapshot() {
        val old =
            AdbMdnsService(
                name = "adb-R5CW730QLKB-oldNonce",
                host = "192.0.2.20",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )
        val current = old.copy(name = "adb-R5CW730QLKB-newNonce", host = "192.0.2.21", port = 38123)

        val services =
            discoveredMdnsServices(
                connectServices = listOf(current),
                pairingServices = emptyList(),
                pairedDeviceKeys = emptySet(),
                retainedServices = listOf(old),
                refreshing = true,
            )

        assertEquals(1, services.size)
        assertFalse(services.single().confirming)
        assertEquals("192.0.2.21", services.single().host)
    }

    @Test
    fun completedRefreshDropsServicesThatWereNotRediscovered() {
        val old =
            AdbMdnsService(
                name = "adb-offline-device-nonce",
                host = "192.0.2.20",
                port = 37123,
                serviceType = AdbMdnsServiceType.TLS_CONNECT,
            )

        val services =
            discoveredMdnsServices(
                connectServices = emptyList(),
                pairingServices = emptyList(),
                pairedDeviceKeys = emptySet(),
                retainedServices = listOf(old),
                refreshing = false,
            )

        assertTrue(services.isEmpty())
    }

    @Test
    fun retryDelayUsesBoundedBackoff() {
        assertEquals(500L, mdnsRetryDelayMillis(1))
        assertEquals(1_000L, mdnsRetryDelayMillis(2))
        assertEquals(2_000L, mdnsRetryDelayMillis(3))
        assertEquals(5_000L, mdnsRetryDelayMillis(4))
        assertEquals(5_000L, mdnsRetryDelayMillis(20))
    }

    @Test
    fun freshnessPolicyRefreshesOnlyWhenSnapshotIsOld() {
        assertTrue(shouldRefreshMdns(nowMillis = 100L, lastRefreshStartedAtMillis = 0L, freshnessWindowMillis = 15L))
        assertFalse(shouldRefreshMdns(nowMillis = 110L, lastRefreshStartedAtMillis = 100L, freshnessWindowMillis = 15L))
        assertTrue(shouldRefreshMdns(nowMillis = 115L, lastRefreshStartedAtMillis = 100L, freshnessWindowMillis = 15L))
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

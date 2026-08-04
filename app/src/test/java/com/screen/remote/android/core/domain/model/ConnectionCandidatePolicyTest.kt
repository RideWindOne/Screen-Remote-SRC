package com.screen.remote.android.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionCandidatePolicyTest {
    @Test
    fun parsesExplicitTcpEndpointCandidatesWithPorts() {
        assertEquals(
            listOf(
                ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555, priority = 0),
                ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.3", 5556, priority = 1),
            ),
            parseTcpConnectionCandidates("192.168.1.2:5555, 192.168.1.3:5556"),
        )
    }

    @Test
    fun explicitTcpCandidatesRequireCompleteEndpointSerials() {
        assertEquals(
            listOf(ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.3", 5556, priority = 1)),
            parseTcpConnectionCandidates("192.168.1.2, 192.168.1.3:5556"),
        )
    }

    @Test
    fun parsesUsbOrMdnsCandidateWithPrefix() {
        assertEquals(
            ConnectionCandidate(ConnectionTransport.USB, "dev_123", 0),
            parseSessionAddressCandidate("usb:dev_123"),
        )
        assertEquals(
            ConnectionCandidate(ConnectionTransport.MDNS, "my-phone", 0),
            parseSessionAddressCandidate("mdns:my-phone"),
        )
    }

    @Test
    fun parsesTcpCandidateWithOrWithoutTcpPrefix() {
        assertEquals(
            ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555),
            parseSessionAddressCandidate("tcp:192.168.1.2:5555"),
        )
        assertEquals(
            ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.3", 5556),
            parseSessionAddressCandidate("192.168.1.3:5556"),
        )
    }

    @Test
    fun formatsAddressEndpointForAllTransports() {
        assertEquals(
            "usb:serial_a",
            ConnectionCandidate(ConnectionTransport.USB, "serial_a").toAddressEndpoint(),
        )
        assertEquals(
            "mdns:device",
            ConnectionCandidate(ConnectionTransport.MDNS, "device.local").toAddressEndpoint(),
        )
        assertEquals(
            "tcp:10.0.0.2:5555",
            ConnectionCandidate(ConnectionTransport.TCP, "10.0.0.2", 5555).toAddressEndpoint(),
        )
    }

    @Test
    fun keepsConfiguredTcpOrderRegardlessOfProbeOrHistory() {
        val olderReachable =
            ConnectionCandidateAttempt(
                ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555, priority = 0),
                reachable = true,
                latencyMillis = 10,
            )
        val lastSuccess =
            ConnectionCandidateAttempt(
                ConnectionCandidate(
                    ConnectionTransport.TCP,
                    "192.168.1.3",
                    5555,
                    priority = 1,
                    lastSuccessfulAtMillis = 42
                ),
                reachable = false,
            )

        assertEquals(
            "192.168.1.2",
            orderedConnectionCandidates(listOf(olderReachable, lastSuccess)).first().host,
        )
    }

    @Test
    fun ordersByTransportPriorityUsbMdnsTcpBeforeTcpOrderingByOriginalRank() {
        val usb = ConnectionCandidateAttempt(
            ConnectionCandidate(ConnectionTransport.USB, "usb-main", 0, priority = 3),
            reachable = false,
        )
        val mdns = ConnectionCandidateAttempt(
            ConnectionCandidate(ConnectionTransport.MDNS, "phone.mdns", 0, priority = 2),
            reachable = true,
        )
        val tcpSecond = ConnectionCandidateAttempt(
            ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.3", 5555, priority = 2),
            reachable = true,
            latencyMillis = 20,
        )
        val tcpFirst = ConnectionCandidateAttempt(
            ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5556, priority = 1),
            reachable = false,
            latencyMillis = 50,
        )

        val ordered = orderedConnectionCandidates(listOf(tcpSecond, mdns, tcpFirst, usb))

        assertEquals(
            listOf(ConnectionTransport.USB, ConnectionTransport.MDNS, ConnectionTransport.TCP, ConnectionTransport.TCP),
            ordered.map { it.transport },
        )
        assertEquals("192.168.1.2", ordered[2].host)
        assertEquals("192.168.1.3", ordered[3].host)
    }

    @Test
    fun marksSuccessAndFailureWithoutMergingDifferentHosts() {
        val candidates =
            listOf(
                ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555),
                ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.3", 5555),
            )

        val failed = markConnectionCandidateFailure(candidates, candidates[0])
        val succeeded = markConnectionCandidateSuccess(failed, candidates[1], 99)

        assertEquals(1, succeeded[0].failureCount)
        assertEquals(99, succeeded[1].lastSuccessfulAtMillis)
    }
}

package com.screen.remote.android.feature.session.viewmodel

import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLatencyTestStateTest {
    @Test
    fun `copy is enabled only after every endpoint finishes`() {
        val candidate = ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555)
        val unfinished =
            ConnectionLatencyTestState(
                endpoints = mapOf(
                    endpointKey(candidate) to ConnectionLatencyEndpointState(
                        candidate,
                        "TCP",
                        finished = false
                    )
                ),
            )
        val finished =
            unfinished.copy(
                endpoints = unfinished.endpoints.mapValues { (_, endpoint) -> endpoint.copy(finished = true) },
            )

        assertFalse(unfinished.allTestsCompleted())
        assertTrue(finished.allTestsCompleted())
        assertFalse(finished.copy(running = true).allTestsCompleted())
    }

    @Test
    fun `copy text contains all endpoints and round logs`() {
        val mdns = ConnectionCandidate(ConnectionTransport.MDNS, "adb-device._adb-tls-connect._tcp", 0)
        val tcp = ConnectionCandidate(ConnectionTransport.TCP, "192.168.1.2", 5555)
        val state =
            ConnectionLatencyTestState(
                endpoints =
                    linkedMapOf(
                        endpointKey(mdns) to
                            ConnectionLatencyEndpointState(
                                candidate = mdns,
                                label = "mDNS endpoint",
                                connectSamples = listOf(100.0),
                                shellSamples = listOf(10.0),
                                roundLogs = listOf("#1 mDNS log"),
                                finished = true,
                            ),
                        endpointKey(tcp) to
                            ConnectionLatencyEndpointState(
                                candidate = tcp,
                                label = "TCP endpoint",
                                connectSamples = listOf(80.0),
                                shellSamples = listOf(8.0),
                                roundLogs = listOf("#1 TCP log"),
                                finished = true,
                            ),
                    ),
            )
        val session =
            SessionData(
                id = "session",
                name = "test-device",
                connectionCandidates = listOf(ConnectionCandidateData("TCP", "192.168.1.2", 5555)),
                color = "BLUE",
            )

        val copied = state.copyText(session)

        assertTrue(copied.contains("会话：test-device"))
        assertTrue(copied.contains("[mDNS endpoint]"))
        assertTrue(copied.contains("#1 mDNS log"))
        assertTrue(copied.contains("[TCP endpoint]"))
        assertTrue(copied.contains("#1 TCP log"))
    }
}

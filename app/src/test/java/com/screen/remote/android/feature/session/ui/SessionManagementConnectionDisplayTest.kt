package com.screen.remote.android.feature.session.ui

import com.screen.remote.android.core.data.repository.ConnectionCandidateData
import com.screen.remote.android.core.data.repository.SessionData
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagementConnectionDisplayTest {
    @Test
    fun `active mdns winner is displayed instead of configured tcp primary candidate`() {
        val session =
            SessionData(
                id = "session",
                name = "Device",
                connectionCandidates =
                    listOf(
                        ConnectionCandidateData(
                            transport = "TCP",
                            host = "192.168.5.13",
                            port = 15555,
                            priority = 0,
                        ),
                        ConnectionCandidateData(
                            transport = "MDNS",
                            host = "10AEAG2YZS0020P",
                            priority = 1,
                        ),
                    ),
                color = "Blue",
            )

        assertEquals(
            "mdns:10AEAG2YZS0020P",
            managementConnectionEndpoint(
                sessionData = session,
                activeDeviceId = "mdns:10AEAG2YZS0020P",
            ),
        )
    }

    @Test
    fun `configured primary candidate is used without an active connection`() {
        val session =
            SessionData(
                id = "session",
                name = "Device",
                connectionCandidates =
                    listOf(
                        ConnectionCandidateData(
                            transport = "TCP",
                            host = "192.168.5.13",
                            port = 15555,
                        ),
                    ),
                color = "Blue",
            )

        assertEquals(
            "tcp:192.168.5.13:15555",
            managementConnectionEndpoint(
                sessionData = session,
                activeDeviceId = null,
            ),
        )
    }
}

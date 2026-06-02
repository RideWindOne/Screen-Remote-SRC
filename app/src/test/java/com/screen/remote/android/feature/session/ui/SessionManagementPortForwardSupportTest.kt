package com.screen.remote.android.feature.session.ui

import com.screen.remote.android.core.data.repository.TcpPortForwardRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagementPortForwardSupportTest {
    @Test
    fun `creates one structured tcp forward`() {
        val config =
            SessionManagementPortForwardManager.configFor(
                listOf(TcpPortForwardRule(targetHost = "192.168.1.1", targetPort = 80, localPort = 18080)),
            )

        assertEquals("relay -L=tcp://127.0.0.1:18080/192.168.1.1:80", config?.remoteConfigLine)
        assertEquals(listOf(18080), config?.localPorts)
    }

    @Test
    fun `creates multiple forwards for one helper process`() {
        val config =
            SessionManagementPortForwardManager.configFor(
                listOf(
                    TcpPortForwardRule(targetHost = "192.168.5.1", targetPort = 80, localPort = 39280),
                    TcpPortForwardRule(targetHost = "192.168.3.1", targetPort = 80, localPort = 39380),
                ),
            )

        assertEquals(
            "relay -L=tcp://127.0.0.1:39280/192.168.5.1:80 -L=tcp://127.0.0.1:39380/192.168.3.1:80",
            config?.remoteConfigLine,
        )
        assertEquals(listOf(39280, 39380), config?.localPorts)
    }

    @Test
    fun `rejects duplicate local ports and invalid targets`() {
        assertNull(
            SessionManagementPortForwardManager.configFor(
                listOf(
                    TcpPortForwardRule(targetHost = "192.168.1.1", targetPort = 80, localPort = 18080),
                    TcpPortForwardRule(targetHost = "router.lan", targetPort = 443, localPort = 18080),
                ),
            ),
        )
        assertNull(
            SessionManagementPortForwardManager.configFor(
                listOf(TcpPortForwardRule(targetHost = "192.168.1.1;reboot", targetPort = 80, localPort = 18080)),
            ),
        )
        assertNull(
            SessionManagementPortForwardManager.configFor(
                listOf(TcpPortForwardRule(targetHost = "192.168.1.1", targetPort = 0, localPort = 18080)),
            ),
        )
    }
}

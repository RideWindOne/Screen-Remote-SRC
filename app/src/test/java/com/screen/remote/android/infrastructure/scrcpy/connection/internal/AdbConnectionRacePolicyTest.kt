package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionRaceOutcome
import com.screen.remote.android.infrastructure.adb.connection.chooseAdbConnectionRaceWinner
import com.screen.remote.android.infrastructure.adb.connection.choosePreferredAdbConnection
import com.screen.remote.android.infrastructure.adb.connection.choosePreferredNetworkAdbConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbConnectionRacePolicyTest {
    @Test
    fun `network winner waits while usb is pending inside two hundred milliseconds`() {
        val tcp = outcome(ConnectionTransport.TCP, 10)

        assertNull(
            chooseAdbConnectionRaceWinner(
                successfulOutcomes = listOf(tcp),
                pending = listOf(candidate(ConnectionTransport.USB)),
                usbWaitExpired = false,
            ),
        )
    }

    @Test
    fun `network winner is selected when usb wait expires`() {
        val tcp = outcome(ConnectionTransport.TCP, 10)
        val mdns = outcome(ConnectionTransport.MDNS, 120)

        assertEquals(
            mdns,
            chooseAdbConnectionRaceWinner(
                successfulOutcomes = listOf(tcp, mdns),
                pending = listOf(candidate(ConnectionTransport.USB)),
                usbWaitExpired = true,
            ),
        )
    }

    @Test
    fun `usb wins when it succeeds inside the decision window`() {
        val tcp = outcome(ConnectionTransport.TCP, 10)
        val mdns = outcome(ConnectionTransport.MDNS, 120)
        val usb = outcome(ConnectionTransport.USB, 190)

        assertEquals(usb, choosePreferredAdbConnection(listOf(tcp, mdns, usb)))
    }

    @Test
    fun `mdns wins over an earlier tcp when usb does not succeed`() {
        val tcp = outcome(ConnectionTransport.TCP, 10)
        val mdns = outcome(ConnectionTransport.MDNS, 190)

        assertEquals(mdns, choosePreferredNetworkAdbConnection(listOf(tcp, mdns)))
    }

    @Test
    fun `fastest tcp wins when neither usb nor mdns succeeds`() {
        val pooledTcp = outcome(ConnectionTransport.TCP, 10, "pooled")
        val freshTcp = outcome(ConnectionTransport.TCP, 80, "fresh")

        assertEquals(pooledTcp, choosePreferredNetworkAdbConnection(listOf(freshTcp, pooledTcp)))
    }

    @Test
    fun `fastest connection wins among candidates of the same transport`() {
        val firstUsb = outcome(ConnectionTransport.USB, 25, "first")
        val secondUsb = outcome(ConnectionTransport.USB, 50, "second")

        assertEquals(firstUsb, choosePreferredAdbConnection(listOf(secondUsb, firstUsb)))
    }

    @Test
    fun `stale connection is ignored during final selection`() {
        val staleUsb = outcome(ConnectionTransport.USB, 10)
        val currentMdns = outcome(ConnectionTransport.MDNS, 20)

        assertEquals(
            currentMdns,
            choosePreferredAdbConnection(listOf(staleUsb, currentMdns)) { it !== staleUsb },
        )
    }

    private fun outcome(
        transport: ConnectionTransport,
        completedAtMillis: Long,
        host: String = transport.name.lowercase(),
    ): AdbConnectionRaceOutcome =
        AdbConnectionRaceOutcome(
            candidate =
                ConnectionCandidate(
                    transport = transport,
                    host = host,
                    port = if (transport == ConnectionTransport.TCP) 5555 else 0,
                ),
            result = Result.failure<AdbConnection>(IllegalStateException("unused by policy")),
            completedAtNanos = completedAtMillis * 1_000_000L,
        )

    private fun candidate(
        transport: ConnectionTransport,
        host: String = transport.name.lowercase(),
    ): ConnectionCandidate =
        ConnectionCandidate(
            transport = transport,
            host = host,
            port = if (transport == ConnectionTransport.TCP) 5555 else 0,
        )
}

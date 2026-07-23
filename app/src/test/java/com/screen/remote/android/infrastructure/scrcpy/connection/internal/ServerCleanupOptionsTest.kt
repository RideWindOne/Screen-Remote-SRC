package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCleanupOptionsTest {
    @Test
    fun `power off on close is disabled when cleanup is disabled`() {
        val command = buildScrcpyCommand(scid = 1, options = options(cleanup = false, powerOff = true))

        assertTrue(command.contains("power_off_on_close=false"))
        assertTrue(command.contains("cleanup=false"))
    }

    @Test
    fun `power off on close is enabled when cleanup is enabled`() {
        val command = buildScrcpyCommand(scid = 1, options = options(cleanup = true, powerOff = true))

        assertTrue(command.contains("power_off_on_close=true"))
    }

    private fun options(
        cleanup: Boolean,
        powerOff: Boolean,
    ) = ScrcpyOptions(
        sessionId = "session",
        connectionCandidates = listOf(ConnectionCandidate(ConnectionTransport.TCP, "device", 5555)),
        config =
            ScrcpyConfig(
                cleanupOnDisconnect = cleanup,
                powerOffOnClose = powerOff,
            ),
    )
}

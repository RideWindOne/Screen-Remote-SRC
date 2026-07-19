package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.infrastructure.scrcpy.session.model.ConnectedContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderRecoveryAttemptsTest {
    @Test
    fun decoderRecoveryStopsAtItsIndependentLimit() {
        val runtime = SessionRuntimeState()

        assertTrue(runtime.tryConsumeDecoderRecoveryAttempt(maxAttempts = 2))
        assertTrue(runtime.tryConsumeDecoderRecoveryAttempt(maxAttempts = 2))
        assertFalse(runtime.tryConsumeDecoderRecoveryAttempt(maxAttempts = 2))
    }

    @Test
    fun socketExpectationHasOneRuntimeOwnerAndResetsWithComponents() {
        val runtime = SessionRuntimeState()

        runtime.updateSocketExpectation(expectedSocketCount = 2, audioEnabled = false)
        assertTrue(runtime.expectedSocketCount() == 2)
        assertFalse(runtime.audioEnabled())

        runtime.clearComponentStates()
        assertTrue(runtime.expectedSocketCount() == 3)
        assertTrue(runtime.audioEnabled())
    }

    @Test
    fun successfulConnectionResetsTheSessionReconnectBudget() {
        val runtime = SessionRuntimeState()
        runtime.incrementReconnectAttempts()
        runtime.incrementReconnectAttempts()

        runtime.updateSessionState(
            SessionState.Connected(
                ConnectedContext(
                    localPort = 27183,
                    connectedSockets = emptySet(),
                    dummyByteConfirmed = true,
                    audioEnabled = false,
                ),
            ),
        )

        assertEquals(0, runtime.reconnectAttempts())
    }
}

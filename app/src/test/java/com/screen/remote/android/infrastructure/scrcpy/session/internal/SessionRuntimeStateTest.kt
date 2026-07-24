package com.screen.remote.android.infrastructure.scrcpy.session.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRuntimeStateTest {
    @Test
    fun `recovery can cancel an already active reconnect callback`() {
        var cancellations = 0
        val runtime = SessionRuntimeState()
        runtime.bind(
            stateMachine = null,
            reconnectCallback = null,
            cancelReconnectCallback = { cancellations += 1 },
        )

        runtime.invokeCancelReconnectCallback()

        assertEquals(1, cancellations)
    }

    @Test
    fun `stopping monitor removes reconnect cancellation callback`() {
        var cancellations = 0
        val runtime = SessionRuntimeState()
        runtime.bind(
            stateMachine = null,
            reconnectCallback = null,
            cancelReconnectCallback = { cancellations += 1 },
        )
        runtime.bind(
            stateMachine = null,
            reconnectCallback = null,
            cancelReconnectCallback = null,
        )

        runtime.invokeCancelReconnectCallback()

        assertEquals(0, cancellations)
    }
}

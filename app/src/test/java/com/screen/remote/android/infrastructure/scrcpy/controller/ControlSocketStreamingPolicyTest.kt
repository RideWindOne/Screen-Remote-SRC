package com.screen.remote.android.infrastructure.scrcpy.controller

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.Socket

class ControlSocketStreamingPolicyTest {
    @Test
    fun `control socket does not retain handshake read timeout`() {
        Socket().use { socket ->
            socket.soTimeout = 10_000

            configureControlSocketForStreaming(socket)

            assertEquals(0, socket.soTimeout)
        }
    }
}

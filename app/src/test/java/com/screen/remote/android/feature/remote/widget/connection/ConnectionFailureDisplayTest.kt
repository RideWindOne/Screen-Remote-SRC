package com.screen.remote.android.feature.remote.widget.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFailureDisplayTest {
    @Test
    fun `ipv4 endpoint is replaced with session name`() {
        assertEquals(
            "Connection to emulator failed",
            connectionFailureDisplayMessage(
                message = "Connection to tcp:192.168.5.14:15555 failed",
                sessionName = "emulator",
            ),
        )
    }

    @Test
    fun `bracketed raced endpoint is replaced without leaking transport`() {
        assertEquals(
            "emulator connection lost",
            connectionFailureDisplayMessage(
                message = "[tcp:192.168.5.14:15555] connection lost",
                sessionName = "emulator",
            ),
        )
    }

    @Test
    fun `bracketed ipv6 endpoint is replaced with session name`() {
        assertEquals(
            "Connection to emulator failed",
            connectionFailureDisplayMessage(
                message = "Connection to [fe80::1]:5555 failed",
                sessionName = "emulator",
            ),
        )
    }

    @Test
    fun `codec reason without endpoint remains unchanged`() {
        val message = "Video encoder OMX.google.h264.encoder failed"
        assertEquals(message, connectionFailureDisplayMessage(message, "emulator"))
    }
}

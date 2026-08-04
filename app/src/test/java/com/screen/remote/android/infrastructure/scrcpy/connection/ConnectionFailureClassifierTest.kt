package com.screen.remote.android.infrastructure.scrcpy.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConnectionFailureClassifierTest {
    @Test
    fun adbStreamLossIsAnExpectedConnectionClosure() {
        assertTrue(IOException("Connection lost while reading stream 10").isExpectedConnectionClosure())
    }

    @Test
    fun nestedTransportClosureIsDetected() {
        val error = IllegalStateException("decoder failed", IOException("Connection reset by peer"))

        assertTrue(error.isExpectedConnectionClosure())
    }

    @Test
    fun protocolFailureStillRequiresDetailedDiagnostics() {
        assertFalse(IOException("Invalid packet size: 99999999").isExpectedConnectionClosure())
    }
}

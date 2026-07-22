package com.screen.remote.android.feature.remote.ui

import com.screen.remote.android.feature.remote.presentation.ConnectStatus
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBackPolicyTest {
    @Test
    fun `reconnect preparation is intercepted even while low level state is disconnected`() {
        val status = ConnectStatus.Connecting("session")

        assertTrue(shouldInterceptRemoteBack(ConnectionState.Disconnected, status))
        assertTrue(shouldCancelConnectionOnBack(ConnectionState.Disconnected, status))
    }

    @Test
    fun `connected back remains a remote key event instead of cancelling`() {
        val status = ConnectStatus.Connected

        assertTrue(shouldInterceptRemoteBack(ConnectionState.Connected, status))
        assertFalse(shouldCancelConnectionOnBack(ConnectionState.Connected, status))
    }

    @Test
    fun `idle screen does not consume system back`() {
        assertFalse(shouldInterceptRemoteBack(ConnectionState.Disconnected, ConnectStatus.Idle))
    }
}

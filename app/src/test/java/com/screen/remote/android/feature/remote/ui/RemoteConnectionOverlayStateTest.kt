package com.screen.remote.android.feature.remote.ui

import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionState
import org.junit.Assert.assertSame
import org.junit.Test

class RemoteConnectionOverlayStateTest {
    @Test
    fun `compatibility mode remains loading until the first frame arrives`() {
        assertSame(
            ConnectionState.Connecting,
            connectionStateForRemoteOverlay(
                connectionState = ConnectionState.Connected,
                compatibilityMode = true,
                compatibilityFrameAvailable = false,
            ),
        )
        assertSame(
            ConnectionState.Connected,
            connectionStateForRemoteOverlay(
                connectionState = ConnectionState.Connected,
                compatibilityMode = true,
                compatibilityFrameAvailable = true,
            ),
        )
    }

    @Test
    fun `normal mode and compatibility errors preserve their connection state`() {
        val error = ConnectionState.Error("capture failed")

        assertSame(
            ConnectionState.Connected,
            connectionStateForRemoteOverlay(
                connectionState = ConnectionState.Connected,
                compatibilityMode = false,
                compatibilityFrameAvailable = false,
            ),
        )
        assertSame(
            error,
            connectionStateForRemoteOverlay(
                connectionState = error,
                compatibilityMode = true,
                compatibilityFrameAvailable = false,
            ),
        )
    }
}

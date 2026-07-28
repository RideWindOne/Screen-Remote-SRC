package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager.dManagement

/**
 * Shared holder for the active ADB connection.
 */
object AdbBridge {
    private val state = AdbBridgeState()

    fun setConnection(connection: AdbConnection) {
        state.currentConnection = connection
    }

    fun getConnection(): AdbConnection? = state.currentConnection

    fun clearConnection() {
        state.currentConnection = null
        dManagement(LogTags.ADB_BRIDGE) { "Clear current connection" }
    }
}

internal class AdbBridgeState {
    var currentConnection: AdbConnection? = null
}

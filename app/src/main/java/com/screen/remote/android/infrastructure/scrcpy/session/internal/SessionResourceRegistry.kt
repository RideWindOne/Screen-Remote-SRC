package com.screen.remote.android.infrastructure.scrcpy.session.internal

import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.EncoderDetectionResult
import com.screen.remote.android.infrastructure.scrcpy.session.monitor.ScrcpyMonitorBus

internal class SessionResourceRegistry(
    private val storage: SessionStorage,
) {
    var adbConnection: AdbConnection? = null
    var codecInfo: EncoderDetectionResult? = null
    var monitorBus: ScrcpyMonitorBus? = null

    fun storage(): SessionStorage = storage

    fun clearRuntimeResources() {
        adbConnection = null
        codecInfo = null
        monitorBus = null
    }
}

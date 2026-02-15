package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.data.storage.SessionStorage
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderDetectionResult
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor.ScrcpyMonitorBus

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

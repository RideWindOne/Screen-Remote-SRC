package com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.data.storage.SessionStorage
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderDetectionResult
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor.ScrcpyMonitorBus

internal class SessionResourceFacade(
    private val resourceRegistry: SessionResourceRegistry,
) {
    var adbConnection: AdbConnection?
        get() = resourceRegistry.adbConnection
        set(value) {
            resourceRegistry.adbConnection = value
        }

    var codecInfo: EncoderDetectionResult?
        get() = resourceRegistry.codecInfo
        set(value) {
            resourceRegistry.codecInfo = value
        }

    var monitorBus: ScrcpyMonitorBus?
        get() = resourceRegistry.monitorBus
        set(value) {
            resourceRegistry.monitorBus = value
        }

    fun storage(): SessionStorage = resourceRegistry.storage()

    fun replaceMonitorBus(deviceIdentifier: String) {
        try {
            monitorBus?.stop()
        } catch (e: Exception) {
            LogManager.w(LogTags.SDL, "停止旧 MonitorBus 失败: ${e.message}")
        }
        monitorBus = ScrcpyMonitorBus(deviceIdentifier).apply { start() }
    }

    fun stopMonitorBus() {
        try {
            monitorBus?.stop()
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "停止监控总线失败: ${e.message}")
        }
    }

    fun clearRuntimeResources() {
        resourceRegistry.clearRuntimeResources()
    }
}

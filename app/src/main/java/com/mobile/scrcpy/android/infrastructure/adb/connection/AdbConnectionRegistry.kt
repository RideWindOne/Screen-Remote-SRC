package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

internal class AdbConnectionRegistry {
    private val connectionPool = ConcurrentHashMap<String, AdbConnection>()
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())

    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices.asStateFlow()

    fun getConnection(deviceId: String): AdbConnection? = connectionPool[deviceId]

    fun getAllConnections(): List<AdbConnection> = connectionPool.values.toList()

    fun isDeviceConnected(deviceId: String): Boolean = connectionPool[deviceId]?.isConnected() ?: false

    fun put(connection: AdbConnection) {
        connectionPool[connection.deviceId] = connection
        refreshConnectedDevices()
    }

    fun remove(deviceId: String): AdbConnection? {
        val removed = connectionPool.remove(deviceId)
        refreshConnectedDevices()
        return removed
    }

    fun removeAndClose(deviceId: String) {
        connectionPool.remove(deviceId)?.close()
        refreshConnectedDevices()
    }

    fun refreshConnectedDevices() {
        _connectedDevices.value = connectionPool.values.map { it.deviceInfo }
    }

    fun disconnectDevice(deviceId: String): Result<Boolean> =
        try {
            val connection = connectionPool.remove(deviceId)
            if (connection != null) {
                connection.close()
                refreshConnectedDevices()
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${CommonTexts.LABEL_DEVICE.get()} $deviceId ${AdbTexts.ADB_DEVICE_DISCONNECTED.get()}",
                )
                Result.success(true)
            } else {
                Result.failure(Exception(AdbTexts.ADB_DEVICE_NOT_CONNECTED.get()))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DISCONNECT_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }

    fun disconnectAll() {
        connectionPool.values.forEach { connection ->
            try {
                connection.close()
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_CLOSE_CONNECTION_FAILED.get()}: ${e.message}",
                    e,
                )
            }
        }
        connectionPool.clear()
        refreshConnectedDevices()
    }
}

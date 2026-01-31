package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ConnectionType
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import dadb.Dadb
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 设备信息提供器
 * 负责通过 ADB 获取设备详细信息
 */
internal object DeviceInfoProvider {
    /**
     * 获取设备信息
     */
    suspend fun getDeviceInfo(
        dadb: Dadb,
        deviceId: String,
        customName: String?,
        connectionType: ConnectionType,
    ): DeviceInfo =
        coroutineScope {
            try {
                val modelDeferred = async { dadb.shell("getprop ro.product.model").output.trim() }
                val manufacturerDeferred = async { dadb.shell("getprop ro.product.manufacturer").output.trim() }
                val androidVersionDeferred = async { dadb.shell("getprop ro.build.version.release").output.trim() }
                val serialNumberDeferred = async { dadb.shell("getprop ro.serialno").output.trim() }

                val model = modelDeferred.await()
                val manufacturer = manufacturerDeferred.await()
                val androidVersion = androidVersionDeferred.await()
                val serialNumber = serialNumberDeferred.await()
                val displayName = customName ?: model

                DeviceInfo(
                    deviceId = deviceId,
                    name = displayName,
                    model = model,
                    manufacturer = manufacturer,
                    androidVersion = androidVersion,
                    serialNumber = serialNumber,
                    connectionType = connectionType,
                )
            } catch (e: java.net.ConnectException) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DISCONNECTED_ECONNREFUSED.get()}: ${e.message}")
                throw Exception(AdbTexts.ADB_RECONNECT_DEVICE.get(), e)
            } catch (e: java.io.EOFException) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_HANDSHAKE_FAILED_OR_INTERRUPTED.get()}: ${e.message}",
                )
                throw Exception(AdbTexts.ADB_COMMUNICATION_FAILED.get(), e)
            } catch (e: IllegalStateException) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "连接已断开",
                )
                throw e
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_GET_DEVICE_INFO_FAILED_DETAIL.get()}: ${e.message}",
                    e,
                )
                throw Exception("${AdbTexts.ADB_CANNOT_GET_DEVICE_INFO.get()}: ${e.message}", e)
            }
        }
}

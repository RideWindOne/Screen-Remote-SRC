package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionType
import com.screen.remote.android.core.i18n.AdbTexts
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
                suspend fun shell(command: String): String {
                    try {
                        logShellCommandStart(LogTags.ADB_CONNECTION, command)
                        val response = dadb.shell(command)
                        logShellCommandResult(
                            tag = LogTags.ADB_CONNECTION,
                            command = command,
                            exitCode = response.exitCode,
                            output = response.output,
                            errorOutput = response.errorOutput,
                        )
                        return response.output.trim()
                    } catch (error: Exception) {
                        logShellCommandFailure(LogTags.ADB_CONNECTION, command, error)
                        throw error
                    }
                }

                val modelDeferred = async { shell("getprop ro.product.model") }
                val manufacturerDeferred = async { shell("getprop ro.product.manufacturer") }
                val androidVersionDeferred = async { shell("getprop ro.build.version.release") }
                val serialNumberDeferred = async { shell("getprop ro.serialno") }

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

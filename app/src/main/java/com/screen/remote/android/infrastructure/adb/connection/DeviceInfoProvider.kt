package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionType
import com.screen.remote.android.core.i18n.AdbTexts
import dadb.Dadb

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
            try {
                val command = buildBasicDeviceInfoCommand()
                logShellCommandStart(LogTags.ADB_CONNECTION, command)
                val response =
                    try {
                        dadb.shell(command)
                    } catch (error: Exception) {
                        logShellCommandFailure(LogTags.ADB_CONNECTION, command, error)
                        throw error
                    }
                logShellCommandResult(
                    tag = LogTags.ADB_CONNECTION,
                    command = command,
                    exitCode = response.exitCode,
                    output = response.output,
                    errorOutput = response.errorOutput,
                )
                check(response.exitCode == 0) { response.errorOutput.ifBlank { "Failed to read basic device information" } }
                val properties = parseBasicDeviceInfo(response.output)
                val displayName = customName ?: properties.model

                DeviceInfo(
                    deviceId = deviceId,
                    name = displayName,
                    model = properties.model,
                    manufacturer = properties.manufacturer,
                    androidVersion = properties.androidVersion,
                    serialNumber = properties.serialNumber,
                    connectionType = connectionType,
                )
            } catch (e: java.net.ConnectException) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DISCONNECTED_ECONNREFUSED.english}: ${e.message}")
                throw Exception(AdbTexts.ADB_RECONNECT_DEVICE.get(), e)
            } catch (e: java.io.EOFException) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_HANDSHAKE_FAILED_OR_INTERRUPTED.english}: ${e.message}",
                )
                throw Exception(AdbTexts.ADB_COMMUNICATION_FAILED.get(), e)
            } catch (e: IllegalStateException) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "The connection has been lost",
                )
                throw e
            } catch (e: Exception) {
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_GET_DEVICE_INFO_FAILED_DETAIL.english}: ${e.message}",
                    e,
                )
                throw Exception("${AdbTexts.ADB_CANNOT_GET_DEVICE_INFO.get()}: ${e.message}", e)
            }
}

internal data class BasicDeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val serialNumber: String,
)

internal fun buildBasicDeviceInfoCommand(): String =
    "echo '$BASIC_INFO_MODEL'; getprop ro.product.model; " +
        "echo '$BASIC_INFO_MANUFACTURER'; getprop ro.product.manufacturer; " +
        "echo '$BASIC_INFO_ANDROID'; getprop ro.build.version.release; " +
        "echo '$BASIC_INFO_SERIAL'; getprop ro.serialno"

internal fun parseBasicDeviceInfo(output: String): BasicDeviceInfo =
    BasicDeviceInfo(
        model = output.basicInfoValue(BASIC_INFO_MODEL, BASIC_INFO_MANUFACTURER),
        manufacturer = output.basicInfoValue(BASIC_INFO_MANUFACTURER, BASIC_INFO_ANDROID),
        androidVersion = output.basicInfoValue(BASIC_INFO_ANDROID, BASIC_INFO_SERIAL),
        serialNumber = output.substringAfter(BASIC_INFO_SERIAL, missingDelimiterValue = "").trim(),
    )

private fun String.basicInfoValue(
    marker: String,
    nextMarker: String,
): String = substringAfter(marker, missingDelimiterValue = "").substringBefore(nextMarker).trim()

private const val BASIC_INFO_MODEL = "__SCREEN_REMOTE_MODEL__"
private const val BASIC_INFO_MANUFACTURER = "__SCREEN_REMOTE_MANUFACTURER__"
private const val BASIC_INFO_ANDROID = "__SCREEN_REMOTE_ANDROID__"
private const val BASIC_INFO_SERIAL = "__SCREEN_REMOTE_SERIAL__"

data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val serialNumber: String,
    val connectionType: ConnectionType = ConnectionType.TCP,
)

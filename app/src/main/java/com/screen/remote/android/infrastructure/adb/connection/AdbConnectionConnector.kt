package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import android.hardware.usb.UsbDevice
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.core.domain.model.ConnectionType
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.infrastructure.adb.AdbRuntimeDiagnostics
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import com.screen.remote.android.infrastructure.adb.usb.UsbAdbManager
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import dadb.Dadb
import dadb.android.runtime.AdbRuntime
import dadb.android.runtime.ExperimentalDadbAndroidApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalDadbAndroidApi::class)
internal class AdbConnectionConnector(
    private val context: Context,
    private val usbAdbManager: UsbAdbManager,
    private val connectionRegistry: AdbConnectionRegistry,
) {
    private val adbRuntime: AdbRuntime
        get() = AdbRuntimeProvider.get()

    suspend fun connectTcp(
        host: String,
        port: Int,
        transportDeviceId: String? = null,
        deviceName: String?,
        forceReconnect: Boolean,
        sessionContext: SessionContext?,
    ): Result<AdbConnection> =
        withContext(Dispatchers.IO) {
            val normalizedHost = normalizeEndpointHost(host)
            val deviceId = transportDeviceId ?: DeviceTransportSerial.tcp(normalizedHost, port)
            try {
                connectionRegistry.getConnection(deviceId)?.let { existingConnection ->
                    existingConnection.bindSessionContext(sessionContext)
                    if (forceReconnect) {
                        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_FORCE_RECONNECT_CLEANUP.english)
                        runCatching { existingConnection.close() }
                        connectionRegistry.remove(deviceId)
                    } else {
                        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_VERIFYING_CONNECTION.english)
                        val verifyResult = existingConnection.verify()
                        if (verifyResult.isSuccess) {
                            return@withContext Result.success(existingConnection)
                        }
                        runCatching { existingConnection.close() }
                        connectionRegistry.remove(deviceId)
                    }
                }

                val dadb =
                    try {
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "Endpoint state before connect: ${AdbRuntimeDiagnostics.endpointSummary(
                                context,
                                normalizedHost,
                                port,
                            )}",
                        )
                        adbRuntime.connectNetworkDadbSession(
                            host = normalizedHost,
                            port = port,
                            connectTimeout = 5000,
                            socketTimeout = 5000,
                            warmStreaming = false,
                        )
                    } catch (e: java.net.ConnectException) {
                        LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CONNECTION_REFUSED.english}: ${e.message}")
                        return@withContext Result.failure(Exception(AdbTexts.ADB_CONNECTION_REFUSED_DETAILS.get()))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        return@withContext Result.failure(e)
                    }

                val verifyResult =
                    AdbConnectionVerifier.verifyDadb(
                        dadb,
                        deviceId,
                        sessionContext = sessionContext,
                    )
                if (verifyResult.isFailure) {
                    runCatching { dadb.close() }
                    return@withContext Result.failure(
                        verifyResult.exceptionOrNull() ?: IllegalStateException("ADB verification failed: $deviceId"),
                    )
                }

                val isTlsConnection = dadb.isTlsConnection()
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "Endpoint state after connect: ${AdbRuntimeDiagnostics.endpointSummary(
                        context,
                        normalizedHost,
                        port,
                    )} tls=$isTlsConnection",
                )

                val connectionType =
                    if (isTlsConnection) {
                        ConnectionType.TLS
                    } else {
                        ConnectionType.TCP
                    }
                val serialNumber = verifyResult.getOrDefault(deviceId)
                dadb.warmStreaming()
                val connection =
                    AdbConnection(
                        deviceId = deviceId,
                        host = normalizedHost,
                        port = port,
                        dadb = dadb,
                        deviceInfo =
                            DeviceInfo(
                                deviceId = deviceId,
                                name = deviceName ?: deviceId,
                                serialNumber = serialNumber,
                                connectionType = connectionType,
                            ),
                        sessionContext = sessionContext,
                    )

                connectionRegistry.put(connection)
                enrichDeviceInfo(
                    connection = connection,
                    dadb = dadb,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    connectionType = connectionType,
                )
                Result.success(connection)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${CommonTexts.ERROR_LABEL.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun connectUsb(
        usbDevice: UsbDevice,
        deviceName: String?,
        sessionContext: SessionContext?,
    ): Result<AdbConnection> =
        withContext(Dispatchers.IO) {
            try {
                val serialNumber = ApiCompatHelper.getUsbDeviceSerialNumber(usbDevice) ?: usbDevice.deviceName
                val deviceId = DeviceTransportSerial.usb(serialNumber)

                LogManager.d(LogTags.ADB_CONNECTION, "========== ${AdbTexts.USB_CONNECTING_DEVICE.english} ==========")
                LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.USB_SERIAL_NUMBER.english}: $serialNumber")
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "USB connect request: deviceId=$deviceId requestedName=${deviceName ?: "<none>"} ${formatUsbDeviceDebug(
                        usbDevice,
                    )}",
                )

                connectionRegistry.getConnection(deviceId)?.let { existingConnection ->
                    existingConnection.bindSessionContext(sessionContext)
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "Existing USB connection found: deviceId=$deviceId delayedAck=${existingConnection.supportsDelayedAck()} " +
                            "connected=${runCatching { existingConnection.isConnected() }.getOrDefault(false)} " +
                            "info=${existingConnection.deviceInfo.name}/${existingConnection.deviceInfo.serialNumber}",
                    )
                    val verifyResult = existingConnection.verify()
                    if (verifyResult.isSuccess) {
                        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_CONNECTION_VERIFIED.english)
                        return@withContext Result.success(existingConnection)
                    }
                    LogManager.w(LogTags.ADB_CONNECTION, AdbTexts.ADB_CONNECTION_VERIFY_FAILED.english)
                    runCatching { existingConnection.close() }
                    connectionRegistry.remove(deviceId)
                }

                val permissionResult = usbAdbManager.requestUsbPermission(usbDevice)
                if (permissionResult.isFailure) {
                    LogManager.e(
                        LogTags.ADB_CONNECTION,
                        "USB permission request failed for $deviceId: ${permissionResult.exceptionOrNull()?.message}",
                    )
                    return@withContext Result.failure(
                        permissionResult.exceptionOrNull() ?: IllegalStateException("USB permission request failed: $deviceId"),
                    )
                }
                if (permissionResult.getOrNull() != true) {
                    LogManager.w(LogTags.ADB_CONNECTION, "USB permission denied for $deviceId")
                    return@withContext Result.failure(Exception(AdbTexts.USB_PERMISSION_DENIED.get()))
                }

                var activeUsbDevice = refreshGrantedUsbDevice(usbDevice)
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "USB device refreshed after permission: old=${formatUsbDeviceDebug(
                        usbDevice,
                    )}, new=${formatUsbDeviceDebug(activeUsbDevice)}",
                )

                val usbManager =
                    context.getSystemService(
                        Context.USB_SERVICE,
                    ) as android.hardware.usb.UsbManager

                var detectedSerial = serialNumber
                var lastError: Throwable? = null
                val features = Dadb.connectFeatures(withDelayedAck = true)

                repeat(2) { attempt ->
                    val dadb =
                        try {
                            LogManager.d(
                                LogTags.ADB_CONNECTION,
                                "Creating USB transport Dadb for $deviceId attempt=${attempt + 1} ${formatUsbDeviceDebug(
                                    activeUsbDevice,
                                )}",
                            )
                            adbRuntime.createUsbDadbSession(
                                usbManager = usbManager,
                                usbDevice = activeUsbDevice,
                                description = deviceId,
                                features = features,
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (e: Exception) {
                            lastError = e
                            return@repeat
                        }

                    val verifyResult = AdbConnectionVerifier.verifyDadb(dadb, deviceId, sessionContext = sessionContext)
                    if (verifyResult.isSuccess) {
                        detectedSerial = verifyResult.getOrDefault(serialNumber)
                        lastError = null
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "USB Dadb verify success for $deviceId attempt=${attempt + 1} serial=$detectedSerial",
                        )
                        val connection =
                            AdbConnection(
                                deviceId = deviceId,
                                host = "usb",
                                port = 0,
                                dadb = dadb,
                                deviceInfo =
                                    DeviceInfo(
                                        deviceId = deviceId,
                                        name = deviceName ?: (usbDevice.productName ?: serialNumber),
                                        model = "Unknown",
                                        manufacturer = usbDevice.manufacturerName ?: "Unknown",
                                        androidVersion = "Unknown",
                                        serialNumber = detectedSerial,
                                        connectionType = ConnectionType.USB,
                                    ),
                                sessionContext = sessionContext,
                            )

                        connectionRegistry.put(connection)
                        enrichDeviceInfo(
                            connection = connection,
                            dadb = dadb,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            connectionType = ConnectionType.USB,
                        )
                        return@withContext Result.success(connection)
                    }

                    lastError = verifyResult.exceptionOrNull()
                    LogManager.w(
                        LogTags.ADB_CONNECTION,
                        "USB Dadb verify failed for $deviceId attempt=${attempt + 1}: ${lastError?.javaClass?.simpleName}: ${lastError?.message}",
                    )
                    if (attempt == 0) {
                        LogManager.w(
                            LogTags.ADB_CONNECTION,
                            "USB Dadb verify failed on first attempt, retrying: ${lastError?.message}",
                        )
                        activeUsbDevice = refreshGrantedUsbDevice(activeUsbDevice)
                    }
                }

                if (lastError != null) {
                    LogManager.e(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.USB_CONNECT_FAILED.english}: ${lastError.message}",
                        lastError,
                    )
                    return@withContext Result.failure(
                        Exception("${AdbTexts.USB_CONNECT_FAILED.get()}: ${lastError.message}", lastError),
                    )
                }

                Result.failure(IllegalStateException("USB connection retry exhausted without error"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${CommonTexts.ERROR_LABEL.english}: ${e.message}", e)
                Result.failure(e)
            }
        }

    suspend fun connectUsbByDeviceId(
        deviceId: String,
        deviceName: String?,
        sessionContext: SessionContext?,
    ): Result<AdbConnection> =
        withContext(Dispatchers.IO) {
            val normalizedId = normalizeUsbDeviceId(deviceId)
            val serial = DeviceTransportSerial.stripUsbPrefix(normalizedId)
            val scannedDevices =
                usbAdbManager.scanUsbDevices().getOrElse { error ->
                    LogManager.e(LogTags.ADB_CONNECTION, "USB scan failed for $normalizedId: ${error.message}", error)
                    return@withContext Result.failure(error)
                }
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "USB connect by id scan result for $normalizedId: ${
                    scannedDevices.joinToString(separator = " | ") { formatUsbDeviceInfoDebug(it) }
                }",
            )

            val matchedDevice =
                resolveUsbDeviceForConnection(
                    deviceId = deviceId,
                    serial = serial,
                    scannedDevices = scannedDevices,
                )

            if (matchedDevice != null) {
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "USB connect by id matched device for $normalizedId: ${formatUsbDeviceInfoDebug(matchedDevice)}",
                )
                return@withContext connectUsb(
                    usbDevice = matchedDevice.device,
                    deviceName = deviceName,
                    sessionContext = sessionContext,
                )
            }

            val permissionCandidate =
                scannedDevices.singleOrNull { device ->
                    !device.hasPermission && device.serialNumber.isBlank()
                }

            if (permissionCandidate != null) {
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "USB device requires permission before matching session $deviceId, requesting permission for ${permissionCandidate.device.deviceName}",
                )

                val permissionResult = usbAdbManager.requestUsbPermission(permissionCandidate.device)
                if (permissionResult.isFailure) {
                    return@withContext Result.failure(permissionResult.exceptionOrNull()!!)
                }
                if (permissionResult.getOrNull() != true) {
                    return@withContext Result.failure(Exception(AdbTexts.USB_PERMISSION_DENIED.get()))
                }

                val refreshedDevices =
                    usbAdbManager.scanUsbDevices().getOrElse { error ->
                        LogManager.e(
                            LogTags.ADB_CONNECTION,
                            "USB rescan failed after permission for $normalizedId: ${error.message}",
                            error,
                        )
                        return@withContext Result.failure(error)
                    }
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "USB rescan after permission for $normalizedId: ${
                        refreshedDevices.joinToString(separator = " | ") { formatUsbDeviceInfoDebug(it) }
                    }",
                )

                val refreshedMatch =
                    resolveUsbDeviceForConnection(
                        deviceId = deviceId,
                        serial = serial,
                        scannedDevices = refreshedDevices,
                    ) ?: refreshedDevices.singleOrNull { it.hasPermission }

                if (refreshedMatch != null) {
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "USB connect by id matched after permission for $normalizedId: ${formatUsbDeviceInfoDebug(
                            refreshedMatch,
                        )}",
                    )
                    return@withContext connectUsb(
                        usbDevice = refreshedMatch.device,
                        deviceName = deviceName,
                        sessionContext = sessionContext,
                    )
                }
            }

            Result.failure(
                IllegalStateException("USB device not found: $deviceId"),
            )
        }

    private fun resolveUsbDeviceForConnection(
        deviceId: String,
        serial: String,
        scannedDevices: List<com.screen.remote.android.infrastructure.adb.usb.UsbDeviceInfo>,
    ) = scannedDevices.firstOrNull { device ->
        val matched =
            device.serialNumber == serial ||
                device.device.deviceName == serial ||
                device.device.deviceName == deviceId
        if (matched) {
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "USB match hit for sessionDeviceId=$deviceId serial=$serial candidate=${formatUsbDeviceInfoDebug(
                    device,
                )}",
            )
        }
        matched
    }

    private fun refreshGrantedUsbDevice(originalDevice: UsbDevice): UsbDevice {
        val refreshedDevices = usbAdbManager.scanUsbDevices().getOrElse { return originalDevice }
        val grantedDevices = refreshedDevices.filter { it.hasPermission }
        if (grantedDevices.isEmpty()) {
            return originalDevice
        }

        val originalSerial =
            runCatching { ApiCompatHelper.getUsbDeviceSerialNumber(originalDevice) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }

        val matchedBySerial =
            originalSerial?.let { serial ->
                grantedDevices.firstOrNull { it.serialNumber == serial }?.device
            }
        if (matchedBySerial != null) {
            return matchedBySerial
        }

        val matchedByIdentity =
            grantedDevices
                .firstOrNull {
                    it.productName == (originalDevice.productName ?: "Unknown") &&
                        it.manufacturerName == (originalDevice.manufacturerName ?: "Unknown")
                }?.device

        return matchedByIdentity ?: grantedDevices.first().device
    }

    private fun normalizeUsbDeviceId(deviceId: String): String =
        DeviceTransportSerial.usb(deviceId)

    private fun enrichDeviceInfo(
        connection: AdbConnection,
        dadb: Dadb,
        deviceId: String,
        deviceName: String?,
        connectionType: ConnectionType,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val detailedDeviceInfo =
                    DeviceInfoProvider.getDeviceInfo(
                        dadb = dadb,
                        deviceId = deviceId,
                        customName = deviceName,
                        connectionType = connectionType,
                    )
                connection.deviceInfo = detailedDeviceInfo
                connectionRegistry.refreshConnectedDevices()

                if (connectionType == ConnectionType.USB) {
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${SessionTexts.LABEL_DEVICE_INFO.english}: ${detailedDeviceInfo.name} (${detailedDeviceInfo.model})",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogManager.w(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_GET_DEVICE_INFO_FAILED.english}: ${e.message}",
                )
            }
        }
    }
}

private fun formatUsbDeviceDebug(device: UsbDevice): String =
    "path=${device.deviceName} vendor=${device.vendorId} product=${device.productId} " +
        "manufacturer=${device.manufacturerName ?: "Unknown"} productName=${device.productName ?: "Unknown"} interfaces=${device.interfaceCount}"

private fun formatUsbDeviceInfoDebug(device: com.screen.remote.android.infrastructure.adb.usb.UsbDeviceInfo): String =
    "path=${device.device.deviceName} serial=${device.serialNumber.ifBlank {
        "<none>"
    }} permission=${device.hasPermission} " +
        "manufacturer=${device.manufacturerName} product=${device.productName}"

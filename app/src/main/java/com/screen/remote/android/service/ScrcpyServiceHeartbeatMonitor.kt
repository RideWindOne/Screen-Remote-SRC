package com.screen.remote.android.service

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.parseSessionAddressCandidate
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

internal class ScrcpyServiceHeartbeatMonitor(
    private val applicationContext: Context,
    private val protectedDevices: ConcurrentHashMap<String, ProtectedAdbDevice>,
    private val onDevicesChanged: () -> Unit,
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    companion object {
        private const val HEARTBEAT_INTERVAL = 15_000L
    }

    fun start() {
        stop()

        heartbeatJob =
            serviceScope.launch {
                LogManager.d(
                    LogTags.SCRCPY_SERVICE,
                    "ADB heartbeat detection has started (interval: ${HEARTBEAT_INTERVAL}ms)"
                )

                while (isActive) {
                    delay(HEARTBEAT_INTERVAL.milliseconds)

                    if (protectedDevices.isEmpty()) {
                        continue
                    }

                    val adbManager = AdbConnectionManager.getInstance(applicationContext)
                    val protectedSnapshots = protectedDevices.entries.toList()
                    val devicesToRemove =
                        protectedSnapshots
                            .map { (deviceId, protectedDevice) ->
                                async {
                                    val expectedConnection = adbManager.getConnection(deviceId)
                                    val reconnectSucceeded =
                                        try {
                                            checkDeviceHeartbeat(
                                                deviceId = deviceId,
                                                protectedDevice = protectedDevice,
                                                connection = expectedConnection,
                                                adbManager = adbManager,
                                            )
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            false
                                        }

                                    if (!reconnectSucceeded) {
                                        FailedProtectedDeviceHeartbeat(
                                            deviceId = deviceId,
                                            protectedDevice = protectedDevice,
                                            expectedConnection = expectedConnection,
                                        )
                                    } else {
                                        null
                                    }
                                }
                            }
                            .awaitAll()
                            .filterNotNull()

                    var removedAny = false
                    devicesToRemove.forEach { failure ->
                        val deviceId = failure.deviceId
                        val expectedDevice = failure.protectedDevice
                        if (!removeProtectedDeviceIfCurrent(protectedDevices, deviceId, expectedDevice)) {
                            LogManager.d(LogTags.SCRCPY_SERVICE, "Skip updated heartbeat failed device: $deviceId")
                            return@forEach
                        }
                        removedAny = true
                        LogManager.d(
                            LogTags.SCRCPY_SERVICE,
                            "Failed device removed: ${expectedDevice.deviceName} ($deviceId)"
                        )
                        failure.expectedConnection?.let { expectedConnection ->
                            try {
                                adbManager.disconnectDeviceIfCurrent(deviceId, expectedConnection)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                LogManager.e(
                                    LogTags.SCRCPY_SERVICE,
                                    "Conditional disconnection of ADB connection failed: ${e.message}"
                                )
                            }
                        }
                    }

                    if (removedAny) {
                        onDevicesChanged()
                    }
                }
            }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun destroy() {
        heartbeatJob = null
        serviceScope.cancel()
    }

    private suspend fun checkDeviceHeartbeat(
        deviceId: String,
        protectedDevice: ProtectedAdbDevice,
        connection: AdbConnection?,
        adbManager: AdbConnectionManager,
    ): Boolean =
        try {
            if (!isStillProtected(deviceId, protectedDevice)) {
                return true
            }
            if (connection == null) {
                LogManager.w(
                    LogTags.SCRCPY_SERVICE,
                    "ADB connection does not exist: $deviceId, try to rebuild according to the original connection protected=${protectedDevices.keys.joinToString()}",
                )
                return tryReconnect(deviceId, protectedDevice, adbManager)
            }

            if (connection.isConnected()) {
                return true
            }

            LogManager.w(
                LogTags.SCRCPY_SERVICE,
                "ADB heartbeat failure: $deviceId delayedAck=${connection.supportsDelayedAck()}, try to reconnect according to the original connection",
            )
            tryReconnect(deviceId, protectedDevice, adbManager)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LogManager.e(
                LogTags.SCRCPY_SERVICE,
                "ADB heartbeat abnormality $deviceId: ${e.message}, try to reconnect according to the original connection",
            )
            tryReconnect(deviceId, protectedDevice, adbManager)
        }

    private suspend fun tryReconnect(
        deviceId: String,
        protectedDevice: ProtectedAdbDevice,
        adbManager: AdbConnectionManager,
    ): Boolean =
        try {
            if (!isStillProtected(deviceId, protectedDevice)) {
                return true
            }
            LogManager.d(
                LogTags.SCRCPY_SERVICE,
                "Start reconnecting to ADB according to the original connection: $deviceId"
            )

            reconnectExactDevice(
                deviceId = deviceId,
                protectedDevice = protectedDevice,
                adbManager = adbManager,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "✗ ADB reconnection exception: $deviceId - ${e.message}")
            false
        }

    private suspend fun reconnectExactDevice(
        deviceId: String,
        protectedDevice: ProtectedAdbDevice,
        adbManager: AdbConnectionManager,
    ): Boolean {
        val candidate = parseExactProtectedConnection(deviceId)
        if (candidate == null) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "Unable to resolve protected exact ADB connection ID: $deviceId")
            return false
        }
        if (!isStillProtected(deviceId, protectedDevice)) return true

        val result =
            when (candidate.transport) {
                ConnectionTransport.USB -> adbManager.connectUsbDeviceById(deviceId)
                ConnectionTransport.MDNS ->
                    adbManager.connectMdnsService(
                        serviceName = candidate.host,
                    )

                ConnectionTransport.TCP ->
                    adbManager.connectDevice(
                        host = candidate.host,
                        port = candidate.port,
                    )
            }

        result.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
        }
        if (!isStillProtected(deviceId, protectedDevice)) return true

        val connectedDeviceId = result.getOrNull()
        if (result.isSuccess && connectedDeviceId == deviceId) {
            LogManager.d(LogTags.SCRCPY_SERVICE, "ADB original connection reconnected successfully: $deviceId")
            return true
        }

        if (!connectedDeviceId.isNullOrBlank() && connectedDeviceId != deviceId) {
            runCatching { adbManager.disconnectDevice(connectedDeviceId) }
        }
        LogManager.e(
            LogTags.SCRCPY_SERVICE,
            "✗ ADB original connection failed to reconnect: $deviceId result=$connectedDeviceId error=${result.exceptionOrNull()?.message}",
        )
        return false
    }

    private fun isStillProtected(
        deviceId: String,
        protectedDevice: ProtectedAdbDevice,
    ): Boolean = protectedDevices[deviceId] === protectedDevice

}

internal fun removeProtectedDeviceIfCurrent(
    protectedDevices: ConcurrentHashMap<String, ProtectedAdbDevice>,
    deviceId: String,
    expectedDevice: ProtectedAdbDevice,
): Boolean = protectedDevices.remove(deviceId, expectedDevice)

internal fun parseExactProtectedConnection(deviceId: String) =
    parseSessionAddressCandidate(deviceId)
        ?.takeIf { candidate -> candidate.deviceIdentifier() == deviceId }

private data class FailedProtectedDeviceHeartbeat(
    val deviceId: String,
    val protectedDevice: ProtectedAdbDevice,
    val expectedConnection: AdbConnection?,
)

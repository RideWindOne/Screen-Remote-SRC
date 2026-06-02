package com.screen.remote.android.service

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.parseSessionAddressCandidate
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
                LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 心跳检测已启动（间隔: ${HEARTBEAT_INTERVAL}ms）")

                while (isActive) {
                    delay(HEARTBEAT_INTERVAL)

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

                    if (protectedSnapshots.isNotEmpty()) {
                        LogManager.d(
                            LogTags.SCRCPY_SERVICE,
                            "本轮心跳检测完成 protected=${protectedSnapshots.size}, remove=${devicesToRemove.size}",
                        )
                    }

                    var removedAny = false
                    devicesToRemove.forEach { failure ->
                        val deviceId = failure.deviceId
                        val expectedDevice = failure.protectedDevice
                        if (!removeProtectedDeviceIfCurrent(protectedDevices, deviceId, expectedDevice)) {
                            LogManager.d(LogTags.SCRCPY_SERVICE, "跳过已更新的心跳失败设备: $deviceId")
                            return@forEach
                        }
                        removedAny = true
                        LogManager.d(LogTags.SCRCPY_SERVICE, "已移除失败设备: ${expectedDevice.deviceName} ($deviceId)")
                        failure.expectedConnection?.let { expectedConnection ->
                            try {
                                adbManager.disconnectDeviceIfCurrent(deviceId, expectedConnection)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                LogManager.e(LogTags.SCRCPY_SERVICE, "条件断开 ADB 连接失败: ${e.message}")
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
                    "ADB 连接不存在: $deviceId，尝试按原连接重建 protected=${protectedDevices.keys.joinToString()}",
                )
                return tryReconnect(deviceId, protectedDevice, adbManager)
            }

            if (connection.isConnected()) {
                LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 心跳正常: $deviceId")
                return true
            }

            LogManager.w(
                LogTags.SCRCPY_SERVICE,
                "ADB 心跳失败: $deviceId delayedAck=${connection.supportsDelayedAck()}，尝试按原连接重连",
            )
            SessionIssueTracker.record("adb.heartbeat", "Heartbeat failed for $deviceId")
            tryReconnect(deviceId, protectedDevice, adbManager)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LogManager.e(
                LogTags.SCRCPY_SERVICE,
                "ADB 心跳异常 $deviceId: ${e.message}，尝试按原连接重连",
            )
            SessionIssueTracker.record("adb.heartbeat", "Heartbeat exception for $deviceId: ${e.message}")
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
            LogManager.d(LogTags.SCRCPY_SERVICE, "开始按原连接重连 ADB: $deviceId")

            reconnectExactDevice(
                deviceId = deviceId,
                protectedDevice = protectedDevice,
                adbManager = adbManager,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "✗ ADB 重连异常: $deviceId - ${e.message}")
            false
        }

    private suspend fun reconnectExactDevice(
        deviceId: String,
        protectedDevice: ProtectedAdbDevice,
        adbManager: AdbConnectionManager,
    ): Boolean {
        val candidate = parseExactProtectedConnection(deviceId)
        if (candidate == null) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "无法解析受保护的精确 ADB 连接标识: $deviceId")
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
            LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 原连接重连成功: $deviceId")
            return true
        }

        if (!connectedDeviceId.isNullOrBlank() && connectedDeviceId != deviceId) {
            runCatching { adbManager.disconnectDevice(connectedDeviceId) }
        }
        LogManager.e(
            LogTags.SCRCPY_SERVICE,
            "✗ ADB 原连接重连失败: $deviceId result=$connectedDeviceId error=${result.exceptionOrNull()?.message}",
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

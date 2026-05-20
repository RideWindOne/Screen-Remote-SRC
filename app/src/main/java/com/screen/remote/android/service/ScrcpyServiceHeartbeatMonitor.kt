package com.screen.remote.android.service

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class ScrcpyServiceHeartbeatMonitor(
    private val applicationContext: Context,
    private val protectedDevices: ConcurrentHashMap<String, ProtectedAdbDevice>,
    private val onDevicesChanged: () -> Unit,
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
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
                    val devicesToRemove = mutableListOf<String>()

                    protectedDevices.forEach { (deviceId, protectedDevice) ->
                        val reconnectSucceeded =
                            try {
                                val connection = adbManager.getConnection(deviceId)
                                if (connection == null) {
                                    LogManager.w(
                                        LogTags.SCRCPY_SERVICE,
                                        "ADB 连接不存在: $deviceId，尝试重建 delayedAck=${protectedDevice.delayedAck} protected=${protectedDevices.keys.joinToString()}",
                                    )
                                    tryReconnect(deviceId, protectedDevice, adbManager)
                                } else {
                                    val result = connection.executeShell("echo 1", retryOnFailure = false)
                                    if (result.isSuccess) {
                                        LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 心跳正常: $deviceId")
                                        true
                                    } else {
                                        LogManager.w(
                                            LogTags.SCRCPY_SERVICE,
                                            "ADB 心跳失败: $deviceId delayedAck=${protectedDevice.delayedAck} connectionDelayedAck=${connection.supportsDelayedAck()}，尝试重连",
                                        )
                                        SessionIssueTracker.record("adb.heartbeat", "Heartbeat failed for $deviceId")
                                        tryReconnect(deviceId, protectedDevice, adbManager)
                                    }
                                }
                            } catch (e: Exception) {
                                LogManager.e(
                                    LogTags.SCRCPY_SERVICE,
                                    "ADB 心跳异常 $deviceId delayedAck=${protectedDevice.delayedAck}: ${e.message}，尝试重连",
                                )
                                SessionIssueTracker.record("adb.heartbeat", "Heartbeat exception for $deviceId: ${e.message}")
                                tryReconnect(deviceId, protectedDevice, adbManager)
                            }

                        if (!reconnectSucceeded) {
                            devicesToRemove.add(deviceId)
                        }
                    }

                    devicesToRemove.forEach { deviceId ->
                        val device = protectedDevices.remove(deviceId)
                        LogManager.d(LogTags.SCRCPY_SERVICE, "已移除失败设备: ${device?.deviceName} ($deviceId)")
                        try {
                            adbManager.disconnectDevice(deviceId)
                        } catch (e: Exception) {
                            LogManager.e(LogTags.SCRCPY_SERVICE, "断开 ADB 连接失败: ${e.message}")
                        }
                    }

                    if (devicesToRemove.isNotEmpty()) {
                        onDevicesChanged()
                    }
                }
            }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun tryReconnect(
        deviceId: String,
        protectedDevice: ProtectedAdbDevice,
        adbManager: AdbConnectionManager,
    ): Boolean =
        try {
            val delayedAck = protectedDevice.delayedAck
            LogManager.d(LogTags.SCRCPY_SERVICE, "开始重连 ADB: $deviceId delayedAck=$delayedAck")

            if (protectedDevice.isUsbConnection) {
                val result = adbManager.connectUsbDeviceById(deviceId, withDelayedAck = delayedAck)
                if (result.isSuccess) {
                    LogManager.d(LogTags.SCRCPY_SERVICE, "USB ADB 重连成功: $deviceId")
                    return true
                }

                LogManager.e(
                    LogTags.SCRCPY_SERVICE,
                    "✗ USB ADB 重连失败: $deviceId - ${result.exceptionOrNull()?.message}",
                )
                return false
            }

            val host = protectedDevice.host
            val port = protectedDevice.port
            if (host.isNullOrBlank() || port <= 0) {
                LogManager.e(LogTags.SCRCPY_SERVICE, "设备缺少会话连接配置，无法重连: $deviceId host=${host ?: "-"} port=$port")
                return false
            }

            val result = adbManager.connectDevice(host, port, forceReconnect = true, withDelayedAck = delayedAck)

            if (result.isSuccess) {
                LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 重连成功: $deviceId")
                true
            } else {
                LogManager.e(LogTags.SCRCPY_SERVICE, "✗ ADB 重连失败: $deviceId - ${result.exceptionOrNull()?.message}")
                false
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "✗ ADB 重连异常: $deviceId - ${e.message}")
            false
        }
}

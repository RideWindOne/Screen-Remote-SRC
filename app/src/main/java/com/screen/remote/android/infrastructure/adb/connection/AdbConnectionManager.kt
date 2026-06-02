package com.screen.remote.android.infrastructure.adb.connection

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ConnectionLost
import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.event.UsbDeviceDisconnected
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.infrastructure.adb.AdbRuntimeDiagnostics
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import com.screen.remote.android.infrastructure.adb.key.core.adb.AdbKeyManager
import com.screen.remote.android.infrastructure.adb.usb.UsbAdbManager
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import dadb.android.runtime.ExperimentalDadbAndroidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局 ADB 连接管理器
 * 负责暴露统一 API，并装配连接池、建链流程和保活协作者。
 */
@OptIn(ExperimentalDadbAndroidApi::class)
class AdbConnectionManager private constructor(
    private val context: Context,
) {
    private val keyManager = AdbKeyManager(context)
    private val usbAdbManager: UsbAdbManager by lazy { UsbAdbManager(context) }
    private val connectionRegistry = AdbConnectionRegistry()
    private val connector =
        AdbConnectionConnector(
            context = context,
            usbAdbManager = usbAdbManager,
            connectionRegistry = connectionRegistry,
        )
    private var usbDetachReceiver: BroadcastReceiver? = null
    private val connectionOperationLocks = ConcurrentHashMap<String, Mutex>()

    val connectedDevices: StateFlow<List<DeviceInfo>> = connectionRegistry.connectedDevices

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AdbConnectionManager? = null

        fun getInstance(context: Context): AdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: AdbConnectionManager(context.applicationContext).also { instance = it }
            }
    }

    init {
        LogManager.d(LogTags.ADB_CONNECTION, "ADB 连接管理器初始化")
        registerUsbDetachReceiver()
    }

    suspend fun connectDevice(
        host: String,
        port: Int = 5555,
        deviceName: String? = null,
        forceReconnect: Boolean = false,
        sessionContext: SessionContext? = null,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val normalizedHost = normalizeEndpointHost(host)
            withDeviceOperationLock(DeviceTransportSerial.tcp(normalizedHost, port)) {
                emit(sessionContext, SessionEvent.AdbConnecting)
                connector.connectTcp(
                    host = normalizedHost,
                    port = port,
                    deviceName = deviceName,
                    forceReconnect = forceReconnect,
                    sessionContext = sessionContext,
                )
            }
        }

    suspend fun connectMdnsService(
        serviceName: String,
        deviceName: String? = null,
        forceReconnect: Boolean = false,
        sessionContext: SessionContext? = null,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val normalizedServiceName = serviceName.trim()
            val mdnsDeviceId = DeviceTransportSerial.mdns(normalizedServiceName)
            withDeviceOperationLock(mdnsDeviceId) {
                emit(sessionContext, SessionEvent.AdbConnecting)
                val service =
                    runCatching {
                        AdbMdnsServiceResolver.resolveTlsConnectService(normalizedServiceName)
                    }.getOrElse { error ->
                        return@withDeviceOperationLock Result.failure(
                            Exception("mDNS service not found: $normalizedServiceName", error),
                        )
                    }
                connector.connectTcp(
                    host = service.host,
                    port = service.port,
                    transportDeviceId = mdnsDeviceId,
                    deviceName = deviceName ?: service.name,
                    forceReconnect = forceReconnect,
                    sessionContext = sessionContext,
                )
            }
        }

    suspend fun connectUsbDevice(
        usbDevice: UsbDevice,
        deviceName: String? = null,
        sessionContext: SessionContext? = null,
    ): Result<String> =
        withDeviceOperationLock(resolveUsbOperationKey(usbDevice)) {
            connector.connectUsb(
                usbDevice = usbDevice,
                deviceName = deviceName,
                sessionContext = sessionContext,
            )
        }

    suspend fun connectUsbDeviceById(
        deviceId: String,
        deviceName: String? = null,
        sessionContext: SessionContext? = null,
    ): Result<String> =
        withDeviceOperationLock(normalizeUsbDeviceId(deviceId)) {
            connector.connectUsbByDeviceId(
                deviceId = deviceId,
                deviceName = deviceName,
                sessionContext = sessionContext,
            )
        }

    /**
     * Resolve, connect and verify one session candidate using the same transport rules as scrcpy.
     */
    suspend fun connectCandidate(candidate: ConnectionCandidate): Result<AdbConnection> =
        withContext(Dispatchers.IO) {
            runCatching {
                val deviceId = candidate.deviceIdentifier()
                getConnection(deviceId)?.let { existingConnection ->
                    if (existingConnection.verifyWithoutSessionEvents().isSuccess) {
                        return@withContext Result.success(existingConnection)
                    }
                    disconnectDeviceIfCurrent(deviceId, existingConnection).getOrThrow()
                }

                val connectedDeviceId =
                    when (candidate.transport) {
                        ConnectionTransport.TCP ->
                            connectDevice(candidate.host, candidate.port).getOrThrow()
                        ConnectionTransport.MDNS ->
                            connectMdnsService(candidate.host).getOrThrow()
                        ConnectionTransport.USB ->
                            connectUsbDeviceById(deviceId).getOrThrow()
                    }

                getConnection(connectedDeviceId)
                    ?: throw IllegalStateException("ADB connection missing after connect: $connectedDeviceId")
            }
        }

    suspend fun scanUsbDevices() = usbAdbManager.scanUsbDevices()

    suspend fun requestUsbPermission(device: UsbDevice) = usbAdbManager.requestUsbPermission(device)

    fun getUsbDevices() = usbAdbManager.usbDevices

    suspend fun verifyConnection(deviceId: String): Boolean {
        val connection = getConnection(deviceId) ?: return false
        return connection.verify().isSuccess
    }

    suspend fun disconnectDevice(deviceId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            withDeviceOperationLock(deviceId) {
                connectionRegistry.disconnectDevice(deviceId)
            }
        }

    suspend fun disconnectDeviceIfCurrent(
        deviceId: String,
        expectedConnection: AdbConnection,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            withDeviceOperationLock(deviceId) {
                connectionRegistry.disconnectDeviceIfCurrent(deviceId, expectedConnection)
            }
        }

    fun getConnection(deviceId: String): AdbConnection? = connectionRegistry.getConnection(deviceId)

    fun getAllConnections(): List<AdbConnection> = connectionRegistry.getAllConnections()

    fun isDeviceConnected(deviceId: String): Boolean = connectionRegistry.isDeviceConnected(deviceId)

    suspend fun disconnectAll() =
        withContext(Dispatchers.IO) {
            LogManager.d(LogTags.ADB_CONNECTION, "断开所有 ADB 连接")
            connectionRegistry.disconnectAll()
        }

    fun getKeyPair() = keyManager.getKeyPair()

    fun getPublicKey() = keyManager.getPublicKey()

    fun reloadKeyPair() = keyManager.reloadKeyPair()

    suspend fun refreshRuntimeIdentity() =
        withContext(Dispatchers.IO) {
            LogManager.d(LogTags.ADB_CONNECTION, "Refreshing ADB runtime identity, disconnecting stale connections")
            connectionRegistry.disconnectAll()
            AdbBridge.clearConnection()
            keyManager.reloadKeyPair()
            LogManager.d(LogTags.ADB_CONNECTION, "Runtime identity refreshed: ${AdbRuntimeDiagnostics.identitySummary(AdbRuntimeProvider.get())}")
        }

    private fun emit(
        sessionContext: SessionContext?,
        event: SessionEvent,
    ) {
        sessionContext?.emit(event)
    }

    private suspend fun <T> withDeviceOperationLock(
        deviceId: String,
        action: suspend () -> T,
    ): T = connectionOperationLocks.getOrPut(deviceId) { Mutex() }.withLock { action() }

    private fun resolveUsbOperationKey(usbDevice: UsbDevice): String {
        val serial =
            runCatching { ApiCompatHelper.getUsbDeviceSerialNumber(usbDevice) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: usbDevice.deviceName
        return normalizeUsbDeviceId(serial)
    }

    private fun normalizeUsbDeviceId(deviceId: String): String =
        DeviceTransportSerial.usb(deviceId)

    private fun registerUsbDetachReceiver() {
        if (usbDetachReceiver != null) {
            return
        }

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) {
                        return
                    }

                    val detachedDevice =
                        ApiCompatHelper.getParcelableExtraCompat(
                            intent,
                            UsbManager.EXTRA_DEVICE,
                            UsbDevice::class.java,
                        ) ?: return

                    val detachedSerial =
                        runCatching { ApiCompatHelper.getUsbDeviceSerialNumber(detachedDevice) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }

                    val activeUsbConnections =
                        connectionRegistry.getAllConnections()
                            .filter { it.deviceId.startsWith("usb:") }

                    val matchedConnection =
                        detachedSerial?.let { serial ->
                            activeUsbConnections.firstOrNull { it.deviceId == DeviceTransportSerial.usb(serial) }
                        } ?: activeUsbConnections.singleOrNull()

                    LogManager.w(
                        LogTags.USB_CONNECTION,
                        "USB detached: device=${detachedDevice.deviceName}, serial=${detachedSerial ?: "<unknown>"}, matched=${matchedConnection?.deviceId ?: "<none>"}, " +
                            "activeUsbConnections=${activeUsbConnections.joinToString { it.deviceId }}",
                    )

                    matchedConnection?.let { connection ->
                        connection.handleTransportDisconnected("USB device detached")
                        connectionRegistry.removeAndClose(connection.deviceId)
                        ScrcpyEventBus.pushEvent(UsbDeviceDisconnected)
                        ScrcpyEventBus.pushEvent(
                            ConnectionLost(
                                deviceId = connection.deviceId,
                                reason = "USB device detached",
                            ),
                        )
                    }
                }
            }

        ApiCompatHelper.registerReceiverCompat(
            context = context,
            receiver = receiver,
            filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            exported = false,
        )
        usbDetachReceiver = receiver
        LogManager.d(LogTags.USB_CONNECTION, "USB detach receiver registered")
    }
}

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

    fun disconnectDeviceIfCurrent(
        deviceId: String,
        expectedConnection: AdbConnection,
    ): Result<Boolean> =
        try {
            if (!connectionPool.remove(deviceId, expectedConnection)) {
                return Result.success(false)
            }
            expectedConnection.close()
            refreshConnectedDevices()
            LogManager.d(LogTags.ADB_CONNECTION, "已清理指定的 ADB 连接对象: $deviceId")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "条件断开 ADB 连接失败 $deviceId: ${e.message}", e)
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

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
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.infrastructure.adb.AdbRuntimeDiagnostics
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import com.screen.remote.android.infrastructure.adb.key.core.adb.AdbKeyManager
import com.screen.remote.android.infrastructure.adb.usb.UsbAdbManager
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import dadb.android.runtime.ExperimentalDadbAndroidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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
        withDelayedAck: Boolean = true,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val normalizedHost = normalizeEndpointHost(host)
            withDeviceOperationLock(formatHostPort(normalizedHost, port)) {
                emit(sessionContext, SessionEvent.AdbConnecting)
                connector.connectTcp(
                    host = normalizedHost,
                    port = port,
                    deviceName = deviceName,
                    forceReconnect = forceReconnect,
                    sessionContext = sessionContext,
                    withDelayedAck = withDelayedAck,
                )
            }
        }

    suspend fun connectUsbDevice(
        usbDevice: UsbDevice,
        deviceName: String? = null,
        sessionContext: SessionContext? = null,
        withDelayedAck: Boolean = true,
    ): Result<String> =
        withDeviceOperationLock(resolveUsbOperationKey(usbDevice)) {
            connector.connectUsb(
                usbDevice = usbDevice,
                deviceName = deviceName,
                sessionContext = sessionContext,
                withDelayedAck = withDelayedAck,
            )
        }

    suspend fun connectUsbDeviceById(
        deviceId: String,
        deviceName: String? = null,
        sessionContext: SessionContext? = null,
        withDelayedAck: Boolean = true,
    ): Result<String> =
        withDeviceOperationLock(normalizeUsbDeviceId(deviceId)) {
            connector.connectUsbByDeviceId(
                deviceId = deviceId,
                deviceName = deviceName,
                sessionContext = sessionContext,
                withDelayedAck = withDelayedAck,
            )
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
    ): T = connectionOperationLocks.computeIfAbsent(deviceId) { Mutex() }.withLock { action() }

    private fun resolveUsbOperationKey(usbDevice: UsbDevice): String {
        val serial =
            runCatching { ApiCompatHelper.getUsbDeviceSerialNumber(usbDevice) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: usbDevice.deviceName
        return normalizeUsbDeviceId(serial)
    }

    private fun normalizeUsbDeviceId(deviceId: String): String =
        if (deviceId.startsWith("usb:")) {
            deviceId
        } else {
            "usb:$deviceId"
        }

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
                            activeUsbConnections.firstOrNull { it.deviceId == "usb:$serial" }
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

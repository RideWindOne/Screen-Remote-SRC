package com.mobile.scrcpy.android.infrastructure.adb.connection

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbDevice
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.infrastructure.adb.key.core.adb.AdbKeyManager
import com.mobile.scrcpy.android.infrastructure.adb.usb.UsbAdbManager
import com.mobile.scrcpy.android.infrastructure.adb.usb.UsbDadb
import com.mobile.scrcpy.android.core.domain.model.ConnectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
/**
 * 全局 ADB 连接管理器
 * 负责管理所有设备的 ADB 连接（TCP 和 USB），保持会话不主动关闭
 * 
 * 职责：
 * - 连接池管理
 * - TCP/USB 设备连接
 * - USB 设备扫描
 * - 连接保活（委托给 AdbConnectionKeepAlive）
 * 
 * 已拆分模块：
 * - AdbKeyManager: 密钥对管理
 * - AdbConnectionVerifier: 连接验证
 * - DeviceInfoProvider: 设备信息获取
 * - AdbConnectionKeepAlive: 连接保活
 */
class AdbConnectionManager private constructor(private val context: Context) {
    // 设备连接池：deviceId -> AdbConnection
    private val connectionPool = ConcurrentHashMap<String, AdbConnection>()
    
    // 连接状态流
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices.asStateFlow()
    
    // 密钥管理器
    private val keyManager = AdbKeyManager(context)
    
    // USB 管理器
    private val usbAdbManager: UsbAdbManager by lazy { UsbAdbManager(context) }
    
    // 心跳保活管理器
    private val keepAliveManager = AdbConnectionKeepAlive(
        keepAliveInterval = 30_000L,
        onConnectionFailed = { deviceId ->
            connectionPool.remove(deviceId)?.close()
            updateConnectedDevices()
        }
    )
    
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AdbConnectionManager? = null
        
        fun getInstance(context: Context): AdbConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: AdbConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_MANAGER_INIT.get())
        keepAliveManager.start { connectionPool.values.toList() }
    }
    
    /**
     * 连接设备
     * @param host 设备 IP 地址
     * @param port ADB 端口，默认 5555
     * @param deviceName 设备名称（可选，用于显示）
     * @return 设备 ID（host:port）
     */
    suspend fun connectDevice(
        host: String,
        port: Int = 5555,
        deviceName: String? = null,
        forceReconnect: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val deviceId = "$host:$port"
            
            val keyPair = keyManager.getKeyPair()
                ?: return@withContext Result.failure(Exception(AdbTexts.ADB_KEYPAIR_NOT_INITIALIZED.get()))
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${AdbTexts.ADB_START_CONNECTING.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_TARGET_ADDRESS.get()}: $deviceId")
            
            // 检查已有连接
            connectionPool[deviceId]?.let { existingConnection ->
                if (forceReconnect) {
                    // 强制重连：清理旧连接
                    LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_FORCE_RECONNECT_CLEANUP.get())
                    runCatching { existingConnection.close() }
                    connectionPool.remove(deviceId)
                } else {
                    // 验证已有连接
                    LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_VERIFYING_CONNECTION.get())
                    val isValid = AdbConnectionVerifier.verifyConnection(existingConnection)
                    if (isValid) {
                        LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_CONNECTION_VERIFIED.get()}")
                        return@withContext Result.success(deviceId)
                    } else {
                        LogManager.w(LogTags.ADB_CONNECTION, AdbTexts.ADB_CONNECTION_VERIFY_FAILED.get())
                        runCatching { existingConnection.close() }
                        connectionPool.remove(deviceId)
                    }
                }
            }
            
            // 创建新的 ADB 连接
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_CREATING_NEW_CONNECTION.get())
            val dadb = try {
                dadb.Dadb.create(host, port, keyPair)
            } catch (e: java.net.ConnectException) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CONNECTION_REFUSED.get()}: ${e.message}")
                return@withContext Result.failure(Exception(AdbTexts.ADB_CONNECTION_REFUSED_DETAILS.get()))
            }
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_DADB_CREATED.get()}")
            
            // 验证新连接
            val verifyResult = AdbConnectionVerifier.verifyDadb(dadb)
            if (verifyResult.isFailure) {
                return@withContext verifyResult
            }
            
            // 创建连接对象（使用临时设备信息，后台异步获取完整信息）
            val tempDeviceInfo = DeviceInfo(
                deviceId = deviceId,
                name = deviceName ?: deviceId,
                model = "Unknown",
                manufacturer = "Unknown",
                androidVersion = "Unknown",
                serialNumber = "",
                connectionType = ConnectionType.TCP
            )
            
            val connection = AdbConnection(
                deviceId = deviceId, host = host,
                port = port, dadb = dadb,
                deviceInfo = tempDeviceInfo
            )
            
            // 加入连接池
            connectionPool[deviceId] = connection
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_ADDED_TO_POOL.get()}")
            
            // 后台异步获取完整设备信息（不阻塞连接流程）
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fullDeviceInfo = DeviceInfoProvider.getDeviceInfo(dadb, deviceId, deviceName, ConnectionType.TCP)
                    connection.deviceInfo = fullDeviceInfo
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${SessionTexts.LABEL_DEVICE_INFO.get()}: ${fullDeviceInfo.name} (${fullDeviceInfo.model})")
                } catch (e: Exception) {
                    LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_GET_DEVICE_INFO_FAILED.get()}: ${e.message}")
                }
            }
            
            // 更新连接设备列表
            updateConnectedDevices()
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${AdbTexts.ADB_CONNECTION_SUCCESS.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_DEVICE_ID.get()}: $deviceId")
            Result.success(deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "========== ${AdbTexts.ADB_CONNECTION_FAILED_TITLE.get()} ==========")
            LogManager.e(LogTags.ADB_CONNECTION, "${CommonTexts.ERROR_LABEL.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 连接 USB 设备
     * @param usbDevice USB 设备对象
     * @param deviceName 设备名称（可选）
     * @return 设备 ID（usb:serialNumber）
     */
    suspend fun connectUsbDevice(
        usbDevice: UsbDevice,
        deviceName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val serialNumber = ApiCompatHelper.getUsbDeviceSerialNumber(usbDevice) ?: usbDevice.deviceName
            val deviceId = "usb:$serialNumber"
            
            val keyPair = keyManager.getKeyPair()
                ?: return@withContext Result.failure(Exception(AdbTexts.ADB_KEYPAIR_NOT_INITIALIZED.get()))
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${AdbTexts.USB_CONNECTING_DEVICE.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.USB_SERIAL_NUMBER.get()}: $serialNumber")
            
            // 检查已有连接
            connectionPool[deviceId]?.let { existingConnection ->
                LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_VERIFYING_CONNECTION.get())
                val isValid = AdbConnectionVerifier.verifyConnection(existingConnection)
                if (isValid) {
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_CONNECTION_VERIFIED.get()}")
                    return@withContext Result.success(deviceId)
                } else {
                    LogManager.w(LogTags.ADB_CONNECTION, AdbTexts.ADB_CONNECTION_VERIFY_FAILED.get())
                    runCatching { existingConnection.close() }
                    connectionPool.remove(deviceId)
                }
            }
            
            // 请求 USB 权限
            val permissionResult = usbAdbManager.requestUsbPermission(usbDevice)
            if (permissionResult.isFailure) {
                return@withContext permissionResult.map { deviceId }
            }
            
            // 使用 USB ADB 通道直接连接
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_CREATING_NEW_CONNECTION.get())
            val dadb = try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
                UsbDadb(usbManager, usbDevice, keyPair, deviceId)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.USB_CONNECT_FAILED.get()}: ${e.message}")
                return@withContext Result.failure(Exception("${AdbTexts.USB_CONNECT_FAILED.get()}: ${e.message}"))
            }
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_DADB_CREATED.get()}")
            
            // 验证连接
            val verifyResult = AdbConnectionVerifier.verifyDadb(dadb)
            if (verifyResult.isFailure) {
                return@withContext verifyResult
            }
            
            // 创建连接对象
            val tempDeviceInfo = DeviceInfo(
                deviceId = deviceId,
                name = deviceName ?: (usbDevice.productName ?: serialNumber),
                model = "Unknown",
                manufacturer = usbDevice.manufacturerName ?: "Unknown",
                androidVersion = "Unknown",
                serialNumber = serialNumber,
                connectionType = ConnectionType.USB
            )
            
            val connection = AdbConnection(
                deviceId = deviceId,
                host = "usb",
                port = 0,
                dadb = dadb,
                deviceInfo = tempDeviceInfo
            )
            
            // 加入连接池
            connectionPool[deviceId] = connection
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_ADDED_TO_POOL.get()}")
            
            // 后台异步获取完整设备信息
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fullDeviceInfo = DeviceInfoProvider.getDeviceInfo(dadb, deviceId, deviceName, ConnectionType.USB)
                    connection.deviceInfo = fullDeviceInfo
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${SessionTexts.LABEL_DEVICE_INFO.get()}: ${fullDeviceInfo.name} (${fullDeviceInfo.model})")
                } catch (e: Exception) {
                    LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_GET_DEVICE_INFO_FAILED.get()}: ${e.message}")
                }
            }
            
            // 更新连接设备列表
            updateConnectedDevices()
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${AdbTexts.ADB_CONNECTION_SUCCESS.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_DEVICE_ID.get()}: $deviceId")
            Result.success(deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "========== ${AdbTexts.USB_CONNECT_FAILED.get()} ==========")
            LogManager.e(LogTags.ADB_CONNECTION, "${CommonTexts.ERROR_LABEL.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 扫描 USB 设备
     */
    suspend fun scanUsbDevices() = usbAdbManager.scanUsbDevices()
    
    /**
     * 请求 USB 权限
     */
    suspend fun requestUsbPermission(device: UsbDevice) = usbAdbManager.requestUsbPermission(device)
    
    /**
     * 获取 USB 设备列表
     */
    fun getUsbDevices() = usbAdbManager.usbDevices
    
    /**
     * 验证 ADB 连接是否可用
     */
    suspend fun verifyConnection(deviceId: String): Boolean {
        val connection = getConnection(deviceId) ?: return false
        return AdbConnectionVerifier.verifyConnection(connection)
    }
    
    /**
     * 断开设备连接
     */
    suspend fun disconnectDevice(deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val connection = connectionPool.remove(deviceId)
            if (connection != null) {
                connection.close()
                updateConnectedDevices()
                LogManager.d(LogTags.ADB_CONNECTION, "${CommonTexts.LABEL_DEVICE.get()} $deviceId ${AdbTexts.ADB_DEVICE_DISCONNECTED.get()}")
                Result.success(true)
            } else {
                Result.failure(Exception(AdbTexts.ADB_DEVICE_NOT_CONNECTED.get()))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_DISCONNECT_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取设备连接
     */
    fun getConnection(deviceId: String): AdbConnection? {
        return connectionPool[deviceId]
    }

    /**
     * 获取所有已连接设备
     */
    fun getAllConnections(): List<AdbConnection> {
        return connectionPool.values.toList()
    }

    /**
     * 检查设备是否已连接
     */
    fun isDeviceConnected(deviceId: String): Boolean {
        return connectionPool[deviceId]?.isConnected() ?: false
    }
    
    /**
     * 更新已连接设备列表
     */
    private fun updateConnectedDevices() {
        val devices = connectionPool.values.map { it.deviceInfo }
        _connectedDevices.value = devices
    }
    
    /**
     * 断开所有设备（应用退出时调用）
     */
    suspend fun disconnectAll() = withContext(Dispatchers.IO) {
        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_DISCONNECT_ALL.get())
        
        // 停止心跳任务
        keepAliveManager.stop()
        
        connectionPool.values.forEach { connection ->
            try {
                connection.close()
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CLOSE_CONNECTION_FAILED.get()}: ${e.message}", e)
            }
        }
        connectionPool.clear()
        updateConnectedDevices()
    }
    
    /**
     * 获取 ADB 密钥对（用于设备扫描等操作）
     */
    fun getKeyPair() = keyManager.getKeyPair()
    
    /**
     * 获取公钥（用于手动授权）
     */
    fun getPublicKey() = keyManager.getPublicKey()
}

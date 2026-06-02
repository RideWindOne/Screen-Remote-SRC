package com.screen.remote.android.feature.device.viewmodel

import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.connection.DeviceInfo
import com.screen.remote.android.infrastructure.adb.usb.UsbDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceViewModel(
    private val adbConnectionManager: AdbConnectionManager,
) : ViewModel() {
    // 已连接设备列表
    val connectedDevices: StateFlow<List<DeviceInfo>> =
        adbConnectionManager.connectedDevices

    // USB 设备列表
    val usbDevices: StateFlow<List<UsbDeviceInfo>> =
        adbConnectionManager.getUsbDevices()

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // USB 扫描状态
    private val _usbScanningState = MutableStateFlow(false)
    val usbScanningState: StateFlow<Boolean> = _usbScanningState.asStateFlow()

    /**
     * 连接设备（TCP/IP）
     */
    fun connectDevice(
        host: String,
        port: Int = 5555,
        deviceName: String? = null,
    ) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            val result = adbConnectionManager.connectDevice(host, port, deviceName)
            _connectionState.value =
                if (result.isSuccess) {
                    ConnectionState.Success(result.getOrNull() ?: "")
                } else {
                    ConnectionState.Error(result.exceptionOrNull()?.message ?: "连接失败")
                }
        }
    }

    /**
     * 连接 USB 设备
     */
    suspend fun connectUsbDevice(
        usbDevice: UsbDevice,
        deviceName: String? = null,
    ): Result<String> {
        _connectionState.value = ConnectionState.Connecting
        val result = adbConnectionManager.connectUsbDevice(usbDevice, deviceName)
        _connectionState.value =
            if (result.isSuccess) {
                ConnectionState.Success(result.getOrNull() ?: "")
            } else {
                ConnectionState.Error(result.exceptionOrNull()?.message ?: "USB 连接失败")
            }
        return result
    }

    /**
     * 扫描 USB 设备
     */
    suspend fun scanUsbDevices() {
        _usbScanningState.value = true
        try {
            adbConnectionManager.scanUsbDevices()
        } finally {
            // 确保无论成功或失败都重置扫描状态
            _usbScanningState.value = false
        }
    }

    /**
     * 断开设备
     */
    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            adbConnectionManager.disconnectDevice(deviceId)
        }
    }

    /**
     * 检查设备是否已连接
     */
    fun isDeviceConnected(deviceId: String): Boolean = adbConnectionManager.isDeviceConnected(deviceId)

    /**
     * 获取公钥
     */
    fun getPublicKey(): String? = adbConnectionManager.getPublicKey()

    /**
     * 重置连接状态
     */
    fun resetConnectionState() {
        _connectionState.value = ConnectionState.Idle
    }

    sealed class ConnectionState {
        object Idle : ConnectionState()

        object Connecting : ConnectionState()

        data class Success(
            val deviceId: String,
        ) : ConnectionState()

        data class Error(
            val message: String,
        ) : ConnectionState()
    }

    class Factory(
        private val adbConnectionManager: AdbConnectionManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
                return DeviceViewModel(adbConnectionManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

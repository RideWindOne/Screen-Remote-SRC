package com.screen.remote.android.feature.device.viewmodel

import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.connection.DeviceInfo
import com.screen.remote.android.infrastructure.adb.usb.UsbDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
                ConnectionState.Success
            } else {
                ConnectionState.Error(result.exceptionOrNull()?.message ?: "USB 连接失败")
            }
        return result
    }

    /**
     * 扫描 USB 设备
     */
    fun scanUsbDevices() {
        _usbScanningState.value = true
        try {
            adbConnectionManager.scanUsbDevices()
        } finally {
            // 确保无论成功或失败都重置扫描状态
            _usbScanningState.value = false
        }
    }

    sealed class ConnectionState {
        object Idle : ConnectionState()

        object Connecting : ConnectionState()

        data object Success : ConnectionState()

        data class Error(
            val message: String,
        ) : ConnectionState()
    }

}

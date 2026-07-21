package com.screen.remote.android.infrastructure.adb.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper
import com.screen.remote.android.core.common.util.compat.PendingIntentApiCompat
import com.screen.remote.android.core.i18n.AdbTexts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * USB ADB 管理器
 * 负责 USB 设备的扫描、权限请求和连接管理
 */
class UsbAdbManager(
    private val context: Context,
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionAction = "${context.packageName}.USB_PERMISSION"

    // USB 设备列表
    private val _usbDevices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val usbDevices: StateFlow<List<UsbDeviceInfo>> = _usbDevices.asStateFlow()

    private val deviceStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                    intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED
                ) {
                    scanUsbDevices()
                }
            }
        }

    init {
        ApiCompatHelper.registerReceiverCompat(
            context = context,
            receiver = deviceStateReceiver,
            filter =
                IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                },
            exported = false,
        )
        scanUsbDevices()
    }

    companion object {
        private const val PERMISSION_POLL_INTERVAL_MS = 250L
        private const val PERMISSION_TIMEOUT_MS = 15_000L
    }

    /**
     * 扫描 USB 设备
     * 查找所有连接的 ADB 设备
     */
    fun scanUsbDevices(): Result<List<UsbDeviceInfo>> =
        runCatching {
            LogManager.d(LogTags.USB_CONNECTION, AdbTexts.USB_SCANNING_DEVICES.english)

            val devices = mutableListOf<UsbDeviceInfo>()
            val deviceList = usbManager.deviceList

            LogManager.d(LogTags.USB_CONNECTION, "${AdbTexts.USB_FOUND_DEVICES.english}: ${deviceList.size}")

            for ((_, device) in deviceList) {
                try {
                    // 检查是否是 ADB 设备
                    if (isAdbDevice(device)) {
                        val hasPermission = usbManager.hasPermission(device)

                        // 尝试获取序列号，如果没有权限可能会失败
                        val serialNumber =
                            try {
                                ApiCompatHelper.getUsbDeviceSerialNumber(device) ?: ""
                            } catch (e: SecurityException) {
                                // 没有权限时无法获取序列号，使用设备名称作为标识
                                LogManager.w(
                                    LogTags.USB_CONNECTION,
                                    "${AdbTexts.USB_PERMISSION_DENIED.english}: ${device.deviceName}",
                                )
                                ""
                            }

                        val deviceInfo =
                            UsbDeviceInfo(
                                device = device,
                                deviceName = device.deviceName,
                                productName = device.productName ?: "Unknown",
                                manufacturerName = device.manufacturerName ?: "Unknown",
                                serialNumber = serialNumber,
                                hasPermission = hasPermission,
                            )
                        devices.add(deviceInfo)

                        LogManager.d(
                            LogTags.USB_CONNECTION,
                            "${AdbTexts.USB_DEVICE_FOUND.english}: ${deviceInfo.productName} " +
                                "(${AdbTexts.USB_PERMISSION.english}: $hasPermission)",
                        )
                    }
                } catch (e: SecurityException) {
                    // 单个设备权限错误不应该影响整个扫描
                    LogManager.w(
                        LogTags.USB_CONNECTION,
                        "${AdbTexts.USB_PERMISSION_DENIED.english}: ${device.deviceName} - ${e.message}",
                    )

                    // 即使没有权限，也添加到列表中，让用户知道有这个设备
                    try {
                        val deviceInfo =
                            UsbDeviceInfo(
                                device = device,
                                deviceName = device.deviceName,
                                productName = device.productName ?: "Unknown",
                                manufacturerName = device.manufacturerName ?: "Unknown",
                                serialNumber = "",
                                hasPermission = false,
                            )
                        devices.add(deviceInfo)
                    } catch (innerException: Exception) {
                        // 如果连基本信息都无法获取，跳过这个设备
                        LogManager.e(LogTags.USB_CONNECTION, "Failed to get device info: ${innerException.message}")
                    }
                } catch (e: Exception) {
                    // 其他异常也不应该影响整个扫描
                    LogManager.e(LogTags.USB_CONNECTION, "Error scanning device ${device.deviceName}: ${e.message}")
                }
            }

            _usbDevices.value = devices
            devices as List<UsbDeviceInfo>
        }.onFailure { e ->
            LogManager.e(LogTags.USB_CONNECTION, "${AdbTexts.USB_SCAN_FAILED.english}: ${e.message}", e)
        }

    /**
     * 检查设备是否是 ADB 设备
     * 参考 Easycontrol 的实现逻辑
     */
    private fun isAdbDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.ADB_CLASS &&
                usbInterface.interfaceSubclass == UsbConstants.ADB_SUBCLASS &&
                usbInterface.interfaceProtocol == UsbConstants.ADB_PROTOCOL
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 请求 USB 权限
     */
    suspend fun requestUsbPermission(device: UsbDevice): Result<Boolean> =
        suspendCancellableCoroutine { continuation ->
            // 检查是否已有权限
            if (usbManager.hasPermission(device)) {
                LogManager.d(LogTags.USB_CONNECTION, AdbTexts.USB_PERMISSION_ALREADY_GRANTED.english)
                continuation.resume(Result.success(true))
                return@suspendCancellableCoroutine
            }

            LogManager.d(LogTags.USB_CONNECTION, AdbTexts.USB_REQUESTING_PERMISSION.english)
            LogManager.d(
                LogTags.USB_CONNECTION,
                "USB permission request start: device=${device.deviceName}, action=$permissionAction",
            )

            val handler = Handler(Looper.getMainLooper())
            var finished = false

            fun finish(result: Result<Boolean>) {
                if (finished) {
                    return
                }
                finished = true
                handler.removeCallbacksAndMessages(null)
                continuation.resume(result)
            }

            // 创建权限请求接收器
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        LogManager.d(
                            LogTags.USB_CONNECTION,
                            "USB permission receiver onReceive: action=${intent.action}, device=${device.deviceName}",
                        )
                        if (permissionAction == intent.action) {
                            synchronized(this) {
                                val usbDevice =
                                    ApiCompatHelper.getParcelableExtraCompat(
                                        intent,
                                        UsbManager.EXTRA_DEVICE,
                                        UsbDevice::class.java,
                                    )

                                if (usbDevice != null && usbDevice.deviceName == device.deviceName) {
                                    val granted =
                                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ||
                                            usbManager.hasPermission(device)

                                    if (granted) {
                                        LogManager.d(LogTags.USB_CONNECTION, AdbTexts.USB_PERMISSION_GRANTED.english)
                                        finish(Result.success(true))
                                    } else {
                                        LogManager.w(LogTags.USB_CONNECTION, AdbTexts.USB_PERMISSION_DENIED.english)
                                        finish(Result.success(false))
                                    }

                                    // 注销接收器
                                    try {
                                        context.unregisterReceiver(this)
                                    } catch (e: Exception) {
                                        LogManager.e(LogTags.USB_CONNECTION, "Failed to unregister receiver", e)
                                    }
                                }
                            }
                        }
                    }
                }

            // 注册接收器
            val filter = IntentFilter(permissionAction)
            ApiCompatHelper.registerReceiverCompat(context, receiver, filter, exported = true)

            val pollPermissionState =
                object : Runnable {
                    private val startAt = System.currentTimeMillis()

                    override fun run() {
                        if (finished) {
                            return
                        }
                        if (usbManager.hasPermission(device)) {
                            LogManager.d(
                                LogTags.USB_CONNECTION,
                                "USB permission detected by polling: ${device.deviceName}",
                            )
                            try {
                                context.unregisterReceiver(receiver)
                            } catch (_: Exception) {
                            }
                            finish(Result.success(true))
                            return
                        }

                        val elapsed = System.currentTimeMillis() - startAt
                        if (elapsed >= PERMISSION_TIMEOUT_MS) {
                            LogManager.w(
                                LogTags.USB_CONNECTION,
                                "USB permission request timed out: ${device.deviceName}",
                            )
                            try {
                                context.unregisterReceiver(receiver)
                            } catch (_: Exception) {
                            }
                            finish(Result.failure(IllegalStateException("USB permission request timed out")))
                            return
                        }

                        handler.postDelayed(this, PERMISSION_POLL_INTERVAL_MS)
                    }
                }

            // 请求权限
            val permissionIntent =
                PendingIntentApiCompat.createUsbPermissionPendingIntent(
                    context,
                    permissionAction,
                )

            try {
                usbManager.requestPermission(device, permissionIntent)
                handler.postDelayed(pollPermissionState, PERMISSION_POLL_INTERVAL_MS)
            } catch (e: Exception) {
                LogManager.e(LogTags.USB_CONNECTION, AdbTexts.USB_PERMISSION_REQUEST_FAILED.english, e)
                try {
                    context.unregisterReceiver(receiver)
                } catch (ignored: Exception) {
                }
                finish(Result.failure(e))
            }

            // 取消时注销接收器
            continuation.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                try {
                    context.unregisterReceiver(receiver)
                } catch (ignored: Exception) {
                }
            }
        }
}

/**
 * USB 设备信息
 */
data class UsbDeviceInfo(
    val device: UsbDevice,
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val serialNumber: String,
    val hasPermission: Boolean,
) {
    /**
     * 获取显示名称
     * 避免制造商和产品名称重复（如 SAMSUNG SAMSUNG）
     */
    fun getDisplayName(): String =
        when {
            // 产品名称未知，使用设备名称
            productName == "Unknown" -> deviceName

            // 制造商未知，只显示产品名称
            manufacturerName == "Unknown" -> productName

            // 产品名称已包含制造商名称，只显示产品名称
            productName.contains(manufacturerName, ignoreCase = true) -> productName

            // 制造商和产品名称相同，只显示一个
            manufacturerName.equals(productName, ignoreCase = true) -> manufacturerName

            // 正常情况，拼接显示
            else -> "$manufacturerName $productName"
        }
}

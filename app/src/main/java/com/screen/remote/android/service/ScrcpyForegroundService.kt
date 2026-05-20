package com.screen.remote.android.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.compat.ServiceApiCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Scrcpy 前台服务（全局单例）
 *
 * Service 主类只保留生命周期、action 分发和设备保护列表管理。
 */
class ScrcpyForegroundService : Service() {
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private val protectedDevices = ConcurrentHashMap<String, ProtectedAdbDevice>()

    private val notificationController by lazy {
        ScrcpyServiceNotificationController(service = this)
    }
    private val heartbeatMonitor by lazy {
        ScrcpyServiceHeartbeatMonitor(
            applicationContext = applicationContext,
            protectedDevices = protectedDevices,
            onDevicesChanged = {
                if (protectedDevices.isEmpty()) {
                    stopForegroundService()
                } else {
                    updateNotification()
                }
            },
        )
    }

    companion object {
        const val ACTION_START = "com.screen.remote.android.START_SERVICE"
        const val ACTION_ADD_DEVICE = "com.screen.remote.android.ADD_DEVICE"
        const val ACTION_REMOVE_DEVICE = "com.screen.remote.android.REMOVE_DEVICE"
        const val ACTION_STOP = "com.screen.remote.android.STOP_SERVICE"

        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DELAYED_ACK = "delayed_ack"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_USB_CONNECTION = "usb_connection"

        fun protectDevice(
            context: android.content.Context,
            deviceId: String,
            deviceName: String,
            delayedAck: Boolean,
            host: String? = null,
            port: Int = 0,
            isUsbConnection: Boolean = false,
        ) {
            val intent =
                Intent(context, ScrcpyForegroundService::class.java).apply {
                    action = ACTION_ADD_DEVICE
                    putExtra(EXTRA_DEVICE_ID, deviceId)
                    putExtra(EXTRA_DEVICE_NAME, deviceName)
                    putExtra(EXTRA_DELAYED_ACK, delayedAck)
                    putExtra(EXTRA_HOST, host)
                    putExtra(EXTRA_PORT, port)
                    putExtra(EXTRA_USB_CONNECTION, isUsbConnection)
                }
            ServiceApiCompat.startForegroundServiceCompat(context, intent)
        }

        fun unprotectDevice(
            context: android.content.Context,
            deviceId: String,
        ) {
            val intent =
                Intent(context, ScrcpyForegroundService::class.java).apply {
                    action = ACTION_REMOVE_DEVICE
                    putExtra(EXTRA_DEVICE_ID, deviceId)
                }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScrcpyForegroundService = this@ScrcpyForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        notificationController.createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START, ACTION_ADD_DEVICE -> {
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "未知设备"
                val delayedAck = intent.getBooleanExtra(EXTRA_DELAYED_ACK, false)
                val host = intent.getStringExtra(EXTRA_HOST)
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                val isUsbConnection = intent.getBooleanExtra(EXTRA_USB_CONNECTION, false)
                if (deviceId != null) {
                    addDevice(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        delayedAck = delayedAck,
                        host = host,
                        port = port,
                        isUsbConnection = isUsbConnection,
                    )
                }
            }

            ACTION_REMOVE_DEVICE -> {
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                if (deviceId != null) {
                    removeDevice(deviceId)
                }
            }

            ACTION_STOP -> {
                stopForegroundService()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        LogManager.d(LogTags.SCRCPY_SERVICE, "服务销毁")
        heartbeatMonitor.stop()
        releaseWakeLock()
        protectedDevices.clear()
        isRunning = false
    }

    private fun addDevice(
        deviceId: String,
        deviceName: String,
        delayedAck: Boolean,
        host: String?,
        port: Int,
        isUsbConnection: Boolean,
    ) {
        protectedDevices[deviceId] =
            ProtectedAdbDevice(
                deviceName = deviceName,
                delayedAck = delayedAck,
                host = host,
                port = port,
                isUsbConnection = isUsbConnection,
            )
        LogManager.d(
            LogTags.SCRCPY_SERVICE,
            "添加保护设备: $deviceName ($deviceId) delayedAck=$delayedAck host=${host ?: "-"} port=$port usb=$isUsbConnection",
        )

        if (!isRunning) {
            try {
                startForegroundService()
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_SERVICE, "启动前台服务失败: ${e.message}", e)
            }
        } else {
            updateNotification()
        }
    }

    private fun removeDevice(deviceId: String) {
        val device = protectedDevices.remove(deviceId)
        LogManager.d(LogTags.SCRCPY_SERVICE, "移除保护设备: ${device?.deviceName} ($deviceId)")

        if (protectedDevices.isEmpty()) {
            LogManager.d(LogTags.SCRCPY_SERVICE, "无设备需要保护，停止服务")
            stopForegroundService()
        } else {
            updateNotification()
        }
    }

    private fun startForegroundService() {
        if (isRunning) {
            return
        }

        notificationController.startForeground(protectedDevices.values.toList())
        isRunning = true
        heartbeatMonitor.start()

        LogManager.d(LogTags.SCRCPY_SERVICE, "前台服务已启动，保护 ${protectedDevices.size} 个设备")
    }

    private fun stopForegroundService() {
        heartbeatMonitor.stop()
        notificationController.stopForeground()
        stopSelf()
        LogManager.d(LogTags.SCRCPY_SERVICE, "前台服务已停止")
    }

    private fun updateNotification() {
        if (!isRunning) {
            return
        }
        notificationController.updateNotification(protectedDevices.values.toList())
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock =
                powerManager
                    .newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "ScrcpyService::WakeLock",
                    ).apply {
                        acquire(10 * 60 * 60 * 1000L)
                    }
            LogManager.d(LogTags.SCRCPY_SERVICE, "WakeLock 已获取")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "获取 WakeLock 失败: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    LogManager.d(LogTags.SCRCPY_SERVICE, "WakeLock 已释放")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "释放 WakeLock 失败: ${e.message}", e)
        }
    }
}

internal data class ProtectedAdbDevice(
    val deviceName: String,
    val delayedAck: Boolean,
    val host: String?,
    val port: Int,
    val isUsbConnection: Boolean,
)

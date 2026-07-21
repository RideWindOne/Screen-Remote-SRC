package com.screen.remote.android.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private var isDestroyed = false
    private var lastStartId = 0
    private val protectedDevices = ConcurrentHashMap<String, ProtectedAdbDevice>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val notificationController by lazy {
        ScrcpyServiceNotificationController(service = this)
    }
    private val heartbeatMonitor by lazy {
        ScrcpyServiceHeartbeatMonitor(
            applicationContext = applicationContext,
            protectedDevices = protectedDevices,
            onDevicesChanged = {
                // 心跳运行在 Dispatchers.IO；Service 生命周期与前台状态只能在主线程串行处理。
                mainHandler.post {
                    if (!isDestroyed) {
                        reconcileProtectedDevices()
                    }
                }
            },
        )
    }

    companion object {
        const val ACTION_ADD_DEVICE = "com.screen.remote.android.ADD_DEVICE"
        const val ACTION_REMOVE_DEVICE = "com.screen.remote.android.REMOVE_DEVICE"
        const val ACTION_STOP = "com.screen.remote.android.STOP_SERVICE"

        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        fun protectDevice(
            context: android.content.Context,
            deviceId: String,
            deviceName: String,
        ) {
            require(deviceId.isNotBlank()) { "保护设备必须包含精确的 ADB 连接标识" }
            val intent =
                Intent(context, ScrcpyForegroundService::class.java).apply {
                    action = ACTION_ADD_DEVICE
                    putExtra(EXTRA_DEVICE_ID, deviceId)
                    putExtra(EXTRA_DEVICE_NAME, deviceName)
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
        isDestroyed = false
        notificationController.createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastStartId = maxOf(lastStartId, startId)

        when (intent?.action) {
            ACTION_ADD_DEVICE -> {
                // 每一次 startForegroundService(ACTION_ADD_DEVICE) 都有独立的前台化时限。
                // 即使旧 Service 正处于 stopSelf() 窗口，也必须无条件重新调用 startForeground()。
                if (!promoteToForeground(startId)) {
                    return START_NOT_STICKY
                }

                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "未知设备"
                if (!deviceId.isNullOrBlank()) {
                    addDevice(
                        deviceId = deviceId,
                        deviceName = deviceName,
                    )
                } else {
                    LogManager.e(LogTags.SCRCPY_SERVICE, "Ignore invalid device protection requests: deviceId=$deviceId")
                    reconcileProtectedDevices()
                }
            }

            ACTION_REMOVE_DEVICE -> {
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                if (deviceId != null) {
                    removeDevice(deviceId, startId)
                } else {
                    reconcileProtectedDevices()
                }
            }

            ACTION_STOP -> {
                protectedDevices.clear()
                stopForegroundService(startId)
            }

            else -> stopForegroundService(startId)
        }

        // 保护列表仅存在于当前进程；null intent 的粘性重启无法恢复设备，也可能再次漏掉前台化。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        LogManager.d(LogTags.SCRCPY_SERVICE, "Service destruction")
        heartbeatMonitor.destroy()
        releaseWakeLock()
        protectedDevices.clear()
        isRunning = false
        lastStartId = 0
    }

    private fun addDevice(
        deviceId: String,
        deviceName: String,
    ) {
        protectedDevices[deviceId] =
            ProtectedAdbDevice(
                deviceName = deviceName,
            )
        LogManager.d(
            LogTags.SCRCPY_SERVICE,
            "Add protection device: $deviceName ($deviceId)",
        )

        updateNotification()
    }

    private fun removeDevice(
        deviceId: String,
        startId: Int,
    ) {
        val device = protectedDevices.remove(deviceId)
        LogManager.d(LogTags.SCRCPY_SERVICE, "Remove protective device: ${device?.deviceName} ($deviceId)")

        if (protectedDevices.isEmpty()) {
            LogManager.d(LogTags.SCRCPY_SERVICE, "No equipment needs protection, stop service")
            stopForegroundService(startId)
        } else {
            updateNotification()
        }
    }

    private fun promoteToForeground(startId: Int): Boolean {
        val wasRunning = isRunning
        try {
            notificationController.startForeground(protectedDevices.values.toList())
        } catch (error: Exception) {
            isRunning = false
            heartbeatMonitor.stop()
            stopSelfResult(startId)
            LogManager.e(LogTags.SCRCPY_SERVICE, "Failed to start foreground service: ${error.message}", error)
            return false
        }

        isRunning = true
        if (!wasRunning) {
            heartbeatMonitor.start()
        }

        LogManager.d(LogTags.SCRCPY_SERVICE, "The foreground service has been started, protecting ${protectedDevices.size} devices")
        return true
    }

    private fun stopForegroundService(startId: Int = lastStartId) {
        val stopped = if (startId > 0) stopSelfResult(startId) else run {
            stopSelf()
            true
        }
        if (!stopped) {
            // 已存在更新的 start 请求；它仍依赖当前前台状态，不能被旧请求提前降级。
            LogManager.d(LogTags.SCRCPY_SERVICE, "Ignore expired stop requests: startId=$startId latest=$lastStartId")
            return
        }

        // stopSelfResult 已确认没有更新的 start 请求，立即清状态，避免后续 ADD 看到旧值。
        isRunning = false
        heartbeatMonitor.stop()
        notificationController.stopForeground()
        LogManager.d(LogTags.SCRCPY_SERVICE, "The front desk service has stopped: startId=$startId stopped=$stopped")
    }

    private fun updateNotification() {
        if (!isRunning) {
            return
        }
        notificationController.updateNotification(protectedDevices.values.toList())
    }

    private fun reconcileProtectedDevices() {
        if (protectedDevices.isEmpty()) {
            stopForegroundService(lastStartId)
        } else if (isRunning) {
            updateNotification()
        } else {
            if (promoteToForeground(lastStartId)) {
                updateNotification()
            }
        }
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
            LogManager.d(LogTags.SCRCPY_SERVICE, "WakeLock acquired")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "Failed to get WakeLock: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    LogManager.d(LogTags.SCRCPY_SERVICE, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "Failed to release WakeLock: ${e.message}", e)
        }
    }
}

// 使用引用身份区分每一次保护请求，防止旧心跳结果删除刚刚以相同参数重新添加的设备。
internal class ProtectedAdbDevice(
    val deviceName: String,
)

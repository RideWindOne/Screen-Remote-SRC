package com.mobile.scrcpy.android.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mobile.scrcpy.android.app.MainActivity
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Scrcpy 前台服务（全局单例）
 * 
 * 核心功能：
 * 1. ADB 连接保活：保持设备连接，防止断开
 * 2. 前台 Service：确保触感反馈等功能在后台稳定运行
 * 
 * 生命周期：
 * 1. 首次连接设备时启动前台服务
 * 2. 后续连接设备只添加到保护列表
 * 3. 断开 scrcpy 时保留 ADB 连接
 * 4. 关闭应用时服务自动销毁
 */
class ScrcpyForegroundService : Service() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null
    
    // 需要保护的设备列表：deviceId -> deviceName
    private val protectedDevices = ConcurrentHashMap<String, String>()
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "scrcpy_service"
        private const val CHANNEL_NAME = "Scrcpy 服务"
        
        const val ACTION_START = "com.mobile.scrcpy.android.START_SERVICE"
        const val ACTION_ADD_DEVICE = "com.mobile.scrcpy.android.ADD_DEVICE"
        const val ACTION_REMOVE_DEVICE = "com.mobile.scrcpy.android.REMOVE_DEVICE"
        const val ACTION_STOP = "com.mobile.scrcpy.android.STOP_SERVICE"
        
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        
        private const val HEARTBEAT_INTERVAL = 15_000L // 15秒心跳
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): ScrcpyForegroundService = this@ScrcpyForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        LogManager.d(LogTags.SCRCPY_SERVICE, "服务创建")
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_ADD_DEVICE -> {
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "未知设备"
                if (deviceId != null) {
                    addDevice(deviceId, deviceName)
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
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogManager.d(LogTags.SCRCPY_SERVICE, "服务销毁")
        stopHeartbeat()
        releaseWakeLock()
        protectedDevices.clear()
        isRunning = false
    }
    
    /**
     * 添加设备到保护列表
     */
    private fun addDevice(deviceId: String, deviceName: String) {
        protectedDevices[deviceId] = deviceName
        LogManager.d(LogTags.SCRCPY_SERVICE, "添加保护设备: $deviceName ($deviceId)")
        
        // 首次添加设备时启动前台服务
        if (!isRunning) {
            try {
                startForegroundService()
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_SERVICE, "启动前台服务失败: ${e.message}", e)
                // 即使前台服务启动失败，也保留设备在列表中
                // 心跳检测会在后台继续工作
            }
        } else {
            updateNotification()
        }
    }
    
    /**
     * 从保护列表移除设备
     */
    private fun removeDevice(deviceId: String) {
        val deviceName = protectedDevices.remove(deviceId)
        LogManager.d(LogTags.SCRCPY_SERVICE, "移除保护设备: $deviceName ($deviceId)")
        
        // 如果没有设备需要保护，停止服务
        if (protectedDevices.isEmpty()) {
            LogManager.d(LogTags.SCRCPY_SERVICE, "无设备需要保护，停止服务")
            stopForegroundService()
        } else {
            updateNotification()
        }
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        if (isRunning) return
        
        val notification = createNotification()
        
        // 使用 ApiCompatHelper 统一处理前台服务启动（Android 14+ 需要指定服务类型）
        ApiCompatHelper.startForegroundCompat(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = notification,
        )
        
        isRunning = true
        
        startHeartbeat()
        
        LogManager.d(LogTags.SCRCPY_SERVICE, "前台服务已启动，保护 ${protectedDevices.size} 个设备")
    }
    
    /**
     * 停止前台服务
     */
    private fun stopForegroundService() {
        stopHeartbeat()

        ApiCompatHelper.stopForegroundCompat(this, removeNotification = true)
        stopSelf()
        LogManager.d(LogTags.SCRCPY_SERVICE, "前台服务已停止")
    }
    
    /**
     * 启动 ADB 心跳检测
     */
    private fun startHeartbeat() {
        stopHeartbeat()
        
        heartbeatJob = serviceScope.launch {
            LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 心跳检测已启动（间隔: ${HEARTBEAT_INTERVAL}ms）")
            
            while (isActive) {
                delay(HEARTBEAT_INTERVAL)
                
                if (protectedDevices.isEmpty()) continue
                
                val adbManager = AdbConnectionManager.getInstance(applicationContext)
                val devicesToRemove = mutableListOf<String>()
                
                protectedDevices.keys.forEach { deviceId ->
                    try {
                        val connection = adbManager.getConnection(deviceId)
                        
                        if (connection == null) {
                            LogManager.w(LogTags.SCRCPY_SERVICE, "ADB 连接不存在: $deviceId，标记移除")
                            devicesToRemove.add(deviceId)
                            return@forEach
                        }
                        
                        // 执行轻量级命令保持连接
                        val result = connection.executeShell("echo 1", retryOnFailure = false)
                        if (result.isSuccess) {
                            LogManager.d(LogTags.SCRCPY_SERVICE, "ADB 心跳正常: $deviceId")
                        } else {
                            LogManager.w(LogTags.SCRCPY_SERVICE, "ADB 心跳失败: $deviceId，尝试重连")
                            
                            // 尝试重连
                            val reconnectResult = tryReconnect(deviceId, adbManager)
                            if (!reconnectResult) {
                                LogManager.e(LogTags.SCRCPY_SERVICE, "ADB 重连失败: $deviceId，标记移除")
                                devicesToRemove.add(deviceId)
                            }
                        }
                    } catch (e: Exception) {
                        LogManager.e(LogTags.SCRCPY_SERVICE, "ADB 心跳异常 $deviceId: ${e.message}，尝试重连")
                        
                        // 尝试重连
                        val reconnectResult = tryReconnect(deviceId, adbManager)
                        if (!reconnectResult) {
                            LogManager.e(LogTags.SCRCPY_SERVICE, "ADB 重连失败: $deviceId，标记移除")
                            devicesToRemove.add(deviceId)
                        }
                    }
                }
                
                // 移除失败的设备
                devicesToRemove.forEach { deviceId ->
                    val deviceName = protectedDevices.remove(deviceId)
                    LogManager.d(LogTags.SCRCPY_SERVICE, "已移除失败设备: $deviceName ($deviceId)")
                    
                    // 断开 ADB 连接
                    try {
                        adbManager.disconnectDevice(deviceId)
                    } catch (e: Exception) {
                        LogManager.w(LogTags.SCRCPY_SERVICE, "断开 ADB 连接失败: ${e.message}")
                    }
                }
                
                // 如果没有设备需要保护，停止服务
                if (protectedDevices.isEmpty() && devicesToRemove.isNotEmpty()) {
                    LogManager.d(LogTags.SCRCPY_SERVICE, "所有设备已移除，停止服务")
                    stopForegroundService()
                } else if (devicesToRemove.isNotEmpty()) {
                    updateNotification()
                }
            }
        }
    }
    
    /**
     * 尝试重连 ADB
     */
    private suspend fun tryReconnect(deviceId: String, adbManager: AdbConnectionManager): Boolean {
        return try {
            LogManager.d(LogTags.SCRCPY_SERVICE, "开始重连 ADB: $deviceId")
            
            // 解析 host:port
            val parts = deviceId.split(":")
            if (parts.size != 2) {
                LogManager.e(LogTags.SCRCPY_SERVICE, "设备 ID 格式错误: $deviceId")
                return false
            }
            
            val host = parts[0]
            val port = parts[1].toIntOrNull() ?: 5555
            
            // 尝试重连
            val result = adbManager.connectDevice(host, port, forceReconnect = true)
            
            if (result.isSuccess) {
                LogManager.d(LogTags.SCRCPY_SERVICE, "✓ ADB 重连成功: $deviceId")
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
    
    /**
     * 停止心跳检测
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    /**
     * 更新通知内容
     */
    private fun updateNotification() {
        if (!isRunning) return
        
        val notification = createNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        ApiCompatHelper.createNotificationChannelCompat(
            context = this,
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            importance = 2, // NotificationManager.IMPORTANCE_LOW,
            description = "保持 ADB 连接活跃，管理悬浮球",
            showBadge = false
        )
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            ApiCompatHelper.getPendingIntentFlags(mutable = false)
        )
        
        val deviceCount = protectedDevices.size
        val contentText = when {
            deviceCount == 1 -> "保持与 ${protectedDevices.values.first()} 的连接"
            deviceCount > 1 -> "保持与 $deviceCount 个设备的连接"
            else -> "镜像服务运行中"
        }
        
        return ApiCompatHelper.createNotificationBuilder(this, CHANNEL_ID)
            .setContentTitle("Scrcpy 镜像")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * 获取 WakeLock - 防止 CPU 休眠
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ScrcpyService::WakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10小时超时
            }
            LogManager.d(LogTags.SCRCPY_SERVICE, "WakeLock 已获取")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVICE, "获取 WakeLock 失败: ${e.message}", e)
        }
    }
    
    /**
     * 释放 WakeLock
     */
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

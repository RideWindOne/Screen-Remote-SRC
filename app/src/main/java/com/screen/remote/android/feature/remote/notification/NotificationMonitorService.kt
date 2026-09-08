package com.screen.remote.android.feature.remote.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 通知监控前台服务
 * 用于保持应用在后台持续运行，接收被控端通知
 * 使用前台服务提高应用优先级，防止被系统杀死
 */
class NotificationMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "notification_monitor_channel"
        private const val CHANNEL_NAME = "通知监控"
        private const val FOREGROUND_NOTIFICATION_ID = 772373

        private const val EXTRA_DEVICE_NAME = "device_name"
        private const val ACTION_START = "start_monitor"
        private const val ACTION_STOP = "stop_monitor"

        /**
         * 启动通知监控前台服务
         */
        fun start(context: Context, deviceName: String) {
            val intent = Intent(context, NotificationMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止通知监控前台服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, NotificationMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "设备"
                startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(deviceName))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    /**
     * 更新前台服务通知内容
     */
    fun updateNotification(deviceName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(deviceName))
    }

    /**
     * 构建前台服务通知
     */
    private fun buildNotification(deviceName: String): Notification {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("通知监控运行中")
            .setContentText("正在监控 $deviceName 的通知")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * 确保通知渠道存在
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "被控端通知监控提醒"
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}

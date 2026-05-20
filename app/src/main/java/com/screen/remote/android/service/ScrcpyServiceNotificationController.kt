package com.screen.remote.android.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.screen.remote.android.app.MainActivity
import com.screen.remote.android.core.common.util.compat.NotificationApiCompat
import com.screen.remote.android.core.common.util.compat.PendingIntentApiCompat
import com.screen.remote.android.core.common.util.compat.ServiceApiCompat

internal class ScrcpyServiceNotificationController(
    private val service: Service,
) {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "scrcpy_service"
        private const val CHANNEL_NAME = "Scrcpy 服务"
    }

    fun createNotificationChannel() {
        NotificationApiCompat.createNotificationChannelCompat(
            context = service,
            channelId = CHANNEL_ID,
            channelName = CHANNEL_NAME,
            importance = 2,
            description = "保持 ADB 连接活跃，管理悬浮球",
            showBadge = false,
        )
    }

    fun startForeground(protectedDevices: List<ProtectedAdbDevice>) {
        ServiceApiCompat.startForegroundCompat(
            service = service,
            notificationId = NOTIFICATION_ID,
            notification = createNotification(protectedDevices),
        )
    }

    fun stopForeground() {
        ServiceApiCompat.stopForegroundCompat(service, removeNotification = true)
    }

    fun updateNotification(protectedDevices: List<ProtectedAdbDevice>) {
        val notificationManager = service.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(protectedDevices))
    }

    private fun createNotification(protectedDevices: List<ProtectedAdbDevice>): Notification {
        val intent =
            Intent(service, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                service,
                0,
                intent,
                PendingIntentApiCompat.getPendingIntentFlags(mutable = false),
            )

        val protectedDeviceNames = protectedDevices.map { it.deviceName }
        val deviceCount = protectedDeviceNames.size
        val contentText =
            when {
                deviceCount == 1 -> "保持与 ${protectedDeviceNames.first()} 的连接"
                deviceCount > 1 -> "保持与 $deviceCount 个设备的连接"
                else -> "镜像服务运行中"
            }

        return NotificationApiCompat
            .createNotificationBuilder(service, CHANNEL_ID)
            .setContentTitle("Scrcpy 镜像")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

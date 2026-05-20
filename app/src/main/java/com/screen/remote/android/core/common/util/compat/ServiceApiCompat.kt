package com.screen.remote.android.core.common.util.compat

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build

internal object ServiceApiCompat {
    fun startForegroundServiceCompat(
        context: Context,
        intent: Intent,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startForegroundCompat(
        service: Service,
        notificationId: Int,
        notification: Notification,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            service.startForeground(notificationId, notification)
        }
    }

    fun stopForegroundCompat(
        service: Service,
        removeNotification: Boolean = true,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags =
                if (removeNotification) {
                    Service.STOP_FOREGROUND_REMOVE
                } else {
                    Service.STOP_FOREGROUND_DETACH
                }
            service.stopForeground(flags)
        } else {
            @Suppress("DEPRECATION")
            service.stopForeground(removeNotification)
        }
    }
}

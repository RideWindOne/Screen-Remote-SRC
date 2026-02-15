package com.mobile.scrcpy.android.core.common.util.compat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

internal object NotificationApiCompat {
    fun createNotificationChannelCompat(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
        description: String? = null,
        showBadge: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel =
            NotificationChannel(
                channelId,
                channelName,
                importance,
            ).apply {
                description?.let { this.description = it }
                setShowBadge(showBadge)
            }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.createNotificationChannel(channel)
    }

    fun createNotificationBuilder(
        context: Context,
        channelId: String,
    ): NotificationCompat.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            NotificationCompat.Builder(context)
        }
}

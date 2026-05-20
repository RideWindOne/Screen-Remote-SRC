package com.screen.remote.android.core.common.util.compat

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

internal object PendingIntentApiCompat {
    fun getPendingIntentFlags(mutable: Boolean = false): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (mutable) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    fun createUsbPermissionPendingIntent(
        context: Context,
        action: String,
    ): PendingIntent {
        val intent =
            Intent(action).apply {
                setPackage(context.packageName)
            }

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            getPendingIntentFlags(mutable = true),
        )
    }
}

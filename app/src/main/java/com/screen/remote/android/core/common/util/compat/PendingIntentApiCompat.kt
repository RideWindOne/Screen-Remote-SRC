package com.screen.remote.android.core.common.util.compat

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

internal object PendingIntentApiCompat {
    fun getPendingIntentFlags(mutable: Boolean = false): Int {
        val mutabilityFlag =
            when {
                !mutable -> PendingIntent.FLAG_IMMUTABLE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
                else -> 0
            }

        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
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

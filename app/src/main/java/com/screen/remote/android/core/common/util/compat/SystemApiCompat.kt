package com.screen.remote.android.core.common.util.compat

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.provider.Settings

fun getDownloadManagerCompat(context: Context): DownloadManager =
    requireNotNull(context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager) {
        "DownloadManager service is unavailable"
    }

fun getSecurityPatchCompat(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Build.VERSION.SECURITY_PATCH
    } else {
        ""
    }

fun canDrawOverlaysCompat(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }

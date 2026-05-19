package com.mobile.scrcpy.android.feature.remote.widget.floating

import com.mobile.scrcpy.android.core.common.manager.LogDetailCategory
import com.mobile.scrcpy.android.core.common.manager.LogManager

internal object FloatingDebugLog {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.MANAGEMENT)) {
            LogManager.d(tag, message())
        }
    }

    fun d(
        tag: String,
        message: String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.MANAGEMENT)) {
            LogManager.d(tag, message)
        }
    }
}

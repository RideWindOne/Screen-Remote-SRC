package com.mobile.scrcpy.android.core.common.manager

object ManagementDebugLog {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.MANAGEMENT)) {
            LogManager.d(tag, message())
        }
    }
}

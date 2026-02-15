package com.mobile.scrcpy.android.core.common.manager

object ControlDebugLog {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.CONTROL_STREAM)) {
            LogManager.d(tag, message())
        }
    }
}

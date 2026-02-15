package com.mobile.scrcpy.android.core.common.manager

object ShellDebugLog {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.SHELL_STREAM)) {
            LogManager.d(tag, message())
        }
    }
}

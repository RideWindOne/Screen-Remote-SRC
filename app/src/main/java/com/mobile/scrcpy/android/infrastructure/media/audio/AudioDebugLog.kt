package com.mobile.scrcpy.android.infrastructure.media.audio

import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.LogDetailCategory

internal object AudioDebugLog {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.AUDIO_STREAM)) {
            LogManager.d(tag, message())
        }
    }
}

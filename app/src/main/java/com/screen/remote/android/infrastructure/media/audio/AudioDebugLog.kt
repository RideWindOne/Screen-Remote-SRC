package com.screen.remote.android.infrastructure.media.audio

import com.screen.remote.android.core.common.manager.LogDetailCategory
import com.screen.remote.android.core.common.manager.LogManager

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

package com.screen.remote.android.infrastructure.media.video

import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.LogDetailCategory

internal object VideoDebugLog {
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (LogManager.isDetailLoggingEnabled(LogDetailCategory.VIDEO_STREAM)) {
            LogManager.d(tag, message())
        }
    }
}

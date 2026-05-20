package com.screen.remote.android.infrastructure.media.video

import android.media.MediaFormat
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.util.ApiCompatHelper

internal class VideoOutputFormatReporter {
    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)? = null

    fun updateVideoSizeFromOutputFormat(outputFormat: MediaFormat) {
        try {
            val cropRect = ApiCompatHelper.getCropRectIfSupported(outputFormat)
            val realWidth: Int
            val realHeight: Int

            if (cropRect != null) {
                realWidth = cropRect.right - cropRect.left + 1
                realHeight = cropRect.bottom - cropRect.top + 1
            } else {
                realWidth = outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                realHeight = outputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            }

            VideoDebugLog.d(LogTags.VIDEO_DECODER) { "视频尺寸: ${realWidth}x$realHeight" }

            val rotation = if (realWidth > realHeight) 90 else 0
            onVideoSizeChanged?.invoke(realWidth, realHeight, rotation)
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "获取输出格式失败: ${e.message}")
        }
    }
}

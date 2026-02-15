package com.mobile.scrcpy.android.infrastructure.media.video

internal class VideoDecoderConfigurationException(
    codecLabel: String,
    reason: String,
    cause: Throwable? = null,
) : IllegalStateException("$codecLabel 配置失败: $reason", cause)

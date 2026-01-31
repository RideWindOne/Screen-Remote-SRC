package com.mobile.scrcpy.android.core.common.event

/**
 * 视频/音频事件
 */

/**
 * 新视频帧
 */
data class NewFrame(
    val frameData: ByteArray,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "新视频帧: ${frameData.size} bytes"

    override fun needsSampling() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NewFrame
        return frameData.contentEquals(other.frameData)
    }

    override fun hashCode(): Int = frameData.contentHashCode()
}

/**
 * 屏幕初始尺寸
 */
data class ScreenInitSize(
    val width: Int,
    val height: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "屏幕尺寸: ${width}x$height"
}

/**
 * 视频帧解码
 */
data class VideoFrameDecoded(
    val deviceId: String,
    val width: Int,
    val height: Int,
    val pts: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 视频帧解码: ${width}x$height pts=$pts"

    override fun needsSampling() = true
}

/**
 * 音频帧解码
 */
data class AudioFrameDecoded(
    val deviceId: String,
    val sampleRate: Int,
    val channels: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 音频帧解码: ${sampleRate}Hz ${channels}ch"

    override fun needsSampling() = true
}

/**
 * 视频解码器停滞
 */
data class VideoDecoderStalled(
    val deviceId: String,
    val reason: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 视频解码器停滞: $reason"
}

/**
 * 音频解码器停滞
 */
data class AudioDecoderStalled(
    val deviceId: String,
    val reason: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MONITOR

    override fun getDescription() = "[$deviceId] 音频解码器停滞: $reason"
}

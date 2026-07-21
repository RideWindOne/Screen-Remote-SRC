package com.screen.remote.android.core.common.event

import com.screen.remote.android.core.domain.model.ScrcpyErrorEvent
import com.screen.remote.android.core.domain.model.ScrcpyStatusEvent

object Quit : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "Application exited"
}

object ServerConnected : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "Server connected"
}

object ServerConnectionFailed : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "Server connection failed"
}

object DeviceDisconnected : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "Device disconnected"
}

object UsbDeviceDisconnected : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "USB device disconnected"
}

data class ConnectionEstablished(
    val deviceId: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "[$deviceId] Connection established"
}

data class ConnectionLost(
    val deviceId: String,
    val reason: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "[$deviceId] Connection lost: $reason"
}

data class StatusChanged(
    val event: ScrcpyStatusEvent,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "Status changed: ${event.status}"
}

data class ScrcpyError(
    val event: ScrcpyErrorEvent,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Error: ${event.errorMessage}"
}

data class KeyDown(
    val scancode: Int,
    val keycode: Int,
    val keymod: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "Key pressed: keycode=$keycode"
}

data class KeyUp(
    val scancode: Int,
    val keycode: Int,
    val keymod: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "Key released: keycode=$keycode"
}

data class TouchDown(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "Touch down: pointer=$pointerId ($x, $y)"
}

data class TouchMove(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.UI

    override fun getDescription() = "Touch move: pointer=$pointerId ($x, $y)"

    override fun needsSampling() = true
}

data class TouchUp(
    val pointerId: Int,
    val x: Float,
    val y: Float,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "Touch up: pointer=$pointerId ($x, $y)"
}

data class Scroll(
    val x: Float,
    val y: Float,
    val hScroll: Float,
    val vScroll: Float,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.DEBUG

    override fun getCategory() = Category.UI

    override fun getDescription() = "Scroll: ($x, $y) h=$hScroll v=$vScroll"
}

data class ClipboardUpdate(
    val content: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.UI

    override fun getDescription() = "Clipboard updated: ${content.take(20)}..."
}

data class NewFrame(
    val frameData: ByteArray,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "New video frame: ${frameData.size} bytes"

    override fun needsSampling() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NewFrame
        return frameData.contentEquals(other.frameData)
    }

    override fun hashCode(): Int = frameData.contentHashCode()
}

data class ScreenInitSize(
    val width: Int,
    val height: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "Screen size: ${width}x$height"
}

data class VideoFrameDecoded(
    val deviceId: String,
    val width: Int,
    val height: Int,
    val pts: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "[$deviceId] Video frame decoded: ${width}x$height pts=$pts"

    override fun needsSampling() = true
}

data class AudioFrameDecoded(
    val deviceId: String,
    val sampleRate: Int,
    val channels: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "[$deviceId] Audio frame decoded: ${sampleRate}Hz ${channels}ch"

    override fun needsSampling() = true
}

data class VideoDecoderStalled(
    val deviceId: String,
    val reason: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "[$deviceId] Video decoder stalled: $reason"
}

data class AudioDecoderStalled(
    val deviceId: String,
    val reason: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.WARN

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "[$deviceId] Audio decoder stalled: $reason"
}

data class DemuxerError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Demuxer error: $message"
}

data class RecorderError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Recorder error: $message"
}

data class AoaOpenError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "AOA open error: $message"
}

data class TimeLimitReached(
    val duration: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Time limit reached: ${duration}ms"
}

data class RunOnMainThread(
    val task: () -> Unit,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Main-thread task executed"
}

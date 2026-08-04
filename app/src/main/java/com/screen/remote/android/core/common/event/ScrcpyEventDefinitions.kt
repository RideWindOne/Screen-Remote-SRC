package com.screen.remote.android.core.common.event

import com.screen.remote.android.core.domain.model.ScrcpyErrorEvent
import com.screen.remote.android.core.domain.model.ScrcpyStatusEvent

object Quit : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.LIFECYCLE

    override fun getDescription() = "Application exited"
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

data class ScreenInitSize(
    val width: Int,
    val height: Int,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.MEDIA

    override fun getDescription() = "Screen size: ${width}x$height"
}

data class DemuxerError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Demuxer error: $message"
}

data class RunOnMainThread(
    val task: () -> Unit,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Main-thread task executed"
}

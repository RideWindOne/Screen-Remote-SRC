package com.mobile.scrcpy.android.core.common.event

/**
 * 系统事件
 */

/**
 * Demuxer 错误
 */
data class DemuxerError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Demuxer 错误: $message"
}

/**
 * Recorder 错误
 */
data class RecorderError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Recorder 错误: $message"
}

/**
 * Controller 错误
 */
data class ControllerError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "Controller 错误: $message"
}

/**
 * AOA 打开错误
 */
data class AoaOpenError(
    val message: String,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.ERROR

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "AOA 打开错误: $message"
}

/**
 * 时间限制到达
 */
data class TimeLimitReached(
    val duration: Long,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.INFO

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "时间限制到达: ${duration}ms"
}

/**
 * 主线程任务执行
 */
data class RunOnMainThread(
    val task: () -> Unit,
) : ScrcpyEvent() {
    override fun getLogLevel() = LogLevel.VERBOSE

    override fun getCategory() = Category.SYSTEM

    override fun getDescription() = "主线程任务执行"
}

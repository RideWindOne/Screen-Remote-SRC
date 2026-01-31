package com.mobile.scrcpy.android.core.common.event

/**
 * Scrcpy 事件基类
 */
sealed class ScrcpyEvent {
    /**
     * 事件日志级别
     */
    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    /**
     * 事件类别
     */
    enum class Category {
        UI, // UI 事件
        MONITOR, // 监控事件
        LIFECYCLE, // 生命周期事件
        SYSTEM, // 系统事件
    }

    /**
     * 获取事件的日志级别
     */
    open fun getLogLevel(): LogLevel = LogLevel.DEBUG

    /**
     * 获取事件类别
     */
    open fun getCategory(): Category = Category.SYSTEM

    /**
     * 获取事件描述（用于日志输出）
     */
    open fun getDescription(): String = this::class.simpleName ?: "Unknown"

    /**
     * 是否需要采样输出（高频事件）
     */
    open fun needsSampling(): Boolean = false
}

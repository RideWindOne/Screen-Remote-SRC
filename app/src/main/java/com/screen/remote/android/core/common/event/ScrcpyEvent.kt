package com.screen.remote.android.core.common.event

sealed class ScrcpyEvent {
    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    enum class Category {
        UI,
        MONITOR,
        LIFECYCLE,
        SYSTEM,
        MEDIA,
    }

    open fun getLogLevel(): LogLevel = LogLevel.DEBUG

    open fun getCategory(): Category = Category.SYSTEM

    open fun getDescription(): String = this::class.simpleName ?: "Unknown"

    open fun needsSampling(): Boolean = false
}

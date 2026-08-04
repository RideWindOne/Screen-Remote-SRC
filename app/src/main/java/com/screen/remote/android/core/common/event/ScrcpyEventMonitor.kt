package com.screen.remote.android.core.common.event

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object ScrcpyEventLogger {
    private val eventCounters = ConcurrentHashMap<String, AtomicLong>()
    private val eventStats = ConcurrentHashMap<String, EventStats>()

    private const val SAMPLING_INTERVAL = 100L

    private var minLogLevel = ScrcpyEvent.LogLevel.DEBUG
    private var enableStats = true
    private var enableVerbose = false

    data class EventStats(
        var totalCount: Long = 0,
        var loggedCount: Long = 0,
        var sampledCount: Long = 0,
    )

    fun logEvent(event: ScrcpyEvent) {
        val eventClass = event::class.simpleName ?: "Unknown"
        val logLevel = event.getLogLevel()
        val category = event.getCategory()

        if (enableStats) {
            updateStats(eventClass)
        }

        if (!shouldLog(logLevel)) {
            return
        }

        if (event.needsSampling()) {
            if (!shouldSample(eventClass)) {
                return
            }
        }

        val logMessage = formatLogMessage(event, category, eventClass)
        outputLog(logLevel, category, logMessage)
    }

    private fun shouldLog(level: ScrcpyEvent.LogLevel): Boolean {
        if (level == ScrcpyEvent.LogLevel.VERBOSE && !enableVerbose) {
            return false
        }

        return level.ordinal >= minLogLevel.ordinal
    }

    private fun shouldSample(eventClass: String): Boolean {
        val counter = eventCounters.getOrPut(eventClass) { AtomicLong(0) }
        val count = counter.incrementAndGet()
        return count % SAMPLING_INTERVAL == 0L
    }

    private fun updateStats(eventClass: String) {
        val stats = eventStats.getOrPut(eventClass) { EventStats() }
        synchronized(stats) {
            stats.totalCount++
        }
    }

    private fun formatLogMessage(
        event: ScrcpyEvent,
        category: ScrcpyEvent.Category,
        eventClass: String,
    ): String {
        val description = event.getDescription()
        val categoryIcon = getCategoryIcon(category)
        val baseMessage = "$categoryIcon [$category] $description"

        if (event.needsSampling()) {
            val counter = eventCounters[eventClass]
            val count = counter?.get() ?: 0
            return "$baseMessage (cumulative: $count)"
        }

        return baseMessage
    }

    private fun getCategoryIcon(category: ScrcpyEvent.Category): String =
        when (category) {
            ScrcpyEvent.Category.UI -> "👆"
            ScrcpyEvent.Category.MONITOR -> "📊"
            ScrcpyEvent.Category.LIFECYCLE -> "🔄"
            ScrcpyEvent.Category.SYSTEM -> "⚙️"
            ScrcpyEvent.Category.MEDIA -> "🎬"
        }

    private fun outputLog(
        level: ScrcpyEvent.LogLevel,
        category: ScrcpyEvent.Category,
        message: String,
    ) {
        val tag = getLogTag(category)

        when (level) {
            ScrcpyEvent.LogLevel.VERBOSE -> LogManager.v(tag, message)
            ScrcpyEvent.LogLevel.DEBUG -> LogManager.d(tag, message)
            ScrcpyEvent.LogLevel.INFO -> LogManager.i(tag, message)
            ScrcpyEvent.LogLevel.WARN -> LogManager.w(tag, message)
            ScrcpyEvent.LogLevel.ERROR -> LogManager.e(tag, message)
        }

        if (enableStats) {
            val eventClass = message.substringAfter("[").substringBefore("]")
            eventStats[eventClass]?.let {
                synchronized(it) {
                    it.loggedCount++
                }
            }
        }
    }

    private fun getLogTag(category: ScrcpyEvent.Category): String =
        when (category) {
            ScrcpyEvent.Category.UI -> LogTags.SCRCPY_EVENT_BUS
            ScrcpyEvent.Category.MONITOR -> LogTags.SCRCPY_EVENT_BUS
            ScrcpyEvent.Category.LIFECYCLE -> LogTags.SCRCPY_CLIENT
            ScrcpyEvent.Category.SYSTEM -> LogTags.SCRCPY_EVENT_BUS
            ScrcpyEvent.Category.MEDIA -> LogTags.VIDEO_DECODER
        }

}

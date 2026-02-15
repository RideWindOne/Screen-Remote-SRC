package com.mobile.scrcpy.android.core.common.event

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Scrcpy 事件日志处理器
 *
 * 统一管理所有事件的日志输出，支持：
 * 1. 日志级别过滤
 * 2. 高频事件采样
 * 3. 事件追踪 ID
 * 4. 事件统计
 * 5. 日志格式化
 */
object ScrcpyEventLogger {
    // 事件计数器（用于采样）
    private val eventCounters = ConcurrentHashMap<String, AtomicLong>()

    // 事件统计
    private val eventStats = ConcurrentHashMap<String, EventStats>()

    // 采样间隔（每 N 次输出一次）
    private const val SAMPLING_INTERVAL = 100L

    // 日志级别过滤（默认 DEBUG 及以上）
    private var minLogLevel = ScrcpyEvent.LogLevel.DEBUG

    // 是否启用事件统计
    private var enableStats = true

    // 是否启用详细日志
    private var enableVerbose = false

    /**
     * 事件统计数据
     */
    data class EventStats(
        var totalCount: Long = 0,
        var loggedCount: Long = 0,
        var sampledCount: Long = 0,
    )

    /**
     * 设置最小日志级别
     */
    fun setMinLogLevel(level: ScrcpyEvent.LogLevel) {
        minLogLevel = level
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, "事件日志级别设置为: $level")
    }

    /**
     * 启用/禁用详细日志
     */
    fun setVerboseEnabled(enabled: Boolean) {
        enableVerbose = enabled
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, "详细日志${if (enabled) "已启用" else "已禁用"}")
    }

    /**
     * 启用/禁用事件统计
     */
    fun setStatsEnabled(enabled: Boolean) {
        enableStats = enabled
    }

    /**
     * 记录事件日志
     */
    fun logEvent(event: ScrcpyEvent) {
        val eventClass = event::class.simpleName ?: "Unknown"
        val logLevel = event.getLogLevel()
        val category = event.getCategory()

        // 更新统计
        if (enableStats) {
            updateStats(eventClass)
        }

        // 检查日志级别
        if (!shouldLog(logLevel)) {
            return
        }

        // 检查是否需要采样
        if (event.needsSampling()) {
            if (!shouldSample(eventClass)) {
                return
            }
        }

        // 格式化并输出日志
        val logMessage = formatLogMessage(event, category, eventClass)
        outputLog(logLevel, category, logMessage)
    }

    /**
     * 检查是否应该输出日志
     */
    private fun shouldLog(level: ScrcpyEvent.LogLevel): Boolean {
        // VERBOSE 需要显式启用
        if (level == ScrcpyEvent.LogLevel.VERBOSE && !enableVerbose) {
            return false
        }

        return level.ordinal >= minLogLevel.ordinal
    }

    /**
     * 检查是否应该采样输出
     */
    private fun shouldSample(eventClass: String): Boolean {
        val counter = eventCounters.getOrPut(eventClass) { AtomicLong(0) }
        val count = counter.incrementAndGet()

        // 每 SAMPLING_INTERVAL 次输出一次
        return count % SAMPLING_INTERVAL == 0L
    }

    /**
     * 更新事件统计
     */
    private fun updateStats(eventClass: String) {
        val stats = eventStats.getOrPut(eventClass) { EventStats() }
        synchronized(stats) {
            stats.totalCount++
        }
    }

    /**
     * 格式化日志消息
     */
    private fun formatLogMessage(
        event: ScrcpyEvent,
        category: ScrcpyEvent.Category,
        eventClass: String,
    ): String {
        val description = event.getDescription()
        val categoryIcon = getCategoryIcon(category)

        // 基础格式: [图标] [类别] 描述
        val baseMessage = "$categoryIcon [$category] $description"

        // 如果需要采样，添加计数信息
        if (event.needsSampling()) {
            val counter = eventCounters[eventClass]
            val count = counter?.get() ?: 0
            return "$baseMessage (累计: $count)"
        }

        return baseMessage
    }

    /**
     * 获取类别图标
     */
    private fun getCategoryIcon(category: ScrcpyEvent.Category): String =
        when (category) {
            ScrcpyEvent.Category.UI -> "👆"
            ScrcpyEvent.Category.MONITOR -> "📊"
            ScrcpyEvent.Category.LIFECYCLE -> "🔄"
            ScrcpyEvent.Category.SYSTEM -> "⚙️"
            ScrcpyEvent.Category.MEDIA -> "🎬"
        }

    /**
     * 输出日志
     */
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

        // 更新已记录计数
        if (enableStats) {
            val eventClass = message.substringAfter("[").substringBefore("]")
            eventStats[eventClass]?.let {
                synchronized(it) {
                    it.loggedCount++
                }
            }
        }
    }

    /**
     * 获取日志标签
     */
    private fun getLogTag(category: ScrcpyEvent.Category): String =
        when (category) {
            ScrcpyEvent.Category.UI -> LogTags.SCRCPY_EVENT_BUS
            ScrcpyEvent.Category.MONITOR -> LogTags.SCRCPY_EVENT_BUS
            ScrcpyEvent.Category.LIFECYCLE -> LogTags.SCRCPY_CLIENT
            ScrcpyEvent.Category.SYSTEM -> LogTags.SCRCPY_EVENT_BUS
            ScrcpyEvent.Category.MEDIA -> LogTags.VIDEO_DECODER
        }

    /**
     * 获取事件统计摘要
     */
    fun getStatsSummary(): String =
        buildString {
            appendLine("=== 事件统计摘要 ===")
            appendLine("总事件类型: ${eventStats.size}")
            appendLine()

            eventStats.entries
                .sortedByDescending { it.value.totalCount }
                .forEach { (eventClass, stats) ->
                    appendLine("[$eventClass]")
                    appendLine("  总计: ${stats.totalCount}")
                    appendLine("  已记录: ${stats.loggedCount}")
                    appendLine("  采样: ${stats.sampledCount}")
                    appendLine()
                }
        }

    /**
     * 重置统计
     */
    fun resetStats() {
        eventCounters.clear()
        eventStats.clear()
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, "事件统计已重置")
    }

    /**
     * 获取特定事件的统计
     */
    fun getEventStats(eventClass: String): EventStats? = eventStats[eventClass]

    /**
     * 获取所有事件统计
     */
    fun getAllEventStats(): Map<String, EventStats> = eventStats.toMap()
}

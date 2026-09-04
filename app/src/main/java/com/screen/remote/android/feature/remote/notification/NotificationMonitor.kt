package com.screen.remote.android.feature.remote.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 被控端通知信息
 */
data class DeviceNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 通知监控器
 * 通过 ADB 轮询 dumpsys notification 检测被控端新通知
 */
class NotificationMonitor(
    private val shellExecutor: suspend (String) -> Result<String>,
    private val scope: CoroutineScope,
    private val onNewNotification: (DeviceNotification) -> Unit,
) {
    private var monitorJob: Job? = null
    private val knownKeys = mutableSetOf<String>()
    private var isFirstPoll = true

    /**
     * 启动通知监控
     * @param intervalMs 轮询间隔，默认 3000ms
     */
    fun start(intervalMs: Long = 3000) {
        if (monitorJob?.isActive == true) return
        isFirstPoll = true
        knownKeys.clear()
        monitorJob = scope.launch {
            while (isActive) {
                try {
                    pollNotifications()
                } catch (_: Exception) {
                    // 忽略单次轮询错误
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * 停止通知监控
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        knownKeys.clear()
    }

    private suspend fun pollNotifications() = withContext(Dispatchers.IO) {
        val result = shellExecutor("dumpsys notification --noredact")
        val output = result.getOrNull() ?: return@withContext
        val notifications = parseNotifications(output)

        if (isFirstPoll) {
            // 首次轮询只记录已知通知，不触发提示
            knownKeys.addAll(notifications.map { it.key })
            isFirstPoll = false
            return@withContext
        }

        notifications.forEach { notification ->
            if (!knownKeys.contains(notification.key)) {
                knownKeys.add(notification.key)
                onNewNotification(notification)
            }
        }

        // 清理已不存在的通知 key，避免内存泄漏
        val currentKeys = notifications.map { it.key }.toSet()
        knownKeys.retainAll(currentKeys)
    }

    /**
     * 解析 dumpsys notification 输出
     * 提取每个 NotificationRecord 的 pkg、title、text
     */
    private fun parseNotifications(output: String): List<DeviceNotification> {
        val notifications = mutableListOf<DeviceNotification>()
        val records = output.split("NotificationRecord(")

        for (record in records) {
            if (record.isBlank()) continue

            val packageName = extractField(record, "pkg=") ?: continue
            val key = extractField(record, "key=") ?: "$packageName-${notifications.size}"
            val title = extractExtrasField(record, "android.title") ?: ""
            val text = extractExtrasField(record, "android.text") ?: ""

            // 跳过没有标题和内容的通知
            if (title.isBlank() && text.isBlank()) continue

            notifications.add(
                DeviceNotification(
                    key = key,
                    packageName = packageName,
                    title = title,
                    text = text,
                )
            )
        }

        return notifications
    }

    /**
     * 提取单行字段，如 pkg=com.tencent.mm
     */
    private fun extractField(record: String, fieldName: String): String? {
        val pattern = Regex("""$fieldName(\S+)""")
        return pattern.find(record)?.groupValues?.get(1)?.trim()
    }

    /**
     * 提取 extras 中的字段，如 android.title=张三
     * 支持 Bundle[{...}] 和逐行格式
     */
    private fun extractExtrasField(record: String, fieldName: String): String? {
        // 尝试匹配 android.title=值（值可能包含空格，直到行尾或下一个字段）
        val pattern = Regex("""$fieldName\s*=\s*(.+?)(?:\n\s*\w|\n\s*$|$)""")
        val match = pattern.find(record) ?: return null
        return match.groupValues[1].trim()
            .removePrefix("Bundle[")
            .removeSuffix("]")
            .trim()
            .takeIf { it.isNotBlank() && it != "null" }
    }
}

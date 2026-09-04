package com.screen.remote.android.feature.remote.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 独立通知监控管理器
 * 负责建立 ADB 连接、轮询被控端通知、保持连接活跃、发送系统通知
 */
object NotificationMonitorManager {
    private const val CHANNEL_ID = "notification_monitor_channel"
    private const val CHANNEL_NAME = "通知监控"
    private const val FOREGROUND_NOTIFICATION_ID = 772373
    private const val POLL_INTERVAL_MS = 3000L
    private const val HEARTBEAT_INTERVAL_MS = 10000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var connectedDeviceId: String? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var monitoringSessionId: String? = null
        private set

    @Volatile
    var currentDeviceName: String? = null
        private set

    private val knownNotificationKeys = mutableSetOf<String>()
    private val lastNotificationContent = mutableMapOf<String, String>()
    private var isFirstPoll = true

    /**
     * 启动通知监控
     */
    fun start(context: Context, sessionData: SessionData): Boolean {
        if (isRunning) return false

        val appContext = context.applicationContext
        val adbManager = AdbConnectionManager.getInstance(appContext)

        isRunning = true
        monitoringSessionId = sessionData.id
        currentDeviceName = sessionData.name
        isFirstPoll = true
        knownNotificationKeys.clear()
        lastNotificationContent.clear()

        // 显示前台服务通知（保持后台运行）
        showForegroundNotification(appContext, sessionData.name)

        // 提示正在连接
        showToast(appContext, "正在连接 ${sessionData.name}...")

        monitorJob = scope.launch {
            try {
                // 获取所有连接候选，按优先级排序
                val candidates = sessionData.toConnectionCandidates().sortedBy { it.priority }
                var lastError: Throwable? = null
                var connectedId: String? = null

                // 依次尝试所有连接候选
                for (candidate in candidates) {
                    if (candidate.transport == ConnectionTransport.USB) {
                        LogManager.w(LogTags.CONTROL_VM, "USB 连接暂不支持独立通知监控，跳过")
                        continue
                    }

                    LogManager.d(LogTags.CONTROL_VM, "通知监控尝试连接: ${candidate.transport} ${candidate.host}:${candidate.port}")

                    val result = adbManager.connectCandidate(
                        candidate = candidate,
                        deviceName = sessionData.name,
                    )

                    if (result.isSuccess) {
                        connectedId = result.getOrNull()?.deviceId
                        LogManager.d(LogTags.CONTROL_VM, "通知监控 ADB 连接成功: $connectedId")
                        break
                    } else {
                        lastError = result.exceptionOrNull()
                        LogManager.w(LogTags.CONTROL_VM, "通知监控连接候选失败: ${lastError?.message}")
                    }
                }

                if (connectedId != null) {
                    connectedDeviceId = connectedId
                    showToast(appContext, "通知监控已启动，正在监控 ${sessionData.name}")

                    // 启动心跳保活
                    startHeartbeat(appContext, connectedId)

                    // 开始轮询通知
                    pollNotifications(appContext, connectedId)
                } else {
                    val errorMsg = lastError?.message ?: "所有连接候选均失败"
                    LogManager.e(LogTags.CONTROL_VM, "通知监控 ADB 连接失败: $errorMsg")
                    showToast(appContext, "通知监控连接失败: $errorMsg")
                    stop(appContext)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.CONTROL_VM, "通知监控异常: ${e.message}", e)
                showToast(appContext, "通知监控异常: ${e.message}")
                stop(appContext)
            }
        }

        return true
    }

    /**
     * 停止通知监控
     */
    fun stop(context: Context) {
        if (!isRunning) return

        val deviceName = currentDeviceName
        isRunning = false
        monitoringSessionId = null
        currentDeviceName = null

        monitorJob?.cancel()
        monitorJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null

        // 断开 ADB 连接
        val deviceId = connectedDeviceId
        if (deviceId != null) {
            scope.launch {
                try {
                    AdbConnectionManager.getInstance(context.applicationContext)
                        .disconnectDevice(deviceId)
                    LogManager.d(LogTags.CONTROL_VM, "通知监控 ADB 已断开: $deviceId")
                } catch (_: Exception) {
                }
            }
        }
        connectedDeviceId = null

        // 取消前台通知
        cancelForegroundNotification(context.applicationContext)

        // 提示已停止
        showToast(context.applicationContext, "通知监控已停止${deviceName?.let { "（$it）" } ?: ""}")
    }

    /**
     * 轮询通知
     */
    private suspend fun pollNotifications(context: Context, deviceId: String) {
        val adbManager = AdbConnectionManager.getInstance(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        while (isRunning) {
            try {
                val connection = adbManager.getConnection(deviceId)
                if (connection == null) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val result = AdbShellManager.execute(connection, "dumpsys notification --noredact")
                val output = result.getOrNull()

                if (output != null) {
                    val notifications = parseNotifications(output)
                    LogManager.d(LogTags.CONTROL_VM, "通知监控轮询: 输出长度=${output.length}, 解析到通知=${notifications.size}, 已知keys=${knownNotificationKeys.size}")

                    if (isFirstPoll) {
                        knownNotificationKeys.addAll(notifications.map { it.key })
                        notifications.forEach { lastNotificationContent[it.key] = "${it.title}|${it.text}" }
                        isFirstPoll = false
                        LogManager.d(LogTags.CONTROL_VM, "通知监控首次轮询，记录 ${notifications.size} 条已知通知")
                    } else {
                        notifications.forEach { notification ->
                            val contentKey = "${notification.title}|${notification.text}"
                            val lastContent = lastNotificationContent[notification.key]

                            if (lastContent == null) {
                                // 全新通知
                                LogManager.d(LogTags.CONTROL_VM, "  新通知: ${notification.packageName} | ${notification.title} | ${notification.text}")
                                knownNotificationKeys.add(notification.key)
                                lastNotificationContent[notification.key] = contentKey
                                sendSystemNotification(
                                    context = context,
                                    notificationManager = notificationManager,
                                    deviceName = currentDeviceName ?: "设备",
                                    notification = notification,
                                )
                            } else if (lastContent != contentKey) {
                                // 通知内容更新（如微信新消息更新同一个通知）
                                LogManager.d(LogTags.CONTROL_VM, "  通知更新: ${notification.packageName} | ${notification.title} | ${notification.text}")
                                lastNotificationContent[notification.key] = contentKey
                                sendSystemNotification(
                                    context = context,
                                    notificationManager = notificationManager,
                                    deviceName = currentDeviceName ?: "设备",
                                    notification = notification,
                                )
                            }
                        }
                        // 清理已不存在的通知
                        val currentKeys = notifications.map { it.key }.toSet()
                        knownNotificationKeys.retainAll(currentKeys)
                        lastNotificationContent.keys.retainAll(currentKeys)
                    }
                } else {
                    LogManager.w(LogTags.CONTROL_VM, "通知监控轮询: dumpsys 输出为空")
                }
            } catch (_: Exception) {
                // 忽略单次轮询错误
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * 心跳保活
     */
    private fun startHeartbeat(context: Context, deviceId: String) {
        heartbeatJob = scope.launch {
            val adbManager = AdbConnectionManager.getInstance(context)
            while (isRunning) {
                try {
                    val connection = adbManager.getConnection(deviceId)
                    if (connection != null) {
                        AdbShellManager.execute(connection, "echo heartbeat")
                    }
                } catch (_: Exception) {
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * 解析 dumpsys notification 输出
     */
    private fun parseNotifications(output: String): List<DeviceNotification> {
        val notifications = mutableListOf<DeviceNotification>()
        val records = output.split("NotificationRecord(")

        for (record in records) {
            if (record.isBlank()) continue

            val packageName = extractField(record, "pkg=") ?: continue
            val key = extractField(record, "key=") ?: "$packageName-${notifications.size}"

            // 尝试从 extras 中提取标题和内容
            val title = extractExtrasField(record, "android.title")
                ?: extractExtrasField(record, "android.subText")
                ?: ""
            val text = extractExtrasField(record, "android.text")
                ?: extractExtrasField(record, "android.bigText")
                ?: extractExtrasField(record, "android.summaryText")
                ?: ""

            // 跳过没有标题和内容的通知（可能是进行中的通知）
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

    private fun extractField(record: String, fieldName: String): String? {
        val pattern = Regex("""$fieldName(\S+)""")
        return pattern.find(record)?.groupValues?.get(1)?.trim()
    }

    /**
     * 从 extras 中提取字段值，支持两种格式：
     * 1. 逐行格式: android.title=String (Ride_Wind)
     * 2. Bundle 格式: Bundle[{android.title=String (Ride_Wind), ...}]
     */
    private fun extractExtrasField(record: String, fieldName: String): String? {
        // 先尝试 Bundle[{...}] 格式
        val bundlePattern = Regex("""Bundle\[\{(.+?)\}\]""")
        val bundleMatch = bundlePattern.find(record)
        if (bundleMatch != null) {
            val bundleContent = bundleMatch.groupValues[1]
            val fieldPattern = Regex("""$fieldName\s*=\s*([^,}]+)""")
            val fieldMatch = fieldPattern.find(bundleContent)
            if (fieldMatch != null) {
                val value = parseTypedValue(fieldMatch.groupValues[1].trim())
                if (value != null) return value
            }
        }

        // 再尝试逐行格式: android.title=String (Ride_Wind)
        val linePattern = Regex("""$fieldName\s*=\s*(.+?)(?:\n\s*\w|\n\s*$|$)""")
        val lineMatch = linePattern.find(record) ?: return null
        val rawValue = lineMatch.groupValues[1].trim()
            .removePrefix("Bundle[")
            .removeSuffix("]")
            .trim()
        return parseTypedValue(rawValue)
    }

    /**
     * 解析带类型的值，如 "String (Ride_Wind)" -> "Ride_Wind"
     * 也支持普通值如 "Ride_Wind" -> "Ride_Wind"
     */
    private fun parseTypedValue(rawValue: String): String? {
        if (rawValue.isBlank() || rawValue == "null") return null

        // 匹配 "String (value)" 或 "Integer (123)" 格式
        val typedPattern = Regex("""\w+\s*\((.*)\)""")
        val match = typedPattern.matchEntire(rawValue)
        if (match != null) {
            val value = match.groupValues[1].trim()
            return value.takeIf { it.isNotBlank() && it != "null" }
        }

        // 普通值
        return rawValue.takeIf { it.isNotBlank() && it != "null" }
    }

    /**
     * 发送系统通知
     */
    private fun sendSystemNotification(
        context: Context,
        notificationManager: NotificationManager,
        deviceName: String,
        notification: DeviceNotification,
    ) {
        ensureNotificationChannel(notificationManager)

        val notificationId = (notification.key.hashCode() and 0xFFFFFF) + 1000

        val contentText = buildString {
            if (notification.title.isNotBlank()) append(notification.title)
            if (notification.text.isNotBlank()) {
                if (isNotEmpty()) append(": ")
                append(notification.text)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(deviceName)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * 显示前台服务通知（保持后台运行）
     */
    private fun showForegroundNotification(context: Context, deviceName: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel(notificationManager)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("通知监控运行中")
            .setContentText("正在监控 $deviceName 的通知")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)

        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, builder.build())
    }

    private fun cancelForegroundNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
    }

    private fun ensureNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "被控端通知监控提醒"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 在主线程显示 Toast
     */
    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

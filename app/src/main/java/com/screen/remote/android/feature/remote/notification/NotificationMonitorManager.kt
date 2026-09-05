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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

    var isRunning: Boolean by mutableStateOf(false)
        private set

    var monitoringSessionId: String? by mutableStateOf(null)
        private set

    @Volatile
    var currentDeviceName: String? = null
        private set

    private val knownNotificationKeys = mutableSetOf<String>()
    private val lastNotificationContent = mutableMapOf<String, String>()
    private var isFirstPoll = true
    private var notifyAllOnStart = false
    private var blockSystemNotifications = true
    private var currentSessionData: SessionData? = null
    private var consecutiveFailures = 0
    private val MAX_CONSECUTIVE_FAILURES = 3
    private val MAX_RECONNECT_ATTEMPTS = 5
    private var reconnectAttempts = 0

    /**
     * 需要过滤的系统服务包名关键词
     * 这些是常驻系统服务通知，不是用户消息
     */
    private val BLOCKED_PACKAGE_KEYWORDS = listOf(
        "clipboard",           // 剪贴板服务（含跨设备剪贴板）
        "universalclipboard",  // 小米跨设备剪贴板服务
    )

    /**
     * 系统应用包名前缀（开启"屏蔽系统通知"时过滤）
     */
    private val SYSTEM_PACKAGE_PREFIXES = listOf(
        // 小米/红米
        "com.miui.",
        "com.xiaomi.",
        "com.milink.",
        // 华为/荣耀
        "com.huawei.",
        "com.hihonor.",
        "com.honor.",
        // OPPO/一加/真我
        "com.oppo.",
        "com.coloros.",
        "com.oneplus.",
        "com.realme.",
        "com.oplus.",
        // vivo/iQOO
        "com.vivo.",
        "com.funtouch.",
        "com.iqoo.",
        // 三星
        "com.samsung.",
        // 魅族
        "com.meizu.",
        // 努比亚/红魔
        "com.nubia.",
        "com.redmagic.",
        // 联想/摩托罗拉
        "com.lenovo.",
        "com.motorola.",
        // 索尼
        "com.sonymobile.",
        "com.sony.",
        // 系统核心服务
        "com.android.systemui",
        "com.android.settings",
        "com.android.providers",
        "com.android.server",
        "android",
    )

    /**
     * 需要保留的系统应用白名单（即使开启屏蔽系统通知也不过滤）
     * 包括短信、电话等重要通知
     */
    private val ALLOWED_SYSTEM_PACKAGES = listOf(
        "com.android.mms",           // 短信
        "com.android.dialer",        // 电话
        "com.android.server.telecom", // 电话服务
        "com.android.incallui",      // 通话界面
        "com.android.contacts",      // 联系人
        "com.android.messaging",     // 信息
        "com.google.android.gm",     // Gmail
        "com.google.android.talk",   // Google Chat
    )

    /**
     * 判断是否为需要过滤的系统服务通知
     * 过滤规则：
     * 1. 包名包含系统服务关键词（如剪贴板服务）
     * 2. 标题包含"服务"且内容为空（系统常驻服务）
     * 3. 如果开启了 blockSystemNotifications，过滤所有系统应用通知（保留白名单中的短信、电话等）
     * 保留：短信、电话、所有第三方应用通知
     */
    private fun isBlockedNotification(packageName: String, title: String, text: String): Boolean {
        val lowerPackage = packageName.lowercase()
        // 过滤剪贴板等系统服务（始终过滤）
        if (BLOCKED_PACKAGE_KEYWORDS.any { lowerPackage.contains(it) }) return true
        // 过滤标题含"服务"且无内容的常驻通知（如"XX服务正在运行"）（始终过滤）
        if (title.contains("服务") && text.isBlank()) return true

        // 如果开启了屏蔽系统通知，过滤系统应用通知
        if (blockSystemNotifications) {
            // 白名单中的系统应用（短信、电话等）保留
            if (ALLOWED_SYSTEM_PACKAGES.any { lowerPackage.startsWith(it.lowercase()) }) return false
            // 其他系统应用过滤
            if (SYSTEM_PACKAGE_PREFIXES.any { lowerPackage.startsWith(it.lowercase()) }) return true
        }

        return false
    }

    /**
     * 启动通知监控
     * 如果已有其他设备在监控，会先停止旧的再启动新的（自动切换）
     * @param notifyAllOnStart 打开时是否提示所有当前未读消息
     * @param blockSystemNotifications 是否屏蔽系统服务通知
     */
    fun start(
        context: Context,
        sessionData: SessionData,
        notifyAllOnStart: Boolean = false,
        blockSystemNotifications: Boolean = true,
    ): Boolean {
        // 如果已经在监控同一个设备，不重复启动
        if (isRunning && monitoringSessionId == sessionData.id) return false

        // 如果正在监控其他设备，先停止旧的
        if (isRunning) {
            stop(context.applicationContext)
        }

        val appContext = context.applicationContext
        val adbManager = AdbConnectionManager.getInstance(appContext)

        isRunning = true
        monitoringSessionId = sessionData.id
        currentDeviceName = sessionData.name
        currentSessionData = sessionData
        isFirstPoll = true
        this.notifyAllOnStart = notifyAllOnStart
        this.blockSystemNotifications = blockSystemNotifications
        knownNotificationKeys.clear()
        lastNotificationContent.clear()
        consecutiveFailures = 0
        reconnectAttempts = 0

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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被正常取消（关闭通知监控），不显示错误
                throw e
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

        // 取消前台通知和所有系统消息通知
        cleanupResidualNotifications(context.applicationContext)

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
                    consecutiveFailures++
                    LogManager.w(LogTags.CONTROL_VM, "通知监控: ADB 连接为空，连续失败 $consecutiveFailures 次")
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        if (attemptReconnect(context)) {
                            consecutiveFailures = 0
                            reconnectAttempts = 0
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // 使用 grep 过滤只保留需要的字段，输出从 750KB 降到几十 KB，避免 OOM
                val result = AdbShellManager.execute(connection, "dumpsys notification --noredact | grep -E 'NotificationRecord\\(|pkg=|key=|android\\.title|android\\.subText|android\\.text|android\\.bigText|android\\.summaryText'")
                val output = result.getOrNull()

                if (output != null) {
                    consecutiveFailures = 0
                    reconnectAttempts = 0
                    val notifications = parseNotifications(output)
                    LogManager.d(LogTags.CONTROL_VM, "通知监控轮询: 输出长度=${output.length}, 解析到通知=${notifications.size}, 已知keys=${knownNotificationKeys.size}")

                    if (isFirstPoll) {
                        knownNotificationKeys.addAll(notifications.map { it.key })
                        notifications.forEach { lastNotificationContent[it.key] = "${it.title}|${it.text}" }
                        isFirstPoll = false
                        LogManager.d(LogTags.CONTROL_VM, "通知监控首次轮询，记录 ${notifications.size} 条已知通知")

                        // 如果设置了打开时提示所有未读消息，则把当前所有通知都提示一遍
                        if (notifyAllOnStart) {
                            LogManager.d(LogTags.CONTROL_VM, "打开时提示所有未读消息，共 ${notifications.size} 条")
                            notifications.forEach { notification ->
                                if (!isBlockedNotification(notification.packageName, notification.title, notification.text)) {
                                    sendSystemNotification(
                                        context = context,
                                        notificationManager = notificationManager,
                                        deviceName = currentDeviceName ?: "设备",
                                        notification = notification,
                                    )
                                }
                            }
                        }
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
                    consecutiveFailures++
                    LogManager.w(LogTags.CONTROL_VM, "通知监控轮询: dumpsys 输出为空，连续失败 $consecutiveFailures 次")
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        if (attemptReconnect(context)) {
                            consecutiveFailures = 0
                            reconnectAttempts = 0
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被正常取消（关闭通知监控），不处理，直接抛出
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                LogManager.w(LogTags.CONTROL_VM, "通知监控轮询异常: ${e.message}，连续失败 $consecutiveFailures 次")
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    try {
                        if (attemptReconnect(context)) {
                            consecutiveFailures = 0
                            reconnectAttempts = 0
                        }
                    } catch (re: Exception) {
                        LogManager.e(LogTags.CONTROL_VM, "通知监控重连异常: ${re.message}", re)
                        stop(context)
                    }
                }
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * 尝试重新连接 ADB
     * @return true 表示重连成功，false 表示重连失败
     */
    private suspend fun attemptReconnect(context: Context): Boolean {
        val sessionData = currentSessionData ?: return false
        reconnectAttempts++

        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            LogManager.e(LogTags.CONTROL_VM, "通知监控: 重连次数超过上限 $MAX_RECONNECT_ATTEMPTS，停止监控")
            showToast(context, "通知监控连接失败，已停止（${currentDeviceName ?: "设备"}）")
            stop(context)
            return false
        }

        return try {
            LogManager.d(LogTags.CONTROL_VM, "通知监控: 尝试第 $reconnectAttempts 次重连 ${sessionData.name}")
            updateForegroundNotification(context, "${currentDeviceName ?: "设备"} - 正在重连（$reconnectAttempts/$MAX_RECONNECT_ATTEMPTS）")
            showToast(context, "通知监控连接断开，正在重连（$reconnectAttempts/$MAX_RECONNECT_ATTEMPTS）...")

            // 先停止旧的心跳
            heartbeatJob?.cancel()
            heartbeatJob = null

            // 断开旧连接
            val oldDeviceId = connectedDeviceId
            if (oldDeviceId != null) {
                try {
                    AdbConnectionManager.getInstance(context.applicationContext).disconnectDevice(oldDeviceId)
                } catch (_: Exception) {}
            }
            connectedDeviceId = null

            // 重新建立连接（复用 start 中的连接逻辑）
            val adbManager = AdbConnectionManager.getInstance(context.applicationContext)
            val candidates = sessionData.toConnectionCandidates().sortedBy { it.priority }

            var connectedId: String? = null
            var lastError: Throwable? = null

            for (candidate in candidates) {
                if (!isRunning) return false
                if (candidate.transport == ConnectionTransport.USB) {
                    continue
                }
                try {
                    val result = adbManager.connectCandidate(
                        candidate = candidate,
                        deviceName = sessionData.name,
                    )
                    if (result.isSuccess) {
                        connectedId = result.getOrNull()?.deviceId
                        LogManager.d(LogTags.CONTROL_VM, "通知监控重连成功: $connectedId")
                        break
                    } else {
                        lastError = result.exceptionOrNull()
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            if (connectedId != null) {
                connectedDeviceId = connectedId
                isFirstPoll = true
                knownNotificationKeys.clear()
                lastNotificationContent.clear()
                // 启动心跳保活（关键：重连后必须启动心跳，否则连接很快又会断开）
                startHeartbeat(context, connectedId)
                updateForegroundNotification(context, currentDeviceName ?: "设备")
                showToast(context, "通知监控已恢复（${currentDeviceName ?: "设备"}）")
                LogManager.d(LogTags.CONTROL_VM, "通知监控重连成功，恢复监控，心跳已启动")
                true
            } else {
                LogManager.w(LogTags.CONTROL_VM, "通知监控重连失败: ${lastError?.message}")
                false
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.CONTROL_VM, "通知监控重连异常: ${e.message}", e)
            showToast(context, "通知监控重连异常，已停止")
            stop(context)
            false
        }
    }

    /**
     * 一次性查询被控端最近的通知（不启动持续监控）
     * 如果通知监控正在运行，复用现有连接；否则临时建立连接，查询完成后断开
     * @param blockSystemNotifications 是否屏蔽系统服务通知
     * @return 通知列表，失败时返回空列表
     */
    suspend fun queryNotifications(
        context: Context,
        sessionData: SessionData,
        blockSystemNotifications: Boolean = true,
    ): List<DeviceNotification> {
        // 同步成员变量，确保 isBlockedNotification 使用正确的设置
        this.blockSystemNotifications = blockSystemNotifications
        val appContext = context.applicationContext
        val adbManager = AdbConnectionManager.getInstance(appContext)

        // 尝试使用现有连接
        var deviceId = connectedDeviceId
        var shouldDisconnect = false

        // 如果没有现有连接或连接已失效，临时建立连接
        if (deviceId == null || adbManager.getConnection(deviceId) == null) {
            val candidates = sessionData.toConnectionCandidates().sortedBy { it.priority }
            for (candidate in candidates) {
                if (candidate.transport == ConnectionTransport.USB) continue
                try {
                    val result = adbManager.connectCandidate(
                        candidate = candidate,
                        deviceName = sessionData.name,
                    )
                    if (result.isSuccess) {
                        deviceId = result.getOrNull()?.deviceId
                        shouldDisconnect = true
                        LogManager.d(LogTags.CONTROL_VM, "查询通知: 临时连接成功 $deviceId")
                        break
                    }
                } catch (e: Exception) {
                    LogManager.w(LogTags.CONTROL_VM, "查询通知: 连接候选失败 ${e.message}")
                }
            }
        }

        if (deviceId == null) {
            LogManager.w(LogTags.CONTROL_VM, "查询通知: 无法连接到 ${sessionData.name}")
            return emptyList()
        }

        // 执行查询
        val connection = adbManager.getConnection(deviceId)
        if (connection == null) {
            LogManager.w(LogTags.CONTROL_VM, "查询通知: 连接为空")
            return emptyList()
        }

        val output = try {
            // 使用 grep 过滤只保留需要的字段，减少内存占用
            val result = AdbShellManager.execute(connection, "dumpsys notification --noredact | grep -E 'NotificationRecord\\(|pkg=|key=|android\\.title|android\\.subText|android\\.text|android\\.bigText|android\\.summaryText'")
            result.getOrNull()
        } catch (e: Exception) {
            LogManager.e(LogTags.CONTROL_VM, "查询通知: 执行命令失败 ${e.message}", e)
            null
        }

        // 如果是临时建立的连接，查询完成后断开
        if (shouldDisconnect) {
            try {
                adbManager.disconnectDevice(deviceId)
                LogManager.d(LogTags.CONTROL_VM, "查询通知: 临时连接已断开")
            } catch (_: Exception) {}
        }

        if (output == null) return emptyList()

        // 解析通知
        val notifications = parseNotifications(output)
        // 应用系统通知过滤
        val filtered = if (blockSystemNotifications) {
            notifications.filter { !isBlockedNotification(it.packageName, it.title, it.text) }
        } else {
            notifications
        }
        LogManager.d(LogTags.CONTROL_VM, "查询通知: 解析到 ${notifications.size} 条，过滤后 ${filtered.size} 条")
        return filtered
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

            // 过滤系统服务通知（如剪贴板服务），保留短信、电话等重要通知
            if (isBlockedNotification(packageName, title, text)) {
                LogManager.d(LogTags.CONTROL_VM, "通知监控过滤系统服务通知: $packageName | $title")
                continue
            }

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

    /**
     * 更新前台服务通知内容（用于显示重连状态等）
     */
    private fun updateForegroundNotification(context: Context, contentText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel(notificationManager)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("通知监控运行中")
            .setContentText(contentText)
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

    /**
     * 清理所有通知监控相关的通知（前台服务通知 + 系统消息通知）
     * 用于应用启动时清理闪退后残留的通知
     */
    fun cleanupResidualNotifications(context: Context) {
        val notificationManager =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 取消当前应用的所有通知（通知监控的通知都在本应用中）
        notificationManager.cancelAll()
        LogManager.d(LogTags.CONTROL_VM, "应用启动，清理残留通知")
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

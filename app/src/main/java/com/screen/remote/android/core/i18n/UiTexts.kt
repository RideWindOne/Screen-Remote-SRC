package com.screen.remote.android.core.i18n

/**
 * 通用文本（按钮、标签、对话框等）
 */
object CommonTexts {
    val BUTTON_DONE = TextPair("完成", "Done")
    val BUTTON_CANCEL = TextPair("取消", "Cancel")
    val BUTTON_SAVE = TextPair("保存", "Save")
    val BUTTON_ADD = TextPair("添加", "Add")
    val BUTTON_CONFIRM = TextPair("确定", "Confirm")
    val BUTTON_OK = TextPair("确定", "OK")
    val BUTTON_BACK = TextPair("返回", "Back")
    val BUTTON_HIDE = TextPair("隐藏", "Hide")
    val BUTTON_SHOW = TextPair("显示", "Show")
    val BUTTON_CLOSE = TextPair("关闭", "Close")
    val BUTTON_RECONNECT = TextPair("重新连接", "Reconnect")
    val BUTTON_CANCEL_CONNECTION = TextPair("取消连接", "Cancel")

    val LABEL_STATUS = TextPair("状态", "Status")
    val ERROR_LABEL = TextPair("错误", "Error")
    val LABEL_DEVICE = TextPair("设备", "Device")
    val LABEL_INTERVAL = TextPair("间隔", "Interval")
    val LABEL_USING = TextPair("使用", "Using")
    val LABEL_ITEMS = TextPair("个", "items")
    val LABEL_CHARACTERS = TextPair("字符", "characters")

    val TIME_1_MINUTE = TextPair("1 分钟", "1 minute")
    val TIME_5_MINUTES = TextPair("5 分钟", "5 minutes")
    val TIME_10_MINUTES = TextPair("10 分钟", "10 minutes")
    val TIME_30_MINUTES = TextPair("30 分钟", "30 minutes")
    val TIME_1_HOUR = TextPair("1 小时", "1 hour")
    val TIME_ALWAYS = TextPair("始终", "Always")

    val STATUS_CONNECTING = TextPair("正在连接...", "Connecting...")
    val ERROR_CONNECTION_FAILED = TextPair("连接失败", "Connection failed")
    val CONNECTION_FAILED_TITLE = TextPair("连接失败", "Connection Failed")

    val FILTER_ALL = TextPair("全部", "All")
    val FILTER_HARDWARE = TextPair("硬件", "Hardware")
    val FILTER_SOFTWARE = TextPair("软件", "Software")

    val HELP_ICON_DESCRIPTION = TextPair("帮助", "Help")
    val HELP_DIALOG_TITLE = TextPair("说明", "Help")
}

/**
 * 日志管理相关文本
 */
object LogTexts {
    val LOG_MANAGEMENT_TITLE = TextPair("日志管理", "Log Management")
    val LOG_DETAIL_TITLE = TextPair("查看日志", "View Log")
    val LOG_SEARCH_PLACEHOLDER = TextPair("搜索日志内容...", "Search logs...")
    val LOG_FILTER_BY_TAG = TextPair("按标签筛选", "Filter by Tag")
    val LOG_ALL_TAGS = TextPair("全部标签", "All Tags")
    val LOG_SHARE_BUTTON = TextPair("分享", "Share")
    val LOG_FILE_TOO_LARGE_TITLE = TextPair("文件过大", "File Too Large")
    val LOG_FILE_TOO_LARGE_MESSAGE =
        TextPair(
            "日志文件超过 1MB，无法直接查看。\n\n建议先清理旧日志，然后重现问题以生成新的日志文件。",
            "Log file exceeds 1MB and cannot be viewed directly.\n\nPlease clear old logs first, then reproduce the issue to generate a new log file.",
        )
    val LOG_CLEAR_AND_RETRY = TextPair("清理日志", "Clear Logs")
    val LOG_NO_RESULTS = TextPair("未找到匹配的日志", "No matching logs found")
    val LOG_FILE_LABEL = TextPair("文件", "File")
    val LOG_SIZE_LABEL = TextPair("大小", "Size")
    val LOG_MODIFIED_LABEL = TextPair("最后修改", "Modified")
    val LOG_DELETE_CONFIRM_TITLE = TextPair("删除日志文件", "Delete Log File")
    val LOG_DELETE_CONFIRM_MESSAGE = TextPair("确定要删除 %s 吗？", "Are you sure you want to delete %s?")
    val LOG_DELETE_BUTTON = TextPair("删除", "Delete")
    val LOG_REFRESH_BUTTON = TextPair("刷新", "Refresh")
    val LOG_STATS_TITLE = TextPair("日志文件统计", "Log Statistics")
    val LOG_FILE_COUNT = TextPair("文件总数", "File Count")
    val LOG_TOTAL_SIZE = TextPair("总大小", "Total Size")
    val LOG_CURRENT_SIZE = TextPair("当前日志大小", "Current Log Size")
    val LOG_QUICK_ACTIONS = TextPair("快捷自动化", "Quick Actions")
    val LOG_CLEAR_OLD_LOGS = TextPair("清除旧日志", "Clear Old Logs")
    val LOG_KEEP_CURRENT_ONLY = TextPair("仅保留当前", "Keep Current Only")
    val LOG_FILES_SECTION = TextPair("日志文件", "Log Files")
    val LOG_VIEW_BUTTON = TextPair("View", "View")
    val LOG_CURRENT_BUTTON = TextPair("当前", "Current")

    val LOG_SYSTEM_INIT_SUCCESS = TextPair("日志系统初始化完成", "Log system initialized")
    val LOG_INIT_FILE_FAILED = TextPair("初始化日志文件失败", "Failed to initialize log file")
    val LOG_CLOSE_FILE_FAILED = TextPair("关闭日志文件失败", "Failed to close log file")
    val LOG_WRITE_FAILED = TextPair("写入日志失败", "Failed to write log")
    val LOG_DELETE_FILE_FAILED = TextPair("删除日志文件失败", "Failed to delete log file")
    val LOG_DELETE_FILE_SUCCESS = TextPair("删除日志文件成功", "Log file deleted successfully")
    val LOG_READ_FILE_FAILED = TextPair("读取日志文件失败", "Failed to read log file")
    val LOG_READ_FILE_ERROR = TextPair("读取日志文件失败", "Failed to read log file")
    val LOG_WRITE_RAW_FAILED = TextPair("写入原始日志失败", "Failed to write raw log")

    val DIALOG_CLEAR_LOGS_TITLE = TextPair("清除全部日志", "Clear All Logs")
    val DIALOG_CLEAR_LOGS_MESSAGE =
        TextPair(
            "这将永久删除所有日志文件。此操作不可撤销！",
            "This will permanently delete all log files. This action cannot be undone!",
        )
    val DIALOG_CLEAR_LOGS_CONFIRM = TextPair("清除", "Clear")
}

/**
 * 事件总线相关文本
 */
object EventBusTexts {
    val STATE_CONNECTED = TextPair("已连接", "Connected")
    val STATE_DISCONNECTED = TextPair("未连接", "Disconnected")
    val STATE_SCREEN_ON = TextPair("亮屏", "Screen On")
    val STATE_SCREEN_OFF = TextPair("息屏", "Screen Off")
    val STATE_LOCKED = TextPair("锁屏", "Locked")
    val STATE_UNLOCKED = TextPair("解锁", "Unlocked")
    val STATE_VIDEO_ACTIVE = TextPair("活跃", "Active")
    val STATE_VIDEO_STALLED = TextPair("停滞", "Stalled")

    val EXCEPTION_SOCKET = TextPair("Socket 错误", "Socket Error")
    val EXCEPTION_DECODER = TextPair("解码器错误", "Decoder Error")
    val EXCEPTION_ADB = TextPair("ADB 错误", "ADB Error")
    val EXCEPTION_SERVER = TextPair("Server 错误", "Server Error")
    val EXCEPTION_NETWORK = TextPair("网络错误", "Network Error")
    val EXCEPTION_UNKNOWN = TextPair("未知错误", "Unknown Error")

    val LOG_EVENT_BUS_STARTED = TextPair("事件总线已启动", "Event bus started")
    val LOG_EVENT_BUS_STOPPED = TextPair("事件总线已停止", "Event bus stopped")
    val LOG_SCREEN_LOCKED = TextPair("设备锁屏", "Device screen locked")
    val LOG_SCREEN_UNLOCKED = TextPair("设备解锁", "Device screen unlocked")
    val LOG_SCREEN_OFF = TextPair("设备息屏", "Device screen off")
    val LOG_SCREEN_ON = TextPair("设备亮屏", "Device screen on")
    val LOG_CONNECTION_ESTABLISHED = TextPair("连接建立", "Connection established")
    val LOG_CONNECTION_LOST = TextPair("连接丢失", "Connection lost")

    val ANOMALY_VIDEO_AFTER_LOCK = TextPair("异常：锁屏后仍有视频输出", "Anomaly: Video output after screen lock")
    val ANOMALY_NO_VIDEO_DATA = TextPair("异常：连接后无视频数据", "Anomaly: No video data after connection")
    val ANOMALY_SOCKET_IDLE = TextPair("异常：Socket 长时间空闲", "Anomaly: Socket idle for too long")

    val SUMMARY_TITLE = TextPair("状态摘要", "State Summary")
    val SUMMARY_CONNECTION = TextPair("连接状态", "Connection")
    val SUMMARY_SCREEN = TextPair("屏幕状态", "Screen")
    val SUMMARY_VIDEO = TextPair("视频", "Video")
    val SUMMARY_AUDIO = TextPair("音频", "Audio")
    val SUMMARY_SERVER_LOG = TextPair("Server 日志", "Server Log")
    val SUMMARY_SOCKET_STATS = TextPair("Socket 统计", "Socket Stats")
    val SUMMARY_RECENT_EXCEPTIONS = TextPair("最近异常", "Recent Exceptions")
    val SUMMARY_FRAMES = TextPair("帧", "frames")
    val SUMMARY_PACKETS = TextPair("包", "packets")
    val SUMMARY_RECEIVED = TextPair("收", "Received")
    val SUMMARY_SENT = TextPair("发", "Sent")
}

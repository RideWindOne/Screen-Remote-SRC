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
    val BUTTON_BACK = TextPair("返回", "Back")
    val BUTTON_HIDE = TextPair("隐藏", "Hide")
    val BUTTON_SHOW = TextPair("显示", "Show")
    val BUTTON_CLOSE = TextPair("关闭", "Close")
    val BUTTON_RECONNECT = TextPair("重新连接", "Reconnect")
    val BUTTON_CANCEL_CONNECTION = TextPair("取消连接", "Cancel")

    val LABEL_STATUS = TextPair("状态", "Status")
    val ERROR_LABEL = TextPair("错误", "Error")
    val LABEL_DEVICE = TextPair("设备", "Device")

    val STATUS_CONNECTING = TextPair("正在连接...", "Connecting...")
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
    val LOG_QUICK_ACTIONS = TextPair("快捷操作", "Quick Actions")
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

    val DIALOG_CLEAR_LOGS_TITLE = TextPair("清除全部日志", "Clear All Logs")
    val DIALOG_CLEAR_LOGS_MESSAGE =
        TextPair(
            "这将永久删除所有日志文件。此操作不可撤销！",
            "This will permanently delete all log files. This action cannot be undone!",
        )
    val DIALOG_CLEAR_LOGS_CONFIRM = TextPair("清除", "Clear")
}

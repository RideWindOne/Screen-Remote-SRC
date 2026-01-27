package com.mobile.scrcpy.android.core.i18n

/**
 * 设置相关文本
 */
object SettingsTexts {
    // 设置页面
    val SETTINGS_TITLE = TextPair("设置", "Settings")
    val SETTINGS_GENERAL = TextPair("通用", "General")
    val SETTINGS_ADB_MANAGEMENT = TextPair("ADB 管理", "ADB Management")
    val SETTINGS_APP_LOGS = TextPair("应用日志", "App Logs")
    val SETTINGS_FEEDBACK_SUPPORT = TextPair("反馈与支持", "Feedback & Support")
    val SETTINGS_APPEARANCE = TextPair("外观", "Appearance")
    val SETTINGS_LANGUAGE = TextPair("语言 / Language", "Language / 语言")
    val SETTINGS_ABOUT = TextPair("关于 Scrcpy Remote", "About Scrcpy Remote")
    val SETTINGS_KEEP_ALIVE = TextPair("后台保持活跃", "Keep Alive in Background")
    val SETTINGS_SHOW_ON_LOCK_SCREEN = TextPair("在灵动岛显示实况", "Show on Dynamic Island")
    val SETTINGS_FLOATING_HAPTIC = TextPair("悬浮球触感反馈", "Floating Ball Haptic Feedback")
    val SETTINGS_MANAGE_ADB_KEYS = TextPair("管理 ADB 密钥", "Manage ADB Keys")
    val SETTINGS_ADB_PAIRING = TextPair("使用配对码进行 ADB 配对", "ADB Pairing with Code")
    val SETTINGS_FILE_TRANSFER_PATH = TextPair("文件发送默认路径", "Default File Transfer Path")
    val SETTINGS_ENABLE_LOG = TextPair("启用日志记录", "Enable Logging")
    val SETTINGS_LOG_MANAGEMENT = TextPair("日志管理", "Log Management")
    val SETTINGS_CLEAR_LOGS = TextPair("清除全部日志", "Clear All Logs")
    val SETTINGS_SUBMIT_ISSUE = TextPair("提交问题", "Submit Issue")
    val SETTINGS_USER_GUIDE = TextPair("使用指南", "User Guide")
    
    // 语言设置
    val LANGUAGE_TITLE = TextPair("语言", "Language")
    val LANGUAGE_SECTION_TITLE = TextPair("语言 / Language", "Language / 语言")
    val LANGUAGE_AUTO = TextPair("跟随系统", "Follow System")
    val LANGUAGE_CHINESE = TextPair("中文", "中文")
    val LANGUAGE_ENGLISH = TextPair("English", "English")
    
    // 外观设置
    val APPEARANCE_TITLE = TextPair("外观", "Appearance")
    val THEME_SECTION_TITLE = TextPair("主题", "Theme")
    val THEME_SYSTEM = TextPair("跟随系统", "Follow System")
    val THEME_DARK = TextPair("深色模式", "Dark Mode")
    val THEME_LIGHT = TextPair("浅色模式", "Light Mode")
    
    // 文件路径对话框
    val DIALOG_FILE_PATH_TITLE = TextPair("文件发送路径", "File Transfer Path")
    val DIALOG_FILE_PATH_DEFAULT = TextPair("默认路径", "Default Path")
    val DIALOG_FILE_PATH_QUICK_SELECT = TextPair("快速选择", "Quick Select")
    val DIALOG_FILE_PATH_INFO = TextPair("信息", "Info")
    val DIALOG_FILE_PATH_INFO_TEXT = TextPair(
        "通过「发送文件」操作发送的文件将被推送到 Android 设备上的此路径。\n\n路径必须以 /sdcard/ 或类似可访问且开放的绝对路径。",
        "Files sent via 'Send File' will be pushed to this path on the Android device.\n\nThe path must be an accessible absolute path starting with /sdcard/ or similar."
    )
    val DIALOG_FILE_PATH_RESET = TextPair("重置为默认", "Reset to Default")
    
    // 关于页面
    val ABOUT_TITLE = TextPair("关于 Scrcpy Remote", "About Scrcpy Remote")
    val ABOUT_BASED_ON = TextPair("基于 Scrcpy", "Based on Scrcpy")
    val ABOUT_DESCRIPTION = TextPair(
        "Scrcpy Remote 是一款基于 ADB 协议的远程桌面工具，通常用于连接具有公网 IP 地址的服务或同一局域网内的服务。",
        "Scrcpy Remote is a remote desktop tool based on ADB protocol, typically used to connect to services with public IP addresses or services within the same local network."
    )
    val ABOUT_CONNECTION_TIP = TextPair(
        "如果无法正常连接到您的服务，请先检查网络连接是否正常。",
        "If you cannot connect to your service properly, please check if the network connection is normal first."
    )
    val ABOUT_HELP_TEXT = TextPair(
        "如果在使用过程中遇到问题并需要帮助，也可以加入我们的 Telegram 频道。",
        "If you encounter problems during use and need help, you can also join our Telegram channel."
    )
    val ABOUT_WECHAT_QR = TextPair("扫码添加微信", "Scan to add WeChat")
    val ABOUT_TELEGRAM_BUTTON = TextPair("Telegram 频道", "Telegram Channel")
    val ABOUT_PORTING_BUTTON = TextPair("软件：XRsec", "Software：XRSec")
}

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
    val SETTINGS_DEVICE_PAIRING = TextPair("使用配对码进行 ADB 配对", "ADB Pairing with Pairing Code")
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
    val DIALOG_FILE_PATH_SELECT_FOLDER = TextPair("选择文件夹", "Select Folder")
    val DIALOG_FILE_PATH_QUICK_SELECT = TextPair("快速选择", "Quick Select")
    val DIALOG_FILE_PATH_INFO = TextPair("信息", "Info")
    val DIALOG_FILE_PATH_INFO_TEXT =
        TextPair(
            "通过「发送文件」操作发送的文件将被推送到 Android 设备上的此路径。\n\n路径必须以 /sdcard/ 或类似可访问且开放的绝对路径。",
            "Files sent via 'Send File' will be pushed to this path on the Android device.\n\nThe path must be an accessible absolute path starting with /sdcard/ or similar.",
        )
    val DIALOG_FILE_PATH_RESET = TextPair("重置为默认", "Reset to Default")

    // 关于页面
    val ABOUT_TITLE = TextPair("关于 Scrcpy Remote", "About Scrcpy Remote")
    val ABOUT_BASED_ON = TextPair("基于 Scrcpy", "Based on Scrcpy")
    val ABOUT_DESCRIPTION =
        TextPair(
            "Scrcpy Remote 是一款基于 ADB 协议的远程桌面工具，通常用于连接具有公网 IP 地址的服务或同一局域网内的服务。",
            "Scrcpy Remote is a remote desktop tool based on ADB protocol, typically used to connect to services with public IP addresses or services within the same local network.",
        )
    val ABOUT_CONNECTION_TIP =
        TextPair(
            "如果无法正常连接到您的服务，请先检查网络连接是否正常。",
            "If you cannot connect to your service properly, please check if the network connection is normal first.",
        )
    val ABOUT_HELP_TEXT =
        TextPair(
            "如果在使用过程中遇到问题并需要帮助，也可以加入我们的 Telegram 频道。",
            "If you encounter problems during use and need help, you can also join our Telegram channel.",
        )
    val ABOUT_WECHAT_QR = TextPair("扫码添加微信", "Scan to add WeChat")
    val ABOUT_TELEGRAM_BUTTON = TextPair("Telegram 频道", "Telegram Channel")
    val ABOUT_PORTING_BUTTON = TextPair("软件：XRsec", "Software：XRSec")

    // 帮助说明文本
    val HELP_GROUP_MANAGE =
        TextPair(
            "创建和管理会话分组，将相关的会话组织在一起。可以创建多级分组结构，方便快速查找和管理大量会话。",
            "Create and manage session groups to organize related sessions together. You can create multi-level group structures for easy search and management of large numbers of sessions.",
        )
    val HELP_KEEP_ALIVE =
        TextPair(
            "设置应用在后台运行时保持活跃的时长。选择「始终」可以让应用在后台持续运行不被系统杀死，但会增加电量消耗。较短的时长可以节省电量，但可能导致后台连接中断。",
            "Set how long the app stays active when running in the background. Selecting 'Always' keeps the app running continuously in the background without being killed by the system, but increases battery consumption. Shorter durations save battery but may cause background connections to be interrupted.",
        )
    val HELP_FLOATING_HAPTIC =
        TextPair(
            "启用后，点击悬浮球按钮时会产生触感反馈（震动）。触感反馈可以提供更好的操作体验，但会略微增加电量消耗。",
            "When enabled, tapping floating ball buttons will produce haptic feedback (vibration). Haptic feedback provides better user experience but slightly increases battery consumption.",
        )
    val HELP_SHOW_ON_LOCK_SCREEN =
        TextPair(
            "在 iPhone 的灵动岛或锁屏界面显示 Scrcpy 连接状态的实况活动。可以快速查看连接状态和控制会话，无需解锁手机。（此功能暂未实现）",
            "Display Scrcpy connection status as Live Activity on iPhone's Dynamic Island or lock screen. Allows quick viewing of connection status and session control without unlocking the phone. (This feature is not yet implemented)",
        )
    val HELP_MANAGE_ADB_KEYS =
        TextPair(
            "管理用于 ADB 连接认证的密钥对。每个密钥对应一个设备的信任关系。如果设备提示「未授权」，可以在此删除旧密钥后重新连接以重新授权。",
            "Manage key pairs used for ADB connection authentication. Each key corresponds to a trust relationship with a device. If a device shows 'unauthorized', you can delete the old key here and reconnect to re-authorize.",
        )
    val HELP_DEVICE_PAIRING =
        TextPair(
            "通过输入配对码的方式配对 Android 设备。在被控设备的「开发者选项」中启用「无线调试」，点击「使用配对码配对设备」，然后在此输入显示的 IP、端口和配对码即可建立连接。",
            "Pair with Android devices by entering pairing code. Enable 'Wireless debugging' in the target device's 'Developer options', tap 'Pair device with pairing code', then enter the displayed IP, port and pairing code here to establish a connection.",
        )
    val HELP_FILE_TRANSFER_PATH =
        TextPair(
            "设置通过「发送文件」功能传输文件到远程设备时的默认保存路径。路径必须是设备上可访问的绝对路径，通常以 /sdcard/ 开头。例如：/sdcard/Download",
            "Set the default save path when transferring files to remote devices via the 'Send File' feature. The path must be an accessible absolute path on the device, usually starting with /sdcard/. Example: /sdcard/Download",
        )
    val HELP_ENABLE_LOG =
        TextPair(
            "启用应用活动日志记录。日志会记录应用的关键操作和错误信息，用于问题排查和调试。日志文件存储在应用私有目录中，不会占用大量空间。",
            "Enable application activity logging. Logs record key operations and error messages for troubleshooting and debugging. Log files are stored in the app's private directory and won't take up much space.",
        )
    val HELP_LOG_MANAGEMENT =
        TextPair(
            "查看和管理应用日志文件。可以查看日志内容、导出日志文件用于问题反馈，或删除不需要的日志文件以释放空间。",
            "View and manage application log files. You can view log content, export log files for issue reporting, or delete unnecessary log files to free up space.",
        )
}

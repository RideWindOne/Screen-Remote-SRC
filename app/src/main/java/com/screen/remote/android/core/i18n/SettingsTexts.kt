package com.screen.remote.android.core.i18n

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
    val SETTINGS_ABOUT = TextPair("关于 Screen Remote", "About Screen Remote")
    val SETTINGS_FLOATING_HAPTIC = TextPair("悬浮球触感反馈", "Floating Ball Haptic Feedback")
    val SETTINGS_PERFORMANCE_STATS = TextPair("显示帧率与网络速率", "Show FPS & Network Rates")
    val SETTINGS_MANAGE_ADB_KEYS = TextPair("管理 ADB 密钥", "Manage ADB Keys")
    val SETTINGS_DEVICE_PAIRING = TextPair("使用配对码进行 ADB 配对", "ADB Pairing with Pairing Code")
    val SETTINGS_ENABLE_LOG = TextPair("启用日志记录", "Enable Logging")
    val SETTINGS_DEBUG_MODE = TextPair("调试模式", "Debug Mode")

    val SETTINGS_EVENT_STREAM_LOG = TextPair("事件流日志", "Event Stream Logs")
    val SETTINGS_AUDIO_STREAM_LOG = TextPair("音频流日志", "Audio Stream Logs")
    val SETTINGS_VIDEO_STREAM_LOG = TextPair("视频流日志", "Video Stream Logs")
    val SETTINGS_CONTROL_STREAM_LOG = TextPair("控制流日志", "Control Stream Logs")
    val SETTINGS_SHELL_STREAM_LOG = TextPair("Shell 日志", "Shell Stream Logs")
    val SETTINGS_MANAGEMENT_LOG = TextPair("管理日志", "Management Logs")
    val SETTINGS_LOG_MANAGEMENT = TextPair("日志管理", "Log Management")
    val SETTINGS_CLEAR_LOGS = TextPair("清除全部日志", "Clear All Logs")
    val SETTINGS_SUBMIT_ISSUE = TextPair("提交问题", "Submit Issue")
    val SETTINGS_USER_GUIDE = TextPair("使用指南", "User Guide")

    // 备份与恢复
    val BACKUP_RESTORE_TITLE = TextPair("备份与恢复", "Backup & Restore")
    val BACKUP_DATA = TextPair("导出数据", "Export Data")
    val RESTORE_DATA = TextPair("导入数据", "Import Data")
    val BACKUP_INFO = TextPair("数据管理", "Data Management")
    val FILE_PICKER_UNAVAILABLE =
        TextPair("没有可用的系统文件选择器", "No system file picker is available")

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

    // 关于页面
    val ABOUT_BASED_ON = TextPair("基于 Scrcpy", "Based on Scrcpy")
    val ABOUT_CHECK_UPDATE = TextPair("检查更新", "Check for updates")
    val ABOUT_AUTO_CHECK_UPDATE = TextPair("自动检测更新", "Automatically check for updates")
    val ABOUT_CHECKING_UPDATE = TextPair("正在检查…", "Checking…")
    val ABOUT_UPDATE_AVAILABLE = TextPair("发现新版本", "Update available")
    val ABOUT_UPDATE_CURRENT_VERSION = TextPair("当前版本：%s", "Current version: %s")
    val ABOUT_UPDATE_LATEST_VERSION = TextPair("最新版本：%s", "Latest version: %s")
    val ABOUT_UPDATE_OPEN_RELEASES = TextPair("查看发布页", "View release")
    val ABOUT_UPDATE_DOWNLOAD_INSTALL = TextPair("下载并安装", "Download and install")
    val ABOUT_UPDATE_DOWNLOADING = TextPair("正在下载 %s", "Downloading %s")
    val ABOUT_UPDATE_CANCEL_DOWNLOAD = TextPair("取消下载", "Cancel download")
    val ABOUT_UPDATE_NO_APK = TextPair("没有适用于当前设备的安装包", "No compatible APK is available")
    val ABOUT_UPDATE_DOWNLOAD_FAILED = TextPair("下载更新失败，请稍后重试", "Update download failed. Please try again")
    val ABOUT_UPDATE_INSTALL_PERMISSION =
        TextPair("请允许 Screen Remote 安装应用后重试", "Allow Screen Remote to install apps, then try again")
    val ABOUT_UPDATE_LATER = TextPair("稍后", "Later")
    val ABOUT_UPDATE_LATEST = TextPair("当前已是最新版本", "You're up to date")
    val ABOUT_UPDATE_FAILED = TextPair("检查更新失败，请稍后重试", "Update check failed. Please try again later")
    val ABOUT_TELEMETRY = TextPair("遥测诊断", "Telemetry diagnostics")
    val ABOUT_TELEMETRY_HELP =
        TextPair(
            "开启后，应用启动时会上传前一天的应用日志；若已上传则仅发送一次 ping。服务端会取得请求 IP，用于诊断服务连接错误和汇总匿名使用情况。日志可能包含连接地址等诊断信息。",
            "When enabled, the app uploads the previous day's app logs at startup. If already uploaded, it only sends one ping. The server receives the request IP to diagnose service connection errors and summarize anonymous usage. Logs may contain connection addresses and other diagnostic details.",
        )
    val ABOUT_TELEMETRY_CONSENT_TITLE = TextPair("开启遥测诊断？", "Enable telemetry diagnostics?")
    val ABOUT_TELEMETRY_CONSENT =
        TextPair(
            "Screen Remote 将每天上传前一天的应用日志，并由服务端取得本次请求的 IP 地址。日志用于分析功能使用情况和连接错误，不会上传截图或远程画面。你可以随时在此关闭。",
            "Screen Remote will upload the previous day's app logs daily, and the server will receive the request IP address. Logs are used to analyze feature usage and connection errors. Screenshots and remote video are not uploaded. You can turn this off at any time.",
        )
    val ABOUT_TELEMETRY_ENABLE = TextPair("同意并开启", "Agree and enable")
    val ABOUT_TELEMETRY_CANCEL = TextPair("取消", "Cancel")
    val ABOUT_DESCRIPTION =
        TextPair(
            "Screen Remote 是一款基于 ADB 协议的远程桌面工具，通常用于连接具有公网 IP 地址的服务或同一局域网内的服务。",
            "Screen Remote is a remote desktop tool based on ADB protocol, typically used to connect to services with public IP addresses or services within the same local network.",
        )
    val ABOUT_CONNECTION_TIP =
        TextPair(
            "如果无法正常连接到您的服务，请先检查网络连接是否正常。",
            "If you cannot connect to your service properly, please check if the network connection is normal first.",
        )
    val ABOUT_HELP_TEXT =
        TextPair(
            "如果在使用过程中遇到问题并需要帮助，也可以加入我们的 Wechat / Telegram 频道。",
            "If you encounter problems during use and need help, you can also join our Wechat / Telegram channel.",
        )
    val ABOUT_WECHAT_QR = TextPair("扫码添加微信群聊", "Scan to add WeChat Group")
    val ABOUT_WECHAT_BUTTON = TextPair("微信 群组", "Wechat Group")
    val ABOUT_WECHAT_ID = TextPair("微信：XR_Sec", "Wechat: XR_Sec")
    val ABOUT_WECHAT_SAVE = TextPair("长按二维码保存到相册", "Long press to save the QR code")
    val ABOUT_WECHAT_SAVED = TextPair("二维码已保存到相册", "QR code saved to gallery")
    val ABOUT_WECHAT_SAVE_F = TextPair("保存二维码失败", "Failed to save QR code")
    val ABOUT_TELEGRAM_BUTTON = TextPair("Telegram 频道", "Telegram Channel")
    val ABOUT_PORTING_BUTTON = TextPair("Github：XRsec", "Github：XRSec")
    val ABOUT_DONATE_BUTTON = TextPair("打赏支持", "Donate")
    val ABOUT_DONATE_TITLE = TextPair("打赏支持", "Donate")
    val ABOUT_DONATE_USDT_LABEL = TextPair("USDT TRC20", "USDT TRC20")
    val ABOUT_DONATE_GATE_LABEL = TextPair("Gate 邀请码", "Gate invitation code")
    val ABOUT_DONATE_COPY_HINT = TextPair("点击复制", "Tap to copy")
    val ABOUT_DONATE_ADDRESS_COPIED = TextPair("TRC20 地址已复制", "TRC20 address copied")
    val ABOUT_DONATE_INVITE_COPIED = TextPair("Gate 邀请码已复制", "Gate invitation code copied")
    val ABOUT_DONATE_GATE_INFO =
        TextPair(
            "享受 88% 高额手续费返佣\n基础返佣 78% 送 VIP10\n包含 10% 返佣\n\n如果这个工具帮到了你，欢迎随手支持一下。\uD83E\uDEF6",
            "Enjoy 88% high-fee rebate\nBase rebate 78% with VIP10\nIncludes 10% rebate\n\nIf this tool has helped you, your support is always appreciated.\uD83E\uDEF6",
        )

    // 帮助说明文本
    val HELP_GROUP_MANAGE =
        TextPair(
            "创建和管理会话分组，将相关的会话组织在一起。可以创建多级分组结构，方便快速查找和管理大量会话。",
            "Create and manage session groups to organize related sessions together. You can create multi-level group structures for easy search and management of large numbers of sessions.",
        )
    val HELP_FLOATING_HAPTIC =
        TextPair(
            "启用后，点击悬浮球按钮时会产生触感反馈（震动）。触感反馈可以提供更好的操作体验，但会略微增加电量消耗。",
            "When enabled, tapping floating ball buttons will produce haptic feedback (vibration). Haptic feedback provides better user experience but slightly increases battery consumption.",
        )
    val HELP_PERFORMANCE_STATS =
        TextPair(
            "在 scrcpy 会话左上角显示实际解码/渲染 FPS、视频流码率，以及本应用的网络发送与接收速率。默认关闭，开启后无需重连即可生效。",
            "Show decoded/rendered FPS, video stream bitrate, and this app's network TX/RX rates in scrcpy sessions. Off by default and takes effect without reconnecting.",
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
    val HELP_ENABLE_LOG =
        TextPair(
            "启用应用活动日志记录。日志会记录应用的关键操作和错误信息，用于问题排查和调试。日志文件存储在应用私有目录中，不会占用大量空间。",
            "Enable application activity logging. Logs record key operations and error messages for troubleshooting and debugging. Log files are stored in the app's private directory and won't take up much space.",
        )
    val HELP_DEBUG_MODE =
        TextPair(
            "在应用所有页面的右下角显示实时日志按钮。日志窗口支持背景透明度调节和按级别着色。",
            "Show a live log button at the bottom-right of every app screen. The log window supports adjustable background opacity and level colors.",
        )
    val HELP_AUDIO_STREAM_LOG =
        TextPair(
            "记录音频流调试细节，例如配置包、音频帧、输出缓冲和部分头信息。仅在排查音频解码或音频链路问题时建议开启。",
            "Record detailed audio stream diagnostics such as config packets, audio frames, output buffers, and header details. Recommended only when debugging audio decoding or transport issues.",
        )
    val HELP_VIDEO_STREAM_LOG =
        TextPair(
            "记录视频流调试细节，例如视频元数据、关键帧、包头和解码器切面日志。仅在排查视频链路问题时建议开启。",
            "Record detailed video stream diagnostics such as video metadata, keyframes, packet headers, and decoder surface logs. Recommended only when debugging video transport issues.",
        )
    val HELP_CONTROL_STREAM_LOG =
        TextPair(
            "记录控制流调试细节，例如控制消息发送线程、文本注入、控制保活与控制 Socket 就绪状态。仅在排查控制失效时建议开启。",
            "Record detailed control stream diagnostics such as sender thread activity, text injection, control keepalive, and control socket readiness. Recommended only when debugging input/control issues.",
        )
    val HELP_EVENT_STREAM_LOG =
        TextPair(
            "记录会话事件流和状态机调试细节，例如 SCLI 会话事件、SDL 状态变化、事件总线与组件快照。仅在排查连接流程、状态流转或清理流程时建议开启。",
            "Record session event-flow and state-machine diagnostics such as SCLI session events, SDL state changes, event bus activity, and component snapshots. Recommended only when debugging connection flow, state transitions, or cleanup.",
        )
    val HELP_SHELL_STREAM_LOG =
        TextPair(
            "记录 scrcpy-server shell 输出、stderr、退出码和启动/监控阶段的包级诊断信息。建议在排查 server 启动失败、server 进程退出或 dummy byte/Socket 异常时开启。",
            "Record scrcpy-server shell stdout, stderr, exit codes, and packet-level diagnostics during startup/runtime monitoring. Recommended when debugging server startup failures, process exits, or dummy-byte/socket anomalies.",
        )
    val HELP_MANAGEMENT_LOG =
        TextPair(
            "记录管理功能的调试细节，例如 ADB Bridge 命令、应用图标辅助请求和文件/应用管理相关的高频诊断日志。仅在排查管理功能时建议开启。",
            "Record detailed management diagnostics such as ADB Bridge commands, app-icon helper requests, and high-frequency file/app management diagnostics. Recommended only when debugging management features.",
        )
    val HELP_LOG_MANAGEMENT =
        TextPair(
            "查看和管理应用日志文件。可以查看日志内容、导出日志文件用于问题反馈，或删除不需要的日志文件以释放空间。",
            "View and manage application log files. You can view log content, export log files for issue reporting, or delete unnecessary log files to free up space.",
        )
    val HELP_BACKUP_DATA =
        TextPair(
            "将应用设置、会话配置、群组信息和 ADB 密钥导出为 JSON 文件，您可以选择保存位置。可用于数据备份或迁移到其他设备。",
            "Export app settings, session configurations, group information and ADB keys as JSON file. You can choose where to save it. Can be used for data backup or migration to other devices.",
        )
    val HELP_RESTORE_DATA =
        TextPair(
            "从您选择的备份文件导入数据，恢复应用设置、会话、群组和 ADB 密钥。注意：导入将覆盖当前所有数据。",
            "Import previously exported backup file from Downloads folder to restore app settings, sessions, groups and ADB keys. Note: Import will overwrite all current data.",
        )
}

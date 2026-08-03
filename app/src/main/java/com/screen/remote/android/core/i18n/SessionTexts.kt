package com.screen.remote.android.core.i18n

/**
 * 会话管理相关文本
 */
object SessionTexts {
    // 主页面
    val MAIN_TITLE_SESSIONS = TextPair("Scrcpy Sessions", "Scrcpy Sessions")
    val MAIN_TAB_SESSIONS = TextPair("会话", "Sessions")
    val MAIN_ADD_SESSION = TextPair("添加会话", "Add Session")
    val MAIN_SCAN_NEARBY_DEVICES = TextPair("扫描附近设备", "Scan Nearby Devices")
    val MAIN_NEARBY_ADB_DEVICES = TextPair("附近的 ADB 设备", "Nearby ADB Devices")
    val MAIN_MDNS_ADDRESS_COPIED = TextPair("mDNS 地址已复制", "mDNS Address Copied")
    val MAIN_TCP_ADDRESS_COPIED = TextPair("TCP 地址已复制", "TCP Address Copied")
    val MAIN_MDNS_CONNECTABLE = TextPair("mDNS 可连接", "mDNS Connectable")
    val MAIN_MDNS_PAIRABLE = TextPair("ADB 可配对", "ADB Pairable")
    val MAIN_TCP_ADB = TextPair("ADB TCP", "ADB TCP")
    val MAIN_TLS_ADB = TextPair("ADB TLS", "ADB TLS")
    val MAIN_SCAN_DISCOVERING_HOSTS = TextPair("正在发现局域网主机…", "Discovering LAN hosts…")
    val MAIN_SCAN_CHECKING_HISTORY = TextPair("正在复查历史地址…", "Checking saved addresses…")
    val MAIN_SCAN_CHECKING_COMMON_PORTS = TextPair("正在探测常用 ADB 端口…", "Checking common ADB ports…")
    val MAIN_SCAN_CHECKING_DYNAMIC_PORTS = TextPair("正在探测动态端口…", "Checking dynamic ports…")
    val MAIN_SCAN_COMPLETE = TextPair("扫描完成", "Scan Complete")
    val MAIN_SCAN_FAILED = TextPair("扫描失败", "Scan Failed")
    val MAIN_SCAN_NETWORK_CHANGED = TextPair("网络已变化，请刷新后重新扫描", "Network changed. Refresh to scan again.")
    val MAIN_SCAN_PROGRESS = TextPair("%d / %d", "%d / %d")
    val MAIN_REFRESH_SCAN = TextPair("重新扫描", "Scan Again")
    val MAIN_LOCAL_NETWORK_PERMISSION_REQUIRED =
        TextPair("需要局域网权限才能扫描设备", "Local network permission is required to scan devices")

    // 会话列表
    val SESSION_NO_SESSIONS = TextPair("没有 Scrcpy Sessions", "No Scrcpy Sessions")
    val SESSION_CLICK_TO_CONNECT = TextPair("点击连接", "Tap to Connect")
    val SESSION_CONNECTED = TextPair("已连接", "Connected")
    val SESSION_CONFIRM_DELETE = TextPair("确认删除", "Confirm Delete")
    val SESSION_CONFIRM_DELETE_MESSAGE = TextPair(
        "确定要删除会话 \"%s\" 吗？",
        "Are you sure you want to delete session \"%s\"?"
    )
    val SESSION_DELETE = TextPair("删除", "Delete")
    val SESSION_CANCEL = TextPair("取消", "Cancel")
    val SESSION_URL_COPIED = TextPair("URL 已复制", "URL Copied")
    val SESSION_EDIT = TextPair("编辑会话", "Edit Session")
    val SESSION_DELETE_SESSION = TextPair("删除会话", "Delete Session")
    val SESSION_CONNECT = TextPair("连接会话", "Connect Session")
    val SESSION_COPY = TextPair("复制会话", "Copy Session")
    val SESSION_COPY_URL = TextPair("复制 URL", "Copy URL")
    val SESSION_MANAGE = TextPair("管理功能", "Manage")
    val SESSION_GAME_MODE_BADGE = TextPair("游戏", "Game")
    val SESSION_RESET_CONNECTION = TextPair("重置连接并重新检测", "Reset Connection and Redetect")
    val SESSION_RESET_CONNECTION_SUCCESS =
        TextPair(
            "ADB 连接和自动探测缓存已重置，下次连接将重新建链并检测编解码器",
            "The ADB connection and auto-detection cache were reset. The next connection will rebuild the link and redetect codecs.",
        )
    val SESSION_RESET_CONNECTION_FAILED =
        TextPair("重置会话连接失败", "Failed to reset the session connection")
    val LATENCY_TEST_ENTRY = TextPair("连接延迟测试", "SpeedTest")
    val LATENCY_TEST_HELP =
        TextPair(
            "测试当前会话的全部 mDNS 与 TCP 地址，并在页面中显示完整结果。",
            "Test every mDNS and TCP endpoint in this session and show all results on screen.",
        )
    val LATENCY_TEST_COPY_ALL = TextPair("复制全部测试结果", "Copy All Test Results")
    val LATENCY_TEST_COPIED = TextPair("全部测试结果已复制", "All test results copied")
    val SESSION_MANAGEMENT_TITLE = TextPair("会话管理", "Session Management")
    val SESSION_EMPTY_HINT =
        TextPair(
            "点击右上角 + 按钮开始新的 scrcpy 会话。\n会话会保存在此处以便快速访问。",
            "Tap the + button in the top right to start a new scrcpy session.\nSessions will be saved here for quick access.",
        )
    val SESSION_SAVE_BUTTON = TextPair("保存会话", "Save Session")
    val SESSION_ADD = TextPair("添加会话", "Add Session")
    val SESSION_SAVE = TextPair("保存", "Save")
    val ONBOARDING_SKIP = TextPair("跳过", "Skip")
    val ONBOARDING_SWIPE_HINT = TextPair("左右滑动，探索更多", "Swipe to explore")

    val ONBOARDING_SESSION_EYEBROW = TextPair("会话配置", "SESSION SETUP")
    val ONBOARDING_SESSION_TITLE = TextPair("画质与流畅度，由你决定", "Tune quality your way")
    val ONBOARDING_SESSION_DESCRIPTION = TextPair(
        "每个会话都能保存独立的画面与声音设置，连接不同设备时无需反复调整。",
        "Each session keeps its own video and audio settings, so every device reconnects just the way you like it."
    )
    val ONBOARDING_RESOLUTION_TITLE = TextPair("分辨率", "Resolution")
    val ONBOARDING_RESOLUTION_BODY =
        TextPair(
            "保持原始画质，或限制为 1080p、720p 来降低延迟。",
            "Keep native quality, or cap it at 1080p or 720p for lower latency."
        )
    val ONBOARDING_FPS_TITLE = TextPair("帧率与码率", "Frame rate & bitrate")
    val ONBOARDING_FPS_BODY =
        TextPair(
            "在 60 fps 的顺滑体验和更低的带宽占用之间自由取舍。",
            "Balance fluid 60 fps motion against lower bandwidth usage."
        )
    val ONBOARDING_CODEC_TITLE = TextPair("音视频编解码", "Audio & video codecs")
    val ONBOARDING_CODEC_BODY =
        TextPair(
            "可选择视频与音频格式，也能指定设备端编码器和本机解码器。",
            "Choose audio and video formats, plus the encoder on the device and decoder on this phone."
        )

    val ONBOARDING_FLOATING_BALL_EYEBROW = TextPair("悬浮球手势", "FLOATING BALL GESTURES")
    val ONBOARDING_FLOATING_BALL_TITLE = TextPair("常用操作，一划即达", "Quick controls, one gesture away")
    val ONBOARDING_FLOATING_BALL_DESCRIPTION =
        TextPair(
            "在会话的连接选项中开启悬浮球，即可在远程控制时快速操作；游戏模式下同样遵循该开关。",
            "Enable the floating ball in a session's connection options for quick controls while connected. Game Mode follows the same setting.",
        )
    val ONBOARDING_FLOATING_BALL_TAP_TITLE = TextPair("点击与拖动", "Tap and drag")
    val ONBOARDING_FLOATING_BALL_TAP_BODY =
        TextPair(
            "点击打开快捷菜单，拖动可调整悬浮球位置。",
            "Tap to open the quick menu, or drag to reposition the floating ball.",
        )
    val ONBOARDING_FLOATING_BALL_SWIPE_TITLE = TextPair("长按后向四周滑动", "Press and slide")
    val ONBOARDING_FLOATING_BALL_SWIPE_BODY =
        TextPair(
            "向上回到桌面、向下展开通知栏、向左返回、向右打开后台任务。",
            "Slide up for Home, down for notifications, left for Back, or right for Recents.",
        )
    val ONBOARDING_FLOATING_BALL_SCREENSHOT_TITLE = TextPair("持续长按", "Keep holding")
    val ONBOARDING_FLOATING_BALL_SCREENSHOT_BODY =
        TextPair(
            "保持长按不移动，可截取远程设备屏幕。",
            "Keep holding without moving to capture the remote screen.",
        )

    val ONBOARDING_ADVANCED_EYEBROW = TextPair("进阶玩法", "ADVANCED FEATURES")
    val ONBOARDING_ADVANCED_TITLE = TextPair("更多场景，一台设备搞定", "Built for more ways to play and work")
    val ONBOARDING_ADVANCED_DESCRIPTION =
        TextPair(
            "从网络服务到独立屏幕和低延迟操控，按你的使用场景自由扩展。",
            "Extend your setup with network services, an independent display, and low-latency controls."
        )
    val ONBOARDING_PORT_FORWARD_TITLE = TextPair("端口转发", "Port forwarding")
    val ONBOARDING_PORT_FORWARD_BODY =
        TextPair(
            "为目标设备配置端口转发，直接访问设备上的网络服务。",
            "Configure forwarding for the target device and reach its network services directly."
        )
    val ONBOARDING_VIRTUAL_DISPLAY_TITLE = TextPair("虚拟屏", "Virtual display")
    val ONBOARDING_VIRTUAL_DISPLAY_BODY =
        TextPair(
            "创建独立虚拟屏运行 App 或启动器，不影响设备主屏。",
            "Run an app or launcher on an independent virtual display without disturbing the main screen."
        )
    val ONBOARDING_GAME_MODE_TITLE = TextPair("游戏模式", "Game mode")
    val ONBOARDING_GAME_MODE_BODY =
        TextPair(
            "针对高帧率画面与触控链路优化，获得更跟手的操控体验。",
            "Optimize high-frame-rate video and touch handling for more responsive controls."
        )

    val ONBOARDING_WIRELESS_EYEBROW = TextPair("灵活连接", "FLEXIBLE CONNECTIONS")
    val ONBOARDING_WIRELESS_TITLE = TextPair("有线、无线，都能快速连上", "Connect over USB or Wi-Fi")
    val ONBOARDING_WIRELESS_DESCRIPTION = TextPair(
        "从 USB 到 Android 无线调试，Screen Remote 会帮你管理不同的连接方式。",
        "From USB to Android wireless debugging, Screen Remote keeps every connection method in one place."
    )
    val ONBOARDING_PAIRING_TITLE = TextPair("无线调试配对", "Wireless debugging pairing")
    val ONBOARDING_PAIRING_BODY = TextPair(
        "直接输入配对地址与配对码，无需电脑完成首次授权。",
        "Enter the pairing address and code to authorize a device without a computer."
    )
    val ONBOARDING_DISCOVERY_TITLE = TextPair("自动发现", "Automatic discovery")
    val ONBOARDING_DISCOVERY_BODY =
        TextPair(
            "通过 mDNS 查找局域网中的可连接设备，减少手动输入。",
            "Find connectable devices on your local network through mDNS."
        )
    val ONBOARDING_ENDPOINTS_TITLE = TextPair("备用地址", "Fallback addresses")
    val ONBOARDING_ENDPOINTS_BODY = TextPair(
        "为同一会话保存多个地址，在网络变化时自动尝试下一条链路。",
        "Save multiple addresses for one session and try another route when the network changes."
    )

    val ONBOARDING_PAIRING_GUIDE_EYEBROW = TextPair("首次连接", "FIRST CONNECTION")
    val ONBOARDING_PAIRING_GUIDE_TITLE = TextPair("四步完成无线调试连接", "Connect with Wireless debugging in 4 steps")
    val ONBOARDING_PAIRING_GUIDE_DESCRIPTION = TextPair(
        "请在被控 Android 设备上完成前两步，并让两台设备连接同一 Wi-Fi。",
        "Complete the first two steps on the Android device you want to control, and keep both devices on the same Wi-Fi network."
    )
    val ONBOARDING_ENABLE_DEVELOPER_OPTIONS_TITLE = TextPair("1  启用开发者选项", "1  Enable Developer options")
    val ONBOARDING_ENABLE_DEVELOPER_OPTIONS_BODY = TextPair(
        "打开「设置 > 关于手机 > 版本信息」，连续点击「软件版本号」7 次；不同品牌的菜单名称可能略有不同。",
        "Open Settings > About phone > Version information, then tap Software version 7 times. Menu names may vary by device brand."
    )
    val ONBOARDING_OPEN_WIRELESS_DEBUGGING_TITLE = TextPair("2  打开无线调试", "2  Open Wireless debugging")
    val ONBOARDING_OPEN_WIRELESS_DEBUGGING_BODY = TextPair(
        "返回设置，在「系统管理与升级 > 开发者选项」中进入「无线调试」页面并开启开关。",
        "Return to Settings, open System management > Developer options > Wireless debugging, and turn it on."
    )
    val ONBOARDING_USE_PAIRING_CODE_TITLE = TextPair("3  使用配对码", "3  Use a pairing code")
    val ONBOARDING_USE_PAIRING_CODE_BODY = TextPair(
        "点击「使用配对码配对设备」，再到 Screen Remote 的「设置 > 使用配对码进行 ADB 配对」，输入页面显示的配对地址和 6 位配对码。",
        "Tap Pair device with pairing code. In Screen Remote, open Settings > ADB Pairing with Pairing Code and enter the pairing address and 6-digit code shown."
    )
    val ONBOARDING_ADD_WIRELESS_SESSION_TITLE = TextPair("4  添加无线会话", "4  Add a wireless session")
    val ONBOARDING_ADD_WIRELESS_SESSION_BODY = TextPair(
        "配对成功后回到主页点击「+」，打开「会话地址」，选择 mDNS 和发现到的设备，保存后即可连接。",
        "After pairing, return home and tap +. Open Session addresses, choose mDNS and the discovered device, then save and connect."
    )

    val ONBOARDING_MANAGE_EYEBROW = TextPair("设备管理", "DEVICE MANAGEMENT")
    val ONBOARDING_MANAGE_TITLE = TextPair("不只投屏，还能管理设备", "More than screen mirroring")
    val ONBOARDING_MANAGE_DESCRIPTION = TextPair(
        "连接后可直接进入管理页面，常用维护工具不必再切换应用。",
        "Open device management after connecting and keep everyday maintenance tools close at hand."
    )
    val ONBOARDING_APPS_TITLE = TextPair("应用管理", "Apps")
    val ONBOARDING_APPS_BODY =
        TextPair(
            "查看应用信息，并执行常用的应用管理操作。",
            "Inspect installed apps and run common app-management actions."
        )
    val ONBOARDING_FILES_TITLE = TextPair("文件与进程", "Files & processes")
    val ONBOARDING_FILES_BODY =
        TextPair(
            "浏览设备文件、传输内容，并查看正在运行的进程。",
            "Browse device files, transfer content, and inspect running processes."
        )
    val ONBOARDING_SHELL_TITLE = TextPair("Shell 与设备信息", "Shell & device info")
    val ONBOARDING_SHELL_BODY =
        TextPair(
            "运行常用命令，快速检查系统、显示与网络状态。",
            "Run useful commands and quickly inspect system, display, and network status."
        )

    val ONBOARDING_BACKUP_EYEBROW = TextPair("备份与恢复", "BACKUP & RESTORE")
    val ONBOARDING_BACKUP_TITLE = TextPair("换设备，也不必重新配置", "Take your setup with you")
    val ONBOARDING_BACKUP_DESCRIPTION = TextPair(
        "将关键配置导出为一个备份文件，需要时再一键恢复。",
        "Export your essential setup to one backup file and restore it whenever you need it."
    )
    val ONBOARDING_CONFIG_TITLE = TextPair("会话、分组与设置", "Sessions, groups & settings")
    val ONBOARDING_CONFIG_BODY =
        TextPair("保存会话参数、分组结构和应用偏好。", "Preserve session options, group structure, and app preferences.")
    val ONBOARDING_KEYS_TITLE = TextPair("ADB 身份", "ADB identity")
    val ONBOARDING_KEYS_BODY = TextPair(
        "备份 ADB 密钥和无线调试 TLS 身份，减少重复授权。",
        "Back up ADB keys and wireless-debugging TLS identity to avoid repeated authorization."
    )
    val ONBOARDING_RESTORE_TITLE = TextPair("JSON 备份文件", "Portable JSON backup")
    val ONBOARDING_RESTORE_BODY = TextPair(
        "使用系统文件选择器导出或导入，文件由你自行保管。",
        "Export or import with the system file picker—the backup stays under your control."
    )

    // 分组管理
    val GROUP_ALL = TextPair("主页", "Home")
    val GROUP_UNGROUPED = TextPair("未分组", "Ungrouped")
    val GROUP_MANAGE = TextPair("管理分组", "Manage Groups")
    val GROUP_ADD = TextPair("添加分组", "Add Group")
    val GROUP_EDIT = TextPair("编辑分组", "Edit Group")
    val GROUP_DELETE = TextPair("删除分组", "Delete Group")
    val GROUP_NAME = TextPair("分组名称", "Group Name")
    val GROUP_OPTION = TextPair("分组选项", "Group Option")
    val GROUP_SELECT = TextPair("选择分组", "Groups")
    val GROUP_SELECT_SINGLE = TextPair("选择分组", "Select Group")
    val GROUP_SELECTED_COUNT = TextPair("已选择 (%d)", "Selected (%d)")
    val GROUP_ALREADY_ADDED = TextPair("已添加", "Added")
    val GROUP_REMOVE = TextPair("删除", "Remove")
    val GROUP_CONFIRM_DELETE = TextPair("确认删除分组", "Confirm Delete Group")
    val GROUP_CONFIRM_DELETE_MESSAGE =
        TextPair("确定要删除分组 \"%s\" 吗？", "Are you sure you want to delete group \"%s\"?")
    val GROUP_PLACEHOLDER_NAME = TextPair("输入分组名称", "Enter group name")
    val GROUP_PLACEHOLDER_DESCRIPTION = TextPair("可选", "Optional")
    val GROUP_PARENT_PATH = TextPair("父路径", "Parent Path")
    val GROUP_PATH_PREVIEW = TextPair("完整路径预览", "Full Path Preview")
    val GROUP_SELECT_PATH = TextPair("选择路径", "Select Path")
    val GROUP_ROOT = TextPair("首页", "Home")

    // 会话对话框
    val DIALOG_CREATE_SESSION = TextPair("创建会话", "Create Session")
    val DIALOG_EDIT_SESSION = TextPair("编辑会话", "Edit Session")
    val DIALOG_SELECT_VIDEO_ENCODER = TextPair("选择视频编码器", "Select Video Encoder")
    val DIALOG_SELECT_AUDIO_ENCODER = TextPair("选择音频编码器", "Select Audio Encoder")

    // 会话对话框 - 分组标题
    val SECTION_REMOTE_DEVICE = TextPair("远程设备", "Remote Device")
    val SECTION_CONNECTION_OPTIONS = TextPair("连接选项", "Connection Options")
    val SECTION_VIDEO_CONFIG = TextPair("视频配置", "Video Config")
    val SECTION_AUDIO_CONFIG = TextPair("音频配置", "Audio Config")
    val SECTION_OTHER_OPTIONS = TextPair("其他选项", "Other Options")
    val SECTION_VIRTUAL_DISPLAY = TextPair("虚拟显示", "Virtual Display")
    val SECTION_ENCODER_OPTIONS = TextPair("编码器选项", "Encoder Options")
    val SECTION_DECODER_OPTIONS = TextPair("解码器选项", "Decoder Options")
    val SECTION_DETECTED_ENCODERS = TextPair("检测到的编码器", "Detected Encoders")
    val SECTION_DETECTED_DECODERS = TextPair("检测到的解码器", "Detected Decoders")
    val SECTION_DETECTED_AUDIO_ENCODERS = TextPair("检测到的音频编码器", "Detected Audio Encoders")

    // 会话对话框 - 标签
    val LABEL_SESSION_NAME = TextPair("会话名称", "Session Name")
    val LABEL_DEVICE_TYPE = TextPair("会话类型", "Session Type")
    val LABEL_HOST = TextPair("主机", "Host")
    val LABEL_PORT = TextPair("端口", "Port")
    val LABEL_SESSION_ADDRESS = TextPair("会话地址", "Address")
    val SESSION_ADDRESS_MULTI = TextPair("多地址", "Multiple addresses")
    val LABEL_PRIMARY_ENDPOINT = TextPair("主会话地址", "Primary Session Address")
    val LABEL_BACKUP_ENDPOINT = TextPair("备用会话地址", "Backup Session Address")
    val LABEL_MDNS_SERVICE = TextPair("mDNS 服务", "mDNS Service")
    val LABEL_MAX_SIZE = TextPair("最大尺寸", "Max Size")
    val LABEL_VIDEO_BITRATE = TextPair("视频码率", "Video Bitrate")
    val PLACEHOLDER_VIDEO_BITRATE = TextPair("留空默认 4M；如 500k、2m、4M", "Empty defaults to 4M; e.g. 500k, 2m, 4M")
    val LABEL_MAX_FPS = TextPair("最大帧率", "Max FPS")
    val LABEL_VIDEO_ENCODER = TextPair("视频编码器", "Video Encoder")
    val LABEL_VIDEO_DECODER = TextPair("视频解码器", "Video Decoder")
    val LABEL_AUDIO_BITRATE = TextPair("音频码率", "Audio Bitrate")
    val LABEL_AUDIO_ENCODER = TextPair("音频编码器", "Audio Encoder")
    val LABEL_AUDIO_DECODER = TextPair("音频解码器", "Audio Decoder")
    val LABEL_AUDIO_BUFFER = TextPair("音频缓冲", "Audio Buffer")
    val LABEL_VIDEO_BUFFER = TextPair("视频缓冲", "Video Buffer")
    val LABEL_AUDIO_VOLUME = TextPair("音量缩放", "Audio Volume")
    val LABEL_DEFAULT = TextPair("默认", "Default")
    val LABEL_ORIGINAL = TextPair("原始", "Original")
    val LABEL_CUSTOM = TextPair("自定义", "Custom")
    val LABEL_CACHED = TextPair("已缓存", "Cached")
    val LABEL_DEVICE_INFO = TextPair("设备信息", "Device Info")
    val LABEL_DEVICE_ID = TextPair("设备 ID", "Device ID")
    val LABEL_EXECUTE_COMMAND = TextPair("执行命令", "Execute command")
    val LABEL_RECEIVED_OUTPUT = TextPair("收到输出", "Received output")
    val LABEL_NEW_DISPLAY_WIDTH = TextPair("宽度", "Width")
    val LABEL_NEW_DISPLAY_HEIGHT = TextPair("高度", "Height")
    val LABEL_NEW_DISPLAY_DPI = TextPair("DPI", "DPI")
    val LABEL_NEW_DISPLAY_SIZE = TextPair("尺寸", "Size")
    val LABEL_START_APP = TextPair("启动 App", "Start App")
    val DEVICE_TYPE_TCP = TextPair("TCP", "TCP")
    val DEVICE_TYPE_USB = TextPair("USB", "USB")
    val DEVICE_TYPE_MDNS = TextPair("mDNS", "mDNS")
    val ACTION_ADD_BACKUP_ENDPOINT = TextPair("添加会话地址", "Add Session Address")
    val ACTION_REMOVE_ENDPOINT = TextPair("删除", "Remove")
    val DIALOG_SESSION_ADDRESS_TITLE = TextPair("会话地址", "Session Address")

    // 会话对话框 - 连接选项
    val SWITCH_GAME_MODE = TextPair("游戏模式", "Game Mode")
    val SWITCH_FULL_SCREEN = TextPair("全屏模式", "Full Screen")
    val SWITCH_SHOW_FLOATING_BALL = TextPair("显示悬浮球", "Show Floating Ball")
    val SWITCH_ENABLE_HARDWARE_DECODING = TextPair("启用硬件解码", "Enable Hardware Decoding")
    val SWITCH_COMPATIBILITY_MODE = TextPair("兼容模式", "Compatibility Mode")
    val LABEL_SCREEN_ROTATION = TextPair("旋转屏幕", "Screen Rotation")
    val OPTION_ROTATION_NONE = TextPair("无", "None")
    val OPTION_ROTATION_LOCAL = TextPair("本机", "Local")
    val OPTION_ROTATION_TARGET = TextPair("目标", "Target")
    val SWITCH_USE_ADB_FORWARD = TextPair("使用 ADB 转发建立连接", "Connect via ADB Forwarding")

    // 会话对话框 - 音频配置
    val SWITCH_ENABLE_AUDIO = TextPair("启用音频", "Enable Audio")

    // 会话对话框 - 其他选项
    val SWITCH_CLIPBOARD_SYNC = TextPair("粘贴板同步", "Clipboard Sync")
    val SWITCH_TURN_SCREEN_OFF = TextPair("连接后关闭远程屏幕", "Turn Screen Off")
    val SWITCH_POWER_OFF_ON_CLOSE = TextPair("断开后锁定设备", "Power Off on Close")
    val SWITCH_CLEANUP_ON_DISCONNECT = TextPair("断开后清理", "Clean Up on Disconnect")
    val SWITCH_KEEP_DEVICE_AWAKE = TextPair("保持控制端唤醒", "Keep Controller Awake")
    val SWITCH_STAY_AWAKE = TextPair("保持远程设备唤醒", "Keep Remote Device Awake")
    val SWITCH_IGNORE_VIDEO_ENCODER_CONSTRAINTS =
        TextPair("忽略视频编码器约束", "Ignore Video Encoder Constraints")

    // 会话对话框 - 虚拟屏
    val SWITCH_NEW_DISPLAY = TextPair("启动新的显示", "New Display")
    val SWITCH_VIRTUAL_DISPLAY_SYSTEM_DECORATIONS = TextPair("显示虚拟屏系统界面", "Show Virtual Display System UI")
    val SWITCH_PRESERVE_VIRTUAL_DISPLAY_CONTENT = TextPair("断开后保留虚拟屏内容", "Preserve Virtual Display Content")
    val ACTION_SYNC_LOCAL_DISPLAY_SIZE = TextPair("同步", "Sync")
    val ACTION_CLEAR_NEW_DISPLAY_SIZE = TextPair("清空", "Clear")
    val ACTION_SWAP_NEW_DISPLAY_SIZE = TextPair("交换宽高", "Swap Width/Height")
    val ACTION_SELECT_REMOTE_APP = TextPair("选择", "Select")
    val DIALOG_SELECT_REMOTE_APP = TextPair("选择远端 App", "Select Remote App")
    val PLACEHOLDER_START_APP = TextPair("远端应用包名", "Remote app package")
    val PLACEHOLDER_SEARCH_REMOTE_APP = TextPair("搜索包名", "Search package")
    val STATUS_LOADING_REMOTE_APPS = TextPair("正在读取远端应用…", "Loading remote apps…")
    val STATUS_ENTER_REMOTE_APP_QUERY =
        TextPair("输入关键字后点击 Q，或直接查询全部", "Enter a keyword and tap Q, or query all apps")
    val ACTION_QUERY_ALL_REMOTE_APPS = TextPair("全部", "All")
    val STATUS_NO_REMOTE_APPS = TextPair("未找到可启动的远端应用", "No launchable remote apps found")
    val ERROR_REMOTE_APP_LIST = TextPair("读取远端应用列表失败", "Couldn't load remote apps")

    // 会话对话框 - 状态
    val STATUS_DETECTING_VIDEO_ENCODERS = TextPair("正在检测视频编码器...", "Detecting video encoders...")
    val STATUS_DETECTING_AUDIO_ENCODERS = TextPair("正在检测音频编码器...", "Detecting audio encoders...")
    val STATUS_DETECTION_FAILED = TextPair("检测失败", "Detection failed")
    val STATUS_NO_ENCODERS_DETECTED = TextPair("未检测到编码器", "No encoders detected")
    val STATUS_NO_DECODERS_DETECTED = TextPair("未检测到解码器", "No decoders detected")
    val STATUS_NO_AUDIO_ENCODERS_DETECTED = TextPair("未检测到音频编码器", "No audio encoders detected")
    val ERROR_CANNOT_GET_CONNECTION = TextPair("无法获取设备连接", "Cannot get device connection")
    val ERROR_DETECTION_EXCEPTION = TextPair("检测异常", "Detection exception")
    val ERROR_DETECTION_FAILED = TextPair("检测失败", "Detection failed")

    // 会话对话框 - 占位符
    val PLACEHOLDER_CUSTOM_ENCODER = TextPair("自定义编码器名称", "Custom encoder name")
    val PLACEHOLDER_CUSTOM_DECODER = TextPair("自定义解码器名称", "Custom decoder name")
    val PLACEHOLDER_SEARCH_ENCODER = TextPair("搜索编码器...", "Search encoder...")
    val PLACEHOLDER_SEARCH_DECODER = TextPair("搜索解码器...", "Search decoder...")
    val PLACEHOLDER_SESSION_NAME = TextPair("可选", "Optional")
    val PLACEHOLDER_DEFAULT_ENCODER = TextPair("默认", "Default")
    val PLACEHOLDER_DEFAULT_AUDIO_ENCODER = TextPair("默认", "Default")
    val MDNS_CONNECT_SERVICES = TextPair("发现设备", "Discovered")
    val MDNS_CONNECT_SCANNING = TextPair("正在确认附近设备...", "Confirming nearby devices...")
    val MDNS_CONNECT_EMPTY = TextPair("暂未发现", "Not found")
    val MDNS_DEVICE_CONFIRMING = TextPair("正在确认", "Confirming")
    val MDNS_DEVICE_UNPAIRED = TextPair("未配对", "Not paired")
    val MDNS_PAIRING_REQUIRED = TextPair("请前往 ADB 配对页面配对", "Please pair the device on the ADB pairing page")
    val ENDPOINT_STATUS_ADB_CONNECTED = TextPair("ADB 已连接", "ADB connected")
    val ENDPOINT_STATUS_NEARBY = TextPair("附近可见", "Nearby")
    val ENDPOINT_STATUS_CONFIRMING = TextPair("正在确认设备", "Confirming device")
    val ENDPOINT_STATUS_UNAVAILABLE = TextPair("未发现", "Unavailable")

    // 编码器选择对话框
    val ENCODER_REFRESH_BUTTON = TextPair("刷新编码器", "Refresh Encoders")
    val ENCODER_ERROR_INPUT_HOST = TextPair("请先输入主机地址", "Please enter host first")

    // 帮助说明文本
    val HELP_SESSION_NAME =
        TextPair(
            "为此会话设置一个易于识别的名称，方便在会话列表中快速找到。留空则使用主机地址作为名称。",
            "Set a recognizable name for this session to quickly find it in the session list. Leave empty to use the host address as the name.",
        )
    val HELP_HOST =
        TextPair(
            "输入远程设备的主机地址，例如 IPv4、IPv6 或主机名。",
            "Enter the remote device host, such as an IPv4 address, IPv6 address, or hostname.",
        )
    val HELP_TCP_DISCOVERY =
        TextPair(
            "发现通过 _adb._tcp 广播的附近设备，选择后自动填写主机和端口。",
            "Discover nearby devices advertised through _adb._tcp and fill in the host and port.",
        )
    val HELP_USB_SERIAL =
        TextPair(
            "输入 USB 设备序列号，也可以点击选择已连接的 USB 设备。",
            "Enter the USB device serial number, or choose a connected USB device.",
        )
    val HELP_MDNS_SERVICE =
        TextPair(
            "输入 mDNS 服务名，也可以从发现设备中选择。",
            "Enter the mDNS service name, or choose one from discovered devices.",
        )
    val HELP_DEVICE_TYPE =
        TextPair(
            "选择 ADB transport 类型。TCP 使用 ip:port，USB 使用 usb:serial，mDNS 使用 mdns:service。",
            "Select the ADB transport type. TCP uses ip:port, USB uses usb:serial, and mDNS uses mdns:service.",
        )
    val HELP_CONNECTION_ENDPOINTS =
        TextPair(
            "同一个会话可以配置多个会话地址。TCP 地址必须把 IP 和端口作为整体填写，例如 192.168.1.2:5555 或 [fd00::1]:5555。",
            "A session can contain multiple session addresses. A TCP address must include host and port as one value, for example 192.168.1.2:5555 or [fd00::1]:5555.",
        )
    val HELP_PORT =
        TextPair(
            "远程设备的 ADB 端口号，默认为 5555。如果使用 ADB 转发建立连接，此端口会被自动设置。",
            "The ADB port number of the remote device, default is 5555. If ADB forwarding is used to establish the connection, this port will be set automatically.",
        )
    val HELP_SELECT_GROUP =
        TextPair(
            "将会话添加到一个或多个分组中，便于管理和查找。可以在主页面通过分组筛选会话。",
            "Add the session to one or more groups for easier management and search. You can filter sessions by group on the home page.",
        )

    // 连接选项
    val HELP_GAME_MODE =
        TextPair(
            "面向实时游戏优化触控与视频调度。开启后，最大尺寸、视频码率和最大帧率使用独立的低延迟档位；连接成功后暂停日志记录和 mDNS 后台发现。悬浮球与帧率面板仍按各自开关显示。",
            "Optimize touch and video scheduling for real-time games. Max size, video bitrate, and max FPS use independent low-latency presets. Logging and background mDNS discovery are paused after connection. The floating ball and performance stats remain controlled by their own switches.",
        )
    val HELP_GAME_MODE_FULL_SCREEN_DISABLED =
        TextPair(
            "游戏模式不支持全屏模式。开启游戏模式后会关闭并禁用基于 TextureView 的全屏渲染。",
            "Full-screen mode is unavailable in game mode. Enabling game mode disables the TextureView-based full-screen renderer.",
        )
    val HELP_USE_FULL_SCREEN =
        TextPair(
            "启用后使用 TextureView 渲染，支持真全屏（隐藏导航栏）和后台运行（不会被系统杀死），但延迟略高。关闭则使用 SurfaceView，延迟更低但不支持真全屏（导航栏仍显示），切换到后台时需要使用虚拟 Surface 方案保持连接。两种模式都可能因屏幕比例不同而出现黑边。",
            "When enabled, uses TextureView for rendering, supporting true fullscreen (hide navigation bar) and background running (won't be killed by system), but with slightly higher latency. When disabled, uses SurfaceView with lower latency but no true fullscreen support (navigation bar remains visible), requiring virtual Surface solution to maintain connection when switching to background. Both modes may have black bars due to different screen aspect ratios.",
        )
    val HELP_SHOW_FLOATING_BALL =
        TextPair(
            "在远程控制页面显示悬浮球。点击打开快捷菜单；拖动可调整位置；长按后向上、下、左、右滑动，分别执行桌面、展开通知栏、返回、后台任务；持续长按可截取远程设备屏幕。",
            "Show the floating ball on the remote control screen. Tap to open the quick menu; drag to reposition it; press and slide up, down, left, or right for Home, notifications, Back, or Recents; keep holding to capture the remote screen.",
        )
    val HELP_ENABLE_HARDWARE_DECODING =
        TextPair(
            "使用硬件解码器解码视频，可以降低 CPU 占用和发热，但部分设备可能不支持或有兼容性问题。",
            "Use hardware decoder to decode video, which can reduce CPU usage and heat, but some devices may not support it or have compatibility issues.",
        )
    val HELP_COMPATIBILITY_MODE =
        TextPair(
            "通过 ADB 截图显示低帧率画面，并使用 ADB 命令控制设备；可绕过 scrcpy 编码兼容问题，但不支持音频，且延迟更高。",
            "Uses low-frame-rate ADB screenshots and ADB input commands. This bypasses scrcpy encoding issues, but audio is unavailable and latency is higher.",
        )
    val HELP_COMPATIBILITY_REQUIRES_SCRCPY =
        TextPair(
            "兼容模式会跳过 scrcpy-server，此选项不可用。",
            "Compatibility mode skips scrcpy-server, so this option is unavailable.",
        )
    val HELP_FOLLOW_ORIENTATION =
        TextPair(
            "无：不做特殊处理。本机：目标设备跟随本机旋转。目标：本机跟随目标设备旋转。",
            "None: no special handling. Local: the target device follows this device. Target: this device follows the target device.",
        )
    val HELP_USE_ADB_FORWARD =
        TextPair(
            "默认关闭。关闭时优先使用基于优化 DADB 驱动的多路复用直连服务流，通常延迟更低、链路更简单；开启后将通过 ADB 端口转发建立 scrcpy 连接，适合兼容性或排障场景。",
            "Off by default. When disabled, the optimized DADB transport uses direct multiplexed service streams for a simpler, lower-latency path. When enabled, scrcpy connects through ADB port forwarding for compatibility or troubleshooting.",
        )

    // 视频配置
    val HELP_GAME_MAX_SIZE =
        TextPair(
            "游戏模式可选 720、1080、1920。尺寸与码率互不联动。",
            "Game mode offers 720, 1080, and 1920. Size and bitrate are configured independently.",
        )
    val HELP_GAME_VIDEO_BITRATE =
        TextPair(
            "游戏模式可选 1M、2M、4M、8M、12M、20M，空值默认 2M，为高帧率复杂画面保留码率余量。调整码率不会自动修改最大尺寸。",
            "Game mode offers 1M, 2M, 4M, 8M, 12M, and 20M, with 2M as the empty-value default, to preserve bitrate headroom for complex high-FPS scenes. Changing bitrate does not change max size.",
        )
    val HELP_GAME_MAX_FPS =
        TextPair(
            "游戏模式仅允许 60、90、120 fps，最低为 60 fps。实际帧率仍受被控设备、编码器和显示刷新率限制。",
            "Game mode allows only 60, 90, and 120 fps, with a minimum of 60 fps. Actual FPS still depends on the remote device, encoder, and display refresh rate.",
        )
    val HELP_NORMAL_MAX_SIZE =
        TextPair(
            "在数值方块内上下滑动，可选 720、1080、1920、原始和自定义。原始表示不限制最大尺寸；停在自定义后会自动要求输入，未输入则回到原始。",
            "Swipe vertically inside the value box to choose 720, 1080, 1920, Original, or Custom. Original applies no max-size limit. Stopping on Custom opens an editor; leaving it empty returns to Original.",
        )
    val HELP_COMPATIBILITY_QUALITY =
        TextPair(
            "兼容模式按 maxSize 采样：0/原始表示不缩放。1~99 表示采样比例百分比（1 即 1%，99 即 99%），100 或空值也视作原始不缩放。可输入任意整数，超过 100 按像素上限采样。图像始终按长宽比例等比缩放，不会放大到超过原始尺寸。当前实现中 jpegQuality 同步按 maxSize 映射。",
            "Compatibility mode samples by maxSize: 0/Original means no scaling. Values 1-99 map directly to sample ratio percentage (1 means 1%, 99 means 99%). A value of 100 or empty means original size. Any integer is accepted; values above 100 are treated as max-pixel limits. Scaling keeps aspect ratio and does not upscale beyond source size. jpegQuality is currently mapped directly from maxSize.",
        )
    val HELP_NORMAL_VIDEO_BITRATE =
        TextPair(
            "在数值方块内上下滑动，可选 8M、12M 和自定义。停在自定义后自动弹出输入框；未输入则保留之前的码率。",
            "Swipe vertically inside the value box to choose 8M, 12M, or Custom. Stopping on Custom opens an editor; leaving it empty keeps the previous bitrate.",
        )
    val HELP_NORMAL_MAX_FPS =
        TextPair(
            "在数值方块内上下滑动，可选 15、30、60、90、120 和自定义。停在自定义后自动弹出输入框；未输入则保留之前的帧率。",
            "Swipe vertically inside the value box to choose 15, 30, 60, 90, 120, or Custom. Stopping on Custom opens an editor; leaving it empty keeps the previous FPS.",
        )
    val HELP_MAX_SIZE =
        TextPair(
            "限制视频宽、高两边的最大像素值（通常等同于限制长边）。留空使用设备原始分辨率。较低的分辨率可以减少带宽占用和延迟。",
            "Limit both video dimensions to this maximum pixel value (normally the long-edge limit). Leave empty for native resolution. Lower values reduce bandwidth and latency.",
        )
    val HELP_VIDEO_BITRATE =
        TextPair(
            "视频编码的码率，影响画质和带宽占用。留空默认使用 4M（4Mbps）。支持单位：M（兆）、K（千）；例如 2M 可进一步节省带宽，720K 适合低带宽。",
            "Video encoding bitrate affects quality and bandwidth usage. Leave empty to use the 4M (4Mbps) default. Supported units: M (mega) and K (kilo); for example, 2M uses less bandwidth and 720K suits low-bandwidth connections.",
        )
    val HELP_MAX_FPS =
        TextPair(
            "限制视频的最大帧率。默认 60 fps。较低的帧率可以减少 CPU 占用和带宽。示例：30 表示每秒 30 帧。",
            "Limit the maximum video frame rate. Default is 60 fps. Lower values reduce CPU and bandwidth usage. Example: 30 means 30 frames per second.",
        )
    val HELP_VIDEO_BUFFER =
        TextPair(
            "视频缓冲时间（毫秒）。增加缓冲可平滑网络抖动，但会增加延迟。常见值：0（实时）、33（1帧）、50-100（平滑播放）。留空使用 0ms。",
            "Video buffer time (milliseconds). Increase buffer to smooth network jitter, but adds latency. Common values: 0 (realtime), 33 (1 frame), 50-100 (smooth playback). Leave empty for 0ms.",
        )
    val HELP_VIDEO_ENCODER =
        TextPair(
            "选择设备上的硬件或软件编码器。不同编码器的性能和画质可能有差异。留空使用默认编码器。点击可检测设备支持的编码器。",
            "Select hardware or software encoder on the device. Different encoders may have different performance and quality. Leave empty to use default encoder. Click to detect supported encoders.",
        )
    val HELP_VIDEO_DECODER =
        TextPair(
            "选择本机的视频解码器。优先选择硬件解码器以降低延迟和功耗。留空使用系统默认解码器。\n\n推荐配置（按优先级）：\n1. 硬件 + 低延迟 + C2架构\n2. 硬件 + 低延迟 + OMX\n3. 硬件 + C2架构\n4. 硬件 + OMX",
            "Select video decoder on this device. Hardware decoders are preferred for lower latency and power consumption. Leave empty to use system default decoder.\n\nRecommended (by priority):\n1. Hardware + Low Latency + C2\n2. Hardware + Low Latency + OMX\n3. Hardware + C2\n4. Hardware + OMX",
        )

    // 音频配置
    val HELP_ENABLE_AUDIO =
        TextPair(
            "启用音频传输。需要设备支持音频捕获（Android 11+）。音频传输会增加带宽占用。",
            "Enable audio transmission. Requires device to support audio capture (Android 11+). Audio transmission will increase bandwidth usage.",
        )
    val HELP_COMPATIBILITY_AUDIO_DISABLED =
        TextPair(
            "兼容模式没有音频输出，音频传输会保持关闭。",
            "Compatibility mode has no audio output, so audio transmission remains disabled.",
        )
    val HELP_AUDIO_BITRATE_PICKER =
        TextPair(
            "在数值方块内上下滑动，可选 128K、192K、256K 和自定义。停在自定义后自动弹出输入框；未输入则保留之前的音频码率。",
            "Swipe vertically inside the value box to choose 128K, 192K, 256K, or Custom. Stopping on Custom opens an editor; leaving it empty keeps the previous audio bitrate.",
        )
    val HELP_AUDIO_ENCODER =
        TextPair(
            "选择设备上的音频编码器。留空使用默认编码器。点击可检测设备支持的音频编码器。",
            "Select audio encoder on the device. Leave empty to use default encoder. Click to detect supported audio encoders.",
        )
    val HELP_AUDIO_DECODER =
        TextPair(
            "选择本机的音频解码器。留空使用系统默认解码器。",
            "Select audio decoder on this device. Leave empty to use system default decoder.",
        )
    val HELP_AUDIO_BITRATE =
        TextPair(
            "音频编码码率，影响音质和带宽占用。常见值：128k（标准）、192k（高质量）、256k（极高质量）。留空使用默认值。",
            "Audio encoding bitrate, affects quality and bandwidth. Common values: 128k (standard), 192k (high quality), 256k (very high quality). Leave empty for default.",
        )
    val HELP_AUDIO_BUFFER =
        TextPair(
            "音频缓冲时间（毫秒）。根据编码格式自动计算：Opus/AAC 默认 50ms，FLAC 默认 120ms。留空使用自动值。",
            "Audio buffer time (milliseconds). Auto-calculated by codec: Opus/AAC default 50ms, FLAC default 120ms. Leave empty for auto.",
        )
    val HELP_AUDIO_VOLUME =
        TextPair(
            "调整音频播放音量的缩放倍数。1.0x 为原始音量，小于 1.0 降低音量，大于 1.0 提高音量（可能失真）。",
            "Adjust audio playback volume scale. 1.0x is original volume, less than 1.0 reduces volume, greater than 1.0 increases volume (may distort).",
        )

    // 其他选项
    val HELP_CLIPBOARD_SYNC =
        TextPair(
            "与远程设备自动同步剪贴板内容；兼容模式使用内置同步方式。关闭后仍可使用文本输入和主动粘贴，但不会自动交换剪贴板。",
            "Automatically synchronize clipboard contents with the remote device; compatibility mode uses the built-in sync path. When disabled, text input and explicit paste still work, but clipboards are not exchanged automatically.",
        )
    val HELP_TURN_SCREEN_OFF =
        TextPair(
            "连接成功后立即关闭远程设备的屏幕显示，但镜像画面仍然传输。适合需要隐私或省电的场景。",
            "Turn off the remote device screen immediately after connection, but mirroring continues. Suitable for privacy or power saving scenarios.",
        )
    val HELP_POWER_OFF_ON_CLOSE =
        TextPair(
            "断开连接时自动锁定远程设备屏幕（相当于按电源键）。此功能依赖 scrcpy 清理进程，因此关闭“断开后清理”时不可用。",
            "Automatically lock the remote device screen when disconnecting (equivalent to pressing power button). This requires the scrcpy cleanup process, so it is unavailable when cleanup on disconnect is disabled.",
        )
    val HELP_CLEANUP_ON_DISCONNECT =
        TextPair(
            "断开连接时恢复 scrcpy 启动期间修改的远程设备状态。关闭后会保留可复用的连接资源，减少下次连接的准备时间。",
            "Restore remote-device state changed during scrcpy startup when disconnecting. Disabling this keeps reusable connection resources and reduces preparation time for the next connection.",
        )
    val HELP_KEEP_DEVICE_AWAKE =
        TextPair(
            "远控期间保持本机（控制端）屏幕常亮，防止控制端自动息屏。此选项不会修改远程设备的唤醒设置。",
            "Keep this controller's screen on during remote control. This option does not change the remote device's stay-awake setting.",
        )
    val HELP_STAY_AWAKE =
        TextPair(
            "连接期间让远程设备在充电时保持唤醒。此功能依赖 scrcpy 清理进程，因此关闭“断开后清理”时不可用。杀掉控制端后台后，只要远端连接随之结束，清理进程通常仍会恢复原设置。",
            "Keep the remote device awake while it is plugged in during the connection. This requires the scrcpy cleanup process, so it is unavailable when cleanup on disconnect is disabled. If the controller app is killed, the previous setting is normally restored once the remote connection ends.",
        )
    val HELP_IGNORE_VIDEO_ENCODER_CONSTRAINTS =
        TextPair(
            "跳过 scrcpy 对视频编码器尺寸和对齐限制的自动修正。仅在设备编码器能力被错误识别时启用，启用后可能导致 server 启动或编码失败。",
            "Skip scrcpy's automatic video encoder size and alignment constraints. Enable only when encoder capabilities are detected incorrectly; this may cause server startup or encoding failures.",
        )

    // 虚拟屏
    val HELP_NEW_DISPLAY =
        TextPair(
            "在远端设备上创建一个独立的虚拟屏幕，可用来运行指定 App 或启动器，不影响主屏幕。启动 App 和尺寸均可留空。\n\nscrcpy 虚拟显示说明：\nhttps://github.com/Genymobile/scrcpy/blob/master/doc/virtual-display.md\n\n可选启动器示例：\nhttps://f-droid.org/en/packages/org.fossify.home/",
            "Create an independent virtual screen on the remote device for an app or launcher without affecting the main screen. The start app and size are both optional.\n\nscrcpy virtual display guide:\nhttps://github.com/Genymobile/scrcpy/blob/master/doc/virtual-display.md\n\nOptional launcher example:\nhttps://f-droid.org/en/packages/org.fossify.home/",
        )
    val HELP_VIRTUAL_DISPLAY_SYSTEM_DECORATIONS =
        TextPair(
            "推荐开启。此选项不只控制状态栏和导航栏，还会影响虚拟屏内的系统导航与按键处理能力。开启后，虚拟屏内的 App 能自行处理主页、最近任务和返回等导航按键；关闭后，这些按键可能落到真机主屏。仅在明确不需要虚拟屏系统界面，并能接受设备相关的导航限制时关闭。",
            "Recommended. This option affects system navigation and key handling inside the virtual display, not only status and navigation bars. When enabled, apps on the virtual display can handle navigation keys such as Home, Recents, and Back themselves; when disabled, these keys may fall back to the physical device's main display. Disable only when virtual-display system UI is intentionally unwanted and device-specific navigation limitations are acceptable.",
        )
    val HELP_PRESERVE_VIRTUAL_DISPLAY_CONTENT =
        TextPair(
            "开启后，关闭虚拟显示时不会销毁其中运行的 App，而是将其移动到真机主屏。",
            "Keep apps running when the virtual display closes by moving them to the physical device's main display instead of destroying them.",
        )
}

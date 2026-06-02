package com.screen.remote.android.core.i18n

/**
 * 会话管理相关文本
 */
object SessionTexts {
    // 主页面
    val MAIN_TITLE_SESSIONS = TextPair("Scrcpy Sessions", "Scrcpy Sessions")
    val MAIN_TAB_SESSIONS = TextPair("会话", "Sessions")
    val MAIN_TAB_ACTIONS = TextPair("自动化", "Actions")
    val MAIN_ADD_SESSION = TextPair("添加会话", "Add Session")
    val MAIN_ADD_ACTION = TextPair("添加自动化", "Add Action")

    // 会话列表
    val SESSION_NO_SESSIONS = TextPair("没有 Scrcpy Sessions", "No Scrcpy Sessions")
    val SESSION_CLICK_TO_CONNECT = TextPair("点击连接", "Tap to Connect")
    val SESSION_CONNECTED = TextPair("已连接", "Connected")
    val SESSION_CONFIRM_DELETE = TextPair("确认删除", "Confirm Delete")
    val SESSION_CONFIRM_DELETE_MESSAGE = TextPair("确定要删除会话 \"%s\" 吗？", "Are you sure you want to delete session \"%s\"?")
    val SESSION_DELETE = TextPair("删除", "Delete")
    val SESSION_CANCEL = TextPair("取消", "Cancel")
    val SESSION_URL_COPIED = TextPair("URL 已复制", "URL Copied")
    val SESSION_EDIT = TextPair("编辑会话", "Edit Session")
    val SESSION_DELETE_SESSION = TextPair("删除会话", "Delete Session")
    val SESSION_CONNECT = TextPair("连接会话", "Connect Session")
    val SESSION_COPY = TextPair("复制会话", "Copy Session")
    val SESSION_MANAGE = TextPair("管理功能", "Manage")
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
    val ONBOARDING_SESSION_DESCRIPTION =
        TextPair("每个会话都能保存独立的画面与声音设置，连接不同设备时无需反复调整。", "Each session keeps its own video and audio settings, so every device reconnects just the way you like it.")
    val ONBOARDING_RESOLUTION_TITLE = TextPair("分辨率", "Resolution")
    val ONBOARDING_RESOLUTION_BODY =
        TextPair("保持原始画质，或限制为 1080p、720p 来降低延迟。", "Keep native quality, or cap it at 1080p or 720p for lower latency.")
    val ONBOARDING_FPS_TITLE = TextPair("帧率与码率", "Frame rate & bitrate")
    val ONBOARDING_FPS_BODY =
        TextPair("在 60 fps 的顺滑体验和更低的带宽占用之间自由取舍。", "Balance fluid 60 fps motion against lower bandwidth usage.")
    val ONBOARDING_CODEC_TITLE = TextPair("音视频编解码", "Audio & video codecs")
    val ONBOARDING_CODEC_BODY =
        TextPair("可选择视频与音频格式，也能指定设备端编码器和本机解码器。", "Choose audio and video formats, plus the encoder on the device and decoder on this phone.")

    val ONBOARDING_WIRELESS_EYEBROW = TextPair("灵活连接", "FLEXIBLE CONNECTIONS")
    val ONBOARDING_WIRELESS_TITLE = TextPair("有线、无线，都能快速连上", "Connect over USB or Wi-Fi")
    val ONBOARDING_WIRELESS_DESCRIPTION =
        TextPair("从 USB 到 Android 无线调试，Screen Remote 会帮你管理不同的连接方式。", "From USB to Android wireless debugging, Screen Remote keeps every connection method in one place.")
    val ONBOARDING_PAIRING_TITLE = TextPair("无线调试配对", "Wireless debugging pairing")
    val ONBOARDING_PAIRING_BODY =
        TextPair("直接输入配对地址与配对码，无需电脑完成首次授权。", "Enter the pairing address and code to authorize a device without a computer.")
    val ONBOARDING_DISCOVERY_TITLE = TextPair("自动发现", "Automatic discovery")
    val ONBOARDING_DISCOVERY_BODY =
        TextPair("通过 mDNS 查找局域网中的可连接设备，减少手动输入。", "Find connectable devices on your local network through mDNS.")
    val ONBOARDING_ENDPOINTS_TITLE = TextPair("备用地址", "Fallback addresses")
    val ONBOARDING_ENDPOINTS_BODY =
        TextPair("为同一会话保存多个地址，在网络变化时自动尝试下一条链路。", "Save multiple addresses for one session and try another route when the network changes.")

    val ONBOARDING_MANAGE_EYEBROW = TextPair("设备管理", "DEVICE MANAGEMENT")
    val ONBOARDING_MANAGE_TITLE = TextPair("不只投屏，还能管理设备", "More than screen mirroring")
    val ONBOARDING_MANAGE_DESCRIPTION =
        TextPair("连接后可直接进入管理页面，常用维护工具不必再切换应用。", "Open device management after connecting and keep everyday maintenance tools close at hand.")
    val ONBOARDING_APPS_TITLE = TextPair("应用管理", "Apps")
    val ONBOARDING_APPS_BODY =
        TextPair("查看应用信息，并执行常用的应用管理操作。", "Inspect installed apps and run common app-management actions.")
    val ONBOARDING_FILES_TITLE = TextPair("文件与进程", "Files & processes")
    val ONBOARDING_FILES_BODY =
        TextPair("浏览设备文件、传输内容，并查看正在运行的进程。", "Browse device files, transfer content, and inspect running processes.")
    val ONBOARDING_SHELL_TITLE = TextPair("Shell 与设备信息", "Shell & device info")
    val ONBOARDING_SHELL_BODY =
        TextPair("运行常用命令，快速检查系统、显示与网络状态。", "Run useful commands and quickly inspect system, display, and network status.")

    val ONBOARDING_BACKUP_EYEBROW = TextPair("备份与恢复", "BACKUP & RESTORE")
    val ONBOARDING_BACKUP_TITLE = TextPair("换设备，也不必重新配置", "Take your setup with you")
    val ONBOARDING_BACKUP_DESCRIPTION =
        TextPair("将关键配置导出为一个备份文件，需要时再一键恢复。", "Export your essential setup to one backup file and restore it whenever you need it.")
    val ONBOARDING_CONFIG_TITLE = TextPair("会话、分组与设置", "Sessions, groups & settings")
    val ONBOARDING_CONFIG_BODY =
        TextPair("保存会话参数、分组结构和应用偏好。", "Preserve session options, group structure, and app preferences.")
    val ONBOARDING_KEYS_TITLE = TextPair("ADB 身份", "ADB identity")
    val ONBOARDING_KEYS_BODY =
        TextPair("备份 ADB 密钥和无线调试 TLS 身份，减少重复授权。", "Back up ADB keys and wireless-debugging TLS identity to avoid repeated authorization.")
    val ONBOARDING_RESTORE_TITLE = TextPair("JSON 备份文件", "Portable JSON backup")
    val ONBOARDING_RESTORE_BODY =
        TextPair("使用系统文件选择器导出或导入，文件由你自行保管。", "Export or import with the system file picker—the backup stays under your control.")

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
    val GROUP_CONFIRM_DELETE_MESSAGE = TextPair("确定要删除分组 \"%s\" 吗？", "Are you sure you want to delete group \"%s\"?")
    val GROUP_PLACEHOLDER_NAME = TextPair("输入分组名称", "Enter group name")
    val GROUP_PLACEHOLDER_DESCRIPTION = TextPair("可选", "Optional")
    val GROUP_PARENT_PATH = TextPair("父路径", "Parent Path")
    val GROUP_PATH_PREVIEW = TextPair("完整路径预览", "Full Path Preview")
    val GROUP_SELECT_PATH = TextPair("选择路径", "Select Path")
    val GROUP_ROOT = TextPair("首页", "Home")
    val GROUP_TYPE = TextPair("分组类型", "Group Type")

    // 自动化页面
    val ACTIONS_NO_ACTIONS = TextPair("没有自动化", "No Actions")
    val ACTIONS_EMPTY_HINT =
        TextPair(
            "点击右上角 + 按钮创建新的 Scrcpy Action。\nAction 用于启动 Scrcpy 会话并自动执行自定义动作。",
            "Tap the + button in the top right to create a new Scrcpy Action.\nActions are used to start Scrcpy sessions and automatically execute custom operations.",
        )

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
    val LABEL_PRIMARY_ENDPOINT = TextPair("主会话地址", "Primary Session Address")
    val LABEL_BACKUP_ENDPOINT = TextPair("备用会话地址", "Backup Session Address")
    val LABEL_MDNS_SERVICE = TextPair("mDNS 服务", "mDNS Service")
    val LABEL_MAX_SIZE = TextPair("最大尺寸", "Max Size")
    val LABEL_VIDEO_BITRATE = TextPair("视频码率", "Video Bitrate")
    val PLACEHOLDER_VIDEO_BITRATE = TextPair("留空默认 4M；如 500k、2m、4M", "Empty defaults to 4M; e.g. 500k, 2m, 4M")
    val LABEL_MAX_FPS = TextPair("最大帧率", "Max FPS")
    val LABEL_VIDEO_CODEC = TextPair("视频编码格式", "Video Codec Format")
    val LABEL_VIDEO_ENCODER = TextPair("视频编码器", "Video Encoder")
    val LABEL_VIDEO_DECODER = TextPair("视频解码器", "Video Decoder")
    val LABEL_AUDIO_CODEC = TextPair("音频编码格式", "Audio Codec Format")
    val LABEL_AUDIO_ENCODER = TextPair("音频编码器", "Audio Encoder")
    val LABEL_AUDIO_DECODER = TextPair("音频解码器", "Audio Decoder")
    val LABEL_AUDIO_BITRATE = TextPair("音频码率", "Audio Bitrate")
    val LABEL_AUDIO_BUFFER = TextPair("音频缓冲", "Audio Buffer")
    val LABEL_VIDEO_BUFFER = TextPair("视频缓冲", "Video Buffer")
    val LABEL_AUDIO_VOLUME = TextPair("音量缩放", "Audio Volume")
    val LABEL_DEFAULT = TextPair("默认", "Default")
    val LABEL_CACHED = TextPair("已缓存", "Cached")
    val LABEL_DEVICE_INFO = TextPair("设备信息", "Device Info")
    val LABEL_DEVICE_ID = TextPair("设备 ID", "Device ID")
    val LABEL_EXECUTE_COMMAND = TextPair("执行命令", "Execute command")
    val LABEL_RECEIVED_OUTPUT = TextPair("收到输出", "Received output")
    val LABEL_NEW_DISPLAY_WIDTH = TextPair("宽度", "Width")
    val LABEL_NEW_DISPLAY_HEIGHT = TextPair("高度", "Height")
    val LABEL_NEW_DISPLAY_DPI = TextPair("DPI", "DPI")
    val DEVICE_TYPE_TCP = TextPair("TCP", "TCP")
    val DEVICE_TYPE_USB = TextPair("USB", "USB")
    val DEVICE_TYPE_MDNS = TextPair("mDNS", "mDNS")
    val ACTION_ADD_BACKUP_ENDPOINT = TextPair("添加会话地址", "Add Session Address")
    val ACTION_REMOVE_ENDPOINT = TextPair("删除", "Remove")
    val DIALOG_SESSION_ADDRESS_TITLE = TextPair("会话地址", "Session Address")

    // 会话对话框 - 开关
    val SWITCH_FORCE_ADB = TextPair("强制使用 ADB 转发连接", "Force ADB Forward")
    val SWITCH_ENABLE_AUDIO = TextPair("启用音频", "Enable Audio")
    val SWITCH_ENABLE_CLIPBOARD_SYNC = TextPair("启用剪贴板同步", "Enable Clipboard Sync")
    val SWITCH_STAY_AWAKE = TextPair("保持唤醒", "Stay Awake")
    val SWITCH_TURN_SCREEN_OFF = TextPair("连接后关闭远程屏幕", "Turn Screen Off")
    val SWITCH_POWER_OFF_ON_CLOSE = TextPair("断开后锁定远程屏幕(按电源键)", "Power Off on Close")
    val SWITCH_NO_CLEANUP_ON_DISCONNECT = TextPair("断开后不清理（保持屏幕状态）", "Don't Clean Up on Disconnect")
    val SWITCH_FULL_SCREEN = TextPair("全屏模式", "Full Screen")
    val SWITCH_KEEP_DEVICE_AWAKE = TextPair("使用期间保持设备唤醒", "Keep Device Awake")
    val SWITCH_ENABLE_HARDWARE_DECODING = TextPair("启用硬件解码", "Enable Hardware Decoding")
    val SWITCH_IGNORE_VIDEO_ENCODER_CONSTRAINTS =
        TextPair("忽略视频编码器约束", "Ignore Video Encoder Constraints")
    val SWITCH_FOLLOW_ORIENTATION = TextPair("跟随设备旋转变化", "Follow Remote Orientation Change")
    val SWITCH_NEW_DISPLAY = TextPair("启动新的显示", "New Display")
    val ACTION_SYNC_LOCAL_DISPLAY_SIZE = TextPair("同步本机尺寸", "Sync Local Size")
    val ACTION_SWAP_NEW_DISPLAY_SIZE = TextPair("交换宽高", "Swap Width/Height")

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
    val MDNS_CONNECT_SCANNING = TextPair("正在扫描可连接设备...", "Scanning for connectable devices...")
    val MDNS_CONNECT_EMPTY = TextPair("无", "None")
    val MDNS_DEVICE_UNPAIRED = TextPair("未配对", "Not paired")
    val MDNS_PAIRING_REQUIRED = TextPair("请前往 ADB 配对页面配对", "Please pair the device on the ADB pairing page")

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
            "远程设备的 ADB 端口号，默认为 5555。如果使用 ADB 转发连接，此端口会被自动设置。",
            "The ADB port number of the remote device, default is 5555. If using ADB forward connection, this port will be set automatically.",
        )
    val HELP_SELECT_GROUP =
        TextPair(
            "将会话添加到一个或多个分组中，便于管理和查找。可以在主页面通过分组筛选会话。",
            "Add the session to one or more groups for easier management and search. You can filter sessions by group on the home page.",
        )
    val HELP_FORCE_ADB =
        TextPair(
            "默认关闭。关闭时优先使用基于优化 DADB 驱动的 ADB 多路复用直连服务流，通常延迟更低、链路更简单；只有在兼容性或排障需要时，才建议开启并强制回退到 ADB 转发连接。",
            "Off by default. When disabled, it prefers direct ADB stream multiplexing based on the optimized DADB transport, which is usually simpler and lower-latency. Enable it only for compatibility or troubleshooting to force fallback to ADB forward.",
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
    val HELP_VIDEO_CODEC =
        TextPair(
            "支持 H264、H265、AV1、VP9 和 VP8。H264 兼容性最好；H265/AV1 压缩效率更高；VP8/VP9 可用于部分不支持 HEVC 的设备。连接时会按远端编码器与本机解码器的真实 MIME 能力校验。",
            "Supports H264, H265, AV1, VP9, and VP8. H264 is the most compatible; H265/AV1 are more efficient; VP8/VP9 cover devices without HEVC. The connection validates real remote-encoder and local-decoder MIME capabilities.",
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
    val HELP_USE_FULL_SCREEN =
        TextPair(
            "启用后使用 TextureView 渲染，支持真全屏（隐藏导航栏）和后台运行（不会被系统杀死），但延迟略高。关闭则使用 SurfaceView，延迟更低但不支持真全屏（导航栏仍显示），切换到后台时需要使用虚拟 Surface 方案保持连接。两种模式都可能因屏幕比例不同而出现黑边。",
            "When enabled, uses TextureView for rendering, supporting true fullscreen (hide navigation bar) and background running (won't be killed by system), but with slightly higher latency. When disabled, uses SurfaceView with lower latency but no true fullscreen support (navigation bar remains visible), requiring virtual Surface solution to maintain connection when switching to background. Both modes may have black bars due to different screen aspect ratios.",
        )
    val HELP_ENABLE_AUDIO =
        TextPair(
            "启用音频传输。需要设备支持音频捕获（Android 11+）。音频传输会增加带宽占用。",
            "Enable audio transmission. Requires device to support audio capture (Android 11+). Audio transmission will increase bandwidth usage.",
        )
    val HELP_AUDIO_CODEC =
        TextPair(
            "选择音频编码格式。AAC 兼容性最好，Opus 压缩率更高，FLAC 无损但占用大，RAW 未压缩。",
            "Select audio codec format. AAC has best compatibility, Opus has better compression, FLAC is lossless but large, RAW is uncompressed.",
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
    val HELP_VIDEO_BUFFER =
        TextPair(
            "视频缓冲时间（毫秒）。增加缓冲可平滑网络抖动，但会增加延迟。常见值：0（实时）、33（1帧）、50-100（平滑播放）。留空使用 0ms。",
            "Video buffer time (milliseconds). Increase buffer to smooth network jitter, but adds latency. Common values: 0 (realtime), 33 (1 frame), 50-100 (smooth playback). Leave empty for 0ms.",
        )
    val HELP_AUDIO_VOLUME =
        TextPair(
            "调整音频播放音量的缩放倍数。1.0x 为原始音量，小于 1.0 降低音量，大于 1.0 提高音量（可能失真）。",
            "Adjust audio playback volume scale. 1.0x is original volume, less than 1.0 reduces volume, greater than 1.0 increases volume (may distort).",
        )
    val HELP_STAY_AWAKE =
        TextPair(
            "连接期间保持远程设备屏幕常亮，防止自动息屏。断开连接后恢复原设置。",
            "Keep the remote device screen on during connection to prevent auto sleep. Restores original setting after disconnection.",
        )
    val HELP_ENABLE_CLIPBOARD_SYNC =
        TextPair(
            "允许 scrcpy 与远程设备同步剪贴板内容。关闭后仍可使用文本输入，但不会自动交换剪贴板。",
            "Allow scrcpy to synchronize clipboard contents with the remote device. When disabled, text input still works, but clipboards are not exchanged automatically.",
        )
    val HELP_TURN_SCREEN_OFF =
        TextPair(
            "连接成功后立即关闭远程设备的屏幕显示，但镜像画面仍然传输。适合需要隐私或省电的场景。",
            "Turn off the remote device screen immediately after connection, but mirroring continues. Suitable for privacy or power saving scenarios.",
        )
    val HELP_POWER_OFF_ON_CLOSE =
        TextPair(
            "断开连接时自动锁定远程设备屏幕（相当于按电源键）。适合远程控制后需要锁屏的场景。",
            "Automatically lock the remote device screen when disconnecting (equivalent to pressing power button). Suitable for scenarios requiring screen lock after remote control.",
        )
    val HELP_NO_CLEANUP_ON_DISCONNECT =
        TextPair(
            "断开连接时不恢复 scrcpy 启动期间修改的远程设备状态，例如保持当前屏幕状态。对应 cleanup=false。",
            "Do not restore remote device state changed during scrcpy startup when disconnecting, keeping the current screen state. Maps to cleanup=false.",
        )
    val HELP_KEEP_DEVICE_AWAKE =
        TextPair(
            "使用期间保持本地设备（控制端）屏幕常亮，防止自动息屏导致连接中断。",
            "Keep the local device (controller) screen on during use to prevent connection interruption due to auto sleep.",
        )
    val HELP_ENABLE_HARDWARE_DECODING =
        TextPair(
            "使用硬件解码器解码视频，可以降低 CPU 占用和发热，但部分设备可能不支持或有兼容性问题。",
            "Use hardware decoder to decode video, which can reduce CPU usage and heat, but some devices may not support it or have compatibility issues.",
        )
    val HELP_IGNORE_VIDEO_ENCODER_CONSTRAINTS =
        TextPair(
            "跳过 scrcpy 对视频编码器尺寸和对齐限制的自动修正。仅在设备编码器能力被错误识别时启用，启用后可能导致 server 启动或编码失败。",
            "Skip scrcpy's automatic video encoder size and alignment constraints. Enable only when encoder capabilities are detected incorrectly; this may cause server startup or encoding failures.",
        )
    val HELP_FOLLOW_ORIENTATION =
        TextPair(
            "自动跟随远程设备的屏幕旋转方向。关闭后本地画面方向保持固定。",
            "Automatically follow the remote device's screen rotation. When turned off, the local screen orientation remains fixed.",
        )
    val HELP_NEW_DISPLAY =
        TextPair(
            "在远程设备上创建一个新的虚拟显示器进行镜像，而不是镜像主屏幕。宽高和 DPI 留空时使用远程主屏幕默认值。",
            "Create a new virtual display on the remote device for mirroring instead of mirroring the main screen. Leave width, height, and DPI empty to use the remote main display defaults.",
        )
}

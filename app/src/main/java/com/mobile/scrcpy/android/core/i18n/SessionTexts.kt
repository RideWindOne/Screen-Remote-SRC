package com.mobile.scrcpy.android.core.i18n

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
    val SESSION_DELETE = TextPair("删除", "Delete")
    val SESSION_CANCEL = TextPair("取消", "Cancel")
    val SESSION_URL_COPIED = TextPair("URL 已复制", "URL Copied")
    val SESSION_EDIT = TextPair("编辑会话", "Edit Session")
    val SESSION_DELETE_SESSION = TextPair("删除会话", "Delete Session")
    val SESSION_CONNECT = TextPair("连接会话", "Connect Session")
    val SESSION_COPY = TextPair("复制会话", "Copy Session")
    val SESSION_EMPTY_HINT =
        TextPair(
            "点击右上角 + 按钮开始新的 scrcpy 会话。\n会话会保存在此处以便快速访问。",
            "Tap the + button in the top right to start a new scrcpy session.\nSessions will be saved here for quick access.",
        )
    val SESSION_SAVE_BUTTON = TextPair("保存会话", "Save Session")
    val SESSION_ADD = TextPair("添加会话", "Add Session")
    val SESSION_SAVE = TextPair("保存", "Save")

    // 分组管理
    val GROUP_ALL = TextPair("主页", "Home")
    val GROUP_UNGROUPED = TextPair("未分组", "Ungrouped")
    val GROUP_MANAGE = TextPair("管理分组", "Manage Groups")
    val GROUP_ADD = TextPair("添加分组", "Add Group")
    val GROUP_EDIT = TextPair("编辑分组", "Edit Group")
    val GROUP_DELETE = TextPair("删除分组", "Delete Group")
    val GROUP_NAME = TextPair("分组名称", "Group Name")
    val GROUP_OPTION = TextPair("分组选项", "Group Option")
    val GROUP_SELECT = TextPair("选择分组", "Select Groups")
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
    val SECTION_DETECTED_ENCODERS = TextPair("检测到的编码器", "Detected Encoders")
    val SECTION_DETECTED_AUDIO_ENCODERS = TextPair("检测到的音频编码器", "Detected Audio Encoders")

    // 会话对话框 - 标签
    val LABEL_SESSION_NAME = TextPair("会话名称", "Session Name")
    val LABEL_HOST = TextPair("主机", "Host")
    val LABEL_PORT = TextPair("端口", "Port")
    val LABEL_MAX_SIZE = TextPair("最大尺寸", "Max Size")
    val LABEL_BITRATE = TextPair("码率", "Bitrate")
    val LABEL_MAX_FPS = TextPair("最大帧率", "Max FPS")
    val LABEL_KEY_FRAME_INTERVAL = TextPair("关键帧间隔", "Key Frame Interval")
    val LABEL_VIDEO_CODEC = TextPair("视频编码", "Video Codec")
    val LABEL_VIDEO_ENCODER = TextPair("视频编码器", "Video Encoder")
    val LABEL_AUDIO_CODEC = TextPair("音频编码", "Audio Codec")
    val LABEL_AUDIO_ENCODER = TextPair("音频编码器", "Audio Encoder")
    val LABEL_AUDIO_VOLUME = TextPair("音量缩放", "Audio Volume")
    val LABEL_DEFAULT_ENCODER = TextPair("默认编码器", "Default Encoder")
    val LABEL_TEST_AUDIO_DECODER = TextPair("测试音频解码器", "Test Audio Decoder")
    val LABEL_DEVICE_INFO = TextPair("设备信息", "Device Info")
    val LABEL_DEVICE_ID = TextPair("设备 ID", "Device ID")
    val LABEL_EXECUTE_COMMAND = TextPair("执行命令", "Execute command")
    val LABEL_RECEIVED_OUTPUT = TextPair("收到输出", "Received output")

    // 会话对话框 - 开关
    val SWITCH_FORCE_ADB = TextPair("强制使用 ADB 转发连接", "Force ADB Forward")
    val SWITCH_ENABLE_AUDIO = TextPair("启用音频", "Enable Audio")
    val SWITCH_STAY_AWAKE = TextPair("保持唤醒", "Stay Awake")
    val SWITCH_TURN_SCREEN_OFF = TextPair("连接后关闭远程屏幕", "Turn Screen Off")
    val SWITCH_POWER_OFF_ON_CLOSE = TextPair("断开后锁定远程屏幕(按电源键)", "Power Off on Close")
    val SWITCH_FULL_SCREEN = TextPair("全屏模式", "Full Screen")
    val SWITCH_KEEP_DEVICE_AWAKE = TextPair("使用期间保持设备唤醒", "Keep Device Awake")
    val SWITCH_ENABLE_HARDWARE_DECODING = TextPair("启用硬件解码", "Enable Hardware Decoding")
    val SWITCH_FOLLOW_ORIENTATION = TextPair("跟随设备旋转变化", "Follow Remote Orientation Change")
    val SWITCH_NEW_DISPLAY = TextPair("启动新的显示", "New Display")

    // 会话对话框 - 状态
    val STATUS_DETECTING_VIDEO_ENCODERS = TextPair("正在检测视频编码器...", "Detecting video encoders...")
    val STATUS_DETECTING_AUDIO_ENCODERS = TextPair("正在检测音频编码器...", "Detecting audio encoders...")
    val STATUS_DETECTION_FAILED = TextPair("检测失败", "Detection failed")
    val STATUS_NO_ENCODERS_DETECTED = TextPair("未检测到编码器", "No encoders detected")
    val STATUS_NO_AUDIO_ENCODERS_DETECTED = TextPair("未检测到音频编码器", "No audio encoders detected")
    val ERROR_CANNOT_GET_CONNECTION = TextPair("无法获取设备连接", "Cannot get device connection")
    val ERROR_DETECTION_EXCEPTION = TextPair("检测异常", "Detection exception")
    val ERROR_DETECTION_FAILED = TextPair("检测失败", "Detection failed")

    // 会话对话框 - 占位符
    val PLACEHOLDER_CUSTOM_ENCODER = TextPair("自定义编码器名称", "Custom encoder name")
    val PLACEHOLDER_SEARCH_ENCODER = TextPair("搜索编码器...", "Search encoder...")
    val PLACEHOLDER_SESSION_NAME = TextPair("可选", "Optional")
    val PLACEHOLDER_DEFAULT_ENCODER = TextPair("默认编码器", "Default Encoder")
    val PLACEHOLDER_DEFAULT_AUDIO_ENCODER = TextPair("默认", "Default")

    // 编码器选择对话框
    val ENCODER_FILTER_ALL = TextPair("全部", "All")
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
            "输入远程设备的 IP 地址。可以输入 'usb' 快速选择 USB 连接的设备。",
            "Enter the IP address of the remote device. You can type 'usb' to quickly select a USB-connected device.",
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
            "强制使用 ADB 转发连接而不是直接 TCP 连接。适用于无法直接访问设备网络的情况。",
            "Force using ADB forward connection instead of direct TCP connection. Useful when the device network is not directly accessible.",
        )
    val HELP_MAX_SIZE =
        TextPair(
            "限制视频的最大分辨率（短边像素）。留空使用设备原始分辨率。较低的分辨率可以减少带宽占用和延迟。示例：720 表示 720p。",
            "Limit the maximum video resolution (short side pixels). Leave empty to use device's native resolution. Lower resolution can reduce bandwidth and latency. Example: 720 for 720p.",
        )
    val HELP_BITRATE =
        TextPair(
            "视频编码的码率，影响画质和带宽占用。支持单位：M（兆）、K（千）。示例：8M 表示 8Mbps，适合高清画质；4M 适合标清；720K 适合低带宽。",
            "Video encoding bitrate, affects quality and bandwidth usage. Supported units: M (mega), K (kilo). Example: 8M for 8Mbps (HD quality); 4M for SD; 720K for low bandwidth.",
        )
    val HELP_MAX_FPS =
        TextPair(
            "限制视频的最大帧率。默认 30 fps。较低的帧率可以减少 CPU 占用和带宽。示例：15 表示 15 帧每秒。",
            "Limit the maximum video frame rate. Default is 30 fps. Lower frame rate can reduce CPU usage and bandwidth. Example: 15 for 15 frames per second.",
        )
    val HELP_KEY_FRAME_INTERVAL =
        TextPair(
            "关键帧间隔越短，画面状态更新越密，操作反馈越即时、越跟手，但编码和带宽压力越大。",
            "Shorter key frame interval means more frequent screen updates, more responsive and smoother control, but higher encoding and bandwidth cost.",
        )
    val HELP_VIDEO_CODEC =
        TextPair(
            "选择视频编码格式。H264 兼容性最好，H265 压缩率更高但需要设备支持，AV1 是最新标准但兼容性较差。",
            "Select video codec format. H264 has best compatibility, H265 has better compression but requires device support, AV1 is the latest standard but has poor compatibility.",
        )
    val HELP_VIDEO_ENCODER =
        TextPair(
            "选择设备上的硬件或软件编码器。不同编码器的性能和画质可能有差异。留空使用默认编码器。点击可检测设备支持的编码器。",
            "Select hardware or software encoder on the device. Different encoders may have different performance and quality. Leave empty to use default encoder. Click to detect supported encoders.",
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
    val HELP_FOLLOW_ORIENTATION =
        TextPair(
            "自动跟随远程设备的屏幕旋转方向。关闭后本地画面方向保持固定。",
            "Automatically follow the remote device's screen rotation. When turned off, the local screen orientation remains fixed.",
        )
    val HELP_NEW_DISPLAY =
        TextPair(
            "在远程设备上创建一个新的虚拟显示器进行镜像，而不是镜像主屏幕。适合需要独立显示内容的场景。",
            "Create a new virtual display on the remote device for mirroring instead of mirroring the main screen. Suitable for scenarios requiring independent display content.",
        )
}

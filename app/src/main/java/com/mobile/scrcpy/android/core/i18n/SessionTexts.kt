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
    val SESSION_EMPTY_HINT = TextPair(
        "点击右上角 + 按钮开始新的 scrcpy 会话。\n会话会保存在此处以便快速访问。",
        "Tap the + button in the top right to start a new scrcpy session.\nSessions will be saved here for quick access."
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
    val GROUP_DESCRIPTION = TextPair("分组描述", "Group Description")
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
    val ACTIONS_EMPTY_HINT = TextPair(
        "点击右上角 + 按钮创建新的 Scrcpy Action。\nAction 用于启动 Scrcpy 会话并自动执行自定义动作。",
        "Tap the + button in the top right to create a new Scrcpy Action.\nActions are used to start Scrcpy sessions and automatically execute custom operations."
    )
    
    // 会话对话框
    val DIALOG_CREATE_SESSION = TextPair("创建会话", "Create Session")
    val DIALOG_EDIT_SESSION = TextPair("编辑会话", "Edit Session")
    val DIALOG_SELECT_VIDEO_ENCODER = TextPair("选择视频编码器", "Select Video Encoder")
    val DIALOG_SELECT_AUDIO_ENCODER = TextPair("选择音频编码器", "Select Audio Encoder")
    
    // 会话对话框 - 分组标题
    val SECTION_REMOTE_DEVICE = TextPair("远程设备", "Remote Device")
    val SECTION_CONNECTION_OPTIONS = TextPair("连接选项", "Connection Options")
    val SECTION_ADB_SESSION_OPTIONS = TextPair("ADB 会话选项", "ADB Session Options")
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
    val SWITCH_USE_FULL_SCREEN = TextPair("使用全屏模式 (TextureView)", "Use Full Screen (TextureView)")
    val SWITCH_KEEP_DEVICE_AWAKE = TextPair("使用期间保持设备唤醒", "Keep Device Awake")
    val SWITCH_ENABLE_HARDWARE_DECODING = TextPair("启用硬件解码", "Enable Hardware Decoding")
    val SWITCH_FOLLOW_ORIENTATION = TextPair("跟随设备旋转变化", "Follow Remote Orientation Change")
    val SWITCH_NEW_DISPLAY = TextPair("启动新的显示", "New Display")
    
    // 会话对话框 - 提示
    val HINT_EMPTY_USE_DEVICE_RESOLUTION = TextPair("留空使用设备分辨率 示例: 720", "Empty for device resolution Example: 720")
    
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
    val PLACEHOLDER_BITRATE = TextPair("16M、4M 或 720K", "16M、4M or 720K")
    val PLACEHOLDER_MAX_FPS = TextPair("默认 30 示例：15", "Default 30 Example：15")
    val PLACEHOLDER_DEFAULT_ENCODER = TextPair("默认编码器", "Default Encoder")
    val PLACEHOLDER_DEFAULT_AUDIO_ENCODER = TextPair("默认", "Default")
    
    // 编码器选择对话框
    val ENCODER_FILTER_ALL = TextPair("全部", "All")
    val ENCODER_REFRESH_BUTTON = TextPair("刷新编码器", "Refresh Encoders")
    val ENCODER_ERROR_INPUT_HOST = TextPair("请先输入主机地址", "Please enter host first")
}

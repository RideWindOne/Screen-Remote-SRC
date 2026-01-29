package com.mobile.scrcpy.android.core.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.scrcpy.android.BuildConfig
import com.mobile.scrcpy.android.core.common.util.ApiCompatHelper

// ============ 颜色常量 ============
object AppColors {
    // ========== 浅色模式 ==========
    /** iOS 蓝色 - 用于按钮、链接等 */
    val iOSBlue = Color(0xFF007AFF)

    /** 分隔线颜色 */
    val divider = Color(0xFFBBBBBB)

    /** Dialog 背景色 */
    val dialogBackground = Color(0xFFECECEC)

    /** 标题栏背景色 */
    val headerBackground = Color(0xFFE7E7E7)

    /** 分组标题文字颜色 */
    val sectionTitleText = Color(0xFF6E6E73)

    /** 副标题/提示文字颜色 */
    val subtitleText = Color(0xFF959595)

    /** 错误颜色 */
    val error = Color(0xFFFF3B30)

    /** 箭头颜色 */
    val arrow = Color(0xFFE5E5EA)

    /** iOS 风格选中背景色（浅灰） */
    val iOSSelectedBackground = Color(0xFFE8E8E8)

    /** 白色背景 */
    val white = Color.White

    /** 黑色文字 */
    val black = Color.Black

    /** 浅色模式 - DropdownMenu 背景（纯白，带阴影形成浮起效果） */
    val lightDropdownBackground = Color(0xFFFFFFFF)

    // ========== 深色模式 ==========

    /** 深色模式 - 页面背景（最外层） */
    val darkBackground = Color(0xFF121212)

    /** 深色模式 - 卡片/横条背景 */
    val darkCard = Color(0xFF1E1E1E)

    /** 深色模式 - Dialog 背景（比卡片更亮，形成浮起效果） */
    val darkDialogBackground = Color(0xFF2C2C2E)

    /** 深色模式 - Dialog 标题栏背景 */
    val darkDialogHeader = Color(0xFF3A3A3C)

    /** 深色模式 - DropdownMenu 背景（与 Dialog 同级，形成浮起效果） */
    val darkDropdownBackground = Color(0xFF2C2C2E)

    /** 深色模式 - 主文字 */
    val darkTextPrimary = Color(0xFFEDEDED)

    /** 深色模式 - 副文字/说明 */
    val darkTextSecondary = Color(0xFFB3B3B3)

    /** 深色模式 - 禁用/次要信息 */
    val darkTextDisabled = Color(0x61FFFFFF) // rgba(255,255,255,0.38)

    /** 深色模式 - 分割线 */
    val darkDivider = Color(0xFF2C2C2C)

    /** 深色模式 - 图标/箭头 */
    val darkIcon = Color(0xFF8A8A8A)

    /** 深色模式 - Switch 开启状态 */
    val darkSwitchOn = Color(0xFF4CAF50)

    /** 深色模式 - Switch 关闭状态轨道 */
    val darkSwitchOffTrack = Color(0xFF5A5A5A)

    /** 深色模式 - Switch 关闭状态圆点 */
    val darkSwitchOffThumb = Color(0xFFBDBDBD)

    /** 深色模式 - iOS 风格选中背景色 */
    val darkIOSSelectedBackground = Color(0xFF3A3A3C)

    /** 深色模式 - 主按钮 */
    val darkButtonPrimary = Color(0xFF1E88E5)

    /** 深色模式 - 次按钮 */
    val darkButtonSecondary = Color(0xFF3A3A3A)

    /** 深色模式 - 不可点击按钮 */
    val darkButtonDisabled = Color(0xFF5A5A5A)
}

// ============ 尺寸常量 ============
object AppDimens {
    // 窗口尺寸
    /** Dialog 窗口宽度比例 */
    const val WINDOW_WIDTH_RATIO = 0.95f

    /** Dialog 窗口最大高度比例（相对屏幕高度） */
    const val WINDOW_MAX_HEIGHT_RATIO = 0.8f

    /** 窗口圆角 */
    val windowCornerRadius = 8.dp

    // 组件高度

    /** 分组标题高度 */
    val sectionTitleHeight = 35.dp

    /** 列表项高度 */
    val listItemHeight = 38.dp

    /** 主题选项高度 */
    val themeOptionHeight = 43.dp

    // 间距

    /** 卡片间距 */
    val cardSpacing = 10.dp

    /** 标准内边距 */
    val paddingStandard = 10.dp

    /** 标准间距 */
    val spacingStandard = 10.dp

    /** 水平内边距 */
    val paddingHorizontal = 10.dp

    /** 垂直内边距 */
    val paddingVertical = 10.dp

    // 卡片

    /** 卡片圆角 */
    val cardCornerRadius = 8.dp

    // 其他

    /** 标签宽度 */
    val labelWidth = 100.dp

    /** 音量文字宽度 */
    val volumeTextWidth = 50.dp

    /** 音量标签宽度 */
    val volumeLabelWidth = 80.dp
}

// ============ 文字大小常量 ============
object AppTextSizes {
    /** 分组标题 */
    val sectionTitle = 13.sp

    /** 列表项 */
    val listItem = 15.sp

    /** 标题 */
    val title = 17.sp

    /** 正文 */
    val body = 15.sp

    /** 副标题 */
    val subtitle = 14.sp

    /** 小字 */
    val caption = 13.sp
}

// ============ 网络常量 ============
object NetworkConstants {
    /** 默认 ADB 端口（字符串） */
    const val DEFAULT_ADB_PORT = "5555"

    /** 默认 ADB 端口（整数） */
    const val DEFAULT_ADB_PORT_INT = 5555

    /** 本地回环地址 */
    const val LOCALHOST = "127.0.0.1"

    /** 连接超时时间（毫秒） */
    const val CONNECT_TIMEOUT_MS = 5000L

    /** 读取超时时间（毫秒） */
    const val READ_TIMEOUT_MS = 10000L

    /** Socket 等待超时（毫秒） */
    const val SOCKET_WAIT_TIMEOUT_MS = 5000L

    /** Socket 等待重试次数 */
    const val SOCKET_WAIT_RETRIES = 10
}

// ============ ADB 配对常量 ============
object AdbPairingConstants {
    /** 配对码长度 */
    const val PAIRING_CODE_LENGTH = 6

    /** 配对超时时间（毫秒） */
    const val PAIRING_TIMEOUT_MS = 30000L

    /** IP 地址正则表达式 */
    const val IP_ADDRESS_REGEX = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"

    /** 端口号范围 */
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
}

// ============ Scrcpy 常量 ============
object ScrcpyConstants {
    // 视频编码

    /** 默认视频编码格式 */
    const val DEFAULT_VIDEO_CODEC = "h264"

    /**
     * 支持的视频编码格式（根据 API 级别动态获取）
     * 使用 ApiCompatHelper.getSupportedVideoCodecs() 获取当前设备支持的编解码器
     */
    val VIDEO_CODECS: List<String>
        get() = ApiCompatHelper.getSupportedVideoCodecs()

    /**
     * 默认最大屏幕尺寸（推荐值）
     * 1080 更适合移动设备间的镜像，平衡性能和清晰度
     * 此值仅用于创建新会话时的预填充，不是运行时默认值
     */
    const val DEFAULT_MAX_SIZE = 1080

    /** 默认最大屏幕尺寸（字符串，用于 UI 预填充） */
    const val DEFAULT_MAX_SIZE_STR = "1080"

    /** 默认码率（整数，单位：bps） */
    const val DEFAULT_BITRATE_INT = 8000000 // 8Mbps

    /** 默认码率（字符串） */
    const val DEFAULT_BITRATE = "4M"

    /** 默认帧率 */
    const val DEFAULT_MAX_FPS = 60

    /** 默认显示 ID */
    const val DEFAULT_DISPLAY_ID = 0

    /** 默认编码器配置 */
    const val DEFAULT_CODEC_OPTIONS = "profile=1,level=52,intra-refresh-period=0"

    // 音频编码

    /** 默认音频编码格式 */
    const val DEFAULT_AUDIO_CODEC = "aac"

    /** 支持的音频编码格式 */
    val AUDIO_CODECS = listOf("opus", "aac", "flac", "raw")

    /** 默认音频码率（单位：bps） */
    const val DEFAULT_AUDIO_BITRATE = 128000 // 128kbps

    /** 默认音量 */
    const val DEFAULT_AUDIO_VOLUME = 1.0f

    /** 最小音量 */
    const val MIN_AUDIO_VOLUME = 0.1f

    /** 最大音量 */
    const val MAX_AUDIO_VOLUME = 2.0f

    /** 音量调节步数 */
    const val AUDIO_VOLUME_STEPS = 18

    // 连接参数

    /** 默认连接超时（毫秒） */
    const val DEFAULT_CONNECT_TIMEOUT = 5000L

    /** Socket 读取超时（毫秒） */
    const val SOCKET_READ_TIMEOUT = 10000L

    /** 默认重连延迟（毫秒） */
    const val DEFAULT_RECONNECT_DELAY = 2000L

    /** 最大重连次数 */
    const val MAX_RECONNECT_ATTEMPTS = 3

    /** 本地转发端口 */
    const val LOCAL_FORWARD_PORT = 27183

    // 解码器参数

    /** 解码器输入缓冲区超时（微秒） */
    const val DECODER_INPUT_TIMEOUT_US = 10000L

    /** 解码器输出缓冲区超时（微秒） */
    const val DECODER_OUTPUT_TIMEOUT_US = 10000L

    /** PTS 时间单位转换（微秒转毫秒） */
    const val PTS_TO_MS_DIVISOR = 1000L

    // 手势参数

    /** 短按阈值（毫秒） - 小于此时间视为点击 */
    const val TAP_THRESHOLD = 200L

    /** 长按阈值 1（毫秒） - 显示辅助工具 */
    const val LONG_PRESS_THRESHOLD_1 = 1000L

    /** 长按阈值 2（毫秒） - 预留功能 */
    const val LONG_PRESS_THRESHOLD_2 = 2000L

    /** 滑动阈值（像素） */
    const val SWIPE_THRESHOLD = 100f

    /** 滑动最小距离（像素） - 用于判断是否为有效滑动 */
    const val SWIPE_MIN_DISTANCE = 50f

    // 菜单位置参数

    /** 菜单位置判断阈值（屏幕高度百分比） */
    const val MENU_POSITION_THRESHOLD = 0.3f

    // 震动反馈参数

    /** 短震动时长（毫秒） */
    const val HAPTIC_FEEDBACK_SHORT = 10L

    /** 中等震动时长（毫秒） */
    const val HAPTIC_FEEDBACK_MEDIUM = 20L

    /** 长震动时长（毫秒） */
    const val HAPTIC_FEEDBACK_LONG = 50L
}

// ============ 应用常量 ============
object AppConstants {
    /** 应用版本 - 自动从 BuildConfig 获取 */
    val APP_VERSION: String
        get() = BuildConfig.APP_VERSION

    /** Scrcpy 版本 */
    const val SCRCPY_VERSION = "3.3.4"

    /** Scrcpy Server 路径 */
    const val SCRCPY_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"

    /** Telegram 频道链接 */
    const val TELEGRAM_CHANNEL = "https://t.me/joinchat/I_HBlFpB27RkZTRl"

    /** GitHub 仓库链接 */
    const val GITHUB_REPO = "https://github.com/XRSec/scrcpy-mobile"

    /** GitHub Issues 链接 */
    const val GITHUB_ISSUES = "https://github.com/XRSec/scrcpy-mobile/issues"

    /** WakeLock 超时时间（毫秒） - 10小时 */
    const val WAKELOCK_TIMEOUT_MS = 10L * 60 * 60 * 1000

    /** StateFlow 订阅超时（毫秒） */
    const val STATEFLOW_SUBSCRIBE_TIMEOUT_MS = 5000L

    /** 进程 ID 起始值 */
    const val PROCESS_ID_START = 10000
}

// ============ 文件路径常量 ============
object FilePathConstants {
    /** 默认文件传输路径 */
    const val DEFAULT_FILE_TRANSFER_PATH = "/sdcard/Download"

    /** 快速选择路径列表 */
    val QUICK_SELECT_PATHS =
        listOf(
            "/sdcard/Download",
            "/sdcard/DCIM",
            "/sdcard/Documents",
            "/sdcard/Pictures",
            "/sdcard/Music",
            "/sdcard/Movies",
        )
}

// ============ UI 常量 ============
object UIConstants {
    /** 隐藏输入框的偏移量（dp） */
    const val HIDDEN_INPUT_OFFSET = -1000

    /** 日志输出间隔（每 N 帧输出一次） */
    const val LOG_FRAME_INTERVAL = 100

    /** 初始日志输出帧数 */
    const val LOG_INITIAL_FRAMES = 5
}

// ============ 会话颜色常量 ============
object SessionColors {
    /** 默认会话颜色 */
    const val DEFAULT_COLOR = "BLUE"
}

// ============ 占位符文本 ============
object PlaceholderTexts {
    // 示例值（不需要翻译）
    const val HOST = "192.168.1.5、USB"
    const val PORT = "5555"
}

// ============ 日志标签常量 ============
object LogTags {
    // 核心组件
    const val APP = "ScrcpyMobile"
    const val ADB_MANAGER = "AdbConnectionManager"
    const val ADB_CONNECTION = "AdbConnection"
    const val ADB_BRIDGE = "AdbBridge"
    const val ADB_KEEP_ALIVE_SERVICE = "AdbKeepAliveService"
    const val ADB_PAIRING = "AdbPairing"
    const val USB_CONNECTION = "UsbConnection"

    // Scrcpy 客户端
    const val SCRCPY_CLIENT = "ScrcpyClient"
    const val SCRCPY_SERVICE = "ScrcpyService"
    const val SCRCPY_SERVER = "ScrcpyServer"

    // 媒体解码
    const val VIDEO_DECODER = "VideoDecoder"
    const val AUDIO_DECODER = "AudioDecoder"
    const val ENCODE = "Encode"
    const val AAC_ENCODE = "AacEncode"
    const val H264_ENCODE = "H264Encode"
    const val H265_ENCODE = "H265Encode"
    const val OPUS_ENCODE = "OpusEncode"
    const val CODEC_TEST_SCREEN = "CodecTestScreen"

    // UI 组件
    const val SCREEN_REMOTE_APP = "ScreenRemoteApp"
    const val REMOTE_DISPLAY = "RemoteDisplayScreen"
    const val SESSION_DIALOG = "SessionDialog"
    const val MAIN_SCREEN = "MainScreen"
    const val MAIN_VIEW_MODEL = "MainViewModel"

    // ViewModels
    const val SESSION_VM = "SessionViewModel"
    const val GROUP_VM = "GroupViewModel"
    const val CONNECTION_VM = "ConnectionViewModel"
    const val CONTROL_VM = "ControlViewModel"
    const val ADB_KEYS_VM = "AdbKeysViewModel"
    const val SETTINGS_VM = "SettingsViewModel"

    // 输入处理
    const val TOUCH_HANDLER = "TouchHandler"
    const val CONTROL_HANDLER = "ControlHandler"
    const val CIRCLE_MENU = "CircleMenu"
    const val FLOATING_CONTROLLER = "FloatingController"
    const val FLOATING_CONTROLLER_MSG = "FloatingControllerMsg" // 重要消息（显示/隐藏/归位等）

    // 工具类
    const val LOG_MANAGER = "LogManager"
    const val TTS_MANAGER = "TTSManager"
    const val LOGCAT_CAPTURE = "LogcatCapture"
}

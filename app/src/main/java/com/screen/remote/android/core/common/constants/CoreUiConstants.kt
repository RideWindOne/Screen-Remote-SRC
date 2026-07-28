package com.screen.remote.android.core.common.constants

import android.annotation.SuppressLint
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.remote.android.BuildConfig

/**
 * ADB 配对常量
 */
object AdbPairingConstants {
    const val PAIRING_CODE_LENGTH = 6
    const val PAIRING_TIMEOUT_MS = 30000L
    const val IP_ADDRESS_REGEX = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
}

/**
 * 应用常量
 */
object AppConstants {
    const val APP_VERSION = BuildConfig.APP_VERSION
    const val SCRCPY_VERSION = BuildConfig.SCRCPY_VERSION
    const val SCRCPY_SERVER_SHA256 = BuildConfig.SCRCPY_SERVER_SHA256
    const val TELEMETRY_BASE_URL = BuildConfig.TELEMETRY_BASE_URL
    const val SCRCPY_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"
    const val TELEGRAM_CHANNEL = "https://t.me/joinchat/I_HBlFpB27RkZTRl"
    const val GITHUB_REPO = "https://github.com/XRSec/Screen-Remote"
    const val GITHUB_ISSUES = "https://github.com/XRSec/Screen-Remote/issues"
    const val GITHUB_USER_GUIDE = "https://github.com/XRSec/Screen-Remote/wiki/User-Documentation"
    const val WAKELOCK_TIMEOUT_MS = 10L * 60 * 60 * 1000
    const val STATEFLOW_SUBSCRIBE_TIMEOUT_MS = 5000L
    const val PROCESS_ID_START = 10000
}

/**
 * 应用尺寸常量
 */
object AppDimens {
    const val WINDOW_WIDTH_RATIO = 0.95f
    const val WINDOW_WIDTH_SMALL_RATIO = 0.85f
    const val WINDOW_MAX_HEIGHT_RATIO = 0.8f

    val windowCornerRadius = IosDesignTokens.dialogCornerRadius
    val sectionTitleHeight = IosDesignTokens.sectionTitleHeight
    val listItemHeight = IosDesignTokens.formRowHeight
    val themeOptionHeight = IosDesignTokens.themeOptionHeight
    val cardSpacing = IosDesignTokens.compactSpacing
    val paddingStandard = IosDesignTokens.compactHorizontalPadding
    val spacingStandard = IosDesignTokens.compactHorizontalPadding
    val paddingHorizontal = IosDesignTokens.compactHorizontalPadding
    val paddingVertical = IosDesignTokens.compactHorizontalPadding
    val cardCornerRadius = IosDesignTokens.cardCornerRadius
    val labelWidth = 100.dp
    val volumeTextWidth = 50.dp
    val volumeLabelWidth = 80.dp
}

/**
 * 应用文字大小常量
 */
object AppTextSizes {
    val sectionTitle = 13.sp
    val listItem = 15.sp
    val title = 17.sp
    val body = 15.sp
    val subtitle = 14.sp
    val caption = 13.sp
}

/**
 * 文件路径常量
 */
@SuppressLint("SdCardPath")
object FilePathConstants {
    const val DEVICE_SCREENSHOT_DIR = "/sdcard/Pictures"
    const val APP_ICONS_DIR = "app_icons"
}

/**
 * iOS 风格设计 token。
 */
object IosDesignTokens {
    val formRowHeight = 40.dp
    val sectionTitleHeight = 35.dp
    val segmentedControlHeight = 38.dp
    val themeOptionHeight = 43.dp

    val dialogHeaderHeight = 50.dp
    val dialogHeaderHorizontalPadding = 8.dp
    val dialogActionSlotWidth = 48.dp
    val dialogHeaderSpacerHeight = 8.dp
    val dialogCompactHeaderSpacerHeight = 4.dp
    val dialogBottomSpacerHeight = 16.dp
    val dialogCompactBottomSpacerHeight = 8.dp

    val dialogCornerRadius = 8.dp
    val cardCornerRadius = 12.dp
    val compactCornerRadius = 8.dp
    val segmentedControlContainerCornerRadius = 15.dp
    val segmentedControlChipCornerRadius = 13.dp
    val searchFieldCornerRadius = 8.dp

    val compactHorizontalPadding = 10.dp
    val standardHorizontalPadding = 16.dp
    val compactSpacing = 12.dp
    val standardSpacing = 16.dp
    val compactInlineSpacing = 6.dp

    val fieldContentStartPadding = 8.dp
    val fieldContentEndPadding = 12.dp

    val helpIconSize = 16.dp
    val trailingIconSize = 16.dp
    val externalIconSize = 18.dp

    val dialogTrailingActionWidth = 160.dp
    val dialogLabelMaxWidth = 132.dp
    val dialogLabelTextMaxWidth = 108.dp

    const val dividerAlpha = 0.3f
    const val dialogHeaderBackgroundAlpha = 0.5f
    const val disabledActionAlpha = 0.3f
}

/**
 * 网络常量
 */
object NetworkConstants {
    const val DEFAULT_ADB_PORT = "5555"
    const val DEFAULT_ADB_PORT_INT = 5555
    const val LOCALHOST = "127.0.0.1"
    const val CONNECT_TIMEOUT_MS = 5000L
    const val READ_TIMEOUT_MS = 10000L
    const val SOCKET_WAIT_TIMEOUT_MS = 5000L
    const val SOCKET_WAIT_RETRIES = 10
    const val SOCKET_RECEIVE_BUFFER_SIZE = 64 * 1024
    const val SOCKET_SEND_BUFFER_SIZE = 64 * 1024
}

/**
 * 占位符文本
 */
object PlaceholderTexts {
    const val HOST = "192.168.1.5、USB"
    const val PORT = "5555"
}

/**
 * 会话颜色常量
 */
object SessionColors {
    const val DEFAULT_COLOR = "BLUE"
}

/**
 * UI 常量
 */
object UIConstants {
    const val HIDDEN_INPUT_OFFSET = -1000
    const val LOG_FRAME_INTERVAL = 100
    const val LOG_INITIAL_FRAMES = 5
}

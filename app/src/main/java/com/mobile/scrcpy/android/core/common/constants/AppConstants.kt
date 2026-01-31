package com.mobile.scrcpy.android.core.common.constants

import com.mobile.scrcpy.android.BuildConfig

/**
 * 应用常量
 * 包含版本信息、路径、链接、超时等配置
 */
object AppConstants {
    /** 应用版本 - 自动从 BuildConfig 获取 */
    const val APP_VERSION = BuildConfig.APP_VERSION

    /** Scrcpy 版本 */
    const val SCRCPY_VERSION = "3.3.4"

    /** Scrcpy Server 路径 */
    const val SCRCPY_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"
    const val SCRCPY_SERVER_2_PATH = "/data/local/tmp/scrcpy-server2.jar"

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

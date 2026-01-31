package com.mobile.scrcpy.android.core.common.constants

/**
 * ADB 配对常量
 */
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

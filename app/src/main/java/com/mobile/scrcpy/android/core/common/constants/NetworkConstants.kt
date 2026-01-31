package com.mobile.scrcpy.android.core.common.constants

/**
 * 网络常量
 * 包含 ADB 端口、超时时间、Socket 缓冲区等配置
 */
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

    /** Socket 接收缓冲区大小（字节）- 参考 scrcpy 原生实现 */
    const val SOCKET_RECEIVE_BUFFER_SIZE = 64 * 1024 // 64KB

    /** Socket 发送缓冲区大小（字节）- 参考 scrcpy 原生实现 */
    const val SOCKET_SEND_BUFFER_SIZE = 64 * 1024 // 64KB
}

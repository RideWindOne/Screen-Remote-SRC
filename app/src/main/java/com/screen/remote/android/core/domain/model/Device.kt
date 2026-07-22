package com.screen.remote.android.core.domain.model

/**
 * 设备连接类型
 */
enum class ConnectionType {
    TCP, // TCP/IP 网络连接
    TLS, // Wireless Debugging / ADB over TLS
    USB, // USB 有线连接
}

/**
 * ADB 密钥对信息
 */
data class AdbKeysInfo(
    val keysDir: String,
    val privateKey: String,
    val publicKey: String,
)

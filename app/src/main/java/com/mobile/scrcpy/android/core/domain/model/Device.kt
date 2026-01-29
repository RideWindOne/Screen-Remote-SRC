package com.mobile.scrcpy.android.core.domain.model

/**
 * 设备连接类型
 */
enum class ConnectionType {
    TCP, // TCP/IP 网络连接
    USB, // USB 有线连接
}

/**
 * 设备连接配置
 */
data class DeviceConfig(
    val deviceId: String,
    val host: String,
    val port: Int = 5555,
    val customName: String? = null,
    val autoConnect: Boolean = false,
    val codecCache: CodecCache? = null,
    val connectionType: ConnectionType = ConnectionType.TCP,
)

/**
 * 编解码器缓存（避免每次启动都检测）
 */
data class CodecCache(
    val videoDecoderName: String? = null,
    val audioDecoderName: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    companion object {
        const val CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L // 7天有效期
    }

    /**
     * 检查缓存是否有效
     */
    fun isValid(): Boolean = System.currentTimeMillis() - lastUpdated < CACHE_VALIDITY_MS
}

/**
 * ADB 密钥对信息
 */
data class AdbKeysInfo(
    val keysDir: String,
    val privateKey: String,
    val publicKey: String,
)

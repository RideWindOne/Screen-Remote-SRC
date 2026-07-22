package com.screen.remote.android.feature.device.data

/**
 * 配对状态
 */
enum class PairingStatus {
    /** 空闲 */
    IDLE,

    /** 连接中 */
    CONNECTING,

    /** 配对中 */
    PAIRING,

    /** 成功 */
    SUCCESS,

    /** 失败 */
    FAILED,
}

/**
 * 配对结果
 */
data class PairingResult(
    /** 是否成功 */
    val success: Boolean,
    /** 错误消息（失败时） */
    val errorMessage: String? = null,
    /** 设备信息（成功时） */
    val deviceInfo: DeviceInfo? = null,
)

/**
 * 设备信息
 */
data class DeviceInfo(
    /** 设备名称 */
    val name: String,
    /** IP 地址 */
    val ipAddress: String,
    /** ADB 端口 */
    val adbPort: Int,
    /** 设备序列号 */
    val serialNumber: String? = null,
)

/**
 * 配对历史记录
 */
data class PairingHistoryItem(
    /** 主机地址和端口（格式：192.168.1.100:12345） */
    val hostPort: String,
    /** 配对时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
) {
    /**
     * 格式化时间显示
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

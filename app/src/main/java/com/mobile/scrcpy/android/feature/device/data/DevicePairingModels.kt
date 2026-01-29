package com.mobile.scrcpy.android.feature.device.data

/**
 * 配对方式
 */
enum class PairingMethod {
    /** 二维码配对 */
    QR_CODE,

    /** 配对码配对 */
    PAIRING_CODE,
}

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
 * 配对请求数据
 */
data class PairingRequest(
    /** 配对方式 */
    val method: PairingMethod,
    /** IP 地址 */
    val ipAddress: String,
    /** 端口 */
    val port: String,
    /** 配对码（仅配对码方式需要） */
    val pairingCode: String? = null,
)

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
 * 二维码数据
 *
 * Android 无线调试二维码格式：
 * WIFI:T:ADB;S:<service-name>;P:<password>;;
 */
data class QRCodeData(
    /** 服务名称（包含 IP 和端口） */
    val serviceName: String,
    /** 密码（配对码） */
    val password: String,
) {
    companion object {
        /**
         * 从二维码字符串解析数据
         */
        fun parse(qrCodeString: String): QRCodeData? {
            return try {
                // 解析格式：WIFI:T:ADB;S:<service-name>;P:<password>;;
                val regex = Regex("WIFI:T:ADB;S:([^;]+);P:([^;]+);;")
                val matchResult = regex.find(qrCodeString) ?: return null

                val serviceName = matchResult.groupValues[1]
                val password = matchResult.groupValues[2]

                QRCodeData(serviceName, password)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 提取 IP 地址
     */
    fun extractIpAddress(): String? =
        try {
            // 服务名称格式通常为：adb-<serial>-<random>._adb-tls-pairing._tcp
            // 或者直接包含 IP:Port
            val parts = serviceName.split(":")
            if (parts.size == 2) {
                parts[0]
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    /**
     * 提取端口
     */
    fun extractPort(): String? =
        try {
            val parts = serviceName.split(":")
            if (parts.size == 2) {
                parts[1]
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
}

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

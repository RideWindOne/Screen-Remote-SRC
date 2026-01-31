package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.domain.model.ConnectionType

/**
 * 设备信息
 */
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val serialNumber: String,
    val connectionType: ConnectionType = ConnectionType.TCP,
)

/**
 * 编码器信息基类
 */
sealed class EncoderInfo(
    open val name: String,
    open val mimeType: String,
) {
    /**
     * 视频编码器信息
     */
    data class Video(
        override val name: String,
        override val mimeType: String,
    ) : EncoderInfo(name, mimeType)

    /**
     * 音频编码器信息
     */
    data class Audio(
        override val name: String,
        override val mimeType: String,
    ) : EncoderInfo(name, mimeType)
}

/**
 * 编码器检测结果
 */
data class EncoderDetectionResult(
    val videoEncoders: List<EncoderInfo.Video>,
    val audioEncoders: List<EncoderInfo.Audio>,
)

package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.domain.model.ConnectionType
import com.mobile.scrcpy.android.feature.codec.component.EncoderInfo

/**
 * 设备信息
 */
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val serialNumber: String,
    val connectionType: ConnectionType = ConnectionType.TCP,
)

/**
 * 视频编码器信息
 */
data class VideoEncoderInfo(
    override val name: String,
    override val mimeType: String,
) : EncoderInfo

/**
 * 音频编码器信息
 */
data class AudioEncoderInfo(
    override val name: String,
    override val mimeType: String,
) : EncoderInfo

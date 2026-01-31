package com.mobile.scrcpy.android.core.domain.model

/**
 * Scrcpy 配置选项 - 唯一配置载体
 *
 * 包含两类字段：
 * 1. 用户配置字段：有明确默认值，由 UI 负责更新
 * 2. 设备能力字段：默认空值，由连接过程检测并填充
 *
 * 特性：
 * - data class，通过 copy() 更新任意字段
 * - sessionId 作为唯一标识
 * - deviceSerial 作为设备身份标识
 * - 设备序列号变化时重新检测设备能力
 */
data class ScrcpyOptions(
    // ========== 标识字段 ==========
    val sessionId: String, // 会话 UUID，作为全局唯一标识
    // ========== 连接信息 ==========
    val host: String, // 网络设备: IP地址, USB设备: 序列号
    val port: Int = 0, // 网络设备: 端口号, USB设备: 0
    // ========== 用户配置字段 ==========
    val maxSize: Int = 1920,
    val videoBitRate: Int = 8000000,
    val maxFps: Int = 60,
    val displayId: Int = 0,
    val showTouches: Boolean = false,
    val stayAwake: Boolean = true,
    val codecOptions: String = "profile=1,level=2",
    val powerOffOnClose: Boolean = false,
    val enableAudio: Boolean = false,
    val audioBitRate: Int = 128000,
    val audioBufferMs: Int? = null,
    val keyFrameInterval: Int = 10,
    val turnScreenOff: Boolean = false,
    // ========== 编解码器辅助字段（UI 编辑 + 自动检测） ==========
    val preferredVideoCodec: String = "", // 偏好的视频编码格式（h264/h265），用于 UI 选择和自动检测参考
    val preferredAudioCodec: String = "", // 偏好的音频编码格式（opus/aac），用于 UI 选择和自动检测参考
    // ========== 用户手动选择的编解码器 ==========
    val userVideoEncoder: String = "", // 用户手动选择的视频编码器（优先级最高）
    val userAudioEncoder: String = "", // 用户手动选择的音频编码器（优先级最高）
    val userVideoDecoder: String = "", // 用户手动选择的视频解码器（优先级最高）
    val userAudioDecoder: String = "", // 用户手动选择的音频解码器（优先级最高）
    // ========== 设备能力字段（自动检测填充） ==========
    val deviceSerial: String = "", // 设备序列号（通过 ro.serialno 获取）
    val remoteVideoEncoders: List<String> = emptyList(), // 远程设备视频编码器列表
    val remoteAudioEncoders: List<String> = emptyList(), // 远程设备音频编码器列表
    val selectedVideoEncoder: String = "", // 系统自动选择的最佳视频编码器
    val selectedAudioEncoder: String = "", // 系统自动选择的最佳音频编码器
    val selectedVideoDecoder: String = "", // 系统自动选择的最佳视频解码器
    val selectedAudioDecoder: String = "", // 系统自动选择的最佳音频解码器
) {
    /**
     * 判断编解码器是否匹配当前设备
     */
    fun isEncoderListValid(deviceSerial: String): Boolean {
        if (this.deviceSerial.isBlank() || deviceSerial.isBlank()) return false
        if (this.deviceSerial != deviceSerial) return false
        if (remoteVideoEncoders.isEmpty() && remoteAudioEncoders.isEmpty()) return false
        return true
    }

    /**
     * 判断是否为 USB 连接
     */
    fun isUsbConnection(): Boolean = port == 0

    /**
     * 获取设备标识（用于日志和显示）
     */
    fun getDeviceIdentifier(): String = if (isUsbConnection()) host else "$host:$port"

    /**
     * 获取最终使用的视频编码器（用户选择 > 系统自动选择）
     */
    fun getFinalVideoEncoder(): String = userVideoEncoder.ifBlank { selectedVideoEncoder }

    /**
     * 获取最终使用的音频编码器（用户选择 > 系统自动选择）
     */
    fun getFinalAudioEncoder(): String = userAudioEncoder.ifBlank { selectedAudioEncoder }

    /**
     * 获取最终使用的视频解码器（用户选择 > 系统自动选择）
     */
    fun getFinalVideoDecoder(): String = userVideoDecoder.ifBlank { selectedVideoDecoder }

    /**
     * 获取最终使用的音频解码器（用户选择 > 系统自动选择）
     */
    fun getFinalAudioDecoder(): String = userAudioDecoder.ifBlank { selectedAudioDecoder }
}

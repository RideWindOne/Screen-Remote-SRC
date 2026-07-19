package com.screen.remote.android.core.domain.model

import com.screen.remote.android.core.common.ScrcpyConstants
import kotlinx.serialization.Serializable

@Serializable
enum class ScrcpyTunnelMode {
    DIRECT_ADB,
    ADB_FORWARD,
}

/** 用户可编辑、可持久化的 scrcpy 配置。 */
@Serializable
data class ScrcpyConfig(
    val gameMode: Boolean = false,
    val useFullScreen: Boolean = false,
    val showFloatingBall: Boolean = true,
    val enableHardwareDecoding: Boolean = true,
    val followRemoteOrientation: Boolean = true,
    val tunnelMode: ScrcpyTunnelMode = ScrcpyTunnelMode.DIRECT_ADB,
    val maxSize: Int = 0,
    val videoBitRate: Int = ScrcpyConstants.DEFAULT_VIDEO_BITRATE_INT,
    val maxFps: Int = 60,
    val userVideoEncoder: String = "",
    val userVideoDecoder: String = "",
    val enableAudio: Boolean = false,
    val audioBitRate: Int = 128000,
    val userAudioEncoder: String = "",
    val userAudioDecoder: String = "",
    val clipboardSync: Boolean = true,
    val turnScreenOff: Boolean = true,
    val powerOffOnClose: Boolean = false,
    val cleanupOnDisconnect: Boolean = false,
    val keepDeviceAwake: Boolean = false,
    val stayAwake: Boolean = false,
    val ignoreVideoEncoderConstraints: Boolean = false,
    val newDisplayEnabled: Boolean = false,
    val virtualDisplaySystemDecorations: Boolean = true,
    val preserveVirtualDisplayContent: Boolean = false,
    val startApp: String = "",
    val newDisplay: String = "",
    val displayId: Int = 0,
    val showTouches: Boolean = false,
    val codecOptions: String = ScrcpyConstants.DEFAULT_CODEC_OPTIONS,
)

/** 连接过程自动生成的设备能力缓存，不属于用户配置。 */
@Serializable
data class DeviceCapabilityCache(
    val deviceSerial: String = "",
    val remoteVideoEncoders: List<EncoderCapability> = emptyList(),
    val selectedVideoCodec: String = "",
    val selectedVideoEncoder: String = "",
    val selectedVideoDecoder: String = "",
    val remoteAudioEncoders: List<EncoderCapability> = emptyList(),
    val selectedAudioCodec: String = "",
    val selectedAudioEncoder: String = "",
    val selectedAudioDecoder: String = "",
)

fun DeviceCapabilityCache.resetForDevice(deviceSerial: String): DeviceCapabilityCache =
    DeviceCapabilityCache(deviceSerial = deviceSerial)

/**
 * 运行时会话选项。
 *
 * 会话身份、连接候选、用户配置和自动能力缓存各自只有一个所有者。持久化层直接
 * 复用 [ScrcpyConfig] 与 [DeviceCapabilityCache]，不再逐字段复制整套配置。
 */
data class ScrcpyOptions(
    val sessionId: String,
    val profileId: String = "",
    val connectionCandidates: List<ConnectionCandidate>,
    val config: ScrcpyConfig = ScrcpyConfig(),
    val capabilityCache: DeviceCapabilityCache = DeviceCapabilityCache(),
) {
    init {
        require(connectionCandidates.isNotEmpty()) { "ScrcpyOptions 必须至少包含一个 connectionCandidate" }
    }

    private fun primaryConnectionCandidate(): ConnectionCandidate =
        connectionCandidates.minBy(ConnectionCandidate::priority)

    fun getDeviceIdentifier(): String = primaryConnectionCandidate().deviceIdentifier()

    fun getFinalVideoEncoder(): String = config.userVideoEncoder.ifBlank { capabilityCache.selectedVideoEncoder }

    fun getFinalAudioEncoder(): String = config.userAudioEncoder.ifBlank { capabilityCache.selectedAudioEncoder }

    fun getFinalVideoDecoder(): String = config.userVideoDecoder.ifBlank { capabilityCache.selectedVideoDecoder }

    fun getFinalAudioDecoder(): String = config.userAudioDecoder.ifBlank { capabilityCache.selectedAudioDecoder }
}

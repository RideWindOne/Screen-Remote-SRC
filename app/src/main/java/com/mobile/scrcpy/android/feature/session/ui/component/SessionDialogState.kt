package com.mobile.scrcpy.android.feature.session.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.data.repository.SessionData
import java.util.UUID

/**
 * 会话对话框状态管理
 */
class SessionDialogState(
    sessionData: SessionData? = null,
) {
    // 基本信息
    var sessionName by mutableStateOf(sessionData?.name ?: "")
    var host by mutableStateOf(
        if (sessionData?.isUsbConnection() == true) {
            ""
        } else {
            sessionData?.host ?: ""
        },
    )
    var port by mutableStateOf(sessionData?.port ?: "")
    var color by mutableStateOf(sessionData?.color ?: "BLUE")

    // USB 模式
    var isUsbMode by mutableStateOf(sessionData?.isUsbConnection() ?: false)
    var usbSerialNumber by mutableStateOf(
        sessionData?.getUsbSerialNumber() ?: "",
    )

    // 分组
    var selectedGroupIds by mutableStateOf(sessionData?.groupIds ?: emptyList())

    // 连接选项
    var forceAdb by mutableStateOf(sessionData?.forceAdb ?: false)

    // 视频配置
    var maxSize by mutableStateOf(sessionData?.maxSize ?: "")
    var videoBitrate by mutableStateOf(sessionData?.videoBitrate ?: "")
    var maxFps by mutableStateOf(sessionData?.maxFps ?: "")
    var preferredVideoCodec by mutableStateOf(sessionData?.preferredVideoCodec ?: ScrcpyConstants.DEFAULT_VIDEO_CODEC)
    var userVideoEncoder by mutableStateOf(sessionData?.userVideoEncoder ?: "")
    var userVideoDecoder by mutableStateOf(sessionData?.userVideoDecoder ?: "")

    // 音频配置
    var enableAudio by mutableStateOf(sessionData?.enableAudio ?: false)
    var preferredAudioCodec by mutableStateOf(sessionData?.preferredAudioCodec ?: ScrcpyConstants.DEFAULT_AUDIO_CODEC)
    var userAudioEncoder by mutableStateOf(sessionData?.userAudioEncoder ?: "")
    var userAudioDecoder by mutableStateOf(sessionData?.userAudioDecoder ?: "")
    var audioBitrate by mutableStateOf(sessionData?.audioBitrate ?: "")
    var audioBufferMs by mutableStateOf(sessionData?.audioBufferMs ?: "")
    var videoBufferMs by mutableStateOf(sessionData?.videoBufferMs ?: "")
    var audioVolume by mutableFloatStateOf(1.0f)

    // 编码器缓存（远程设备能力，每个会话独立）
    var remoteVideoEncoders by mutableStateOf(sessionData?.remoteVideoEncoders ?: emptyList())
    var remoteAudioEncoders by mutableStateOf(sessionData?.remoteAudioEncoders ?: emptyList())
    var selectedVideoEncoder by mutableStateOf(sessionData?.selectedVideoEncoder ?: "")
    var selectedAudioEncoder by mutableStateOf(sessionData?.selectedAudioEncoder ?: "")
    var selectedVideoDecoder by mutableStateOf(sessionData?.selectedVideoDecoder ?: "")
    var selectedAudioDecoder by mutableStateOf(sessionData?.selectedAudioDecoder ?: "")
    var deviceSerial by mutableStateOf(sessionData?.deviceSerial ?: "")

    // 其他选项
    var keyFrameInterval by mutableIntStateOf(sessionData?.keyFrameInterval ?: 2)
    var stayAwake by mutableStateOf(sessionData?.stayAwake ?: false)
    var turnScreenOff by mutableStateOf(sessionData?.turnScreenOff ?: true)
    var powerOffOnClose by mutableStateOf(sessionData?.powerOffOnClose ?: false)
    var useFullScreen by mutableStateOf(sessionData?.useFullScreen ?: false)
    var keepDeviceAwake by mutableStateOf(false)
    var enableHardwareDecoding by mutableStateOf(true)
    var followRemoteOrientation by mutableStateOf(false)
    var showNewDisplay by mutableStateOf(false)

    // UI 状态
    var showKeyFrameIntervalMenu by mutableStateOf(false)
    var showVideoCodecMenu by mutableStateOf(false)
    var showAudioCodecMenu by mutableStateOf(false)
    var showEncoderOptionsDialog by mutableStateOf(false)
    var showAudioEncoderDialog by mutableStateOf(false)
    var showVideoDecoderSelector by mutableStateOf(false)
    var showAudioDecoderSelector by mutableStateOf(false)
    var showUsbDeviceDialog by mutableStateOf(false)
    var showGroupSelector by mutableStateOf(false)

    /**
     * 转换为 SessionData
     */
    fun toSessionData(existingId: String? = null): SessionData {
        val finalHost = if (isUsbMode) usbSerialNumber else host
        val finalPort = if (isUsbMode) "0" else port

        return SessionData(
            id = existingId ?: UUID.randomUUID().toString(),
            name = sessionName,
            host = finalHost,
            port = finalPort,
            color = color,
            forceAdb = forceAdb,
            maxSize = maxSize,
            videoBitrate = videoBitrate,
            maxFps = maxFps,
            preferredVideoCodec = preferredVideoCodec,
            userVideoEncoder = userVideoEncoder,
            userVideoDecoder = userVideoDecoder,
            enableAudio = enableAudio,
            preferredAudioCodec = preferredAudioCodec,
            userAudioEncoder = userAudioEncoder,
            userAudioDecoder = userAudioDecoder,
            audioBitrate = audioBitrate,
            audioBufferMs = audioBufferMs,
            videoBufferMs = videoBufferMs,
            keyFrameInterval = keyFrameInterval,
            stayAwake = stayAwake,
            turnScreenOff = turnScreenOff,
            powerOffOnClose = powerOffOnClose,
            useFullScreen = useFullScreen,
            selectedVideoEncoder = selectedVideoEncoder,
            selectedAudioEncoder = selectedAudioEncoder,
            selectedVideoDecoder = selectedVideoDecoder,
            selectedAudioDecoder = selectedAudioDecoder,
            deviceSerial = deviceSerial,
            remoteVideoEncoders = remoteVideoEncoders,
            remoteAudioEncoders = remoteAudioEncoders,
            groupIds = selectedGroupIds,
        )
    }

    /**
     * 检查是否有有效的设备连接信息
     */
    fun hasValidDevice(): Boolean =
        if (isUsbMode) {
            usbSerialNumber.isNotBlank()
        } else {
            host.isNotBlank()
        }

    /**
     * 验证输入
     */
    fun validate(): Boolean {
        if (sessionName.isBlank()) return false
        if (!isUsbMode && host.isBlank()) return false
        if (isUsbMode && usbSerialNumber.isBlank()) return false
        return true
    }
}

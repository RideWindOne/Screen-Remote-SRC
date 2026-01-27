package com.mobile.scrcpy.android.feature.session.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.feature.session.data.repository.SessionData
import java.util.UUID

/**
 * 会话对话框状态管理
 */
class SessionDialogState(sessionData: SessionData? = null) {
    // 基本信息
    var sessionName by mutableStateOf(sessionData?.name ?: "")
    var host by mutableStateOf(
        if (sessionData?.isUsbConnection() == true) "" 
        else sessionData?.host ?: ""
    )
    var port by mutableStateOf(sessionData?.port ?: "5555")
    var color by mutableStateOf(sessionData?.color ?: "BLUE")
    
    // USB 模式
    var isUsbMode by mutableStateOf(sessionData?.isUsbConnection() ?: false)
    var usbSerialNumber by mutableStateOf(
        sessionData?.getUsbSerialNumber() ?: ""
    )
    
    // 分组
    var selectedGroupIds by mutableStateOf(sessionData?.groupIds ?: emptyList())
    
    // 连接选项
    var forceAdb by mutableStateOf(sessionData?.forceAdb ?: false)
    
    // 视频配置
    var maxSize by mutableStateOf(sessionData?.maxSize ?: "")
    var bitrate by mutableStateOf(sessionData?.bitrate ?: "")
    var maxFps by mutableStateOf(sessionData?.maxFps ?: "")
    var videoCodec by mutableStateOf(sessionData?.videoCodec ?: ScrcpyConstants.DEFAULT_VIDEO_CODEC)
    var videoEncoder by mutableStateOf(sessionData?.videoEncoder ?: "")
    
    // 音频配置
    var enableAudio by mutableStateOf(sessionData?.enableAudio ?: false)
    var audioCodec by mutableStateOf(sessionData?.audioCodec ?: ScrcpyConstants.DEFAULT_AUDIO_CODEC)
    var audioEncoder by mutableStateOf(sessionData?.audioEncoder ?: "")
    var audioVolume by mutableFloatStateOf(1.0f)
    
    // 其他选项
    var stayAwake by mutableStateOf(sessionData?.stayAwake ?: false)
    var turnScreenOff by mutableStateOf(sessionData?.turnScreenOff ?: true)
    var powerOffOnClose by mutableStateOf(sessionData?.powerOffOnClose ?: false)
    var useFullScreen by mutableStateOf(sessionData?.useFullScreen ?: false)
    var keepDeviceAwake by mutableStateOf(false)
    var enableHardwareDecoding by mutableStateOf(true)
    var followRemoteOrientation by mutableStateOf(false)
    var showNewDisplay by mutableStateOf(false)
    
    // UI 状态
    var showVideoCodecMenu by mutableStateOf(false)
    var showAudioCodecMenu by mutableStateOf(false)
    var showEncoderOptionsDialog by mutableStateOf(false)
    var showAudioEncoderDialog by mutableStateOf(false)
    var showUsbDeviceDialog by mutableStateOf(false)
    
    /**
     * 转换为 SessionData
     */
    fun toSessionData(existingId: String? = null): SessionData {
        val finalHost = if (isUsbMode) {
            "usb:$usbSerialNumber"
        } else {
            host
        }
        
        return SessionData(
            id = existingId ?: UUID.randomUUID().toString(),
            name = sessionName,
            host = finalHost,
            port = port,
            color = color,
            forceAdb = forceAdb,
            maxSize = maxSize,
            bitrate = bitrate,
            maxFps = maxFps,
            videoCodec = videoCodec,
            videoEncoder = videoEncoder,
            enableAudio = enableAudio,
            audioCodec = audioCodec,
            audioEncoder = audioEncoder,
            stayAwake = stayAwake,
            turnScreenOff = turnScreenOff,
            powerOffOnClose = powerOffOnClose,
            useFullScreen = useFullScreen,
            groupIds = selectedGroupIds
        )
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

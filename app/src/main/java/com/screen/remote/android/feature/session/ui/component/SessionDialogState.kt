package com.screen.remote.android.feature.session.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.core.common.util.parseHostPort
import com.screen.remote.android.core.data.repository.SessionData
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
    var enableClipboardSync by mutableStateOf(sessionData?.enableClipboardSync ?: true)
    var turnScreenOff by mutableStateOf(sessionData?.turnScreenOff ?: true)
    var powerOffOnClose by mutableStateOf(sessionData?.powerOffOnClose ?: false)
    var cleanupOnDisconnect by mutableStateOf(sessionData?.cleanupOnDisconnect ?: true)
    var useFullScreen by mutableStateOf(sessionData?.useFullScreen ?: false)
    var keepDeviceAwake by mutableStateOf(sessionData?.keepDeviceAwake ?: false)
    var enableHardwareDecoding by mutableStateOf(sessionData?.enableHardwareDecoding ?: true)
    var followRemoteOrientation by mutableStateOf(sessionData?.followRemoteOrientation ?: false)
    var showNewDisplay by mutableStateOf(sessionData?.newDisplayEnabled ?: false)
    var newDisplayWidth by mutableStateOf(parseNewDisplay(sessionData?.newDisplay).width)
    var newDisplayHeight by mutableStateOf(parseNewDisplay(sessionData?.newDisplay).height)
    var newDisplayDpi by mutableStateOf(parseNewDisplay(sessionData?.newDisplay).dpi)

    // UI 状态
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
        val parsedEndpoint = if (!isUsbMode) parseHostPort(host) else null
        val finalHost = if (isUsbMode) usbSerialNumber else normalizeEndpointHost(parsedEndpoint?.host ?: host)
        val finalPort = if (isUsbMode) "0" else parsedEndpoint?.port?.toString() ?: port.trim()

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
            newDisplayEnabled = showNewDisplay,
            newDisplay = if (showNewDisplay) buildNewDisplay(newDisplayWidth, newDisplayHeight, newDisplayDpi) else "",
            preferredVideoCodec = preferredVideoCodec,
            userVideoEncoder = userVideoEncoder,
            userVideoDecoder = userVideoDecoder,
            enableAudio = enableAudio,
            preferredAudioCodec = preferredAudioCodec,
            userAudioEncoder = userAudioEncoder,
            userAudioDecoder = userAudioDecoder,
            audioBitrate = audioBitrate,
            enableClipboardSync = enableClipboardSync,
            stayAwake = false,
            turnScreenOff = turnScreenOff,
            powerOffOnClose = powerOffOnClose,
            cleanupOnDisconnect = cleanupOnDisconnect,
            useFullScreen = useFullScreen,
            keepDeviceAwake = keepDeviceAwake,
            enableHardwareDecoding = enableHardwareDecoding,
            followRemoteOrientation = followRemoteOrientation,
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

internal data class NewDisplayParts(
    val width: String = "",
    val height: String = "",
    val dpi: String = "",
)

internal fun parseNewDisplay(input: String?): NewDisplayParts {
    val trimmed = input?.trim().orEmpty()
    if (trimmed.isEmpty()) return NewDisplayParts()

    val sizePart = trimmed.substringBefore('/')
    val dpiPart = trimmed.substringAfter('/', "")
    val width = sizePart.substringBefore('x', "")
    val height = sizePart.substringAfter('x', "")
    return NewDisplayParts(
        width = width.filter(Char::isDigit),
        height = height.filter(Char::isDigit),
        dpi = dpiPart.filter(Char::isDigit),
    )
}

internal fun buildNewDisplay(
    width: String,
    height: String,
    dpi: String,
): String =
    buildString {
        val normalizedWidth = width.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
        val normalizedHeight = height.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
        val normalizedDpi = dpi.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
        if (normalizedWidth != null && normalizedHeight != null) {
            append("${normalizedWidth}x$normalizedHeight")
        }
        if (normalizedDpi != null) {
            append("/$normalizedDpi")
        }
    }

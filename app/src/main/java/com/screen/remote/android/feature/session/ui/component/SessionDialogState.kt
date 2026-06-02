package com.screen.remote.android.feature.session.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.constants.NetworkConstants
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.toData
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.formatSessionAddress
import com.screen.remote.android.core.domain.model.parseSessionAddressCandidate
import com.screen.remote.android.core.domain.model.parseTcpHostPort
import com.screen.remote.android.core.domain.model.toAddressEndpoint
import java.util.UUID

/**
 * 会话对话框状态管理
 */
class SessionDialogState(
    sessionData: SessionData? = null,
) {
    private val initialPrimaryCandidate =
        sessionData?.toConnectionCandidates()?.minByOrNull(ConnectionCandidate::priority)
    var deviceType by mutableStateOf(SessionDeviceType.from(sessionData))

    // 基本信息
    var sessionName by mutableStateOf(sessionData?.name ?: "")
    var host by mutableStateOf(
        when (initialPrimaryCandidate?.transport) {
            ConnectionTransport.USB -> ""
            ConnectionTransport.MDNS -> normalizeMdnsServiceName(initialPrimaryCandidate.host)
            ConnectionTransport.TCP -> initialPrimaryCandidate.host
            null -> ""
        },
    )
    var port by mutableStateOf(
        initialPrimaryCandidate
            ?.takeIf { it.transport == ConnectionTransport.TCP }
            ?.port
            ?.toString()
            ?: "0",
    )
    var color by mutableStateOf(sessionData?.color ?: "BLUE")
    var profileId by mutableStateOf(sessionData?.profileId ?: "")
    var useProfileDefaults by mutableStateOf(sessionData?.useProfileDefaults ?: false)
    var backupEndpoints by mutableStateOf(backupEndpointsFrom(sessionData))

    // USB 模式
    var isUsbMode: Boolean
        get() = deviceType == SessionDeviceType.USB
        set(value) {
            deviceType = if (value) SessionDeviceType.USB else SessionDeviceType.TCP
        }
    var usbSerialNumber by mutableStateOf(
        normalizeUsbSerial(initialPrimaryCandidate?.takeIf { it.transport == ConnectionTransport.USB }?.host ?: ""),
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
    var remoteVideoEncoders: List<EncoderCapability> by
        mutableStateOf(sessionData?.remoteVideoEncoders ?: emptyList())
    var remoteAudioEncoders: List<EncoderCapability> by
        mutableStateOf(sessionData?.remoteAudioEncoders ?: emptyList())
    var selectedVideoEncoder by mutableStateOf(sessionData?.selectedVideoEncoder ?: "")
    var selectedAudioEncoder by mutableStateOf(sessionData?.selectedAudioEncoder ?: "")
    var selectedVideoCodec by mutableStateOf(sessionData?.selectedVideoCodec ?: "")
    var selectedAudioCodec by mutableStateOf(sessionData?.selectedAudioCodec ?: "")
    var selectedVideoDecoder by mutableStateOf(sessionData?.selectedVideoDecoder ?: "")
    var selectedAudioDecoder by mutableStateOf(sessionData?.selectedAudioDecoder ?: "")
    var deviceSerial by mutableStateOf(sessionData?.deviceSerial ?: "")

    // 其他选项
    var enableClipboardSync by mutableStateOf(sessionData?.enableClipboardSync ?: true)
    var turnScreenOff by mutableStateOf(sessionData?.turnScreenOff ?: true)
    var powerOffOnClose by mutableStateOf(sessionData?.powerOffOnClose ?: false)
    var cleanupOnDisconnect by mutableStateOf(sessionData?.cleanupOnDisconnect ?: true)
    var ignoreVideoEncoderConstraints by mutableStateOf(sessionData?.ignoreVideoEncoderConstraints ?: false)
    var useFullScreen by mutableStateOf(sessionData?.useFullScreen ?: false)
    var keepDeviceAwake by mutableStateOf(sessionData?.keepDeviceAwake ?: false)
    var enableHardwareDecoding by mutableStateOf(sessionData?.enableHardwareDecoding ?: true)
    var followRemoteOrientation by mutableStateOf(sessionData?.followRemoteOrientation ?: false)
    private val tcpPortForwardRules = sessionData?.tcpPortForwardRules
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
    var showMdnsServiceDialog by mutableStateOf(false)
    var showGroupSelector by mutableStateOf(false)
    var showDeviceTypeMenu by mutableStateOf(false)
    var showSessionAddressDialog by mutableStateOf(false)
    var showConnectionLatencyTest by mutableStateOf(false)

    /**
     * 转换为 SessionData
     */
    fun toSessionData(existingId: String? = null): SessionData {
        val parsedEndpoint = if (deviceType == SessionDeviceType.TCP) parseTcpHostPort(host) else null
        val finalHost =
            when (deviceType) {
                SessionDeviceType.USB -> normalizeUsbSerial(usbSerialNumber)
                SessionDeviceType.MDNS -> normalizeMdnsServiceName(host)
                SessionDeviceType.TCP -> normalizeEndpointHost(parsedEndpoint?.host ?: host)
            }
        val finalPort =
            when (deviceType) {
                SessionDeviceType.USB -> "0"
                SessionDeviceType.MDNS -> "0"
                SessionDeviceType.TCP -> parsedEndpoint?.port?.toString() ?: port.trim()
            }

        return SessionData(
            id = existingId ?: UUID.randomUUID().toString(),
            name = sessionName,
            connectionCandidates = buildConnectionCandidates(finalHost, finalPort).map { it.toData() },
            color = color,
            profileId = profileId,
            useProfileDefaults = useProfileDefaults,
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
            ignoreVideoEncoderConstraints = ignoreVideoEncoderConstraints,
            useFullScreen = useFullScreen,
            keepDeviceAwake = keepDeviceAwake,
            enableHardwareDecoding = enableHardwareDecoding,
            followRemoteOrientation = followRemoteOrientation,
            tcpPortForwardRules = tcpPortForwardRules ?: listOf(com.screen.remote.android.core.data.repository.TcpPortForwardRule()),
            selectedVideoEncoder = selectedVideoEncoder,
            selectedAudioEncoder = selectedAudioEncoder,
            selectedVideoCodec = selectedVideoCodec,
            selectedAudioCodec = selectedAudioCodec,
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
        when (deviceType) {
            SessionDeviceType.USB -> usbSerialNumber.isNotBlank()
            SessionDeviceType.TCP, SessionDeviceType.MDNS -> host.isNotBlank()
        }

    fun isMdnsMode(): Boolean = deviceType == SessionDeviceType.MDNS

    fun addBackupEndpoint() {
        backupEndpoints = backupEndpoints + ""
    }

    fun updateBackupEndpoint(
        index: Int,
        value: String,
    ) {
        backupEndpoints = backupEndpoints.mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }
    }

    fun removeBackupEndpoint(index: Int) {
        backupEndpoints = backupEndpoints.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun sessionAddressPreview(): String = primarySessionAddressPreview()

    fun primarySessionAddressPreview(): String =
        when (deviceType) {
            SessionDeviceType.TCP -> {
                val parsedEndpoint = parseTcpHostPort(host)
                val displayHost = parsedEndpoint?.host ?: host
                val displayPort =
                    parsedEndpoint?.port
                        ?: port.toIntOrNull() ?: NetworkConstants.DEFAULT_ADB_PORT_INT
                if (displayHost.isBlank()) {
                    ""
                } else {
                    formatSessionAddress(ConnectionTransport.TCP, normalizeEndpointHost(displayHost), displayPort)
                }
            }
            SessionDeviceType.USB -> formatSessionAddress(ConnectionTransport.USB, normalizeUsbSerial(usbSerialNumber.ifBlank { "..." }))
            SessionDeviceType.MDNS -> formatSessionAddress(ConnectionTransport.MDNS, normalizeMdnsServiceName(host.ifBlank { "..." }))
        }.let(DeviceTransportSerial::stripAnyTransportPrefix)

    fun selectDeviceType(type: SessionDeviceType) {
        deviceType = type
        when (type) {
            SessionDeviceType.USB -> port = "0"
            SessionDeviceType.MDNS -> port = "0"
            SessionDeviceType.TCP -> {
                if (port.isBlank() || port == "0") {
                    port = "5555"
                }
            }
        }
    }

    fun updateUsbSerialNumber(value: String) {
        usbSerialNumber = normalizeUsbSerial(value)
    }

    fun updateMdnsServiceName(value: String) {
        host = normalizeMdnsServiceName(value)
        port = "0"
    }

    fun selectMdnsService(
        serviceName: String,
        displayName: String,
    ) {
        deviceType = SessionDeviceType.MDNS
        host = normalizeMdnsServiceName(serviceName)
        port = "0"
        if (sessionName.isBlank()) {
            sessionName = displayName
        }
    }

    /**
     * 验证输入
     */
    fun validate(): Boolean {
        if (sessionName.isBlank()) return false
        if (deviceType != SessionDeviceType.USB && host.isBlank()) return false
        if (deviceType == SessionDeviceType.USB && usbSerialNumber.isBlank()) return false
        return true
    }

    private fun buildConnectionCandidates(
        finalHost: String,
        finalPort: String,
    ): List<ConnectionCandidate> {
        val primary =
            when (deviceType) {
                SessionDeviceType.TCP ->
                    ConnectionCandidate(
                        transport = ConnectionTransport.TCP,
                        host = finalHost,
                        port = finalPort.toIntOrNull() ?: 5555,
                    )
                SessionDeviceType.USB ->
                    ConnectionCandidate(
                        transport = ConnectionTransport.USB,
                        host = finalHost,
                    )
                SessionDeviceType.MDNS ->
                    ConnectionCandidate(
                        transport = ConnectionTransport.MDNS,
                        host = finalHost,
                    )
            }

        val backups =
            backupEndpoints
                .mapNotNull { parseSessionAddressCandidate(it) }
                .filterNot { it.transport == primary.transport && it.host == primary.host && it.port == primary.port }

        return (listOf(primary) + backups)
            .distinctBy { "${it.transport}:${it.host}:${it.port}" }
            .mapIndexed { index, candidate -> candidate.copy(priority = index) }
    }
}

private fun backupEndpointsFrom(sessionData: SessionData?): List<String> =
    sessionData
        ?.toConnectionCandidates()
        ?.drop(1)
        ?.map { it.toAddressEndpoint() }
        .orEmpty()

private fun normalizeUsbSerial(value: String): String =
    DeviceTransportSerial.stripUsbPrefix(value)

private fun normalizeMdnsServiceName(value: String): String =
    DeviceTransportSerial.mdnsDeviceSerial(value)

enum class SessionDeviceType {
    TCP,
    USB,
    MDNS,
    ;

    companion object {
        fun from(sessionData: SessionData?): SessionDeviceType =
            when {
                sessionData?.isUsbConnection() == true -> USB
                sessionData?.isMdnsConnection() == true -> MDNS
                else -> TCP
            }
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

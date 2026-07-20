package com.screen.remote.android.feature.session.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.screen.remote.android.core.common.constants.NetworkConstants
import com.screen.remote.android.core.common.constants.ScrcpyConstants
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.common.util.normalizeEndpointHost
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.data.repository.toData
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.DeviceCapabilityCache
import com.screen.remote.android.core.domain.model.ScrcpyConfig
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
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
    private val initialConfig = sessionData?.config ?: ScrcpyConfig()
    private val initialCapabilityCache = sessionData?.capabilityCache ?: DeviceCapabilityCache()
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

    // 会话级配置和设备能力各自保持单一状态对象，避免在 UI 草稿层再次展开整套字段。
    var config by mutableStateOf(initialConfig)
        private set
    var capabilityCache by mutableStateOf(initialCapabilityCache)
        private set

    // 视频配置
    var maxSize by mutableStateOf(initialConfig.maxSize.takeIf { it > 0 }?.toString().orEmpty())
    var videoBitrate by mutableStateOf(formatBitrateForEditor(initialConfig.videoBitRate))
    var maxFps by mutableStateOf(initialConfig.maxFps.toString())

    // 音频配置
    var audioBitrate by mutableStateOf(formatBitrateForEditor(initialConfig.audioBitRate))
    var audioVolume by mutableFloatStateOf(1.0f)
    private val tcpPortForwardRules = sessionData?.tcpPortForwardRules
    var newDisplayWidth by mutableStateOf(parseNewDisplay(initialConfig.newDisplay).width)
    var newDisplayHeight by mutableStateOf(parseNewDisplay(initialConfig.newDisplay).height)
    var newDisplayDpi by mutableStateOf(parseNewDisplay(initialConfig.newDisplay).dpi)

    // UI 状态
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
    var showRemoteAppSelector by mutableStateOf(false)

    init {
        if (config.gameMode) {
            config = config.copy(useFullScreen = false)
            normalizeGameVideoSettings()
        }
    }

    fun updateConfig(transform: ScrcpyConfig.() -> ScrcpyConfig) {
        config = config.transform()
    }

    fun updateCapabilityCache(transform: DeviceCapabilityCache.() -> DeviceCapabilityCache) {
        capabilityCache = capabilityCache.transform()
    }

    fun updateGameMode(enabled: Boolean) {
        if (config.gameMode == enabled) return
        config = config.copy(gameMode = enabled)
        if (enabled) {
            config = config.copy(useFullScreen = false)
            normalizeGameVideoSettings()
        }
    }

    private fun normalizeGameVideoSettings() {
        maxSize = closestGameOption(maxSize.toIntOrNull(), GAME_MAX_SIZE_OPTIONS, defaultValue = 720).toString()
        videoBitrate =
            closestGameOption(
                parseBitrateBitsPerSecond(videoBitrate),
                GAME_VIDEO_BITRATE_OPTIONS,
                defaultValue = GAME_VIDEO_BITRATE_OPTIONS.first(),
            ).toGameBitrateLabel()
        maxFps = closestGameOption(maxFps.toIntOrNull(), GAME_MAX_FPS_OPTIONS, defaultValue = 60).toString()
    }

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
            config =
                config.copy(
                    maxSize = maxSize.toIntOrNull()?.takeIf { it > 0 } ?: 0,
                    videoBitRate = parseBitrateBitsPerSecond(videoBitrate) ?: ScrcpyConstants.DEFAULT_VIDEO_BITRATE_INT,
                    maxFps = maxFps.toIntOrNull() ?: 60,
                    newDisplay =
                        if (config.newDisplayEnabled) {
                            buildNewDisplay(newDisplayWidth, newDisplayHeight, newDisplayDpi)
                        } else {
                            ""
                        },
                    audioBitRate = parseBitrateBitsPerSecond(audioBitrate) ?: 128000,
                    stayAwake = config.stayAwake && config.cleanupOnDisconnect,
                    useFullScreen = config.useFullScreen && !config.gameMode,
                    startApp = if (config.newDisplayEnabled) config.startApp.trim() else "",
                ),
            tcpPortForwardRules = tcpPortForwardRules ?: listOf(com.screen.remote.android.core.data.repository.TcpPortForwardRule()),
            capabilityCache =
                capabilityCache,
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

internal val GAME_MAX_SIZE_OPTIONS = listOf(720, 1080, 1920)

/**
 * 高帧率游戏场景需要为复杂画面保留码率余量，避免编码器因缺少码率而持续抬高 QP。
 * 建议来源：https://github.com/Genymobile/scrcpy/pull/6954#issuecomment-5022877392
 */
internal val GAME_VIDEO_BITRATE_OPTIONS = listOf(1_000_000, 2_000_000, 4_000_000, 8_000_000, 12_000_000, 20_000_000)
internal val GAME_MAX_FPS_OPTIONS = listOf(60, 90, 120)

internal fun closestGameOption(
    value: Int?,
    options: List<Int>,
    defaultValue: Int,
): Int =
    value?.let { current -> options.minByOrNull { option -> kotlin.math.abs(option.toLong() - current.toLong()) } }
        ?: defaultValue

private fun parseBitrateBitsPerSecond(value: String): Int? {
    val normalized = value.trim().lowercase()
    if (normalized.isBlank()) return null
    val multiplier =
        when {
            normalized.endsWith("m") -> 1_000_000
            normalized.endsWith("k") -> 1_000
            else -> 1
        }
    val number = if (multiplier == 1) normalized else normalized.dropLast(1)
    return number.toDoubleOrNull()?.times(multiplier)?.toInt()
}

/** Convert stored bps values back to the compact units used by the session editor. */
internal fun formatBitrateForEditor(bitsPerSecond: Int): String =
    when {
        bitsPerSecond > 0 && bitsPerSecond % 1_000_000 == 0 -> "${bitsPerSecond / 1_000_000}M"
        bitsPerSecond > 0 && bitsPerSecond % 1_000 == 0 -> "${bitsPerSecond / 1_000}K"
        else -> bitsPerSecond.toString()
    }

private fun Int.toGameBitrateLabel(): String = "${this / 1_000_000}M"

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

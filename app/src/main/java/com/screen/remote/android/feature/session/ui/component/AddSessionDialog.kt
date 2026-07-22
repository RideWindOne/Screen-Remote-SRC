package com.screen.remote.android.feature.session.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.AppTextSizes
import com.screen.remote.android.core.common.PlaceholderTexts
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.common.constants.NetworkConstants
import com.screen.remote.android.core.common.constants.ScrcpyConstants
import com.screen.remote.android.core.common.util.resolveLocalDisplaySpec
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.DialogContainer
import com.screen.remote.android.core.designsystem.component.DialogHeader
import com.screen.remote.android.core.designsystem.component.DialogHeaderSpacer
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.designsystem.component.GroupSelectorDialog
import com.screen.remote.android.core.designsystem.component.HelpIcon
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenu
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.screen.remote.android.core.designsystem.component.SectionTitle
import com.screen.remote.android.core.common.util.DeviceTransportSerial
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupType
import com.screen.remote.android.core.domain.model.ConnectionCandidate
import com.screen.remote.android.core.domain.model.ConnectionTransport
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.core.domain.model.parseSessionAddressCandidate
import com.screen.remote.android.core.domain.model.parseTcpHostPort
import com.screen.remote.android.core.domain.model.toAddressEndpoint
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CodecTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.feature.codec.component.EncoderSelectionDialog
import com.screen.remote.android.feature.codec.component.EncoderType
import com.screen.remote.android.feature.codec.ui.AudioCodecSelectorScreen
import com.screen.remote.android.feature.codec.ui.VideoCodecSelectorScreen
import com.screen.remote.android.feature.codec.util.CodecUtils
import com.screen.remote.android.feature.device.ui.component.UsbDeviceSelectionDialog
import com.screen.remote.android.feature.session.ui.ConnectionLatencyTestPage
import com.screen.remote.android.feature.session.ui.SessionManagementCenteredDialog
import com.screen.remote.android.feature.session.ui.quoteShellArg
import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val SessionDialogSectionShape = RoundedCornerShape(12.dp)
private val AudioVolumeRowHorizontalPadding = 10.dp
private val AudioVolumeLabelMinWidth = 30.dp
private val AudioVolumeLabelMaxWidth = 120.dp
private val AudioVolumeLabelSpacing = 6.dp
private val AudioVolumeValueWidth = 50.dp
private val AudioVolumeSliderTrackHeight = 4.dp
private val AudioVolumeSliderThumbSize = 14.dp
private val AudioVolumeSliderThumbHaloSize = 22.dp
private val VideoPickerWidth = 104.dp
private val VideoPickerHeight = 42.dp
private val VideoPickerInnerMargin = 2.dp
private val VideoPickerItemGap = 2.dp
private val VideoPickerDragThreshold = 50.dp

private class EditableSessionAddress(
    type: SessionDeviceType = SessionDeviceType.TCP,
    host: String = "",
    port: String = "5555",
) {
    var type by mutableStateOf(type)
    var host by mutableStateOf(host)
    var port by mutableStateOf(port)
    var showTypeMenu by mutableStateOf(false)

    fun selectType(nextType: SessionDeviceType) {
        type = nextType
        when (nextType) {
            SessionDeviceType.USB -> port = "0"
            SessionDeviceType.MDNS -> port = "0"
            SessionDeviceType.TCP -> if (port.isBlank() || port == "0") port = "5555"
        }
    }

    fun toEndpoint(): String? {
        val normalizedHost =
            when (type) {
                SessionDeviceType.USB -> DeviceTransportSerial.stripUsbPrefix(host)
                SessionDeviceType.MDNS -> DeviceTransportSerial.mdnsDeviceSerial(host)
                SessionDeviceType.TCP -> parseTcpHostPort(host.trim())?.host ?: DeviceTransportSerial.stripTcpPrefix(
                    host
                )
            }
        if (normalizedHost.isBlank()) {
            return null
        }

        return when (type) {
            SessionDeviceType.USB -> ConnectionCandidate(
                ConnectionTransport.USB,
                normalizedHost,
                port = 0
            ).toAddressEndpoint()

            SessionDeviceType.MDNS -> ConnectionCandidate(
                ConnectionTransport.MDNS,
                normalizedHost,
                0
            ).toAddressEndpoint()

            SessionDeviceType.TCP -> {
                val parsed = parseTcpHostPort(host.trim())
                val fallbackPort =
                    parsed?.port
                        ?: port.trim().toIntOrNull()
                        ?: NetworkConstants.DEFAULT_ADB_PORT_INT
                ConnectionCandidate(ConnectionTransport.TCP, normalizedHost, fallbackPort).toAddressEndpoint()
            }
        }
    }

    companion object {
        fun from(endpoint: String): EditableSessionAddress {
            val value = endpoint.trim()
            val candidate = parseSessionAddressCandidate(value)
            return when {
                candidate != null ->
                    EditableSessionAddress(
                        type =
                            when (candidate.transport) {
                                ConnectionTransport.USB -> SessionDeviceType.USB
                                ConnectionTransport.MDNS -> SessionDeviceType.MDNS
                                ConnectionTransport.TCP -> SessionDeviceType.TCP
                            },
                        host = candidate.host,
                        port = candidate.port.coerceAtLeast(0).toString(),
                    )

                else -> {
                    val parsed = parseTcpHostPort(value)
                    EditableSessionAddress(
                        type = SessionDeviceType.TCP,
                        host = parsed?.host ?: DeviceTransportSerial.stripTcpPrefix(value),
                        port = parsed?.port?.toString() ?: "5555",
                    )
                }
            }
        }
    }
}

private class SessionAddressDialogState(
    source: SessionDialogState,
) {
    var deviceType by mutableStateOf(source.deviceType)
    var host by mutableStateOf(source.host)
    var port by mutableStateOf(source.port)
    var usbSerialNumber by mutableStateOf(source.usbSerialNumber)
    var backupAddresses by mutableStateOf(source.backupEndpoints.map { EditableSessionAddress.from(it) })
    var showDeviceTypeMenu by mutableStateOf(false)
    var showUsbDeviceDialog by mutableStateOf(false)
    var showMdnsServiceDialog by mutableStateOf(false)
    var selectedBackupUsbAddressIndex by mutableStateOf<Int?>(null)
    var selectedBackupMdnsAddressIndex by mutableStateOf<Int?>(null)

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

    fun addBackupEndpoint() {
        backupAddresses = backupAddresses + EditableSessionAddress()
    }

    fun removeBackupEndpoint(index: Int) {
        backupAddresses = backupAddresses.filterIndexed { itemIndex, _ -> itemIndex != index }
        selectedBackupUsbAddressIndex = selectedBackupUsbAddressIndex.adjustAfterRemoving(index)
        selectedBackupMdnsAddressIndex = selectedBackupMdnsAddressIndex.adjustAfterRemoving(index)
    }

    fun applyTo(target: SessionDialogState) {
        target.deviceType = deviceType
        target.host = host
        target.port = port
        target.updateUsbSerialNumber(usbSerialNumber)
        if (deviceType == SessionDeviceType.MDNS) {
            target.updateMdnsServiceName(host)
        }
        target.backupEndpoints =
            backupAddresses
                .mapNotNull { it.toEndpoint() }
    }
}

private fun Int?.adjustAfterRemoving(removedIndex: Int): Int? =
    when {
        this == null -> null
        this == removedIndex -> null
        this > removedIndex -> this - 1
        else -> this
    }

@Composable
private fun rememberMdnsSessionDiscoveryManager(): MdnsSessionDiscoveryManager {
    val manager = remember { MdnsSessionDiscoveryManager.get() }
    DisposableEffect(manager) {
        val lease = manager.acquireInteractiveDiscovery()
        onDispose {
            lease.close()
        }
    }
    return manager
}

@Composable
fun AddSessionDialog(
    sessionData: SessionData? = null,
    availableGroups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onConfirm: (SessionData) -> Unit,
) {
    val state = remember(sessionData) { SessionDialogState(sessionData) }
    val remoteAppCache = remember(state) { RemoteLaunchableAppCache() }
    val sessionGroups = remember(availableGroups) { availableGroups.filter { it.type == GroupType.SESSION } }
    val mdnsManager = rememberMdnsSessionDiscoveryManager()
    val mdnsState by mdnsManager.state.collectAsState()

    AddSessionDialogContent(
        state = state,
        isEditMode = sessionData != null,
        availableGroups = sessionGroups,
        onDismiss = onDismiss,
        onConnectionLatencyTest = { state.showConnectionLatencyTest = true },
        onConfirm = {
            onConfirm(state.toSessionData(sessionData?.id))
            onDismiss()
        },
    )

    AddSessionDialogOverlays(
        state = state,
        sessionId = sessionData?.id,
        availableGroups = sessionGroups,
        mdnsConnectServices = mdnsState.connectServices,
        mdnsConnectLoading = mdnsState.loading,
        remoteAppCache = remoteAppCache,
    )

    if (state.showConnectionLatencyTest && sessionData != null) {
        ConnectionLatencyTestPage(
            sessionData = state.toSessionData(sessionData.id),
            onBack = { state.showConnectionLatencyTest = false },
        )
    }
}

@Composable
private fun AddSessionDialogContent(
    state: SessionDialogState,
    isEditMode: Boolean,
    availableGroups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onConnectionLatencyTest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current

    DialogPage(
        title =
            if (isEditMode) {
                SessionTexts.SESSION_EDIT.get()
            } else {
                SessionTexts.SESSION_ADD.get()
            },
        onDismiss = onDismiss,
        leftButtonText = SessionTexts.SESSION_CANCEL.get(),
        rightButtonText = SessionTexts.SESSION_SAVE.get(),
        onRightButtonClick = {
            if (state.validate()) {
                if (isVideoCodecSelectionCompatible(state) && isAudioCodecSelectionCompatible(state)) {
                    onConfirm()
                } else {
                    showCodecProtocolMismatch(context)
                }
            }
        },
        enableScroll = true,
        scrollContentTopPadding = IosDesignTokens.dialogCompactHeaderSpacerHeight,
        scrollContentBottomPadding = IosDesignTokens.dialogCompactBottomSpacerHeight,
        verticalSpacing = 8.dp,
    ) {
        RemoteDeviceSection(
            state = state,
            availableGroups = availableGroups,
            onGroupSelectorClick = { state.showGroupSelector = true },
            onConnectionLatencyTest = onConnectionLatencyTest,
            showConnectionLatencyTest = isEditMode,
        )
        ConnectionOptionsSection(state)
        VideoConfigSection(state)
        AudioConfigSection(state)
        OtherOptionsSection(state)
        VirtualDisplaySection(state)
    }
}

@Composable
private fun AddSessionDialogOverlays(
    state: SessionDialogState,
    sessionId: String?,
    availableGroups: List<DeviceGroup>,
    mdnsConnectServices: List<MdnsDiscoveredConnectService>,
    mdnsConnectLoading: Boolean,
    remoteAppCache: RemoteLaunchableAppCache,
) {
    VideoEncoderSelectionOverlay(
        state = state,
        sessionId = sessionId,
    )
    VideoDecoderSelectionOverlay(state = state)
    AudioEncoderSelectionOverlay(
        state = state,
        sessionId = sessionId,
    )
    AudioDecoderSelectionOverlay(state = state)
    UsbDeviceSelectionOverlay(state)
    MdnsServiceSelectionOverlay(
        state = state,
        services = mdnsConnectServices,
        loading = mdnsConnectLoading,
    )
    GroupSelectionOverlay(
        state = state,
        availableGroups = availableGroups,
    )
    SessionAddressOverlay(
        state = state,
        mdnsConnectServices = mdnsConnectServices,
        mdnsConnectLoading = mdnsConnectLoading,
    )
    RemoteAppSelectionOverlay(
        state = state,
        sessionId = sessionId,
        cache = remoteAppCache,
    )
}

@Composable
private fun SessionDialogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SessionDialogSectionShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

@Composable
private fun RemoteDeviceSection(
    state: SessionDialogState,
    availableGroups: List<DeviceGroup>,
    onGroupSelectorClick: () -> Unit,
    onConnectionLatencyTest: () -> Unit,
    showConnectionLatencyTest: Boolean,
) {
    SessionDialogSection(title = SessionTexts.SECTION_REMOTE_DEVICE.get()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LabeledTextField(
                label = SessionTexts.LABEL_SESSION_NAME.get(),
                value = state.sessionName,
                onValueChange = { state.sessionName = it },
                placeholder = SessionTexts.PLACEHOLDER_SESSION_NAME.get(),
                helpText = SessionTexts.HELP_SESSION_NAME.get(),
            )

            AppDivider()

            CompactClickableRow(
                text = SessionTexts.LABEL_SESSION_ADDRESS.get(),
                trailingText = state.sessionAddressPreview(),
                onClick = { state.showSessionAddressDialog = true },
                helpText = SessionTexts.HELP_CONNECTION_ENDPOINTS.get(),
                showArrow = true,
            )

            AppDivider()

            CompactClickableRow(
                text = SessionTexts.GROUP_SELECT.get(),
                trailingText = formatGroupDisplay(state.selectedGroupIds, availableGroups),
                onClick = onGroupSelectorClick,
                helpText = SessionTexts.HELP_SELECT_GROUP.get(),
                showArrow = true,
            )

            if (showConnectionLatencyTest) {
                AppDivider()

                CompactClickableRow(
                    text = SessionTexts.LATENCY_TEST_ENTRY.get(),
                    trailingText = "10 × 10",
                    onClick = onConnectionLatencyTest,
                    helpText = SessionTexts.LATENCY_TEST_HELP.get(),
                    showArrow = true,
                )
            }
        }
    }
}

@Composable
private fun DeviceTypeDropdownRow(state: SessionDialogState) {
    LabeledDropdownRow(
        label = SessionTexts.LABEL_DEVICE_TYPE.get(),
        trailingText = state.deviceType.displayText(),
        onClick = { state.showDeviceTypeMenu = true },
        helpText = SessionTexts.HELP_DEVICE_TYPE.get(),
    ) {
        IOSStyledDropdownMenu(
            expanded = state.showDeviceTypeMenu,
            onDismissRequest = { state.showDeviceTypeMenu = false },
            alignment = Alignment.TopEnd,
        ) {
            SessionDeviceType.entries.forEach { type ->
                IOSStyledDropdownMenuItem(
                    text = type.displayText(),
                    onClick = {
                        state.showDeviceTypeMenu = false
                        state.selectDeviceType(type)
                    },
                )
            }
        }
    }
}

private fun SessionDeviceType.displayText(): String =
    when (this) {
        SessionDeviceType.TCP -> SessionTexts.DEVICE_TYPE_TCP.get()
        SessionDeviceType.USB -> SessionTexts.DEVICE_TYPE_USB.get()
        SessionDeviceType.MDNS -> SessionTexts.DEVICE_TYPE_MDNS.get()
    }

@Composable
private fun SessionAddressOverlay(
    state: SessionDialogState,
    mdnsConnectServices: List<MdnsDiscoveredConnectService>,
    mdnsConnectLoading: Boolean,
) {
    if (!state.showSessionAddressDialog) {
        return
    }

    val editorState = remember(state.showSessionAddressDialog) { SessionAddressDialogState(state) }

    DialogPage(
        title = SessionTexts.DIALOG_SESSION_ADDRESS_TITLE.get(),
        onDismiss = { state.showSessionAddressDialog = false },
        leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        editorState.applyTo(state)
                        state.showSessionAddressDialog = false
                    },
                ) {
                    Text(
                        text = CommonTexts.BUTTON_SAVE.get(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = editorState::addBackupEndpoint) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = SessionTexts.ACTION_ADD_BACKUP_ENDPOINT.get(),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        enableScroll = true,
        verticalSpacing = 8.dp,
    ) {
        PrimarySessionAddressCard(
            state = editorState,
            mdnsConnectServices = mdnsConnectServices,
            mdnsConnectLoading = mdnsConnectLoading,
            onUsbDeviceClick = { editorState.showUsbDeviceDialog = true },
        )

        BackupEndpointsEditor(
            state = editorState,
            mdnsConnectServices = mdnsConnectServices,
            mdnsConnectLoading = mdnsConnectLoading,
        )

        SessionAddressUsbDeviceSelectionOverlay(editorState)
        SessionAddressMdnsServiceSelectionOverlay(
            state = editorState,
            services = mdnsConnectServices,
            loading = mdnsConnectLoading,
        )
        BackupAddressUsbDeviceSelectionOverlay(editorState)
        BackupAddressMdnsServiceSelectionOverlay(
            state = editorState,
            services = mdnsConnectServices,
            loading = mdnsConnectLoading,
        )
    }
}

@Composable
private fun PrimarySessionAddressCard(
    state: SessionAddressDialogState,
    mdnsConnectServices: List<MdnsDiscoveredConnectService>,
    mdnsConnectLoading: Boolean,
    onUsbDeviceClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SessionDialogSectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        SessionAddressCardHeader(title = SessionTexts.LABEL_PRIMARY_ENDPOINT.get())

        AppDivider()

        PrimarySessionAddressEditor(
            state = state,
            mdnsConnectServices = mdnsConnectServices,
            mdnsConnectLoading = mdnsConnectLoading,
            onUsbDeviceClick = onUsbDeviceClick,
        )
    }
}

@Composable
private fun SessionAddressDeviceTypeDropdownRow(state: SessionAddressDialogState) {
    LabeledDropdownRow(
        label = SessionTexts.LABEL_DEVICE_TYPE.get(),
        trailingText = state.deviceType.displayText(),
        onClick = { state.showDeviceTypeMenu = true },
        helpText = SessionTexts.HELP_DEVICE_TYPE.get(),
    ) {
        IOSStyledDropdownMenu(
            expanded = state.showDeviceTypeMenu,
            onDismissRequest = { state.showDeviceTypeMenu = false },
            alignment = Alignment.TopEnd,
        ) {
            SessionDeviceType.entries.forEach { type ->
                IOSStyledDropdownMenuItem(
                    text = type.displayText(),
                    onClick = {
                        state.showDeviceTypeMenu = false
                        state.selectDeviceType(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionAddressUsbDeviceSelectionOverlay(state: SessionAddressDialogState) {
    if (!state.showUsbDeviceDialog) {
        return
    }

    UsbDeviceSelectionDialog(
        currentSerialNumber = state.usbSerialNumber,
        onDeviceSelected = { serialNumber, _ ->
            state.usbSerialNumber = normalizeUsbAddress(serialNumber)
            state.selectDeviceType(SessionDeviceType.USB)
            state.host = ""
            state.showUsbDeviceDialog = false
        },
        onDismiss = {
            state.showUsbDeviceDialog = false
        },
    )
}

@Composable
private fun BackupAddressUsbDeviceSelectionOverlay(state: SessionAddressDialogState) {
    val selectedIndex = state.selectedBackupUsbAddressIndex ?: return
    val address = state.backupAddresses.getOrNull(selectedIndex) ?: return

    UsbDeviceSelectionDialog(
        currentSerialNumber = address.host,
        onDeviceSelected = { serialNumber, _ ->
            address.selectType(SessionDeviceType.USB)
            address.host = normalizeUsbAddress(serialNumber)
            state.selectedBackupUsbAddressIndex = null
        },
        onDismiss = {
            state.selectedBackupUsbAddressIndex = null
        },
    )
}

@Composable
private fun SessionAddressMdnsServiceSelectionOverlay(
    state: SessionAddressDialogState,
    services: List<MdnsDiscoveredConnectService>,
    loading: Boolean,
) {
    if (!state.showMdnsServiceDialog) {
        return
    }

    MdnsDeviceSelectionDialog(
        services = services,
        loading = loading,
        selectedDeviceSerial = state.host,
        onSelected = { service ->
            state.deviceType = SessionDeviceType.MDNS
            state.host = service.deviceSerial
            state.port = "0"
            state.showMdnsServiceDialog = false
        },
        onDismiss = { state.showMdnsServiceDialog = false },
    )
}

@Composable
private fun BackupAddressMdnsServiceSelectionOverlay(
    state: SessionAddressDialogState,
    services: List<MdnsDiscoveredConnectService>,
    loading: Boolean,
) {
    val selectedIndex = state.selectedBackupMdnsAddressIndex ?: return
    val address = state.backupAddresses.getOrNull(selectedIndex) ?: return

    MdnsDeviceSelectionDialog(
        services = services,
        loading = loading,
        selectedDeviceSerial = address.host,
        onSelected = { service ->
            address.selectType(SessionDeviceType.MDNS)
            address.host = service.deviceSerial
            state.selectedBackupMdnsAddressIndex = null
        },
        onDismiss = { state.selectedBackupMdnsAddressIndex = null },
    )
}

@Composable
private fun PrimarySessionAddressEditor(
    state: SessionAddressDialogState,
    mdnsConnectServices: List<MdnsDiscoveredConnectService>,
    mdnsConnectLoading: Boolean,
    onUsbDeviceClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SessionAddressDeviceTypeDropdownRow(state)

        AppDivider()

        when (state.deviceType) {
            SessionDeviceType.USB -> {
                LabeledTextField(
                    label = AdbTexts.USB_SERIAL_NUMBER.get(),
                    value = state.usbSerialNumber,
                    onValueChange = { state.usbSerialNumber = normalizeUsbAddress(it) },
                    placeholder = "10AEAG2YZS0020P",
                    helpText = SessionTexts.HELP_USB_SERIAL.get(),
                )

                AppDivider()

                CompactClickableRow(
                    text = AdbTexts.USB_SELECT_DEVICE.get(),
                    trailingText =
                        state.usbSerialNumber.ifBlank {
                            AdbTexts.USB_NO_DEVICE_SELECTED.get()
                        },
                    onClick = onUsbDeviceClick,
                    showArrow = true,
                    helpText = SessionTexts.HELP_USB_SERIAL.get(),
                )
            }

            SessionDeviceType.MDNS -> {
                LabeledTextField(
                    label = SessionTexts.LABEL_MDNS_SERVICE.get(),
                    value = state.host,
                    onValueChange = {
                        state.host = normalizeMdnsAddress(it)
                        state.port = "0"
                    },
                    placeholder = "R5CW730QLKB",
                    helpText = SessionTexts.HELP_MDNS_SERVICE.get(),
                )

                AppDivider()

                CompactClickableRow(
                    text = SessionTexts.MDNS_CONNECT_SERVICES.get(),
                    trailingText =
                        when {
                            mdnsConnectServices.isNotEmpty() -> "${mdnsConnectServices.size}"
                            mdnsConnectLoading -> SessionTexts.MDNS_CONNECT_SCANNING.get()
                            else -> SessionTexts.MDNS_CONNECT_EMPTY.get()
                        },
                    onClick = { state.showMdnsServiceDialog = true },
                    showArrow = true,
                    helpText = SessionTexts.HELP_MDNS_SERVICE.get(),
                )
            }

            SessionDeviceType.TCP -> {
                LabeledTextField(
                    label = SessionTexts.LABEL_HOST.get(),
                    value = state.host,
                    onValueChange = { state.host = it },
                    placeholder = PlaceholderTexts.HOST,
                    helpText = SessionTexts.HELP_HOST.get(),
                )

                AppDivider()

                LabeledTextField(
                    label = SessionTexts.LABEL_PORT.get(),
                    value = state.port,
                    onValueChange = { state.port = it },
                    placeholder = PlaceholderTexts.PORT,
                    keyboardType = KeyboardType.Number,
                    helpText = SessionTexts.HELP_PORT.get(),
                )
            }
        }
    }
}

@Composable
private fun BackupEndpointsEditor(
    state: SessionAddressDialogState,
    mdnsConnectServices: List<MdnsDiscoveredConnectService>,
    mdnsConnectLoading: Boolean,
) {
    if (state.backupAddresses.isEmpty()) {
        Text(
            text = SessionTexts.HELP_CONNECTION_ENDPOINTS.get(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.backupAddresses.forEachIndexed { index, address ->
            SessionAddressItemEditor(
                address = address,
                mdnsConnectServices = mdnsConnectServices,
                mdnsConnectLoading = mdnsConnectLoading,
                onRemove = { state.removeBackupEndpoint(index) },
                onUsbDeviceClick = { state.selectedBackupUsbAddressIndex = index },
                onMdnsServicesClick = { state.selectedBackupMdnsAddressIndex = index },
            )
        }
    }
}

@Composable
private fun SessionAddressItemEditor(
    address: EditableSessionAddress,
    mdnsConnectServices: List<MdnsDiscoveredConnectService>,
    mdnsConnectLoading: Boolean,
    onRemove: () -> Unit,
    onUsbDeviceClick: () -> Unit,
    onMdnsServicesClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SessionDialogSectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        SessionAddressCardHeader(title = SessionTexts.LABEL_BACKUP_ENDPOINT.get()) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = SessionTexts.ACTION_REMOVE_ENDPOINT.get(),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        AppDivider()

        EditableAddressTypeDropdownRow(address)

        AppDivider()

        when (address.type) {
            SessionDeviceType.TCP -> {
                LabeledTextField(
                    label = SessionTexts.LABEL_HOST.get(),
                    value = address.host,
                    onValueChange = { address.host = it },
                    placeholder = PlaceholderTexts.HOST,
                    helpText = SessionTexts.HELP_HOST.get(),
                )

                AppDivider()

                LabeledTextField(
                    label = SessionTexts.LABEL_PORT.get(),
                    value = address.port,
                    onValueChange = { address.port = it },
                    placeholder = PlaceholderTexts.PORT,
                    keyboardType = KeyboardType.Number,
                    helpText = SessionTexts.HELP_PORT.get(),
                )
            }

            SessionDeviceType.USB -> {
                LabeledTextField(
                    label = AdbTexts.USB_SERIAL_NUMBER.get(),
                    value = address.host,
                    onValueChange = { address.host = normalizeUsbAddress(it) },
                    placeholder = "10AEAG2YZS0020P",
                    helpText = SessionTexts.HELP_USB_SERIAL.get(),
                )

                AppDivider()

                CompactClickableRow(
                    text = AdbTexts.USB_SELECT_DEVICE.get(),
                    trailingText =
                        address.host.ifBlank {
                            AdbTexts.USB_NO_DEVICE_SELECTED.get()
                        },
                    onClick = onUsbDeviceClick,
                    showArrow = true,
                    helpText = SessionTexts.HELP_USB_SERIAL.get(),
                )
            }

            SessionDeviceType.MDNS -> {
                LabeledTextField(
                    label = SessionTexts.LABEL_MDNS_SERVICE.get(),
                    value = address.host,
                    onValueChange = { address.host = normalizeMdnsAddress(it) },
                    placeholder = "R5CW730QLKB",
                    helpText = SessionTexts.HELP_MDNS_SERVICE.get(),
                )

                AppDivider()

                CompactClickableRow(
                    text = SessionTexts.MDNS_CONNECT_SERVICES.get(),
                    trailingText =
                        when {
                            mdnsConnectServices.isNotEmpty() -> "${mdnsConnectServices.size}"
                            mdnsConnectLoading -> SessionTexts.MDNS_CONNECT_SCANNING.get()
                            else -> SessionTexts.MDNS_CONNECT_EMPTY.get()
                        },
                    onClick = onMdnsServicesClick,
                    showArrow = true,
                    helpText = SessionTexts.HELP_MDNS_SERVICE.get(),
                )
            }
        }
    }
}

@Composable
private fun SessionAddressCardHeader(
    title: String,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
private fun EditableAddressTypeDropdownRow(address: EditableSessionAddress) {
    LabeledDropdownRow(
        label = SessionTexts.LABEL_DEVICE_TYPE.get(),
        trailingText = address.type.displayText(),
        onClick = { address.showTypeMenu = true },
        helpText = SessionTexts.HELP_DEVICE_TYPE.get(),
    ) {
        IOSStyledDropdownMenu(
            expanded = address.showTypeMenu,
            onDismissRequest = { address.showTypeMenu = false },
            alignment = Alignment.TopEnd,
        ) {
            SessionDeviceType.entries.forEach { type ->
                IOSStyledDropdownMenuItem(
                    text = type.displayText(),
                    onClick = {
                        address.showTypeMenu = false
                        address.selectType(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun MdnsServiceSelectionOverlay(
    state: SessionDialogState,
    services: List<MdnsDiscoveredConnectService>,
    loading: Boolean,
) {
    if (!state.showMdnsServiceDialog) {
        return
    }

    MdnsDeviceSelectionDialog(
        services = services,
        loading = loading,
        selectedDeviceSerial = state.host,
        onSelected = { service ->
            state.selectMdnsService(
                serviceName = service.deviceSerial,
                displayName = service.name,
            )
            state.showMdnsServiceDialog = false
        },
        onDismiss = { state.showMdnsServiceDialog = false },
    )
}

@Composable
private fun MdnsDeviceSelectionDialog(
    services: List<MdnsDiscoveredConnectService>,
    loading: Boolean,
    selectedDeviceSerial: String,
    onSelected: (MdnsDiscoveredConnectService) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        DialogContainer {
            DialogHeader(
                title = SessionTexts.MDNS_CONNECT_SERVICES.get(),
                onDismiss = onDismiss,
                showBackButton = false,
                leftButtonText = CommonTexts.BUTTON_CLOSE.get(),
                centerTitleInWindow = true,
            )

            DialogHeaderSpacer()

            MdnsServiceListContent(
                services = services,
                loading = loading,
                selectedDeviceSerial = selectedDeviceSerial,
                onSelected = { service ->
                    onSelected(service)
                    if (service.requiresPairingPrompt()) {
                        Toast
                            .makeText(
                                context,
                                SessionTexts.MDNS_PAIRING_REQUIRED.get(),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun MdnsServiceListContent(
    services: List<MdnsDiscoveredConnectService>,
    loading: Boolean,
    selectedDeviceSerial: String,
    onSelected: (MdnsDiscoveredConnectService) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.paddingStandard,
                    end = AppDimens.paddingStandard,
                    bottom = AppDimens.paddingStandard,
                ),
    ) {
        if (services.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = SessionTexts.MDNS_CONNECT_SCANNING.get(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = AppTextSizes.body,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                } else {
                    Text(
                        text = SessionTexts.MDNS_CONNECT_EMPTY.get(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = AppTextSizes.body,
                    )
                }
            }
        } else {
            val selectedServiceIndex =
                selectedMdnsServiceIndex(
                    services = services,
                    selectedDeviceSerial = selectedDeviceSerial,
                )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                services.forEachIndexed { index, service ->
                    MdnsServiceItem(
                        service = service,
                        isSelected = index == selectedServiceIndex,
                        onClick = { onSelected(service) },
                    )
                }
            }
        }
    }
}

internal fun selectedMdnsServiceIndex(
    services: List<MdnsDiscoveredConnectService>,
    selectedDeviceSerial: String,
): Int {
    val normalizedSerial = normalizeMdnsAddress(selectedDeviceSerial)
    if (normalizedSerial.isBlank()) {
        return -1
    }
    val matchingIndexes =
        services.indices.filter { index ->
            services[index].deviceSerial.equals(normalizedSerial, ignoreCase = true)
        }
    return matchingIndexes.firstOrNull { index -> !services[index].requiresPairing }
        ?: matchingIndexes.firstOrNull()
        ?: -1
}

internal fun MdnsDiscoveredConnectService.requiresPairingPrompt(): Boolean =
    requiresPairing || !previouslyPaired

@Composable
private fun MdnsServiceItem(
    service: MdnsDiscoveredConnectService,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
                .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.5.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = service.name,
                fontSize = AppTextSizes.body,
                maxLines = 1,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 12.dp),
            )

            Text(
                text =
                    if (service.previouslyPaired && !service.requiresPairing) {
                        AdbTexts.PAIRING_DISCOVERY_RECORDED.get()
                    } else {
                        SessionTexts.MDNS_DEVICE_UNPAIRED.get()
                    },
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (service.previouslyPaired && !service.requiresPairing) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                modifier = Modifier.padding(end = 8.dp),
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ConnectionOptionsSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_CONNECTION_OPTIONS.get()) {
        CompactSwitchRow(
            text = SessionTexts.SWITCH_GAME_MODE.get(),
            checked = state.config.gameMode,
            onCheckedChange = state::updateGameMode,
            helpText = SessionTexts.HELP_GAME_MODE.get(),
        )

        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_FULL_SCREEN.get(),
            checked = state.config.useFullScreen && !state.config.gameMode,
            onCheckedChange = { enabled -> state.updateConfig { copy(useFullScreen = enabled) } },
            helpText =
                if (state.config.gameMode) {
                    SessionTexts.HELP_GAME_MODE_FULL_SCREEN_DISABLED.get()
                } else {
                    SessionTexts.HELP_USE_FULL_SCREEN.get()
                },
            enabled = !state.config.gameMode,
        )

        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_SHOW_FLOATING_BALL.get(),
            checked = state.config.showFloatingBall,
            onCheckedChange = { enabled -> state.updateConfig { copy(showFloatingBall = enabled) } },
            helpText = SessionTexts.HELP_SHOW_FLOATING_BALL.get(),
        )
        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_ENABLE_HARDWARE_DECODING.get(),
            checked = state.config.enableHardwareDecoding,
            onCheckedChange = { enabled -> state.updateConfig { copy(enableHardwareDecoding = enabled) } },
            helpText = SessionTexts.HELP_ENABLE_HARDWARE_DECODING.get(),
        )

        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_FOLLOW_ORIENTATION.get(),
            checked = state.config.followRemoteOrientation,
            onCheckedChange = { enabled -> state.updateConfig { copy(followRemoteOrientation = enabled) } },
            helpText = SessionTexts.HELP_FOLLOW_ORIENTATION.get(),
        )

        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_USE_ADB_FORWARD.get(),
            checked = state.config.tunnelMode == ScrcpyTunnelMode.ADB_FORWARD,
            onCheckedChange = { enabled ->
                state.updateConfig {
                    copy(tunnelMode = if (enabled) ScrcpyTunnelMode.ADB_FORWARD else ScrcpyTunnelMode.DIRECT_ADB)
                }
            },
            helpText = SessionTexts.HELP_USE_ADB_FORWARD.get(),
        )

    }
}

@Composable
private fun VideoConfigSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_VIDEO_CONFIG.get()) {
        VerticalOptionPicker(
            label = SessionTexts.LABEL_MAX_SIZE.get(),
            value = state.maxSize,
            presets =
                if (state.config.gameMode) {
                    listOf("720" to "720", "1080" to "1080", "1920" to "1920")
                } else {
                    listOf(
                        "720" to "720",
                        "1080" to "1080",
                        "1920" to "1920",
                        SessionTexts.LABEL_ORIGINAL.get() to "",
                    )
                },
            customEnabled = !state.config.gameMode,
            emptyCustomFallback = "",
            alwaysApplyEmptyCustomFallback = true,
            onValueChange = { state.maxSize = it },
            helpText = if (state.config.gameMode) SessionTexts.HELP_GAME_MAX_SIZE.get() else SessionTexts.HELP_NORMAL_MAX_SIZE.get(),
        )

        AppDivider()

        VerticalOptionPicker(
            label = SessionTexts.LABEL_VIDEO_BITRATE.get(),
            value = state.videoBitrate,
            presets =
                if (state.config.gameMode) {
                    listOf(
                        "1M" to "1M",
                        "2M" to "2M",
                        "4M" to "4M",
                        "8M" to "8M",
                        "12M" to "12M",
                        "20M" to "20M",
                    )
                } else {
                    listOf(
                        "700K" to "700K",
                        "1M" to "1M",
                        "2M" to "2M",
                        "4M" to "4M",
                        "8M" to "8M",
                        "12M" to "12M",
                        "20M" to "20M",
                        "40M" to "40M",
                        "80M" to "80M",
                        "120M" to "120M"
                    )
                },
            customEnabled = !state.config.gameMode,
            customUnits = listOf("K", "M"),
            defaultCustomUnit = "M",
            emptyCustomFallback = ScrcpyConstants.DEFAULT_VIDEO_BITRATE,
            onValueChange = { state.videoBitrate = it },
            helpText =
                if (state.config.gameMode) {
                    SessionTexts.HELP_GAME_VIDEO_BITRATE.get()
                } else {
                    SessionTexts.HELP_NORMAL_VIDEO_BITRATE.get()
                },
        )

        AppDivider()

        VerticalOptionPicker(
            label = SessionTexts.LABEL_MAX_FPS.get(),
            value = state.maxFps,
            presets =
                if (state.config.gameMode) {
                    listOf("60" to "60", "90" to "90", "120" to "120")
                } else {
                    listOf("15" to "15", "30" to "30", "60" to "60", "90" to "90", "120" to "120")
                },
            customEnabled = !state.config.gameMode,
            emptyCustomFallback = ScrcpyConstants.DEFAULT_MAX_FPS.toString(),
            onValueChange = { state.maxFps = it },
            helpText = if (state.config.gameMode) SessionTexts.HELP_GAME_MAX_FPS.get() else SessionTexts.HELP_NORMAL_MAX_FPS.get(),
        )

        AppDivider()

        LabeledClickableRow(
            label = SessionTexts.LABEL_VIDEO_ENCODER.get(),
            trailingText =
                when {
                    !state.hasValidDevice() -> SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
                    state.config.userVideoEncoder.isNotEmpty() -> state.config.userVideoEncoder
                    else -> SessionTexts.LABEL_DEFAULT.get()
                },
            onClick = {
                if (state.hasValidDevice()) {
                    state.showEncoderOptionsDialog = true
                }
            },
            helpText = SessionTexts.HELP_VIDEO_ENCODER.get(),
        )

        AppDivider()

        LabeledClickableRow(
            label = SessionTexts.LABEL_VIDEO_DECODER.get(),
            trailingText = state.config.userVideoDecoder.ifEmpty { SessionTexts.LABEL_DEFAULT.get() },
            onClick = { state.showVideoDecoderSelector = true },
            helpText = SessionTexts.HELP_VIDEO_DECODER.get(),
        )
    }
}

@Composable
private fun VerticalOptionPicker(
    label: String,
    value: String,
    presets: List<Pair<String, String>>,
    customEnabled: Boolean,
    customUnits: List<String> = emptyList(),
    defaultCustomUnit: String = "",
    emptyCustomFallback: String? = null,
    alwaysApplyEmptyCustomFallback: Boolean = false,
    onValueChange: (String) -> Unit,
    helpText: String,
) {
    val displayedValue =
        if (value.isBlank() && !alwaysApplyEmptyCustomFallback) {
            emptyCustomFallback.orEmpty()
        } else {
            value
        }
    val matchesPreset = presets.any { (_, storedValue) -> storedValue.equals(displayedValue, ignoreCase = true) }
    var customValue by remember(customEnabled) {
        mutableStateOf(if (customEnabled && !matchesPreset && displayedValue.isNotBlank()) displayedValue else "")
    }
    val options =
        if (customEnabled) {
            presets + (SessionTexts.LABEL_CUSTOM.get() to customValue)
        } else {
            presets
        }
    var pendingCustomSelection by remember { mutableStateOf(false) }
    val selectedIndex =
        if (pendingCustomSelection) {
            options.lastIndex
        } else {
            options.indexOfFirst { (_, storedValue) -> storedValue.equals(displayedValue, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?: 0
        }
    val selectedIsCustom = customEnabled && selectedIndex == options.lastIndex
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showCustomEditor by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { VideoPickerDragThreshold.toPx() }
    val innerPickerHeight = VideoPickerHeight - VideoPickerInnerMargin * 2
    val innerPickerHeightPx = with(density) { innerPickerHeight.toPx() }
    val pickerHeightPx = with(density) { VideoPickerHeight.toPx() }
    val itemPitchPx = innerPickerHeightPx + with(density) { VideoPickerItemGap.toPx() }
    val renderedDragSteps = if (isDragging) -accumulatedDrag / dragThresholdPx else 0f
    fun optionIndexForDrag(): Int {
        val optionSteps = (-accumulatedDrag / dragThresholdPx).roundToInt()
        return (selectedIndex + optionSteps).coerceIn(options.indices)
    }

    val dragState =
        rememberDraggableState { delta ->
            val maximumUpwardDrag = -(options.lastIndex - selectedIndex) * dragThresholdPx
            val maximumDownwardDrag = selectedIndex * dragThresholdPx
            accumulatedDrag =
                (accumulatedDrag + delta).coerceIn(
                    maximumUpwardDrag,
                    maximumDownwardDrag,
                )
        }

    val pickerColumnOffset =
        pickerHeightPx / 2f -
            innerPickerHeightPx / 2f -
            (selectedIndex + renderedDragSteps) * itemPitchPx

    fun optionLabel(index: Int): String =
        if (customEnabled && index == options.lastIndex) {
            customValue.ifBlank { SessionTexts.LABEL_CUSTOM.get() }
        } else {
            options[index].first
        }

    fun settleSelection() {
        val nextIndex = optionIndexForDrag()
        if (nextIndex != selectedIndex) {
            if (customEnabled && nextIndex == options.lastIndex) {
                pendingCustomSelection = true
                showCustomEditor = true
            } else {
                pendingCustomSelection = false
                onValueChange(options[nextIndex].second)
            }
        }
        accumulatedDrag = 0f
        isDragging = false
    }

    LabeledRow(
        label = label,
        helpText = helpText,
    ) {
        Box(
            modifier =
                Modifier
                    .width(VideoPickerWidth)
                    .height(VideoPickerHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        startDragImmediately = true,
                        onDragStarted = {
                            accumulatedDrag = 0f
                            isDragging = true
                        },
                        onDragStopped = { settleSelection() },
                    )
                    .clickable(enabled = selectedIsCustom) {
                        pendingCustomSelection = false
                        showCustomEditor = true
                    },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer { translationY = pickerColumnOffset },
                verticalArrangement = Arrangement.spacedBy(VideoPickerItemGap),
            ) {
                options.indices.forEach { optionIndex ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(innerPickerHeight)
                                .padding(horizontal = VideoPickerInnerMargin)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color =
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = if (isDragging) 0.55f else 0.22f,
                                        ),
                                    shape = RoundedCornerShape(6.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = optionLabel(optionIndex),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (showCustomEditor) {
        var editorValue by remember(customValue) {
            mutableStateOf(customValue.filter(Char::isDigit))
        }
        var editorUnit by remember(customValue, customUnits) {
            mutableStateOf(
                customUnits.firstOrNull { unit -> customValue.endsWith(unit, ignoreCase = true) }
                    ?: defaultCustomUnit,
            )
        }
        val parsedValue = editorValue.toIntOrNull()?.takeIf { it > 0 }
        val dismissCustomEditor = {
            if (pendingCustomSelection) {
                if (alwaysApplyEmptyCustomFallback || value.isBlank()) {
                    emptyCustomFallback?.let(onValueChange)
                }
            }
            pendingCustomSelection = false
            showCustomEditor = false
        }
        SessionManagementCenteredDialog(
            title = "${SessionTexts.LABEL_CUSTOM.get()} $label",
            onDismiss = dismissCustomEditor,
            leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
            rightButtonText = CommonTexts.BUTTON_CONFIRM.get(),
            onRightButtonClick = {
                val storedValue = "$parsedValue$editorUnit"
                customValue = storedValue
                onValueChange(storedValue)
                pendingCustomSelection = false
                showCustomEditor = false
            },
            rightButtonEnabled = parsedValue != null,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = editorValue,
                    onValueChange = { next -> editorValue = next.filter(Char::isDigit) },
                    label = { Text(label) },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (customUnits.isNotEmpty()) {
                    val currentUnitIndex = customUnits.indexOf(editorUnit).coerceAtLeast(0)
                    Surface(
                        modifier =
                            Modifier
                                .width(52.dp)
                                .height(48.dp)
                                .clickable {
                                    editorUnit = customUnits[(currentUnitIndex + 1) % customUnits.size]
                                },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = editorUnit,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioConfigSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_AUDIO_CONFIG.get()) {
        CompactSwitchRow(
            text = SessionTexts.SWITCH_ENABLE_AUDIO.get(),
            checked = state.config.enableAudio,
            onCheckedChange = { enabled -> state.updateConfig { copy(enableAudio = enabled) } },
            helpText = SessionTexts.HELP_ENABLE_AUDIO.get(),
        )

        if (state.config.enableAudio) {
            AppDivider()

            VerticalOptionPicker(
                label = SessionTexts.LABEL_AUDIO_BITRATE.get(),
                value = state.audioBitrate,
                presets =
                    if (state.config.gameMode) {
                        listOf("64K" to "64K", "128K" to "128K")
                    } else {
                        listOf("64K" to "64K", "128K" to "128K", "192K" to "192K", "256K" to "256K")
                    },
                customEnabled = true,
                customUnits = listOf("K", "M"),
                defaultCustomUnit = "K",
                emptyCustomFallback = "128K",
                onValueChange = { state.audioBitrate = it },
                helpText = SessionTexts.HELP_AUDIO_BITRATE_PICKER.get(),
            )

            AppDivider()

            LabeledClickableRow(
                label = SessionTexts.LABEL_AUDIO_ENCODER.get(),
                trailingText =
                    when {
                        !state.hasValidDevice() -> SessionTexts.ENCODER_ERROR_INPUT_HOST.get()
                        state.config.userAudioEncoder.isNotEmpty() -> state.config.userAudioEncoder
                        else -> SessionTexts.LABEL_DEFAULT.get()
                    },
                onClick = {
                    if (state.hasValidDevice()) {
                        state.showAudioEncoderDialog = true
                    }
                },
                helpText = SessionTexts.HELP_AUDIO_ENCODER.get(),
            )

            AppDivider()

            LabeledClickableRow(
                label = SessionTexts.LABEL_AUDIO_DECODER.get(),
                trailingText = state.config.userAudioDecoder.ifEmpty { SessionTexts.LABEL_DEFAULT.get() },
                onClick = { state.showAudioDecoderSelector = true },
                helpText = SessionTexts.HELP_AUDIO_DECODER.get(),
            )

            AppDivider()

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .padding(horizontal = AudioVolumeRowHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AudioVolumeLabelSpacing),
                    modifier =
                        Modifier
                            .widthIn(min = AudioVolumeLabelMinWidth, max = AudioVolumeLabelMaxWidth)
                            .wrapContentWidth(),
                ) {
                    Text(
                        SessionTexts.LABEL_AUDIO_VOLUME.get(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HelpIcon(helpText = SessionTexts.HELP_AUDIO_VOLUME.get())
                }
                Slider(
                    value = state.audioVolume,
                    onValueChange = { state.audioVolume = it },
                    valueRange = 0.1f..2.0f,
                    steps = 18,
                    modifier = Modifier.weight(1f),
                    thumb = {
                        Box(
                            modifier =
                                Modifier
                                    .size(AudioVolumeSliderThumbHaloSize)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(AudioVolumeSliderThumbSize)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                        ),
                            )
                        }
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(AudioVolumeSliderTrackHeight),
                            colors =
                                SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                                ),
                            drawStopIndicator = null,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 2.dp,
                        )
                    },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                        ),
                )
                Text(
                    "${String.format("%.1f", state.audioVolume)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(AudioVolumeValueWidth),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

internal fun isVideoCodecSelectionCompatible(state: SessionDialogState): Boolean =
    CodecUtils.isEncoderDecoderCompatible(
        encoders = state.capabilityCache.remoteVideoEncoders,
        encoderName = state.config.userVideoEncoder,
        decoderName = state.config.userVideoDecoder,
        mediaType = CodecMediaType.VIDEO,
    )

internal fun isAudioCodecSelectionCompatible(state: SessionDialogState): Boolean =
    !state.config.enableAudio ||
        CodecUtils.isEncoderDecoderCompatible(
            encoders = state.capabilityCache.remoteAudioEncoders,
            encoderName = state.config.userAudioEncoder,
            decoderName = state.config.userAudioDecoder,
            mediaType = CodecMediaType.AUDIO,
        )

@Composable
private fun OtherOptionsSection(state: SessionDialogState) {
    SessionDialogSection(title = SessionTexts.SECTION_OTHER_OPTIONS.get()) {
        CompactSwitchRow(
            text = SessionTexts.SWITCH_CLIPBOARD_SYNC.get(),
            checked = state.config.clipboardSync,
            onCheckedChange = { enabled -> state.updateConfig { copy(clipboardSync = enabled) } },
            helpText = SessionTexts.HELP_CLIPBOARD_SYNC.get(),
        )
        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_TURN_SCREEN_OFF.get(),
            checked = state.config.turnScreenOff,
            onCheckedChange = { enabled -> state.updateConfig { copy(turnScreenOff = enabled) } },
            helpText = SessionTexts.HELP_TURN_SCREEN_OFF.get(),
        )
        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_POWER_OFF_ON_CLOSE.get(),
            checked = state.config.powerOffOnClose,
            onCheckedChange = { enabled -> state.updateConfig { copy(powerOffOnClose = enabled) } },
            helpText = SessionTexts.HELP_POWER_OFF_ON_CLOSE.get(),
        )
        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_CLEANUP_ON_DISCONNECT.get(),
            checked = state.config.cleanupOnDisconnect,
            onCheckedChange = { cleanupEnabled ->
                state.updateConfig {
                    copy(
                        cleanupOnDisconnect = cleanupEnabled,
                        stayAwake = stayAwake && cleanupEnabled,
                    )
                }
            },
            helpText = SessionTexts.HELP_CLEANUP_ON_DISCONNECT.get(),
        )
        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_KEEP_DEVICE_AWAKE.get(),
            checked = state.config.keepDeviceAwake,
            onCheckedChange = { enabled -> state.updateConfig { copy(keepDeviceAwake = enabled) } },
            helpText = SessionTexts.HELP_KEEP_DEVICE_AWAKE.get(),
        )
        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_STAY_AWAKE.get(),
            checked = state.config.stayAwake && state.config.cleanupOnDisconnect,
            onCheckedChange = { enabled -> state.updateConfig { copy(stayAwake = enabled) } },
            helpText = SessionTexts.HELP_STAY_AWAKE.get(),
            enabled = state.config.cleanupOnDisconnect,
        )

        AppDivider()

        CompactSwitchRow(
            text = SessionTexts.SWITCH_IGNORE_VIDEO_ENCODER_CONSTRAINTS.get(),
            checked = state.config.ignoreVideoEncoderConstraints,
            onCheckedChange = { enabled -> state.updateConfig { copy(ignoreVideoEncoderConstraints = enabled) } },
            helpText = SessionTexts.HELP_IGNORE_VIDEO_ENCODER_CONSTRAINTS.get(),
        )
    }
}

@Composable
private fun VirtualDisplaySection(state: SessionDialogState) {
    val context = LocalContext.current

    SessionDialogSection(title = SessionTexts.SECTION_VIRTUAL_DISPLAY.get()) {
        CompactSwitchRow(
            text = SessionTexts.SWITCH_NEW_DISPLAY.get(),
            checked = state.config.newDisplayEnabled,
            onCheckedChange = { enabled -> state.updateConfig { copy(newDisplayEnabled = enabled) } },
            helpText = SessionTexts.HELP_NEW_DISPLAY.get(),
        )

        if (state.config.newDisplayEnabled) {
            AppDivider()

            CompactSwitchRow(
                text = SessionTexts.SWITCH_VIRTUAL_DISPLAY_SYSTEM_DECORATIONS.get(),
                checked = state.config.virtualDisplaySystemDecorations,
                onCheckedChange = { enabled -> state.updateConfig { copy(virtualDisplaySystemDecorations = enabled) } },
                helpText = SessionTexts.HELP_VIRTUAL_DISPLAY_SYSTEM_DECORATIONS.get(),
            )
            AppDivider()

            CompactSwitchRow(
                text = SessionTexts.SWITCH_PRESERVE_VIRTUAL_DISPLAY_CONTENT.get(),
                checked = state.config.preserveVirtualDisplayContent,
                onCheckedChange = { enabled -> state.updateConfig { copy(preserveVirtualDisplayContent = enabled) } },
                helpText = SessionTexts.HELP_PRESERVE_VIRTUAL_DISPLAY_CONTENT.get(),
            )
            AppDivider()

            LabeledRow(
                label = SessionTexts.LABEL_START_APP.get(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactTextField(
                        value = state.config.startApp,
                        onValueChange = { value -> state.updateConfig { copy(startApp = value.trim()) } },
                        placeholder = SessionTexts.PLACEHOLDER_START_APP.get(),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { state.showRemoteAppSelector = true },
                        modifier = Modifier.widthIn(min = 56.dp),
                    ) {
                        Text(SessionTexts.ACTION_SELECT_REMOTE_APP.get())
                    }
                }
            }
            AppDivider()

            LabeledRow(label = SessionTexts.LABEL_NEW_DISPLAY_SIZE.get()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineDisplayMetricField(
                        value = state.newDisplayWidth,
                        onValueChange = { state.newDisplayWidth = it.filter(Char::isDigit) },
                        placeholder = SessionTexts.LABEL_NEW_DISPLAY_WIDTH.get(),
                        modifier = Modifier.weight(1f),
                    )
                    Text("×", modifier = Modifier.padding(horizontal = 3.dp))
                    InlineDisplayMetricField(
                        value = state.newDisplayHeight,
                        onValueChange = { state.newDisplayHeight = it.filter(Char::isDigit) },
                        placeholder = SessionTexts.LABEL_NEW_DISPLAY_HEIGHT.get(),
                        modifier = Modifier.weight(1f),
                    )
                    Text("×", modifier = Modifier.padding(horizontal = 3.dp))
                    InlineDisplayMetricField(
                        value = state.newDisplayDpi,
                        onValueChange = { state.newDisplayDpi = it.filter(Char::isDigit) },
                        placeholder = SessionTexts.LABEL_NEW_DISPLAY_DPI.get(),
                        modifier = Modifier.weight(0.8f),
                    )
                    TextButton(
                        onClick = {
                            val display = context.resolveLocalDisplaySpec()
                            state.newDisplayWidth = display.width.toString()
                            state.newDisplayHeight = display.height.toString()
                            state.newDisplayDpi = display.densityDpi.takeIf { it > 0 }?.toString().orEmpty()
                        },
                        modifier = Modifier.width(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(SessionTexts.ACTION_SYNC_LOCAL_DISPLAY_SIZE.get())
                    }
                    TextButton(
                        onClick = {
                            state.newDisplayWidth = ""
                            state.newDisplayHeight = ""
                            state.newDisplayDpi = ""
                        },
                        modifier = Modifier.width(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(SessionTexts.ACTION_CLEAR_NEW_DISPLAY_SIZE.get())
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineDisplayMetricField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp),
        textStyle =
            MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

private data class RemoteLaunchableApp(
    val packageName: String,
)

private class RemoteLaunchableAppCache {
    private val appsByDevice = mutableMapOf<String, List<RemoteLaunchableApp>>()

    fun get(deviceKey: String): List<RemoteLaunchableApp>? = appsByDevice[deviceKey]

    fun put(
        deviceKey: String,
        apps: List<RemoteLaunchableApp>,
    ) {
        appsByDevice[deviceKey] = apps
    }
}

@Composable
private fun RemoteAppSelectionOverlay(
    state: SessionDialogState,
    sessionId: String?,
    cache: RemoteLaunchableAppCache,
) {
    if (!state.showRemoteAppSelector) return

    val context = LocalContext.current
    val connectionManager = remember(context) { AdbConnectionManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<RemoteLaunchableApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }

    fun search() {
        val keyword = query.trim()
        if (loading) return
        val candidates = state.codecDetectionCandidates(sessionId)
        val deviceKey = candidates.sortedBy(ConnectionCandidate::priority).joinToString("|") { it.deviceIdentifier() }
        val filterApps: (List<RemoteLaunchableApp>) -> List<RemoteLaunchableApp> = { source ->
            if (keyword.isEmpty()) {
                source
            } else {
                source.filter { it.packageName.contains(keyword, ignoreCase = true) }
            }
        }
        cache.get(deviceKey)?.let { cached ->
            apps = filterApps(cached)
            error = null
            hasSearched = true
            return
        }

        loading = true
        error = null
        hasSearched = true
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val connection =
                            candidates
                                .sortedBy(ConnectionCandidate::priority)
                                .firstNotNullOfOrNull { candidate ->
                                    connectionManager.connectCandidate(candidate).getOrNull()
                                } ?: error("No reachable remote device")
                        val remoteFilter =
                            keyword.takeIf(String::isNotEmpty)?.let { value ->
                                " | grep -i -F -- ${quoteShellArg(value)} || true"
                            }.orEmpty()
                        val launcherOutput =
                            connection.executeShell(
                                "cmd package query-activities --brief -a android.intent.action.MAIN " +
                                    "-c android.intent.category.LAUNCHER$remoteFilter",
                                retryOnFailure = false,
                            ).getOrThrow()
                        val homeOutput =
                            connection.executeShell(
                                "cmd package query-activities --brief -a android.intent.action.MAIN " +
                                    "-c android.intent.category.HOME$remoteFilter",
                                retryOnFailure = false,
                            ).getOrNull().orEmpty()
                        parseRemoteLaunchableApps("$launcherOutput\n$homeOutput")
                    }
                }
            result.onSuccess { loadedApps ->
                if (keyword.isEmpty()) {
                    cache.put(deviceKey, loadedApps)
                }
                apps = filterApps(loadedApps)
            }
            result.onFailure { failure ->
                error = failure.message ?: SessionTexts.ERROR_REMOTE_APP_LIST.get()
            }
            loading = false
        }
    }

    SessionManagementCenteredDialog(
        title = SessionTexts.DIALOG_SELECT_REMOTE_APP.get(),
        onDismiss = { state.showRemoteAppSelector = false },
        contentPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(SessionTexts.PLACEHOLDER_SEARCH_REMOTE_APP.get()) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = ::search,
                enabled = !loading,
                modifier = Modifier.widthIn(min = 64.dp),
            ) {
                Text(
                    if (query.isBlank()) {
                        SessionTexts.ACTION_QUERY_ALL_REMOTE_APPS.get()
                    } else {
                        "Q"
                    },
                )
            }
        }
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            text = SessionTexts.STATUS_LOADING_REMOTE_APPS.get(),
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            error != null -> {
                Text(
                    text = "${SessionTexts.ERROR_REMOTE_APP_LIST.get()}: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            !hasSearched -> {
                Text(
                    text = SessionTexts.STATUS_ENTER_REMOTE_APP_QUERY.get(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            apps.isEmpty() -> {
                Text(
                    text = SessionTexts.STATUS_NO_REMOTE_APPS.get(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(apps, key = RemoteLaunchableApp::packageName) { app ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        state.updateConfig { copy(startApp = app.packageName) }
                                        state.showRemoteAppSelector = false
                                    },
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = app.packageName,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        AppDivider()
                    }
                }
            }
        }
    }
}

private fun parseRemoteLaunchableApps(output: String): List<RemoteLaunchableApp> =
    output
        .lineSequence()
        .map(String::trim)
        .mapNotNull { line ->
            val component = line.substringAfterLast(' ').substringBefore('/')
            component.takeIf { it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }
        }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .map(::RemoteLaunchableApp)
        .toList()

@Composable
private fun VideoEncoderSelectionOverlay(
    state: SessionDialogState,
    sessionId: String?,
) {
    if (!state.showEncoderOptionsDialog) {
        return
    }

    EncoderSelectionDialog(
        encoderType = EncoderType.VIDEO,
        connectionCandidates = state.codecDetectionCandidates(sessionId),
        currentEncoder = state.config.userVideoEncoder,
        currentCodec = state.capabilityCache.selectedVideoCodec,
        cachedEncoders = state.capabilityCache.remoteVideoEncoders,
        onDismiss = { state.showEncoderOptionsDialog = false },
        onEncoderSelected = { encoder, _ ->
            state.updateConfig { copy(userVideoEncoder = encoder) }
            state.updateCapabilityCache {
                copy(selectedVideoCodec = "", selectedVideoEncoder = "", selectedVideoDecoder = "")
            }
            state.showEncoderOptionsDialog = false
        },
        onEncodersDetected = { encoders ->
            state.updateCapabilityCache { copy(remoteVideoEncoders = encoders) }
        },
    )
}

@Composable
private fun VideoDecoderSelectionOverlay(
    state: SessionDialogState,
) {
    if (!state.showVideoDecoderSelector) {
        return
    }

    VideoCodecSelectorScreen(
        currentCodecName = state.config.userVideoDecoder.ifBlank { null },
        onCodecSelected = { decoder ->
            state.updateConfig { copy(userVideoDecoder = decoder) }
            state.updateCapabilityCache {
                copy(selectedVideoCodec = "", selectedVideoEncoder = "", selectedVideoDecoder = "")
            }
            state.showVideoDecoderSelector = false
        },
        onBack = {
            state.showVideoDecoderSelector = false
        },
    )
}

@Composable
private fun AudioEncoderSelectionOverlay(
    state: SessionDialogState,
    sessionId: String?,
) {
    if (!state.showAudioEncoderDialog) {
        return
    }

    EncoderSelectionDialog(
        encoderType = EncoderType.AUDIO,
        connectionCandidates = state.codecDetectionCandidates(sessionId),
        currentEncoder = state.config.userAudioEncoder,
        currentCodec = state.capabilityCache.selectedAudioCodec,
        cachedEncoders = state.capabilityCache.remoteAudioEncoders,
        onDismiss = { state.showAudioEncoderDialog = false },
        onEncoderSelected = { encoder, _ ->
            state.updateConfig { copy(userAudioEncoder = encoder) }
            state.updateCapabilityCache {
                copy(selectedAudioCodec = "", selectedAudioEncoder = "", selectedAudioDecoder = "")
            }
            state.showAudioEncoderDialog = false
        },
        onEncodersDetected = { encoders ->
            state.updateCapabilityCache { copy(remoteAudioEncoders = encoders) }
        },
    )
}

@Composable
private fun AudioDecoderSelectionOverlay(
    state: SessionDialogState,
) {
    if (!state.showAudioDecoderSelector) {
        return
    }

    AudioCodecSelectorScreen(
        currentCodecName = state.config.userAudioDecoder.ifBlank { null },
        onCodecSelected = { decoder ->
            state.updateConfig { copy(userAudioDecoder = decoder) }
            state.updateCapabilityCache {
                copy(selectedAudioCodec = "", selectedAudioEncoder = "", selectedAudioDecoder = "")
            }
            state.showAudioDecoderSelector = false
        },
        onBack = {
            state.showAudioDecoderSelector = false
        },
    )
}

@Composable
private fun UsbDeviceSelectionOverlay(state: SessionDialogState) {
    if (!state.showUsbDeviceDialog) {
        return
    }

    UsbDeviceSelectionDialog(
        currentSerialNumber = state.usbSerialNumber,
        onDeviceSelected = { serialNumber, deviceName ->
            state.updateUsbSerialNumber(serialNumber)
            state.selectDeviceType(SessionDeviceType.USB)
            state.host = ""
            state.showUsbDeviceDialog = false

            if (state.sessionName.isBlank()) {
                state.sessionName = deviceName
            }
        },
        onDismiss = {
            state.showUsbDeviceDialog = false
            if (state.usbSerialNumber.isBlank()) {
                state.host = ""
                state.selectDeviceType(SessionDeviceType.TCP)
            }
        },
    )
}

@Composable
private fun GroupSelectionOverlay(
    state: SessionDialogState,
    availableGroups: List<DeviceGroup>,
) {
    if (!state.showGroupSelector) {
        return
    }

    GroupSelectorDialog(
        selectedGroupIds = state.selectedGroupIds,
        availableGroups = availableGroups,
        onGroupsSelected = { selectedIds ->
            state.selectedGroupIds = selectedIds
            state.showGroupSelector = false
        },
        onDismiss = {
            state.showGroupSelector = false
        },
    )
}

private fun formatGroupDisplay(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
): String {
    if (selectedGroupIds.isEmpty()) {
        return SessionTexts.GROUP_UNGROUPED.get()
    }

    val groupNames = availableGroups.filter { it.id in selectedGroupIds }.map { it.name }
    if (groupNames.isEmpty()) {
        return SessionTexts.GROUP_UNGROUPED.get()
    }

    return if (groupNames.size <= 3) {
        groupNames.joinToString(", ")
    } else {
        val firstThree = groupNames.take(3).joinToString(", ")
        val remaining = groupNames.size - 3
        "$firstThree +$remaining"
    }
}

private fun normalizeUsbAddress(raw: String): String =
    DeviceTransportSerial.stripUsbPrefix(raw)

internal fun SessionDialogState.codecDetectionCandidates(sessionId: String?): List<ConnectionCandidate> =
    toSessionData(sessionId ?: "codec-detection").toConnectionCandidates()

private fun normalizeMdnsAddress(raw: String): String =
    DeviceTransportSerial.mdnsDeviceSerial(raw)

private fun showCodecProtocolMismatch(context: Context) {
    Toast
        .makeText(
            context,
            CodecTexts.CODEC_PROTOCOL_MISMATCH.get(),
            Toast.LENGTH_SHORT,
        ).show()
}

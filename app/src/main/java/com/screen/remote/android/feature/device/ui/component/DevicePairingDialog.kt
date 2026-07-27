package com.screen.remote.android.feature.device.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screen.remote.android.core.common.AdbPairingConstants
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.util.formatHostPort
import com.screen.remote.android.core.common.util.parseHostPort
import com.screen.remote.android.core.designsystem.component.AppDivider
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.designsystem.component.SectionTitle
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.feature.device.data.PairingHistoryItem
import com.screen.remote.android.feature.device.data.PairingResult
import com.screen.remote.android.feature.device.data.PairingStatus
import com.screen.remote.android.feature.device.viewmodel.DevicePairingViewModel
import com.screen.remote.android.feature.session.ui.component.LabeledTextField
import com.screen.remote.android.infrastructure.adb.mdns.MdnsDiscoveredConnectService
import com.screen.remote.android.infrastructure.adb.mdns.MdnsSessionDiscoveryManager
import kotlin.time.Duration.Companion.milliseconds

/**
 * ADB 配对码配对对话框入口。
 *
 * 入口层只负责状态装配、提交流程和委托子视图。
 */
@Composable
fun AdbPairingCodeDialog(
    onDismiss: () -> Unit,
    viewModel: DevicePairingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val pairingStatus by viewModel.pairingStatus.collectAsState()
    val pairingResult by viewModel.pairingResult.collectAsState()
    val pairingHistory by viewModel.pairingHistory.collectAsState()
    val mdnsManager = remember { MdnsSessionDiscoveryManager.get() }
    val mdnsState by mdnsManager.state.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var hostPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var selectedMdnsDeviceSerial by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPairingHistory(context)
    }

    DisposableEffect(mdnsManager) {
        val lease = mdnsManager.acquireInteractiveDiscovery()
        onDispose {
            lease.close()
        }
    }

    LaunchedEffect(pairingResult) {
        pairingResult?.let { result ->
            if (result.success) {
                kotlinx.coroutines.delay(2000.milliseconds)
                viewModel.resetPairingStatus()
            }
        }
    }

    DialogPage(
        title = AdbTexts.PAIRING_TITLE.get(),
        onDismiss = {
            viewModel.resetPairingStatus()
            onDismiss()
        },
        showBackButton = true,
        enableScroll = true,
        rightButtonText = AdbTexts.BUTTON_PAIR.get(),
        rightButtonEnabled = hostPort.isNotEmpty() && pairingCode.isNotEmpty(),
        onRightButtonClick = {
            performPairing(
                hostPort = hostPort,
                pairingCode = pairingCode,
                onError = { errorMessage = it },
                onPair = { host, port, code ->
                    viewModel.pairWithCode(
                        context = context,
                        ipAddress = host,
                        port = port,
                        pairingCode = code,
                        mdnsDeviceSerial = selectedMdnsDeviceSerial,
                    )
                },
            )
        },
    ) {
        SectionTitle(AdbTexts.PAIRING_INSTRUCTION_TITLE.get())
        PairingInstructionCard()

        if (pairingHistory.isNotEmpty()) {
            SectionTitle(AdbTexts.PAIRING_HISTORY_TITLE.get())
            PairingHistoryCard(
                history = pairingHistory,
                onClearHistory = { showClearHistoryDialog = true },
                onDeleteHistory = { selectedHostPort ->
                    viewModel.deletePairingHistoryItem(context, selectedHostPort)
                },
                onSelectHistory = { selectedHostPort ->
                    hostPort = mergeSelectedHostWithCurrentPort(selectedHostPort, hostPort)
                    selectedMdnsDeviceSerial = null
                    errorMessage = ""
                },
            )
        }

        SectionTitle(AdbTexts.PAIRING_DISCOVERY_TITLE.get())
        PairingDiscoveryCard(
            loading = mdnsState.loading,
            services = mdnsState.connectServices,
            onSelectService = { service ->
                hostPort = formatHostPort(service.host, service.port.toString())
                selectedMdnsDeviceSerial = service.deviceSerial
                errorMessage = ""
            },
        )

        SectionTitle(AdbTexts.PAIRING_INFO_TITLE.get())
        PairingInputCard(
            hostPort = hostPort,
            onHostPortChange = {
                hostPort = it
                selectedMdnsDeviceSerial = null
                errorMessage = ""
            },
            pairingCode = pairingCode,
            onPairingCodeChange = {
                pairingCode = it
                errorMessage = ""
            },
            errorMessage = errorMessage,
        )
    }

    PairingDialogOverlays(
        pairingStatus = pairingStatus,
        pairingResult = pairingResult,
        showClearHistoryDialog = showClearHistoryDialog,
        onDismissStatus = viewModel::resetPairingStatus,
        onConfirmClearHistory = {
            viewModel.clearPairingHistory(context)
            showClearHistoryDialog = false
        },
        onDismissClearHistory = { showClearHistoryDialog = false },
    )
}

@Composable
internal fun PairingDiscoveryCard(
    loading: Boolean,
    services: List<MdnsDiscoveredConnectService>,
    onSelectService: (MdnsDiscoveredConnectService) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                services.isNotEmpty() -> {
                    services.forEachIndexed { index, service ->
                        if (index > 0) {
                            AppDivider()
                        }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(AppDimens.listItemHeight)
                                    .then(
                                        if (service.requiresPairing && !service.confirming) {
                                            Modifier.clickable { onSelectService(service) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = service.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text =
                                    if (service.confirming) {
                                        AdbTexts.PAIRING_DISCOVERY_CONFIRMING.get()
                                    } else if (service.requiresPairing) {
                                        AdbTexts.PAIRING_DISCOVERY_PAIRABLE.get()
                                    } else if (service.previouslyPaired) {
                                        AdbTexts.PAIRING_DISCOVERY_RECORDED.get()
                                    } else {
                                        AdbTexts.PAIRING_DISCOVERY_DISCOVERED.get()
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            if (service.requiresPairing && !service.confirming) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                loading -> {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(AppDimens.listItemHeight)
                                .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = AdbTexts.PAIRING_DISCOVERY_SCANNING.get(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }

                else -> {
                    Text(
                        text = AdbTexts.PAIRING_DISCOVERY_EMPTY.get(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun mergeSelectedHostWithCurrentPort(
    selectedHostPort: String,
    currentHostPort: String,
): String {
    if (parseHostPort(selectedHostPort, allowUnbracketedIpv6 = true) != null) {
        return selectedHostPort
    }

    val currentPort = parseHostPort(currentHostPort, allowUnbracketedIpv6 = true)?.port?.toString().orEmpty()
    return if (currentPort.isNotBlank()) {
        formatHostPort(selectedHostPort, currentPort)
    } else {
        selectedHostPort
    }
}

private fun performPairing(
    hostPort: String,
    pairingCode: String,
    onError: (String) -> Unit,
    onPair: (host: String, port: String, code: String) -> Unit,
) {
    when {
        hostPort.isEmpty() || pairingCode.isEmpty() -> {
            onError(AdbTexts.ERROR_EMPTY_FIELD.get())
        }

        parseHostPort(hostPort, allowUnbracketedIpv6 = true) == null -> {
            onError(AdbTexts.ERROR_INVALID_IP.get())
        }

        pairingCode.length != AdbPairingConstants.PAIRING_CODE_LENGTH -> {
            onError(AdbTexts.ERROR_INVALID_CODE.get())
        }

        else -> {
            val endpoint = parseHostPort(hostPort, allowUnbracketedIpv6 = true)
            if (endpoint == null) {
                onError(AdbTexts.ERROR_INVALID_IP.get())
                return
            }

            if (endpoint.port < AdbPairingConstants.MIN_PORT || endpoint.port > AdbPairingConstants.MAX_PORT) {
                onError(AdbTexts.ERROR_INVALID_PORT.get())
                return
            }

            onPair(endpoint.host, endpoint.port.toString(), pairingCode)
        }
    }
}

@Composable
internal fun PairingInstructionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = AdbTexts.PAIRING_INSTRUCTION_CONTENT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun PairingHistoryCard(
    history: List<PairingHistoryItem>,
    onClearHistory: () -> Unit,
    onDeleteHistory: (hostPort: String) -> Unit,
    onSelectHistory: (hostPort: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            history.forEachIndexed { index, item ->
                if (index > 0) {
                    AppDivider()
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .clickable { onSelectHistory(item.hostPort) }
                            .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.hostPort,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.getFormattedTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { onDeleteHistory(item.hostPort) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AppDivider()

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = AdbTexts.PAIRING_HISTORY_CLEAR.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun PairingInputCard(
    hostPort: String,
    onHostPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    errorMessage: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LabeledTextField(
                label = AdbTexts.PAIRING_HOST_PORT_LABEL.get(),
                value = hostPort,
                onValueChange = onHostPortChange,
                placeholder = "192.168.1.100:12345",
                keyboardType = KeyboardType.Text,
            )

            AppDivider()

            LabeledTextField(
                label = AdbTexts.PAIRING_CODE_LABEL.get(),
                value = pairingCode,
                onValueChange = {
                    if (it.length <= AdbPairingConstants.PAIRING_CODE_LENGTH && it.all(Char::isDigit)) {
                        onPairingCodeChange(it)
                    }
                },
                placeholder = "123456",
                keyboardType = KeyboardType.Number,
            )

            if (errorMessage.isNotEmpty()) {
                AppDivider()
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
internal fun PairingDialogOverlays(
    pairingStatus: PairingStatus,
    pairingResult: PairingResult?,
    showClearHistoryDialog: Boolean,
    onDismissStatus: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onDismissClearHistory: () -> Unit,
) {
    if (pairingStatus != PairingStatus.IDLE) {
        PairingStatusDialog(
            status = pairingStatus,
            result = pairingResult,
            onDismiss = onDismissStatus,
        )
    }

    if (showClearHistoryDialog) {
        ClearHistoryConfirmDialog(
            onConfirm = onConfirmClearHistory,
            onDismiss = onDismissClearHistory,
        )
    }
}

@Composable
private fun PairingStatusDialog(
    status: PairingStatus,
    result: PairingResult?,
    onDismiss: () -> Unit,
) {
    val canDismiss = status == PairingStatus.SUCCESS || status == PairingStatus.FAILED

    Dialog(
        onDismissRequest = {
            if (canDismiss) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = canDismiss,
                dismissOnClickOutside = canDismiss,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .wrapContentHeight(),
            shape = RoundedCornerShape(AppDimens.windowCornerRadius),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (status) {
                    PairingStatus.CONNECTING, PairingStatus.PAIRING -> {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text =
                                if (status == PairingStatus.CONNECTING) {
                                    AdbTexts.PAIRING_STATUS_CONNECTING.get()
                                } else {
                                    AdbTexts.PAIRING_STATUS_PAIRING.get()
                                },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }

                    PairingStatus.SUCCESS -> {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = AdbTexts.PAIRING_STATUS_SUCCESS.get(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AdbTexts.PAIRING_SUCCESS_MESSAGE.get(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    PairingStatus.FAILED -> {
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = AdbTexts.PAIRING_STATUS_FAILED.get(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result?.errorMessage ?: AdbTexts.PAIRING_FAILED_MESSAGE.get(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ClearHistoryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = AdbTexts.PAIRING_HISTORY_CLEAR_CONFIRM_TITLE.get(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = AdbTexts.PAIRING_HISTORY_CLEAR_CONFIRM_MESSAGE.get(),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(AdbTexts.PAIRING_HISTORY_CLEAR_BUTTON.get())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(CommonTexts.BUTTON_CANCEL.get())
            }
        },
    )
}

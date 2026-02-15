package com.mobile.scrcpy.android.feature.device.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.scrcpy.android.core.common.AdbPairingConstants
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.feature.device.viewmodel.DevicePairingViewModel

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

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var hostPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadPairingHistory(context)
    }

    LaunchedEffect(pairingResult) {
        pairingResult?.let { result ->
            if (result.success) {
                kotlinx.coroutines.delay(2000)
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
                    viewModel.pairWithCode(context, host, port, code)
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
                    errorMessage = ""
                },
            )
        }

        SectionTitle(AdbTexts.PAIRING_INFO_TITLE.get())
        PairingInputCard(
            hostPort = hostPort,
            onHostPortChange = {
                hostPort = it
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

private fun mergeSelectedHostWithCurrentPort(
    selectedHostPort: String,
    currentHostPort: String,
): String {
    if (selectedHostPort.contains(":")) {
        return selectedHostPort
    }

    val currentPort = currentHostPort.substringAfter(':', "")
    return if (currentPort.isNotBlank()) {
        "$selectedHostPort:$currentPort"
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

        !hostPort.contains(":") -> {
            onError(AdbTexts.ERROR_INVALID_IP.get())
        }

        pairingCode.length != AdbPairingConstants.PAIRING_CODE_LENGTH -> {
            onError(AdbTexts.ERROR_INVALID_CODE.get())
        }

        else -> {
            val parts = hostPort.split(":")
            if (parts.size != 2) {
                onError(AdbTexts.ERROR_INVALID_IP.get())
                return
            }

            val port = parts[1].toIntOrNull()
            if (port == null || port < AdbPairingConstants.MIN_PORT || port > AdbPairingConstants.MAX_PORT) {
                onError(AdbTexts.ERROR_INVALID_PORT.get())
                return
            }

            onPair(parts[0], parts[1], pairingCode)
        }
    }
}

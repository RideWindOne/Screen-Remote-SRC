package com.mobile.scrcpy.android.feature.device.ui.component

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.mobile.scrcpy.android.core.common.AdbPairingConstants
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.SectionTitle
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.feature.device.data.PairingHistoryItem
import com.mobile.scrcpy.android.feature.device.data.PairingResult
import com.mobile.scrcpy.android.feature.device.data.PairingStatus
import com.mobile.scrcpy.android.feature.device.viewmodel.DevicePairingViewModel
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledTextField

/**
 * ADB 配对码配对对话框（DialogPage 版本）
 *
 * 用于通过配对码方式配对 Android 设备的无线调试功能
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // 加载配对历史
    LaunchedEffect(Unit) {
        viewModel.loadPairingHistory(context)
    }

    // 配对成功后自动关闭配对状态对话框
    LaunchedEffect(pairingResult) {
        pairingResult?.let { result ->
            if (result.success) {
                kotlinx.coroutines.delay(2000)
                viewModel.resetPairingStatus()
            }
        }
    }

    fun performPairing() {
        // 验证输入
        when {
            hostPort.isEmpty() || pairingCode.isEmpty() -> {
                errorMessage = AdbTexts.ERROR_EMPTY_FIELD.get()
            }

            !hostPort.contains(":") -> {
                errorMessage = AdbTexts.ERROR_INVALID_IP.get()
            }

            pairingCode.length != AdbPairingConstants.PAIRING_CODE_LENGTH -> {
                errorMessage = AdbTexts.ERROR_INVALID_CODE.get()
            }

            else -> {
                val parts = hostPort.split(":")
                if (parts.size != 2) {
                    errorMessage = AdbTexts.ERROR_INVALID_IP.get()
                } else {
                    val port = parts[1].toIntOrNull()
                    if (port == null || port < AdbPairingConstants.MIN_PORT || port > AdbPairingConstants.MAX_PORT) {
                        errorMessage = AdbTexts.ERROR_INVALID_PORT.get()
                    } else {
                        viewModel.pairWithCode(context, parts[0], parts[1], pairingCode)
                    }
                }
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
        onRightButtonClick = { performPairing() },
    ) {
        // 说明
        SectionTitle(AdbTexts.PAIRING_INSTRUCTION_TITLE.get())
        InstructionCard()

        // 配对历史
        if (pairingHistory.isNotEmpty()) {
            SectionTitle(AdbTexts.PAIRING_HISTORY_TITLE.get())
            PairingHistoryCard(
                history = pairingHistory,
                onClearHistory = { showClearHistoryDialog = true },
                onSelectHistory = { hostPort, code ->
                    viewModel.pairWithCode(context, hostPort.substringBefore(":"), hostPort.substringAfter(":"), code)
                },
            )
        }

        // 配对信息输入
        SectionTitle(AdbTexts.PAIRING_INFO_TITLE.get())
        PairingInputCard(
            hostPort = hostPort,
            onHostPortChange = { hostPort = it },
            pairingCode = pairingCode,
            onPairingCodeChange = { pairingCode = it },
            errorMessage = errorMessage,
            onErrorMessageChange = { errorMessage = it },
        )
    }

    // 配对状态对话框（独立显示）
    if (pairingStatus != PairingStatus.IDLE) {
        PairingStatusDialog(
            status = pairingStatus,
            result = pairingResult,
            onDismiss = {
                viewModel.resetPairingStatus()
            },
        )
    }

    // 清除历史确认对话框
    if (showClearHistoryDialog) {
        ClearHistoryConfirmDialog(
            onConfirm = {
                viewModel.clearPairingHistory(context)
                showClearHistoryDialog = false
            },
            onDismiss = { showClearHistoryDialog = false },
        )
    }
}

/**
 * 说明卡片
 */
@Composable
private fun InstructionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Text(
            text = AdbTexts.PAIRING_INSTRUCTION_CONTENT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * 配对历史卡片
 */
@Composable
private fun PairingHistoryCard(
    history: List<PairingHistoryItem>,
    onClearHistory: () -> Unit,
    onSelectHistory: (hostPort: String, code: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 历史记录列表 - 一行显示
            history.forEachIndexed { index, item ->
                if (index > 0) AppDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .clickable { onSelectHistory(item.hostPort, "") }
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AppDivider()

            // 清除历史按钮
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

/**
 * 配对信息输入卡片
 */
@Composable
private fun PairingInputCard(
    hostPort: String,
    onHostPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    errorMessage: String,
    onErrorMessageChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Host:Port 输入 - 使用 LabeledTextField
            LabeledTextField(
                label = AdbTexts.PAIRING_HOST_PORT_LABEL.get(),
                value = hostPort,
                onValueChange = {
                    onHostPortChange(it)
                    onErrorMessageChange("")
                },
                placeholder = "192.168.1.100:12345",
                keyboardType = KeyboardType.Text,
            )

            AppDivider()

            // Pairing Code 输入 - 使用 LabeledTextField
            LabeledTextField(
                label = AdbTexts.PAIRING_CODE_LABEL.get(),
                value = pairingCode,
                onValueChange = {
                    if (it.length <= AdbPairingConstants.PAIRING_CODE_LENGTH && it.all { char -> char.isDigit() }) {
                        onPairingCodeChange(it)
                        onErrorMessageChange("")
                    }
                },
                placeholder = "123456",
                keyboardType = KeyboardType.Number,
            )

            // 错误提示
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

/**
 * 配对状态对话框（独立显示）
 */
@Composable
private fun PairingStatusDialog(
    status: PairingStatus,
    result: PairingResult?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (status == PairingStatus.SUCCESS || status == PairingStatus.FAILED) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = status == PairingStatus.SUCCESS || status == PairingStatus.FAILED,
                dismissOnClickOutside = status == PairingStatus.SUCCESS || status == PairingStatus.FAILED,
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                        )
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

                    else -> {}
                }
            }
        }
    }
}

/**
 * 清除历史确认对话框
 */
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
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
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

package com.mobile.scrcpy.android.feature.device.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.feature.device.data.PairingResult
import com.mobile.scrcpy.android.feature.device.data.PairingStatus

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

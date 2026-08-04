package com.screen.remote.android.feature.settings.ui.internal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.feature.device.ui.component.AdbPairingDialog
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

@Composable
internal fun SettingsScreenDialogs(
    routeState: SettingsScreenRouteState,
    texts: SettingsScreenTexts,
    devicePairingHostPort: String,
) {
    if (routeState.showDevicePairingDialog) {
        AdbPairingDialog(
            onDismiss = routeState::closeDevicePairingDialog,
            initialHostPort = devicePairingHostPort,
        )
    }

    if (routeState.showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = routeState::closeClearLogsDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(texts.clearLogsTitle) },
            text = { Text(texts.clearLogsMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LogManager.clearAllLogs()
                        routeState.closeClearLogsDialog()
                    },
                ) {
                    Text(texts.clearLogsConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = routeState::closeClearLogsDialog) {
                    Text(texts.cancel)
                }
            },
        )
    }

}

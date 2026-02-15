package com.mobile.scrcpy.android.feature.settings.ui.internal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.designsystem.component.IOSAlertDialog as AlertDialog
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import com.mobile.scrcpy.android.feature.device.ui.component.AdbPairingCodeDialog
import com.mobile.scrcpy.android.feature.settings.ui.FilePathDialog

@Composable
internal fun SettingsScreenDialogs(
    routeState: SettingsScreenRouteState,
    settings: AppSettings,
    texts: SettingsScreenTexts,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    if (routeState.showDevicePairingDialog) {
        AdbPairingCodeDialog(
            onDismiss = routeState::closeDevicePairingDialog,
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

    if (routeState.showFilePathDialog) {
        FilePathDialog(
            currentPath = settings.fileTransferPath,
            onDismiss = routeState::closeFilePathDialog,
            onConfirm = { path ->
                onUpdateSettings(settings.copy(fileTransferPath = path))
                routeState.closeFilePathDialog()
            },
        )
    }
}

package com.mobile.scrcpy.android.feature.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.common.util.FilePickerHelper
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.IOSAlertDialog as AlertDialog
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialogState by remember { mutableStateOf(BackupRestoreDialogState()) }

    val exportLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/json",
        ) { uri ->
            uri ?: return@rememberExportFileLauncher
            scope.launch {
                dialogState =
                    runBackupAction(
                        onSuccess = { BackupManager.exportData(context, viewModel, uri) },
                    )
            }
        }

    val importLauncher =
        FilePickerHelper.rememberImportFileLauncher(
            mimeTypes = arrayOf("application/json"),
        ) { uri ->
            uri ?: return@rememberImportFileLauncher
            scope.launch {
                dialogState =
                    runBackupAction(
                        onSuccess = { BackupManager.importData(context, viewModel, uri) },
                    )
            }
        }

    val txtTitle = rememberText(SettingsTexts.BACKUP_RESTORE_TITLE)
    val txtDone = rememberText(CommonTexts.BUTTON_DONE)
    val txtBackupData = rememberText(SettingsTexts.BACKUP_DATA)
    val txtRestoreData = rememberText(SettingsTexts.RESTORE_DATA)
    val txtBackupInfo = rememberText(SettingsTexts.BACKUP_INFO)

    DialogPage(
        title = txtTitle,
        onDismiss = onBack,
        showBackButton = false,
        rightButtonText = txtDone,
        onRightButtonClick = onBack,
        enableScroll = true,
    ) {
        SettingsCard(title = txtBackupInfo) {
            SettingsItem(
                title = txtBackupData,
                helpText = SettingsTexts.HELP_BACKUP_DATA.get(),
                onClick = {
                    val timestamp = System.currentTimeMillis()
                    exportLauncher.launch("scrcpy_backup_$timestamp.json")
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtRestoreData,
                helpText = SettingsTexts.HELP_RESTORE_DATA.get(),
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                },
            )
        }
    }

    BackupRestoreResultDialogs(
        dialogState = dialogState,
        onDismiss = { dialogState = BackupRestoreDialogState() },
    )
}

@Composable
private fun BackupRestoreResultDialogs(
    dialogState: BackupRestoreDialogState,
    onDismiss: () -> Unit,
) {
    val txtConfirm = rememberText(CommonTexts.BUTTON_CONFIRM)

    if (dialogState.successMessage != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(dialogState.successMessage) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(txtConfirm)
                }
            },
        )
    }

    if (dialogState.errorMessage != null) {
        val txtError = rememberText(CommonTexts.ERROR_LABEL)
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(txtError) },
            text = { Text(dialogState.errorMessage) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(txtConfirm)
                }
            },
        )
    }
}

private suspend fun runBackupAction(
    onSuccess: suspend () -> String,
): BackupRestoreDialogState =
    try {
        BackupRestoreDialogState(successMessage = onSuccess())
    } catch (e: Exception) {
        BackupRestoreDialogState(errorMessage = e.message ?: "Unknown error")
    }

private data class BackupRestoreDialogState(
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

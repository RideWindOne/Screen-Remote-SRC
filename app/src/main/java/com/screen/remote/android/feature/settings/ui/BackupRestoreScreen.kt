package com.screen.remote.android.feature.settings.ui

import android.content.ActivityNotFoundException
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
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.common.util.FilePickerHelper
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SettingsTexts
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
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
            initialDirectoryUri = FilePickerHelper.DOWNLOADS_DIRECTORY_URI,
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
            initialDirectoryUri = FilePickerHelper.DOWNLOADS_DIRECTORY_URI,
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
                    try {
                        exportLauncher.launch("screen_remote_backup_$timestamp.json")
                    } catch (_: ActivityNotFoundException) {
                        dialogState =
                            BackupRestoreDialogState(
                                errorMessage = SettingsTexts.FILE_PICKER_UNAVAILABLE.get(),
                            )
                    }
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtRestoreData,
                helpText = SettingsTexts.HELP_RESTORE_DATA.get(),
                onClick = {
                    try {
                        importLauncher.launch(arrayOf("application/json"))
                    } catch (_: ActivityNotFoundException) {
                        dialogState =
                            BackupRestoreDialogState(
                                errorMessage = SettingsTexts.FILE_PICKER_UNAVAILABLE.get(),
                            )
                    }
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

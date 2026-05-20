package com.screen.remote.android.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.LogTexts
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

@Composable
internal fun LogViewerDialogs(
    state: LogViewerState,
    onDismiss: () -> Unit,
    onClearAndRetry: () -> Unit,
) {
    if (state.showFileTooLargeDialog) {
        AlertDialog(
            onDismissRequest = {
                state.dismissFileTooLargeDialog()
                onDismiss()
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LogTexts.LOG_FILE_TOO_LARGE_TITLE.get()) },
            text = { Text(LogTexts.LOG_FILE_TOO_LARGE_MESSAGE.get()) },
            confirmButton = {
                TextButton(onClick = onClearAndRetry) {
                    Text(LogTexts.LOG_CLEAR_AND_RETRY.get())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.dismissFileTooLargeDialog()
                        onDismiss()
                    },
                ) {
                    Text(CommonTexts.BUTTON_CANCEL.get())
                }
            },
        )
    }

    if (state.showFilterDialog) {
        TagFilterDialog(
            availableTags = state.availableTags,
            selectedTags = state.selectedTags,
            onTagsSelected = state::updateSelectedTags,
            onDismiss = state::dismissFilterDialog,
        )
    }
}

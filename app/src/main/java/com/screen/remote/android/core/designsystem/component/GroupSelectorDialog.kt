package com.screen.remote.android.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.screen.remote.android.core.common.AppColors
import com.screen.remote.android.core.designsystem.component.tree.TreeActions
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts

@Composable
fun GroupSelectorDialog(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
    onGroupsSelected: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberGroupSelectorDialogState(selectedGroupIds)
    val treeNodes =
        remember(availableGroups) {
            TreeActions.buildGroupTree(availableGroups)
        }

    DialogPage(
        title = SessionTexts.GROUP_SELECT.get(),
        onDismiss = onDismiss,
        leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
        trailingContent = {
            GroupSelectorDialogActions(
                state = state,
                onGroupsSelected = onGroupsSelected,
            )
        },
    ) {
        GroupSelectorDialogContent(
            state = state,
            availableGroups = availableGroups,
            treeNodes = treeNodes,
        )
    }
}

@Composable
private fun GroupSelectorDialogActions(
    state: GroupSelectorDialogState,
    onGroupsSelected: (List<String>) -> Unit,
) {
    Row {
        TextButton(
            onClick = { onGroupsSelected(state.tempSelectedIds) },
        ) {
            Text(
                text = CommonTexts.BUTTON_SAVE.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.iOSBlue,
            )
        }

        IconButton(
            onClick = { state.applySelection() },
            enabled = state.hasPendingSelection,
        ) {
            Icon(
                imageVector = if (state.isEditMode) Icons.Default.Check else Icons.Default.Add,
                contentDescription = state.actionContentDescription(),
                tint =
                    if (state.hasPendingSelection) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
            )
        }
    }
}

private fun GroupSelectorDialogState.actionContentDescription(): String =
    if (isEditMode) {
        CommonTexts.BUTTON_DONE.get()
    } else {
        CommonTexts.BUTTON_ADD.get()
    }

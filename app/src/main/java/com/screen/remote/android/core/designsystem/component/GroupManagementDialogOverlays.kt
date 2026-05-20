package com.screen.remote.android.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupType
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

@Composable
internal fun GroupManagementDialogOverlays(
    state: GroupManagementDialogState,
    filteredGroups: List<DeviceGroup>,
    onAddGroup: (name: String, parentPath: String, type: GroupType) -> Unit,
    onUpdateGroup: (DeviceGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    if (state.showAddDialog) {
        key(state.editingGroup?.id ?: "new") {
            AddGroupDialog(
                groups = filteredGroups,
                initialName = state.editingGroup?.name ?: "",
                initialParentPath = state.editingGroup?.parentPath ?: "/",
                initialType = state.editingGroup?.type ?: state.selectedType,
                isEditMode = state.editingGroup != null,
                onConfirm = { name, parentPath, type ->
                    val editingGroup = state.editingGroup
                    if (editingGroup != null) {
                        val path = if (parentPath == "/") "/$name" else "$parentPath/$name"
                        onUpdateGroup(
                            editingGroup.copy(
                                name = name,
                                type = type,
                                path = path,
                                parentPath = parentPath,
                            ),
                        )
                    } else {
                        onAddGroup(name, parentPath, type)
                    }
                    state.dismissAddDialog()
                },
                onDismiss = state::dismissAddDialog,
            )
        }
    }

    state.groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = state::dismissDeleteDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
            },
            title = { Text(SessionTexts.GROUP_CONFIRM_DELETE.get()) },
            text = {
                Text(
                    String.format(
                        SessionTexts.GROUP_CONFIRM_DELETE_MESSAGE.get(),
                        group.name,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup(group.id)
                        state.dismissDeleteDialog()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(SessionTexts.GROUP_DELETE.get())
                }
            },
            dismissButton = {
                TextButton(onClick = state::dismissDeleteDialog) {
                    Text(CommonTexts.BUTTON_CANCEL.get())
                }
            },
        )
    }
}

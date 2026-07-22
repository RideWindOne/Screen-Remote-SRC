package com.screen.remote.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.constants.AppColors
import com.screen.remote.android.core.common.constants.AppDimens.listItemHeight
import com.screen.remote.android.core.designsystem.component.tree.TreeActions
import com.screen.remote.android.core.designsystem.component.tree.TreeNodeItemForManagement
import com.screen.remote.android.core.designsystem.component.tree.TreeRootItemForManagement
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupType
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.designsystem.component.IOSAlertDialog as AlertDialog

/**
 * 分组管理对话框（树形展示）
 */
@Composable
fun GroupManagementDialog(
    groups: List<DeviceGroup>,
    onDismiss: () -> Unit,
    onAddGroup: (name: String, parentPath: String, type: GroupType) -> Unit,
    onUpdateGroup: (DeviceGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    val state = rememberGroupManagementDialogState()
    val filteredGroups =
        remember(groups, state.selectedType) {
            groups.filter { it.type == state.selectedType }
        }
    val treeNodes =
        remember(filteredGroups) {
            TreeActions.buildGroupTree(filteredGroups)
        }

    DialogPage(
        title = SessionTexts.GROUP_MANAGE.get(),
        onDismiss = onDismiss,
        showBackButton = true,
        rightButtonText = SessionTexts.GROUP_ADD.get(),
        onRightButtonClick = state::startAdding,
        horizontalPadding = 0.dp,
    ) {
        GroupManagementTypeSelector(
            selectedType = state.selectedType,
            onTypeSelected = { state.selectedType = it },
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 10.dp)
                    .padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            LazyColumn {
                item {
                    TreeRootItemForManagement(
                        hasChildren = treeNodes.isNotEmpty(),
                        isExpanded = "/" in state.expandedPaths,
                        onToggleExpand = { state.toggleExpanded("/") },
                    )
                }

                if ("/" in state.expandedPaths) {
                    items(treeNodes.size) { index ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        TreeNodeItemForManagement(
                            node = treeNodes[index],
                            expandedPaths = state.expandedPaths,
                            onToggleExpand = state::toggleExpanded,
                            onEdit = state::startEditing,
                            onDelete = state::requestDelete,
                        )
                    }
                }
            }
        }
    }

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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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

@Composable
private fun GroupManagementTypeSelector(
    selectedType: GroupType,
    onTypeSelected: (GroupType) -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val groupTypeBorderColor =
        if (isDarkTheme) AppColors.darkGroupTypeBorder else AppColors.lightGroupTypeBorder

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChip(
            selected = selectedType == GroupType.SESSION,
            onClick = { onTypeSelected(GroupType.SESSION) },
            label = { Text(SessionTexts.MAIN_TAB_SESSIONS.get()) },
            modifier = Modifier.weight(1f).height(listItemHeight),
            border =
                if (selectedType == GroupType.SESSION) {
                    null
                } else {
                    BorderStroke(1.dp, groupTypeBorderColor)
                },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor =
                        if (isDarkTheme) AppColors.darkIOSSelectedBackground else AppColors.iOSSelectedBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
        FilterChip(
            selected = selectedType == GroupType.AUTOMATION,
            onClick = { onTypeSelected(GroupType.AUTOMATION) },
            label = { Text(SessionTexts.MAIN_TAB_ACTIONS.get()) },
            modifier = Modifier.weight(1f).height(listItemHeight),
            border =
                if (selectedType == GroupType.AUTOMATION) {
                    null
                } else {
                    BorderStroke(1.dp, groupTypeBorderColor)
                },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor =
                        if (isDarkTheme) AppColors.darkIOSSelectedBackground else AppColors.iOSSelectedBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
    }
}

@Composable
internal fun rememberGroupManagementDialogState(): GroupManagementDialogState =
    remember { GroupManagementDialogState() }

@Stable
internal class GroupManagementDialogState {
    var showAddDialog by mutableStateOf(false)
    var editingGroup by mutableStateOf<DeviceGroup?>(null)
    var groupToDelete by mutableStateOf<DeviceGroup?>(null)
    var expandedPaths by mutableStateOf(setOf<String>())
    var selectedType by mutableStateOf(GroupType.SESSION)

    fun startAdding() {
        editingGroup = null
        showAddDialog = true
    }

    fun startEditing(group: DeviceGroup) {
        editingGroup = group
        showAddDialog = true
    }

    fun requestDelete(group: DeviceGroup) {
        groupToDelete = group
    }

    fun dismissAddDialog() {
        showAddDialog = false
        editingGroup = null
    }

    fun dismissDeleteDialog() {
        groupToDelete = null
    }

    fun toggleExpanded(path: String) {
        expandedPaths =
            if (path in expandedPaths) {
                expandedPaths - path
            } else {
                expandedPaths + path
            }
    }
}

package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeActions
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.i18n.SessionTexts

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
        GroupManagementDialogContent(
            state = state,
            treeNodes = treeNodes,
        )
    }

    GroupManagementDialogOverlays(
        state = state,
        filteredGroups = filteredGroups,
        onAddGroup = onAddGroup,
        onUpdateGroup = onUpdateGroup,
        onDeleteGroup = onDeleteGroup,
    )
}

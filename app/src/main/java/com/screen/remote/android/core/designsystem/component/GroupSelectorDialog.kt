package com.screen.remote.android.core.designsystem.component

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
    val availableGroupIds = remember(availableGroups) { availableGroups.mapTo(hashSetOf()) { it.id } }
    val validSelectedGroupIds =
        remember(selectedGroupIds, availableGroupIds) {
            sanitizeSelectedGroupIds(selectedGroupIds, availableGroupIds)
        }
    val state = rememberGroupSelectorDialogState(validSelectedGroupIds)
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
        enableScroll = true,
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
    TextButton(
        onClick = { onGroupsSelected(state.tempSelectedIds) },
    ) {
        Text(
            text = CommonTexts.BUTTON_SAVE.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

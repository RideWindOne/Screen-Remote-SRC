package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.IosDesignTokens
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeNodeItemForSelector
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeRootItemForSelector
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode
import com.mobile.scrcpy.android.core.i18n.SessionTexts

@Composable
internal fun GroupSelectorDialogContent(
    state: GroupSelectorDialogState,
    availableGroups: List<DeviceGroup>,
    treeNodes: List<GroupTreeNode>,
) {
    GroupSelectorTreeSection(
        state = state,
        treeNodes = treeNodes,
    )

    if (state.tempSelectedIds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(IosDesignTokens.standardSpacing))
        GroupSelectorSelectedGroupsSection(
            state = state,
            availableGroups = availableGroups,
        )
    }
}

@Composable
private fun GroupSelectorTreeSection(
    state: GroupSelectorDialogState,
    treeNodes: List<GroupTreeNode>,
) {
    Text(
        text = if (LanguageManager.isChinese()) "选择分组" else "Select Group",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = IosDesignTokens.dialogHeaderSpacerHeight),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        LazyColumn {
            item {
                TreeRootItemForSelector(
                    hasChildren = treeNodes.isNotEmpty(),
                    isExpanded = "/" in state.expandedPaths,
                    onToggleExpand = { state.toggleExpanded("/") },
                )
            }

            if ("/" in state.expandedPaths) {
                items(treeNodes.size) { index ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = IosDesignTokens.standardHorizontalPadding),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = IosDesignTokens.dividerAlpha),
                    )

                    TreeNodeItemForSelector(
                        node = treeNodes[index],
                        currentSelectedId = state.currentSelectedGroupId,
                        alreadyAddedIds = state.alreadyAddedIds,
                        expandedPaths = state.expandedPaths,
                        onToggleExpand = state::toggleExpanded,
                        onSelect = state::toggleCurrentSelection,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupSelectorSelectedGroupsSection(
    state: GroupSelectorDialogState,
    availableGroups: List<DeviceGroup>,
) {
    Text(
        text =
            if (LanguageManager.isChinese()) {
                "已选择 (${state.tempSelectedIds.size})"
            } else {
                "Selected (${state.tempSelectedIds.size})"
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = IosDesignTokens.dialogHeaderSpacerHeight),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column {
            state.tempSelectedIds.forEachIndexed { index, groupId ->
                val group = availableGroups.find { it.id == groupId } ?: return@forEachIndexed

                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = IosDesignTokens.standardHorizontalPadding),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = IosDesignTokens.dividerAlpha),
                    )
                }

                GroupSelectorSelectedGroupRow(
                    group = group,
                    onEdit = { state.startEditing(group) },
                    onRemove = { state.removeSelectedGroup(groupId) },
                )
            }
        }
    }
}

@Composable
private fun GroupSelectorSelectedGroupRow(
    group: DeviceGroup,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.standardHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(IosDesignTokens.dialogHeaderSpacerHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = SessionTexts.GROUP_EDIT.get(),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (LanguageManager.isChinese()) "删除" else "Remove",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

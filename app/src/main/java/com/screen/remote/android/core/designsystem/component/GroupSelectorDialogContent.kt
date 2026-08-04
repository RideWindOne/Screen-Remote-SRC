package com.screen.remote.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.designsystem.component.tree.TreeNodeItemForSelector
import com.screen.remote.android.core.designsystem.component.tree.TreeRootItemForSelector
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.domain.model.GroupTreeNode
import com.screen.remote.android.core.i18n.SessionTexts

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
        text = SessionTexts.GROUP_SELECT_SINGLE.get(),
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
            TreeRootItemForSelector(
                hasChildren = treeNodes.isNotEmpty(),
                isExpanded = "/" in state.expandedPaths,
                onToggleExpand = { state.toggleExpanded("/") },
            )

            if ("/" in state.expandedPaths) {
                treeNodes.forEach { node ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = IosDesignTokens.standardHorizontalPadding),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = IosDesignTokens.dividerAlpha),
                    )

                    TreeNodeItemForSelector(
                        node = node,
                        currentSelectedId = state.currentSelectedGroupId,
                        alreadyAddedIds = state.alreadyAddedIds,
                        expandedPaths = state.expandedPaths,
                        onToggleExpand = state::toggleExpanded,
                        onSelect = state::selectGroup,
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
        text = SessionTexts.GROUP_SELECTED_COUNT.format(state.tempSelectedIds.size),
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
                text = group.path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    contentDescription = SessionTexts.GROUP_REMOVE.get(),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

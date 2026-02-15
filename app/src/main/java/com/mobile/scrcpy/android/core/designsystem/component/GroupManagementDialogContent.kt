package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.constants.AppColors
import com.mobile.scrcpy.android.core.common.constants.AppDimens.listItemHeight
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeNodeItemForManagement
import com.mobile.scrcpy.android.core.designsystem.component.tree.TreeRootItemForManagement
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.i18n.SessionTexts

@Composable
internal fun GroupManagementDialogContent(
    state: GroupManagementDialogState,
    treeNodes: List<GroupTreeNode>,
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
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
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

@Composable
private fun GroupManagementTypeSelector(
    selectedType: GroupType,
    onTypeSelected: (GroupType) -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
            modifier =
                Modifier
                    .weight(1f)
                    .height(listItemHeight),
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor =
                        if (isDarkTheme) {
                            AppColors.darkIOSSelectedBackground
                        } else {
                            AppColors.iOSSelectedBackground
                        },
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
        FilterChip(
            selected = selectedType == GroupType.AUTOMATION,
            onClick = { onTypeSelected(GroupType.AUTOMATION) },
            label = { Text(SessionTexts.MAIN_TAB_ACTIONS.get()) },
            modifier =
                Modifier
                    .weight(1f)
                    .height(listItemHeight),
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor =
                        if (isDarkTheme) {
                            AppColors.darkIOSSelectedBackground
                        } else {
                            AppColors.iOSSelectedBackground
                        },
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
        )
    }
}

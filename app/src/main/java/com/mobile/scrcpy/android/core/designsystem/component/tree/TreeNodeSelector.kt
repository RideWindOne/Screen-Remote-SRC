/**
 * TreeNodeSelector
 *
 * Purpose: Tree node UI components for selector mode (multi-select and added state)
 *
 * This file is part of the TreeNode.kt refactoring.
 * It contains tree node components for selector mode with multi-select and already-added state display.
 *
 * Visibility: Public (re-exported from TreeNode.kt)
 * Dependencies: Jetpack Compose, Material3, GroupTreeNode, LanguageManager
 */
package com.mobile.scrcpy.android.core.designsystem.component.tree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode

/**
 * 树形节点项（选择模式，递归渲染）
 * 用于分组选择器，支持多选和已添加状态显示
 */
@Composable
fun TreeNodeItemForSelector(
    node: GroupTreeNode,
    currentSelectedId: String?,
    alreadyAddedIds: List<String>,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val isExpanded = expandedPaths.contains(node.group.path)
    val hasChildren = node.children.isNotEmpty()
    val isCurrentSelected = currentSelectedId == node.group.id
    val isAlreadyAdded = alreadyAddedIds.contains(node.group.id)

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .background(
                        if (isCurrentSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            Color.Transparent
                        },
                    ).clickable { onSelect(node.group.id) }
                    .padding(start = (16 + node.level * 20).dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 展开/折叠图标
                if (hasChildren) {
                    IconButton(
                        onClick = { onToggleExpand(node.group.path) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (isExpanded) {
                                    Icons.Default.KeyboardArrowDown
                                } else {
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                                },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // 文件夹图标
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint =
                        when {
                            isAlreadyAdded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isCurrentSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )

                // 分组名称和描述
                Column {
                    Text(
                        text = node.group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (isAlreadyAdded) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    if (node.group.description.isNotBlank()) {
                        Text(
                            text = node.group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isAlreadyAdded) 0.3f else 1f,
                                ),
                        )
                    }
                }
            }

            // 右侧状态图标
            if (isAlreadyAdded) {
                Text(
                    text = if (LanguageManager.isChinese()) "已添加" else "Added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            } else if (isCurrentSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 子节点（带动画）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                node.children.forEach { childNode ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                    TreeNodeItemForSelector(
                        node = childNode,
                        currentSelectedId = currentSelectedId,
                        alreadyAddedIds = alreadyAddedIds,
                        expandedPaths = expandedPaths,
                        onToggleExpand = onToggleExpand,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

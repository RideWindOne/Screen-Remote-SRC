/**
 * TreeNodePath
 *
 * Purpose: Tree node UI components for path selection mode
 *
 * This file is part of the TreeNode.kt refactoring.
 * It contains tree node components for path selection mode with expand/collapse and selection support.
 *
 * Visibility: Public (re-exported from TreeNode.kt)
 * Dependencies: Jetpack Compose, Material3, GroupTreeNode
 */
package com.mobile.scrcpy.android.core.designsystem.component.tree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode
import com.mobile.scrcpy.android.core.i18n.SessionTexts

/**
 * 树形节点项（路径选择模式，递归渲染）
 *
 * 用于渲染树形结构中的节点，支持展开/折叠和选中状态。
 *
 * @param node 树形节点数据
 * @param selectedPath 当前选中的路径
 * @param expandedPaths 已展开的路径集合
 * @param onToggleExpand 展开/折叠回调
 * @param onSelect 选中回调
 */
@Composable
fun TreeNodeItem(
    node: GroupTreeNode,
    selectedPath: String,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val isExpanded = expandedPaths.contains(node.group.path)
    val hasChildren = node.children.isNotEmpty()
    val isSelected = selectedPath == node.group.path

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .clickable { onSelect(node.group.path) }
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
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )

                // 分组名称
                Text(
                    text = node.group.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (isSelected) {
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
                    TreeNodeItem(
                        node = childNode,
                        selectedPath = selectedPath,
                        expandedPaths = expandedPaths,
                        onToggleExpand = onToggleExpand,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

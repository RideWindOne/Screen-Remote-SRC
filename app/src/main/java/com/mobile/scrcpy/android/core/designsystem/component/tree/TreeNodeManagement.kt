/**
 * TreeNodeManagement
 *
 * Purpose: Tree node UI components for management mode (edit/delete operations)
 *
 * This file is part of the TreeNode.kt refactoring.
 * It contains tree node components for management mode with edit and delete functionality.
 *
 * Visibility: Public (re-exported from TreeNode.kt)
 * Dependencies: Jetpack Compose, Material3, GroupTreeNode, DeviceGroup
 */
package com.mobile.scrcpy.android.core.designsystem.component.tree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode
import com.mobile.scrcpy.android.core.i18n.SessionTexts

/**
 * 树形节点项（管理模式，递归渲染）
 * 用于分组管理，支持编辑和删除操作
 */
@Composable
fun TreeNodeItemForManagement(
    node: GroupTreeNode,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onEdit: (DeviceGroup) -> Unit,
    onDelete: (DeviceGroup) -> Unit,
) {
    val isExpanded = expandedPaths.contains(node.group.path)
    val hasChildren = node.children.isNotEmpty()

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppDimens.listItemHeight)
                    .padding(start = (16 + node.level * 20).dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
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
                            modifier = Modifier.size(24.dp),
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 分组名称和描述
                Column {
                    Text(
                        text = node.group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (node.group.description.isNotBlank()) {
                        Text(
                            text = node.group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 右侧按钮组（编辑+删除）
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 编辑按钮
                IconButton(
                    onClick = { onEdit(node.group) },
                    modifier =
                        Modifier
                            .size(30.dp)
                            .padding(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = SessionTexts.GROUP_EDIT.get(),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // 删除按钮（最右边）
                IconButton(
                    onClick = { onDelete(node.group) },
                    modifier =
                        Modifier
                            .size(30.dp)
                            .padding(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = SessionTexts.GROUP_DELETE.get(),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                }
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
                    TreeNodeItemForManagement(
                        node = childNode,
                        expandedPaths = expandedPaths,
                        onToggleExpand = onToggleExpand,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

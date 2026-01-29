package com.mobile.scrcpy.android.core.designsystem.component

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupTreeNode
import com.mobile.scrcpy.android.core.i18n.SessionTexts

/**
 * 树形根目录项（管理模式）
 * 用于分组管理，支持展开/折叠
 */
@Composable
fun TreeRootItemForManagement(
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = SessionTexts.GROUP_ROOT.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 展开/折叠图标（移到右边）
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier =
                    Modifier
                        .size(30.dp)
                        .padding(0.dp),
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
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

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

/**
 * 树形根目录项（选择模式）
 * 用于分组选择器，支持展开/折叠
 */
@Composable
fun TreeRootItemForSelector(
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = SessionTexts.GROUP_ROOT.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 展开/折叠图标（移到右边）
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
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
        }
    }
}

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

/**
 * 树形根目录项（路径选择模式）
 *
 * 用于渲染树形结构的根节点（Home），支持展开/折叠和选中状态。
 *
 * @param isSelected 是否选中
 * @param hasChildren 是否有子节点
 * @param isExpanded 是否展开
 * @param onToggleExpand 展开/折叠回调
 * @param onClick 点击回调
 */
@Composable
fun TreeRootItem(
    isSelected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = SessionTexts.GROUP_ROOT.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // 右侧：展开/折叠按钮或选中标记
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
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
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

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

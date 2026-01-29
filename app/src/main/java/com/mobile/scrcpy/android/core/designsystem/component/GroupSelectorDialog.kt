package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.LanguageManager
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts

/**
 * 分组选择器组件（支持多选，树形结构）
 * 新交互：
 * 1. 上方：分组树选择器（单选，可展开/折叠，Home 默认折叠）
 * 2. 中间：添加按钮（在完成按钮旁边）
 * 3. 下方：已选择的分组列表（每项带删除按钮）
 *
 * @param selectedGroupIds 已选中的分组 ID 列表
 * @param availableGroups 可用的分组列表
 * @param onGroupsSelected 选择完成回调
 * @param onDismiss 关闭回调
 */
@Composable
fun GroupSelectorDialog(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
    onGroupsSelected: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 已选择的分组列表（这是最终会保存的）
    var tempSelectedIds by remember { mutableStateOf(selectedGroupIds) }
    // 当前在树中选中的分组（用于添加）
    var currentSelectedGroupId by remember { mutableStateOf<String?>(null) }
    // 是否处于编辑模式（从已选列表点击编辑进入）
    var editingGroupId by remember { mutableStateOf<String?>(null) }
    // 展开的路径集合（包括根目录 "/"）
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // 构建树形结构
    val treeNodes =
        remember(availableGroups) {
            GroupTreeUtils.buildGroupTree(availableGroups)
        }
    // 检查根目录是否有子节点
    val hasRootChildren = treeNodes.isNotEmpty()

    // 编辑模式：有选中即可点击（包括选中相同的项）
    // 添加模式：选中的不在已选列表中
    val isEditMode = editingGroupId != null
    val hasSelection =
        if (isEditMode) {
            currentSelectedGroupId != null
        } else {
            currentSelectedGroupId != null && !tempSelectedIds.contains(currentSelectedGroupId!!)
        }

    DialogPage(
        title = SessionTexts.GROUP_SELECT.get(),
        onDismiss = onDismiss,
        leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
        trailingContent = {
            Row {
                // 保存按钮
                TextButton(
                    onClick = { onGroupsSelected(tempSelectedIds) },
                ) {
                    Text(
                        text = CommonTexts.BUTTON_SAVE.get(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 添加/完成按钮
                IconButton(
                    onClick = {
                        currentSelectedGroupId?.let { groupId ->
                            if (isEditMode) {
                                // 编辑模式：替换正在编辑的项
                                editingGroupId?.let { oldId ->
                                    tempSelectedIds =
                                        tempSelectedIds.map {
                                            if (it == oldId) groupId else it
                                        }
                                }
                                editingGroupId = null
                            } else {
                                // 添加模式：添加到列表
                                if (!tempSelectedIds.contains(groupId)) {
                                    tempSelectedIds = tempSelectedIds + groupId
                                }
                            }
                            // 清空树的选择状态
                            currentSelectedGroupId = null
                        }
                    },
                    enabled = hasSelection,
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Add,
                        contentDescription =
                            if (isEditMode) {
                                if (LanguageManager.isChinese()) "完成" else "Done"
                            } else {
                                CommonTexts.BUTTON_ADD.get()
                            },
                        tint =
                            if (hasSelection) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                    )
                }
            }
        },
    ) {
        // 上方：分组树选择器
        Text(
            text = if (LanguageManager.isChinese()) "选择分组" else "Select Group",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            LazyColumn {
                // 根目录（Home）- 可展开/折叠
                item {
                    TreeRootItemForSelector(
                        hasChildren = hasRootChildren,
                        isExpanded = expandedPaths.contains("/"),
                        onToggleExpand = {
                            expandedPaths =
                                if (expandedPaths.contains("/")) {
                                    expandedPaths - "/"
                                } else {
                                    expandedPaths + "/"
                                }
                        },
                    )
                }

                // 树形节点（只在根目录展开时显示）
                if (expandedPaths.contains("/")) {
                    items(treeNodes.size) { index ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        TreeNodeItemForSelector(
                            node = treeNodes[index],
                            currentSelectedId = currentSelectedGroupId,
                            alreadyAddedIds =
                                if (editingGroupId != null) {
                                    // 编辑模式：排除正在编辑的项，允许选择它
                                    tempSelectedIds.filter { it != editingGroupId }
                                } else {
                                    tempSelectedIds
                                },
                            expandedPaths = expandedPaths,
                            onToggleExpand = { path ->
                                expandedPaths =
                                    if (expandedPaths.contains(path)) {
                                        expandedPaths - path
                                    } else {
                                        expandedPaths + path
                                    }
                            },
                            onSelect = { groupId ->
                                currentSelectedGroupId = if (currentSelectedGroupId == groupId) null else groupId
                            },
                        )
                    }
                }
            }
        }

        // 下方：已选择的分组列表
        if (tempSelectedIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text =
                    if (LanguageManager.isChinese()) {
                        "已选择 (${tempSelectedIds.size})"
                    } else {
                        "Selected (${tempSelectedIds.size})"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            ) {
                Column {
                    tempSelectedIds.forEachIndexed { index, groupId ->
                        val group = availableGroups.find { it.id == groupId }

                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }

                        if (group != null) {
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
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    // 编辑按钮
                                    IconButton(
                                        onClick = {
                                            // 进入编辑模式：记录正在编辑的项，在树中选中它
                                            editingGroupId = groupId
                                            currentSelectedGroupId = groupId
                                            // 展开该分组的父路径
                                            val pathsToExpand = mutableSetOf<String>()
                                            pathsToExpand.add("/") // 展开根目录
                                            var currentPath = group.parentPath
                                            while (currentPath != "/" && currentPath.isNotEmpty()) {
                                                pathsToExpand.add(currentPath)
                                                // 获取父路径
                                                val lastSlash = currentPath.lastIndexOf('/')
                                                currentPath =
                                                    if (lastSlash > 0) {
                                                        currentPath.substring(0, lastSlash)
                                                    } else {
                                                        "/"
                                                    }
                                            }
                                            expandedPaths = expandedPaths + pathsToExpand
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = SessionTexts.GROUP_EDIT.get(),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }

                                    // 删除按钮
                                    IconButton(
                                        onClick = {
                                            tempSelectedIds = tempSelectedIds.filter { it != groupId }
                                            // 如果删除的是正在编辑的项，退出编辑模式
                                            if (editingGroupId == groupId) {
                                                editingGroupId = null
                                                currentSelectedGroupId = null
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = if (LanguageManager.isChinese()) "删除" else "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分组选择按钮（用于会话对话框）
 * 样式与 LabeledTextField 保持一致，显示路径式分组
 */
@Composable
fun GroupSelectorButton(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
    onGroupsSelected: (List<String>) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    // 将选中的分组名称用斜杠连接，形成路径式显示
    val displayText =
        if (selectedGroupIds.isNotEmpty()) {
            availableGroups
                .filter { selectedGroupIds.contains(it.id) }
                .joinToString(" / ") { it.name }
        } else {
            SessionTexts.GROUP_PLACEHOLDER_DESCRIPTION.get()
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SessionTexts.GROUP_SELECT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp),
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (selectedGroupIds.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
    }

    if (showDialog) {
        GroupSelectorDialog(
            selectedGroupIds = selectedGroupIds,
            availableGroups = availableGroups,
            onGroupsSelected = onGroupsSelected,
            onDismiss = { showDialog = false },
        )
    }
}

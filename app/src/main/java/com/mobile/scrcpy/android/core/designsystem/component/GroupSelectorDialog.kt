package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onDismiss: () -> Unit
) {
    // 已选择的分组列表（这是最终会保存的）
    var tempSelectedIds by remember { mutableStateOf(selectedGroupIds) }
    // 当前在树中选中的分组（用于添加）
    var currentSelectedGroupId by remember { mutableStateOf<String?>(null) }
    // 是否处于编辑模式（从已选列表点击编辑进入）
    var isEditingMode by remember { mutableStateOf(false) }
    // 展开的路径集合（包括根目录 "/"）
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // 构建树形结构
    val treeNodes = remember(availableGroups) {
        GroupTreeUtils.buildGroupTree(availableGroups)
    }
    // 检查根目录是否有子节点
    val hasRootChildren = treeNodes.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                // 顶部标题栏（添加"添加"按钮）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = CommonTexts.BUTTON_CANCEL.get(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = SessionTexts.GROUP_SELECT.get(),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 保存按钮（放左边）
                        TextButton(
                            onClick = {
                                // 只保存已添加到列表的分组
                                onGroupsSelected(tempSelectedIds)
                            }
                        ) {
                            Text(
                                text = CommonTexts.BUTTON_SAVE.get(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 添加/完成按钮（编辑模式时显示为完成图标，放右边）
                        val hasSelection = currentSelectedGroupId != null && !tempSelectedIds.contains(currentSelectedGroupId!!)
                        TextButton(
                            onClick = {
                                currentSelectedGroupId?.let { groupId ->
                                    if (!tempSelectedIds.contains(groupId)) {
                                        tempSelectedIds = tempSelectedIds + groupId
                                    }
                                    // 清空树的选择状态和编辑模式
                                    currentSelectedGroupId = null
                                    isEditingMode = false
                                }
                            },
                            enabled = hasSelection
                        ) {
                            Icon(
                                imageVector = if (isEditingMode) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = if (isEditingMode) {
                                    if (LanguageManager.isChinese()) "完成" else "Done"
                                } else {
                                    CommonTexts.BUTTON_ADD.get()
                                },
                                tint = if (hasSelection) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    // 上方：分组树选择器
                    Text(
                        text = if (LanguageManager.isChinese()) "选择分组" else "Select Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        LazyColumn {
                            // 根目录（Home）- 可展开/折叠
                            item {
                                TreeRootItemForSelector(
                                    hasChildren = hasRootChildren,
                                    isExpanded = expandedPaths.contains("/"),
                                    onToggleExpand = {
                                        expandedPaths = if (expandedPaths.contains("/")) {
                                            expandedPaths - "/"
                                        } else {
                                            expandedPaths + "/"
                                        }
                                    }
                                )
                            }

                            // 树形节点（只在根目录展开时显示）
                            if (expandedPaths.contains("/")) {
                                items(treeNodes.size) { index ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )

                                    TreeNodeItemForSelector(
                                        node = treeNodes[index],
                                        currentSelectedId = currentSelectedGroupId,
                                        alreadyAddedIds = tempSelectedIds,
                                        expandedPaths = expandedPaths,
                                        onToggleExpand = { path ->
                                            expandedPaths = if (expandedPaths.contains(path)) {
                                                expandedPaths - path
                                            } else {
                                                expandedPaths + path
                                            }
                                        },
                                        onSelect = { groupId ->
                                            currentSelectedGroupId = if (currentSelectedGroupId == groupId) null else groupId
                                            // 如果取消选中，也要退出编辑模式
                                            if (currentSelectedGroupId == null) {
                                                isEditingMode = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 下方：已选择的分组列表
                    if (tempSelectedIds.isNotEmpty()) {
                        Text(
                            text = if (LanguageManager.isChinese())
                                "已选择 (${tempSelectedIds.size})"
                            else
                                "Selected (${tempSelectedIds.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Column {
                                tempSelectedIds.forEachIndexed { index, groupId ->
                                    val group = availableGroups.find { it.id == groupId }

                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    }

                                    if (group != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(AppDimens.listItemHeight)
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = group.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 编辑按钮
                                                IconButton(
                                                    onClick = {
                                                        // 1. 从已选列表中移除
                                                        tempSelectedIds = tempSelectedIds.filter { it != groupId }
                                                        // 2. 在树中选中该分组
                                                        currentSelectedGroupId = groupId
                                                        // 3. 设置为编辑模式
                                                        isEditingMode = true
                                                        // 4. 展开该分组的父路径
                                                        val pathsToExpand = mutableSetOf<String>()
                                                        pathsToExpand.add("/") // 展开根目录
                                                        var currentPath = group.parentPath
                                                        while (currentPath != "/" && currentPath.isNotEmpty()) {
                                                            pathsToExpand.add(currentPath)
                                                            // 获取父路径
                                                            val lastSlash = currentPath.lastIndexOf('/')
                                                            currentPath = if (lastSlash > 0) {
                                                                currentPath.substring(0, lastSlash)
                                                            } else {
                                                                "/"
                                                            }
                                                        }
                                                        expandedPaths = expandedPaths + pathsToExpand
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = SessionTexts.GROUP_EDIT.get(),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                // 删除按钮
                                                IconButton(
                                                    onClick = {
                                                        tempSelectedIds = tempSelectedIds.filter { it != groupId }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = if (LanguageManager.isChinese()) "删除" else "Remove",
                                                        tint = MaterialTheme.colorScheme.error
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
    onGroupsSelected: (List<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    // 将选中的分组名称用斜杠连接，形成路径式显示
    val displayText = if (selectedGroupIds.isNotEmpty()) {
        availableGroups
            .filter { selectedGroupIds.contains(it.id) }
            .joinToString(" / ") { it.name }
    } else {
        SessionTexts.GROUP_PLACEHOLDER_DESCRIPTION.get()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = SessionTexts.GROUP_SELECT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedGroupIds.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
    }

    if (showDialog) {
        GroupSelectorDialog(
            selectedGroupIds = selectedGroupIds,
            availableGroups = availableGroups,
            onGroupsSelected = onGroupsSelected,
            onDismiss = { showDialog = false }
        )
    }
}

package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.mobile.scrcpy.android.core.domain.model.DefaultGroups
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType

import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
/**
 * 分组管理对话框（树形展示）
 */
@Composable
fun GroupManagementDialog(
    groups: List<DeviceGroup>,
    sessionCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onAddGroup: (name: String, parentPath: String, description: String, type: GroupType) -> Unit,
    onUpdateGroup: (DeviceGroup) -> Unit,
    onDeleteGroup: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<DeviceGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<DeviceGroup?>(null) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    var selectedType by remember { mutableStateOf(GroupType.SESSION) }

    // 按类型过滤分组
    val filteredGroups = remember(groups, selectedType) {
        groups.filter { it.type == selectedType }
    }

    // 构建树形结构
    val treeNodes = remember(filteredGroups) { 
        GroupTreeUtils.buildGroupTree(filteredGroups)
    }

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
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = SessionTexts.GROUP_MANAGE.get(),
                    onDismiss = onDismiss,
                    showBackButton = false,
                    leftButtonText = CommonTexts.BUTTON_CLOSE.get(),
                    rightButtonText = SessionTexts.GROUP_ADD.get(),
                    onRightButtonClick = {
                        editingGroup = null
                        showAddDialog = true
                    }
                )

                // 类型选择器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = selectedType == GroupType.SESSION,
                        onClick = {
                            selectedType = GroupType.SESSION
                        },
                        label = { Text(SessionTexts.MAIN_TAB_SESSIONS.get()) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == GroupType.AUTOMATION,
                        onClick = {
                            selectedType = GroupType.AUTOMATION
                        },
                        label = { Text(SessionTexts.MAIN_TAB_ACTIONS.get()) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 分组列表（整个列表用一个 Card 包裹）
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    LazyColumn {
                        // 根目录（Home）- 可展开/折叠
                        item {
                            TreeRootItemForManagement(
                                hasChildren = treeNodes.isNotEmpty(),
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

                                TreeNodeItemForManagement(
                                    node = treeNodes[index],
                                    expandedPaths = expandedPaths,
                                    onToggleExpand = { path ->
                                        expandedPaths = if (expandedPaths.contains(path)) {
                                            expandedPaths - path
                                        } else {
                                            expandedPaths + path
                                        }
                                    },
                                    onEdit = {
                                        editingGroup = it
                                        showAddDialog = true
                                    },
                                    onDelete = {
                                        groupToDelete = it
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑分组对话框
    if (showAddDialog) {
        AddGroupDialog(
            groups = filteredGroups,
            initialName = editingGroup?.name ?: "",
            initialParentPath = editingGroup?.parentPath ?: "/",
            initialDescription = editingGroup?.description ?: "",
            initialType = editingGroup?.type ?: selectedType,
            isEditMode = editingGroup != null,
            onConfirm = { name, parentPath, description, type ->
                if (editingGroup != null) {
                    // 编辑模式：更新分组
                    val path = if (parentPath == "/") "/$name" else "$parentPath/$name"
                    onUpdateGroup(
                        editingGroup!!.copy(
                            name = name,
                            type = type,
                            path = path,
                            parentPath = parentPath,
                            description = description
                        )
                    )
                } else {
                    // 添加模式
                    onAddGroup(name, parentPath, description, type)
                }
                showAddDialog = false
                editingGroup = null
            },
            onDismiss = {
                showAddDialog = false
                editingGroup = null
            }
        )
    }

    // 删除确认对话框
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(SessionTexts.GROUP_CONFIRM_DELETE.get()) },
            text = {
                Text(
                    String.format(
                        SessionTexts.GROUP_CONFIRM_DELETE_MESSAGE.get(),
                        group.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(group.id)
                    groupToDelete = null
                }) {
                    Text(SessionTexts.GROUP_DELETE.get())
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text(CommonTexts.BUTTON_CANCEL.get())
                }
            }
        )
    }
}

/**
 * 底部路径面包屑（显示完整路径）
 */
@Composable
fun PathBreadcrumb(
    selectedGroupPath: String
) {
    // 如果是默认状态，不显示
    if (selectedGroupPath == DefaultGroups.ALL_DEVICES ||
        selectedGroupPath == DefaultGroups.UNGROUPED) {
        return
    }

    val pathParts = selectedGroupPath.split("/").filter { it.isNotEmpty() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // All
            Text(
                text = SessionTexts.GROUP_ALL.get(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 路径的每一层
            pathParts.forEach { part ->
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = part,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

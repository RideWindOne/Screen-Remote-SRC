package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup

import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
/**
 * 添加/编辑分组对话框
 * 支持选择父路径和分组类型
 */
@Composable
fun AddGroupDialog(
    groups: List<DeviceGroup>,
    initialName: String = "",
    initialParentPath: String = "/",
    initialDescription: String = "",
    initialType: GroupType = GroupType.SESSION,
    isEditMode: Boolean = false,
    onConfirm: (name: String, parentPath: String, description: String, type: GroupType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var parentPath by remember { mutableStateOf(initialParentPath) }
    var description by remember { mutableStateOf(initialDescription) }
    var groupType by remember { mutableStateOf(initialType) }
    var showPathSelector by remember { mutableStateOf(false) }

    // 计算完整路径预览
    val fullPath = if (parentPath == "/") "/$name" else "$parentPath/$name"

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
                    title = if (isEditMode) {
                        SessionTexts.GROUP_EDIT.get()
                    } else {
                        SessionTexts.GROUP_ADD.get()
                    },
                    onDismiss = onDismiss,
                    showBackButton = false,
                    leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
                    rightButtonText = CommonTexts.BUTTON_SAVE.get(),
                    rightButtonEnabled = name.isNotBlank(),
                    onRightButtonClick = {
                        if (name.isNotBlank()) {
                            onConfirm(name, parentPath, description, groupType)
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 分组名称
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(SessionTexts.GROUP_NAME.get()) },
                        placeholder = { Text(SessionTexts.GROUP_PLACEHOLDER_NAME.get()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 分组类型选择
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = SessionTexts.GROUP_TYPE.get(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 会话分组
                            FilterChip(
                                selected = groupType == GroupType.SESSION,
                                onClick = { groupType = GroupType.SESSION },
                                label = { Text(SessionTexts.MAIN_TAB_SESSIONS.get()) },
                                modifier = Modifier.weight(1f)
                            )
                            // 自动化分组
                            FilterChip(
                                selected = groupType == GroupType.AUTOMATION,
                                onClick = { groupType = GroupType.AUTOMATION },
                                label = { Text(SessionTexts.MAIN_TAB_ACTIONS.get()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 父路径选择
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = SessionTexts.GROUP_PARENT_PATH.get(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPathSelector = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (parentPath == "/") {
                                        SessionTexts.GROUP_ROOT.get()
                                    } else {
                                        parentPath
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "›",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFE5E5EA)
                                )
                            }
                        }
                    }

                    // 完整路径预览
                    if (name.isNotBlank()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = SessionTexts.GROUP_PATH_PREVIEW.get(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fullPath,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 描述
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(SessionTexts.GROUP_DESCRIPTION.get()) },
                        placeholder = { Text(SessionTexts.GROUP_PLACEHOLDER_DESCRIPTION.get()) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        }
    }

    // 路径选择对话框
    if (showPathSelector) {
        PathSelectorDialog(
            groups = groups,
            selectedPath = parentPath,
            onPathSelected = {
                parentPath = it
                showPathSelector = false
            },
            onDismiss = { showPathSelector = false }
        )
    }
}

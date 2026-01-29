package com.mobile.scrcpy.android.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.domain.model.DeviceGroup
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledClickableRow
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledInputRow
import com.mobile.scrcpy.android.feature.settings.ui.SettingsCard
import com.mobile.scrcpy.android.feature.settings.ui.SettingsDivider

/**
 * 添加/编辑分组对话框
 * 支持选择父路径和分组类型
 */
@Composable
fun AddGroupDialog(
    groups: List<DeviceGroup>,
    initialName: String = "",
    initialParentPath: String = "/",
    initialType: GroupType = GroupType.SESSION,
    isEditMode: Boolean = false,
    onConfirm: (name: String, parentPath: String, type: GroupType) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var parentPath by remember { mutableStateOf(initialParentPath) }
    var groupType by remember { mutableStateOf(initialType) }
    var showPathSelector by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()

    // 计算完整路径预览
    val fullPath = if (parentPath == "/") "/$name" else "$parentPath/$name"

    // 计算初始完整路径（用于编辑模式排除自身）
    val initialFullPath =
        remember {
            if (initialParentPath == "/") "/$initialName" else "$initialParentPath/$initialName"
        }

    // 检查路径是否重复（忽略大小写，同类型分组）
    val isDuplicate =
        remember(name, parentPath, groupType, groups) {
            if (name.isBlank()) return@remember false
            val targetPath = fullPath.lowercase()
            groups.any { group ->
                group.path.lowercase() == targetPath &&
                    group.type == groupType &&
                    (!isEditMode || group.path.lowercase() != initialFullPath.lowercase())
            }
        }

    DialogPage(
        title =
            if (isEditMode) {
                SessionTexts.GROUP_EDIT.get()
            } else {
                SessionTexts.GROUP_ADD.get()
            },
        onDismiss = onDismiss,
        showBackButton = false,
        leftButtonText = CommonTexts.BUTTON_CANCEL.get(),
        rightButtonText = CommonTexts.BUTTON_SAVE.get(),
        rightButtonEnabled = name.isNotBlank() && !isDuplicate,
        onRightButtonClick = {
            if (name.isNotBlank() && !isDuplicate) {
                onConfirm(name, parentPath, groupType)
            }
        },
        enableScroll = false,
    ) {
        SettingsCard(title = SessionTexts.GROUP_OPTION.get()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterChip(
                        selected = groupType == GroupType.SESSION,
                        onClick = { groupType = GroupType.SESSION },
                        label = { Text(SessionTexts.MAIN_TAB_SESSIONS.get()) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(AppDimens.listItemHeight),
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
                        selected = groupType == GroupType.AUTOMATION,
                        onClick = { groupType = GroupType.AUTOMATION },
                        label = { Text(SessionTexts.MAIN_TAB_ACTIONS.get()) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(AppDimens.listItemHeight),
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
            SettingsDivider()
            LabeledInputRow(
                label = SessionTexts.GROUP_NAME.get(),
                value = name,
                onValueChange = { name = it },
                placeholder = SessionTexts.GROUP_PLACEHOLDER_NAME.get(),
                isError = isDuplicate,
            )
            SettingsDivider()
            LabeledClickableRow(
                label = SessionTexts.GROUP_PARENT_PATH.get(),
                trailingText =
                    if (parentPath == "/") {
                        SessionTexts.GROUP_ROOT.get()
                    } else {
                        parentPath
                    },
                onClick = { showPathSelector = true },
                leadingIcon = if (parentPath == "/") Icons.Default.Home else null,
                leadingIconTint = AppColors.iOSBlue,
            )
        }

        AnimatedVisibility(
            visible = name.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SettingsCard(title = SessionTexts.GROUP_PATH_PREVIEW.get()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(AppDimens.listItemHeight)
                            .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = fullPath,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDuplicate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
            onDismiss = { showPathSelector = false },
        )
    }
}

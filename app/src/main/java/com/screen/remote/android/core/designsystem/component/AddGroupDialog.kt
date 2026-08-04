package com.screen.remote.android.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.remote.android.core.common.AppDimens
import com.screen.remote.android.core.common.IosDesignTokens
import com.screen.remote.android.core.domain.model.DeviceGroup
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.SessionTexts

/**
 * 添加/编辑分组对话框
 * 支持选择父路径
 */
@Composable
fun AddGroupDialog(
    groups: List<DeviceGroup>,
    initialName: String = "",
    initialParentPath: String = "/",
    isEditMode: Boolean = false,
    onConfirm: (name: String, parentPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var parentPath by remember { mutableStateOf(initialParentPath) }
    var showPathSelector by remember { mutableStateOf(false) }

    // 计算完整路径预览
    val fullPath = if (parentPath == "/") "/$name" else "$parentPath/$name"

    // 计算初始完整路径（用于编辑模式排除自身）
    val initialFullPath = remember {
        if (initialParentPath == "/") "/$initialName" else "$initialParentPath/$initialName"
    }

    // 检查路径是否重复（忽略大小写）
    val isDuplicate =
        remember(name, parentPath, groups) {
            if (name.isBlank()) return@remember false
            val targetPath = fullPath.lowercase()
            groups.any { group ->
                group.path.lowercase() == targetPath &&
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
                onConfirm(name, parentPath)
            }
        },
        enableScroll = false,
    ) {
        AddGroupSettingsCard(title = SessionTexts.GROUP_OPTION.get()) {
            AddGroupInputRow(
                label = SessionTexts.GROUP_NAME.get(),
                value = name,
                onValueChange = { name = it },
                placeholder = SessionTexts.GROUP_PLACEHOLDER_NAME.get(),
                isError = isDuplicate,
            )
            AddGroupSettingsDivider()
            AddGroupClickableRow(
                label = SessionTexts.GROUP_PARENT_PATH.get(),
                trailingText =
                    if (parentPath == "/") {
                        SessionTexts.GROUP_ROOT.get()
                    } else {
                        parentPath
                    },
                onClick = { showPathSelector = true },
                leadingIcon = if (parentPath == "/") Icons.Default.Home else null,
                leadingIconTint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = name.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            AddGroupSettingsCard(title = SessionTexts.GROUP_PATH_PREVIEW.get()) {
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

@Composable
private fun AddGroupSettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun AddGroupSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = IosDesignTokens.standardHorizontalPadding),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = IosDesignTokens.dividerAlpha),
    )
}

@Composable
private fun AddGroupInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
) {
    AddGroupLabeledRow(label = label) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle =
                TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            decorationBox = { innerTextField ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = IosDesignTokens.fieldContentStartPadding,
                                end = IosDesignTokens.fieldContentEndPadding
                            ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            modifier = Modifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    fontSize = 15.sp,
                                    lineHeight = 15.sp,
                                    color =
                                        if (isError) {
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        },
                                    textAlign = TextAlign.End,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun AddGroupClickableRow(
    label: String,
    trailingText: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color? = null,
) {
    AddGroupLabeledRow(label = label) {
        Row(
            modifier =
                Modifier
                    .width(IosDesignTokens.dialogTrailingActionWidth)
                    .fillMaxHeight()
                    .clickable(onClick = onClick)
                    .padding(horizontal = IosDesignTokens.dialogHeaderHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = leadingIconTint ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IosDesignTokens.externalIconSize),
                )
                Spacer(modifier = Modifier.width(IosDesignTokens.compactInlineSpacing))
            }
            Text(
                text = trailingText,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AddGroupLabeledRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(horizontal = IosDesignTokens.compactHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.widthIn(max = IosDesignTokens.dialogLabelMaxWidth),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = IosDesignTokens.compactSpacing),
            contentAlignment = Alignment.CenterEnd,
        ) {
            content()
        }
    }
}

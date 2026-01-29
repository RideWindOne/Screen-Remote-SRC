package com.mobile.scrcpy.android.feature.settings.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppColors
import com.mobile.scrcpy.android.core.common.FilePathConstants
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.feature.device.ui.component.KeyActionItem
import com.mobile.scrcpy.android.feature.session.ui.component.LabeledInputRow

/**
 * 文件路径选择对话框
 *
 * 功能：
 * - 手动输入路径
 * - 快捷选择常用路径（Download、Documents、Pictures 等）
 * - 重置到当前路径
 *
 * @param currentPath 当前路径
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，返回选择的路径
 */
@Composable
fun FilePathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var editablePath by remember { mutableStateOf(currentPath) }

    val txtTitle = rememberText(SettingsTexts.DIALOG_FILE_PATH_TITLE)
    val txtDefault = rememberText(SettingsTexts.DIALOG_FILE_PATH_DEFAULT)
    val txtQuickSelect = rememberText(SettingsTexts.DIALOG_FILE_PATH_QUICK_SELECT)
    val txtInfo = rememberText(SettingsTexts.DIALOG_FILE_PATH_INFO)
    val txtInfoText = rememberText(SettingsTexts.DIALOG_FILE_PATH_INFO_TEXT)
    val txtReset = rememberText(SettingsTexts.DIALOG_FILE_PATH_RESET)
    val txtSave = rememberText(CommonTexts.BUTTON_SAVE)
    val isDarkTheme = isSystemInDarkTheme()

    val quickPaths = FilePathConstants.QUICK_SELECT_PATHS

    DialogPage(
        title = txtTitle,
        onDismiss = onDismiss,
        showBackButton = true,
        enableScroll = true,
        rightButtonText = txtSave,
        rightButtonEnabled = editablePath.isNotBlank(),
        onRightButtonClick = { onConfirm(editablePath) },
    ) {
        // 路径输入
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            LabeledInputRow(
                label = txtDefault,
                value = editablePath,
                onValueChange = { editablePath = it },
                placeholder = "/sdcard/Download",
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 快捷选择
        SettingsCard(title = txtQuickSelect) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
            ) {
                quickPaths.chunked(3).forEach { rowPaths ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowPaths.forEach { path ->
                            val isSelected = editablePath == path
                            FilterChip(
                                selected = isSelected,
                                onClick = { editablePath = path },
                                label = {
                                    Text(
                                        path.substringAfterLast("/"),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
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
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowPaths.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 重置按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            KeyActionItem(
                icon = Icons.Default.Refresh,
                title = txtReset,
                onClick = { editablePath = currentPath },
            )
        }

        // 说明信息
        SettingsCard(title = txtInfo) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = txtInfoText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

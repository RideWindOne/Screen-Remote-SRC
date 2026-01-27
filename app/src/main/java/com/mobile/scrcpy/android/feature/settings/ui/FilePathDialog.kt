package com.mobile.scrcpy.android.feature.settings.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.core.common.FilePathConstants
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.DialogHeader
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts

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
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun FilePathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var editablePath by remember { mutableStateOf(currentPath) }
    
    val txtTitle = rememberText(SettingsTexts.DIALOG_FILE_PATH_TITLE)
    val txtDefault = rememberText(SettingsTexts.DIALOG_FILE_PATH_DEFAULT)
    val txtQuickSelect = rememberText(SettingsTexts.DIALOG_FILE_PATH_QUICK_SELECT)
    val txtInfo = rememberText(SettingsTexts.DIALOG_FILE_PATH_INFO)
    val txtInfoText = rememberText(SettingsTexts.DIALOG_FILE_PATH_INFO_TEXT)
    val txtReset = rememberText(SettingsTexts.DIALOG_FILE_PATH_RESET)
    val txtSave = rememberText(CommonTexts.BUTTON_SAVE)

    val quickPaths = FilePathConstants.QUICK_SELECT_PATHS

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

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
                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = txtTitle,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 默认路径输入
                    Text(
                        txtDefault,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        OutlinedTextField(
                            value = editablePath,
                            onValueChange = { editablePath = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            placeholder = { Text("Download") },
                            singleLine = true
                        )
                    }

                    // 快捷选择
                    Text(
                        txtQuickSelect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickPaths.chunked(3).forEach { rowPaths ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPaths.forEach { path ->
                                        val isSelected = editablePath == path
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { editablePath = path },
                                            label = {
                                                Text(
                                                    path.substringAfterLast("/"),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowPaths.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // 重置按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editablePath = currentPath },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(txtReset)
                        }
                    }

                    // 说明信息
                    Text(
                        txtInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            txtInfoText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 保存按钮
                    Button(
                        onClick = { onConfirm(editablePath) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editablePath.isNotBlank()
                    ) {
                        Text(txtSave)
                    }
                }
            }
        }
    }
}

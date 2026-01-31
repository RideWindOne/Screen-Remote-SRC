package com.mobile.scrcpy.android.feature.settings.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobile.scrcpy.android.core.common.AppDimens
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.AppDivider
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.LogFileItem
import com.mobile.scrcpy.android.core.designsystem.component.LogViewerDialog
import com.mobile.scrcpy.android.core.designsystem.component.formatFileSize
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.LogTexts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 日志管理屏幕
 *
 * 功能：
 * - 日志文件统计（文件数量、总大小、当前日志大小）
 * - 快捷操作（清除旧日志，保留当前）
 * - 日志文件列表（查看、删除）
 * - 日志查看器（搜索、按 TAG 筛选、分享）
 */
@Composable
fun LogManagementScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var totalSize by remember { mutableLongStateOf(0L) }
    var currentLogSize by remember { mutableLongStateOf(0L) }
    var fileCount by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showLogViewer by remember { mutableStateOf(false) }

    val txtTitle = rememberText(LogTexts.LOG_MANAGEMENT_TITLE)

    fun loadLogFiles() {
        scope.launch {
            withContext(Dispatchers.IO) {
                logFiles = LogManager.getLogFiles()
                totalSize = LogManager.getTotalLogSize()
                fileCount = logFiles.size
                currentLogSize = logFiles.firstOrNull()?.length() ?: 0L
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogFiles()
    }

    DialogPage(
        title = txtTitle,
        onDismiss = onDismiss,
        showBackButton = true,
        trailingContent = {
            TextButton(onClick = { loadLogFiles() }) {
                Text(
                    LogTexts.LOG_REFRESH_BUTTON.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF007AFF),
                )
            }
        },
        enableScroll = true,
        verticalSpacing = 10.dp,
    ) {
        // 日志文件统计
        LogSection(title = LogTexts.LOG_STATS_TITLE.get()) {
            LogStatItem(
                label = LogTexts.LOG_FILE_COUNT.get() + "：",
                value = fileCount.toString(),
            )
            AppDivider()
            LogStatItem(
                label = LogTexts.LOG_TOTAL_SIZE.get() + "：",
                value = formatFileSize(totalSize),
            )
            AppDivider()
            LogStatItem(
                label = LogTexts.LOG_CURRENT_SIZE.get() + "：",
                value = formatFileSize(currentLogSize),
            )
        }

        // 快捷自动化
        LogSection(title = LogTexts.LOG_QUICK_ACTIONS.get()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(AppDimens.listItemHeight)
                        .clickable {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    LogManager.clearOldLogs()
                                }
                                loadLogFiles()
                            }
                        }.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFFFCC00),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = LogTexts.LOG_CLEAR_OLD_LOGS.get(),
                        color = Color(0xFFFFCC00),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    text = LogTexts.LOG_KEEP_CURRENT_ONLY.get(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 日志文件列表
        if (logFiles.isNotEmpty()) {
            LogSection(title = LogTexts.LOG_FILES_SECTION.get()) {
                logFiles.forEach { file ->
                    LogFileItem(
                        file = file,
                        isCurrent = file == logFiles.firstOrNull(),
                        onView = {
                            selectedFile = file
                            showLogViewer = true
                        },
                        onDelete = {
                            selectedFile = file
                            showDeleteDialog = true
                        },
                    )
                }
            }
        }
        AppDivider()
    }

    // 删除确认对话框
    if (showDeleteDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(LogTexts.LOG_DELETE_CONFIRM_TITLE.get()) },
            text = {
                Text(
                    LogTexts.LOG_DELETE_CONFIRM_MESSAGE
                        .get()
                        .replace("%s", selectedFile?.name ?: ""),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                selectedFile?.let { LogManager.deleteLogFile(it) }
                            }
                            loadLogFiles()
                            showDeleteDialog = false
                        }
                    },
                ) {
                    Text(LogTexts.LOG_DELETE_BUTTON.get(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(CommonTexts.BUTTON_CANCEL.get())
                }
            },
        )
    }

    // 日志查看器
    if (showLogViewer && selectedFile != null) {
        LogViewerDialog(
            file = selectedFile!!,
            onDismiss = { showLogViewer = false },
        )
    }
}

/**
 * 日志区域容器
 */
@Composable
private fun LogSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    SettingsCard(title = title, content = content)
}

/**
 * 日志统计项
 */
@Composable
private fun LogStatItem(
    label: String,
    value: String,
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}

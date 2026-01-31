package com.mobile.scrcpy.android.feature.settings.ui

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.common.manager.HapticFeedbackManager
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.LogTexts
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.feature.device.ui.component.AdbKeyManagementDialog
import com.mobile.scrcpy.android.feature.device.ui.component.AdbPairingCodeDialog
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel

/**
 * 设置主屏幕
 *
 * 功能：
 * - 通用设置（外观、分组、保持唤醒、触感反馈、语言、锁屏显示、关于）
 * - ADB 管理（密钥管理、配对、文件传输路径）
 * - 应用日志（启用日志、日志管理、清除日志）
 * - 反馈与支持（提交问题、用户指南）
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAdbKeys: () -> Unit = {}, // TODO
    onNavigateToLogManagement: () -> Unit = {},
    onNavigateToGroupManagement: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showAdbKeyDialog by remember { mutableStateOf(false) }
    var showDevicePairingDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    var showKeepAliveDialog by remember { mutableStateOf(false) }
    var showFilePathDialog by remember { mutableStateOf(false) }

    // 双语文本
    val txtTitle = rememberText(SettingsTexts.SETTINGS_TITLE)
    val txtDone = rememberText(CommonTexts.BUTTON_DONE)
    val txtGeneral = rememberText(SettingsTexts.SETTINGS_GENERAL)
    val txtAppearance = rememberText(SettingsTexts.SETTINGS_APPEARANCE)
    val txtKeepAlive = rememberText(SettingsTexts.SETTINGS_KEEP_ALIVE)
    val txtShowOnLockScreen = rememberText(SettingsTexts.SETTINGS_SHOW_ON_LOCK_SCREEN)
    val txtFloatingHaptic = rememberText(SettingsTexts.SETTINGS_FLOATING_HAPTIC)
    val txtLanguage = rememberText(SettingsTexts.SETTINGS_LANGUAGE)
    val txtAbout = rememberText(SettingsTexts.SETTINGS_ABOUT)
    val txtGroupManage = rememberText(SessionTexts.GROUP_MANAGE)
    val txtAdbManagement = rememberText(SettingsTexts.SETTINGS_ADB_MANAGEMENT)
    val txtManageAdbKeys = rememberText(SettingsTexts.SETTINGS_MANAGE_ADB_KEYS)
    val txtDevicePairing = rememberText(SettingsTexts.SETTINGS_DEVICE_PAIRING)
    val txtFileTransferPath = rememberText(SettingsTexts.SETTINGS_FILE_TRANSFER_PATH)
    val txtAppLogs = rememberText(SettingsTexts.SETTINGS_APP_LOGS)
    val txtEnableLog = rememberText(SettingsTexts.SETTINGS_ENABLE_LOG)
    val txtLogManagement = rememberText(SettingsTexts.SETTINGS_LOG_MANAGEMENT)
    val txtClearLogs = rememberText(SettingsTexts.SETTINGS_CLEAR_LOGS)
    val txtFeedbackSupport = rememberText(SettingsTexts.SETTINGS_FEEDBACK_SUPPORT)
    val txtSubmitIssue = rememberText(SettingsTexts.SETTINGS_SUBMIT_ISSUE)
    val txtUserGuide = rememberText(SettingsTexts.SETTINGS_USER_GUIDE)

    // 帮助文本（使用 rememberText 支持语言切换）
    val helpGroupManage = rememberText(SettingsTexts.HELP_GROUP_MANAGE)
    val helpKeepAlive = rememberText(SettingsTexts.HELP_KEEP_ALIVE)
    val helpFloatingHaptic = rememberText(SettingsTexts.HELP_FLOATING_HAPTIC)
    val helpShowOnLockScreen = rememberText(SettingsTexts.HELP_SHOW_ON_LOCK_SCREEN)
    val helpManageAdbKeys = rememberText(SettingsTexts.HELP_MANAGE_ADB_KEYS)
    val helpDevicePairing = rememberText(SettingsTexts.HELP_DEVICE_PAIRING)
    val helpFileTransferPath = rememberText(SettingsTexts.HELP_FILE_TRANSFER_PATH)
    val helpEnableLog = rememberText(SettingsTexts.HELP_ENABLE_LOG)
    val helpLogManagement = rememberText(SettingsTexts.HELP_LOG_MANAGEMENT)

    val txt1Min = rememberText(CommonTexts.TIME_1_MINUTE)
    val txt5Min = rememberText(CommonTexts.TIME_5_MINUTES)
    val txt10Min = rememberText(CommonTexts.TIME_10_MINUTES)
    val txt30Min = rememberText(CommonTexts.TIME_30_MINUTES)
    val txt1Hour = rememberText(CommonTexts.TIME_1_HOUR)
    val txtAlways = rememberText(CommonTexts.TIME_ALWAYS)

    DialogPage(
        title = txtTitle,
        onDismiss = onBack,
        showBackButton = false,
        rightButtonText = txtDone,
        onRightButtonClick = onBack,
        enableScroll = true,
    ) {
        // 通用设置卡片
        SettingsCard(title = txtGeneral) {
            SettingsItem(
                title = txtAppearance,
                onClick = onNavigateToAppearance,
            )
            SettingsDivider()
            SettingsItem(
                title = txtGroupManage,
                helpText = helpGroupManage,
                onClick = onNavigateToGroupManagement,
            )
            SettingsDivider()
            SettingsItem(
                title = rememberText(SettingsTexts.BACKUP_RESTORE_TITLE),
                helpText = SettingsTexts.HELP_BACKUP_DATA.get(),
                onClick = onNavigateToBackupRestore,
            )
            SettingsDivider()
            SettingsItemWithMenu(
                title = txtKeepAlive,
                subtitle =
                    when (settings.keepAliveMinutes) {
                        1 -> txt1Min
                        5 -> txt5Min
                        10 -> txt10Min
                        30 -> txt30Min
                        60 -> txt1Hour
                        -1 -> txtAlways
                        else -> "${settings.keepAliveMinutes} minutes"
                    },
                expanded = showKeepAliveDialog,
                onExpandedChange = { showKeepAliveDialog = it },
                helpText = helpKeepAlive,
                menuContent = {
                    listOf(
                        1 to txt1Min,
                        5 to txt5Min,
                        10 to txt10Min,
                        30 to txt30Min,
                        60 to txt1Hour,
                        -1 to txtAlways,
                    ).forEach { (minutes, label) ->
                        IOSStyledDropdownMenuItem(
                            text = label,
                            onClick = {
                                viewModel.updateSettings(settings.copy(keepAliveMinutes = minutes))
                                showKeepAliveDialog = false
                            },
                        )
                    }
                },
            )
            SettingsDivider()
            SettingsSwitch(
                title = txtFloatingHaptic,
                checked = settings.enableFloatingHapticFeedback,
                helpText = helpFloatingHaptic,
                onCheckedChange = {
                    viewModel.updateSettings(settings.copy(enableFloatingHapticFeedback = it))
                    // 同步更新全局触感反馈状态
                    HapticFeedbackManager.setEnabled(it)
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtLanguage,
                onClick = onNavigateToLanguage,
            )
            SettingsDivider()
            SettingsSwitch(
                title = txtShowOnLockScreen,
                checked = settings.showOnLockScreen,
                enabled = false,
                helpText = helpShowOnLockScreen,
                onCheckedChange = {
                    viewModel.updateSettings(settings.copy(showOnLockScreen = it))
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtAbout,
                onClick = onNavigateToAbout,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ADB 管理卡片
        SettingsCard(title = txtAdbManagement) {
            SettingsItem(
                title = txtManageAdbKeys,
                helpText = helpManageAdbKeys,
                onClick = { showAdbKeyDialog = true },
            )
            SettingsDivider()
            SettingsItem(
                title = txtDevicePairing,
                enabled = false,
                helpText = helpDevicePairing,
                onClick = { showDevicePairingDialog = true },
            )
            SettingsDivider()
            SettingsItem(
                title = txtFileTransferPath,
                subtitle = settings.fileTransferPath.substringAfterLast('/'),
                helpText = helpFileTransferPath,
                onClick = { showFilePathDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 应用日志卡片
        SettingsCard(title = txtAppLogs) {
            SettingsSwitch(
                title = txtEnableLog,
                checked = settings.enableActivityLog,
                helpText = helpEnableLog,
                onCheckedChange = {
                    viewModel.updateSettings(settings.copy(enableActivityLog = it))
                    LogManager.setEnabled(it)
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtLogManagement,
                helpText = helpLogManagement,
                onClick = onNavigateToLogManagement,
            )
            SettingsDivider()
            SettingsItem(
                title = txtClearLogs,
                isDestructive = true,
                onClick = { showClearLogsDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 反馈与支持卡片
        SettingsCard(title = txtFeedbackSupport) {
            SettingsItem(
                title = txtSubmitIssue,
                showExternalIcon = true,
                isLink = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, AppConstants.GITHUB_ISSUES.toUri())
                    context.startActivity(intent)
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtUserGuide,
                showExternalIcon = true,
                isLink = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, AppConstants.GITHUB_REPO.toUri())
                    context.startActivity(intent)
                },
            )
        }
    }

    // ADB 密钥管理对话框
    if (showAdbKeyDialog) {
        AdbKeyManagementDialog(
//            viewModel = viewModel,
            onDismiss = { showAdbKeyDialog = false },
        )
    }

    // ADB 配对码配对对话框
    if (showDevicePairingDialog) {
        AdbPairingCodeDialog(
            onDismiss = { showDevicePairingDialog = false },
        )
    }

    // 清除日志确认对话框
    if (showClearLogsDialog) {
        val txtClearLogsTitle = rememberText(LogTexts.DIALOG_CLEAR_LOGS_TITLE)
        val txtClearLogsMessage = rememberText(LogTexts.DIALOG_CLEAR_LOGS_MESSAGE)
        val txtClearLogsConfirm = rememberText(LogTexts.DIALOG_CLEAR_LOGS_CONFIRM)
        val txtCancel = rememberText(CommonTexts.BUTTON_CANCEL)

        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(txtClearLogsTitle) },
            text = { Text(txtClearLogsMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LogManager.clearAllLogs()
                        showClearLogsDialog = false
                    },
                ) {
                    Text(txtClearLogsConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
                    Text(txtCancel)
                }
            },
        )
    }

    // 文件路径选择对话框
    if (showFilePathDialog) {
        FilePathDialog(
            currentPath = settings.fileTransferPath,
            onDismiss = { showFilePathDialog = false },
            onConfirm = { path ->
                viewModel.updateSettings(settings.copy(fileTransferPath = path))
                showFilePathDialog = false
            },
        )
    }
}

package com.screen.remote.android.feature.settings.ui.internal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.manager.HapticFeedbackManager
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.designsystem.component.IOSStyledDropdownMenuItem
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.feature.settings.ui.SettingsCard
import com.screen.remote.android.feature.settings.ui.SettingsDivider
import com.screen.remote.android.feature.settings.ui.SettingsItem
import com.screen.remote.android.feature.settings.ui.SettingsItemWithMenu
import com.screen.remote.android.feature.settings.ui.SettingsSwitch

@Composable
internal fun SettingsScreenContent(
    settings: AppSettings,
    texts: SettingsScreenTexts,
    routeState: SettingsScreenRouteState,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAdbKeys: () -> Unit,
    onNavigateToLogManagement: () -> Unit,
    onNavigateToGroupManagement: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onOpenIssueTracker: () -> Unit,
    onOpenUserGuide: () -> Unit,
) {
    DialogPage(
        title = texts.title,
        onDismiss = onBack,
        showBackButton = false,
        rightButtonText = texts.done,
        onRightButtonClick = onBack,
        enableScroll = true,
    ) {
        GeneralSettingsSection(
            settings = settings,
            texts = texts,
            routeState = routeState,
            onNavigateToAppearance = onNavigateToAppearance,
            onNavigateToLanguage = onNavigateToLanguage,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToGroupManagement = onNavigateToGroupManagement,
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onUpdateSettings = onUpdateSettings,
        )

        Spacer(
            modifier =
                androidx.compose.ui.Modifier
                    .height(10.dp),
        )

        AdbManagementSection(
            settings = settings,
            texts = texts,
            routeState = routeState,
            onNavigateToAdbKeys = onNavigateToAdbKeys,
        )

        Spacer(
            modifier =
                androidx.compose.ui.Modifier
                    .height(10.dp),
        )

        LogSettingsSection(
            settings = settings,
            texts = texts,
            routeState = routeState,
            onNavigateToLogManagement = onNavigateToLogManagement,
            onUpdateSettings = onUpdateSettings,
        )

        Spacer(
            modifier =
                androidx.compose.ui.Modifier
                    .height(10.dp),
        )

        FeedbackSupportSection(
            texts = texts,
            onOpenIssueTracker = onOpenIssueTracker,
            onOpenUserGuide = onOpenUserGuide,
        )
    }
}

@Composable
private fun GeneralSettingsSection(
    settings: AppSettings,
    texts: SettingsScreenTexts,
    routeState: SettingsScreenRouteState,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToGroupManagement: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    SettingsCard(title = texts.general) {
        SettingsItem(
            title = texts.appearance,
            onClick = onNavigateToAppearance,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.groupManage,
            helpText = texts.helpGroupManage,
            onClick = onNavigateToGroupManagement,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.backupRestore,
            helpText = texts.helpBackupData,
            onClick = onNavigateToBackupRestore,
        )
        SettingsDivider()
        SettingsItemWithMenu(
            title = texts.keepAlive,
            subtitle = texts.keepAliveLabel(settings.keepAliveMinutes),
            expanded = routeState.showKeepAliveMenu,
            onExpandedChange = routeState::setKeepAliveMenuVisible,
            helpText = texts.helpKeepAlive,
            menuContent = {
                listOf(
                    1 to texts.oneMinute,
                    5 to texts.fiveMinutes,
                    10 to texts.tenMinutes,
                    30 to texts.thirtyMinutes,
                    60 to texts.oneHour,
                    -1 to texts.always,
                ).forEach { (minutes, label) ->
                    IOSStyledDropdownMenuItem(
                        text = label,
                        onClick = {
                            onUpdateSettings(settings.copy(keepAliveMinutes = minutes))
                            routeState.setKeepAliveMenuVisible(false)
                        },
                    )
                }
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.floatingHaptic,
            checked = settings.enableFloatingHapticFeedback,
            helpText = texts.helpFloatingHaptic,
            onCheckedChange = {
                onUpdateSettings(settings.copy(enableFloatingHapticFeedback = it))
                HapticFeedbackManager.setEnabled(it)
            },
        )
        SettingsDivider()
        SettingsItem(
            title = texts.language,
            onClick = onNavigateToLanguage,
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.showOnLockScreen,
            checked = settings.showOnLockScreen,
            enabled = false,
            helpText = texts.helpShowOnLockScreen,
            onCheckedChange = {
                onUpdateSettings(settings.copy(showOnLockScreen = it))
            },
        )
        SettingsDivider()
        SettingsItem(
            title = texts.about,
            onClick = onNavigateToAbout,
        )
    }
}

@Composable
private fun AdbManagementSection(
    settings: AppSettings,
    texts: SettingsScreenTexts,
    routeState: SettingsScreenRouteState,
    onNavigateToAdbKeys: () -> Unit,
) {
    SettingsCard(title = texts.adbManagement) {
        SettingsItem(
            title = texts.manageAdbKeys,
            helpText = texts.helpManageAdbKeys,
            onClick = onNavigateToAdbKeys,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.devicePairing,
            helpText = texts.helpDevicePairing,
            onClick = routeState::openDevicePairingDialog,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.fileTransferPath,
            subtitle = settings.fileTransferPath.substringAfterLast('/'),
            helpText = texts.helpFileTransferPath,
            onClick = routeState::openFilePathDialog,
        )
    }
}

@Composable
private fun LogSettingsSection(
    settings: AppSettings,
    texts: SettingsScreenTexts,
    routeState: SettingsScreenRouteState,
    onNavigateToLogManagement: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    SettingsCard(title = texts.appLogs) {
        SettingsSwitch(
            title = texts.enableLog,
            checked = settings.enableActivityLog,
            helpText = texts.helpEnableLog,
            onCheckedChange = {
                val updated = settings.copy(enableActivityLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.eventStreamLog,
            checked = settings.enableEventStreamLog,
            enabled = settings.enableActivityLog,
            helpText = texts.helpEventStreamLog,
            onCheckedChange = {
                val updated = settings.copy(enableEventStreamLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.audioStreamLog,
            checked = settings.enableAudioStreamLog,
            enabled = settings.enableActivityLog,
            helpText = texts.helpAudioStreamLog,
            onCheckedChange = {
                val updated = settings.copy(enableAudioStreamLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.videoStreamLog,
            checked = settings.enableVideoStreamLog,
            enabled = settings.enableActivityLog,
            helpText = texts.helpVideoStreamLog,
            onCheckedChange = {
                val updated = settings.copy(enableVideoStreamLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.controlStreamLog,
            checked = settings.enableControlStreamLog,
            enabled = settings.enableActivityLog,
            helpText = texts.helpControlStreamLog,
            onCheckedChange = {
                val updated = settings.copy(enableControlStreamLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.shellStreamLog,
            checked = settings.enableShellStreamLog,
            enabled = settings.enableActivityLog,
            helpText = texts.helpShellStreamLog,
            onCheckedChange = {
                val updated = settings.copy(enableShellStreamLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.managementLog,
            checked = settings.enableManagementLog,
            enabled = settings.enableActivityLog,
            helpText = texts.helpManagementLog,
            onCheckedChange = {
                val updated = settings.copy(enableManagementLog = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
        SettingsItem(
            title = texts.logManagement,
            helpText = texts.helpLogManagement,
            onClick = onNavigateToLogManagement,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.clearLogs,
            isDestructive = true,
            onClick = routeState::openClearLogsDialog,
        )
    }
}

@Composable
private fun FeedbackSupportSection(
    texts: SettingsScreenTexts,
    onOpenIssueTracker: () -> Unit,
    onOpenUserGuide: () -> Unit,
) {
    SettingsCard(title = texts.feedbackSupport) {
        SettingsItem(
            title = texts.submitIssue,
            showExternalIcon = true,
            isLink = true,
            onClick = onOpenIssueTracker,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.userGuide,
            showExternalIcon = true,
            isLink = true,
            onClick = onOpenUserGuide,
        )
    }
}

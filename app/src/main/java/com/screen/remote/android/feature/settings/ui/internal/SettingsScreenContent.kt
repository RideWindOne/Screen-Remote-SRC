package com.screen.remote.android.feature.settings.ui.internal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.screen.remote.android.core.common.constants.IosDesignTokens
import com.screen.remote.android.core.common.manager.HapticFeedbackManager
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.designsystem.component.DialogPage
import com.screen.remote.android.core.domain.model.AppSettings
import com.screen.remote.android.core.i18n.SettingsTexts
import com.screen.remote.android.feature.settings.ui.SettingsCard
import com.screen.remote.android.feature.settings.ui.SettingsDivider
import com.screen.remote.android.feature.settings.ui.SettingsItem
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
    onNavigateToCustomCommands: () -> Unit,
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
        scrollContentTopPadding = IosDesignTokens.dialogCompactHeaderSpacerHeight,
        scrollContentBottomPadding = IosDesignTokens.dialogCompactBottomSpacerHeight,
    ) {
        GeneralSettingsSection(
            settings = settings,
            texts = texts,
            onNavigateToAppearance = onNavigateToAppearance,
            onNavigateToLanguage = onNavigateToLanguage,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToGroupManagement = onNavigateToGroupManagement,
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToCustomCommands = onNavigateToCustomCommands,
            onUpdateSettings = onUpdateSettings,
        )

        Spacer(
            modifier =
                androidx.compose.ui.Modifier
                    .height(10.dp),
        )

        AdbManagementSection(
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
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToGroupManagement: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToCustomCommands: () -> Unit,
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
            title = SettingsTexts.SETTINGS_CUSTOM_COMMANDS.get(),
            helpText = SettingsTexts.SETTINGS_CUSTOM_COMMANDS_HELP.get(),
            onClick = onNavigateToCustomCommands,
        )
        SettingsDivider()
        SettingsItem(
            title = texts.backupRestore,
            helpText = texts.helpBackupData,
            onClick = onNavigateToBackupRestore,
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
        SettingsSwitch(
            title = texts.allowScreenCapture,
            checked = settings.allowScreenCapture,
            helpText = texts.helpAllowScreenCapture,
            onCheckedChange = {
                onUpdateSettings(settings.copy(allowScreenCapture = it))
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = texts.performanceStats,
            checked = settings.showPerformanceStats,
            helpText = texts.helpPerformanceStats,
            onCheckedChange = {
                onUpdateSettings(settings.copy(showPerformanceStats = it))
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = "打开通知时提示所有未读消息",
            checked = settings.notifyAllNotificationsOnStart,
            helpText = "开启后，打开通知监控时会提示当前所有未读消息；关闭后只提示之后新来的消息",
            onCheckedChange = {
                onUpdateSettings(settings.copy(notifyAllNotificationsOnStart = it))
            },
        )
        SettingsDivider()
        SettingsSwitch(
            title = "屏蔽系统通知",
            checked = settings.blockSystemNotifications,
            helpText = "开启后，屏蔽系统服务通知（如小米互联、跨屏协同等），保留微信、短信、电话等应用通知",
            onCheckedChange = {
                onUpdateSettings(settings.copy(blockSystemNotifications = it))
            },
        )
        SettingsDivider()
        SettingsItem(
            title = texts.language,
            onClick = onNavigateToLanguage,
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
            title = texts.debugMode,
            checked = settings.enableDebugMode,
            helpText = texts.helpDebugMode,
            onCheckedChange = {
                val updated = settings.copy(enableDebugMode = it)
                onUpdateSettings(updated)
                LogManager.applySettings(updated)
            },
        )
        SettingsDivider()
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

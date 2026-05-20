package com.screen.remote.android.feature.settings.ui.internal

import androidx.compose.runtime.Composable
import com.screen.remote.android.core.common.manager.rememberText
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.core.i18n.LogTexts
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.i18n.SettingsTexts

internal data class SettingsScreenTexts(
    val title: String,
    val done: String,
    val general: String,
    val appearance: String,
    val groupManage: String,
    val backupRestore: String,
    val keepAlive: String,
    val floatingHaptic: String,
    val language: String,
    val showOnLockScreen: String,
    val about: String,
    val adbManagement: String,
    val manageAdbKeys: String,
    val devicePairing: String,
    val fileTransferPath: String,
    val appLogs: String,
    val enableLog: String,
    val audioStreamLog: String,
    val videoStreamLog: String,
    val controlStreamLog: String,
    val eventStreamLog: String,
    val shellStreamLog: String,
    val managementLog: String,
    val logManagement: String,
    val clearLogs: String,
    val feedbackSupport: String,
    val submitIssue: String,
    val userGuide: String,
    val helpGroupManage: String,
    val helpBackupData: String,
    val helpKeepAlive: String,
    val helpFloatingHaptic: String,
    val helpShowOnLockScreen: String,
    val helpManageAdbKeys: String,
    val helpDevicePairing: String,
    val helpFileTransferPath: String,
    val helpEnableLog: String,
    val helpAudioStreamLog: String,
    val helpVideoStreamLog: String,
    val helpControlStreamLog: String,
    val helpEventStreamLog: String,
    val helpShellStreamLog: String,
    val helpManagementLog: String,
    val helpLogManagement: String,
    val oneMinute: String,
    val fiveMinutes: String,
    val tenMinutes: String,
    val thirtyMinutes: String,
    val oneHour: String,
    val always: String,
    val clearLogsTitle: String,
    val clearLogsMessage: String,
    val clearLogsConfirm: String,
    val cancel: String,
)

@Composable
internal fun rememberSettingsScreenTexts(): SettingsScreenTexts =
    SettingsScreenTexts(
        title = rememberText(SettingsTexts.SETTINGS_TITLE),
        done = rememberText(CommonTexts.BUTTON_DONE),
        general = rememberText(SettingsTexts.SETTINGS_GENERAL),
        appearance = rememberText(SettingsTexts.SETTINGS_APPEARANCE),
        groupManage = rememberText(SessionTexts.GROUP_MANAGE),
        backupRestore = rememberText(SettingsTexts.BACKUP_RESTORE_TITLE),
        keepAlive = rememberText(SettingsTexts.SETTINGS_KEEP_ALIVE),
        floatingHaptic = rememberText(SettingsTexts.SETTINGS_FLOATING_HAPTIC),
        language = rememberText(SettingsTexts.SETTINGS_LANGUAGE),
        showOnLockScreen = rememberText(SettingsTexts.SETTINGS_SHOW_ON_LOCK_SCREEN),
        about = rememberText(SettingsTexts.SETTINGS_ABOUT),
        adbManagement = rememberText(SettingsTexts.SETTINGS_ADB_MANAGEMENT),
        manageAdbKeys = rememberText(SettingsTexts.SETTINGS_MANAGE_ADB_KEYS),
        devicePairing = rememberText(SettingsTexts.SETTINGS_DEVICE_PAIRING),
        fileTransferPath = rememberText(SettingsTexts.SETTINGS_FILE_TRANSFER_PATH),
        appLogs = rememberText(SettingsTexts.SETTINGS_APP_LOGS),
        enableLog = rememberText(SettingsTexts.SETTINGS_ENABLE_LOG),
        audioStreamLog = rememberText(SettingsTexts.SETTINGS_AUDIO_STREAM_LOG),
        videoStreamLog = rememberText(SettingsTexts.SETTINGS_VIDEO_STREAM_LOG),
        controlStreamLog = rememberText(SettingsTexts.SETTINGS_CONTROL_STREAM_LOG),
        eventStreamLog = rememberText(SettingsTexts.SETTINGS_EVENT_STREAM_LOG),
        shellStreamLog = rememberText(SettingsTexts.SETTINGS_SHELL_STREAM_LOG),
        managementLog = rememberText(SettingsTexts.SETTINGS_MANAGEMENT_LOG),
        logManagement = rememberText(SettingsTexts.SETTINGS_LOG_MANAGEMENT),
        clearLogs = rememberText(SettingsTexts.SETTINGS_CLEAR_LOGS),
        feedbackSupport = rememberText(SettingsTexts.SETTINGS_FEEDBACK_SUPPORT),
        submitIssue = rememberText(SettingsTexts.SETTINGS_SUBMIT_ISSUE),
        userGuide = rememberText(SettingsTexts.SETTINGS_USER_GUIDE),
        helpGroupManage = rememberText(SettingsTexts.HELP_GROUP_MANAGE),
        helpBackupData = rememberText(SettingsTexts.HELP_BACKUP_DATA),
        helpKeepAlive = rememberText(SettingsTexts.HELP_KEEP_ALIVE),
        helpFloatingHaptic = rememberText(SettingsTexts.HELP_FLOATING_HAPTIC),
        helpShowOnLockScreen = rememberText(SettingsTexts.HELP_SHOW_ON_LOCK_SCREEN),
        helpManageAdbKeys = rememberText(SettingsTexts.HELP_MANAGE_ADB_KEYS),
        helpDevicePairing = rememberText(SettingsTexts.HELP_DEVICE_PAIRING),
        helpFileTransferPath = rememberText(SettingsTexts.HELP_FILE_TRANSFER_PATH),
        helpEnableLog = rememberText(SettingsTexts.HELP_ENABLE_LOG),
        helpAudioStreamLog = rememberText(SettingsTexts.HELP_AUDIO_STREAM_LOG),
        helpVideoStreamLog = rememberText(SettingsTexts.HELP_VIDEO_STREAM_LOG),
        helpControlStreamLog = rememberText(SettingsTexts.HELP_CONTROL_STREAM_LOG),
        helpEventStreamLog = rememberText(SettingsTexts.HELP_EVENT_STREAM_LOG),
        helpShellStreamLog = rememberText(SettingsTexts.HELP_SHELL_STREAM_LOG),
        helpManagementLog = rememberText(SettingsTexts.HELP_MANAGEMENT_LOG),
        helpLogManagement = rememberText(SettingsTexts.HELP_LOG_MANAGEMENT),
        oneMinute = rememberText(CommonTexts.TIME_1_MINUTE),
        fiveMinutes = rememberText(CommonTexts.TIME_5_MINUTES),
        tenMinutes = rememberText(CommonTexts.TIME_10_MINUTES),
        thirtyMinutes = rememberText(CommonTexts.TIME_30_MINUTES),
        oneHour = rememberText(CommonTexts.TIME_1_HOUR),
        always = rememberText(CommonTexts.TIME_ALWAYS),
        clearLogsTitle = rememberText(LogTexts.DIALOG_CLEAR_LOGS_TITLE),
        clearLogsMessage = rememberText(LogTexts.DIALOG_CLEAR_LOGS_MESSAGE),
        clearLogsConfirm = rememberText(LogTexts.DIALOG_CLEAR_LOGS_CONFIRM),
        cancel = rememberText(CommonTexts.BUTTON_CANCEL),
    )

internal fun SettingsScreenTexts.keepAliveLabel(minutes: Int): String =
    when (minutes) {
        1 -> oneMinute
        5 -> fiveMinutes
        10 -> tenMinutes
        30 -> thirtyMinutes
        60 -> oneHour
        -1 -> always
        else -> "$minutes minutes"
    }

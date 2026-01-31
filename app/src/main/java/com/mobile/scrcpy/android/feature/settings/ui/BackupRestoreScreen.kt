package com.mobile.scrcpy.android.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mobile.scrcpy.android.core.common.manager.rememberText
import com.mobile.scrcpy.android.core.common.util.FilePickerHelper
import com.mobile.scrcpy.android.core.designsystem.component.DialogPage
import com.mobile.scrcpy.android.core.domain.model.AppSettings
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.core.i18n.SettingsTexts
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Composable
fun BackupRestoreScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 导出文件选择器
    val exportLauncher =
        FilePickerHelper.rememberExportFileLauncher(
            mimeType = "application/json",
        ) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val result = BackupManager.exportData(context, viewModel, it)
                        successMessage = result
                        showSuccessDialog = true
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Unknown error"
                        showErrorDialog = true
                    }
                }
            }
        }

    // 导入文件选择器
    val importLauncher =
        FilePickerHelper.rememberImportFileLauncher(
            mimeTypes = arrayOf("application/json"),
        ) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val result = BackupManager.importData(context, viewModel, it)
                        successMessage = result
                        showSuccessDialog = true
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Unknown error"
                        showErrorDialog = true
                    }
                }
            }
        }

    val txtTitle = rememberText(SettingsTexts.BACKUP_RESTORE_TITLE)
    val txtDone = rememberText(CommonTexts.BUTTON_DONE)
    val txtBackupData = rememberText(SettingsTexts.BACKUP_DATA)
    val txtRestoreData = rememberText(SettingsTexts.RESTORE_DATA)
    val txtBackupInfo = rememberText(SettingsTexts.BACKUP_INFO)

    DialogPage(
        title = txtTitle,
        onDismiss = onBack,
        showBackButton = false,
        rightButtonText = txtDone,
        onRightButtonClick = onBack,
        enableScroll = true,
    ) {
        SettingsCard(title = txtBackupInfo) {
            SettingsItem(
                title = txtBackupData,
                helpText = SettingsTexts.HELP_BACKUP_DATA.get(),
                onClick = {
                    val timestamp = System.currentTimeMillis()
                    exportLauncher.launch("scrcpy_backup_$timestamp.json")
                },
            )
            SettingsDivider()
            SettingsItem(
                title = txtRestoreData,
                helpText = SettingsTexts.HELP_RESTORE_DATA.get(),
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                },
            )
        }
    }

    // 成功对话框
    if (showSuccessDialog) {
        val txtConfirm = rememberText(CommonTexts.BUTTON_CONFIRM)

        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text(txtConfirm)
                }
            },
        )
    }

    // 错误对话框
    if (showErrorDialog) {
        val txtError = rememberText(CommonTexts.ERROR_LABEL)
        val txtConfirm = rememberText(CommonTexts.BUTTON_CONFIRM)

        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(txtError) },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text(txtConfirm)
                }
            },
        )
    }
}

/**
 * 备份管理器
 */
object BackupManager {
    suspend fun exportData(
        context: Context,
        viewModel: MainViewModel,
        uri: Uri,
    ): String =
        try {
            // 收集所有数据
            val sessions = viewModel.sessionRepository.sessionDataFlow.first()
            val groups = viewModel.groupViewModel.groups.first()
            val settings = viewModel.settingsViewModel.settings.first()

            // 读取 ADB 密钥
            val adbKeysDir = File(context.filesDir, "adb_keys")
            val privateKeyFile = File(adbKeysDir, "adbkey")
            val publicKeyFile = File(adbKeysDir, "adbkey.pub")

            val privateKey =
                if (privateKeyFile.exists()) {
                    privateKeyFile.readText()
                } else {
                    ""
                }
            val publicKey =
                if (publicKeyFile.exists()) {
                    publicKeyFile.readText()
                } else {
                    ""
                }

            // 构建备份数据
            val backupData =
                BackupData(
                    version = 1,
                    sessions = sessions,
                    groups =
                        groups.map {
                            BackupGroupData(
                                it.id,
                                it.name,
                                it.type.name,
                                it.path,
                                it.parentPath,
                                it.description,
                                it.createdAt,
                            )
                        },
                    settings = settings,
                    adbKeys = AdbKeysData(privateKey, publicKey),
                )

            // 序列化为 JSON
            val json =
                Json {
                    prettyPrint = true
                    encodeDefaults = true
                }
            val jsonString = json.encodeToString(BackupData.serializer(), backupData)

            // 写入文件
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }

            "导出成功"
        } catch (e: Exception) {
            throw Exception("${CommonTexts.ERROR_LABEL.get()}: ${e.message}")
        }

    suspend fun importData(
        context: Context,
        viewModel: MainViewModel,
        uri: Uri,
    ): String =
        try {
            // 读取文件
            val jsonString =
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("无法读取文件")

            // 解析 JSON
            val json = Json { ignoreUnknownKeys = true }
            val backupData = json.decodeFromString(BackupData.serializer(), jsonString)

            // 恢复 ADB 密钥
            if (backupData.adbKeys.privateKey.isNotEmpty() && backupData.adbKeys.publicKey.isNotEmpty()) {
                val adbKeysDir = File(context.filesDir, "adb_keys")
                if (!adbKeysDir.exists()) {
                    adbKeysDir.mkdirs()
                }
                File(adbKeysDir, "adbkey").writeText(backupData.adbKeys.privateKey)
                File(adbKeysDir, "adbkey.pub").writeText(backupData.adbKeys.publicKey)
            }

            // 恢复设置
            viewModel.settingsViewModel.updateSettings(backupData.settings)

            // ========== 第一步：记录备份分组信息 ==========
            // 备份分组：旧ID -> path
            val backupOldIdToPath = backupData.groups.associate { it.id to it.path } // TODO
            // 备份分组：path -> 旧ID
            val backupPathToOldId = backupData.groups.associate { it.path to it.id }

            // ========== 第二步：恢复分组（按 path 覆盖）==========
            val currentGroups = viewModel.groupViewModel.groups.first()

            // 删除相同 path 的旧分组
            backupData.groups.forEach { groupData ->
                val existingGroup = currentGroups.find { it.path == groupData.path }
                existingGroup?.let { viewModel.groupViewModel.removeGroup(it.id) }
            }

            // 添加备份中的分组
            backupData.groups.forEach { groupData ->
                viewModel.groupViewModel.addGroup(
                    groupData.name,
                    groupData.parentPath,
                    GroupType.valueOf(groupData.type),
                )
            }

            // 等待分组添加完成
            kotlinx.coroutines.delay(100)

            // ========== 第三步：构建完整的 ID 映射表 ==========
            val updatedGroups = viewModel.groupViewModel.groups.first()

            // 新分组：path -> 新ID
            val pathToNewId = updatedGroups.associate { it.path to it.id }

            // 核心映射：备份旧ID -> 当前新ID
            val backupIdToNewId = mutableMapOf<String, String>()
            backupData.groups.forEach { backupGroup ->
                val newId = pathToNewId[backupGroup.path]
                if (newId != null) {
                    backupIdToNewId[backupGroup.id] = newId
                }
            }

            // ========== 第四步：更新现有会话的 groupIds ==========
            val currentSessions = viewModel.sessionRepository.sessionDataFlow.first()

            currentSessions.forEach { session ->
                if (session.groupIds.isNotEmpty()) {
                    var needsUpdate = false
                    val updatedGroupIds =
                        session.groupIds.map { oldId ->
                            // 查找这个 oldId 对应的 path
                            val oldGroup = currentGroups.find { it.id == oldId }
                            if (oldGroup != null && backupPathToOldId.containsKey(oldGroup.path)) {
                                // 这个分组被覆盖了，使用新 ID
                                needsUpdate = true
                                pathToNewId[oldGroup.path] ?: oldId
                            } else {
                                oldId
                            }
                        }
                    if (needsUpdate) {
                        viewModel.sessionRepository.updateSession(session.copy(groupIds = updatedGroupIds))
                    }
                }
            }

            // ========== 第五步：恢复备份中的会话（映射 groupIds）==========
            val currentSessionIds = currentSessions.map { it.id }.toSet()

            backupData.sessions.forEach { session ->
                // 映射 groupIds：备份旧ID -> 当前新ID
                val updatedGroupIds =
                    session.groupIds.map { backupOldId ->
                        backupIdToNewId[backupOldId] ?: backupOldId
                    }
                val updatedSession = session.copy(groupIds = updatedGroupIds)

                // 根据 ID 是否存在，决定是更新还是新增
                if (currentSessionIds.contains(session.id)) {
                    viewModel.sessionRepository.updateSession(updatedSession)
                } else {
                    viewModel.sessionRepository.addSession(updatedSession)
                }
            }

            "导入成功"
        } catch (e: Exception) {
            throw Exception("${CommonTexts.ERROR_LABEL.get()}: ${e.message}")
        }
}

/**
 * 备份数据结构
 */
@Serializable
data class BackupData(
    val version: Int,
    val sessions: List<SessionData>,
    val groups: List<BackupGroupData>,
    val settings: AppSettings,
    val adbKeys: AdbKeysData,
)

@Serializable
data class BackupGroupData(
    val id: String,
    val name: String,
    val type: String,
    val path: String,
    val parentPath: String,
    val description: String,
    val createdAt: Long,
)

@Serializable
data class AdbKeysData(
    val privateKey: String,
    val publicKey: String,
)

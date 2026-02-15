package com.mobile.scrcpy.android.feature.settings.ui

import android.content.Context
import android.net.Uri
import com.mobile.scrcpy.android.core.data.repository.SessionData
import com.mobile.scrcpy.android.core.domain.model.GroupType
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import com.mobile.scrcpy.android.feature.session.viewmodel.MainViewModel
import com.mobile.scrcpy.android.infrastructure.adb.AdbEndpointTlsDataStore
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.adb.key.core.adb.AdbKeyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File

internal object BackupManager {
    private val exportJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }
    private val importJson = Json { ignoreUnknownKeys = true }

    suspend fun exportData(
        context: Context,
        viewModel: MainViewModel,
        uri: Uri,
    ): String =
        try {
            val sessions = viewModel.sessionRepository.sessionDataFlow.first()
            val groups = viewModel.groupViewModel.groups.first()
            val settings = viewModel.settingsViewModel.settings.first()
            val adbKeys = readAdbKeys(context)

            val backupData =
                BackupData(
                    version = 2,
                    sessions = sessions,
                    groups =
                        groups.map {
                            BackupGroupData(
                                id = it.id,
                                name = it.name,
                                type = it.type.name,
                                path = it.path,
                                parentPath = it.parentPath,
                                description = it.description,
                                createdAt = it.createdAt,
                            )
                        },
                    settings = settings,
                    adbKeys = adbKeys,
                )

            val jsonString = exportJson.encodeToString(BackupData.serializer(), backupData)

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
            val jsonString =
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("无法读取文件")

            val backupData = importJson.decodeFromString(BackupData.serializer(), jsonString)

            restoreAdbKeys(context, backupData.adbKeys)
            viewModel.settingsViewModel.updateSettings(backupData.settings)
            restoreGroupsAndSessions(viewModel, backupData)

            "导入成功"
        } catch (e: Exception) {
            throw Exception("${CommonTexts.ERROR_LABEL.get()}: ${e.message}")
        }

    private fun readAdbKeys(context: Context): AdbKeysData {
        val runtimeRoot = runtimeRoot(context)
        return AdbKeysData(
            privateKey = readTextIfExists(File(runtimeRoot, "adbkey")),
            publicKey = readTextIfExists(File(runtimeRoot, "adbkey.pub")),
            tlsIdentityStateJson = AdbEndpointTlsDataStore(context).exportJson().orEmpty(),
        )
    }

    private suspend fun restoreAdbKeys(
        context: Context,
        adbKeys: AdbKeysData,
    ) {
        if (adbKeys.privateKey.isBlank() || adbKeys.publicKey.isBlank()) {
            return
        }

        val runtimeRoot = runtimeRoot(context)
        writeText(runtimeRoot, "adbkey", adbKeys.privateKey)
        writeText(runtimeRoot, "adbkey.pub", adbKeys.publicKey)
        AdbEndpointTlsDataStore(context).importJson(adbKeys.tlsIdentityStateJson)
        AdbConnectionManager.getInstance(context).refreshRuntimeIdentity()
    }

    private fun runtimeRoot(context: Context): File = AdbKeyManager.defaultStorageRoot(context)

    private fun readTextIfExists(file: File): String =
        if (file.exists()) {
            file.readText()
        } else {
            ""
        }

    private fun writeText(
        root: File,
        name: String,
        content: String,
    ) {
        if (!root.exists()) {
            root.mkdirs()
        }
        File(root, name).writeText(content)
    }

    private fun writeOptionalText(
        root: File,
        name: String,
        content: String,
    ) {
        if (!root.exists()) {
            root.mkdirs()
        }
        val file = File(root, name)
        if (content.isBlank()) {
            if (file.exists()) {
                file.delete()
            }
            return
        }
        file.writeText(content)
    }

    private suspend fun restoreGroupsAndSessions(
        viewModel: MainViewModel,
        backupData: BackupData,
    ) {
        val currentGroups = viewModel.groupViewModel.groups.first()
        val backupPathToOldId = backupData.groups.associate { it.path to it.id }

        backupData.groups.forEach { groupData ->
            currentGroups.find { it.path == groupData.path }?.let { existingGroup ->
                viewModel.groupViewModel.removeGroup(existingGroup.id)
            }
        }

        backupData.groups.forEach { groupData ->
            viewModel.groupViewModel.addGroup(
                groupData.name,
                groupData.parentPath,
                GroupType.valueOf(groupData.type),
            )
        }

        delay(100)

        val updatedGroups = viewModel.groupViewModel.groups.first()
        val pathToNewId = updatedGroups.associate { it.path to it.id }
        val backupIdToNewId =
            backupData.groups
                .mapNotNull { backupGroup ->
                    pathToNewId[backupGroup.path]?.let { newId -> backupGroup.id to newId }
                }.toMap()

        val currentSessions = viewModel.sessionRepository.sessionDataFlow.first()
        updateExistingSessions(viewModel, currentSessions, currentGroups, backupPathToOldId, pathToNewId)
        restoreBackupSessions(viewModel, backupData, currentSessions, backupIdToNewId)
    }

    private suspend fun updateExistingSessions(
        viewModel: MainViewModel,
        currentSessions: List<SessionData>,
        currentGroups: List<com.mobile.scrcpy.android.core.domain.model.DeviceGroup>,
        backupPathToOldId: Map<String, String>,
        pathToNewId: Map<String, String>,
    ) {
        currentSessions.forEach { session ->
            if (session.groupIds.isEmpty()) {
                return@forEach
            }

            var needsUpdate = false
            val updatedGroupIds =
                session.groupIds.map { oldId ->
                    val oldGroup = currentGroups.find { it.id == oldId }
                    if (oldGroup != null && backupPathToOldId.containsKey(oldGroup.path)) {
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

    private suspend fun restoreBackupSessions(
        viewModel: MainViewModel,
        backupData: BackupData,
        currentSessions: List<SessionData>,
        backupIdToNewId: Map<String, String>,
    ) {
        val currentSessionIds = currentSessions.map { it.id }.toSet()

        backupData.sessions.forEach { session ->
            val updatedSession =
                session.copy(
                    groupIds = session.groupIds.map { backupOldId -> backupIdToNewId[backupOldId] ?: backupOldId },
                )

            if (session.id in currentSessionIds) {
                viewModel.sessionRepository.updateSession(updatedSession)
            } else {
                viewModel.sessionRepository.addSession(updatedSession)
            }
        }
    }
}

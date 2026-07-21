package com.screen.remote.android.feature.settings.ui

import android.content.Context
import android.net.Uri
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.repository.SessionData
import com.screen.remote.android.core.domain.model.GroupType
import com.screen.remote.android.core.i18n.CommonTexts
import com.screen.remote.android.feature.device.data.PairingEndpointMetadataManager
import com.screen.remote.android.feature.session.viewmodel.MainViewModel
import com.screen.remote.android.infrastructure.adb.AdbEndpointTlsDataStore
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import com.screen.remote.android.infrastructure.adb.key.core.adb.AdbKeyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
            val pairingEndpointMetadata = PairingEndpointMetadataManager(context).exportBackup()
            val adbKeys = readAdbKeys(context)

            val backupData =
                BackupData(
                    version = 4,
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
                    pairingEndpointMetadata = pairingEndpointMetadata,
                    adbKeys = adbKeys,
                )

            val jsonString = exportJson.encodeToString(BackupData.serializer(), backupData)

            val outputStream =
                context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw Exception("无法写入文件")
            outputStream.use {
                outputStream.write(jsonString.toByteArray())
                outputStream.flush()
            }

            "导出成功"
        } catch (e: Exception) {
            LogManager.e(LogTags.BACKUP_RESTORE, "Failed to export backup: uri=$uri", e)
            throw Exception("${CommonTexts.ERROR_LABEL.get()}: ${e.message}", e)
        }

    suspend fun importData(
        context: Context,
        viewModel: MainViewModel,
        uri: Uri,
    ): String =
        try {
            val rawJsonString =
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("无法读取文件")

            val jsonString = sanitizeBackupRuntimeCaches(extractFirstJsonObject(rawJsonString))
            val backupData = importJson.decodeFromString(BackupData.serializer(), jsonString)

            restoreAdbKeys(context, backupData.adbKeys)
            PairingEndpointMetadataManager(context).importBackup(backupData.pairingEndpointMetadata)
            viewModel.settingsViewModel.updateSettings(backupData.settings)
            restoreGroupsAndSessions(viewModel, backupData)

            "导入成功"
        } catch (e: Exception) {
            LogManager.e(LogTags.BACKUP_RESTORE, "Failed to import backup: uri=$uri", e)
            throw Exception("${CommonTexts.ERROR_LABEL.get()}: ${e.message}", e)
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
        currentGroups: List<com.screen.remote.android.core.domain.model.DeviceGroup>,
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

private val runtimeEncoderListFields =
    setOf(
        "remoteVideoEncoders",
        "remoteAudioEncoders",
    )

private val runtimeCodecStringFields =
    setOf(
        "deviceSerial",
        "selectedVideoCodec",
        "selectedAudioCodec",
        "selectedVideoDecoder",
        "selectedAudioDecoder",
        "selectedVideoEncoder",
        "selectedAudioEncoder",
    )

private val backupSanitizerJson = Json { ignoreUnknownKeys = true }

/**
 * 备份中的编解码器探测结果只是运行时缓存。缓存结构不匹配时忽略该字段，
 * 让 SessionData 使用默认空值并在下次连接时重新探测；其它会话字段仍严格反序列化。
 */
internal fun sanitizeBackupRuntimeCaches(rawJson: String): String {
    val root = backupSanitizerJson.parseToJsonElement(rawJson).jsonObject
    val sessions = root["sessions"]?.jsonArray ?: return rawJson
    val sanitizedSessions =
        sessions.map { sessionElement ->
            val session = sessionElement as? JsonObject ?: return@map sessionElement
            val fields = session.toMutableMap()

            runtimeEncoderListFields.forEach { fieldName ->
                val value = fields[fieldName] ?: return@forEach
                val isStructuredEncoderList =
                    value is JsonArray && value.all(::isValidEncoderCacheEntry)
                if (!isStructuredEncoderList) {
                    fields.remove(fieldName)
                }
            }
            runtimeCodecStringFields.forEach { fieldName ->
                val value = fields[fieldName] ?: return@forEach
                if (value !is JsonPrimitive || !value.isString) {
                    fields.remove(fieldName)
                }
            }

            JsonObject(fields)
        }

    return JsonObject(root + ("sessions" to JsonArray(sanitizedSessions))).toString()
}

private fun isValidEncoderCacheEntry(element: kotlinx.serialization.json.JsonElement): Boolean {
    val encoder = element as? JsonObject ?: return false
    return listOf("name", "codec", "mimeType", "mediaType").all { fieldName ->
        val value = encoder[fieldName]
        value is JsonPrimitive && value.isString
    }
}

/**
 * 部分 DocumentsProvider 在覆盖已有文件时可能没有截断旧内容，导致一个完整备份后残留旧 JSON 尾部。
 * 只提取第一个完整的根对象；对象内部字符串里的花括号和转义字符不会参与层级计算。
 */
internal fun extractFirstJsonObject(raw: String): String {
    val start = raw.indexOfFirst { !it.isWhitespace() && it != '\uFEFF' }
    require(start >= 0 && raw[start] == '{') { "备份文件不是有效的 JSON 对象" }

    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until raw.length) {
        val char = raw[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }

        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                require(depth >= 0) { "备份文件 JSON 结构无效" }
                if (depth == 0) {
                    return raw.substring(start, index + 1)
                }
            }
        }
    }

    throw IllegalArgumentException("备份文件 JSON 不完整")
}

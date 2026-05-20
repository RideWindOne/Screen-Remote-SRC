package com.screen.remote.android.feature.device.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.AdbKeysInfo
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.infrastructure.adb.AdbRuntimeProvider
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionManager
import dadb.android.runtime.ExperimentalDadbAndroidApi
import dadb.android.runtime.AdbRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * ADB 密钥管理 ViewModel
 * 职责：密钥生成/保存/导入/导出、公钥获取
 */
@OptIn(ExperimentalDadbAndroidApi::class)
class AdbKeysViewModel(
    private val context: Context, // TODO
    private val adbConnectionManager: AdbConnectionManager,
) : ViewModel() {
    private val storageRoot
        get() = AdbRuntime.defaultStorageRoot(context)

    private val adbRuntime: AdbRuntime
        get() = AdbRuntimeProvider.get()

    // ============ 密钥生成 ============

    suspend fun generateAdbKeys(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                adbRuntime.regenerateKeyPair()

                adbConnectionManager.refreshRuntimeIdentity()

                LogManager.d(LogTags.ADB_KEYS_VM, "新的 ADB 密钥对生成成功")
                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "生成 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ============ 密钥读取 ============

    fun getAdbPublicKey(): Flow<String?> =
        flow {
            emit(adbConnectionManager.getPublicKey())
        }

    fun getAdbKeysInfo(): Flow<AdbKeysInfo> =
        flow {
            val identity = adbRuntime.readIdentity()
            emit(
                AdbKeysInfo(
                    keysDir = storageRoot.absolutePath,
                    privateKey = identity.privateKey.orEmpty(),
                    publicKey = identity.publicKey.orEmpty(),
                ),
            )
        }

    // ============ 密钥保存 ============

    suspend fun saveAdbKeys(
        privateKey: String,
        publicKey: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            persistIdentity(privateKey, publicKey)
        }

    // ============ 密钥导出 ============

    suspend fun exportAdbKeysSeparately(
        privateKeyUri: android.net.Uri,
        publicKeyUri: android.net.Uri,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val identity = adbRuntime.readIdentity()
                val privateKey = identity.privateKey
                val publicKey = identity.publicKey
                if (privateKey.isNullOrEmpty() || publicKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Keys not found"))
                }

                context.contentResolver.openOutputStream(privateKeyUri)?.use { output ->
                    output.write(privateKey.toByteArray())
                }

                context.contentResolver.openOutputStream(publicKeyUri)?.use { output ->
                    output.write(publicKey.toByteArray())
                }

                LogManager.d(LogTags.ADB_KEYS_VM, "ADB 密钥导出成功")
                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "导出 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ============ 密钥导入 ============

    suspend fun importAdbKeysFromUris(uris: List<android.net.Uri>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (uris.size != 2) {
                    return@withContext Result.failure(Exception(AdbTexts.ERROR_SELECT_EXACTLY_2_FILES.get()))
                }

                var privateKeyUri: android.net.Uri? = null
                var publicKeyUri: android.net.Uri? = null

                uris.forEach { candidateUri ->
                    val fileName = getFileName(candidateUri)
                    when {
                        fileName.equals("adbkey", ignoreCase = true) || !fileName.contains(".") -> {
                            privateKeyUri = candidateUri
                        }

                        fileName.equals(
                            "adbkey.pub",
                            ignoreCase = true,
                        ) || fileName.endsWith(".pub", ignoreCase = true) -> {
                            publicKeyUri = candidateUri
                        }
                    }
                }

                if (privateKeyUri == null || publicKeyUri == null) {
                    return@withContext Result.failure(Exception(AdbTexts.ERROR_IDENTIFY_KEY_FILES.get()))
                }

                val privateKey =
                    context.contentResolver.openInputStream(privateKeyUri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: return@withContext Result.failure(Exception("无法读取私钥文件"))
                val publicKey =
                    context.contentResolver.openInputStream(publicKeyUri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: return@withContext Result.failure(Exception("无法读取公钥文件"))

                persistIdentity(privateKey, publicKey)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "导入 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun getFileName(uri: android.net.Uri): String {
        var fileName = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment ?: ""
        }
        return fileName
    }

    // 保留旧的文本编辑方式导入（用于手动粘贴）
    suspend fun importAdbKeys(
        privateKey: String,
        publicKey: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            persistIdentity(privateKey, publicKey)
        }

    private suspend fun persistIdentity(
        privateKey: String,
        publicKey: String,
    ): Result<Unit> =
        runCatching {
            adbRuntime.replaceKeyPair(privateKey, publicKey)

            adbConnectionManager.refreshRuntimeIdentity()

            LogManager.d(LogTags.ADB_KEYS_VM, "ADB 密钥保存成功")
            LogManager.d(LogTags.ADB_KEYS_VM, "ADB 运行时目录: ${storageRoot.absolutePath}")
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                LogManager.e(LogTags.ADB_KEYS_VM, "保存 ADB 密钥失败: ${error.message}", error)
                Result.failure(error)
            },
        )

    // ============ Factory ============

    companion object {
        fun provideFactory(
            context: Context,
            adbConnectionManager: AdbConnectionManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AdbKeysViewModel(context, adbConnectionManager) as T
            }
    }
}

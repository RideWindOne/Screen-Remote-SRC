package com.mobile.scrcpy.android.feature.device.viewmodel.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.AdbKeysInfo
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ADB 密钥管理 ViewModel
 * 职责：密钥生成/保存/导入/导出、公钥获取
 */
class AdbKeysViewModel(
    private val context: Context, // TODO
    private val adbConnectionManager: AdbConnectionManager,
) : ViewModel() {
    // ============ 密钥生成 ============

    suspend fun generateAdbKeys(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val keysDir = File(context.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 删除旧密钥
                if (privateKeyFile.exists()) {
                    privateKeyFile.delete()
                }
                if (publicKeyFile.exists()) {
                    publicKeyFile.delete()
                }

                // 生成新密钥
                dadb.AdbKeyPair.generate(privateKeyFile, publicKeyFile)

                // 重新加载密钥对
                adbConnectionManager.reloadKeyPair()

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
            val keysDir = File(context.filesDir, "adb_keys").absolutePath
            val keysDirFile = File(keysDir)

            val privateKeyFile = File(keysDirFile, "adbkey")
            val publicKeyFile = File(keysDirFile, "adbkey.pub")

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

            emit(AdbKeysInfo(keysDir, privateKey, publicKey))
        }

    // ============ 密钥保存 ============

    suspend fun saveAdbKeys(
        privateKey: String,
        publicKey: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val keysDir = File(context.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 保存私钥
                privateKeyFile.writeText(privateKey)
                // 保存公钥
                publicKeyFile.writeText(publicKey)

                // 重新加载密钥对
                adbConnectionManager.reloadKeyPair()

                LogManager.d(LogTags.ADB_KEYS_VM, "ADB 密钥保存成功")
                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "保存 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ============ 密钥导出 ============

    suspend fun exportAdbKeysSeparately(
        privateKeyUri: android.net.Uri,
        publicKeyUri: android.net.Uri,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val keysDir = File(context.filesDir, "adb_keys")
                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
                    return@withContext Result.failure(Exception("Keys not found"))
                }

                // 导出私钥
                context.contentResolver.openOutputStream(privateKeyUri)?.use { output ->
                    privateKeyFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                // 导出公钥
                context.contentResolver.openOutputStream(publicKeyUri)?.use { output ->
                    publicKeyFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
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

                val keysDir = File(context.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                // 读取文件名判断哪个是私钥哪个是公钥
                var privateKeyUri: android.net.Uri? = null
                var publicKeyUri: android.net.Uri? = null

                uris.forEach { uri ->
                    val fileName = getFileName(uri)
                    when {
                        fileName.equals("adbkey", ignoreCase = true) || !fileName.contains(".") -> {
                            privateKeyUri = uri
                        }

                        fileName.equals(
                            "adbkey.pub",
                            ignoreCase = true,
                        ) || fileName.endsWith(".pub", ignoreCase = true) -> {
                            publicKeyUri = uri
                        }
                    }
                }

                if (privateKeyUri == null || publicKeyUri == null) {
                    return@withContext Result.failure(Exception(AdbTexts.ERROR_IDENTIFY_KEY_FILES.get()))
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 导入私钥
                context.contentResolver.openInputStream(privateKeyUri)?.use { input ->
                    privateKeyFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 导入公钥
                context.contentResolver.openInputStream(publicKeyUri)?.use { input ->
                    publicKeyFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 重新加载密钥对
                adbConnectionManager.reloadKeyPair()

                LogManager.d(LogTags.ADB_KEYS_VM, "ADB 密钥导入成功")
                LogManager.d(LogTags.ADB_KEYS_VM, "私钥文件: ${privateKeyFile.absolutePath}")
                LogManager.d(LogTags.ADB_KEYS_VM, "公钥文件: ${publicKeyFile.absolutePath}")

                Result.success(Unit)
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
        // 如果无法获取文件名，使用 URI 的最后一段
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
            try {
                val keysDir = File(context.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 导入私钥
                privateKeyFile.writeText(privateKey)
                // 导入公钥
                publicKeyFile.writeText(publicKey)

                // 重新加载密钥对
                adbConnectionManager.reloadKeyPair()

                LogManager.d(LogTags.ADB_KEYS_VM, "ADB 密钥导入成功")
                LogManager.d(LogTags.ADB_KEYS_VM, "私钥文件: ${privateKeyFile.absolutePath}")
                LogManager.d(LogTags.ADB_KEYS_VM, "公钥文件: ${publicKeyFile.absolutePath}")

                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "导入 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }

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

package com.mobile.scrcpy.android.feature.device.viewmodel.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.core.domain.model.AdbKeysInfo
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
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager
) : ViewModel() {

    // ============ 密钥生成 ============

    suspend fun generateAdbKeys(): Result<Unit> {
        return withContext(Dispatchers.IO) {
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

                LogManager.d(LogTags.ADB_KEYS_VM, "新的 ADB 密钥对生成成功")
                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "生成 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ============ 密钥读取 ============

    fun getAdbPublicKey(): Flow<String?> = flow {
        emit(adbConnectionManager.getPublicKey())
    }

    fun getAdbKeysInfo(): Flow<AdbKeysInfo> = flow {
        val keysDir = File(context.filesDir, "adb_keys").absolutePath
        val keysDirFile = File(keysDir)

        val privateKeyFile = File(keysDirFile, "adbkey")
        val publicKeyFile = File(keysDirFile, "adbkey.pub")

        val privateKey = if (privateKeyFile.exists()) {
            privateKeyFile.readText()
        } else {
            ""
        }

        val publicKey = if (publicKeyFile.exists()) {
            publicKeyFile.readText()
        } else {
            ""
        }

        emit(AdbKeysInfo(keysDir, privateKey, publicKey))
    }

    // ============ 密钥保存 ============

    suspend fun saveAdbKeys(privateKey: String, publicKey: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
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

                LogManager.d(LogTags.ADB_KEYS_VM, "ADB 密钥保存成功")
                LogManager.d(LogTags.ADB_KEYS_VM, "私钥文件: ${privateKeyFile.absolutePath}")
                LogManager.d(LogTags.ADB_KEYS_VM, "公钥文件: ${publicKeyFile.absolutePath}")

                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_KEYS_VM, "保存 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ============ 密钥导出 ============

    suspend fun exportAdbKeys(): Result<String> {
        return try {
            val keysDir = File(context.filesDir, "adb_keys").absolutePath
            Result.success(keysDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ 密钥导入 ============

    suspend fun importAdbKeys(privateKey: String, publicKey: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
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

    // ============ Factory ============

    companion object {
        fun provideFactory(
            context: Context,
            adbConnectionManager: AdbConnectionManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdbKeysViewModel(context, adbConnectionManager) as T
            }
        }
    }
}

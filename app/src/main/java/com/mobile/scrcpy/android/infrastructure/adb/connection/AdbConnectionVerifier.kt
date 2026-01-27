package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import dadb.Dadb

import com.mobile.scrcpy.android.core.i18n.AdbTexts
/**
 * ADB 连接验证器
 * 负责验证 ADB 连接是否可用
 */
internal object AdbConnectionVerifier {
    
    /**
     * 验证 ADB 连接是否可用
     */
    suspend fun verifyConnection(connection: AdbConnection): Boolean {
        return try {
            val result = connection.executeShell("echo 1", retryOnFailure = false)
            result.isSuccess
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * 验证 Dadb 实例是否可用
     */
    fun verifyDadb(dadb: Dadb): Result<String> {
        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_VERIFYING.get())
        return try {
            val testResponse = dadb.shell("echo 1")
            if (testResponse.exitCode != 0) {
                throw Exception(AdbTexts.ERROR_ADB_COMMAND_FAILED.get())
            }
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${AdbTexts.ADB_VERIFY_SUCCESS.get()}")
            Result.success("")
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_VERIFY_FAILED.get()}: ${e.message}")
            try {
                dadb.close()
            } catch (closeException: Exception) {
                LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CLOSE_DADB_ERROR.get()}: ${closeException.message}")
            }
            
            val errorMsg = when (e) {
                is java.net.ConnectException -> AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get()
                is java.io.EOFException -> AdbTexts.ERROR_ADB_HANDSHAKE_FAILED.get()
                else -> "${AdbTexts.ERROR_ADB_CONNECTION_UNAVAILABLE.get()}: ${e.message}"
            }
            Result.failure(Exception(errorMsg, e))
        }
    }
}

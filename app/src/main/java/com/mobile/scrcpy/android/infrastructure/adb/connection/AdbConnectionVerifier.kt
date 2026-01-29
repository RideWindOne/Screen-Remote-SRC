package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * ADB 连接验证器
 * 负责验证 ADB 连接是否可用
 */
internal object AdbConnectionVerifier {
    /**
     * 验证 ADB 连接是否可用
     */
    suspend fun verifyConnection(connection: AdbConnection): Boolean =
        try {
            val result = connection.executeShell("echo 1", retryOnFailure = false)
            result.isSuccess
        } catch (_: Exception) {
            false
        }

    /**
     * 验证 Dadb 实例是否可用
     * @param timeoutMs 超时时间（毫秒），默认 5 秒
     */
    suspend fun verifyDadb(
        dadb: Dadb,
        timeoutMs: Long = 5000,
    ): Result<String> {
        LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_VERIFYING.get())
        var errorMsg: String? = null

        try {
            val testResponse =
                withContext(Dispatchers.IO) {
                    val future =
                        java.util.concurrent.CompletableFuture.supplyAsync {
                            dadb.shell("echo 1")
                        }
                    future.get(timeoutMs, TimeUnit.MILLISECONDS)
                }

            if (testResponse.exitCode != 0) {
                LogManager.w(LogTags.ADB_CONNECTION, "exitCode=${testResponse.exitCode}")
                throw Exception(AdbTexts.ERROR_ADB_COMMAND_FAILED.get())
            }

            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_VERIFY_SUCCESS.get())
            return Result.success("")
        } catch (e: TimeoutException) {
            errorMsg = AdbTexts.ADB_VERIFY_TIMEOUT.get()
            return Result.failure(Exception(errorMsg, e))
        } catch (e: Exception) {
            errorMsg =
                when (e) {
                    is java.net.ConnectException -> AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get()
                    is java.io.EOFException -> AdbTexts.ERROR_ADB_HANDSHAKE_FAILED.get()
                    else -> "${AdbTexts.ERROR_ADB_CONNECTION_UNAVAILABLE.get()}: ${e.message}"
                }
            return Result.failure(Exception(errorMsg, e))
        } finally {
            try {
                dadb.close()
            } catch (closeException: Exception) {
                LogManager.w(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_CLOSE_DADB_ERROR.get()}: ${closeException.message}",
                )
            }
            if (errorMsg != null) {
                // 统一处理错误
                LogManager.e(LogTags.ADB_CONNECTION, errorMsg)
            }
        }
    }
}

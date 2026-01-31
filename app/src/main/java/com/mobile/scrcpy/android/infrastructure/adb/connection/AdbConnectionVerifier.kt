package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * ADB 连接验证器
 * 负责验证 ADB 连接是否可用，并异步更新设备序列号和推送会话事件
 */
internal object AdbConnectionVerifier {
    /**
     * 验证 Dadb 实例并获取设备序列号
     *
     * 功能：
     * 1. 验证 ADB 连接是否可用
     * 2. 获取设备序列号
     * 3. 推送 ADB 连接状态事件（AdbConnected / AdbDisconnected）
     * 4. 异步更新当前会话的序列号（如果序列号变化，清空编解码器字段）
     *
     * @param dadb Dadb 实例
     * @param deviceId 设备 ID（必填，用于日志和事件）
     * @param timeoutMs 超时时间（毫秒），默认 5 秒
     * @return Result<设备序列号>，如果无法获取则使用 deviceId
     */
    suspend fun verifyDadb(
        dadb: Dadb,
        deviceId: String,
        timeoutMs: Long = 5000,
    ): Result<String> {
        var errorMsg: String? = null
        CoroutineScope(Dispatchers.IO).launch {
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.AdbVerifying)
        }

        try {
            val serialResponse =
                withContext(Dispatchers.IO) { withTimeout(timeoutMs) { dadb.shell("getprop ro.serialno") } }

            if (serialResponse.exitCode != 0) {
                LogManager.w(LogTags.ADB_CONNECTION, "exitCode=${serialResponse.exitCode}")
                throw Exception(AdbTexts.ERROR_ADB_COMMAND_FAILED.get())
            }
            // 如果序列号为空，使用 deviceId 作为后备
            val finalSerial = serialResponse.output.trim().ifBlank { deviceId }

            CoroutineScope(Dispatchers.IO).launch {
                CurrentSession.currentOrNull?.handleEvent(SessionEvent.AdbConnected)
            }

            return Result.success(finalSerial)
        } catch (e: TimeoutCancellationException) {
            errorMsg = AdbTexts.ADB_VERIFY_TIMEOUT.get()
            // 推送 ADB 断开事件
            LogManager.d(LogTags.ADB_CONNECTION, errorMsg)
            return Result.failure(Exception(errorMsg, e))
        } catch (e: Exception) {
            errorMsg =
                when (e) {
                    is java.net.ConnectException -> AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get()
                    is java.io.EOFException -> AdbTexts.ERROR_ADB_HANDSHAKE_FAILED.get()
                    else -> "${AdbTexts.ERROR_ADB_CONNECTION_UNAVAILABLE.get()}: ${e.message}"
                }
            // 推送 ADB 断开事件
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.AdbDisconnected(errorMsg))
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
                LogManager.e(LogTags.ADB_CONNECTION, errorMsg)
            }
        }
    }
}

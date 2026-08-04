package com.screen.remote.android.infrastructure.adb.shell

import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.core.common.event.ShellCommandExecuted
import com.screen.remote.android.core.common.event.ShellCommandFailed
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val EXPAND_NOTIFICATIONS_COMMAND =
    "if command -v cmd >/dev/null 2>&1; then " +
        "cmd statusbar expand-notifications; " +
        "else service call statusbar 1; fi"

/**
 * ADB Shell 命令管理器
 *
 * 统一管理所有 Shell 命令执行，自动收集状态信息并推送到事件总线
 */
object AdbShellManager {
    /**
     * 执行 Shell 命令（带监控）
     */
    suspend fun execute(
        connection: AdbConnection,
        command: String,
        retryOnFailure: Boolean = true,
        reportToEventBus: Boolean = true,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val deviceId = connection.deviceInfo.deviceId
            val startTime = System.currentTimeMillis()

            try {
                // 执行命令
                val result = connection.executeShell(command, retryOnFailure)
                val duration = System.currentTimeMillis() - startTime

                // 上报到事件总线
                if (reportToEventBus) {
                    if (result.isSuccess) {
                        ScrcpyEventBus.pushEvent(
                            ShellCommandExecuted(
                                deviceId = deviceId,
                                command = command,
                                output = result.getOrNull() ?: "",
                                durationMs = duration,
                                success = true,
                            ),
                        )
                    } else {
                        ScrcpyEventBus.pushEvent(
                            ShellCommandFailed(
                                deviceId = deviceId,
                                command = command,
                                error = result.exceptionOrNull()?.message ?: "Unknown error",
                                durationMs = duration,
                            ),
                        )
                    }
                }

                result
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime

                // 上报异常
                if (reportToEventBus) {
                    ScrcpyEventBus.pushEvent(
                        ShellCommandFailed(
                            deviceId = deviceId,
                            command = command,
                            error = e.message ?: "Unknown error",
                            durationMs = duration,
                        ),
                    )
                }

                Result.failure(e)
            }
        }

    /**
     * 杀死进程
     */
    suspend fun killProcess(
        connection: AdbConnection,
        pattern: String,
    ): Result<String> =
        execute(
            connection,
            "pkill -f '$pattern' || killall -9 app_process",
            retryOnFailure = false,
        )

}

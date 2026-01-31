package com.mobile.scrcpy.android.infrastructure.adb.shell

import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
import com.mobile.scrcpy.android.core.common.event.ShellCommandExecuted
import com.mobile.scrcpy.android.core.common.event.ShellCommandFailed
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * 获取设备属性
     */
    suspend fun getProperty(
        connection: AdbConnection,
        property: String,
    ): Result<String> =
        execute(
            connection = connection,
            command = "getprop $property",
            retryOnFailure = false,
            reportToEventBus = false,
        )

    /**
     * 唤醒屏幕
     */
    suspend fun wakeUpScreen(connection: AdbConnection): Result<String> =
        execute(connection, "input keyevent KEYCODE_WAKEUP")

    /**
     * 展开通知栏
     */
    suspend fun expandNotifications(connection: AdbConnection): Result<String> =
        execute(connection, "cmd statusbar expand-notifications")

    /**
     * 设置剪贴板
     */
    suspend fun setClipboard(
        connection: AdbConnection,
        text: String,
    ): Result<String> =
        execute(
            connection,
            "service call clipboard 1 i32 0 s16 com.android.shell s16 \"$text\"",
        )

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

    /**
     * 设置文件权限
     */
    suspend fun chmod(
        connection: AdbConnection,
        mode: String,
        path: String,
    ): Result<String> =
        execute(
            connection,
            "chmod $mode $path",
            retryOnFailure = false,
            reportToEventBus = false,
        )

    /**
     * 心跳检测
     */
    suspend fun heartbeat(connection: AdbConnection): Result<String> =
        execute(
            connection = connection,
            command = "echo 1",
            retryOnFailure = false,
            reportToEventBus = false,
        )

    /**
     * 验证连接
     */
    suspend fun verifyConnection(connection: AdbConnection): Boolean {
        val result = heartbeat(connection)
        return result.isSuccess
    }
}

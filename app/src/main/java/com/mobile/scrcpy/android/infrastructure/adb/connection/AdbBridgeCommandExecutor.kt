package com.mobile.scrcpy.android.infrastructure.adb.connection

import kotlinx.coroutines.runBlocking

internal class AdbBridgeCommandExecutor(
    private val state: AdbBridgeState,
) {
    fun execute(args: Array<String>): AdbBridgeCommandResult {
        val connection = state.currentConnection ?: return AdbBridgeCommandResult(false, "ADB 未连接")

        return when {
            args.size >= 2 && args[0] == "shell" -> {
                executeShellCommand(connection, args.drop(1).joinToString(" "))
            }

            args.size >= 3 && args[0] == "push" -> {
                executePushCommand(connection, args[1], args[2])
            }

            args.size >= 3 && args[0] == "pull" -> {
                executePullCommand(connection, args[1], args[2])
            }

            args.size >= 3 && args[0] == "forward" -> {
                executeForwardCommand(connection, args[1], args[2])
            }

            args.size >= 2 && args[0] == "install" -> {
                executeInstallCommand(connection, args[1])
            }

            args.size >= 2 && args[0] == "uninstall" -> {
                executeUninstallCommand(connection, args[1])
            }

            else -> {
                AdbBridgeCommandResult(false, "不支持的 ADB 命令: ${args.joinToString(" ")}")
            }
        }
    }

    private fun executeShellCommand(
        connection: AdbConnection,
        command: String,
    ): AdbBridgeCommandResult =
        runBlocking {
            val result = connection.executeShell(command)
            result.toCommandResult(failureMessage = "执行失败")
        }

    private fun executePushCommand(
        connection: AdbConnection,
        local: String,
        remote: String,
    ): AdbBridgeCommandResult =
        runBlocking {
            val result = connection.pushFile(local, remote)
            result.toCommandResult(failureMessage = "推送失败", successOutput = "")
        }

    private fun executePullCommand(
        connection: AdbConnection,
        remote: String,
        local: String,
    ): AdbBridgeCommandResult =
        runBlocking {
            val result = connection.pullFile(remote, local)
            result.toCommandResult(failureMessage = "拉取失败", successOutput = "")
        }

    private fun executeForwardCommand(
        connection: AdbConnection,
        local: String,
        remote: String,
    ): AdbBridgeCommandResult {
        val localPort = local.substringAfter("tcp:").toIntOrNull()
        val remotePort = remote.substringAfter("tcp:").toIntOrNull()

        if (localPort == null || remotePort == null) {
            return AdbBridgeCommandResult(false, "无效的端口格式")
        }

        return runBlocking {
            val result = connection.setupPortForward(localPort, remotePort)
            result.toCommandResult(failureMessage = "端口转发失败", successOutput = "")
        }
    }

    private fun executeInstallCommand(
        connection: AdbConnection,
        apkPath: String,
    ): AdbBridgeCommandResult =
        runBlocking {
            val result = connection.installApk(apkPath)
            result.toCommandResult(failureMessage = "安装失败", successOutput = "")
        }

    private fun executeUninstallCommand(
        connection: AdbConnection,
        packageName: String,
    ): AdbBridgeCommandResult =
        runBlocking {
            val result = connection.uninstallPackage(packageName)
            result.toCommandResult(failureMessage = "卸载失败", successOutput = "")
        }
}

internal data class AdbBridgeCommandResult(
    val success: Boolean,
    val output: String,
)

private fun Result<String>.toCommandResult(failureMessage: String): AdbBridgeCommandResult =
    if (isSuccess) {
        AdbBridgeCommandResult(true, getOrNull() ?: "")
    } else {
        AdbBridgeCommandResult(false, exceptionOrNull()?.message ?: failureMessage)
    }

private fun Result<Boolean>.toCommandResult(
    failureMessage: String,
    successOutput: String,
): AdbBridgeCommandResult =
    if (isSuccess) {
        AdbBridgeCommandResult(true, successOutput)
    } else {
        AdbBridgeCommandResult(false, exceptionOrNull()?.message ?: failureMessage)
    }

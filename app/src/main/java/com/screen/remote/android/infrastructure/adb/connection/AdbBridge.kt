package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.manager.LogManager.dManagement

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB bridge facade exposed to native code.
 *
 * Native-facing APIs stay here; connection state, pseudo process lifecycle,
 * and command execution are delegated to dedicated collaborators.
 */
object AdbBridge {
    private val state = AdbBridgeState()
    private val processRegistry = AdbBridgeProcessRegistry()
    private val commandExecutor = AdbBridgeCommandExecutor(state)

    fun setConnection(connection: AdbConnection) {
        state.currentConnection = connection
    }

    fun getConnection(): AdbConnection? = state.currentConnection

    fun clearConnection() {
        state.currentConnection = null
        dManagement(LogTags.ADB_BRIDGE) { "清除当前连接" }
    }

    @JvmStatic
    fun executeAdbCommand(args: Array<String>): Int {
        val pid = processRegistry.createPid()

        dManagement(LogTags.ADB_BRIDGE) { "========== 执行 ADB 命令 ==========" }
        dManagement(LogTags.ADB_BRIDGE) { "PID: $pid" }
        dManagement(LogTags.ADB_BRIDGE) { "命令: adb ${args.joinToString(" ")}" }

        processRegistry.startProcess(pid) {
            try {
                Thread.currentThread().name = "ADB-$pid"
                val result = commandExecutor.execute(args)
                processRegistry.completeProcess(pid, result)

                dManagement(LogTags.ADB_BRIDGE) { "PID $pid 执行完成: success=${result.success}" }
                dManagement(LogTags.ADB_BRIDGE) { "输出: ${result.output}" }
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_BRIDGE, "PID $pid 执行失败: ${e.message}", e)
                processRegistry.failProcess(pid, e.message ?: "")
            }
        }

        return pid
    }

    @JvmStatic
    fun waitProcess(pid: Int): Int {
        dManagement(LogTags.ADB_BRIDGE) { "等待进程 $pid 完成..." }
        val success = processRegistry.waitProcess(pid)
        dManagement(LogTags.ADB_BRIDGE) { "进程 $pid 完成: success=$success" }
        return if (success) 0 else 1
    }

    @JvmStatic
    fun readProcessOutput(pid: Int): String {
        waitProcess(pid)
        val output = processRegistry.readOutput(pid)
        dManagement(LogTags.ADB_BRIDGE) { "读取进程 $pid 输出: ${output.length} 字节" }
        return output
    }

    @JvmStatic
    fun terminateProcess(pid: Int): Boolean {
        dManagement(LogTags.ADB_BRIDGE) { "终止进程 $pid" }
        return processRegistry.terminateProcess(pid)
    }

    @JvmStatic
    fun cleanupProcess(pid: Int) {
        processRegistry.cleanupProcess(pid)
        dManagement(LogTags.ADB_BRIDGE) { "清理进程 $pid 资源" }
    }
}

internal class AdbBridgeState {
    var currentConnection: AdbConnection? = null
}

internal class AdbBridgeProcessRegistry {
    private val pidGenerator = AtomicInteger(10000)
    private val processResults = ConcurrentHashMap<Int, String>()
    private val processStatus = ConcurrentHashMap<Int, Boolean>()
    private val processThreads = ConcurrentHashMap<Int, Thread>()

    fun createPid(): Int = pidGenerator.incrementAndGet()

    fun startProcess(
        pid: Int,
        work: () -> Unit,
    ) {
        val thread =
            Thread {
                try {
                    work()
                } finally {
                    processThreads.remove(pid)
                }
            }

        processThreads[pid] = thread
        thread.start()
    }

    fun completeProcess(
        pid: Int,
        result: AdbBridgeCommandResult,
    ) {
        processResults[pid] = result.output
        processStatus[pid] = result.success
    }

    fun failProcess(
        pid: Int,
        message: String,
    ) {
        processResults[pid] = message
        processStatus[pid] = false
    }

    fun waitProcess(pid: Int): Boolean {
        processThreads[pid]?.let { thread ->
            try {
                thread.join()
            } catch (e: InterruptedException) {
                LogManager.e(LogTags.ADB_BRIDGE, "等待进程 $pid 被中断", e)
            }
        }
        return processStatus[pid] ?: false
    }

    fun readOutput(pid: Int): String = processResults[pid] ?: ""

    fun terminateProcess(pid: Int): Boolean {
        val thread = processThreads.remove(pid)
        if (thread != null && thread.isAlive) {
            thread.interrupt()
            return true
        }
        return false
    }

    fun cleanupProcess(pid: Int) {
        processResults.remove(pid)
        processStatus.remove(pid)
        processThreads.remove(pid)
    }
}

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

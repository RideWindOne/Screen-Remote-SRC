package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.ManagementDebugLog

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
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "清除当前连接" }
    }

    @JvmStatic
    fun executeAdbCommand(args: Array<String>): Int {
        val pid = processRegistry.createPid()

        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "========== 执行 ADB 命令 ==========" }
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "PID: $pid" }
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "命令: adb ${args.joinToString(" ")}" }

        processRegistry.startProcess(pid) {
            try {
                Thread.currentThread().name = "ADB-$pid"
                val result = commandExecutor.execute(args)
                processRegistry.completeProcess(pid, result)

                ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "PID $pid 执行完成: success=${result.success}" }
                ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "输出: ${result.output}" }
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_BRIDGE, "PID $pid 执行失败: ${e.message}", e)
                processRegistry.failProcess(pid, e.message ?: "")
            }
        }

        return pid
    }

    @JvmStatic
    fun waitProcess(pid: Int): Int {
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "等待进程 $pid 完成..." }
        val success = processRegistry.waitProcess(pid)
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "进程 $pid 完成: success=$success" }
        return if (success) 0 else 1
    }

    @JvmStatic
    fun readProcessOutput(pid: Int): String {
        waitProcess(pid)
        val output = processRegistry.readOutput(pid)
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "读取进程 $pid 输出: ${output.length} 字节" }
        return output
    }

    @JvmStatic
    fun terminateProcess(pid: Int): Boolean {
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "终止进程 $pid" }
        return processRegistry.terminateProcess(pid)
    }

    @JvmStatic
    fun cleanupProcess(pid: Int) {
        processRegistry.cleanupProcess(pid)
        ManagementDebugLog.d(LogTags.ADB_BRIDGE) { "清理进程 $pid 资源" }
    }
}

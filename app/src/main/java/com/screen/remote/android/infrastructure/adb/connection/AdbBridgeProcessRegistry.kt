package com.screen.remote.android.infrastructure.adb.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

package com.screen.remote.android.infrastructure.scrcpy.connection

import com.screen.remote.android.core.common.manager.LogManager.dShell

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import dadb.AdbShellStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shell 流类型别名
 */
typealias ShellStream = AdbShellStream

/**
 * Shell 输出监控器
 * 负责监控 scrcpy server 的 shell 输出，检测错误
 */
class ConnectionShellMonitor(
    private val sessionContext: SessionContext,
) {
    private companion object {
        private const val SERVER_DEVICE_LOG_SETTLE_MS = 350L
        private const val SERVER_ENCODER_LOG_SETTLE_MS = 100L
    }

    private var shellStream: ShellStream? = null
    private var readerScope: CoroutineScope? = null
    private var readerJob: Job? = null
    private val recentShellLines = ArrayDeque<String>()
    private val stateLock = Any()
    private var startupReadyDeadlineAtMs: Long? = null
    private var startupFailure: ServerIssue? = null
    private var runtimeMonitoringEnabled = false
    private var intentionalStop = false
    private var readerFinished = false

    /**
     * 设置 Shell 流并监听 scrcpy-server 启动状态
     */
    fun setShellStream(stream: ShellStream) {
        stopReader(closeStream = false)
        shellStream = stream
        synchronized(recentShellLines) {
            recentShellLines.clear()
        }
        synchronized(stateLock) {
            startupReadyDeadlineAtMs = null
            startupFailure = null
            runtimeMonitoringEnabled = false
            intentionalStop = false
            readerFinished = false
        }
        startReader(stream)
        dShell(LogTags.SCRCPY_SERVER) { "shell stream attached" }
    }

    /**
     * 等待 scrcpy-server 启动完成
     * 通过监听 shell 输出判断 server 是否准备就绪
     * @param timeoutMs 超时时间（毫秒）
     * @return true 表示启动成功，false 表示超时
     */
    suspend fun waitForServerReady(timeoutMs: Long = 10000): Boolean =
        withContext(Dispatchers.IO) {
            val currentStream = shellStream ?: return@withContext false
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val now = System.currentTimeMillis()
                val (failure, deadline, finished, stopRequested) =
                    synchronized(stateLock) {
                        Quadruple(
                            startupFailure,
                            startupReadyDeadlineAtMs,
                            readerFinished,
                            intentionalStop,
                        )
                    }

                if (failure != null) {
                    return@withContext false
                }

                if (deadline != null && now >= deadline) {
                    synchronized(stateLock) {
                        if (!runtimeMonitoringEnabled) {
                            runtimeMonitoringEnabled = true
                        }
                    }
                    dShell(LogTags.SCRCPY_SERVER) {
                        "startup ready confirmed for shell stream=${currentStream.hashCode()}"
                    }
                    return@withContext true
                }

                if (finished || stopRequested) {
                    dumpRecentShellLines("startup-finished-without-ready")
                    return@withContext false
                }

                delay(10)
            }

            LogManager.w(LogTags.SCRCPY_SERVER, "等待 scrcpy-server 启动超时")
            dumpRecentShellLines("startup-timeout")
            false
        }

    /**
     * 开始监控 Shell 输出
     */
    fun startMonitor() {
        if (shellStream == null) {
            return
        }
        synchronized(stateLock) {
            runtimeMonitoringEnabled = true
        }
        dShell(LogTags.SCRCPY_SERVER) { "shell runtime monitor started" }
    }

    fun hasStartupFailed(): Boolean =
        synchronized(stateLock) {
            startupFailure != null || readerFinished || intentionalStop
        }

    /**
     * 停止监控
     */
    fun stopMonitor() {
        dShell(LogTags.SCRCPY_SERVER) { "shell runtime monitor stopped" }
        synchronized(stateLock) {
            intentionalStop = true
            runtimeMonitoringEnabled = false
        }
        cancelReaderScope()
    }

    /**
     * 关闭 Shell 流
     */
    fun closeShellStream() {
        try {
            synchronized(stateLock) {
                intentionalStop = true
            }
            dShell(LogTags.SCRCPY_SERVER) { "closing shell stream" }
            shellStream?.close()
            shellStream = null
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVER, "Failed to close shell stream: ${e.message}")
        } finally {
            cancelReaderScope()
        }
    }

    fun dumpDiagnostics(reason: String) {
        dumpRecentShellLines(reason)
    }

    private fun appendShellLine(
        source: String,
        line: String,
    ) {
        synchronized(recentShellLines) {
            if (recentShellLines.size >= 10) {
                recentShellLines.removeFirst()
            }
            recentShellLines.addLast("$source $line")
        }
    }

    private fun dumpRecentShellLines(reason: String) {
        val snapshot =
            synchronized(recentShellLines) {
                recentShellLines.toList()
            }

        if (snapshot.isEmpty()) {
            LogManager.d(LogTags.SCRCPY_SERVER, "DIAG server-tail reason=$reason empty=true")
            return
        }

        LogManager.d(LogTags.SCRCPY_SERVER, "DIAG server-tail reason=$reason count=${snapshot.size}")
        snapshot.forEachIndexed { index, line ->
            LogManager.d(LogTags.SCRCPY_SERVER, "DIAG server-tail[$index] $line")
        }
    }

    private fun logShellPacket(
        stage: String,
        type: String,
        payload: String,
    ) {
        dShell(LogTags.SCRCPY_SERVER) {
            val normalized = payload.replace('\n', ' ').replace('\r', ' ').take(240)
            "shell packet stage=$stage type=$type length=${payload.length} payload=${normalized.ifBlank { "<empty>" }}"
        }
    }

    private fun startReader(stream: ShellStream) {
        readerScope = CoroutineScope(Dispatchers.IO)
        readerJob =
            readerScope?.launch {
                try {
                    while (isActive) {
                        when (val packet = stream.read()) {
                            is dadb.AdbShellPacket.StdOut -> handleStdOut(packet)
                            is dadb.AdbShellPacket.StdError -> handleStdError(packet)
                            is dadb.AdbShellPacket.Exit -> {
                                handleExit(packet)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    handleReaderException(e)
                } finally {
                    synchronized(stateLock) {
                        readerFinished = true
                    }
                    dShell(LogTags.SCRCPY_SERVER) { "shell reader finished" }
                }
            }
    }

    private fun stopReader(closeStream: Boolean) {
        synchronized(stateLock) {
            intentionalStop = true
        }
        if (closeStream) {
            runCatching { shellStream?.close() }
        }
        cancelReaderScope()
    }

    private fun cancelReaderScope() {
        readerJob?.cancel()
        readerJob = null
        readerScope?.cancel()
        readerScope = null
    }

    private fun handleStdOut(packet: dadb.AdbShellPacket.StdOut) {
        val line = String(packet.payload).trim()
        logShellPacket(stage = currentStageLabel(), type = "stdout", payload = line)
        if (line.isEmpty()) {
            return
        }

        appendShellLine("OUT", line)
        LogManager.d(LogTags.SCRCPY_SERVER, line)

        if (isRuntimeMonitoringEnabled()) {
            if (line.contains("error", ignoreCase = true) ||
                line.contains("exception", ignoreCase = true) ||
                line.contains("failed", ignoreCase = true)
            ) {
                SessionIssueTracker.record("server.stdout", line)
                sessionContext.emit(
                    SessionEvent.ServerFailed(
                        ServerIssue(
                            kind = ServerIssueKind.RuntimeStdOut,
                            detail = line,
                        ),
                    ),
                )
            }
            return
        }

        if (line.contains("Device:", ignoreCase = true)) {
            scheduleStartupReady(SERVER_DEVICE_LOG_SETTLE_MS)
        } else if (
            line.contains("Encoder:", ignoreCase = true) ||
            line.contains("video encoder", ignoreCase = true) ||
            line.contains("audio encoder", ignoreCase = true)
        ) {
            scheduleStartupReady(SERVER_ENCODER_LOG_SETTLE_MS)
        }
    }

    private fun handleStdError(packet: dadb.AdbShellPacket.StdError) {
        val line = String(packet.payload).trim()
        logShellPacket(stage = currentStageLabel(), type = "stderr", payload = line)
        if (line.isEmpty()) {
            return
        }

        appendShellLine("ERR", line)
        LogManager.e(LogTags.SCRCPY_SERVER, line)
        SessionIssueTracker.record("server.stderr", line)

        if (isRuntimeMonitoringEnabled()) {
            sessionContext.emit(
                SessionEvent.ServerFailed(
                    ServerIssue(
                        kind = ServerIssueKind.RuntimeStdErr,
                        detail = line,
                    ),
                ),
            )
            return
        }

        if (line.contains("ERROR", ignoreCase = true) || line.contains("FATAL", ignoreCase = true)) {
            recordStartupFailure(
                ServerIssue(
                    kind = ServerIssueKind.StartupStdErr,
                    detail = line,
                ),
            )
        }
    }

    private fun handleExit(packet: dadb.AdbShellPacket.Exit) {
        val exitCode = packet.payload.getOrNull(0)?.toInt() ?: -1
        dShell(LogTags.SCRCPY_SERVER) { "${currentStageLabel()} exit packet: exitCode=$exitCode" }
        dumpRecentShellLines("${currentStageLabel()}-exit")
        SessionIssueTracker.record("server.exit", "Server process exited: $exitCode")

        if (isRuntimeMonitoringEnabled()) {
            sessionContext.emit(
                SessionEvent.ServerFailed(
                    ServerIssue(
                        kind = ServerIssueKind.ProcessExited,
                        detail = "Server 进程退出",
                        exitCode = exitCode,
                    ),
                ),
            )
            return
        }

        recordStartupFailure(
            ServerIssue(
                kind = ServerIssueKind.ProcessExited,
                detail = "进程意外退出",
                exitCode = exitCode,
            ),
        )
    }

    private fun handleReaderException(error: Exception) {
        val stopRequested =
            synchronized(stateLock) {
                intentionalStop
            }
        if (stopRequested) {
            dShell(LogTags.SCRCPY_SERVER) {
                "shell reader stopped intentionally: ${error.javaClass.simpleName}: ${error.message ?: "<no-message>"}"
            }
            return
        }

        val errorMsg =
            when (error) {
                is java.io.EOFException -> "Server 进程意外终止"
                else -> error.message ?: error.javaClass.simpleName
            }

        if (isRuntimeMonitoringEnabled()) {
            if (error !is java.io.EOFException) {
                LogManager.e(LogTags.SCRCPY_SERVER, "Shell 监控异常 -> $errorMsg", error)
            }
            dumpRecentShellLines("monitor-exception")
            SessionIssueTracker.record("server.monitor", errorMsg)
            sessionContext.emit(
                SessionEvent.ServerFailed(
                    ServerIssue(
                        kind = ServerIssueKind.MonitorException,
                        detail = "Shell 监控异常 -> $errorMsg",
                    ),
                ),
            )
            return
        }

        dumpRecentShellLines("startup-exception")
        if (error !is java.io.EOFException) {
            LogManager.e(LogTags.SCRCPY_SERVER, "等待 scrcpy-server 启动时出错: ${error.message}", error)
        }
        recordStartupFailure(
            ServerIssue(
                kind = ServerIssueKind.MonitorException,
                detail = "Shell 监控异常 -> $errorMsg",
            ),
        )
    }

    private fun scheduleStartupReady(delayMs: Long) {
        val candidate = System.currentTimeMillis() + delayMs
        synchronized(stateLock) {
            if (runtimeMonitoringEnabled || startupFailure != null) {
                return
            }
            val current = startupReadyDeadlineAtMs
            startupReadyDeadlineAtMs =
                when {
                    current == null -> candidate
                    candidate < current -> candidate
                    else -> current
                }
        }
    }

    private fun recordStartupFailure(issue: ServerIssue) {
        val shouldEmit =
            synchronized(stateLock) {
                if (startupFailure != null || intentionalStop) {
                    false
                } else {
                    startupFailure = issue
                    true
                }
            }

        if (!shouldEmit) {
            return
        }

        LogManager.e(LogTags.SCRCPY_SERVER, "✗ scrcpy-server 启动失败")
        sessionContext.emit(SessionEvent.ServerFailed(issue))
    }

    private fun isRuntimeMonitoringEnabled(): Boolean =
        synchronized(stateLock) {
            runtimeMonitoringEnabled
        }

    private fun currentStageLabel(): String = if (isRuntimeMonitoringEnabled()) "runtime" else "startup"

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )
}

package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
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
class ConnectionShellMonitor {
    private var shellStream: ShellStream? = null
    private var monitorScope: CoroutineScope? = null
    private var monitorJob: Job? = null

    /**
     * 设置 Shell 流并监听 scrcpy-server 启动状态
     */
    fun setShellStream(stream: ShellStream) {
        shellStream = stream
    }

    /**
     * 等待 scrcpy-server 启动完成
     * 通过监听 shell 输出判断 server 是否准备就绪
     * @param timeoutMs 超时时间（毫秒）
     * @return true 表示启动成功，false 表示超时
     */
    suspend fun waitForServerReady(timeoutMs: Long = 10000): Boolean =
        withContext(Dispatchers.IO) {
            val stream = shellStream ?: return@withContext false
            val startTime = System.currentTimeMillis()

            try {
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    when (val packet = stream.read()) {
                        is dadb.AdbShellPacket.StdOut -> {
                            val line = String(packet.payload).trim()
                            if (line.isNotEmpty()) {
                                LogManager.d(LogTags.SCRCPY_SERVER, line)

                                // 检测 scrcpy-server 启动成功的标志
                                if (line.contains("INFO:", ignoreCase = true) ||
                                    line.contains("Device:", ignoreCase = true) ||
                                    line.contains("Encoder:", ignoreCase = true)
                                ) {
                                    return@withContext true
                                }
                            }
                        }

                        is dadb.AdbShellPacket.StdError -> {
                            val line = String(packet.payload).trim()
                            if (line.isNotEmpty()) {
                                LogManager.e(LogTags.SCRCPY_SERVER, line)

                                // 检测致命错误 - 发送事件
                                if (line.contains("ERROR", ignoreCase = true) ||
                                    line.contains("FATAL", ignoreCase = true)
                                ) {
                                    LogManager.e(LogTags.SCRCPY_SERVER, "✗ scrcpy-server 启动失败")
                                    CurrentSession.currentOrNull?.handleEvent(
                                        SessionEvent.ServerFailed(line),
                                    )
                                    return@withContext false
                                }
                            }
                        }

                        is dadb.AdbShellPacket.Exit -> {
                            LogManager.e(LogTags.SCRCPY_SERVER, "✗ scrcpy-server 进程意外退出")
                            CurrentSession.currentOrNull?.handleEvent(
                                SessionEvent.ServerFailed("进程意外退出"),
                            )
                            return@withContext false
                        }
                    }

                    // 短暂延迟，避免 CPU 占用过高
                    delay(10)
                }

                LogManager.w(LogTags.SCRCPY_SERVER, "等待 scrcpy-server 启动超时")
                return@withContext false
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_SERVER, "等待 scrcpy-server 启动时出错: ${e.message}")
                return@withContext false
            }
        }

    /**
     * 开始监控 Shell 输出
     */
    fun startMonitor() {
        val stream = shellStream ?: return

        // 创建新的协程作用域
        monitorScope = CoroutineScope(Dispatchers.IO)

        monitorJob =
            monitorScope?.launch {
                try {
                    while (isActive) {
                        when (val packet = stream.read()) {
                            is dadb.AdbShellPacket.StdOut -> {
                                val line = String(packet.payload).trim()
                                if (line.isNotEmpty()) {
                                    LogManager.d(LogTags.SCRCPY_SERVER, line)

                                    // 检测错误信息 - 发送事件
                                    if (line.contains("error", ignoreCase = true) ||
                                        line.contains("exception", ignoreCase = true) ||
                                        line.contains("failed", ignoreCase = true)
                                    ) {
                                        CurrentSession.currentOrNull?.handleEvent(
                                            SessionEvent.ServerFailed(line),
                                        )
                                    }
                                }
                            }

                            is dadb.AdbShellPacket.StdError -> {
                                val line = String(packet.payload).trim()
                                if (line.isNotEmpty()) {
                                    LogManager.e(LogTags.SCRCPY_SERVER, line)
                                    CurrentSession.currentOrNull?.handleEvent(
                                        SessionEvent.ServerFailed(line),
                                    )
                                }
                            }

                            is dadb.AdbShellPacket.Exit -> {
                                val exitCode = packet.payload.getOrNull(0)?.toInt() ?: -1
                                CurrentSession.currentOrNull?.handleEvent(
                                    SessionEvent.ServerFailed("Server 进程退出: $exitCode"),
                                )
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        val errorMsg =
                            when (e) {
                                is java.io.EOFException -> "Server 进程意外终止"
                                else -> e.message ?: e.javaClass.simpleName
                            }
                        // EOFException 是正常的进程终止，不打印堆栈
                        if (e !is java.io.EOFException) {
                            LogManager.e(LogTags.SCRCPY_SERVER, "Shell 监控异常 -> $errorMsg", e)
                        }
                        CurrentSession.currentOrNull?.handleEvent(
                            SessionEvent.ServerFailed("Shell 监控异常 -> $errorMsg"),
                        )
                    }
                }
            }
    }

    /**
     * 停止监控
     */
    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        monitorScope?.cancel()
        monitorScope = null
    }

    /**
     * 关闭 Shell 流
     */
    fun closeShellStream() {
        try {
            shellStream?.close()
            shellStream = null
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_SERVER, "Failed to close shell stream: ${e.message}")
        }
    }
}

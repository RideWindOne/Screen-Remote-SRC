package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import dadb.AdbShellStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

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
     * 设置 Shell 流
     */
    fun setShellStream(stream: ShellStream) {
        shellStream = stream
    }
    
    /**
     * 开始监控 Shell 输出
     */
    fun startMonitor(onError: (String) -> Unit) {
        val stream = shellStream ?: return
        
        // 创建新的协程作用域
        monitorScope = CoroutineScope(Dispatchers.IO)
        
        monitorJob = monitorScope?.launch {
            try {
                while (isActive) {
                    val packet = stream.read()
                    
                    when (packet) {
                        is dadb.AdbShellPacket.StdOut -> {
                            val line = String(packet.payload).trim()
                            if (line.isNotEmpty()) {
                                LogManager.d(LogTags.SCRCPY_SERVER, "Server: $line")
                                
                                // 检测错误信息
                                if (line.contains("error", ignoreCase = true) ||
                                    line.contains("exception", ignoreCase = true) ||
                                    line.contains("failed", ignoreCase = true)) {
                                    onError(line)
                                }
                            }
                        }
                        is dadb.AdbShellPacket.StdError -> {
                            val line = String(packet.payload).trim()
                            if (line.isNotEmpty()) {
                                LogManager.e(LogTags.SCRCPY_SERVER, "Server Error: $line")
                                onError(line)
                            }
                        }
                        is dadb.AdbShellPacket.Exit -> {
                            LogManager.d(LogTags.SCRCPY_SERVER, "Server shell exited")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    LogManager.e(LogTags.SCRCPY_SERVER, "Shell monitor error: ${e.message}")
                    onError("Shell monitor error: ${e.message}")
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

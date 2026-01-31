package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Socket

/**
 * 连接健康监控器
 * 主动检测 Socket 连接状态，及时发现断连
 */
class ConnectionHealthMonitor {
    private var monitorJob: Job? = null
    private var onConnectionLost: (() -> Unit)? = null

    /**
     * 开始监控
     */
    fun startMonitoring(
        videoSocket: Socket?,
        audioSocket: Socket?,
        controlSocket: Socket?,
        onConnectionLost: () -> Unit,
    ) {
        stopMonitoring()

        this.onConnectionLost = onConnectionLost

        monitorJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    try {
                        // 检查 Socket 状态
                        val videoAlive = videoSocket?.isSocketAlive() ?: false
                        val audioAlive = audioSocket?.isSocketAlive() ?: true // 音频可选
                        val controlAlive = controlSocket?.isSocketAlive() ?: false

                        if (!videoAlive || !controlAlive || !audioAlive) {
                            LogManager.w(
                                LogTags.SDL_HM,
                                "Socket 健康检查失败: video=$videoAlive, audio=$audioAlive, control=$controlAlive",
                            )
                            onConnectionLost()
                            break
                        }

                        // 每隔一段时间检查一次
                        delay(ScrcpyConstants.HEALTH_CHECK_INTERVAL_MS)
                    } catch (e: Exception) {
                        LogManager.e(LogTags.SDL_HM, "健康检查异常: ${e.message}")
                        onConnectionLost()
                        break
                    }
                }
            }
    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        onConnectionLost = null
    }

    /**
     * 检查 Socket 是否存活
     */
    private fun Socket.isSocketAlive(): Boolean {
        return try {
            // 检查基本状态
            if (isClosed || !isConnected) {
                return false
            }

            // 尝试启用 TCP keepalive（如果支持）
            keepAlive = true

            // 检查输出流是否可用（通过尝试获取）
            outputStream ?: return false

            true
        } catch (e: Exception) {
            false
        }
    }
}

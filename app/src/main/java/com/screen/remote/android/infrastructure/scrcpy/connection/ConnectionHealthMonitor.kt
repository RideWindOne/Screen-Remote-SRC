package com.screen.remote.android.infrastructure.scrcpy.connection

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.manager.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    private companion object {
        private const val SOCKET_FAILURE_STRIKES = 2
        private const val ALLOW_TRANSIENT_ERROR_INTERVAL_MS = 150L
    }

    private var monitorJob: Job? = null
    private var onConnectionLost: (() -> Unit)? = null
    private var consecutiveFailureCount = 0
    private var lastNotifyAtMs = 0L

    /**
     * 开始监控
     */
    fun startMonitoring(
        videoSocket: Socket?,
        audioSocket: Socket?,
        controlSocket: Socket?,
        onConnectionLostCallback: () -> Unit,
    ) {
        stopMonitoring()

        this.onConnectionLost = onConnectionLostCallback

        monitorJob =
            CoroutineScope(Dispatchers.IO).launch {
                lastNotifyAtMs = 0L
                while (isActive) {
                    try {
                        // 检查 Socket 状态
                        val videoAlive = videoSocket?.isSocketAlive() ?: false
                        val audioAlive = audioSocket?.isSocketAlive() ?: true // 音频可选
                        val controlAlive = controlSocket?.isSocketAlive() ?: false

                        if (!videoAlive || !controlAlive || !audioAlive) {
                            consecutiveFailureCount++
                            LogManager.w(
                                LogTags.SDL_HM,
                                "Socket health check failed ($consecutiveFailureCount/$SOCKET_FAILURE_STRIKES): video=$videoAlive, audio=$audioAlive, control=$controlAlive",
                            )

                            if (consecutiveFailureCount < SOCKET_FAILURE_STRIKES) {
                                delay(ALLOW_TRANSIENT_ERROR_INTERVAL_MS)
                                continue
                            }

                            notifyConnectionLost()
                            break
                        }

                        consecutiveFailureCount = 0
                        // 每隔一段时间检查一次
                        delay(ScrcpyConstants.HEALTH_CHECK_INTERVAL_MS)
                    } catch (_: CancellationException) {
                        break
                    } catch (e: Exception) {
                        LogManager.e(LogTags.SDL_HM, "Health check exception: ${e.message}")
                        if (!isActive) {
                            break
                        }
                        val now = System.currentTimeMillis()
                        val sinceLastNotify = now - lastNotifyAtMs
                        if (sinceLastNotify < ALLOW_TRANSIENT_ERROR_INTERVAL_MS) {
                            delay(ALLOW_TRANSIENT_ERROR_INTERVAL_MS - sinceLastNotify)
                        } else {
                            notifyConnectionLost()
                            break
                        }
                    }
                }
            }
    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        onConnectionLost = null
        consecutiveFailureCount = 0
        lastNotifyAtMs = 0L
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun notifyConnectionLost() {
        lastNotifyAtMs = System.currentTimeMillis()
        consecutiveFailureCount = 0
        onConnectionLost?.invoke()
    }

    /**
     * 检查 Socket 是否存活
     */
    private fun Socket.isSocketAlive(): Boolean {
        return try {
            // 检查基本状态
            if (isClosed || !isConnected || isInputShutdown || isOutputShutdown) {
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

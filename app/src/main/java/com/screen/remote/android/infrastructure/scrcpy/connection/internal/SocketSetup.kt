package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import java.util.Random

/**
 * Socket 设置逻辑
 *
 * 职责：
 * - Socket 连接建立
 * - SCID 生成
 * - 端口分配（预留）
 */

/**
 * 连接媒体与控制通道。
 */
internal suspend fun ConnectionLifecycle.connectSockets(
    options: ScrcpyOptions,
    connection: AdbConnection,
    socketName: String,
    localPort: Int,
    tunnelMode: ScrcpyTunnelMode,
) {
    socketManager.connectSockets(
        connection = connection,
        socketName = socketName,
        localPort = localPort,
        enableAudio = options.config.enableAudio,
        tunnelMode = tunnelMode,
        shouldAbortDirectProbe = shellMonitor::hasStartupFailed,
    )
}

/**
 * 生成 SCID
 */
internal fun generateScid(): Int {
    val random = Random()
    return random.nextInt(0x7FFFFFFF)
}

/**
 * 查找可用端口
 * 通过创建临时 ServerSocket 让系统自动分配可用端口
 */
internal suspend fun findAvailablePort(): Int =
    try {
        java.net.ServerSocket(0).use { socket ->
            socket.localPort
        }
    } catch (e: Exception) {
        // 如果失败，返回默认端口范围内的随机端口
        27183 + Random().nextInt(1000)
    }

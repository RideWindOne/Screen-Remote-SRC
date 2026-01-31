package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal

import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
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
 * 连接 Socket
 */
internal suspend fun ConnectionLifecycle.connectSockets(options: ScrcpyOptions) {
    // 推送 Socket 连接中事件
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.SocketConnecting)

    socketManager.connectSockets(options.enableAudio, options.keyFrameInterval)
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
internal suspend fun findAvailablePort(): Int {
    return try {
        java.net.ServerSocket(0).use { socket ->
            socket.localPort
        }
    } catch (e: Exception) {
        // 如果失败，返回默认端口范围内的随机端口
        27183 + Random().nextInt(1000)
    }
}

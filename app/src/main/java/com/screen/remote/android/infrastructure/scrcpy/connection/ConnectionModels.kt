package com.screen.remote.android.infrastructure.scrcpy.connection

import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection

/**
 * 一次实际 scrcpy 建链所拥有的资源快照。
 *
 * 配置可能在会话运行期间更新，清理逻辑不能重新读取“当前配置”来猜测当初
 * 建立了哪些资源。这里记录本次连接真正采用的设备、端口、SCID 和隧道模式，
 * 创建、重连和销毁都以同一份快照为准。
 */
internal data class ActiveScrcpyConnection(
    val sessionId: String,
    val adbConnection: AdbConnection,
    val localPort: Int,
    val scid: Int,
    val socketName: String,
    val tunnelMode: ScrcpyTunnelMode,
) {
    val deviceId: String = adbConnection.deviceId
}

internal data class PreparedAdbConnection(
    val connection: AdbConnection,
    val localPort: Int,
)

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()

    object Connecting : ConnectionState()

    object Connected : ConnectionState()

    object Disconnecting : ConnectionState()

    object Reconnecting : ConnectionState()

    data class Error(
        val message: String,
    ) : ConnectionState()
}

/**
 * 触摸动作（对应 Android MotionEvent）
 */
object TouchAction {
    const val ACTION_DOWN = 0 // 第一个手指按下
    const val ACTION_UP = 1 // 最后一个手指抬起
    const val ACTION_MOVE = 2 // 手指移动
}

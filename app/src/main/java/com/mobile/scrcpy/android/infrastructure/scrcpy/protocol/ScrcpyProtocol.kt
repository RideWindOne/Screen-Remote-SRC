package com.mobile.scrcpy.android.infrastructure.scrcpy.protocol

import com.mobile.scrcpy.android.core.common.AppConstants
import dadb.AdbShellPacket
import java.io.IOException

/**
 * Scrcpy 协议常量和命令构建工具
 */
object ScrcpyProtocol {
    // PTS 标志位常量（与 scrcpy 服务端一致）
    const val PACKET_FLAG_SESSION = 1L shl 63
    const val PACKET_FLAG_CONFIG = 1L shl 62
    const val PACKET_FLAG_KEY_FRAME = 1L shl 61
    const val PACKET_PTS_MASK = PACKET_FLAG_KEY_FRAME - 1

    // 控制消息类型
    const val MSG_TYPE_INJECT_KEYCODE = 0
    const val MSG_TYPE_INJECT_TEXT = 1
    const val MSG_TYPE_INJECT_TOUCH_EVENT = 2
    const val MSG_TYPE_SET_DISPLAY_POWER = 10

    /**
     * 构建 scrcpy-server 基础命令
     * @param params 参数列表（key=value 格式）
     * @param serverPath 自定义 server 路径（默认使用标准路径）
     */
    fun buildScrcpyServerCommand(
        vararg params: String,
        serverPath: String = AppConstants.SCRCPY_SERVER_PATH,
    ): String {
        val paramsStr = if (params.isNotEmpty()) " ${params.joinToString(" ")}" else ""
        return "CLASSPATH=$serverPath app_process / com.genymobile.scrcpy.Server " +
            "${AppConstants.SCRCPY_VERSION}$paramsStr"
    }
}

data class VideoFrameInfo(
    val pts: Long,
    val isConfig: Boolean,
    val isKeyFrame: Boolean,
)

/**
 * 视频流接口，用于统一 AdbShellStream 和 ScrcpySocketStream
 */
interface VideoStream : AutoCloseable {
    @Throws(IOException::class)
    fun read(): AdbShellPacket

    fun currentFrameInfo(): VideoFrameInfo? = null
}

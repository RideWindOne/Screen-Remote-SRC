package com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy

import com.mobile.scrcpy.android.core.common.AppConstants
import dadb.AdbShellPacket
import java.io.IOException

/**
 * Scrcpy 协议常量和工具函数
 */
object ScrcpyProtocol {
    // PTS 标志位常量（与 scrcpy 服务端一致）
    const val PACKET_FLAG_CONFIG = 1L shl 63
    const val PACKET_FLAG_KEY_FRAME = 1L shl 62
    const val PACKET_PTS_MASK = PACKET_FLAG_KEY_FRAME - 1

    // 控制消息类型
    const val MSG_TYPE_INJECT_KEYCODE = 0
    const val MSG_TYPE_INJECT_TEXT = 1
    const val MSG_TYPE_INJECT_TOUCH_EVENT = 2

    /**
     * 构建 scrcpy-server 基础命令
     * @param params 参数列表（key=value 格式）
     */
    fun buildScrcpyServerCommand(vararg params: String): String {
        val paramsStr = if (params.isNotEmpty()) " ${params.joinToString(" ")}" else ""
        return "CLASSPATH=${AppConstants.SCRCPY_SERVER_PATH} app_process / com.genymobile.scrcpy.Server ${AppConstants.SCRCPY_VERSION}$paramsStr"
    }

    /**
     * 将 int 值写入字节数组（大端序）
     */
    fun writeInt(
        buffer: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    /**
     * 将 long 值写入字节数组（大端序）
     */
    fun writeLong(
        buffer: ByteArray,
        offset: Int,
        value: Long,
    ) {
        buffer[offset] = ((value shr 56) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 48) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 40) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 32) and 0xFF).toByte()
        buffer[offset + 4] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 5] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 6] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 7] = (value and 0xFF).toByte()
    }

    /**
     * 将 short 值写入字节数组（大端序）
     */
    fun writeShort(
        buffer: ByteArray,
        offset: Int,
        value: Int,
    ) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    /**
     * 从字节数组读取 int 值（大端序）
     */
    fun bytesToInt(
        buffer: ByteArray,
        offset: Int,
    ): Int =
        ((buffer[offset].toInt() and 0xFF) shl 24) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
            (buffer[offset + 3].toInt() and 0xFF)
}

/**
 * 视频流接口，用于统一 AdbShellStream 和 ScrcpySocketStream
 */
interface VideoStream : AutoCloseable {
    @Throws(IOException::class)
    fun read(): AdbShellPacket
}

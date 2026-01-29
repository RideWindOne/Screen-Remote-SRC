/*
 * ADB 协议实现
 *
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 * - adblib: https://github.com/tananaev/adblib
 */

package com.mobile.scrcpy.android.infrastructure.adb.usb

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * ADB 协议常量和消息生成工具
 */
object AdbProtocol {
    // ADB 消息头长度
    const val ADB_HEADER_LENGTH = 24

    // 认证类型
    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3

    // ADB 命令
    const val CMD_AUTH = 0x48545541 // "AUTH"
    const val CMD_CNXN = 0x4e584e43 // "CNXN"
    const val CMD_OPEN = 0x4e45504f // "OPEN"
    const val CMD_OKAY = 0x59414b4f // "OKAY"
    const val CMD_CLSE = 0x45534c43 // "CLSE"
    const val CMD_WRTE = 0x45545257 // "WRTE"

    // 连接参数
    const val CONNECT_VERSION = 0x01000000

    /**
     * 最大数据大小
     *
     * 设置为 15KB 是因为有些设备 USB 仅支持最大 16KB
     * 如果 ADB 使用过大的数据，会导致 USB 无法传输，丢失数据
     *
     * 注意：旧版本的 adb 服务端硬编码 maxdata=4096
     * 如果设备实在太老，可尝试将此处修改为 4096
     */
    const val CONNECT_MAXDATA = 15 * 1024

    // 连接载荷
    val CONNECT_PAYLOAD = "host::\u0000".toByteArray()

    /**
     * 生成 CNXN (Connect) 消息
     */
    fun generateConnect(): ByteBuffer = generateMessage(CMD_CNXN, CONNECT_VERSION, CONNECT_MAXDATA, CONNECT_PAYLOAD)

    /**
     * 生成 AUTH (Authentication) 消息
     */
    fun generateAuth(
        type: Int,
        data: ByteArray,
    ): ByteBuffer = generateMessage(CMD_AUTH, type, 0, data)

    /**
     * 生成 OPEN 消息
     */
    fun generateOpen(
        localId: Int,
        dest: String,
    ): ByteBuffer {
        val destBytes = dest.toByteArray(StandardCharsets.UTF_8)
        val payload = ByteArray(destBytes.size + 1)
        System.arraycopy(destBytes, 0, payload, 0, destBytes.size)
        payload[destBytes.size] = 0 // null terminator
        return generateMessage(CMD_OPEN, localId, 0, payload)
    }

    /**
     * 生成 WRTE (Write) 消息
     */
    fun generateWrite(
        localId: Int,
        remoteId: Int,
        data: ByteArray,
    ): ByteBuffer = generateMessage(CMD_WRTE, localId, remoteId, data)

    /**
     * 生成 CLSE (Close) 消息
     */
    fun generateClose(
        localId: Int,
        remoteId: Int,
    ): ByteBuffer = generateMessage(CMD_CLSE, localId, remoteId, null)

    /**
     * 生成 OKAY 消息
     */
    fun generateOkay(
        localId: Int,
        remoteId: Int,
    ): ByteBuffer = generateMessage(CMD_OKAY, localId, remoteId, null)

    /**
     * 生成 ADB 消息
     */
    private fun generateMessage(
        cmd: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray?,
    ): ByteBuffer {
        val size = if (payload == null) ADB_HEADER_LENGTH else (ADB_HEADER_LENGTH + payload.size)
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(cmd)
        buffer.putInt(arg0)
        buffer.putInt(arg1)

        if (payload == null) {
            buffer.putInt(0) // payload length
            buffer.putInt(0) // checksum
        } else {
            buffer.putInt(payload.size)
            buffer.putInt(payloadChecksum(payload))
        }

        buffer.putInt(cmd.inv()) // magic = ~cmd

        if (payload != null) {
            buffer.put(payload)
        }

        buffer.flip()
        return buffer
    }

    /**
     * 计算载荷校验和
     */
    private fun payloadChecksum(payload: ByteArray): Int {
        var checksum = 0
        for (b in payload) {
            checksum += (b.toInt() and 0xFF)
        }
        return checksum
    }

    /**
     * ADB 消息数据类
     */
    data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payloadLength: Int,
        val payload: ByteBuffer?,
    ) {
        /**
         * 获取命令名称（用于调试）
         */
        fun getCommandName(): String =
            when (command) {
                CMD_AUTH -> "AUTH"
                CMD_CNXN -> "CNXN"
                CMD_OPEN -> "OPEN"
                CMD_OKAY -> "OKAY"
                CMD_CLSE -> "CLSE"
                CMD_WRTE -> "WRTE"
                else -> "UNKNOWN(0x${command.toString(16)})"
            }

        companion object {
            /**
             * 从通道解析 ADB 消息
             */
            fun parse(channel: AdbChannel): AdbMessage {
                // 读取消息头
                val header = channel.read(ADB_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)

                val command = header.getInt()
                val arg0 = header.getInt()
                val arg1 = header.getInt()
                val payloadLength = header.getInt()
                // val checksum = header.getInt()  // 跳过校验和
                // val magic = header.getInt()     // 跳过 magic

                // 读取载荷（如果有）
                val payload =
                    if (payloadLength > 0) {
                        channel.read(payloadLength)
                    } else {
                        null
                    }

                return AdbMessage(command, arg0, arg1, payloadLength, payload)
            }
        }
    }
}

/**
 * ADB 通道接口
 * 定义 ADB 通信的基本操作
 */
interface AdbChannel {
    /**
     * 写入数据到通道
     */
    fun write(data: ByteBuffer)

    /**
     * 从通道读取指定大小的数据
     */
    fun read(size: Int): ByteBuffer

    /**
     * 刷新通道
     */
    fun flush()

    /**
     * 关闭通道
     */
    fun close()
}

/*
 * USB Dadb 实现
 *
 * 将 USB ADB 通道包装成 Dadb 接口，使其能够像 TCP Dadb 一样使用
 *
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 * - adblib: https://github.com/tananaev/adblib
 */

package com.mobile.scrcpy.android.infrastructure.adb.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import dadb.AdbKeyPair
import dadb.AdbStream
import dadb.Dadb
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * USB Dadb 实现
 * 通过 USB ADB 通道实现 Dadb 接口
 */
class UsbDadb(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice,
    private val keyPair: AdbKeyPair,
    private val deviceId: String,
) : Dadb {
    private val channel = UsbAdbChannel(usbManager, usbDevice)
    private val supportedFeatures = mutableSetOf<String>()

    // 流管理
    private val localIdGenerator = AtomicInteger(1)
    private var maxData = AdbProtocol.CONNECT_MAXDATA
    private val streams = ConcurrentHashMap<Int, UsbAdbStream>()
    private val pendingStreams = ConcurrentHashMap<Int, LinkedBlockingQueue<UsbAdbStream>>()

    // 消息处理线程
    private val messageThread: Thread

    @Volatile
    private var closed = false

    init {
        // 初始化 ADB 连接
        initializeConnection()

        // 启动消息处理线程
        messageThread =
            Thread {
                handleMessages()
            }.apply {
                name = "USB-ADB-Messages"
                priority = Thread.MAX_PRIORITY
                start()
            }
    }

    /**
     * 初始化 ADB 连接（握手）
     */
    private fun initializeConnection() {
        LogManager.d(LogTags.ADB_CONNECTION, "Starting ADB handshake...")

        // 1. 发送 CNXN 消息
        channel.write(AdbProtocol.generateConnect())

        // 2. 接收响应
        var message = AdbProtocol.AdbMessage.parse(channel)
        LogManager.d(LogTags.ADB_CONNECTION, "Received: ${message.getCommandName()}")

        // 3. 处理认证
        if (message.command == AdbProtocol.CMD_AUTH) {
            // 发送签名
            val token = message.payload ?: throw IOException("AUTH message without token")

            // 提取 token 数据
            val tokenArray = ByteArray(token.remaining())
            token.duplicate().get(tokenArray)

            // 使用 RSA 签名 token
            val signature = UsbAdbAuth.signToken(tokenArray, keyPair)
            channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE, signature))

            message = AdbProtocol.AdbMessage.parse(channel)
            LogManager.d(LogTags.ADB_CONNECTION, "Received: ${message.getCommandName()}")

            // 如果还需要认证，发送公钥
            if (message.command == AdbProtocol.CMD_AUTH) {
                val publicKey = UsbAdbAuth.getPublicKeyBytes(keyPair)
                channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_RSA_PUBLIC, publicKey))
                message = AdbProtocol.AdbMessage.parse(channel)
                LogManager.d(LogTags.ADB_CONNECTION, "Received: ${message.getCommandName()}")
            }
        }

        // 4. 验证连接成功
        if (message.command != AdbProtocol.CMD_CNXN) {
            throw IOException("ADB handshake failed: expected CNXN, got ${message.getCommandName()}")
        }

        // 5. 保存最大数据大小
        maxData = message.arg1
        LogManager.d(LogTags.ADB_CONNECTION, "ADB handshake completed, maxData=$maxData")
    }

    /**
     * 处理接收到的消息
     */
    private fun handleMessages() {
        try {
            while (!closed && !Thread.currentThread().isInterrupted) {
                val message = AdbProtocol.AdbMessage.parse(channel)

                when (message.command) {
                    AdbProtocol.CMD_OKAY -> handleOkay(message)
                    AdbProtocol.CMD_WRTE -> handleWrite(message)
                    AdbProtocol.CMD_CLSE -> handleClose(message)
                    else -> LogManager.w(LogTags.ADB_CONNECTION, "Unknown command: ${message.getCommandName()}")
                }
            }
        } catch (e: Exception) {
            if (!closed) {
                LogManager.e(LogTags.ADB_CONNECTION, "Message handling error: ${e.message}")
                close()
            }
        }
    }

    /**
     * 处理 OKAY 消息
     */
    private fun handleOkay(message: AdbProtocol.AdbMessage) {
        val localId = message.arg1
        val remoteId = message.arg0

        // 检查是否是新连接的响应
        val queue = pendingStreams[localId]
        if (queue != null) {
            val stream = streams[localId]
            if (stream != null) {
                stream.remoteId = remoteId
                stream.canWrite = true
                queue.offer(stream)
            }
            return
        }

        // 已有连接的 OKAY
        val stream = streams[localId]
        if (stream != null) {
            stream.canWrite = true
        }
    }

    /**
     * 处理 WRTE 消息
     */
    private fun handleWrite(message: AdbProtocol.AdbMessage) {
        val localId = message.arg1
        val remoteId = message.arg0

        val stream = streams[localId]
        if (stream != null && message.payload != null) {
            stream.receiveData(message.payload)
            // 发送 OKAY 确认
            channel.write(AdbProtocol.generateOkay(localId, remoteId))
        }
    }

    /**
     * 处理 CLSE 消息
     */
    private fun handleClose(message: AdbProtocol.AdbMessage) {
        val localId = message.arg1

        val stream = streams.remove(localId)
        stream?.markClosed()
    }

    override fun open(destination: String): AdbStream {
        if (closed) throw IOException("Connection is closed")

        val localId = localIdGenerator.getAndIncrement()
        val queue = LinkedBlockingQueue<UsbAdbStream>(1)
        pendingStreams[localId] = queue

        val stream =
            UsbAdbStream(
                localId = localId,
                destination = destination,
                maxData = maxData,
                channel = channel,
                onClose = { streams.remove(it) },
            )
        streams[localId] = stream

        // 发送 OPEN 消息
        channel.write(AdbProtocol.generateOpen(localId, destination))

        // 等待连接建立
        val result =
            queue.poll(5, TimeUnit.SECONDS)
                ?: throw IOException("Timeout waiting for stream to open: $destination")

        pendingStreams.remove(localId)

        LogManager.d(LogTags.ADB_CONNECTION, "Stream opened: $destination (local=$localId, remote=${result.remoteId})")
        return result
    }

    override fun supportsFeature(feature: String): Boolean = feature in supportedFeatures

    override fun close() {
        if (closed) return
        closed = true

        LogManager.d(LogTags.ADB_CONNECTION, "Closing USB Dadb...")

        // 关闭所有流
        streams.values.forEach { it.markClosed() }
        streams.clear()
        pendingStreams.clear()

        // 停止消息处理线程
        messageThread.interrupt()

        // 关闭通道
        channel.close()
    }

    override fun toString(): String = deviceId
}

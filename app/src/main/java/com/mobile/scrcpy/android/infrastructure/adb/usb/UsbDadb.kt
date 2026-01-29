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
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
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
    private val streams = ConcurrentHashMap<Int, UsbAdbStreamImpl>()
    private val pendingStreams = ConcurrentHashMap<Int, LinkedBlockingQueue<UsbAdbStreamImpl>>()

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
            val signature = signToken(tokenArray, keyPair)
            channel.write(AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE, signature))

            message = AdbProtocol.AdbMessage.parse(channel)
            LogManager.d(LogTags.ADB_CONNECTION, "Received: ${message.getCommandName()}")

            // 如果还需要认证，发送公钥
            if (message.command == AdbProtocol.CMD_AUTH) {
                // 使用反射获取 publicKeyBytes
                val publicKeyBytesField = keyPair.javaClass.getDeclaredField("publicKeyBytes")
                publicKeyBytesField.isAccessible = true
                val publicKey = publicKeyBytesField.get(keyPair) as ByteArray

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
        val queue = LinkedBlockingQueue<UsbAdbStreamImpl>(1)
        pendingStreams[localId] = queue

        val stream = UsbAdbStreamImpl(localId, destination)
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

    /**
     * 使用 RSA 私钥签名 token
     * 参考 dadb 的实现
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    private fun signToken(
        token: ByteArray,
        keyPair: AdbKeyPair,
    ): ByteArray {
        try {
            // 获取私钥（使用反射）
            val privateKeyField = keyPair.javaClass.getDeclaredField("privateKey")
            privateKeyField.isAccessible = true
            val privateKey = privateKeyField.get(keyPair) as java.security.PrivateKey

            // RSA 签名
            val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, privateKey)

            // 添加签名填充
            val signaturePadding =
                ubyteArrayOf(
                    0x00u,
                    0x01u,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0xffu,
                    0x00u,
                    0x30u,
                    0x21u,
                    0x30u,
                    0x09u,
                    0x06u,
                    0x05u,
                    0x2bu,
                    0x0eu,
                    0x03u,
                    0x02u,
                    0x1au,
                    0x05u,
                    0x00u,
                    0x04u,
                    0x14u,
                ).toByteArray()

            cipher.update(signaturePadding)
            return cipher.doFinal(token)
        } catch (e: Exception) {
            throw IOException("Failed to sign token: ${e.message}", e)
        }
    }

    /**
     * USB ADB Stream 实现
     */
    private inner class UsbAdbStreamImpl(
        val localId: Int,
        val destination: String,
    ) : AdbStream {
        var remoteId: Int = 0
        var canWrite: Boolean = false

        private val receiveBuffer = LinkedBlockingQueue<ByteBuffer>()
        private var streamClosed = false

        private val inputStream = UsbInputStream()
        private val outputStream = UsbOutputStream()

        override val source: BufferedSource = inputStream.source().buffer()
        override val sink: BufferedSink = outputStream.sink().buffer()

        fun receiveData(data: ByteBuffer) {
            receiveBuffer.offer(data)
        }

        fun markClosed() {
            streamClosed = true
            receiveBuffer.offer(ByteBuffer.allocate(0)) // 唤醒等待的读取
        }

        override fun close() {
            if (streamClosed) return
            streamClosed = true

            // 发送 CLSE 消息
            if (remoteId != 0) {
                channel.write(AdbProtocol.generateClose(localId, remoteId))
            }

            streams.remove(localId)
            LogManager.d(LogTags.ADB_CONNECTION, "Stream closed: $destination")
        }

        /**
         * 输入流实现
         */
        private inner class UsbInputStream : InputStream() {
            private var currentBuffer: ByteBuffer? = null

            override fun read(): Int {
                val buffer =
                    currentBuffer ?: run {
                        val newBuffer =
                            receiveBuffer.poll(5, TimeUnit.SECONDS)
                                ?: throw IOException("Read timeout")
                        if (newBuffer.remaining() == 0 && streamClosed) {
                            return -1
                        }
                        currentBuffer = newBuffer
                        newBuffer
                    }

                return if (buffer.hasRemaining()) {
                    buffer.get().toInt() and 0xFF
                } else {
                    currentBuffer = null
                    read()
                }
            }

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
                val buffer =
                    currentBuffer ?: run {
                        val newBuffer =
                            receiveBuffer.poll(5, TimeUnit.SECONDS)
                                ?: throw IOException("Read timeout")
                        if (newBuffer.remaining() == 0 && streamClosed) {
                            return -1
                        }
                        currentBuffer = newBuffer
                        newBuffer
                    }

                val available = buffer.remaining()
                if (available == 0) {
                    currentBuffer = null
                    return read(b, off, len)
                }

                val toRead = minOf(available, len)
                buffer.get(b, off, toRead)

                if (!buffer.hasRemaining()) {
                    currentBuffer = null
                }

                return toRead
            }
        }

        /**
         * 输出流实现
         */
        private inner class UsbOutputStream : OutputStream() {
            private val buffer = Buffer()

            override fun write(b: Int) {
                buffer.writeByte(b)
            }

            override fun write(
                b: ByteArray,
                off: Int,
                len: Int,
            ) {
                buffer.write(b, off, len)
            }

            override fun flush() {
                if (buffer.size == 0L) return

                // 等待可以写入
                while (!canWrite && !streamClosed) {
                    Thread.sleep(10)
                }

                if (streamClosed) {
                    throw IOException("Stream is closed")
                }

                // 分块发送数据
                while (buffer.size > 0) {
                    val chunkSize = minOf((maxData - 128).toLong(), buffer.size).toInt()
                    val chunk = ByteArray(chunkSize)
                    buffer.read(chunk)

                    channel.write(AdbProtocol.generateWrite(localId, remoteId, chunk))
                    canWrite = false

                    // 等待 OKAY
                    while (!canWrite && !streamClosed) {
                        Thread.sleep(10)
                    }
                }
            }
        }
    }
}

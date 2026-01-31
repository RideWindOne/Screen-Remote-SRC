package com.mobile.scrcpy.android.infrastructure.adb.usb

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import dadb.AdbStream
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * USB ADB Stream 实现
 */
internal class UsbAdbStream(
    val localId: Int,
    val destination: String,
    private val maxData: Int,
    private val channel: UsbAdbChannel,
    private val onClose: (Int) -> Unit,
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

        onClose(localId)
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

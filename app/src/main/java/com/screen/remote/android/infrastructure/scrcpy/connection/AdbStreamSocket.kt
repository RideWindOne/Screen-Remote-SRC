package com.screen.remote.android.infrastructure.scrcpy.connection

import dadb.AdbStream
import okio.Buffer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 将 ADB 多路复用流适配成 Socket 语义，尽量复用现有 scrcpy 读取链路。
 */
internal class AdbStreamSocket(
    private val adbStream: AdbStream,
    private val streamLabel: String,
) : Socket() {
    @Volatile private var closed = false
    @Volatile private var connected = true
    @Volatile private var timeoutMs = 0
    @Volatile private var keepAliveEnabled = false
    @Volatile private var tcpNoDelayEnabled = true
    @Volatile private var receiveBufferSizeValue = 64 * 1024
    @Volatile private var sendBufferSizeValue = 64 * 1024

    private val pendingChunks = LinkedBlockingQueue<Chunk>()
    private val terminalError = AtomicReference<IOException?>()
    private val input = AdbSocketInputStream()
    private val output = AdbSocketOutputStream()
    private val pumpThread =
        thread(
            start = true,
            isDaemon = true,
            name = "adb-stream-socket-$streamLabel",
        ) {
            pumpInput()
        }

    override fun getInputStream(): InputStream = input

    override fun getOutputStream(): OutputStream = output

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        connected = false
        runCatching { adbStream.close() }
        pendingChunks.offer(Chunk.Eof)
        pumpThread.interrupt()
    }

    override fun isConnected(): Boolean = connected && !closed

    override fun isClosed(): Boolean = closed

    override fun setSoTimeout(timeout: Int) {
        timeoutMs = timeout.coerceAtLeast(0)
    }

    override fun getSoTimeout(): Int = timeoutMs

    override fun setKeepAlive(on: Boolean) {
        keepAliveEnabled = on
    }

    override fun getKeepAlive(): Boolean = keepAliveEnabled

    override fun setTcpNoDelay(on: Boolean) {
        tcpNoDelayEnabled = on
    }

    override fun getTcpNoDelay(): Boolean = tcpNoDelayEnabled

    override fun setReceiveBufferSize(size: Int) {
        receiveBufferSizeValue = size
    }

    override fun getReceiveBufferSize(): Int = receiveBufferSizeValue

    override fun setSendBufferSize(size: Int) {
        sendBufferSizeValue = size
    }

    override fun getSendBufferSize(): Int = sendBufferSizeValue

    private fun pumpInput() {
        val readBuffer = ByteArray(DEFAULT_CHUNK_SIZE)
        try {
            while (!closed) {
                val read = adbStream.source.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) {
                    break
                }
                pendingChunks.put(Chunk.Data(readBuffer.copyOf(read)))
            }
        } catch (e: IOException) {
            if (!closed) {
                terminalError.set(e)
            }
        } finally {
            connected = false
            pendingChunks.offer(Chunk.Eof)
        }
    }

    private inner class AdbSocketInputStream : InputStream() {
        private var current: ByteArray? = null
        private var offset = 0

        override fun read(): Int {
            val singleByte = ByteArray(1)
            val read = read(singleByte, 0, 1)
            return if (read <= 0) -1 else singleByte[0].toInt() and 0xFF
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (len == 0) {
                return 0
            }

            while (true) {
                val chunk = current
                if (chunk != null && offset < chunk.size) {
                    val copySize = minOf(len, chunk.size - offset)
                    chunk.copyInto(b, off, offset, offset + copySize)
                    offset += copySize
                    if (offset >= chunk.size) {
                        current = null
                        offset = 0
                    }
                    return copySize
                }

                when (val next = takeChunk()) {
                    is Chunk.Data -> {
                        current = next.bytes
                        offset = 0
                    }

                    Chunk.Eof -> {
                        terminalError.get()?.let { throw it }
                        return -1
                    }
                }
            }
        }

        private fun takeChunk(): Chunk {
            val timeout = timeoutMs
            return if (timeout > 0) {
                pendingChunks.poll(timeout.toLong(), TimeUnit.MILLISECONDS)
                    ?: throw SocketTimeoutException("ADB stream read timed out: $streamLabel")
            } else {
                pendingChunks.take()
            }
        }
    }

    private inner class AdbSocketOutputStream : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()))
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            if (closed) {
                throw IOException("ADB stream socket closed: $streamLabel")
            }
            val buffer = Buffer().write(b, off, len)
            adbStream.sink.write(buffer, len.toLong())
        }

        override fun flush() {
            if (!closed) {
                adbStream.sink.flush()
            }
        }

        override fun close() {
            this@AdbStreamSocket.close()
        }
    }

    private sealed interface Chunk {
        data class Data(
            val bytes: ByteArray,
        ) : Chunk

        data object Eof : Chunk
    }

    private companion object {
        private const val DEFAULT_CHUNK_SIZE = 8 * 1024
    }
}

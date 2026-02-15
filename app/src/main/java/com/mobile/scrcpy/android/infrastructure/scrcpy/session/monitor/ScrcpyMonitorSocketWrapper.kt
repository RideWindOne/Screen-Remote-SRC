package com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor

import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SocketType
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

internal object ScrcpyMonitorSocketWrapper {
    fun wrap(
        socket: Socket,
        socketType: SocketType,
        monitorBus: ScrcpyMonitorBus?,
    ): Socket {
        if (monitorBus == null) {
            return socket
        }

        return object : Socket() {
            override fun getInputStream(): InputStream =
                MonitoredInputStream(socket.getInputStream(), socketType, monitorBus)

            override fun getOutputStream(): OutputStream =
                MonitoredOutputStream(socket.getOutputStream(), socketType, monitorBus)

            override fun connect(endpoint: java.net.SocketAddress?) = socket.connect(endpoint)

            override fun connect(
                endpoint: java.net.SocketAddress?,
                timeout: Int,
            ) = socket.connect(endpoint, timeout)

            override fun close() = socket.close()

            override fun isConnected() = socket.isConnected

            override fun isClosed() = socket.isClosed

            override fun getInetAddress() = socket.inetAddress

            override fun getPort() = socket.port

            override fun getLocalPort() = socket.localPort
        }
    }

    private class MonitoredInputStream(
        private val original: InputStream,
        private val socketType: SocketType,
        private val monitorBus: ScrcpyMonitorBus,
    ) : InputStream() {
        private var lastActivityTime = System.currentTimeMillis()
        private var idleCheckCounter = 0

        override fun read(): Int {
            val result = original.read()
            if (result != -1) {
                onDataReceived(1)
            }
            return result
        }

        override fun read(b: ByteArray): Int {
            val result = original.read(b)
            if (result > 0) {
                onDataReceived(result.toLong())
            }
            return result
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            val result = original.read(b, off, len)
            if (result > 0) {
                onDataReceived(result.toLong())
            } else {
                checkIdle()
            }
            return result
        }

        private fun onDataReceived(bytes: Long) {
            monitorBus.pushEvent(ScrcpyMonitorEvent.SocketDataReceived(socketType, bytes))
            lastActivityTime = System.currentTimeMillis()
            idleCheckCounter = 0
        }

        private fun checkIdle() {
            idleCheckCounter++
            if (idleCheckCounter >= IDLE_CHECK_INTERVAL) {
                val idleTime = System.currentTimeMillis() - lastActivityTime
                if (idleTime > IDLE_TIMEOUT_MS) {
                    monitorBus.pushEvent(ScrcpyMonitorEvent.SocketIdle(socketType, idleTime))
                }
                idleCheckCounter = 0
            }
        }

        override fun close() = original.close()
    }

    private class MonitoredOutputStream(
        private val original: OutputStream,
        private val socketType: SocketType,
        private val monitorBus: ScrcpyMonitorBus,
    ) : OutputStream() {
        override fun write(b: Int) {
            original.write(b)
            onDataSent(1)
        }

        override fun write(b: ByteArray) {
            original.write(b)
            onDataSent(b.size.toLong())
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            original.write(b, off, len)
            onDataSent(len.toLong())
        }

        private fun onDataSent(bytes: Long) {
            monitorBus.pushEvent(ScrcpyMonitorEvent.SocketDataSent(socketType, bytes))
        }

        override fun flush() = original.flush()

        override fun close() = original.close()
    }

    private const val IDLE_CHECK_INTERVAL = 10
    private const val IDLE_TIMEOUT_MS = 5000L
}

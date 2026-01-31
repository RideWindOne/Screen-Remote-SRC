package com.mobile.scrcpy.android.infrastructure.scrcpy.session

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * 监控总线集成辅助类
 * 
 * 提供便捷方法，用于在现有代码中集成监控总线
 */
object ScrcpyMonitorIntegration {
    /**
     * 包装 Socket，自动上报数据统计
     */
    fun wrapSocket(
        socket: Socket,
        socketType: SocketType,
        monitorBus: ScrcpyMonitorBus?,
    ): Socket {
        if (monitorBus == null) return socket

        return object : Socket() {
            override fun getInputStream(): InputStream {
                val original = socket.getInputStream()
                return MonitoredInputStream(original, socketType, monitorBus)
            }

            override fun getOutputStream(): OutputStream {
                val original = socket.getOutputStream()
                return MonitoredOutputStream(original, socketType, monitorBus)
            }

            // 委托其他方法
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

    /**
     * 监控的 InputStream
     */
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
            if (idleCheckCounter >= 10) { // 每 10 次检查一次
                val idleTime = System.currentTimeMillis() - lastActivityTime
                if (idleTime > 5000) { // 5 秒空闲
                    monitorBus.pushEvent(ScrcpyMonitorEvent.SocketIdle(socketType, idleTime))
                }
                idleCheckCounter = 0
            }
        }

        override fun close() = original.close()
    }

    /**
     * 监控的 OutputStream
     */
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

    /**
     * 监控 Server 日志输出
     */
    fun monitorServerLog(
        logLine: String,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.ServerLog(logLine))

        // 检测特殊日志，推送设备状态事件
        when {
            logLine.contains("screen locked", ignoreCase = true) -> {
                monitorBus?.pushEvent(ScrcpyMonitorEvent.DeviceScreenLocked)
            }

            logLine.contains("screen unlocked", ignoreCase = true) -> {
                monitorBus?.pushEvent(ScrcpyMonitorEvent.DeviceScreenUnlocked)
            }

            logLine.contains("screen off", ignoreCase = true) -> {
                monitorBus?.pushEvent(ScrcpyMonitorEvent.DeviceScreenOff)
            }

            logLine.contains("screen on", ignoreCase = true) -> {
                monitorBus?.pushEvent(ScrcpyMonitorEvent.DeviceScreenOn)
            }
        }
    }

    /**
     * 监控视频帧解码
     */
    fun monitorVideoFrame(
        width: Int,
        height: Int,
        pts: Long,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.VideoFrameDecoded(width, height, pts))
    }

    /**
     * 监控音频帧解码
     */
    fun monitorAudioFrame(
        sampleRate: Int,
        channels: Int,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.AudioFrameDecoded(sampleRate, channels))
    }

    /**
     * 上报视频解码器停滞
     */
    fun reportVideoDecoderStalled(
        reason: String,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.VideoDecoderStalled(reason))
    }

    /**
     * 上报音频解码器停滞
     */
    fun reportAudioDecoderStalled(
        reason: String,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.AudioDecoderStalled(reason))
    }

    /**
     * 上报连接建立
     */
    fun reportConnectionEstablished(monitorBus: ScrcpyMonitorBus?) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.ConnectionEstablished)
    }

    /**
     * 上报连接丢失
     */
    fun reportConnectionLost(
        reason: String,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.ConnectionLost(reason))
    }

    /**
     * 上报异常
     */
    fun reportException(
        type: ExceptionType,
        message: String,
        throwable: Throwable? = null,
        monitorBus: ScrcpyMonitorBus?,
    ) {
        monitorBus?.pushEvent(ScrcpyMonitorEvent.Exception(type, message, throwable))
    }

    /**
     * 输出状态摘要到日志
     */
    fun logStateSummary(monitorBus: ScrcpyMonitorBus?) {
        if (monitorBus == null) return

        val summary = monitorBus.getStateSummary()
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, summary)
    }
}

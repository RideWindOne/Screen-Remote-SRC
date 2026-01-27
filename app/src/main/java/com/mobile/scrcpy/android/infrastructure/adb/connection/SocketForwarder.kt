package com.mobile.scrcpy.android.infrastructure.adb.connection

import dadb.Dadb
import okio.BufferedSink
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * Socket 转发器，支持将本地 TCP 端口转发到设备的 socket（包括 localabstract）
 * 参考 dadb PR #90: Extend tcpForward to support more socket domains
 */
internal class SocketForwarder(
    private val dadb: Dadb,
    private val hostPort: Int,
    private val targetSocket: String, // 例如: "localabstract:scrcpy_12345678" 或 "tcp:27183"
) : AutoCloseable {

    private var state: State = State.STOPPED
    private var serverThread: Thread? = null
    private var server: ServerSocket? = null
    private var clientExecutor: ExecutorService? = null

    fun isRunning(): Boolean = state == State.STARTED

    fun start() {
        check(state == State.STOPPED) { "Forwarder is already started at port $hostPort" }

        moveToState(State.STARTING)

        clientExecutor = Executors.newCachedThreadPool()
        serverThread = thread {
            try {
                handleForwarding()
            } catch (ignored: SocketException) {
                // Do nothing
            } catch (e: IOException) {
                // Log error if needed
            } finally {
                moveToState(State.STOPPED)
            }
        }

        waitFor(10, 10000) {  // 增加超时到 10 秒
            state == State.STARTED
        }
    }

    private fun handleForwarding() {
        val serverRef = ServerSocket(hostPort)
        server = serverRef

        moveToState(State.STARTED)

        while (!Thread.interrupted()) {
            val client = serverRef.accept()

            clientExecutor?.execute {
                try {
                    // 关键：使用 targetSocket 而不是固定的 "tcp:$targetPort"
                    // 支持 localabstract:, localreserved:, localfilesystem: 等
                    val adbStream = dadb.open(targetSocket)

                    val readerThread = thread {
                        forward(
                            client.getInputStream().source(),
                            adbStream.sink
                        )
                    }

                    try {
                        forward(
                            adbStream.source,
                            client.sink().buffer()
                        )
                    } finally {
                        adbStream.close()
                        client.close()

                        readerThread.interrupt()
                    }
                } catch (e: Exception) {
                    // ADB 连接断开或其他错误，安静地关闭客户端连接
                    try {
                        client.close()
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }

    override fun close() {
        if (state == State.STOPPED || state == State.STOPPING) {
            return
        }

        // Make sure that we are not stopping the server while it is in a transient state
        waitFor(10, 10000) {  // 增加超时到 10 秒
            state == State.STARTED
        }

        moveToState(State.STOPPING)

        server?.close()
        server = null
        serverThread?.interrupt()
        serverThread = null
        clientExecutor?.shutdown()
        clientExecutor?.awaitTermination(5, TimeUnit.SECONDS)
        clientExecutor = null

        waitFor(10, 10000) {  // 增加超时到 10 秒
            state == State.STOPPED
        }
    }

    private fun forward(source: Source, sink: BufferedSink) {
        try {
            while (!Thread.interrupted()) {
                try {
                    if (source.read(sink.buffer, 256) >= 0) {
                        sink.flush()
                    } else {
                        return
                    }
                } catch (ignored: IOException) {
                    // Do nothing
                }
            }
        } catch (ignored: InterruptedException) {
            // Do nothing
        } catch (ignored: InterruptedIOException) {
            // do nothing
        }
    }

    private fun moveToState(state: State) {
        this.state = state
    }

    private enum class State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    private fun waitFor(intervalMs: Int, timeoutMs: Int, test: () -> Boolean) {
        val start = System.currentTimeMillis()
        var lastCheck = start
        while (!test()) {
            val now = System.currentTimeMillis()
            val timeSinceStart = now - start
            val timeSinceLastCheck = now - lastCheck
            if (timeoutMs in 0..timeSinceStart) {
                throw TimeoutException("SocketForwarder 超时 (${timeoutMs}ms): 当前状态=$state, 端口=$hostPort")
            }
            val sleepTime = intervalMs - timeSinceLastCheck
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            }
            lastCheck = System.currentTimeMillis()
        }
    }
}

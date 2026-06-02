package com.screen.remote.android.infrastructure.scrcpy.controller

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.LogManager.dControl
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataOutputStream
import java.net.Socket

internal class ScrcpyControllerTransport(
    private val getControlSocket: () -> Socket?,
    private val clearControlSocket: () -> Unit,
    private val localPort: Int,
) {
    private interface ControlMessage {
        fun writeTo(output: DataOutputStream)
    }

    private data class TouchMessage(
        val action: Int,
        val pointerId: Long,
        val x: Int,
        val y: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val pressure: Float,
    ) : ControlMessage {
        override fun writeTo(output: DataOutputStream) {
            output.writeByte(ScrcpyProtocol.MSG_TYPE_INJECT_TOUCH_EVENT)
            output.writeByte(action)
            output.writeLong(pointerId)
            output.writeInt(x)
            output.writeInt(y)
            output.writeShort(screenWidth)
            output.writeShort(screenHeight)
            output.writeShort((pressure * 0xFFFF).toInt().coerceIn(0, 0xFFFF))
            output.writeInt(0)
            output.writeInt(0)
        }
    }

    private data class KeyMessage(
        val action: Int,
        val keyCode: Int,
        val repeat: Int,
        val metaState: Int,
    ) : ControlMessage {
        override fun writeTo(output: DataOutputStream) {
            output.writeByte(ScrcpyProtocol.MSG_TYPE_INJECT_KEYCODE)
            output.writeByte(action)
            output.writeInt(keyCode)
            output.writeInt(repeat)
            output.writeInt(metaState)
        }
    }

    private data class TextMessage(
        val textBytes: ByteArray,
    ) : ControlMessage {
        override fun writeTo(output: DataOutputStream) {
            output.writeByte(ScrcpyProtocol.MSG_TYPE_INJECT_TEXT)
            output.writeInt(textBytes.size)
            output.write(textBytes)
        }
    }

    private object KeepaliveMessage : ControlMessage {
        override fun writeTo(output: DataOutputStream) {
            output.writeByte(ScrcpyProtocol.MSG_TYPE_INJECT_TEXT)
            output.writeInt(0)
        }
    }

    private data class DisplayPowerMessage(
        val on: Boolean,
    ) : ControlMessage {
        override fun writeTo(output: DataOutputStream) {
            output.writeByte(ScrcpyProtocol.MSG_TYPE_SET_DISPLAY_POWER)
            output.writeBoolean(on)
        }
    }

    private var wakeSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val queueLock = Any()
    private val orderedMessages = ArrayDeque<ControlMessage>()
    private val latestTouchMoves = LinkedHashMap<Long, TouchMessage>()
    private val controlScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var senderJob: Job? = null

    @Volatile
    private var output: DataOutputStream? = null

    @Volatile
    private var outputSocket: Socket? = null

    @Volatile
    private var lastControlActivityAtMs: Long = 0L
    private var keepaliveSentCount: Int = 0

    fun start(deviceId: String) {
        if (senderJob?.isActive == true) {
            dControl(LogTags.SCRCPY_CLIENT) { "控制消息发送线程已在运行: $deviceId" }
            return
        }

        lastControlActivityAtMs = System.currentTimeMillis()
        keepaliveSentCount = 0

        senderJob =
            controlScope.launch {
                dControl(LogTags.SDL) { "控制消息发送线程已启动" }
                while (isActive) {
                    try {
                        val signalReceived =
                            withTimeoutOrNull(ScrcpyConstants.CONTROL_KEEPALIVE_INTERVAL_MS) {
                                wakeSignal.receive()
                                true
                            } ?: false

                        if (signalReceived) {
                            drainPendingMessages()
                        } else if (shouldSendKeepalive()) {
                            sendControlMessage(KeepaliveMessage, isKeepalive = true)
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            LogManager.e(LogTags.SCRCPY_CLIENT, "控制消息发送异常: ${e.message}")
                        }
                    }
                }
                dControl(LogTags.SDL) { "控制消息发送线程已停止: $deviceId" }
            }
    }

    fun isRunning(): Boolean = senderJob?.isActive == true

    fun stop() {
        senderJob?.cancel()
        senderJob = null
        synchronized(queueLock) {
            orderedMessages.clear()
            latestTouchMoves.clear()
        }
        wakeSignal.close()
        wakeSignal = Channel(capacity = Channel.CONFLATED)
        output = null
        outputSocket = null
        lastControlActivityAtMs = 0L
        keepaliveSentCount = 0
        dControl(LogTags.SDL) { "控制消息发送线程已取消" }
    }

    fun destroy() {
        stop()
        controlScope.cancel()
    }

    fun enqueueTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
    ): Result<Boolean> =
        enqueue(
            TouchMessage(
                action = action,
                pointerId = pointerId,
                x = x,
                y = y,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                pressure = pressure,
            ),
        )

    fun enqueueKey(
        action: Int,
        keyCode: Int,
        repeat: Int,
        metaState: Int,
    ): Result<Boolean> =
        enqueue(
            KeyMessage(
                action = action,
                keyCode = keyCode,
                repeat = repeat,
                metaState = metaState,
            ),
        )

    fun enqueueText(text: String): Result<Boolean> {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        if (textBytes.size > 300) {
            return Result.failure(IllegalArgumentException(RemoteTexts.ERROR_TEXT_TOO_LONG.get()))
        }

        return enqueue(TextMessage(textBytes))
    }

    fun enqueueDisplayPower(on: Boolean): Result<Boolean> = enqueue(DisplayPowerMessage(on))

    private fun enqueue(message: ControlMessage): Result<Boolean> =
        try {
            synchronized(queueLock) {
                if (message is TouchMessage && message.action == 2) {
                    latestTouchMoves[message.pointerId] = message
                } else {
                    if (message is TouchMessage) {
                        if (message.action == 1 || message.action == 3) {
                            latestTouchMoves.remove(message.pointerId)?.let { latestMove ->
                                orderedMessages.addLast(latestMove)
                            }
                        } else {
                            latestTouchMoves.remove(message.pointerId)
                        }
                    }
                    orderedMessages.addLast(message)
                }
            }
            wakeSignal.trySend(Unit)
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "消息入队失败: ${e.message}")
            Result.failure(e)
        }

    fun currentSocket(): Socket? = getControlSocket()

    fun hasReadySocket(): Boolean {
        val socket = currentSocket() ?: return false
        return !socket.isClosed && socket.isConnected
    }

    private fun shouldSendKeepalive(): Boolean {
        val socket = currentSocket() ?: return false
        if (socket.isClosed || !socket.isConnected) {
            return false
        }

        val idleMs = System.currentTimeMillis() - lastControlActivityAtMs
        return idleMs >= ScrcpyConstants.CONTROL_KEEPALIVE_INTERVAL_MS
    }

    private fun drainPendingMessages() {
        while (true) {
            val nextMessage =
                synchronized(queueLock) {
                    when {
                        orderedMessages.isNotEmpty() -> orderedMessages.removeFirst()
                        latestTouchMoves.isNotEmpty() -> {
                            val iterator = latestTouchMoves.entries.iterator()
                            if (!iterator.hasNext()) {
                                null
                            } else {
                                iterator.next().value.also { iterator.remove() }
                            }
                        }
                        else -> null
                    }
                } ?: return

            sendControlMessage(nextMessage, isKeepalive = false)
        }
    }

    private fun sendControlMessage(
        message: ControlMessage,
        isKeepalive: Boolean,
    ) {
        val out = ensureOutput()
        if (out == null) {
            if (!isKeepalive) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "控制 Socket 未就绪，消息已丢弃")
            }
            return
        }

        try {
            message.writeTo(out)
            out.flush()
            lastControlActivityAtMs = System.currentTimeMillis()

            if (isKeepalive) {
                keepaliveSentCount++
                if (keepaliveSentCount == 1 || keepaliveSentCount % 10 == 0) {
                    dControl(LogTags.SCRCPY_CLIENT) { "控制流保活已发送: count=$keepaliveSentCount, port=$localPort" }
                }
            }
        } catch (e: Exception) {
            handleWriteError(e, isKeepalive)
        }
    }

    private fun ensureOutput(): DataOutputStream? {
        val socket = getControlSocket()
        if (socket == null || socket.isClosed || !socket.isConnected) {
            output = null
            outputSocket = null
            return null
        }

        val cachedOutput = output
        if (cachedOutput != null && outputSocket === socket) {
            return cachedOutput
        }

        return DataOutputStream(socket.getOutputStream()).also {
            output = it
            outputSocket = socket
        }
    }

    private fun handleWriteError(
        e: Exception,
        isKeepalive: Boolean,
    ) {
        val message = if (isKeepalive) "控制流保活发送失败: ${e.message}" else "Socket 发送失败: ${e.message}"
        LogManager.e(LogTags.SCRCPY_CLIENT, message)
        SessionIssueTracker.record("control.write", e.message ?: "Unknown control socket write error")
        output = null
        outputSocket = null
        clearControlSocket()
    }
}

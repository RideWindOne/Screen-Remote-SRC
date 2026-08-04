package com.screen.remote.android.infrastructure.scrcpy.controller

import android.os.Process
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.ScrcpyConstants
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.LogManager.dControl
import com.screen.remote.android.core.common.manager.SessionIssueTracker
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.scrcpy.connection.TouchAction
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyClipboardProtocol
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyDeviceMessage
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyDeviceMessageReader
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

internal fun configureControlSocketForStreaming(socket: Socket) {
    socket.soTimeout = 0
}

internal class TouchTransportTiming(
    private var minimumHoldDurationNanos: Long = 0L,
    private var moveIntervalNanos: Long = 0L,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val downSentAtNanos = HashMap<Long, Long>()
    private var lastMoveSentAtNanos: Long? = null

    init {
        require(minimumHoldDurationNanos >= 0L)
        require(moveIntervalNanos >= 0L)
    }

    @Synchronized
    fun configureGameMode(enabled: Boolean) {
        minimumHoldDurationNanos =
            if (enabled) {
                ScrcpyConstants.GAME_CONTROL_MIN_TOUCH_HOLD_MS * NANOS_PER_MILLISECOND
            } else {
                0L
            }
        moveIntervalNanos =
            if (enabled) {
                ScrcpyConstants.GAME_CONTROL_TOUCH_MOVE_INTERVAL_MS * NANOS_PER_MILLISECOND
            } else {
                0L
            }
        clearLocked()
    }

    @Synchronized
    fun remainingHoldDelayNanos(pointerId: Long): Long {
        val downAt = downSentAtNanos[pointerId] ?: return 0L
        return (minimumHoldDurationNanos - (nanoTime() - downAt)).coerceAtLeast(0L)
    }

    @Synchronized
    fun remainingMoveDelayNanos(): Long {
        val lastMoveAt = lastMoveSentAtNanos ?: return 0L
        return (moveIntervalNanos - (nanoTime() - lastMoveAt)).coerceAtLeast(0L)
    }

    @Synchronized
    fun onTouchSent(
        action: Int,
        pointerId: Long,
    ) {
        when (action) {
            TouchAction.ACTION_DOWN -> downSentAtNanos[pointerId] = nanoTime()
            TouchAction.ACTION_UP -> downSentAtNanos.remove(pointerId)
            TouchAction.ACTION_MOVE -> lastMoveSentAtNanos = nanoTime()
        }
    }

    @Synchronized
    fun clear() {
        clearLocked()
    }

    private fun clearLocked() {
        downSentAtNanos.clear()
        lastMoveSentAtNanos = null
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal class ScrcpyControllerTransport(
    private val getControlSocket: () -> Socket?,
    private val clearControlSocket: () -> Unit,
    private val localPort: Int,
    private val onClipboardReceived: (String) -> Unit,
    private val issueTracker: SessionIssueTracker,
) {
    private interface ControlMessage {
        fun encodeTo(buffer: ByteBuffer)
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
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_INJECT_TOUCH_EVENT.toByte())
            buffer.put(action.toByte())
            buffer.putLong(pointerId)
            buffer.putInt(x)
            buffer.putInt(y)
            buffer.putShort(screenWidth.toShort())
            buffer.putShort(screenHeight.toShort())
            buffer.putShort((pressure.coerceIn(0f, 1f) * 0xFFFF).toInt().toShort())
            buffer.putInt(0)
            buffer.putInt(0)
        }
    }

    private data class KeyMessage(
        val action: Int,
        val keyCode: Int,
        val repeat: Int,
        val metaState: Int,
    ) : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_INJECT_KEYCODE.toByte())
            buffer.put(action.toByte())
            buffer.putInt(keyCode)
            buffer.putInt(repeat)
            buffer.putInt(metaState)
        }
    }

    private class TextMessage(
        val textBytes: ByteArray,
    ) : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_INJECT_TEXT.toByte())
            buffer.putInt(textBytes.size)
            buffer.put(textBytes)
        }
    }

    private class EncodedMessage(
        val bytes: ByteArray,
    ) : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(bytes)
        }
    }

    private object KeepaliveMessage : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_INJECT_TEXT.toByte())
            buffer.putInt(0)
        }
    }

    private object RotateDeviceMessage : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_ROTATE_DEVICE.toByte())
        }
    }

    private data class DisplayPowerMessage(
        val on: Boolean,
    ) : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_SET_DISPLAY_POWER.toByte())
            buffer.put((if (on) 1 else 0).toByte())
        }
    }

    private class StartAppMessage(
        val nameBytes: ByteArray,
    ) : ControlMessage {
        override fun encodeTo(buffer: ByteBuffer) {
            buffer.put(ScrcpyProtocol.MSG_TYPE_START_APP.toByte())
            buffer.put(nameBytes.size.toByte())
            buffer.put(nameBytes)
        }
    }

    private var wakeSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val queueLock = Any()
    private val orderedMessages = ArrayDeque<ControlMessage>()
    private val latestTouchMoves = LinkedHashMap<Long, TouchMessage>()
    private val controlDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "scrcpy-control-$localPort").apply { isDaemon = true }
            }.asCoroutineDispatcher()
    private val controlScope = CoroutineScope(controlDispatcher + SupervisorJob())
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var senderJob: Job? = null
    private var receiverJob: Job? = null

    @Volatile
    private var output: DataOutputStream? = null

    @Volatile
    private var outputSocket: Socket? = null

    @Volatile
    private var lastControlActivityAtMs: Long = 0L
    private var keepaliveSentCount: Int = 0
    private val messageBuffer = ByteBuffer.allocate(MAX_BUFFERED_CONTROL_MESSAGE_SIZE)
    private val touchTiming = TouchTransportTiming()

    fun start(
        deviceId: String,
        gameMode: Boolean,
    ) {
        touchTiming.configureGameMode(gameMode)
        if (senderJob?.isActive == true) {
            dControl(LogTags.SCRCPY_CLIENT) { "The control message sending thread is already running: $deviceId" }
            return
        }

        lastControlActivityAtMs = System.currentTimeMillis()
        keepaliveSentCount = 0

        senderJob =
            controlScope.launch {
                configureSenderThreadPriority(gameMode)
                dControl(LogTags.SDL) { "The control message sending thread has been started" }
                while (isActive) {
                    try {
                        drainReadyMessages()

                        val waitMs = nextWakeDelayMs()
                        if (waitMs == 0L) {
                            continue
                        }

                        val signalReceived =
                            withTimeoutOrNull(waitMs.milliseconds) {
                                wakeSignal.receive()
                                true
                            } ?: false

                        if (!signalReceived && !hasPendingMessages() && shouldSendKeepalive()) {
                            sendControlMessage(KeepaliveMessage, isKeepalive = true)
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            LogManager.e(LogTags.SCRCPY_CLIENT, "Control message sending exception: ${e.message}")
                        }
                    }
                }
                dControl(LogTags.SDL) { "The control message sending thread has stopped: $deviceId" }
            }

        receiverJob =
            receiverScope.launch {
                receiveDeviceMessages(deviceId)
            }
    }

    fun isRunning(): Boolean = senderJob?.isActive == true

    fun stop() {
        senderJob?.cancel()
        senderJob = null
        receiverJob?.cancel()
        receiverJob = null
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
        touchTiming.clear()
        dControl(LogTags.SDL) { "The control message sending thread has been canceled" }
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

    fun enqueueClipboardAndPaste(text: String): Result<Boolean> =
        try {
            enqueue(EncodedMessage(ScrcpyClipboardProtocol.encode(text, paste = true)))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }

    fun enqueueDisplayPowerOff(): Result<Boolean> = enqueue(DisplayPowerMessage(on = false))

    fun enqueueRotateDevice(): Result<Boolean> = enqueue(RotateDeviceMessage)

    fun enqueueStartApp(name: String): Result<Boolean> {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.isEmpty() || nameBytes.size > 255) {
            return Result.failure(IllegalArgumentException("Start app name must contain 1 to 255 UTF-8 bytes"))
        }
        return enqueue(StartAppMessage(nameBytes))
    }

    private fun enqueue(message: ControlMessage): Result<Boolean> =
        try {
            synchronized(queueLock) {
                if (message is TouchMessage && message.action == 2) {
                    latestTouchMoves[message.pointerId] = message
                } else {
                    // DOWN/UP/key/text are ordering barriers. Flush only the latest MOVE for each
                    // active pointer before the barrier so old positions can never overtake it.
                    flushLatestTouchMovesLocked()
                    orderedMessages.addLast(message)
                }
            }
            wakeSignal.trySend(Unit)
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "Message enqueue failed: ${e.message}")
            Result.failure(e)
        }

    private fun flushLatestTouchMovesLocked() {
        latestTouchMoves.values.forEach(orderedMessages::addLast)
        latestTouchMoves.clear()
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

    private suspend fun drainReadyMessages() {
        while (true) {
            val nextMessage =
                synchronized(queueLock) {
                    when {
                        orderedMessages.isNotEmpty() -> orderedMessages.removeFirst()
                        latestTouchMoves.isNotEmpty() && touchTiming.remainingMoveDelayNanos() == 0L -> {
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

    private fun nextWakeDelayMs(): Long =
        synchronized(queueLock) {
            when {
                orderedMessages.isNotEmpty() -> 0L
                latestTouchMoves.isNotEmpty() -> nanosToDelayMillis(touchTiming.remainingMoveDelayNanos())
                else -> ScrcpyConstants.CONTROL_KEEPALIVE_INTERVAL_MS
            }
        }

    private fun hasPendingMessages(): Boolean =
        synchronized(queueLock) {
            orderedMessages.isNotEmpty() || latestTouchMoves.isNotEmpty()
        }

    // Control writes intentionally run on controlDispatcher, a dedicated single blocking thread.
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun sendControlMessage(
        message: ControlMessage,
        isKeepalive: Boolean,
    ) {
        val out = ensureOutput()
        if (out == null) {
            if (!isKeepalive) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "Control Socket is not ready, message has been discarded")
            }
            return
        }

        try {
            if (message is TouchMessage && message.action == TouchAction.ACTION_UP) {
                val holdDelayNanos = touchTiming.remainingHoldDelayNanos(message.pointerId)
                if (holdDelayNanos > 0L) {
                    delay(nanosToDelayMillis(holdDelayNanos).milliseconds)
                }
            }

            if (message is EncodedMessage) {
                // Clipboard packets can be 256 KiB. Write their already encoded bytes directly
                // instead of retaining another large buffer and copying every packet into it.
                out.write(message.bytes)
            } else {
                messageBuffer.clear()
                message.encodeTo(messageBuffer)
                out.write(messageBuffer.array(), 0, messageBuffer.position())
            }
            out.flush()
            lastControlActivityAtMs = System.currentTimeMillis()

            if (message is TouchMessage) {
                touchTiming.onTouchSent(message.action, message.pointerId)
            }

            if (isKeepalive) {
                keepaliveSentCount++
                if (keepaliveSentCount == 1 || keepaliveSentCount % 10 == 0) {
                    dControl(LogTags.SCRCPY_CLIENT) { "Control flow keepalive sent: count=$keepaliveSentCount, port=$localPort" }
                }
            }
        } catch (e: CancellationException) {
            throw e
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
        val message =
            if (isKeepalive) "Control keepalive send failed: ${e.message}" else "Socket send failed: ${e.message}"
        LogManager.e(LogTags.SCRCPY_CLIENT, message)
        issueTracker.record("control.write", e.message ?: "Unknown control socket write error")
        output = null
        outputSocket = null
        touchTiming.clear()
        clearControlSocket()
    }

    // This receiver is launched only on receiverScope, whose dispatcher is Dispatchers.IO.
    private suspend fun receiveDeviceMessages(deviceId: String) {
        var activeChannel: Pair<Socket, DataInputStream>? = null
        while (currentCoroutineContext().isActive) {
            val socket = getControlSocket()
            if (socket == null || socket.isClosed || !socket.isConnected) {
                activeChannel = null
                delay(CONTROL_SOCKET_POLL_INTERVAL_MS.milliseconds)
                continue
            }

            val input =
                activeChannel
                    ?.takeIf { channel -> channel.first === socket }
                    ?.second
                    ?: run {
                        // 握手完成后的 control 通道允许长期没有设备消息，不能沿用建链阶段的读超时。
                        // 停止/重连通过关闭 socket 唤醒阻塞读取。
                        configureControlSocketForStreaming(socket)
                        DataInputStream(socket.getInputStream()).also { stream ->
                            activeChannel = socket to stream
                        }
                    }

            try {
                when (val message = ScrcpyDeviceMessageReader.read(input)) {
                    is ScrcpyDeviceMessage.Clipboard -> onClipboardReceived(message.text)
                    is ScrcpyDeviceMessage.ClipboardAck -> Unit
                    is ScrcpyDeviceMessage.UhidOutput -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EOFException) {
                if (currentCoroutineContext().isActive && getControlSocket() === socket) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "The control message receiving stream is closed: $deviceId")
                    clearControlSocket()
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive && getControlSocket() === socket) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "Control message reception failed: ${e.message}")
                    issueTracker.record("control.read", e.message ?: "Unknown control socket read error")
                    clearControlSocket()
                }
            }
        }
    }

    private fun configureSenderThreadPriority(gameMode: Boolean) {
        val priority =
            if (gameMode) {
                Process.THREAD_PRIORITY_DISPLAY
            } else {
                Process.THREAD_PRIORITY_DEFAULT
            }
        runCatching { Process.setThreadPriority(priority) }
            .onFailure { error ->
                LogManager.w(LogTags.SCRCPY_CLIENT, "Failed to set control flow thread priority: ${error.message}")
            }
    }

    private fun nanosToDelayMillis(nanos: Long): Long =
        if (nanos <= 0L) {
            0L
        } else {
            (nanos + NANOS_PER_MILLISECOND - 1L) / NANOS_PER_MILLISECOND
        }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L

        // INJECT_TEXT is capped at 300 UTF-8 bytes; all other buffered packets are smaller.
        const val MAX_BUFFERED_CONTROL_MESSAGE_SIZE = 512
        const val CONTROL_SOCKET_POLL_INTERVAL_MS = 50L
    }
}

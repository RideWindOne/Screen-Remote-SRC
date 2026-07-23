package com.screen.remote.android.infrastructure.scrcpy.session

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionRuntimeState
import com.screen.remote.android.infrastructure.scrcpy.session.internal.processEvent
import com.screen.remote.android.infrastructure.scrcpy.session.internal.stopMonitor
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import com.screen.remote.android.infrastructure.scrcpy.session.model.DecoderResolutionRecoveryRequest
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.monitor.ScrcpyMonitorBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 单个运行中会话。
 *
 * 该对象同时承载：
 * - 持久化配置快照
 * - 运行态资源引用
 * - 会话状态和组件状态
 * - 会话域事件入口
 */
class Session(
    private var _options: ScrcpyOptions,
    internal val storage: SessionStorage,
    val onVideoResolution: (Int, Int) -> Unit,
    private val onDecoderResolutionRecoveryRequest: (DecoderResolutionRecoveryRequest?) -> Unit,
) {
    private val optionsLock = Any()
    private val rejectedDecoderLock = Any()
    private val runtimeRejectedDecoderNamesByKey = mutableMapOf<String, MutableSet<String>>()
    private val eventScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventChannel = Channel<QueuedSessionEvent>(Channel.UNLIMITED)
    internal val runtime = SessionRuntimeState()
    internal var adbConnection: AdbConnection? = null
    internal var monitorBus: ScrcpyMonitorBus? = null
    private var pendingDecoderResolutionRecovery: DecoderResolutionRecoveryRequest? = null

    init {
        eventScope.launch {
            for (queuedEvent in eventChannel) {
                try {
                    processEvent(queuedEvent.event)
                } catch (e: Exception) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "Handling event exception: ${e.message}", e)
                } finally {
                    queuedEvent.processed?.complete(Unit)
                }
            }
        }
    }

    val options: ScrcpyOptions
        get() = synchronized(optionsLock) { _options }

    val sessionId: String
        get() = _options.sessionId

    val deviceIdentifier: String
        get() = adbConnection?.deviceId ?: options.getDeviceIdentifier()

    val sessionState: StateFlow<SessionState> = runtime.sessionState
    val componentSnapshot: StateFlow<SessionComponentStateSnapshot> = runtime.componentSnapshot

    internal fun setOptions(options: ScrcpyOptions) {
        synchronized(optionsLock) {
            _options = options
        }
    }

    internal fun updateOptionsInMemory(update: (ScrcpyOptions) -> ScrcpyOptions): ScrcpyOptions =
        synchronized(optionsLock) {
            update(_options).also { _options = it }
        }

    internal fun runtimeRejectedDecoders(key: String): Set<String> =
        synchronized(rejectedDecoderLock) {
            runtimeRejectedDecoderNamesByKey[key]?.toSet().orEmpty()
        }

    internal fun rememberRuntimeRejectedDecoder(
        key: String,
        decoderName: String,
    ) {
        if (key.isBlank() || decoderName.isBlank()) return
        synchronized(rejectedDecoderLock) {
            runtimeRejectedDecoderNamesByKey.getOrPut(key) { linkedSetOf() } += decoderName
        }
    }

    internal fun persistOptionsInBackground(update: (ScrcpyOptions) -> ScrcpyOptions) {
        val targetSessionId = sessionId
        eventScope.launch {
            runCatching {
                storage.updateOptions(targetSessionId, update)
            }.onFailure { error ->
                LogManager.w(
                    LogTags.SCRCPY_CLIENT,
                    "Failed to save session configuration in the background: sessionId=$targetSessionId, ${error.message}",
                )
            }
        }
    }

    internal fun publishDecoderResolutionRecovery(request: DecoderResolutionRecoveryRequest) {
        pendingDecoderResolutionRecovery = request
        onDecoderResolutionRecoveryRequest(request)
    }

    internal fun consumeDecoderResolutionRecovery(): DecoderResolutionRecoveryRequest? =
        pendingDecoderResolutionRecovery.also {
            pendingDecoderResolutionRecovery = null
            onDecoderResolutionRecoveryRequest(null)
        }

    internal fun clearDecoderResolutionRecovery() {
        pendingDecoderResolutionRecovery = null
        onDecoderResolutionRecoveryRequest(null)
    }

    internal fun hasPendingDecoderResolutionRecovery(): Boolean =
        pendingDecoderResolutionRecovery != null

    fun handleEvent(event: SessionEvent) {
        if (eventChannel.trySend(QueuedSessionEvent(event)).isFailure) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "Session stopped, event ignored: ${event::class.simpleName}")
        }
    }

    internal suspend fun handleEventAndWait(event: SessionEvent) {
        val processed = CompletableDeferred<Unit>()
        eventChannel.send(QueuedSessionEvent(event, processed))
        processed.await()
    }

    internal fun cleanup() {
        try {
            stopMonitor()
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "Failed to stop monitor: ${e.message}")
        }
        try {
            monitorBus?.stop()
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "Failed to stop monitoring bus: ${e.message}")
        }
        monitorBus = null
        adbConnection = null
        clearDecoderResolutionRecovery()
        synchronized(rejectedDecoderLock) { runtimeRejectedDecoderNamesByKey.clear() }
        eventChannel.close()
        eventScope.cancel()
    }

    private data class QueuedSessionEvent(
        val event: SessionEvent,
        val processed: CompletableDeferred<Unit>? = null,
    )
}

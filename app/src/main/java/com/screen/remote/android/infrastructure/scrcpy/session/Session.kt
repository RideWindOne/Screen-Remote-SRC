package com.screen.remote.android.infrastructure.scrcpy.session

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.data.storage.SessionStorage
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.EncoderDetectionResult
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionComponentStateStore
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionResourceFacade
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionResourceRegistry
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionRuntimeFacade
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionRuntimeBindings
import com.screen.remote.android.infrastructure.scrcpy.session.internal.SessionStateStore
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionState
import com.screen.remote.android.infrastructure.scrcpy.session.monitor.ScrcpyMonitorBus
import com.screen.remote.android.infrastructure.scrcpy.session.internal.processEvent
import com.screen.remote.android.infrastructure.scrcpy.session.internal.stopMonitor
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionComponentStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val storage: SessionStorage,
    val onVideoResolution: (Int, Int) -> Unit,
) {
    private val stateStore = SessionStateStore()
    private val componentStateStore = SessionComponentStateStore()
    private val runtimeBindings = SessionRuntimeBindings()
    private val resourceRegistry = SessionResourceRegistry(storage)
    private val eventScope = CoroutineScope(Dispatchers.IO)
    internal val runtime = SessionRuntimeFacade(stateStore, componentStateStore, runtimeBindings)
    internal val resources = SessionResourceFacade(resourceRegistry)

    val options: ScrcpyOptions
        get() = _options

    val sessionId: String
        get() = _options.sessionId

    val deviceIdentifier: String
        get() = _options.getDeviceIdentifier()

    var adbConnection: AdbConnection?
        get() = resources.adbConnection
        set(value) {
            resources.adbConnection = value
        }

    var codecInfo: EncoderDetectionResult?
        get() = resources.codecInfo
        set(value) {
            resources.codecInfo = value
        }

    val sessionState: StateFlow<SessionState> = runtime.sessionState
    val componentSnapshot: StateFlow<SessionComponentStateSnapshot> = runtime.componentSnapshot

    var monitorBus: ScrcpyMonitorBus?
        get() = resources.monitorBus
        set(value) {
            resources.monitorBus = value
        }

    internal fun setOptions(options: ScrcpyOptions) {
        _options = options
    }

    internal fun storage(): SessionStorage = resources.storage()

    fun handleEvent(event: SessionEvent) {
        eventScope.launch {
            try {
                processEvent(event)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "处理事件异常: ${e.message}", e)
            }
        }
    }

    internal fun cleanup() {
        try {
            stopMonitor()
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "停止监控器失败: ${e.message}")
        }
        resources.stopMonitorBus()
        resources.clearRuntimeResources()
    }
}

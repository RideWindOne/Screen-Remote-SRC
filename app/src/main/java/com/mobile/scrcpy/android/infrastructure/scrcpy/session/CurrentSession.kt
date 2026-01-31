package com.mobile.scrcpy.android.infrastructure.scrcpy.session

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.data.storage.SessionStorage
import com.mobile.scrcpy.android.core.domain.model.ConnectionStep
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.core.domain.model.StepStatus
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderDetectionResult
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionStateMachine
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.processEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.stopMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 当前会话管理 - 全局单例
 *
 * ## 职责
 * - 管理当前活跃会话（配置 + 运行态）
 * - 提供零参数访问接口
 * - 自动切换会话
 * - 严格隔离会话
 *
 * ## 特性
 * - 同时只有一个活跃会话
 * - 零参数访问（CurrentSession.current）
 * - 启动新会话自动停止旧会话
 *
 * ## 文件拆分说明
 * 为提高代码可维护性，原 717 行的 CurrentSession.kt 已按功能拆分为多个文件：
 *
 * ### 核心文件（本文件）
 * - **CurrentSession.kt** (~150 行)
 *   - CurrentSession object（全局单例）
 *   - Session 类定义和公开 API
 *   - 配置访问方法（options, sessionId, deviceIdentifier）
 *   - 运行态属性（adbConnection, codecInfo, monitorBus）
 *   - 会话状态管理（sessionState, componentStates）
 *   - Internal 辅助方法（供扩展函数使用）
 *
 * ### 内部实现文件（internal/ 目录）
 * - **internal/SessionEventHandlers.kt** (~400 行)
 *   - 所有事件处理方法（handleXxx）
 *   - 事件处理的辅助方法
 *   - 组件状态管理
 *   - 使用扩展函数：`Session.processEvent(event)`
 *
 * - **internal/SessionConfigManager.kt** (~100 行)
 *   - 配置更新方法：`Session.updateOptions(update)`
 *   - 设备序列号更新：`Session.updateDeviceSerial(newSerial)`
 *   - 编解码器配置保存：`Session.saveCodecDetectionResult(...)`
 *   - 使用扩展函数模式
 *
 * - **internal/SessionMonitor.kt** (~70 行)
 *   - 监控器初始化：`Session.initMonitor(stateMachine, onReconnect)`
 *   - 监控总线管理：`Session.createMonitorBus()`
 *   - 监控器停止：`Session.stopMonitor()`
 *   - 使用扩展函数模式
 *
 * ## 设计模式
 * - **扩展函数模式**：内部实现使用扩展函数，保持 Session 类简洁
 * - **Internal 可见性**：内部实现标记为 internal，避免暴露给外部
 * - **单一职责**：每个文件专注于特定功能领域
 *
 * ## 使用示例
 * ```kotlin
 * // 启动会话
 * CurrentSession.start(options, storage) { width, height ->
 *     // 处理视频分辨率
 * }
 *
 * // 访问当前会话（零参数）
 * val session = CurrentSession.current
 * val options = session.options
 * val connection = session.adbConnection
 *
 * // 更新配置（委托给 SessionConfigManager）
 * session.updateOptions { it.copy(maxSize = 1080) }
 *
 * // 处理事件（委托给 SessionEventHandlers）
 * session.handleEvent(SessionEvent.AdbConnected)
 *
 * // 初始化监控（委托给 SessionMonitor）
 * session.initMonitor(stateMachine) { /* 重连逻辑 */ }
 *
 * // 停止会话
 * CurrentSession.stop()
 * ```
 *
 * @see com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.processEvent
 * @see com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.updateOptions
 * @see com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.initMonitor
 */
object CurrentSession {
    @Volatile
    private var currentSession: Session? = null

    /**
     * 获取当前活跃会话
     * @throws IllegalStateException 如果会话不存在
     */
    val current: Session
        get() = currentSession ?: error("当前没有活跃会话，请先调用 CurrentSession.start()")

    /**
     * 获取当前活跃会话（可空）
     */
    val currentOrNull: Session?
        get() = currentSession

    /**
     * 启动会话（自动停止旧会话）
     */
    fun start(
        options: ScrcpyOptions,
        storage: SessionStorage,
        onVideoResolution: (Int, Int) -> Unit = { _, _ -> },
    ): Session {
        // 如果已存在，先清理
        currentSession?.let {
            LogManager.w(LogTags.SCRCPY_CLIENT, "会话已存在，先清理: ${it.deviceIdentifier}")
            stop()
        }

        val session = Session(options, storage, onVideoResolution)
        currentSession = session
        return session
    }

    /**
     * 停止当前会话
     */
    fun stop() {
        currentSession?.let {
            LogManager.d(LogTags.SCRCPY_CLIENT, "停止会话: ${it.deviceIdentifier}, sessionId=${it.sessionId}")
            it.cleanup()
            currentSession = null
        }
    }

    /**
     * 检查会话是否存在
     */
    fun exists(): Boolean = currentSession != null

    /**
     * 获取当前设备标识（便捷方法）
     */
    val deviceIdentifier: String?
        get() = currentSession?.deviceIdentifier

    /**
     * 获取当前会话 ID（便捷方法）
     */
    val sessionId: String?
        get() = currentSession?.sessionId
}

/**
 * 会话实例 - 包含单个会话的配置和运行态
 *
 * ## 设计原则
 * - **配置与运行态分离**：配置通过 ScrcpyOptions 管理，运行态在会话期间持有
 * - **零参数访问**：通过 CurrentSession.current 访问，无需传递 sessionId
 * - **自动持久化**：配置更新自动保存到 SessionStorage
 * - **事件驱动**：通过 handleEvent() 统一处理所有会话事件
 *
 * ## 核心属性
 *
 * ### 配置访问（只读）
 * - **options**: ScrcpyOptions - 当前配置（包含 host、port、编解码器等）
 * - **sessionId**: String - 会话唯一标识
 * - **deviceIdentifier**: String - 设备标识（便捷访问）
 *
 * ### 运行态（可变）
 * - **adbConnection**: AdbConnection? - ADB 连接实例（连接建立时赋值）
 * - **codecInfo**: EncoderDetectionResult? - 编解码器检测结果
 * - **monitorBus**: ScrcpyMonitorBus? - 监控总线
 *
 * ### 状态管理
 * - **sessionState**: StateFlow<SessionState> - 会话状态（响应式）
 * - **componentStates**: Map<SessionComponent, ComponentState> - 组件状态
 *
 * ## 公开 API 方法
 *
 * ### 配置管理（委托给 SessionConfigManager）
 * - `updateOptions(update)` - 更新配置并自动保存
 * - `updateDeviceSerial(newSerial)` - 更新设备序列号（自动清空设备能力）
 * - `saveCodecDetectionResult(...)` - 保存编解码器检测结果
 * - `saveCodecSelection(...)` - 保存用户手动选择的编解码器
 *
 * ### 事件处理（委托给 SessionEventHandlers）
 * - `handleEvent(event)` - 处理会话事件（统一入口）
 *
 * ### 监控管理（委托给 SessionMonitor）
 * - `createMonitorBus()` - 创建监控总线
 * - `initMonitor(stateMachine, onReconnect)` - 初始化监控器
 * - `stopMonitor()` - 停止监控器
 *
 * ### 状态查询
 * - `getCurrentState()` - 获取当前会话状态
 * - `getComponentState(component)` - 获取组件状态
 *
 * ## Internal 方法
 * 以下方法仅供内部扩展函数使用，不应在外部调用：
 * - `setOptions(options)` - 设置配置（供 SessionConfigManager 使用）
 * - `getStorage()` - 获取存储（供 SessionConfigManager 使用）
 * - `updateProgress(step, status, message)` - 更新进度文本
 * - `updateSessionState(state)` - 更新会话状态
 * - `updateComponentState(component, state)` - 更新组件状态
 * - `clearComponentStates()` - 清空组件状态
 * - `getReconnectAttempts()` - 获取重连次数
 * - `incrementReconnectAttempts()` - 增加重连次数
 * - `resetReconnectAttempts()` - 重置重连次数
 * - `invokeReconnectCallback()` - 调用重连回调
 * - `setStateMachineInternal(stateMachine)` - 设置 StateMachine
 * - `setReconnectCallbackInternal(callback)` - 设置重连回调
 *
 * ## 使用示例
 * ```kotlin
 * // 通过 CurrentSession 访问
 * val session = CurrentSession.current
 *
 * // 配置管理
 * session.updateOptions { it.copy(maxSize = 1080) }
 * session.updateDeviceSerial("192.168.1.100:5555")
 *
 * // 事件处理
 * session.handleEvent(SessionEvent.AdbConnected)
 * session.handleEvent(SessionEvent.ServerStarted)
 *
 * // 监控管理
 * session.createMonitorBus()
 * session.initMonitor(stateMachine) { /* 重连逻辑 */ }
 *
 * // 状态查询
 * val state = session.getCurrentState()
 * val adbState = session.getComponentState(SessionComponent.AdbConnection)
 * ```
 *
 * @property options 当前配置（只读）
 * @property sessionId 会话唯一标识
 * @property deviceIdentifier 设备标识
 * @property adbConnection ADB 连接实例
 * @property codecInfo 编解码器检测结果
 * @property monitorBus 监控总线
 * @property sessionState 会话状态（响应式）
 * @property onVideoResolution 视频分辨率回调
 */
class Session(
    private var _options: ScrcpyOptions,
    private val storage: SessionStorage,
    val onVideoResolution: (Int, Int) -> Unit,
) {
    // ========== 配置访问（只读，供外部使用） ==========

    /**
     * 当前配置（只读）
     * 包含所有会话配置：host、port、编解码器、显示设置等
     */
    val options: ScrcpyOptions
        get() = _options

    /**
     * 会话 ID
     */
    val sessionId: String
        get() = _options.sessionId

    /**
     * 设备标识（便捷访问）
     */
    val deviceIdentifier: String
        get() = _options.getDeviceIdentifier()

    // ========== 运行态（连接期间持有） ==========

    /**
     * ADB 连接实例
     * 在连接建立时赋值，断开时清空
     */
    var adbConnection: AdbConnection? = null

    /**
     * 编码器检测结果
     * 包含远程设备支持的编解码器列表
     */
    var codecInfo: EncoderDetectionResult? = null

    // ========== 会话状态管理 ==========

    /**
     * 会话状态（响应式）
     */
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * 组件状态
     */
    internal val componentStates = mutableMapOf<SessionComponent, ComponentState>()

    /**
     * 重连计数
     */
    private var reconnectAttempts = 0

    /**
     * 重连回调
     */
    private var onReconnectRequest: (() -> Unit)? = null

    /**
     * StateMachine（用于更新进度文本）
     */
    private var stateMachine: ConnectionStateMachine? = null

    /**
     * 事件处理协程
     */
    private val eventScope = CoroutineScope(Dispatchers.IO)

    /**
     * 监控总线
     */
    var monitorBus: ScrcpyMonitorBus? = null

    // ========== 配置更新（委托给 SessionConfigManager） ==========
    // 公开方法：updateOptions(), updateDeviceSerial(), saveCodecDetectionResult(), saveCodecSelection()
    // 实现位置：internal/SessionConfigManager.kt

    /**
     * 内部方法：设置配置（供扩展函数使用）
     * 仅供 SessionConfigManager 内部使用，外部请使用 updateOptions()
     */
    internal fun setOptions(options: ScrcpyOptions) {
        _options = options
    }

    /**
     * 内部方法：获取存储（供扩展函数使用）
     * 仅供 SessionConfigManager 内部使用
     */
    internal fun getStorage(): SessionStorage = storage

    // ========== 监控总线（委托给 SessionMonitor） ==========
    // 公开方法：createMonitorBus(), initMonitor(), stopMonitor()
    // 实现位置：internal/SessionMonitor.kt

    /**
     * Internal 方法：设置 StateMachine（供 SessionMonitor 使用）
     * 仅供 SessionMonitor 内部使用
     */
    internal fun setStateMachineInternal(stateMachine: ConnectionStateMachine?) {
        this.stateMachine = stateMachine
    }

    /**
     * Internal 方法：设置重连回调（供 SessionMonitor 使用）
     * 仅供 SessionMonitor 内部使用
     */
    internal fun setReconnectCallbackInternal(callback: (() -> Unit)?) {
        this.onReconnectRequest = callback
    }

    /**
     * 处理事件（统一入口）
     *
     * 所有会话事件通过此方法处理，实际处理逻辑委托给 SessionEventHandlers
     *
     * @param event 会话事件（ADB、Server、Socket、Decoder 等）
     * @see com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.processEvent
     */
    fun handleEvent(event: SessionEvent) {
        eventScope.launch {
            try {
                processEvent(event)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "处理事件异常: ${e.message}", e)
            }
        }
    }

    // ========== Internal 辅助方法（供事件处理器使用） ==========
    // 以下方法仅供 internal/ 目录下的扩展函数使用
    // 外部代码不应直接调用这些方法

    /**
     * 更新进度文本
     * 供 SessionEventHandlers 使用，更新 ConnectionStateMachine 的进度显示
     */
    internal fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String,
    ) {
        stateMachine?.updateProgress(step, status, message)
    }

    /**
     * 更新会话状态
     * 供 SessionEventHandlers 使用，更新响应式状态流
     */
    internal fun updateSessionState(state: SessionState) {
        _sessionState.value = state
    }

    /**
     * 更新组件状态
     * 供 SessionEventHandlers 使用，跟踪各组件（ADB、Socket、Decoder 等）的状态
     */
    internal fun updateComponentState(
        component: SessionComponent,
        state: ComponentState,
    ) {
        componentStates[component] = state
    }

    /**
     * 清空组件状态
     * 供 SessionEventHandlers 使用，在会话清理时重置所有组件状态
     */
    internal fun clearComponentStates() {
        componentStates.clear()
    }

    /**
     * 获取重连次数
     * 供 SessionEventHandlers 使用，判断是否达到重连上限
     */
    internal fun getReconnectAttempts(): Int = reconnectAttempts

    /**
     * 增加重连次数
     * 供 SessionEventHandlers 使用，在触发重连时递增计数
     */
    internal fun incrementReconnectAttempts() {
        reconnectAttempts++
    }

    /**
     * 重置重连次数
     * 供 SessionEventHandlers 使用，在会话清理或连接成功时重置
     */
    internal fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }

    /**
     * 调用重连回调
     * 供 SessionEventHandlers 使用，触发重连逻辑
     */
    internal fun invokeReconnectCallback() {
        onReconnectRequest?.invoke()
    }

    /**
     * 获取当前会话状态
     *
     * @return 当前会话状态（Idle、Connecting、Connected 等）
     */
    fun getCurrentState(): SessionState = _sessionState.value

    /**
     * 获取组件状态
     *
     * @param component 组件类型（AdbConnection、VideoSocket、VideoDecoder 等）
     * @return 组件状态，如果组件未初始化则返回 null
     */
    fun getComponentState(component: SessionComponent): ComponentState? = componentStates[component]

    /**
     * 清理资源
     *
     * 在会话停止时调用，清理所有运行态资源：
     * - 停止监控器
     * - 停止监控总线
     * - 清空 ADB 连接
     * - 清空编解码器信息
     */
    internal fun cleanup() {
        try {
            stopMonitor()
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "停止监控器失败: ${e.message}")
        }
        try {
            monitorBus?.stop()
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "停止监控总线失败: ${e.message}")
        }
        adbConnection = null
        codecInfo = null
        monitorBus = null
    }
}

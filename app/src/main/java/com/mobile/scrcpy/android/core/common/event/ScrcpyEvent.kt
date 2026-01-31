package com.mobile.scrcpy.android.core.common.event

/**
 * Scrcpy 事件系统 - 统一事件定义
 *
 * 事件分类：
 * 1. UI 事件 - 用户交互、输入、窗口操作 (ScrcpyUIEvents.kt)
 * 2. 监控事件 - 系统状态、性能指标、资源使用 (ScrcpyMonitorEvents.kt)
 * 3. 生命周期事件 - 连接、断开、启动、停止 (ScrcpyLifecycleEvents.kt)
 * 4. 媒体事件 - 视频/音频帧处理 (ScrcpyMediaEvents.kt)
 * 5. 系统事件 - 错误、异常、任务执行 (ScrcpySystemEvents.kt)
 *
 * 日志级别：
 * - VERBOSE: 高频事件（视频帧、Socket 数据）
 * - DEBUG: 调试事件（命令执行、状态变化）
 * - INFO: 重要事件（连接建立、设备状态）
 * - WARN: 警告事件（超时、重试、降级）
 * - ERROR: 错误事件（异常、失败）
 *
 * 参考：docs/EVENT_SYSTEM_GUIDE.md
 */

// 重新导出所有事件类型，保持向后兼容
typealias ScrcpyEventQuit = Quit
typealias ScrcpyEventServerConnected = ServerConnected
typealias ScrcpyEventServerConnectionFailed = ServerConnectionFailed
typealias ScrcpyEventDeviceDisconnected = DeviceDisconnected
typealias ScrcpyEventUsbDeviceDisconnected = UsbDeviceDisconnected
typealias ScrcpyEventConnectionEstablished = ConnectionEstablished
typealias ScrcpyEventConnectionLost = ConnectionLost
typealias ScrcpyEventStatusChanged = StatusChanged
typealias ScrcpyEventError = Error

typealias ScrcpyEventKeyDown = KeyDown
typealias ScrcpyEventKeyUp = KeyUp
typealias ScrcpyEventMouseMotion = MouseMotion
typealias ScrcpyEventMouseButtonDown = MouseButtonDown
typealias ScrcpyEventMouseButtonUp = MouseButtonUp
typealias ScrcpyEventTouchDown = TouchDown
typealias ScrcpyEventTouchMove = TouchMove
typealias ScrcpyEventTouchUp = TouchUp
typealias ScrcpyEventScroll = Scroll
typealias ScrcpyEventClipboardUpdate = ClipboardUpdate

typealias ScrcpyEventNewFrame = NewFrame
typealias ScrcpyEventScreenInitSize = ScreenInitSize
typealias ScrcpyEventVideoFrameDecoded = VideoFrameDecoded
typealias ScrcpyEventAudioFrameDecoded = AudioFrameDecoded
typealias ScrcpyEventVideoDecoderStalled = VideoDecoderStalled
typealias ScrcpyEventAudioDecoderStalled = AudioDecoderStalled

typealias ScrcpyEventShellCommandExecuted = ShellCommandExecuted
typealias ScrcpyEventShellCommandFailed = ShellCommandFailed
typealias ScrcpyEventForwardSetup = ForwardSetup
typealias ScrcpyEventForwardRemoved = ForwardRemoved
typealias ScrcpyEventFilePushSuccess = FilePushSuccess
typealias ScrcpyEventFilePushFailed = FilePushFailed
typealias ScrcpyEventAdbVerifying = AdbVerifying
typealias ScrcpyEventAdbVerifySuccess = AdbVerifySuccess
typealias ScrcpyEventAdbVerifyFailed = AdbVerifyFailed
typealias ScrcpyEventServerLog = ServerLog
typealias ScrcpyEventSocketDataReceived = SocketDataReceived
typealias ScrcpyEventSocketDataSent = SocketDataSent
typealias ScrcpyEventSocketIdle = SocketIdle
typealias ScrcpyEventDeviceScreenLocked = DeviceScreenLocked
typealias ScrcpyEventDeviceScreenUnlocked = DeviceScreenUnlocked
typealias ScrcpyEventDeviceScreenOff = DeviceScreenOff
typealias ScrcpyEventDeviceScreenOn = DeviceScreenOn
typealias ScrcpyEventMonitorException = MonitorException

typealias ScrcpyEventDemuxerError = DemuxerError
typealias ScrcpyEventRecorderError = RecorderError
typealias ScrcpyEventControllerError = ControllerError
typealias ScrcpyEventAoaOpenError = AoaOpenError
typealias ScrcpyEventTimeLimitReached = TimeLimitReached
typealias ScrcpyEventRunOnMainThread = RunOnMainThread

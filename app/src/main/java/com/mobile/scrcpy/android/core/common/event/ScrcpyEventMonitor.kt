package com.mobile.scrcpy.android.core.common.event

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

/**
 * Scrcpy 事件监控器
 * 
 * 监听 ScrcpyEventBus 的监控事件，自动更新设备状态并输出日志
 * 
 * 作用域：会话级监控器，与 ScrcpyEventBus 配合使用
 * 生命周期：在 Application.onCreate 启动，应用退出时停止
 * 关系定位：作为 ScrcpyEventBus 的消费者，自动处理监控类事件
 */
object ScrcpyEventMonitor {
    private var isMonitoring = false

    /**
     * 启动监控
     */
    fun start() {
        if (isMonitoring) return
        isMonitoring = true

        // 注册监控事件处理器
        registerMonitorHandlers()
        
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, "事件监控器已启动")
    }

    /**
     * 停止监控
     */
    fun stop() {
        isMonitoring = false
        LogManager.i(LogTags.SCRCPY_EVENT_BUS, "事件监控器已停止")
    }

    /**
     * 注册监控事件处理器
     */
    private fun registerMonitorHandlers() {
        // Shell 命令执行
        ScrcpyEventBus.on<ShellCommandExecuted> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.shellCommandCount++
            state.lastShellCommand = event.command
        }
        
        ScrcpyEventBus.on<ShellCommandFailed> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.shellCommandFailCount++
        }
        
        // ADB Forward 操作
        ScrcpyEventBus.on<ForwardSetup> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.forwardSetupCount++
            
            if (!event.success) {
                state.forwardSetupFailCount++
            }
        }
        
        ScrcpyEventBus.on<ForwardRemoved> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.forwardRemoveCount++
        }
        
        // ADB 文件推送
        ScrcpyEventBus.on<FilePushSuccess> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.filePushCount++
            state.filePushTotalBytes += event.fileSize
            state.lastFilePushPath = event.remotePath
        }
        
        ScrcpyEventBus.on<FilePushFailed> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.filePushFailCount++
        }
        
        // ADB 授权验证
        ScrcpyEventBus.on<AdbVerifySuccess> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.adbVerifyCount++
        }
        
        ScrcpyEventBus.on<AdbVerifyFailed> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.adbVerifyFailCount++
        }
        
        // Server 日志
        ScrcpyEventBus.on<ServerLog> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.serverLogCount++
            state.lastServerLog = event.message
        }

        // Socket 数据接收
        ScrcpyEventBus.on<SocketDataReceived> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            val stats = state.socketStats.getOrPut(event.socketType) { SocketStats() }
            stats.bytesReceived += event.bytesCount
            stats.packetsReceived++
        }

        // Socket 数据发送
        ScrcpyEventBus.on<SocketDataSent> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            val stats = state.socketStats.getOrPut(event.socketType) { SocketStats() }
            stats.bytesSent += event.bytesCount
            stats.packetsSent++
        }

        // Socket 空闲
        ScrcpyEventBus.on<SocketIdle> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            val stats = state.socketStats.getOrPut(event.socketType) { SocketStats() }
            stats.idleCount++
        }

        // 视频帧解码
        ScrcpyEventBus.on<VideoFrameDecoded> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.videoFrameCount++
            state.isVideoActive = true
        }

        // 音频帧解码
        ScrcpyEventBus.on<AudioFrameDecoded> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.audioFrameCount++
            state.isAudioActive = true
        }

        // 视频解码器停滞
        ScrcpyEventBus.on<VideoDecoderStalled> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isVideoActive = false
            state.videoStallCount++
        }

        // 音频解码器停滞
        ScrcpyEventBus.on<AudioDecoderStalled> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isAudioActive = false
            state.audioStallCount++
        }

        // 设备锁屏
        ScrcpyEventBus.on<DeviceScreenLocked> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isScreenLocked = true
        }

        // 设备解锁
        ScrcpyEventBus.on<DeviceScreenUnlocked> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isScreenLocked = false
        }

        // 设备息屏
        ScrcpyEventBus.on<DeviceScreenOff> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isScreenOn = false
        }

        // 设备亮屏
        ScrcpyEventBus.on<DeviceScreenOn> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isScreenOn = true
        }

        // 连接建立
        ScrcpyEventBus.on<ConnectionEstablished> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isConnected = true
        }

        // 连接丢失
        ScrcpyEventBus.on<ConnectionLost> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.isConnected = false
            state.disconnectionReason = event.reason
        }

        // 异常
        ScrcpyEventBus.on<MonitorException> { event ->
            val state = ScrcpyEventBus.getDeviceState(event.deviceId)
            state.recentExceptions.add(
                ExceptionRecord(
                    type = event.type,
                    message = event.message,
                ),
            )
            // 只保留最近 20 条
            if (state.recentExceptions.size > 20) {
                state.recentExceptions.removeAt(0)
            }
        }
    }
}

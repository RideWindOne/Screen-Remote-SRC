package com.screen.remote.android.infrastructure.scrcpy.client

import com.screen.remote.android.core.common.event.ScrcpyEventBus
import com.screen.remote.android.infrastructure.media.audio.AudioStream
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionHealthMonitor
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionShellMonitor
import com.screen.remote.android.infrastructure.scrcpy.controller.ScrcpyController
import com.screen.remote.android.infrastructure.scrcpy.protocol.VideoStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Scrcpy 客户端清理逻辑
 */
internal object ScrcpyClientCleanup {
    /**
     * 会话运行时清理：用于结束会话或取消建链。
     * 清理顺序：解码器 → Socket → Forward → Server → 控制器 → Shell监控 → 健康监控 → 会话/事件系统
     *
     * 注意：这里不主动断开健康的 ADB 连接，ADB 连接保活与失效摘除由 ADB 层管理。
     */
    suspend fun cleanupSessionRuntime(
        videoStreamState: MutableStateFlow<VideoStream?>,
        audioStreamState: MutableStateFlow<AudioStream?>,
        lifecycle: ConnectionLifecycle,
        controller: ScrcpyController,
        shellMonitor: ConnectionShellMonitor,
        healthMonitor: ConnectionHealthMonitor,
        videoResolution: MutableStateFlow<Pair<Int, Int>?>,
        deviceId: String?,
        sessionManager: com.screen.remote.android.infrastructure.scrcpy.session.SessionManager,
    ) = coroutineScope {
        // 第一阶段：先停健康监控，避免本地关闭流/Socket 时被误判成连接丢失
        healthMonitor.stopMonitoring()

        // 第二阶段：停止解码器和流（优先级最高，避免读取错误）
        videoStreamState.value?.close()
        audioStreamState.value?.close()
        videoStreamState.value = null
        audioStreamState.value = null
        delay(50) // 等待解码器完全停止

        // 第三阶段：断开连接（Socket、Forward、Server）
        lifecycle.disconnect()
        delay(50)

        // 第四阶段：停止控制器和监控
        val componentJobs =
            listOf(
                async { controller.stop() },
                async { shellMonitor.stopMonitor() },
            )
        componentJobs.awaitAll()

        // 第五阶段：清理会话和事件系统
        deviceId?.let {
            val cleanupJobs =
                listOf(
                    async { sessionManager.stop() },
                    async { ScrcpyEventBus.cleanup() },
                )
            cleanupJobs.awaitAll()
        }

        // 最后：清理状态
        videoResolution.value = null
    }

    /**
     * ADB 异常清理：ADB 连接丢失时使用
     * 清理：ADB、Server、Socket、健康监控
     * 保留：SDL 事件系统、会话监控器、Shell 监控、Controller（用于重连）
     */
    suspend fun cleanupOnAdbError(
        videoStreamState: MutableStateFlow<VideoStream?>,
        audioStreamState: MutableStateFlow<AudioStream?>,
        lifecycle: ConnectionLifecycle,
        healthMonitor: ConnectionHealthMonitor,
    ) = coroutineScope {
        // 并行清理
        val jobs =
            listOf(
                async { healthMonitor.stopMonitoring() },
                async { lifecycle.disconnect() },
            )

        jobs.awaitAll()

        // 清理流状态
        videoStreamState.value = null
        audioStreamState.value = null
    }
}

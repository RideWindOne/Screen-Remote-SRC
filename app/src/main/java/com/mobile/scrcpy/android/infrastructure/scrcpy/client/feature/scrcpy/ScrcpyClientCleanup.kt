package com.mobile.scrcpy.android.infrastructure.scrcpy.client.feature.scrcpy

import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionShellMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionHealthMonitor
import com.mobile.scrcpy.android.infrastructure.scrcpy.controller.feature.scrcpy.ScrcpyController
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.core.common.event.ScrcpyEventBus
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
     * 完整清理：断开会话时使用
     * 清理顺序：解码器 → Socket → Forward → Server → 控制器 → Shell监控 → 健康监控 → 配置 → 事件系统
     */
    suspend fun cleanupAll(
        videoStreamState: MutableStateFlow<VideoStream?>,
        audioStreamState: MutableStateFlow<AudioStream?>,
        lifecycle: ConnectionLifecycle,
        controller: ScrcpyController,
        shellMonitor: ConnectionShellMonitor,
        healthMonitor: ConnectionHealthMonitor,
        videoResolution: MutableStateFlow<Pair<Int, Int>?>,
        deviceId: String?,
    ) = coroutineScope {
        // 第一阶段：停止解码器和流（优先级最高，避免读取错误）
        videoStreamState.value?.close()
        audioStreamState.value?.close()
        videoStreamState.value = null
        audioStreamState.value = null
        delay(50) // 等待解码器完全停止

        // 第二阶段：断开连接（Socket、Forward、Server）
        lifecycle.disconnect()
        delay(50)

        // 第三阶段：停止控制器和监控
        val componentJobs =
            listOf(
                async { controller.stop() },
                async { shellMonitor.stopMonitor() },
                async { healthMonitor.stopMonitoring() },
            )
        componentJobs.awaitAll()

        // 第四阶段：清理会话和事件系统
        deviceId?.let {
            val cleanupJobs =
                listOf(
                    async { CurrentSession.stop() },
                    async { ScrcpyEventBus.cleanup() },
                )
            cleanupJobs.awaitAll()
        }

        // 最后：清理状态
        videoResolution.value = null
    }

    /**
     * 部分清理：取消连接/重连时使用
     * 清理顺序：解码器 → Socket → Forward → Server → 控制器 → Shell监控 → 健康监控 → 配置 → 事件系统
     * 保留：ADB 连接
     */
    suspend fun cleanupConnectionOnly(
        videoStreamState: MutableStateFlow<VideoStream?>,
        audioStreamState: MutableStateFlow<AudioStream?>,
        lifecycle: ConnectionLifecycle,
        controller: ScrcpyController,
        shellMonitor: ConnectionShellMonitor,
        healthMonitor: ConnectionHealthMonitor,
        videoResolution: MutableStateFlow<Pair<Int, Int>?>,
        deviceId: String?,
    ) = coroutineScope {
        // 第一阶段：停止解码器和流
        videoStreamState.value?.close()
        audioStreamState.value?.close()
        videoStreamState.value = null
        audioStreamState.value = null
        delay(50)

        // 第二阶段：断开连接（Socket、Forward、Server）
        lifecycle.disconnect()
        delay(50)

        // 第三阶段：停止控制器和监控
        val componentJobs =
            listOf(
                async { controller.stop() },
                async { shellMonitor.stopMonitor() },
                async { healthMonitor.stopMonitoring() },
            )
        componentJobs.awaitAll()

        // 第四阶段：清理会话和事件系统
        deviceId?.let {
            val cleanupJobs =
                listOf(
                    async { CurrentSession.stop() },
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

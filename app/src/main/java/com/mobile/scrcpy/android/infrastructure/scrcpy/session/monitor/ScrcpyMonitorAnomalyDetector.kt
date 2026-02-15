package com.mobile.scrcpy.android.infrastructure.scrcpy.session.monitor

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager

internal class ScrcpyMonitorAnomalyDetector(
    private val deviceId: String,
) {
    fun detect(
        state: GlobalScrcpyState,
        now: Long,
    ) {
        if (state.isScreenLocked && state.isVideoActive) {
            val timeSinceLock = now - state.screenLockTime
            if (timeSinceLock > 5000) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：锁屏后仍有视频输出（${timeSinceLock}ms）",
                )
            }
        }

        if (state.isConnected) {
            val timeSinceConnection = now - state.connectionTime
            val timeSinceLastVideo = now - state.lastVideoFrameTime

            if (timeSinceConnection > 10000 && timeSinceLastVideo > 10000 && state.videoFrameCount == 0L) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：连接后 ${timeSinceConnection}ms 无视频数据",
                )
            }
        }

        state.socketStats.forEach { (type, stats) ->
            val idleTime = now - stats.lastActivityTime
            if (idleTime > 30000 && state.isConnected) {
                LogManager.w(
                    LogTags.SCRCPY_EVENT_BUS,
                    "[$deviceId] 异常：Socket[$type] 空闲 ${idleTime}ms",
                )
            }
        }
    }
}

package com.mobile.scrcpy.android.infrastructure.adb.connection

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
/**
 * ADB 连接心跳保活管理器
 * 负责定期检测连接状态，清理失效连接
 */
internal class AdbConnectionKeepAlive(
    private val keepAliveInterval: Long = 30_000L, // 30秒心跳
    private val onConnectionFailed: (String) -> Unit // 连接失败回调
) {
    private val keepAliveScope = CoroutineScope(Dispatchers.IO)
    private var keepAliveJob: Job? = null
    
    /**
     * 启动连接保活任务
     */
    fun start(getConnections: () -> List<AdbConnection>) {
        keepAliveJob?.cancel()
        keepAliveJob = keepAliveScope.launch {
            while (isActive) {
                delay(keepAliveInterval)
                
                // 对所有连接执行心跳检测
                val failedConnections = mutableListOf<String>()
                
                getConnections().forEach { connection ->
                    val result = connection.executeShell("echo 1", retryOnFailure = false)
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        LogManager.w(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_HEARTBEAT_FAILED.get()}: ${connection.deviceId} - ${error?.message}")
                        
                        // 如果是 ECONNREFUSED，说明 ADB 已断开，标记为失效
                        if (error?.message?.contains(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get()) == true) {
                            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CONNECTION_DETECTED_DISCONNECTED.get()}: ${connection.deviceId}")
                            failedConnections.add(connection.deviceId)
                        }
                    }
                }
                
                // 通知失效的连接
                failedConnections.forEach { deviceId ->
                    LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CLEANUP_INVALID_CONNECTION.get()}: $deviceId")
                    onConnectionFailed(deviceId)
                }
            }
        }
        LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_KEEPALIVE_STARTED.get()}（${CommonTexts.LABEL_INTERVAL.get()}: ${keepAliveInterval}ms）")
    }
    
    /**
     * 停止保活任务
     */
    fun stop() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
}

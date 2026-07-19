/**
 * ADB 连接设置 - 处理 ADB 连接建立、验证和资源清理
 *
 * 本文件包含 ConnectionLifecycle 的 ADB 连接相关扩展函数：
 * - setupAdbConnection: 建立 ADB 连接
 * - verifyAndGetAdbConnection: 验证并获取 ADB 连接
 * - cleanupOldResources: 清理旧资源
 */
package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.AdbConnectionPurpose
import com.screen.remote.android.infrastructure.adb.connection.raceAdbConnections
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager.killProcess
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.connection.PreparedAdbConnection
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.internal.createMonitorBus
import com.screen.remote.android.infrastructure.scrcpy.session.internal.updateDeviceSerial
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbConnectionContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalTrigger
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * 建立 ADB 连接
 * 职责：
 * - 异步查找可用端口
 * - 获取 ADB 连接
 * - 设置全局 ADB Bridge
 */
internal suspend fun ConnectionLifecycle.setupAdbConnection(
    options: ScrcpyOptions,
    session: Session,
): PreparedAdbConnection =
    coroutineScope {
        val portJob = async { findAvailablePort() }

        val connection = getOrCreateAdbConnection(options, session)
        AdbBridge.setConnection(connection)

        val remoteFingerprint =
            connection.getCachedCandidatePreflight()?.buildFingerprint?.takeIf { it.isNotBlank() }
                ?: connection.executeShell("getprop ro.build.fingerprint", retryOnFailure = false)
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
        val deviceInfo = connection.deviceInfo
        val codecCapabilitySignature =
            listOf(
                deviceInfo.serialNumber.ifBlank { connection.deviceId },
                remoteFingerprint.ifBlank { "${deviceInfo.manufacturer}/${deviceInfo.model}/${deviceInfo.androidVersion}" },
                AppConstants.SCRCPY_VERSION,
                "codec-capability-v2",
            ).joinToString("|")
        session.updateDeviceSerial(codecCapabilitySignature)

        PreparedAdbConnection(
            connection = connection,
            localPort = portJob.await(),
        )
    }

/**
 * 获取或创建 ADB 连接
 * 职责：
 * - 检查并验证已有连接
 * - 创建新连接
 */
private suspend fun ConnectionLifecycle.getOrCreateAdbConnection(
    options: ScrcpyOptions,
    session: Session,
): AdbConnection =
    run {
        val raceGeneration = beginAdbRace()
        sessionContext.emit(SessionEvent.AdbConnecting)
        sessionContext.emit(SessionEvent.AdbVerifying)

        val selected =
            raceAdbConnections(
                candidates = options.connectionCandidates,
                purpose = AdbConnectionPurpose.SCRCPY_SESSION,
                connectionManager = adbConnectionManager,
                attemptScope = backgroundScope,
                cleanupScope = backgroundScope,
                logTag = LogTags.SCRCPY_CLIENT,
                logLabel = "ADB",
                isCurrentRace = { isCurrentAdbRace(raceGeneration) },
            )
        val selectedConnection = selected.result.getOrThrow()
        selectedConnection.bindSessionContext(sessionContext.bindCurrent())
        session.adbConnection = selectedConnection
        session.createMonitorBus(selectedConnection.deviceId)
        issueTracker.updateDeviceId(selectedConnection.deviceId)
        sessionContext.emit(
            SessionEvent.AdbConnected(
                AdbConnectionContext(
                    deviceId = selectedConnection.deviceId,
                    serial = selectedConnection.deviceInfo.serialNumber.ifBlank { selectedConnection.deviceId },
                ),
            ),
        )
        selectedConnection
    }

/**
 * 清理旧资源
 */
internal suspend fun ConnectionLifecycle.cleanupOldResources(
    previous: com.screen.remote.android.infrastructure.scrcpy.connection.ActiveScrcpyConnection?,
) {
    if (previous == null) {
        return
    }

    // 重连由生命周期统一接管时，旧媒体/控制 socket 可能仍有部分处于打开状态。
    // 必须先停止旧健康检查并关闭整组 socket，再启动新 server 并按协议顺序重建。
    healthMonitor.stopMonitoring()
    socketManager.closeAllSockets()

    // 必须在创建新 forward 之前完成。系统可能复用同一个本地端口，后台延迟删除
    // 旧映射会把刚创建的新映射一并删掉。
    supervisorScope {
        if (previous.tunnelMode == ScrcpyTunnelMode.ADB_FORWARD && previous.localPort > 0) {
            launch {
                previous.adbConnection
                    .removeAdbForward(previous.localPort, ForwardRemovalTrigger.CleanupOldResources)
                    .onFailure(::logOldResourceCleanupFailure)
            }
        }

        launch {
            runCatching {
                val oldScidHex = String.format("%08x", previous.scid)
                killProcess(previous.adbConnection, "scrcpy.*scid=$oldScidHex")
                LogManager.d(
                    LogTags.SCRCPY_CLIENT,
                    "${RemoteTexts.SCRCPY_CLEANED_OLD_SERVER_PROCESS.get()} (scid=$oldScidHex)",
                )
            }.onFailure(::logOldResourceCleanupFailure)
        }
    }
}

private fun logOldResourceCleanupFailure(error: Throwable) {
    LogManager.w(
        LogTags.SCRCPY_CLIENT,
        "${RemoteTexts.SCRCPY_CLEANUP_OLD_RESOURCES_FAILED.get()}: ${error.message}",
    )
}

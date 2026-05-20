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
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.core.i18n.RemoteTexts
import com.screen.remote.android.infrastructure.adb.connection.AdbBridge
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.shell.AdbShellManager.killProcess
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalTrigger
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * 建立 ADB 连接
 * 职责：
 * - 异步查找可用端口
 * - 获取 ADB 连接
 * - 设置全局 ADB Bridge
 */
internal suspend fun ConnectionLifecycle.setupAdbConnection(
    options: ScrcpyOptions,
): AdbConnection =
    coroutineScope {
        val portJob = async { findAvailablePort() }

        val connection = getOrCreateAdbConnection(options)
        AdbBridge.setConnection(connection)

        localPort = portJob.await()
        connection
    }

/**
 * 获取或创建 ADB 连接
 * 职责：
 * - 检查并验证已有连接
 * - 创建新连接
 */
private suspend fun ConnectionLifecycle.getOrCreateAdbConnection(
    options: ScrcpyOptions,
): AdbConnection {
    sessionContext.emit(SessionEvent.AdbVerifying)

    val host = options.host
    val port = options.port
    val deviceId = options.getDeviceIdentifier()
    val isUsbConnection = options.isUsbConnection()

    // 检查已有连接
    val existingConnection = adbConnectionManager.getConnection(deviceId)
    if (existingConnection != null) {
        existingConnection.bindSessionContext(sessionContext)

        // scrcpy 不兼容 delayed_ack，若当前连接启用了 delayed_ack 则需断开重建
        if (existingConnection.supportsDelayedAck()) {
            LogManager.d(LogTags.SCRCPY_CLIENT, "已有连接启用了 delayed_ack，scrcpy 不兼容，断开重建")
            adbConnectionManager.disconnectDevice(deviceId)
        } else {
            // 验证已有连接
            LogManager.d(LogTags.SCRCPY_CLIENT, "验证已有连接: $deviceId")
            val verifyResult = existingConnection.verify()
            if (verifyResult.isSuccess) {
                LogManager.d(LogTags.SCRCPY_CLIENT, "已有连接验证成功")
                return existingConnection
            } else {
                LogManager.w(LogTags.SCRCPY_CLIENT, "已有连接验证失败，将重新建立连接")
                adbConnectionManager.disconnectDevice(deviceId)
            }
        }
    }

    // 网络设备需要建立新连接（forceReconnect=true 跳过 connectDevice 内部的检查）
    if (!isUsbConnection) {
        adbConnectionManager.connectDevice(
            host,
            port,
            forceReconnect = true,
            sessionContext = sessionContext,
            withDelayedAck = false,
        ).getOrThrow()

        return adbConnectionManager.getConnection(deviceId)
            ?: throw Exception(AdbTexts.ADB_CONNECTION_REFUSED.get())
    }

    if (isUsbConnection) {
        val reconnectResult =
            adbConnectionManager.connectUsbDeviceById(
                deviceId = deviceId,
                sessionContext = sessionContext,
                withDelayedAck = false,
            )
        if (reconnectResult.isSuccess) {
            return adbConnectionManager.getConnection(deviceId)
                ?: throw Exception(AdbTexts.ERROR_USB_CONNECTION_LOST.get())
        }
    }

    // USB 设备连接未找到
    return handleConnectionNotFound(deviceId, host, port, isUsbConnection)
}

/**
 * 处理连接未找到的情况
 */
private suspend fun ConnectionLifecycle.handleConnectionNotFound(
    deviceId: String,
    host: String,
    port: Int,
    isUsbConnection: Boolean,
): AdbConnection {
    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ${RemoteTexts.SCRCPY_ADB_CONNECTION_UNAVAILABLE.get()}")

    if (isUsbConnection) {
        throw Exception(AdbTexts.ERROR_USB_CONNECTION_LOST.get())
    }

    // 网络设备重连
    sessionContext.emit(SessionEvent.AdbConnecting)

    val reconnectResult = adbConnectionManager.connectDevice(host, port, sessionContext = sessionContext, withDelayedAck = false)
    if (reconnectResult.isFailure) {
        throw Exception(
            "${AdbTexts.ERROR_ADB_RECONNECT_FAILED.get()}: ${reconnectResult.exceptionOrNull()?.message}",
        )
    }

    LogManager.d(LogTags.SCRCPY_CLIENT, RemoteTexts.SCRCPY_ADB_RECONNECT_SUCCESS.get())

    return adbConnectionManager.getConnection(deviceId)
        ?: throw Exception(AdbTexts.ADB_CONNECTION_REFUSED.get())
}

/**
 * 清理旧资源
 */
internal suspend fun ConnectionLifecycle.cleanupOldResources(connection: AdbConnection) {
    try {
        connection.removeAdbForward(localPort, ForwardRemovalTrigger.CleanupOldResources)
        if (currentScid != null) {
            val oldScidHex = String.format("%08x", currentScid)
            killProcess(
                connection,
                "scrcpy.*scid=$oldScidHex",
            )
            LogManager.d(
                LogTags.SCRCPY_CLIENT,
                "${RemoteTexts.SCRCPY_CLEANED_OLD_SERVER_PROCESS.get()} (scid=$oldScidHex)",
            )
        }
        delay(200)
    } catch (e: Exception) {
        LogManager.w(
            LogTags.SCRCPY_CLIENT,
            "${RemoteTexts.SCRCPY_CLEANUP_OLD_RESOURCES_FAILED.get()}: ${e.message}",
        )
    }
}

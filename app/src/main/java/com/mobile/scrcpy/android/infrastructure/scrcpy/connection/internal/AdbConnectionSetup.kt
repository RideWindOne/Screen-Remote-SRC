/**
 * ADB 连接设置 - 处理 ADB 连接建立、验证和资源清理
 *
 * 本文件包含 ConnectionLifecycle 的 ADB 连接相关扩展函数：
 * - setupAdbConnection: 建立 ADB 连接
 * - verifyAndGetAdbConnection: 验证并获取 ADB 连接
 * - cleanupOldResources: 清理旧资源
 */
package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.adb.shell.AdbShellManager.killProcess
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
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
    host: String,
    port: Int,
): AdbConnection =
    coroutineScope {
        val portJob = async { findAvailablePort() }

        val connection = getOrCreateAdbConnection(host, port)
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
    host: String,
    port: Int,
): AdbConnection {
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.AdbVerifying)

    val deviceId = if (port == 0) host else "$host:$port"
    val isUsbConnection = (port == 0)

    // 检查已有连接
    val existingConnection = adbConnectionManager.getConnection(deviceId)
    if (existingConnection != null) {
        // 验证已有连接
        LogManager.d(LogTags.SCRCPY_CLIENT, "验证已有连接: $deviceId")
        val verifyResult = existingConnection.verify()
        if (verifyResult.isSuccess) {
            LogManager.d(LogTags.SCRCPY_CLIENT, "已有连接验证成功")
            return existingConnection
        } else {
            LogManager.w(LogTags.SCRCPY_CLIENT, "已有连接验证失败，将重新建立连接")
            // 验证失败，断开旧连接（会自动从池中移除）
            adbConnectionManager.disconnectDevice(deviceId)
        }
    }

    // 网络设备需要建立新连接（forceReconnect=true 跳过 connectDevice 内部的检查）
    if (!isUsbConnection) {
        val connectResult = adbConnectionManager.connectDevice(host, port, forceReconnect = true)
        if (connectResult.isFailure) {
            val errorMsg = connectResult.exceptionOrNull()?.message ?: "ADB 连接失败"
            throw Exception(errorMsg, connectResult.exceptionOrNull())
        }

        return adbConnectionManager.getConnection(deviceId)
            ?: throw Exception(AdbTexts.ADB_CONNECTION_REFUSED.get())
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
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.AdbConnecting)

    val reconnectResult = adbConnectionManager.connectDevice(host, port)
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
        connection.removeAdbForward(localPort)
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

package com.mobile.scrcpy.android.infrastructure.adb.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.CommonTexts
import dadb.AdbShellStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

/**
 * ADB 连接封装
 * 负责单个设备的 ADB 连接管理和操作
 */
class AdbConnection(
    val deviceId: String,
    val host: String,
    val port: Int,
    private val dadb: Dadb,
    var deviceInfo: DeviceInfo, // 改为 var 以支持后台更新
) {
    // 端口转发管理（SocketForwarder 或 dadb.tcpForward）
    private val forwarders = ConcurrentHashMap<Int, AutoCloseable>()

    /**
     * 检查连接是否有效
     * 注意：这个方法会实际执行 shell 命令，可能较慢
     * 如果只是检查连接对象是否存在，应该检查连接池而不是调用此方法
     */
    fun isConnected(): Boolean =
        try {
            // 使用更轻量级的命令，或者可以改为检查 dadb 内部状态
            // 但 dadb 没有公开的状态检查方法，所以只能通过实际执行命令来测试
            dadb.shell("echo 1").exitCode == 0
        } catch (_: Exception) {
            false
        }

    /**
     * 执行 Shell 命令
     *
     * 注意：dadb 有自动重连机制（参考 DadbImpl.connection() 方法）
     * - 如果连接已关闭，下次调用 shell() 时会自动创建新连接
     * - 所以如果第一次调用失败，可以尝试重试一次，让 dadb 自动重连
     * - 但如果遇到 ECONNREFUSED，说明远程 ADB 服务已断开，不应重试
     */
    suspend fun executeShell(
        command: String,
        retryOnFailure: Boolean = true,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val response = dadb.shell(command)
                Result.success(response.output)
            } catch (e: ConnectException) {
                // ECONNREFUSED - 远程 ADB 服务已断开，不应重试
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_DISCONNECTED_ECONNREFUSED.get()} (ECONNREFUSED)，${AdbTexts.ADB_CANNOT_EXECUTE_COMMAND.get()}: $command - ${e.message}",
                )
                Result.failure(Exception(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
            } catch (e: EOFException) {
                // ADB 连接已关闭
                // dadb 会在下次调用时自动重连，所以可以重试一次
                if (retryOnFailure) {
                    LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_AUTO_RECONNECT_RETRY.get()}: $command")
                    try {
                        delay(100) // 短暂延迟，让 dadb 完成重连
                        val retryResponse = dadb.shell(command)
                        LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_AUTO_RECONNECT_SUCCESS.get()}")
                        Result.success(retryResponse.output)
                    } catch (retryException: Exception) {
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "${AdbTexts.ADB_AUTO_RECONNECT_STILL_FAILED.get()}: ${retryException.message}",
                        )
                        Result.failure(retryException)
                    }
                } else {
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_CONNECTION_CLOSED.get()}，${AdbTexts.ADB_CANNOT_EXECUTE_COMMAND.get()}: $command",
                    )
                    Result.failure(e)
                }
            } catch (e: SocketException) {
                // Socket 异常，检查是否是 ECONNREFUSED
                if (e.message?.contains("ECONNREFUSED", ignoreCase = true) == true) {
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_SOCKET_EXCEPTION.get()} (ECONNREFUSED): $command - ${e.message}",
                    )
                    Result.failure(Exception(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
                } else if (retryOnFailure) {
                    // 其他 Socket 异常，尝试重连
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_SOCKET_EXCEPTION_RETRY.get()}: $command - ${e.message}",
                    )
                    try {
                        delay(100) // 短暂延迟，让 dadb 完成重连
                        val retryResponse = dadb.shell(command)
                        LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_AUTO_RECONNECT_SUCCESS.get()}")
                        Result.success(retryResponse.output)
                    } catch (retryException: Exception) {
                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "${AdbTexts.ADB_AUTO_RECONNECT_STILL_FAILED.get()}: ${retryException.message}",
                        )
                        Result.failure(retryException)
                    }
                } else {
                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_SOCKET_EXCEPTION.get()}: $command - ${e.message}",
                    )
                    Result.failure(e)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_EXECUTE_COMMAND_FAILED.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 异步执行 Shell 命令
     */
    suspend fun executeShellAsync(command: String) =
        withContext(Dispatchers.IO) {
            try {
                dadb.openShell(command)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_ASYNC_EXECUTE_FAILED.get()}: ${e.message}", e)
            }
        }

    /**
     * 打开 Shell 流
     */
    suspend fun openShellStream(command: String): AdbShellStream? =
        withContext(Dispatchers.IO) {
            try {
                dadb.openShell(command)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_OPEN_SHELL_STREAM_FAILED.get()}: ${e.message}", e)
                null
            }
        }

    /**
     * 设置端口转发
     */
    suspend fun setupPortForward(
        localPort: Int,
        remotePort: Int,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // 先关闭已存在的转发
                forwarders[localPort]?.close()

                val forwarder = dadb.tcpForward(localPort, remotePort)
                forwarders[localPort] = forwarder

                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_PORT_FORWARD_SUCCESS.get()}: $localPort -> $remotePort",
                )
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_PORT_FORWARD_FAILED.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 设置 ADB forward（用于 scrcpy socket 连接）
     * 参考 dadb PR #90: 使用自定义 SocketForwarder 直接支持 localabstract socket
     * 1. 使用 SocketForwarder 将本地 LOCAL_PORT 直接转发到设备的 localabstract:NAME
     * 2. 客户端连接到 127.0.0.1:LOCAL_PORT
     *
     * 优势：减少一层转发，性能更好
     */
    suspend fun setupAdbForward(
        localPort: Int,
        socketName: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // 使用自定义 SocketForwarder，直接支持 localabstract socket
                // 参考 dadb PR #90: Extend tcpForward to support more socket domains
                try {
                    // 先关闭已存在的转发
                    forwarders[localPort]?.close()

                    val targetSocket = "localabstract:$socketName"
                    val forwarder = SocketForwarder(dadb, localPort, targetSocket)
                    forwarder.start()
                    forwarders[localPort] = forwarder

                    LogManager.d(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_FORWARD_SETUP_SUCCESS.get()}（${CommonTexts.LABEL_USING.get()} SocketForwarder: $localPort -> $targetSocket）",
                    )
                    Result.success(true)
                } catch (e: Exception) {
                    LogManager.e(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_SOCKET_FORWARDER_FAILED.get()}: ${e.message}",
                        e,
                    )
                    Result.failure(e)
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FORWARD_SETUP_EXCEPTION.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 检查 ADB forward 是否存在且可用
     * 不仅检查 SocketForwarder 状态，还测试端口是否真的可以连接
     */
    suspend fun checkAdbForward(localPort: Int): Boolean =
        withContext(Dispatchers.IO) {
            // 1. 检查 SocketForwarder 是否存在且运行
            val forwarder = forwarders[localPort] as? SocketForwarder
            if (forwarder?.isRunning() != true) {
                LogManager.d(LogTags.ADB_CONNECTION, "forwarder not Running")
                return@withContext false
            }

            // 2. 测试端口是否真的可以连接（快速测试）
            try {
                val testSocket = Socket()
                testSocket.connect(InetSocketAddress("127.0.0.1", localPort), 500)
                testSocket.close()
                LogManager.d(LogTags.ADB_CONNECTION, "forwarder can connect")
                return@withContext true
            } catch (_: Exception) {
                // 端口无法连接，说明 forward 虽然在运行但不可用
                LogManager.d(LogTags.ADB_CONNECTION, "forwarder can't connect")
                return@withContext false
            }
        }

    /**
     * 移除 ADB forward
     */
    suspend fun removeAdbForward(localPort: Int): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // 关闭转发器（SocketForwarder）
                forwarders[localPort]?.close()
                forwarders.remove(localPort)

                LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FORWARD_REMOVED.get()}: tcp:$localPort")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FORWARD_REMOVE_EXCEPTION.get()}: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ========== 文件操作代理方法 ==========

    /**
     * 推送文件
     */
    suspend fun pushFile(
        localPath: String,
        remotePath: String,
    ): Result<Boolean> = AdbFileOperations.pushFile(dadb, localPath, remotePath)

    /**
     * 拉取文件
     */
    suspend fun pullFile(
        remotePath: String,
        localPath: String,
    ): Result<Boolean> = AdbFileOperations.pullFile(dadb, remotePath, localPath)

    /**
     * 安装 APK
     */
    suspend fun installApk(apkPath: String): Result<Boolean> = AdbFileOperations.installApk(dadb, apkPath)

    /**
     * 卸载应用
     */
    suspend fun uninstallPackage(packageName: String): Result<Boolean> =
        AdbFileOperations.uninstallPackage(dadb, packageName)

    /**
     * 推送 scrcpy-server.jar 到设备
     */
    suspend fun pushScrcpyServer(
        context: Context,
        scrcpyServerPath: String = "/data/local/tmp/scrcpy-server.jar",
    ): Result<Boolean> = AdbFileOperations.pushScrcpyServer(dadb, context, scrcpyServerPath)

    // ========== 编码器检测代理方法 ==========

    /**
     * 检测可用的视频编码器
     */
    suspend fun detectVideoEncoders(context: Context): Result<List<VideoEncoderInfo>> =
        AdbEncoderDetector.detectVideoEncoders(dadb, context, ::openShellStream)

    /**
     * 检测音频编码器
     */
    suspend fun detectAudioEncoders(context: Context): Result<List<AudioEncoderInfo>> =
        AdbEncoderDetector.detectAudioEncoders(dadb, context, ::openShellStream)

    /**
     * 关闭连接
     */
    fun close() {
        try {
            // 关闭所有端口转发
            forwarders.values.forEach { it.close() }
            forwarders.clear()

            // 关闭 ADB 连接
            dadb.close()
            LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_CONNECTION_CLOSED.get()}: $deviceId")
        } catch (e: Exception) {
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_CLOSE_CONNECTION_FAILED_DETAIL.get()}: ${e.message}",
                e,
            )
        }
    }
}

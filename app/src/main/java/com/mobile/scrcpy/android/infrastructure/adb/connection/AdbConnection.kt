package com.mobile.scrcpy.android.infrastructure.adb.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.common.manager.ManagementDebugLog
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.internal.saveDiscoveredEncoders
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.AdbIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.AdbIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecDetectionContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecDetectionSummary
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.CodecIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ForwardRemovalTrigger
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.runtime.SessionContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.AdbStreamSocket
import dadb.AdbShellStream
import dadb.Dadb
import dadb.helper.RemoteAppIconBatchData
import dadb.helper.RemoteAppIconBatchRequest
import dadb.helper.RemoteAppIconData
import dadb.helper.RemoteAppListItem
import dadb.helper.RemoteHelperFileState
import dadb.helper.RemoteHelperProbeResult
import dadb.helper.loadAppIconBatchWithHelper
import dadb.helper.loadAppIconWithHelper
import dadb.helper.loadAppListPageWithHelper
import dadb.helper.prepareRemoteAppIconHelper
import dadb.helper.runRemoteAppHelperProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADB 连接封装
 * 负责单个设备的 ADB 能力入口和会话级协作者装配
 */
class AdbConnection(
    val deviceId: String,
    val host: String,
    val port: Int,
    private val dadb: Dadb,
    private val delayedAckEnabled: Boolean = false,
    var deviceInfo: DeviceInfo,
    private var sessionContext: SessionContext? = null,
) {
    private val transportDisconnectNotified = AtomicBoolean(false)

    private val shellExecutor =
        AdbConnectionShellExecutor(
            dadb = dadb,
            deviceId = deviceId,
        )

    private val forwardRegistry =
        AdbConnectionForwardRegistry(
            dadb = dadb,
            deviceId = deviceId,
            sessionContextProvider = { sessionContext },
        )

    suspend fun verify(): Result<String> =
        AdbConnectionVerifier.verifyDadb(dadb, deviceId, sessionContext = sessionContext)

    fun supportsDelayedAck(): Boolean = delayedAckEnabled

    fun bindSessionContext(sessionContext: SessionContext?) {
        this.sessionContext = sessionContext
    }

    fun handleTransportDisconnected(reason: String) {
        if (!transportDisconnectNotified.compareAndSet(false, true)) {
            return
        }

        sessionContext?.emit(
            SessionEvent.AdbDisconnected(
                AdbIssue(
                    kind = AdbIssueKind.ConnectionDisconnected,
                    detail = reason,
                ),
            ),
        )
        sessionContext?.emit(
            SessionEvent.RequestReconnect(
                ReconnectIssue(
                    kind = ReconnectIssueKind.RuntimeError,
                    detail = reason,
                ),
            ),
        )
    }

    fun isConnected(): Boolean =
        try {
            val command = "echo 1"
            logShellCommandStart(LogTags.ADB_CONNECTION, command)
            val response = dadb.shell(command)
            logShellCommandResult(
                tag = LogTags.ADB_CONNECTION,
                command = command,
                exitCode = response.exitCode,
                output = response.output,
                errorOutput = response.errorOutput,
            )
            response.exitCode == 0
        } catch (error: Exception) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, "echo 1", error)
            false
        }

    suspend fun executeShell(
        command: String,
        retryOnFailure: Boolean = true,
    ): Result<String> = shellExecutor.execute(command, retryOnFailure)

    suspend fun executeShellAsync(command: String) {
        shellExecutor.executeAsync(command)
    }

    suspend fun openShellStream(command: String): AdbShellStream? = shellExecutor.openStream(command)

    suspend fun openPtyShellStream(command: String = ""): AdbShellStream? = shellExecutor.openPtyStream(command)

    suspend fun openLocalAbstractSocket(socketName: String): Result<Socket> =
        withContext(Dispatchers.IO) {
            runCatching {
                AdbStreamSocket(
                    adbStream = dadb.open("localabstract:$socketName"),
                    streamLabel = socketName,
                )
            }
        }

    suspend fun executeService(destination: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                dadb.open(destination).use { stream ->
                    stream.source.readString(StandardCharsets.UTF_8).trim()
                }
            }
        }

    suspend fun restartTcpip(port: Int): Result<String> = executeService("tcpip:$port")

    suspend fun setupPortForward(
        localPort: Int,
        remotePort: Int,
    ): Result<Boolean> = forwardRegistry.setupPortForward(localPort, remotePort)

    suspend fun setupAdbForward(
        localPort: Int,
        socketName: String,
    ): Result<Boolean> = forwardRegistry.setupAdbForward(localPort, socketName)

    suspend fun checkAdbForward(localPort: Int): Boolean = forwardRegistry.checkAdbForward(localPort)

    suspend fun removeAdbForward(
        localPort: Int,
        trigger: ForwardRemovalTrigger = ForwardRemovalTrigger.Unknown,
    ): Result<Boolean> = forwardRegistry.removeAdbForward(localPort, trigger)

    suspend fun pushFile(
        localPath: String,
        remotePath: String,
    ): Result<Boolean> = AdbFileOperations.pushFile(dadb, localPath, remotePath)

    suspend fun pullFile(
        remotePath: String,
        localPath: String,
    ): Result<Boolean> = AdbFileOperations.pullFile(dadb, remotePath, localPath)

    suspend fun installApk(apkPath: String): Result<Boolean> = AdbFileOperations.installApk(dadb, apkPath)

    suspend fun uninstallPackage(packageName: String): Result<Boolean> =
        AdbFileOperations.uninstallPackage(dadb, packageName)

    suspend fun pushScrcpyServer(
        context: Context,
        scrcpyServerPath: String = "/data/local/tmp/scrcpy-server.jar",
    ): Result<Boolean> = AdbFileOperations.pushScrcpyServer(dadb, context, scrcpyServerPath)

    suspend fun prepareAppIconHelper(localHelperJar: File): Result<RemoteHelperFileState> =
        withContext(Dispatchers.IO) {
            ManagementDebugLog.d(LogTags.ADB_CONNECTION) { "helper 准备 jar=${localHelperJar.absolutePath}" }
            runCatching {
                dadb.prepareRemoteAppIconHelper(localHelperJar)
            }.onFailure { error ->
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "helper 准备失败: ${error.message}",
                    error,
                )
            }
        }

    suspend fun loadAppIconWithHelper(
        packageName: String,
        localHash: String?,
        localHelperJar: File,
    ): Result<RemoteAppIconData> =
        withContext(Dispatchers.IO) {
            ManagementDebugLog.d(LogTags.ADB_CONNECTION) {
                "helper 图标请求 package=$packageName localHash=${localHash ?: "<none>"} jar=${localHelperJar.absolutePath}"
            }
            runCatching {
                dadb.loadAppIconWithHelper(
                    packageName = packageName,
                    localHash = localHash,
                    localHelperJar = localHelperJar,
                )
            }.onFailure { error ->
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "helper 图标请求失败 package=$packageName: ${error.message}",
                    error,
                )
            }
        }

    suspend fun loadAppListPageWithHelper(
        offset: Int,
        limit: Int,
        includeSystem: Boolean,
        localHelperJar: File,
    ): Result<List<RemoteAppListItem>> =
        withContext(Dispatchers.IO) {
            ManagementDebugLog.d(LogTags.ADB_CONNECTION) {
                "helper 列表请求 offset=$offset limit=$limit includeSystem=$includeSystem jar=${localHelperJar.absolutePath}"
            }
            runCatching {
                dadb.loadAppListPageWithHelper(
                    offset = offset,
                    limit = limit,
                    includeSystem = includeSystem,
                    localHelperJar = localHelperJar,
                )
            }.onFailure { error ->
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "helper 列表请求失败 offset=$offset limit=$limit includeSystem=$includeSystem: ${error.message}",
                    error,
                )
            }
        }

    suspend fun loadAppIconBatchWithHelper(
        requests: List<RemoteAppIconBatchRequest>,
        localHelperJar: File,
    ): Result<RemoteAppIconBatchData> =
        withContext(Dispatchers.IO) {
            ManagementDebugLog.d(LogTags.ADB_CONNECTION) {
                "helper 批量图标请求 count=${requests.size} jar=${localHelperJar.absolutePath}"
            }
            runCatching {
                dadb.loadAppIconBatchWithHelper(
                    requests = requests,
                    localHelperJar = localHelperJar,
                )
            }.onFailure { error ->
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "helper 批量图标请求失败 count=${requests.size}: ${error.message}",
                    error,
                )
            }
        }

    suspend fun runAppHelperProbe(
        command: String,
        args: List<String>,
        localHelperJar: File,
    ): Result<RemoteHelperProbeResult> =
        withContext(Dispatchers.IO) {
            ManagementDebugLog.d(LogTags.ADB_CONNECTION) {
                "helper probe command=$command args=${args.joinToString(" ")} jar=${localHelperJar.absolutePath}"
            }
            runCatching {
                dadb.runRemoteAppHelperProbe(
                    command = command,
                    args = args,
                    localHelperJar = localHelperJar,
                )
            }.onFailure { error ->
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "helper probe 失败 command=$command: ${error.message}",
                    error,
                )
            }
        }

    suspend fun detectEncoders(
        context: Context,
        skipPush: Boolean = false,
    ): Result<EncoderDetectionResult> {
        val detectionContext = CodecDetectionContext(reusedUploadedServer = skipPush)
        sessionContext?.emit(SessionEvent.VideoEncoderDetecting(detectionContext))
        sessionContext?.emit(SessionEvent.AudioEncoderDetecting(detectionContext))
        val result = AdbEncoderDetector.detectEncoders(dadb, context, ::openShellStream, skipPush)

        if (result.isSuccess) {
            withContext(Dispatchers.IO) {
                try {
                    val detectionResult = result.getOrNull() ?: return@withContext
                    val session = sessionContext?.currentSession() ?: return@withContext

                    val videoEncoders = detectionResult.videoEncoders.map { it.name }
                    val audioEncoders = detectionResult.audioEncoders.map { it.name }

                    if (videoEncoders.isNotEmpty()) {
                        sessionContext?.emit(
                            SessionEvent.VideoEncoderDetected(
                                CodecDetectionSummary(
                                    totalCount = videoEncoders.size,
                                    sampleNames = videoEncoders.take(3),
                                    reusedUploadedServer = skipPush,
                                ),
                            ),
                        )
                    } else {
                        sessionContext?.emit(
                            SessionEvent.VideoEncoderDetectFailed(
                                CodecIssue(
                                    kind = CodecIssueKind.NoEncodersFound,
                                    detail = "No remote video encoders detected",
                                ),
                            ),
                        )
                    }

                    if (audioEncoders.isNotEmpty()) {
                        sessionContext?.emit(
                            SessionEvent.AudioEncoderDetected(
                                CodecDetectionSummary(
                                    totalCount = audioEncoders.size,
                                    sampleNames = audioEncoders.take(3),
                                    reusedUploadedServer = skipPush,
                                ),
                            ),
                        )
                    } else {
                        sessionContext?.emit(
                            SessionEvent.AudioEncoderError(
                                CodecIssue(
                                    kind = CodecIssueKind.NoEncodersFound,
                                    detail = "No remote audio encoders detected",
                                ),
                            ),
                        )
                    }

                    if (videoEncoders.isNotEmpty() || audioEncoders.isNotEmpty()) {
                        session.saveDiscoveredEncoders(
                            remoteVideoEncoders = videoEncoders,
                            remoteAudioEncoders = audioEncoders,
                        )

                        LogManager.d(
                            LogTags.ADB_CONNECTION,
                            "已保存编解码器到会话 ${session.sessionId}: 视频=${videoEncoders.size}, 音频=${audioEncoders.size}",
                        )
                    }
                } catch (e: Exception) {
                    LogManager.w(LogTags.ADB_CONNECTION, "异步保存编码器列表失败: ${e.message}")
                }
            }
        } else {
            val detail = result.exceptionOrNull()?.message ?: "Unknown encoder detection error"
            sessionContext?.emit(
                SessionEvent.VideoEncoderDetectFailed(
                    CodecIssue(
                        kind = CodecIssueKind.DetectionFailed,
                        detail = detail,
                    ),
                ),
            )
            sessionContext?.emit(
                SessionEvent.AudioEncoderError(
                    CodecIssue(
                        kind = CodecIssueKind.DetectionFailed,
                        detail = detail,
                    ),
                ),
            )
        }

        return result
    }

    fun close() {
        try {
            forwardRegistry.closeAll()
            dadb.close()
            LogManager.d(LogTags.ADB_CONNECTION, "ADB 连接已关闭: $deviceId")
        } catch (e: Exception) {
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "关闭 ADB 连接失败: ${e.message}",
                e,
            )
        }
    }
}

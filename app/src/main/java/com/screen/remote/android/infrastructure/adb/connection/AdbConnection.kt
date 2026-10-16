package com.screen.remote.android.infrastructure.adb.connection

import android.content.Context
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.event.ForwardRemoved
import com.screen.remote.android.core.common.event.ForwardSetup
import com.screen.remote.android.core.common.event.ScrcpyEventBus.pushEvent
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.common.manager.LogManager.dManagement
import com.screen.remote.android.core.common.manager.LogManager.dShell
import com.screen.remote.android.core.i18n.AdbTexts
import com.screen.remote.android.infrastructure.scrcpy.connection.AdbStreamSocket
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbConnectionContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardRemovalTrigger
import com.screen.remote.android.infrastructure.scrcpy.session.model.ForwardSetupContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ReconnectIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
import com.screen.remote.android.infrastructure.scrcpy.session.runtime.SessionContext
import dadb.AdbConnectException
import dadb.AdbShellStream
import dadb.Dadb
import dadb.DadbRoute
import dadb.DadbSession
import dadb.PortForwarder
import dadb.helper.RemoteAppData
import dadb.helper.RemoteAppIconBatchData
import dadb.helper.RemoteDeviceSnapshot
import dadb.helper.RemoteDirectoryEntry
import dadb.helper.RemoteHelperFileState
import dadb.helper.RemoteProcessEntry
import dadb.helper.RemoteScreenshotStream
import dadb.helper.RemoteTouchStream
import dadb.helper.injectRemoteTextWithHelper
import dadb.helper.loadAppIconBatchWithHelper
import dadb.helper.loadAppsWithHelper
import dadb.helper.loadDeviceSnapshotWithHelper
import dadb.helper.loadDirectoryWithHelper
import dadb.helper.loadProcessesWithHelper
import dadb.helper.openRemoteScreenshotStreamWithHelper
import dadb.helper.openRemoteTouchStreamWithHelper
import dadb.helper.prepareRemoteDadbHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * ADB 连接封装
 * 负责单个设备的 ADB 能力入口和会话级协作者装配
 */
class AdbConnection(
    val deviceId: String,
    val host: String,
    val port: Int,
    private val dadb: DadbSession,
    var deviceInfo: DeviceInfo,
    private var sessionContext: SessionContext? = null,
    private val passwordHolder: AtomicReference<String> = AtomicReference(""),
) {
    private val transportDisconnectNotified = AtomicBoolean(false)
    private val forcePrimaryStreaming = AtomicBoolean(false)
    private val streamingForwardRegistryMutex = Mutex()

    @Volatile
    private var streamingForwardRegistry: AdbConnectionForwardRegistry? = null

    @Volatile
    private var cachedDisplayInfo: AdbDisplayInfo? = null

    @Volatile
    private var cachedCandidatePreflight: AdbCandidatePreflight? = null

    private val shellExecutor = AdbConnectionShellExecutor(
        dadb = dadb,
        deviceId = deviceId,
    )

    private val forwardRegistry = AdbConnectionForwardRegistry(
        dadb = dadb,
        deviceId = deviceId,
        sessionContextProvider = { sessionContext },
    )

    suspend fun verify(): Result<String> =
        AdbConnectionVerifier.verifyDadb(
            dadb,
            deviceId,
            sessionContext = sessionContext,
        )

    suspend fun verifyWithoutSessionEvents(): Result<String> =
        AdbConnectionVerifier.verifyDadb(
            dadb,
            deviceId,
            sessionContext = null,
        )

    fun supportsDelayedAck(): Boolean = if (forcePrimaryStreaming.get()) {
        false
    } else {
        dadb.routeIfReady(DadbRoute.STREAMING)?.supportsFeature(Dadb.FEATURE_DELAYED_ACK) == true
    }

    private fun shouldFallbackToPrimaryStreaming(error: Throwable): Boolean {
        if (forcePrimaryStreaming.get()) {
            return true
        }
        var cursor: Throwable? = error
        while (cursor != null) {
            if (cursor is SocketTimeoutException) {
                return true
            }
            val message = cursor.message.orEmpty().lowercase()
            if (message.contains("connection handshake failed") || message.contains("could not create the streaming adb connection")) {
                return true
            }
            cursor = cursor.cause
        }
        return false
    }

    private fun markStreamingFallback(error: Throwable) {
        if (forcePrimaryStreaming.compareAndSet(false, true)) {
            LogManager.w(
                LogTags.ADB_CONNECTION,
                "Streaming ADB not available, fallback to primary connection: ${error.message}",
            )
        }
    }

    fun bindSessionContext(sessionContext: SessionContext?) {
        this.sessionContext = sessionContext
    }

    fun setShellPassword(password: String) {
        passwordHolder.set(password)
    }

    fun clearShellPassword() {
        passwordHolder.set("")
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

    fun isConnected(): Boolean = runBlocking {
        executeShell("echo 1", retryOnFailure = false).isSuccess
    }

    suspend fun executeShell(
        command: String,
        retryOnFailure: Boolean = true,
        useShellPassword: Boolean = false,
    ): Result<String> = shellExecutor.execute(command, retryOnFailure, useShellPassword)

    suspend fun executeShellAsync(command: String) {
        shellExecutor.executeAsync(command)
    }

    fun getCachedDisplayInfo(): AdbDisplayInfo? = cachedDisplayInfo

    fun getCachedCandidatePreflight(): AdbCandidatePreflight? = cachedCandidatePreflight

    suspend fun refreshCandidatePreflight(purpose: AdbConnectionPurpose): Result<AdbCandidatePreflight> = executeShell(
        buildAdbCandidatePreflightCommand(purpose), retryOnFailure = false
    ).mapCatching(::parseAdbCandidatePreflight).map { preflight ->
        val merged = cachedCandidatePreflight?.merge(preflight) ?: preflight
        cachedCandidatePreflight = merged
        merged.displayInfo?.let { displayInfo -> cachedDisplayInfo = displayInfo }
        merged
    }

    suspend fun refreshDisplayInfo(): Result<AdbDisplayInfo> =
        executeShell(READ_ADB_DISPLAY_INFO_COMMAND, retryOnFailure = false).mapCatching(::parseAdbDisplayInfo)
            .onSuccess { displayInfo ->
                cachedDisplayInfo = displayInfo
                cachedCandidatePreflight = cachedCandidatePreflight?.copy(displayInfo = displayInfo)
            }

    fun markCompatibleScrcpyServerAvailable() {
        cachedCandidatePreflight =
            cachedCandidatePreflight?.copy(hasCompatibleScrcpyServer = true) ?: AdbCandidatePreflight(
                buildFingerprint = "",
                displayInfo = cachedDisplayInfo,
                hasCompatibleScrcpyServer = true,
            )
    }

    suspend fun openShellStream(command: String): AdbShellStream? = shellExecutor.openStream(command)

    suspend fun openStreamingShellStream(command: String): AdbShellStream? {
        val streamingDadb = runCatching { streamingDadb() }.getOrElse { error ->
            LogManager.e(
                LogTags.ADB_CONNECTION, "Waiting for ADB delayed-ACK secondary link failed: ${error.message}", error
            )
            return null
        }
        return AdbConnectionShellExecutor(
            dadb = streamingDadb,
            deviceId = deviceId,
        ).openStream(command)
    }

    suspend fun openLocalAbstractSocket(socketName: String): Result<Socket> = withContext(Dispatchers.IO) {
        runCatching {
            val streamingDadb = streamingDadb()
            AdbStreamSocket(
                adbStream = streamingDadb.open("localabstract:$socketName"),
                streamLabel = socketName,
            )
        }
    }

    suspend fun executeService(destination: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            dadb.open(destination).use { stream ->
                stream.source.readString(StandardCharsets.UTF_8).trim()
            }
        }
    }

    suspend fun openRemoteScreenshotStream(
        localHelperJar: File,
        maxSize: Int,
        jpegQuality: Int,
        forceDisplaySurface: Boolean = false,
    ): Result<RemoteScreenshotStream> = withContext(Dispatchers.IO) {
        runCatching {
            streamingDadb().openRemoteScreenshotStreamWithHelper(
                localHelperJar = localHelperJar,
                maxSize = maxSize,
                jpegQuality = jpegQuality,
                forceDisplaySurface = forceDisplaySurface,
            )
        }
    }

    suspend fun openRemoteTouchStream(localHelperJar: File): Result<RemoteTouchStream> = withContext(Dispatchers.IO) {
        runCatching {
            streamingDadb().openRemoteTouchStreamWithHelper(
                localHelperJar = localHelperJar,
            )
        }
    }

    suspend fun injectRemoteText(
        localHelperJar: File,
        text: String,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            streamingDadb().injectRemoteTextWithHelper(
                text = text,
                localHelperJar = localHelperJar,
            )
            true
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
    ): Result<Boolean> = streamingForwardRegistry().setupAdbForward(localPort, socketName)

    suspend fun checkAdbForward(localPort: Int): Boolean = streamingForwardRegistry?.checkAdbForward(localPort) ?: false

    fun isAdbForwardRunning(localPort: Int): Boolean = streamingForwardRegistry?.isAdbForwardRunning(localPort) == true

    suspend fun removeAdbForward(
        localPort: Int,
        trigger: ForwardRemovalTrigger = ForwardRemovalTrigger.Unknown,
    ): Result<Boolean> = streamingForwardRegistry?.removeAdbForward(localPort, trigger) ?: Result.success(true)

    suspend fun pushFile(
        localPath: String,
        remotePath: String,
        onProgressBytes: (Long) -> Unit = {},
    ): Result<Boolean> = AdbFileOperations.pushFile(dadb, localPath, remotePath, onProgressBytes)

    /**
     * 从输入流直接推送文件（跳过本地缓存，提升上传速度）
     */
    suspend fun pushStream(
        inputStream: java.io.InputStream,
        remotePath: String,
        fileSize: Long,
        lastModified: Long = System.currentTimeMillis(),
        onProgressBytes: (Long) -> Unit = {},
    ): Result<Boolean> = AdbFileOperations.pushStream(dadb, inputStream, remotePath, fileSize, lastModified, onProgressBytes)

    suspend fun pullFile(
        remotePath: String,
        localPath: String,
    ): Result<Boolean> = AdbFileOperations.pullFile(dadb, remotePath, localPath)

    suspend fun installApk(apkPath: String): Result<Boolean> = AdbFileOperations.installApk(dadb, apkPath)

    suspend fun installApks(apkPaths: List<String>): Result<Boolean> = AdbFileOperations.installApks(dadb, apkPaths)

    suspend fun pushScrcpyServer(
        context: Context,
        scrcpyServerPath: String = "/data/local/tmp/scrcpy-server.jar",
    ): Result<Boolean> = runCatching { streamingDadb() }.fold(
        onSuccess = { streamingDadb ->
            AdbFileOperations.pushScrcpyServer(streamingDadb, context, scrcpyServerPath)
        },
        onFailure = { error -> Result.failure(error) },
    )

    suspend fun prepareDadbHelper(localHelperJar: File): Result<RemoteHelperFileState> = withContext(Dispatchers.IO) {
        dManagement(LogTags.ADB_CONNECTION) { "helper preparation jar=${localHelperJar.absolutePath}" }
        runCatching {
            dadb.prepareRemoteDadbHelper(localHelperJar)
        }.onFailure { error ->
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "Helper preparation failed: ${error.message}",
                error,
            )
        }
    }

    suspend fun loadDirectoryWithHelper(
        path: String,
        localHelperJar: File,
    ): Result<List<RemoteDirectoryEntry>> = withContext(Dispatchers.IO) {
        dManagement(LogTags.ADB_CONNECTION) {
            "helper directory request: path=$path jar=${localHelperJar.absolutePath}"
        }
        runCatching {
            dadb.loadDirectoryWithHelper(
                path = path,
                localHelperJar = localHelperJar,
            )
        }.onFailure { error ->
            logHelperRequestFailure(
                operation = "directory",
                detail = "path=$path",
                error = error,
            )
        }
    }

    suspend fun loadProcessesWithHelper(localHelperJar: File): Result<List<RemoteProcessEntry>> =
        withContext(Dispatchers.IO) {
            dManagement(LogTags.ADB_CONNECTION) {
                "helper process request: jar=${localHelperJar.absolutePath}"
            }
            runCatching {
                dadb.loadProcessesWithHelper(localHelperJar = localHelperJar)
            }.onFailure { error ->
                logHelperRequestFailure(
                    operation = "process",
                    detail = "jar=${localHelperJar.absolutePath}",
                    error = error,
                )
            }
        }

    private fun logHelperRequestFailure(
        operation: String,
        detail: String,
        error: Throwable,
    ) {
        val levelMessage = "Helper $operation request failed ($detail): ${error.message}"
        if (error is SocketTimeoutException) {
            LogManager.w(LogTags.ADB_CONNECTION, "$levelMessage (timeout, retry later)")
        } else {
            LogManager.w(LogTags.ADB_CONNECTION, levelMessage)
        }
    }

    suspend fun loadAppIconBatchWithHelper(
        packageNames: List<String>,
        localHelperJar: File,
    ): Result<RemoteAppIconBatchData> = withContext(Dispatchers.IO) {
        dManagement(LogTags.ADB_CONNECTION) {
            "helper batch icon request: count=${packageNames.size} jar=${localHelperJar.absolutePath}"
        }
        runCatching {
            dadb.loadAppIconBatchWithHelper(
                packageNames = packageNames,
                localHelperJar = localHelperJar,
            )
        }.onFailure { error ->
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "Helper batch icon request failed count=${packageNames.size}: ${error.message}",
            )
        }
    }

    suspend fun loadAppsWithHelper(
        includeUser: Boolean,
        includeSystem: Boolean,
        includeEnabled: Boolean,
        includeDisabled: Boolean,
        fields: Set<String>,
        packageNames: Set<String> = emptySet(),
        localHelperJar: File,
    ): Result<List<RemoteAppData>> = withContext(Dispatchers.IO) {
        dManagement(LogTags.ADB_CONNECTION) {
            "helper app data request: user=$includeUser system=$includeSystem enabled=$includeEnabled disabled=$includeDisabled fields=${
                fields.sorted().joinToString(",")
            }"
        }
        runCatching {
            dadb.loadAppsWithHelper(
                includeUser = includeUser,
                includeSystem = includeSystem,
                includeEnabled = includeEnabled,
                includeDisabled = includeDisabled,
                fields = fields,
                packageNames = packageNames,
                localHelperJar = localHelperJar,
            )
        }.onFailure { error ->
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "Helper app data request failed: ${error.message}",
                error,
            )
        }
    }

    suspend fun loadDeviceSnapshotWithHelper(localHelperJar: File): Result<RemoteDeviceSnapshot> =
        withContext(Dispatchers.IO) {
            dManagement(LogTags.ADB_CONNECTION) {
                "helper device snapshot request"
            }
            runCatching {
                dadb.loadDeviceSnapshotWithHelper(localHelperJar = localHelperJar)
            }.onFailure { error ->
                LogManager.e(
                    LogTags.ADB_CONNECTION,
                    "Helper device snapshot request failed: ${error.message}",
                    error,
                )
            }
        }

    suspend fun detectEncoders(
        context: Context,
        skipPush: Boolean = false,
    ): Result<EncoderDetectionResult> {
        val streamingDadb = runCatching { streamingDadb() }.getOrElse { error ->
            return Result.failure(error)
        }
        val streamingShellExecutor = AdbConnectionShellExecutor(
            dadb = streamingDadb,
            deviceId = deviceId,
        )
        val result = AdbEncoderDetector.detectEncoders(
            streamingDadb,
            context,
            streamingShellExecutor::openStream,
            skipPush,
        )

        return result
    }

    fun close() {
        val failures = listOfNotNull(
            runCatching { forwardRegistry.closeAll() }.exceptionOrNull(),
            runCatching { streamingForwardRegistry?.closeAll() }.exceptionOrNull(),
            runCatching { dadb.close() }.exceptionOrNull(),
        )
        if (failures.isEmpty()) {
            LogManager.d(LogTags.ADB_CONNECTION, "ADB connection closed: $deviceId")
        } else {
            val first = failures.first()
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "${failures.size} resource release failed when closing ADB connection: ${first.message}",
                first,
            )
        }
    }

    private suspend fun streamingForwardRegistry(): AdbConnectionForwardRegistry =
        streamingForwardRegistryMutex.withLock {
            streamingForwardRegistry ?: AdbConnectionForwardRegistry(
                dadb = streamingDadb(),
                deviceId = deviceId,
                sessionContextProvider = { sessionContext },
            ).also { streamingForwardRegistry = it }
        }

    private suspend fun streamingDadb(): Dadb = withContext(Dispatchers.IO) {
        if (forcePrimaryStreaming.get()) {
            return@withContext dadb.route(DadbRoute.PRIMARY)
        }
        try {
            dadb.route(DadbRoute.STREAMING)
        } catch (error: AdbConnectException) {
            if (shouldFallbackToPrimaryStreaming(error)) {
                markStreamingFallback(error)
                dadb.route(DadbRoute.PRIMARY)
            } else {
                throw error
            }
        } catch (error: Exception) {
            if (shouldFallbackToPrimaryStreaming(error)) {
                markStreamingFallback(error)
                dadb.route(DadbRoute.PRIMARY)
            } else {
                throw error
            }
        }
    }
}

internal class AdbConnectionShellExecutor(
    private val dadb: Dadb,
    private val deviceId: String,
) {
    suspend fun execute(
        command: String,
        retryOnFailure: Boolean,
        useShellPassword: Boolean = false,
    ): Result<String> = withContext(Dispatchers.IO) {
        executeShellCommand(command, retryOnFailure, useShellPassword)
    }

    suspend fun executeAsync(command: String) = withContext(Dispatchers.IO) {
        try {
            logShellCommandStart(LogTags.ADB_CONNECTION, command)
            dadb.openShell(command)
            logShellStreamReady(LogTags.ADB_CONNECTION, command)
        } catch (e: Exception) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_ASYNC_EXECUTE_FAILED.english}: ${e.message}", e)
        }
    }

    suspend fun openStream(command: String): AdbShellStream? = withContext(Dispatchers.IO) {
        try {
            logShellStreamOpen(LogTags.ADB_CONNECTION, command)
            dadb.openShell(command).also {
                logShellStreamReady(LogTags.ADB_CONNECTION, command)
            }
        } catch (e: Exception) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
            LogManager.e(
                LogTags.ADB_CONNECTION, "${AdbTexts.ADB_OPEN_SHELL_STREAM_FAILED.english}: ${e.message}", e
            )
            null
        }
    }

    private suspend fun retryShellCommand(
        command: String,
        retryOnFailure: Boolean,
        useShellPassword: Boolean,
        originalError: Exception,
        commandForLog: String = command,
    ): Result<String> {
        if (!retryOnFailure) {
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_CONNECTION_CLOSED.english}，${AdbTexts.ADB_CANNOT_EXECUTE_COMMAND.english}: $commandForLog",
            )
            return Result.failure(originalError)
        }

        LogManager.d(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_AUTO_RECONNECT_RETRY.english}: $commandForLog")
        return try {
            delay(100.milliseconds)
            val retryResponse = executeDadbShell(command, useShellPassword)
            logShellCommandResult(
                tag = LogTags.ADB_CONNECTION,
                command = commandForLog,
                exitCode = retryResponse.exitCode,
                output = retryResponse.output,
                errorOutput = retryResponse.errorOutput,
            )
            LogManager.d(LogTags.ADB_CONNECTION, AdbTexts.ADB_AUTO_RECONNECT_SUCCESS.english)
            if (retryResponse.exitCode == 0) {
                Result.success(retryResponse.output)
            } else {
                Result.failure(
                    ShellCommandException(
                        command = commandForLog,
                        exitCode = retryResponse.exitCode,
                        output = retryResponse.output,
                        stderr = retryResponse.errorOutput,
                    ),
                )
            }
        } catch (retryException: Exception) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, commandForLog, retryException)
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_AUTO_RECONNECT_STILL_FAILED.english}: ${retryException.message}",
            )
            Result.failure(retryException)
        }
    }

    private suspend fun executeShellCommand(
        command: String,
        retryOnFailure: Boolean,
        useShellPassword: Boolean,
    ): Result<String> {
        return try {
            logShellCommandStart(LogTags.ADB_CONNECTION, command)
            val response = executeDadbShell(command, useShellPassword)
            logShellCommandResult(
                tag = LogTags.ADB_CONNECTION,
                command = command,
                exitCode = response.exitCode,
                output = response.output,
                errorOutput = response.errorOutput,
            )

            if (response.exitCode == 0) {
                Result.success(response.output)
            } else {
                Result.failure(
                    ShellCommandException(
                        command = command,
                        exitCode = response.exitCode,
                        output = response.output,
                        stderr = response.errorOutput,
                    ),
                )
            }
        } catch (e: ConnectException) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
            LogManager.d(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_DISCONNECTED_ECONNREFUSED.english} (ECONNREFUSED)，${AdbTexts.ADB_CANNOT_EXECUTE_COMMAND.english}: $command - ${e.message}",
            )
            Result.failure(Exception(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
        } catch (e: EOFException) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
            retryShellCommand(command, retryOnFailure, useShellPassword, e, command)
        } catch (e: SocketException) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
            if (e.message?.contains("ECONNREFUSED", ignoreCase = true) == true) {
                LogManager.d(
                    LogTags.ADB_CONNECTION,
                    "${AdbTexts.ADB_SOCKET_EXCEPTION.english} (ECONNREFUSED): $command - ${e.message}",
                )
                Result.failure(Exception(AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
            } else {
                retryShellCommand(command, retryOnFailure, useShellPassword, e, command)
            }
        } catch (e: Exception) {
            logShellCommandFailure(LogTags.ADB_CONNECTION, command, e)
            LogManager.e(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_EXECUTE_COMMAND_FAILED.english}: device=$deviceId, msg=${e.message}",
                e,
            )
            Result.failure(e)
        }
    }

    private fun executeDadbShell(
        command: String,
        useShellPassword: Boolean,
    ) = if (useShellPassword) dadb.shellWithSuPassword(command) else dadb.shell(command)
}

private data class ShellCommandException(
    val command: String,
    val exitCode: Int,
    val output: String,
    val stderr: String,
) : Exception("Shell command failed: exitCode=$exitCode, command=$command")

internal class AdbConnectionForwardRegistry(
    private val dadb: Dadb,
    private val deviceId: String,
    private val sessionContextProvider: () -> SessionContext?,
) {
    private val forwarders = ConcurrentHashMap<Int, PortForwarder>()
    private val forwardTargets = ConcurrentHashMap<Int, String>()

    suspend fun setupPortForward(
        localPort: Int,
        remotePort: Int,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val targetSocket = "tcp:$remotePort"
            forwarders[localPort]?.close()
            forwardTargets.remove(localPort)
            val forwarder = dadb.forward("127.0.0.1", localPort, targetSocket)
            forwarders[localPort] = forwarder
            forwardTargets[localPort] = targetSocket

            LogManager.d(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_PORT_FORWARD_SUCCESS.english}: $localPort -> $remotePort",
            )
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${AdbTexts.ADB_PORT_FORWARD_FAILED.english}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun setupAdbForward(
        localPort: Int,
        socketName: String,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val targetSocket = "localabstract:$socketName"

        try {
            forwarders[localPort]?.close()
            forwardTargets.remove(localPort)
            val forwarder = dadb.forward(localPort, targetSocket)
            forwarders[localPort] = forwarder
            forwardTargets[localPort] = targetSocket

            val duration = System.currentTimeMillis() - startTime
            pushEvent(
                ForwardSetup(
                    deviceId = deviceId,
                    localPort = localPort,
                    remoteSocket = targetSocket,
                    durationMs = duration,
                    success = true,
                ),
            )
            sessionContextProvider()?.emit(
                SessionEvent.ForwardSetup(
                    localPort = localPort,
                    remoteSocket = targetSocket,
                    context = ForwardSetupContext(durationMs = duration),
                ),
            )
            Result.success(true)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime

            LogManager.e(
                LogTags.ADB_CONNECTION,
                "${AdbTexts.ADB_SOCKET_FORWARDER_FAILED.english}: ${e.message}",
                e,
            )

            pushEvent(
                ForwardSetup(
                    deviceId = deviceId,
                    localPort = localPort,
                    remoteSocket = targetSocket,
                    durationMs = duration,
                    success = false,
                    error = e.message,
                ),
            )
            sessionContextProvider()?.emit(
                SessionEvent.ForwardFailed(
                    ForwardIssue(
                        kind = ForwardIssueKind.SetupFailed,
                        localPort = localPort,
                        remoteSocket = targetSocket,
                        detail = e.message ?: "Unknown error",
                    ),
                ),
            )
            Result.failure(e)
        }
    }

    suspend fun checkAdbForward(localPort: Int): Boolean = withContext(Dispatchers.IO) {
        val forwarder = forwarders[localPort]
        if (forwarder?.isRunning() != true) {
            LogManager.d(LogTags.ADB_CONNECTION, "forwarder not Running")
            return@withContext false
        }

        try {
            val testSocket = Socket()
            testSocket.connect(InetSocketAddress("127.0.0.1", localPort), 500)
            testSocket.close()
            LogManager.d(LogTags.ADB_CONNECTION, "forwarder can connect")
            true
        } catch (_: Exception) {
            LogManager.d(LogTags.ADB_CONNECTION, "forwarder can't connect")
            false
        }
    }

    fun isAdbForwardRunning(localPort: Int): Boolean = forwarders[localPort]?.isRunning() == true

    suspend fun removeAdbForward(
        localPort: Int,
        trigger: ForwardRemovalTrigger = ForwardRemovalTrigger.Unknown,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            forwarders[localPort]?.close()
            forwarders.remove(localPort)
            val remoteSocket = forwardTargets.remove(localPort)
            pushEvent(
                ForwardRemoved(
                    deviceId = deviceId,
                    localPort = localPort,
                ),
            )
            sessionContextProvider()?.emit(
                SessionEvent.ForwardRemoved(
                    localPort = localPort,
                    context = ForwardRemovalContext(
                        remoteSocket = remoteSocket,
                        trigger = trigger,
                    ),
                ),
            )
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(
                LogTags.ADB_CONNECTION, "${AdbTexts.ADB_FORWARD_REMOVE_EXCEPTION.english}: ${e.message}", e
            )
            Result.failure(e)
        }
    }

    fun closeAll() {
        forwarders.values.forEach { it.close() }
        forwarders.clear()
        forwardTargets.clear()
    }
}

internal object AdbConnectionVerifier {
    suspend fun verifyDadb(
        dadb: Dadb,
        deviceId: String,
        timeoutMs: Long = 5000,
        sessionContext: SessionContext? = null,
    ): Result<String> {
        var errorMsg: String? = null
        var shouldCloseDadb = true
        CoroutineScope(Dispatchers.IO).launch {
            emit(sessionContext, SessionEvent.AdbVerifying)
        }

        try {
            val serialOutput = withContext(Dispatchers.IO) {
                val executor =
                    AdbConnectionShellExecutor(
                        dadb = dadb,
                        deviceId = deviceId,
                    )
                val command = "getprop ro.serialno"
                try {
                    withTimeout(timeoutMs.milliseconds) {
                        executor.execute(
                            command = command,
                            retryOnFailure = false,
                        ).getOrThrow()
                    }
                } catch (e: TimeoutCancellationException) {
                    runCatching { dadb.close() }
                    throw e
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    throw error
                }
            }

            val finalSerial = serialOutput.trim().ifBlank { deviceId }

            CoroutineScope(Dispatchers.IO).launch {
                emit(
                    sessionContext,
                    SessionEvent.AdbConnected(
                        AdbConnectionContext(
                            deviceId = deviceId,
                            serial = finalSerial,
                        ),
                    ),
                )
            }

            shouldCloseDadb = false
            return Result.success(finalSerial)
        } catch (e: TimeoutCancellationException) {
            errorMsg = AdbTexts.ADB_VERIFY_TIMEOUT.get()
            emit(
                sessionContext,
                SessionEvent.AdbDisconnected(
                    AdbIssue(
                        kind = AdbIssueKind.VerifyTimeout,
                        detail = errorMsg,
                    ),
                ),
            )
            LogManager.d(LogTags.ADB_CONNECTION, errorMsg)
            return Result.failure(Exception(errorMsg, e))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            val issue = when (e) {
                is ConnectException -> AdbIssue(
                    kind = AdbIssueKind.ConnectionDisconnected,
                    detail = AdbTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(),
                )

                is EOFException -> AdbIssue(
                    kind = AdbIssueKind.HandshakeFailed,
                    detail = AdbTexts.ERROR_ADB_HANDSHAKE_FAILED.get(),
                )

                else -> AdbIssue(
                    kind = AdbIssueKind.ConnectionUnavailable,
                    detail = "${AdbTexts.ERROR_ADB_CONNECTION_UNAVAILABLE.get()}: ${e.message}",
                )
            }
            errorMsg = issue.message
            emit(sessionContext, SessionEvent.AdbDisconnected(issue))
            return Result.failure(Exception(errorMsg, e))
        } finally {
            if (shouldCloseDadb) {
                try {
                    dadb.close()
                } catch (closeException: Exception) {
                    LogManager.w(
                        LogTags.ADB_CONNECTION,
                        "${AdbTexts.ADB_CLOSE_DADB_ERROR.english}: ${closeException.message}",
                    )
                }
            }
            if (errorMsg != null) {
                LogManager.w(LogTags.ADB_CONNECTION, errorMsg)
            }
        }
    }

    private fun emit(
        sessionContext: SessionContext?,
        event: SessionEvent,
    ) {
        sessionContext?.emit(event)
    }
}

private const val SHELL_LOG_MAX_PREVIEW_LENGTH = 240

internal fun logShellCommandStart(
    tag: String,
    command: String,
) {
    dShell(tag) {
        "shell start: ${shellLogPreview(command)}"
    }
}

internal fun logShellCommandResult(
    tag: String,
    command: String,
    exitCode: Int,
    output: String,
    errorOutput: String,
) {
    dShell(tag) {
        buildString {
            append("shell result: command=")
            append(shellLogPreview(command))
            append(" exit=")
            append(exitCode)
            append(" stdout=")
            append(shellLogPreview(output))
            append(" stderr=")
            append(shellLogPreview(errorOutput))
        }
    }
}

internal fun logShellCommandFailure(
    tag: String,
    command: String,
    error: Throwable,
) {
    dShell(tag) {
        "shell failure: command=${shellLogPreview(command)} error=${error.javaClass.simpleName}: ${error.message ?: "<no-message>"}"
    }
}

internal fun logShellStreamOpen(
    tag: String,
    command: String,
) {
    dShell(tag) {
        "shell stream open: ${shellLogPreview(command)}"
    }
}

internal fun logShellStreamReady(
    tag: String,
    command: String,
) {
    dShell(tag) {
        "shell stream ready: ${shellLogPreview(command)}"
    }
}

internal fun shellLogPreview(
    value: String,
    maxLength: Int = SHELL_LOG_MAX_PREVIEW_LENGTH,
): String {
    val normalized = value.replace('\n', ' ').replace('\r', ' ').trim()

    if (normalized.isBlank()) {
        return "<empty>"
    }

    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength) + "..."
    }
}

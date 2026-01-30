package com.mobile.scrcpy.android.infrastructure.scrcpy.connection

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.NetworkConstants
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ConnectionStep
import com.mobile.scrcpy.android.core.domain.model.StepStatus
import com.mobile.scrcpy.android.core.i18n.AdbTexts
import com.mobile.scrcpy.android.core.i18n.RemoteTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbBridge
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnectionManager
import com.mobile.scrcpy.android.infrastructure.media.audio.AudioStream
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Random

/**
 * 连接生命周期管理器 - 管理连接和断开的完整生命周期
 */
class ConnectionLifecycle(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager,
    private val localPort: Int,
    private val stateMachine: ConnectionStateMachine,
    private val socketManager: ConnectionSocketManager,
    private val metadataReader: ConnectionMetadataReader,
    private val shellMonitor: ConnectionShellMonitor,
) {
    var currentScid: Int? = null
        private set

    /**
     * 建立连接
     */
    suspend fun connect(
        deviceId: String,
        maxSize: Int?,
        bitRate: Int,
        maxFps: Int,
        videoCodec: String,
        videoEncoder: String,
        enableAudio: Boolean,
        audioCodec: String,
        audioEncoder: String,
        keyFrameInterval: Int,
        stayAwake: Boolean,
        turnScreenOff: Boolean,
        powerOffOnClose: Boolean,
        skipAdbConnect: Boolean,
        onVideoResolution: (Int, Int) -> Unit,
    ): Result<Pair<VideoStream?, AudioStream?>> =
        withContext(Dispatchers.IO) {
            try {
                // 步骤 1: 验证 ADB 连接
                if (!skipAdbConnect) {
                    stateMachine.updateProgress(
                        ConnectionStep.ADB_CONNECT,
                        StepStatus.RUNNING,
                        AdbTexts.PROGRESS_VERIFYING_ADB.get(),
                    )
                }

                val connection = verifyAndGetAdbConnection(deviceId)
                AdbBridge.setConnection(connection)

                stateMachine.updateProgress(
                    ConnectionStep.ADB_CONNECT,
                    StepStatus.SUCCESS,
                    AdbTexts.PROGRESS_ADB_NORMAL.get(),
                )

                // 步骤 2: 清理旧资源
                cleanupOldResources(connection, deviceId)

                // 步骤 3: 生成 SCID
                val scid = generateScid()
                currentScid = scid
                val socketName = "scrcpy_%08x".format(scid)

                // 步骤 4: 并行执行 Forward 和 Push
                setupForwardAndPushServer(connection, socketName)

                // 步骤 5: 启动 scrcpy-server
                startScrcpyServer(
                    connection,
                    scid,
                    maxSize,
                    bitRate,
                    maxFps,
                    videoCodec,
                    videoEncoder,
                    enableAudio,
                    audioCodec,
                    audioEncoder,
                    keyFrameInterval,
                    stayAwake,
                    powerOffOnClose,
                )

                // 步骤 6: 连接 Socket
                stateMachine.updateProgress(
                    ConnectionStep.CONNECT_SOCKET,
                    StepStatus.RUNNING,
                    "${AdbTexts.PROGRESS_CONNECTING_STREAM.get()} (127.0.0.1:$localPort)",
                )

                socketManager.connectSockets(enableAudio, keyFrameInterval)

                stateMachine.updateProgress(
                    ConnectionStep.CONNECT_SOCKET,
                    StepStatus.SUCCESS,
                    AdbTexts.PROGRESS_SOCKET_CONNECTED.get(),
                )

                // 步骤 7: 读取元数据并创建流
                val (videoStream, audioStream) =
                    metadataReader.readMetadataAndCreateStreams(
                        enableAudio,
                        keyFrameInterval,
                        onVideoResolution,
                    )

                stateMachine.updateProgress(
                    ConnectionStep.COMPLETED,
                    StepStatus.SUCCESS,
                    AdbTexts.PROGRESS_CONNECTION_ESTABLISHED.get(),
                )

                Result.success(Pair(videoStream, audioStream))
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_CONNECTION_FAILED.get()}: ${e.message}", e)
                AdbBridge.clearConnection()
                Result.failure(e)
            }
        }

    /**
     * 断开连接
     */
    suspend fun disconnect(deviceId: String?) =
        withContext(Dispatchers.IO) {
            try {
                // 停止 Shell 监控
                shellMonitor.stopMonitor()
                shellMonitor.closeShellStream()

                // 关闭所有 Socket
                socketManager.closeAllSockets()

                // 移除 ADB Forward
                if (deviceId != null) {
                    val connection = adbConnectionManager.getConnection(deviceId)
                    if (connection != null) {
                        try {
                            connection.removeAdbForward(localPort)
                            LogManager.d(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_REMOVED_ADB_FORWARD.get()}")
                        } catch (e: Exception) {
                            LogManager.w(
                                LogTags.SCRCPY_CLIENT,
                                "${RemoteTexts.SCRCPY_REMOVE_FORWARD_FAILED.get()}: ${e.message}",
                            )
                        }
                    }
                }

                // 终止服务器进程
                if (deviceId != null && currentScid != null) {
                    val connection = adbConnectionManager.getConnection(deviceId)
                    if (connection != null) {
                        try {
                            val scidHex = String.format("%08x", currentScid)
                            val killCmd = "pkill -f 'scrcpy.*scid=$scidHex' || killall -9 app_process"
                            connection.executeShell(killCmd, retryOnFailure = false)
                            LogManager.d(
                                LogTags.SCRCPY_CLIENT,
                                "${RemoteTexts.SCRCPY_TERMINATED_SERVER_PROCESS.get()} (scid=$scidHex)",
                            )
                        } catch (e: Exception) {
                            LogManager.w(
                                LogTags.SCRCPY_CLIENT,
                                "${RemoteTexts.SCRCPY_TERMINATE_SERVER_FAILED.get()}: ${e.message}",
                            )
                        }
                    }
                }

                stateMachine.clearProgress()
                currentScid = null

                LogManager.d(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_DISCONNECTED_ADB_KEPT.get()}")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "断开连接失败: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * 验证并获取 ADB 连接
     */
    private suspend fun verifyAndGetAdbConnection(deviceId: String): AdbConnection {
        val activeConnection =
            adbConnectionManager.getConnection(deviceId)
                ?: throw Exception("Device not connected")

        val isValid = adbConnectionManager.verifyConnection(deviceId)
        if (!isValid) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ${RemoteTexts.SCRCPY_ADB_CONNECTION_UNAVAILABLE.get()}")

            stateMachine.updateProgress(
                ConnectionStep.ADB_CONNECT,
                StepStatus.RUNNING,
                AdbTexts.PROGRESS_ADB_RECONNECTING.get(),
            )

            if (deviceId.startsWith("usb:")) {
                throw Exception(AdbTexts.ERROR_USB_CONNECTION_LOST.get())
            } else {
                val parts = deviceId.split(":")
                if (parts.size == 2) {
                    val host = parts[0]
                    val port = parts[1].toIntOrNull() ?: NetworkConstants.DEFAULT_ADB_PORT_INT
                    val reconnectResult = adbConnectionManager.connectDevice(host, port)
                    if (reconnectResult.isFailure) {
                        throw Exception(
                            "${AdbTexts.ERROR_ADB_RECONNECT_FAILED.get()}: ${reconnectResult.exceptionOrNull()?.message}",
                        )
                    }
                    LogManager.d(LogTags.SCRCPY_CLIENT, "${RemoteTexts.SCRCPY_ADB_RECONNECT_SUCCESS.get()}")
                } else {
                    throw Exception(AdbTexts.ERROR_INVALID_DEVICE_ID.get() + ": $deviceId")
                }
            }
        }

        return adbConnectionManager.getConnection(deviceId)
            ?: throw Exception(AdbTexts.ERROR_CANNOT_GET_ADB_CONNECTION.get())
    }

    /**
     * 清理旧资源
     */
    private suspend fun cleanupOldResources(
        connection: AdbConnection,
        deviceId: String,
    ) {
        try {
            connection.removeAdbForward(localPort)
            if (currentScid != null) {
                val oldScidHex = String.format("%08x", currentScid)
                val killCmd = "pkill -f 'scrcpy.*scid=$oldScidHex' || true"
                connection.executeShell(killCmd, retryOnFailure = false)
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

    /**
     * 设置 Forward 和推送服务器
     */
    private suspend fun setupForwardAndPushServer(
        connection: AdbConnection,
        socketName: String,
    ) = withContext(Dispatchers.IO) {
        stateMachine.updateProgress(
            ConnectionStep.ADB_FORWARD,
            StepStatus.RUNNING,
            "${RemoteTexts.SCRCPY_PORT_FORWARD.get()} $localPort → $socketName",
        )
        stateMachine.updateProgress(
            ConnectionStep.PUSH_SERVER,
            StepStatus.RUNNING,
            AdbTexts.PROGRESS_PUSHING_SERVER.get(),
        )

        val forwardJob =
            async {
                connection.setupAdbForward(localPort, socketName).getOrElse {
                    throw Exception("Forward failed")
                }
            }

        val pushJob =
            async {
                connection.pushScrcpyServer(context).getOrElse {
                    throw Exception("Push failed")
                }
            }

        try {
            forwardJob.await()
            stateMachine.updateProgress(
                ConnectionStep.ADB_FORWARD,
                StepStatus.SUCCESS,
                AdbTexts.PROGRESS_PORT_FORWARD.get(),
            )
        } catch (e: Exception) {
            stateMachine.updateProgress(
                ConnectionStep.ADB_FORWARD,
                StepStatus.FAILED,
                error = e.message,
            )
            throw e
        }

        try {
            pushJob.await()
            stateMachine.updateProgress(
                ConnectionStep.PUSH_SERVER,
                StepStatus.SUCCESS,
                AdbTexts.PROGRESS_SERVER_PUSHED.get(),
            )
        } catch (e: Exception) {
            stateMachine.updateProgress(
                ConnectionStep.PUSH_SERVER,
                StepStatus.FAILED,
                error = e.message,
            )
            throw e
        }
    }

    /**
     * 启动 Scrcpy 服务器
     */
    private suspend fun startScrcpyServer(
        connection: AdbConnection,
        scid: Int,
        maxSize: Int?,
        bitRate: Int,
        maxFps: Int,
        videoCodec: String,
        videoEncoder: String,
        enableAudio: Boolean,
        audioCodec: String,
        audioEncoder: String,
        keyFrameInterval: Int,
        stayAwake: Boolean,
        powerOffOnClose: Boolean,
    ) {
        stateMachine.updateProgress(
            ConnectionStep.START_SERVER,
            StepStatus.RUNNING,
            "${AdbTexts.PROGRESS_STARTING_SERVER.get()} (scid: ${"%08x".format(scid)})",
        )

        val command =
            buildScrcpyCommand(
                maxSize,
                bitRate,
                maxFps,
                scid,
                videoCodec,
                videoEncoder,
                enableAudio,
                audioCodec,
                audioEncoder,
                keyFrameInterval,
                stayAwake,
                powerOffOnClose,
            )

        val stream =
            connection.openShellStream(command)
                ?: throw Exception("Failed to start server")

        shellMonitor.setShellStream(stream)

        delay(1500)

        stateMachine.updateProgress(
            ConnectionStep.START_SERVER,
            StepStatus.SUCCESS,
            AdbTexts.PROGRESS_SERVER_STARTED.get(),
        )
    }

    /**
     * 构建 Scrcpy 命令
     */
    private fun buildScrcpyCommand(
        maxSize: Int?,
        bitRate: Int,
        maxFps: Int,
        scid: Int,
        videoCodec: String,
        videoEncoder: String,
        enableAudio: Boolean,
        audioCodec: String,
        audioEncoder: String,
        keyFrameInterval: Int,
        stayAwake: Boolean,
        powerOffOnClose: Boolean,
    ): String {
        val scidHex = String.format("%08x", scid)
        val params =
            mutableListOf(
                "scid=$scidHex",
                "log_level=debug",
            )

        if (maxSize != null && maxSize > 0) {
            params.add("max_size=$maxSize")
        }

        params.addAll(
            listOf(
                "video_bit_rate=$bitRate",
                "max_fps=$maxFps",
                "video_codec=$videoCodec",
                "stay_awake=$stayAwake",
                "power_off_on_close=$powerOffOnClose",
                "tunnel_forward=true",
            ),
        )

        if (videoEncoder.isNotBlank()) {
            params.add("video_encoder=$videoEncoder")
        }

        if (enableAudio) {
            params.add("audio_codec=$audioCodec")
            params.add("audio_bit_rate=128000")
            if (audioEncoder.isNotBlank()) {
                params.add("audio_encoder=$audioEncoder")
            }
        } else {
            params.add("audio=false")
        }

        params.add(
            "video_codec_options=profile=1,level=52,key-frame-interval=$keyFrameInterval",
        )

        return ScrcpyProtocol.buildScrcpyServerCommand(*params.toTypedArray())
    }

    /**
     * 生成 SCID
     */
    private fun generateScid(): Int {
        val random = Random()
        return random.nextInt(0x7FFFFFFF)
    }
}

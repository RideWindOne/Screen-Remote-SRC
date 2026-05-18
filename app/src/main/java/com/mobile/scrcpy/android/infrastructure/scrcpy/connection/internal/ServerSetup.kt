package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal

import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.AdbIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.AdbIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssue
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerPushContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.ServerStartContext
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.model.SessionEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.mobile.scrcpy.android.core.common.AppConstants

/**
 * Server 设置逻辑 - 负责 Forward 设置、Server 推送和启动
 */

/**
 * 设置 Forward 和推送服务器
 */
internal suspend fun ConnectionLifecycle.setupForwardAndPushServer(
    connection: AdbConnection,
    socketName: String,
) = coroutineScope {
    val pushTargetPath = AppConstants.SCRCPY_SERVER_PATH

    // 推送 Forward 设置中事件
    sessionContext.emit(SessionEvent.ForwardSetting)

    val forwardJob =
        async {
            connection.setupAdbForward(localPort, socketName).getOrElse { error ->
                // 推送 ADB 断开事件（forward 失败通常意味着 ADB 连接有问题）
                sessionContext.emit(
                    SessionEvent.AdbDisconnected(
                        AdbIssue(
                            kind = AdbIssueKind.ForwardSetupFailed,
                            detail = "Forward failed: ${error.message}",
                        ),
                    ),
                )
                throw Exception("Forward failed: ${error.message}", error)
            }
        }

    // 推送 Server 推送中事件
    sessionContext.emit(
        SessionEvent.ServerPushing(
            ServerPushContext(targetPath = pushTargetPath),
        ),
    )

    val pushJob =
        async {
            val pushStartTime = System.currentTimeMillis()
            connection.pushScrcpyServer(context).getOrElse { error ->
                throw Exception("Push failed: ${error.message}", error)
            }
            System.currentTimeMillis() - pushStartTime
        }

    try {
        forwardJob.await()
    } catch (e: Exception) {
        throw e
    }

    try {
        val duration = pushJob.await()
        // 推送 Server 推送成功事件
        sessionContext.emit(
            SessionEvent.ServerPushed(
                ServerPushContext(
                    targetPath = pushTargetPath,
                    durationMs = duration,
                ),
            ),
        )
    } catch (e: Exception) {
        // 推送 Server 推送失败事件
        sessionContext.emit(
            SessionEvent.ServerPushFailed(
                ServerIssue(
                    kind = ServerIssueKind.PushFailed,
                    detail = e.message ?: "Unknown error",
                ),
            ),
        )
        throw e
    }

    // Push 成功后，检测远程编码器（如果需要）
    val session = sessionContext.currentSession()
    val needDetect =
        session?.options?.let { options ->
            // 需要检测的情况：用户选择的编解码器为空（需要自动选择）
            options.userVideoEncoder.isBlank() || // 用户手动选择的视频编码器（优先级最高）
                options.userAudioEncoder.isBlank() || // 用户手动选择的音频编码器（优先级最高）
                options.userVideoDecoder.isBlank() || // 用户手动选择的视频解码器（优先级最高）
                options.userAudioDecoder.isBlank() || // 用户手动选择的音频解码器（优先级最高）
                options.selectedVideoEncoder.isBlank() ||
                options.selectedAudioEncoder.isBlank() ||
                options.selectedVideoDecoder.isBlank() ||
                options.selectedAudioDecoder.isBlank() ||
                options.preferredVideoCodec.isBlank() ||
                options.preferredAudioCodec.isBlank()
        } ?: true

    if (needDetect) {
        detectRemoteEncodersAfterPush(connection)
    }
}

/**
 * 启动 Scrcpy 服务器
 */
internal suspend fun ConnectionLifecycle.startScrcpyServer(
    connection: AdbConnection,
    scid: Int,
) {
    // 推送 Server 启动事件
    sessionContext.emit(SessionEvent.ServerStarting)

    val command = buildScrcpyCommand(scid)

    LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_EXECUTE_COMMAND.get()}: $command")
    val stream =
        connection.openShellStream(command) ?: run {
            // 推送 Server 启动失败事件
            sessionContext.emit(
                SessionEvent.ServerFailed(
                    ServerIssue(
                        kind = ServerIssueKind.StartFailed,
                        detail = "Failed to start server",
                    ),
                ),
            )
            throw Exception("Failed to start server")
        }

    shellMonitor.setShellStream(stream)

    // 等待 scrcpy-server 启动完成
    val serverReady = shellMonitor.waitForServerReady(timeoutMs = 10000)
    if (!serverReady) {
        // 推送 Server 启动失败事件
        sessionContext.emit(
            SessionEvent.ServerFailed(
                ServerIssue(
                    kind = ServerIssueKind.StartupTimeout,
                    detail = "scrcpy-server 启动超时或失败",
                ),
            ),
        )
        throw Exception("scrcpy-server 启动超时或失败")
    }

    // 启动持续监控（监控运行时日志和进程退出）
    shellMonitor.startMonitor()

    // 推送 Server 启动成功事件
    sessionContext.emit(
        SessionEvent.ServerStarted(
            ServerStartContext(scid = scid),
        ),
    )
}

/**
 * 构建 Scrcpy 命令（从会话配置读取参数）
 */
internal fun ConnectionLifecycle.buildScrcpyCommand(scid: Int): String {
    val options = sessionContext.currentOptions() ?: throw IllegalStateException("会话不存在")
    val videoCodec = options.preferredVideoCodec.ifBlank { "h264" }

    val scidHex = String.format("%08x", scid)
    val params =
        mutableListOf(
            "scid=$scidHex",
            "log_level=debug",
        )

    if (options.maxSize > 0) {
        params.add("max_size=${options.maxSize}")
    }

    params.addAll(
        listOf(
            "video_bit_rate=${options.videoBitRate}",
            "max_fps=${options.maxFps}",
            "video_codec=$videoCodec",
            "display_id=${options.displayId.coerceAtLeast(0)}",
            "stay_awake=${options.stayAwake}",
            "power_off_on_close=${options.powerOffOnClose}",
            "tunnel_forward=true",
            "send_device_meta=true",
            "send_codec_meta=true",
            "send_frame_meta=true",
            "send_dummy_byte=true",
        ),
    )

    if (options.showTouches) {
        params.add("show_touches=true")
    }

    options.getFinalVideoEncoder().takeIf { it.isNotBlank() }?.let { encoder ->
        params.add("video_encoder=$encoder")
    }

    if (options.enableAudio) {
        params.add("audio_codec=${options.preferredAudioCodec}")
        params.add("audio_bit_rate=${options.audioBitRate}")
        options.getFinalAudioEncoder().takeIf { it.isNotBlank() }?.let { encoder ->
            params.add("audio_encoder=$encoder")
        }
    } else {
        params.add("audio=false")
    }

    buildVideoCodecOptions(options.codecOptions, options.keyFrameInterval)?.let { codecOptions ->
        params.add("video_codec_options=$codecOptions")
    }

    return ScrcpyProtocol.buildScrcpyServerCommand(*params.toTypedArray())
}

private fun buildVideoCodecOptions(
    userCodecOptions: String,
    keyFrameInterval: Int,
): String? {
    val options =
        userCodecOptions
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

    if (options.none { it.substringBefore('=').trim() == "key-frame-interval" }) {
        options.add("key-frame-interval=${keyFrameInterval.coerceAtLeast(0)}")
    }

    return options.takeIf { it.isNotEmpty() }?.joinToString(",")
}

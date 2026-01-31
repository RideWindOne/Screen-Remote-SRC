package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal

import android.content.Context
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.i18n.SessionTexts
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.protocol.feature.scrcpy.ScrcpyProtocol
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    // 推送 Forward 设置中事件
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.ForwardSetting)

    val forwardJob =
        async {
            connection.setupAdbForward(localPort, socketName).getOrElse { error ->
                // 推送 Forward 失败事件
                CurrentSession.currentOrNull?.handleEvent(
                    SessionEvent.ForwardFailed("$localPort -> $socketName: ${error.message ?: "Unknown error"}"),
                )
                // 推送 ADB 断开事件（forward 失败通常意味着 ADB 连接有问题）
                CurrentSession.currentOrNull?.handleEvent(
                    SessionEvent.AdbDisconnected("Forward failed: ${error.message}"),
                )
                throw Exception("Forward failed: ${error.message}", error)
            }
            // 推送 Forward 设置成功事件
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.ForwardSetup("$localPort -> $socketName"))
        }

    // 推送 Server 推送中事件
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.ServerPushing)

    val pushJob =
        async {
            connection.pushScrcpyServer(context).getOrElse { error ->
                throw Exception("Push failed: ${error.message}", error)
            }
        }

    try {
        forwardJob.await()
    } catch (e: Exception) {
        throw e
    }

    try {
        pushJob.await()
        // 推送 Server 推送成功事件
        CurrentSession.currentOrNull?.handleEvent(SessionEvent.ServerPushed)
    } catch (e: Exception) {
        // 推送 Server 推送失败事件
        CurrentSession.currentOrNull?.handleEvent(
            SessionEvent.ServerPushFailed(e.message ?: "Unknown error"),
        )
        throw e
    }

    // Push 成功后，检测远程编码器（如果需要）
    val session = CurrentSession.currentOrNull
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
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.ServerStarting)

    val command = buildScrcpyCommand(scid)

    LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_EXECUTE_COMMAND.get()}: $command")
    val stream =
        connection.openShellStream(command) ?: run {
            // 推送 Server 启动失败事件
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.ServerFailed("Failed to start server"))
            throw Exception("Failed to start server")
        }

    shellMonitor.setShellStream(stream)

    // 等待 scrcpy-server 启动完成
    val serverReady = shellMonitor.waitForServerReady(timeoutMs = 10000)
    if (!serverReady) {
        // 推送 Server 启动失败事件
        CurrentSession.currentOrNull?.handleEvent(SessionEvent.ServerFailed("scrcpy-server 启动超时或失败"))
        throw Exception("scrcpy-server 启动超时或失败")
    }

    // 启动持续监控（监控运行时日志和进程退出）
    shellMonitor.startMonitor()

    // 推送 Server 启动成功事件
    CurrentSession.currentOrNull?.handleEvent(SessionEvent.ServerStarted)
}

/**
 * 构建 Scrcpy 命令（从会话配置读取参数）
 */
internal fun ConnectionLifecycle.buildScrcpyCommand(scid: Int): String {
    val options = CurrentSession.current.options

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
            "video_codec=${options.preferredVideoCodec}",
            "stay_awake=${options.stayAwake}",
            "power_off_on_close=${options.powerOffOnClose}",
            "tunnel_forward=true",
        ),
    )

    if (options.selectedVideoEncoder.isNotBlank()) {
        params.add("video_encoder=${options.getFinalVideoEncoder()}")
    }

    if (options.enableAudio) {
        params.add("audio_codec=${options.preferredAudioCodec}")
        params.add("audio_bit_rate=${options.audioBitRate}")
        if (options.selectedAudioEncoder.isNotBlank()) {
            params.add("audio_encoder=${options.getFinalAudioEncoder()}")
        }
    } else {
        params.add("audio=false")
    }

    params.add(
        "video_codec_options=profile=1,level=52,key-frame-interval=${options.keyFrameInterval}",
    )

    return ScrcpyProtocol.buildScrcpyServerCommand(*params.toTypedArray())
}

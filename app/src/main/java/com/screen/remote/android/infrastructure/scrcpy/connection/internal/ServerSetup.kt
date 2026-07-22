package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.i18n.SessionTexts
import com.screen.remote.android.core.domain.model.CodecCatalog
import com.screen.remote.android.core.domain.model.CodecMediaType
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.ScrcpyTunnelMode
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.media.codec.CodecSelector
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.protocol.ScrcpyProtocol
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.AdbIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerPushContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.ServerStartContext
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent
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
    localPort: Int,
    tunnelMode: ScrcpyTunnelMode,
    onServerAvailable: (suspend () -> Unit)? = null,
) = coroutineScope {
    val pushTargetPath = AppConstants.SCRCPY_SERVER_PATH

    val forwardJob =
        if (tunnelMode == ScrcpyTunnelMode.ADB_FORWARD) {
            // 推送 Forward 设置中事件
            sessionContext.emit(SessionEvent.ForwardSetting)
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
        } else {
            null
        }

    // 推送 Server 推送中事件
    sessionContext.emit(
        SessionEvent.ServerPushing(
            ServerPushContext(targetPath = pushTargetPath),
        ),
    )

    var pushDurationMs = -1L
    val pushJob =
        async {
            val pushStartTime = System.currentTimeMillis()
            if (!connection.hasRemoteScrcpyServer(pushTargetPath)) {
                connection.pushScrcpyServer(context).getOrElse { error ->
                    throw Exception("Push failed: ${error.message}", error)
                }
                connection.markCompatibleScrcpyServerAvailable()
            }
            pushDurationMs = System.currentTimeMillis() - pushStartTime
            onServerAvailable?.invoke()
        }

    val setupJobs = mutableListOf<kotlinx.coroutines.Deferred<*>>()
    setupJobs.add(pushJob)
    forwardJob?.let {
        setupJobs.add(it)
    }

    try {
        kotlinx.coroutines.joinAll(*setupJobs.toTypedArray())
        sessionContext.emit(
            SessionEvent.ServerPushed(
                ServerPushContext(
                    targetPath = pushTargetPath,
                    durationMs = pushDurationMs,
                ),
            ),
        )
    } catch (e: Exception) {
        val forwardFailedForReal =
            runCatching { forwardJob?.await() }.isFailure

        val pushFailed = runCatching { pushJob.await() }.isFailure

        if (pushFailed && !forwardFailedForReal) {
            sessionContext.emit(
                SessionEvent.ServerPushFailed(
                    ServerIssue(
                        kind = ServerIssueKind.PushFailed,
                        detail = e.message ?: "Unknown error",
                    ),
                ),
            )
        }
        throw e
    }
}

private suspend fun AdbConnection.hasRemoteScrcpyServer(path: String): Boolean =
    if (path == AppConstants.SCRCPY_SERVER_PATH) {
        getCachedCandidatePreflight()?.hasCompatibleScrcpyServer ?: probeRemoteScrcpyServer(path)
    } else {
        probeRemoteScrcpyServer(path)
    }

private suspend fun AdbConnection.probeRemoteScrcpyServer(path: String): Boolean =
    runCatching {
        executeShell(
            "if [ -s '$path' ] && [ \"\$(sha256sum '$path' 2>/dev/null | cut -d' ' -f1)\" = " +
                "'${AppConstants.SCRCPY_SERVER_SHA256}' ]; then echo 1; else echo 0; fi",
            retryOnFailure = false,
        )
            .getOrNull()
            ?.trim() == "1"
    }.getOrDefault(false)

/**
 * 启动 Scrcpy 服务器
 */
internal suspend fun ConnectionLifecycle.startScrcpyServer(
    connection: AdbConnection,
    scid: Int,
    options: ScrcpyOptions,
    waitForReady: Boolean = true,
) {
    // 推送 Server 启动事件
    sessionContext.emit(SessionEvent.ServerStarting)

    val command = buildScrcpyCommand(scid, options)

    LogManager.d(LogTags.ADB_CONNECTION, "${SessionTexts.LABEL_EXECUTE_COMMAND.english}: $command")
    val stream =
        connection.openStreamingShellStream(command) ?: run {
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

    if (!waitForReady) {
        return
    }

    val serverReady = shellMonitor.waitForServerReady(timeoutMs = 10000)
    if (!serverReady) {
        sessionContext.emit(
            SessionEvent.ServerFailed(
                ServerIssue(
                    kind = ServerIssueKind.StartupTimeout,
                    detail = "scrcpy-server startup timed out or failed",
                ),
            ),
        )
        throw Exception("scrcpy-server startup timed out or failed")
    }

    completeScrcpyServerStartup(scid)
}

internal fun ConnectionLifecycle.completeScrcpyServerStartup(scid: Int) {
    shellMonitor.startMonitor()
    sessionContext.emit(SessionEvent.ServerStarted(ServerStartContext(scid = scid)))
}

/**
 * 构建 Scrcpy 命令（从会话配置读取参数）
 */
internal fun ConnectionLifecycle.buildScrcpyCommand(
    scid: Int,
    options: ScrcpyOptions,
): String {
    val config = options.config
    val videoEncoder = options.getFinalVideoEncoder()
    val videoCodec = resolveVideoCodec(options, videoEncoder)

    val scidHex = String.format("%08x", scid)
    val params =
        mutableListOf(
            "scid=$scidHex",
            "log_level=debug",
        )

    if (config.maxSize > 0) {
        params.add("max_size=${config.maxSize}")
    }

    params.addAll(
        listOf(
            "video_bit_rate=${config.videoBitRate}",
            "max_fps=${config.maxFps}",
            "video_codec=$videoCodec",
            "stay_awake=${config.stayAwake}",
            "power_off_on_close=${config.powerOffOnClose}",
            "tunnel_forward=true",
            "send_device_meta=true",
            "send_stream_meta=true",
            "send_frame_meta=true",
            "send_dummy_byte=true",
        ),
    )

    if (config.newDisplayEnabled) {
        params.add("new_display=${config.newDisplay.trim()}")
        if (!config.virtualDisplaySystemDecorations) {
            params.add("vd_system_decorations=false")
        }
        if (config.preserveVirtualDisplayContent) {
            params.add("vd_destroy_content=false")
        }
    } else {
        params.add("display_id=${config.displayId.coerceAtLeast(0)}")
    }

    if (config.showTouches) {
        params.add("show_touches=true")
    }

    if (!config.clipboardSync) {
        params.add("clipboard_autosync=false")
    }

    if (!config.cleanupOnDisconnect) {
        params.add("cleanup=false")
    }

    if (config.ignoreVideoEncoderConstraints) {
        params.add("ignore_video_encoder_constraints=true")
    }

    videoEncoder.takeIf { it.isNotBlank() }?.let { encoder ->
        params.add("video_encoder=$encoder")
    }

    if (config.enableAudio) {
        val audioCodec = resolveAudioCodec(options, options.getFinalAudioEncoder())
        params.add("audio_codec=$audioCodec")
        params.add("audio_bit_rate=${config.audioBitRate}")
        options.getFinalAudioEncoder().takeIf { it.isNotBlank() && audioCodec != "raw" }?.let { encoder ->
            params.add("audio_encoder=$encoder")
        }
    } else {
        params.add("audio=false")
    }

    buildVideoCodecOptions(
        userCodecOptions = config.codecOptions,
        videoEncoder = videoEncoder,
        videoCodec = videoCodec,
    )?.let { codecOptions ->
        params.add("video_codec_options=$codecOptions")
    }

    return ScrcpyProtocol.buildScrcpyServerCommand(*params.toTypedArray())
}

internal fun resolveVideoCodec(
    options: ScrcpyOptions,
    videoEncoder: String,
): String {
    val capabilityCache = options.capabilityCache
    val selectedCodec = CodecCatalog.normalizedName(CodecMediaType.VIDEO, capabilityCache.selectedVideoCodec)
    if (selectedCodec != null &&
        (videoEncoder.isBlank() || capabilityCache.remoteVideoEncoders.any { it.name == videoEncoder && it.codec == selectedCodec })
    ) {
        return selectedCodec
    }
    if (videoEncoder.isBlank()) {
        return selectedCodec ?: CodecCatalog.DEFAULT_VIDEO_CODEC
    }

    return capabilityCache.remoteVideoEncoders.firstOrNull { it.name == videoEncoder }?.codec
        ?: CodecSelector.inferVideoCodecFromName(videoEncoder).ifBlank { CodecCatalog.DEFAULT_VIDEO_CODEC }
}

internal fun resolveAudioCodec(
    options: ScrcpyOptions,
    audioEncoder: String,
): String {
    val capabilityCache = options.capabilityCache
    val selectedCodec = CodecCatalog.normalizedName(CodecMediaType.AUDIO, capabilityCache.selectedAudioCodec)
    if (selectedCodec == "raw" ||
        (selectedCodec != null &&
            (audioEncoder.isBlank() || capabilityCache.remoteAudioEncoders.any { it.name == audioEncoder && it.codec == selectedCodec }))
    ) {
        return selectedCodec
    }
    if (audioEncoder.isBlank()) return selectedCodec ?: CodecCatalog.DEFAULT_AUDIO_CODEC

    return capabilityCache.remoteAudioEncoders.firstOrNull { it.name == audioEncoder }?.codec
        ?: CodecSelector.inferAudioCodecFromName(audioEncoder).ifBlank { CodecCatalog.DEFAULT_AUDIO_CODEC }
}

internal fun buildVideoCodecOptions(
    userCodecOptions: String,
    videoEncoder: String,
    videoCodec: String,
): String? {
    val userOptions =
        userCodecOptions
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (!isMtkAvcEncoder(videoEncoder, videoCodec)) {
        return userOptions.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    val userOptionKeys = userOptions.mapTo(mutableSetOf(), ::codecOptionKey)
    val optimizedOptions =
        MTK_AVC_CODEC_OPTIONS.filter { option ->
            codecOptionKey(option) !in userOptionKeys
        }

    return (userOptions + optimizedOptions).joinToString(",")
}

internal fun isMtkAvcEncoder(
    videoEncoder: String,
    videoCodec: String,
): Boolean {
    val normalizedEncoder = videoEncoder.trim().lowercase()
    return videoCodec.equals("h264", ignoreCase = true) &&
        (normalizedEncoder.startsWith("c2.mtk.") || normalizedEncoder.startsWith("omx.mtk."))
}

private fun codecOptionKey(option: String): String =
    option.substringBefore('=').substringBefore(':').trim().lowercase()

/**
 * MTK AVC 编码器的低延迟抗劣化参数。
 *
 * MTK 码控在高复杂度场景中可能将 QP 拉高后长时间不回落。QP 上限和较短 GOP
 * 用于防止持续模糊，VBR 则为复杂帧保留码率突发空间。用户显式填写的同名选项优先。
 *
 * 参数来源：https://github.com/Genymobile/scrcpy/pull/6954#issuecomment-5022877392
 */
private val MTK_AVC_CODEC_OPTIONS =
    listOf(
        "profile=1",
        "max-bframes=0",
        "i-frame-interval=10",
        "priority=0",
        "bitrate-mode=1",
        "video-qp-max=35",
    )

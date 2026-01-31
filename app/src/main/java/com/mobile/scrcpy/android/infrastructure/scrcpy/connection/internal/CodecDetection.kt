package com.mobile.scrcpy.android.infrastructure.scrcpy.connection.internal

import com.mobile.scrcpy.android.core.common.AppConstants
import com.mobile.scrcpy.android.core.common.LogTags
import com.mobile.scrcpy.android.core.common.manager.LogManager
import com.mobile.scrcpy.android.core.domain.model.ScrcpyOptions
import com.mobile.scrcpy.android.infrastructure.adb.connection.AdbConnection
import com.mobile.scrcpy.android.infrastructure.adb.connection.EncoderDetectionResult
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelectionResult
import com.mobile.scrcpy.android.infrastructure.media.codec.CodecSelector
import com.mobile.scrcpy.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.CurrentSession
import com.mobile.scrcpy.android.infrastructure.scrcpy.session.SessionEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 编解码器检测逻辑
 *
 * 包含所有编解码器检测相关方法：
 * - detectRemoteEncodersAfterPush: 在 Push Server 之后检测远程编码器
 * - fetchRemoteEncoders: 获取远程编码器列表
 * - processCodecSelection: 处理编解码器选择和保存
 * - shouldDetectVideoCodec: 判断是否需要检测视频编解码器
 * - shouldDetectAudioCodec: 判断是否需要检测音频编解码器
 * - shouldDetectDecoder: 判断是否需要检测解码器
 * - shouldDetectEncoder: 判断是否需要检测编码器
 * - selectVideoCodecIfNeeded: 选择视频编解码器
 * - selectAudioCodecIfNeeded: 选择音频编解码器
 * - validateCodecSelection: 验证编解码器选择结果
 * - saveCodecSelection: 保存编解码器选择
 * - copyServerForDetection: 复制 server 到临时路径用于检测
 * - detectEncodersFromRemote: 从远程设备检测编码器
 * - readEncoderDetectionOutput: 读取编码器检测输出
 * - parseVideoEncoderNames: 解析视频编码器名称列表
 * - parseAudioEncoderNames: 解析音频编码器名称列表
 */

/**
 * 在 Push Server 之后检测远程编码器
 * 复用已上传的 scrcpy-server，一次性检测视频和音频编码器
 */
@OptIn(DelicateCoroutinesApi::class)
internal suspend fun ConnectionLifecycle.detectRemoteEncodersAfterPush(connection: AdbConnection) {
    val session =
        CurrentSession.currentOrNull ?: run {
            CurrentSession.currentOrNull?.handleEvent(SessionEvent.SessionError("Session not found"))
            return
        }

    val options = session.options
    val needDetectVideo = shouldDetectVideoCodec(options)
    val needDetectAudio = shouldDetectAudioCodec(options)

    if (!needDetectVideo && !needDetectAudio) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "所有编解码器已配置，跳过检测")
        return
    }

    // 获取远程编码器列表（同步等待）
    val (videoEncoderNames, audioEncoderNames) = fetchRemoteEncoders(connection, options) ?: return

    // 后续处理全部异步化
    GlobalScope.launch(Dispatchers.IO) {
        processCodecSelection(needDetectVideo, needDetectAudio, videoEncoderNames, audioEncoderNames, options)
    }
}

/**
 * 获取远程编码器列表（同步等待）
 * @return Pair<视频编码器列表, 音频编码器列表>，失败返回 null
 */
internal suspend fun ConnectionLifecycle.fetchRemoteEncoders(
    connection: AdbConnection,
    options: ScrcpyOptions,
): Pair<List<String>, List<String>>? {
    // 如果远程编码器列表已存在，直接使用
    if (options.remoteVideoEncoders.isNotEmpty() || options.remoteAudioEncoders.isNotEmpty()) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "远程编码器列表已存在，跳过检测")
        return Pair(options.remoteVideoEncoders, options.remoteAudioEncoders)
    }

    LogManager.d(LogTags.SCRCPY_CLIENT, "开始检测远程编码器（复用已上传的 server）...")

    // 复制 server 到临时路径
    if (!copyServerForDetection(connection)) return null

    // 一次性检测所有编码器
    val detectionResult = detectEncodersFromRemote(connection) ?: return null

    return Pair(
        detectionResult.videoEncoders.map { it.name },
        detectionResult.audioEncoders.map { it.name },
    )
}

/**
 * 处理编解码器选择和保存（异步）
 */
internal suspend fun ConnectionLifecycle.processCodecSelection(
    needDetectVideo: Boolean,
    needDetectAudio: Boolean,
    videoEncoderNames: List<String>,
    audioEncoderNames: List<String>,
    options: ScrcpyOptions,
) {
    // 选择最佳编解码器
    val videoResult = selectVideoCodecIfNeeded(needDetectVideo, videoEncoderNames, options)
    val audioResult = selectAudioCodecIfNeeded(needDetectAudio, audioEncoderNames, options)

    // 检查选择是否成功
    if (!validateCodecSelection(needDetectVideo, needDetectAudio, videoResult, audioResult)) {
        return
    }

    // 保存编解码器选择
    saveCodecSelection(videoResult, audioResult)
}

/**
 * 判断是否需要检测视频编解码器
 */
internal fun ConnectionLifecycle.shouldDetectVideoCodec(options: ScrcpyOptions): Boolean =
    when {
        // 用户全选了 → 不检测
        options.userVideoEncoder.isNotBlank() && options.userVideoDecoder.isNotBlank() -> {
            false
        }

        // 用户选了编码器，没选解码器
        options.userVideoEncoder.isNotBlank() && options.userVideoDecoder.isBlank() -> {
            shouldDetectDecoder(
                options.selectedVideoDecoder,
                options.userVideoEncoder,
                CodecSelector::inferVideoCodecFromName,
            )
        }

        // 用户选了解码器，没选编码器
        options.userVideoEncoder.isBlank() && options.userVideoDecoder.isNotBlank() -> {
            shouldDetectEncoder(
                options.selectedVideoEncoder,
                options.userVideoDecoder,
                CodecSelector::inferVideoCodecFromName,
            )
        }

        // 用户都没选 → 系统必须两个都选了才不检测
        else -> {
            options.selectedVideoEncoder.isBlank() || options.selectedVideoDecoder.isBlank()
        }
    }

/**
 * 判断是否需要检测音频编解码器
 */
internal fun ConnectionLifecycle.shouldDetectAudioCodec(options: ScrcpyOptions): Boolean =
    when {
        // 用户全选了 → 不检测
        options.userAudioEncoder.isNotBlank() && options.userAudioDecoder.isNotBlank() -> {
            false
        }

        // 用户选了编码器，没选解码器
        options.userAudioEncoder.isNotBlank() && options.userAudioDecoder.isBlank() -> {
            shouldDetectDecoder(
                options.selectedAudioDecoder,
                options.userAudioEncoder,
                CodecSelector::inferAudioCodecFromName,
            )
        }

        // 用户选了解码器，没选编码器
        options.userAudioEncoder.isBlank() && options.userAudioDecoder.isNotBlank() -> {
            shouldDetectEncoder(
                options.selectedAudioEncoder,
                options.userAudioDecoder,
                CodecSelector::inferAudioCodecFromName,
            )
        }

        // 用户都没选 → 系统必须两个都选了才不检测
        else -> {
            options.selectedAudioEncoder.isBlank() || options.selectedAudioDecoder.isBlank()
        }
    }

/**
 * 判断是否需要检测解码器（用户选了编码器的情况）
 */
internal fun ConnectionLifecycle.shouldDetectDecoder(
    selectedDecoder: String,
    userEncoder: String,
    inferCodec: (String) -> String,
): Boolean {
    if (selectedDecoder.isBlank()) return true // 系统也没选 → 需要检测

    // 系统有选，检查格式是否匹配
    val userCodec = inferCodec(userEncoder)
    val selectedCodec = inferCodec(selectedDecoder)
    return userCodec != selectedCodec // 不匹配 → 需要检测
}

/**
 * 判断是否需要检测编码器（用户选了解码器的情况）
 */
internal fun ConnectionLifecycle.shouldDetectEncoder(
    selectedEncoder: String,
    userDecoder: String,
    inferCodec: (String) -> String,
): Boolean {
    if (selectedEncoder.isBlank()) return true // 系统也没选 → 需要检测

    // 系统有选，检查格式是否匹配
    val userCodec = inferCodec(userDecoder)
    val selectedCodec = inferCodec(selectedEncoder)
    return userCodec != selectedCodec // 不匹配 → 需要检测
}

/**
 * 选择视频编解码器（如果需要）
 */
internal fun ConnectionLifecycle.selectVideoCodecIfNeeded(
    needDetect: Boolean,
    encoderNames: List<String>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestVideoCodec(
            remoteEncoders = encoderNames,
            userEncoder = options.userVideoEncoder.ifBlank { null },
            userDecoder = options.userVideoDecoder.ifBlank { null },
        )
    } else {
        null
    }

/**
 * 选择音频编解码器（如果需要）
 */
internal fun ConnectionLifecycle.selectAudioCodecIfNeeded(
    needDetect: Boolean,
    encoderNames: List<String>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestAudioCodec(
            remoteEncoders = encoderNames,
            userEncoder = options.userAudioEncoder.ifBlank { null },
            userDecoder = options.userAudioDecoder.ifBlank { null },
        )
    } else {
        null
    }

/**
 * 验证编解码器选择结果
 */
internal fun ConnectionLifecycle.validateCodecSelection(
    needDetectVideo: Boolean,
    needDetectAudio: Boolean,
    videoResult: CodecSelectionResult?,
    audioResult: CodecSelectionResult?,
): Boolean {
    if ((needDetectVideo && videoResult == null) || (needDetectAudio && audioResult == null)) {
        LogManager.e(LogTags.SCRCPY_CLIENT, "编解码器选择失败") // TODO 双语
        CurrentSession.currentOrNull?.handleEvent(
            SessionEvent.ServerFailed("编解码器选择失败"),
        ) // TODO 双语
        return false
    }
    return true
}

/**
 * 保存编解码器选择到当前会话（异步）
 */
internal suspend fun ConnectionLifecycle.saveCodecSelection(
    videoResult: CodecSelectionResult?,
    audioResult: CodecSelectionResult?,
) {
    try {
        val currentSession = CurrentSession.currentOrNull
        if (currentSession == null) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "会话不存在") // TODO 双语
            return
        }

        LogManager.d(LogTags.SDL, "${currentSession.options}")
        LogManager.d(
            LogTags.SCRCPY_CLIENT,
            "保存编解码器选择: " +
                "视频编码器=${videoResult?.encoder ?: "跳过"}, " +
                "视频解码器=${videoResult?.decoder ?: "跳过"}, " +
                "视频格式=${videoResult?.codec ?: "跳过"}, " +
                "音频编码器=${audioResult?.encoder ?: "跳过"}, " +
                "音频解码器=${audioResult?.decoder ?: "跳过"}, " +
                "音频格式=${audioResult?.codec ?: "跳过"}",
        )

        val updated =
            currentSession.options.copy(
                selectedVideoEncoder = videoResult?.encoder ?: currentSession.options.selectedVideoEncoder,
                selectedAudioEncoder = audioResult?.encoder ?: currentSession.options.selectedAudioEncoder,
                selectedVideoDecoder = videoResult?.decoder ?: currentSession.options.selectedVideoDecoder,
                selectedAudioDecoder = audioResult?.decoder ?: currentSession.options.selectedAudioDecoder,
                preferredVideoCodec = videoResult?.codec ?: currentSession.options.preferredVideoCodec,
                preferredAudioCodec = audioResult?.codec ?: currentSession.options.preferredAudioCodec,
            )
        currentSession.setOptions(updated)
        currentSession.getStorage().saveOptions(updated)

        LogManager.d(LogTags.SCRCPY_CLIENT, "已保存编解码器选择到会话 ${currentSession.sessionId}") // TODO 双语
    } catch (e: Exception) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "保存编解码器选择失败: ${e.message}") // TODO 双语
    }
}

/**
 * 复制 server 到临时路径用于检测
 */
internal suspend fun ConnectionLifecycle.copyServerForDetection(connection: AdbConnection): Boolean =
    try {
        connection.executeShell("cp ${AppConstants.SCRCPY_SERVER_PATH} ${AppConstants.SCRCPY_SERVER_2_PATH}")
        true
    } catch (e: Exception) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "复制 server 失败: ${e.message}")
        false
    }

/**
 * 从远程设备检测编码器
 */
internal suspend fun ConnectionLifecycle.detectEncodersFromRemote(connection: AdbConnection): EncoderDetectionResult? {
    val result =
        try {
            connection.detectEncoders(context, skipPush = true)
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "获取编码器异常: ${e.message}")
            return null
        }

    if (result.isFailure) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "获取编码器失败: ${result.exceptionOrNull()?.message}")
        return null
    }

    return result.getOrThrow()
}

/**
 * 读取编码器检测输出
 */
internal fun ConnectionLifecycle.readEncoderDetectionOutput(shellStream: dadb.AdbShellStream): String {
    val output = StringBuilder()
    var lineCount = 0
    val maxLines = 200

    try {
        while (lineCount < maxLines) {
            val packet =
                try {
                    shellStream.read()
                } catch (e: java.io.EOFException) {
                    break
                }

            when (packet) {
                is dadb.AdbShellPacket.StdOut -> {
                    val text = String(packet.payload, Charsets.UTF_8)
                    output.append(text)
                    lineCount++

                    // 读到音频编码器列表说明已完成
                    if (text.contains("List of audio encoders:")) {
                        break
                    }
                }

                is dadb.AdbShellPacket.Exit -> {
                    break
                }

                else -> {}
            }
        }
    } finally {
        try {
            shellStream.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
    }

    return output.toString()
}

/**
 * 解析视频编码器名称列表
 */
internal fun ConnectionLifecycle.parseVideoEncoderNames(output: String): List<String> {
    val encoders = mutableListOf<String>()
    val videoSection =
        if (output.contains("List of video encoders:")) {
            val start = output.indexOf("List of video encoders:")
            val end =
                if (output.contains("List of audio encoders:")) {
                    output.indexOf("List of audio encoders:")
                } else {
                    output.length
                }
            output.substring(start, end)
        } else {
            return encoders
        }

    val lines = videoSection.lines()
    for (line in lines) {
        val encoderMatch = Regex("--video-encoder='?([^'\\s]+)'?").find(line.trim())
        if (encoderMatch != null) {
            encoders.add(encoderMatch.groupValues[1].trim('\''))
        }
    }

    return encoders
}

/**
 * 解析音频编码器名称列表
 */
internal fun ConnectionLifecycle.parseAudioEncoderNames(output: String): List<String> {
    val encoders = mutableListOf<String>()
    val audioSection =
        if (output.contains("List of audio encoders:")) {
            val start = output.indexOf("List of audio encoders:")
            output.substring(start)
        } else {
            return encoders
        }

    val lines = audioSection.lines()
    for (line in lines) {
        val encoderMatch = Regex("--audio-encoder='?([^'\\s]+)'?").find(line.trim())
        if (encoderMatch != null) {
            encoders.add(encoderMatch.groupValues[1].trim('\''))
        }
    }

    return encoders
}

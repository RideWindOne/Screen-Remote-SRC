package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.EncoderDetectionResult
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle

internal suspend fun ConnectionLifecycle.fetchRemoteEncoders(
    connection: AdbConnection,
    options: ScrcpyOptions,
): Pair<List<String>, List<String>>? {
    if (options.remoteVideoEncoders.isNotEmpty() || options.remoteAudioEncoders.isNotEmpty()) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "远程编码器列表已存在，跳过检测")
        return Pair(options.remoteVideoEncoders, options.remoteAudioEncoders)
    }

    LogManager.d(LogTags.SCRCPY_CLIENT, "开始检测远程编码器（复用已上传的 server）...")

    if (!copyServerForDetection(connection)) {
        return null
    }

    val detectionResult = detectEncodersFromRemote(connection) ?: return null
    return Pair(
        detectionResult.videoEncoders.map { it.name },
        detectionResult.audioEncoders.map { it.name },
    )
}

internal suspend fun ConnectionLifecycle.copyServerForDetection(connection: AdbConnection): Boolean =
    try {
        connection.executeShell("cp ${AppConstants.SCRCPY_SERVER_PATH} ${AppConstants.SCRCPY_SERVER_2_PATH}")
        true
    } catch (e: Exception) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "复制 server 失败: ${e.message}")
        false
    }

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

internal fun ConnectionLifecycle.readEncoderDetectionOutput(shellStream: dadb.AdbShellStream): String {
    val output = StringBuilder()
    var lineCount = 0
    val maxLines = 200

    try {
        while (lineCount < maxLines) {
            val packet =
                try {
                    shellStream.read()
                } catch (_: java.io.EOFException) {
                    break
                }

            when (packet) {
                is dadb.AdbShellPacket.StdOut -> {
                    val text = String(packet.payload, Charsets.UTF_8)
                    output.append(text)
                    lineCount++

                    if (text.contains("List of audio encoders:")) {
                        break
                    }
                }

                is dadb.AdbShellPacket.Exit -> break
                else -> Unit
            }
        }
    } finally {
        try {
            shellStream.close()
        } catch (_: Exception) {
            // Ignore stream close failures after detection.
        }
    }

    return output.toString()
}

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

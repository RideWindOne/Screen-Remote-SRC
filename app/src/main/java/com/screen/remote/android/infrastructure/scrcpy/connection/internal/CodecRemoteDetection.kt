package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.common.AppConstants
import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.infrastructure.adb.connection.AdbConnection
import com.screen.remote.android.infrastructure.adb.connection.EncoderDetectionResult
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle

internal suspend fun ConnectionLifecycle.fetchRemoteEncoders(
    connection: AdbConnection,
    options: ScrcpyOptions,
    needVideo: Boolean,
    needAudio: Boolean,
): Pair<List<EncoderCapability>, List<EncoderCapability>>? {
    if (hasRequiredRemoteEncoderCache(options, needVideo, needAudio)) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "本次所需的远程编码器列表已存在，跳过检测")
        return Pair(options.remoteVideoEncoders, options.remoteAudioEncoders)
    }

    LogManager.d(LogTags.SCRCPY_CLIENT, "开始检测远程编码器（复用已上传的 server）...")

    if (!copyServerForDetection(connection)) {
        return null
    }

    val detectionResult = detectEncodersFromRemote(connection) ?: return null
    return Pair(detectionResult.videoEncoders, detectionResult.audioEncoders)
}

internal suspend fun ConnectionLifecycle.copyServerForDetection(connection: AdbConnection): Boolean =
    try {
        connection
            .executeShell(
                "if cp -f ${AppConstants.SCRCPY_SERVER_PATH} ${AppConstants.SCRCPY_SERVER_2_PATH}; then " +
                    "if [ -s ${AppConstants.SCRCPY_SERVER_2_PATH} ]; then echo 1; else echo 0; fi; " +
                    "else echo 0; fi",
                retryOnFailure = false,
            ).getOrNull()
            ?.trim() == "1"
    } catch (e: Exception) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "复制 server 失败: ${e.message}")
        false
    }

internal suspend fun ConnectionLifecycle.detectEncodersFromRemote(connection: AdbConnection): EncoderDetectionResult? {
    val result =
        try {
            connection.detectEncoders(
                context = context,
                skipPush = true,
                persistToBoundSession = false,
            )
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

internal fun hasRequiredRemoteEncoderCache(
    options: ScrcpyOptions,
    needVideo: Boolean,
    needAudio: Boolean,
): Boolean =
    (!needVideo || options.remoteVideoEncoders.isNotEmpty()) &&
        (!needAudio || options.remoteAudioEncoders.isNotEmpty())

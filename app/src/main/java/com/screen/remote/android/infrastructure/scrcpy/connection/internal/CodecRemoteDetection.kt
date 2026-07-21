package com.screen.remote.android.infrastructure.scrcpy.connection.internal

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
        LogManager.d(LogTags.SCRCPY_CLIENT, "The required remote encoder list already exists, skip detection")
        return Pair(options.capabilityCache.remoteVideoEncoders, options.capabilityCache.remoteAudioEncoders)
    }

    LogManager.d(LogTags.SCRCPY_CLIENT, "Start detecting the remote encoder (reusing the uploaded server)...")

    val detectionResult = detectEncodersFromRemote(connection) ?: return null
    return Pair(detectionResult.videoEncoders, detectionResult.audioEncoders)
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
            LogManager.w(LogTags.SCRCPY_CLIENT, "Get encoder exception: ${e.message}")
            return null
        }

    if (result.isFailure) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "Failed to get encoder: ${result.exceptionOrNull()?.message}")
        return null
    }

    return result.getOrThrow()
}

internal fun hasRequiredRemoteEncoderCache(
    options: ScrcpyOptions,
    needVideo: Boolean,
    needAudio: Boolean,
): Boolean =
    (!needVideo || options.capabilityCache.remoteVideoEncoders.isNotEmpty()) &&
        (!needAudio || options.capabilityCache.remoteAudioEncoders.isNotEmpty())

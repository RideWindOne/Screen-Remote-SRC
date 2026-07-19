package com.screen.remote.android.infrastructure.scrcpy.connection.internal

import com.screen.remote.android.core.common.LogTags
import com.screen.remote.android.core.common.manager.LogManager
import com.screen.remote.android.core.domain.model.EncoderCapability
import com.screen.remote.android.core.domain.model.ScrcpyOptions
import com.screen.remote.android.infrastructure.media.codec.CodecSelectionResult
import com.screen.remote.android.infrastructure.media.codec.CodecSelector
import com.screen.remote.android.infrastructure.scrcpy.connection.ConnectionLifecycle
import com.screen.remote.android.infrastructure.scrcpy.session.Session
import com.screen.remote.android.infrastructure.scrcpy.session.internal.saveCodecDetectionResult
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssue
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionIssueKind
import com.screen.remote.android.infrastructure.scrcpy.session.model.SessionEvent

/**
 * 编解码器检测与选择入口。
 *
 * 远程检测流程和本地选择策略已拆到协作文件。
 */
internal suspend fun ConnectionLifecycle.detectRemoteEncodersAfterPush(
    connection: com.screen.remote.android.infrastructure.adb.connection.AdbConnection,
    expectedSessionId: String,
) {
    val session =
        sessionContext.currentSession() ?: run {
            sessionContext.emit(
                SessionEvent.SessionError(
                    SessionIssue(
                        kind = SessionIssueKind.SessionNotFound,
                        detail = "Session not found",
                    ),
                ),
            )
            return
        }

    val options = session.options
    if (session.sessionId != expectedSessionId) {
        return
    }

    if (!options.config.enableAudio) {
        val remoteVideoOnlyNeedDetect = shouldDetectVideoCodec(options)
        if (!remoteVideoOnlyNeedDetect) {
            LogManager.d(LogTags.SCRCPY_CLIENT, "编解码器配置完整且未启用音频，跳过检测")
            return
        }
    }

    val needDetectVideo = shouldDetectVideoCodec(options)
    val needDetectAudio = shouldDetectAudioCodec(options)

    if (!needDetectVideo && !needDetectAudio) {
        LogManager.d(LogTags.SCRCPY_CLIENT, "所有编解码器已配置，跳过检测")
        return
    }

    if (sessionContext.currentSession()?.sessionId != expectedSessionId) {
        return
    }

    val (videoEncoderCapabilities, audioEncoderCapabilities) =
        fetchRemoteEncoders(
            connection = connection,
            options = options,
            needVideo = needDetectVideo,
            needAudio = needDetectAudio,
        ) ?: return

    if (sessionContext.currentSession()?.sessionId != expectedSessionId) {
        return
    }

    processCodecSelection(
        needDetectVideo = needDetectVideo,
        needDetectAudio = needDetectAudio,
        videoEncoderCapabilities = videoEncoderCapabilities,
        audioEncoderCapabilities = audioEncoderCapabilities,
        options = options,
        targetSession = session,
        expectedSessionId = expectedSessionId,
    )
}

internal suspend fun ConnectionLifecycle.processCodecSelection(
    needDetectVideo: Boolean,
    needDetectAudio: Boolean,
    videoEncoderCapabilities: List<EncoderCapability>,
    audioEncoderCapabilities: List<EncoderCapability>,
    options: ScrcpyOptions,
    targetSession: Session,
    expectedSessionId: String,
) {
    val videoResult = selectVideoCodecIfNeeded(needDetectVideo, videoEncoderCapabilities, options)
    val audioResult = selectAudioCodecIfNeeded(needDetectAudio, audioEncoderCapabilities, options)

    if (!validateCodecSelection(needDetectVideo, needDetectAudio, videoResult, audioResult)) {
        return
    }

    if (sessionContext.currentSession()?.sessionId != expectedSessionId) {
        return
    }
    val currentCapabilities = targetSession.options.capabilityCache
    targetSession.saveCodecDetectionResult(
        detectedCapabilities =
            currentCapabilities.copy(
                remoteVideoEncoders = videoEncoderCapabilities,
                remoteAudioEncoders = audioEncoderCapabilities,
                selectedVideoCodec = videoResult?.codec ?: currentCapabilities.selectedVideoCodec,
                selectedAudioCodec = audioResult?.codec ?: currentCapabilities.selectedAudioCodec,
                selectedVideoEncoder = videoResult?.encoder ?: currentCapabilities.selectedVideoEncoder,
                selectedAudioEncoder = audioResult?.encoder ?: currentCapabilities.selectedAudioEncoder,
                selectedVideoDecoder = videoResult?.decoder ?: currentCapabilities.selectedVideoDecoder,
                selectedAudioDecoder = audioResult?.decoder ?: currentCapabilities.selectedAudioDecoder,
            ),
        clearUserVideoSelection = videoResult?.ignoredUserSelection == true,
        clearUserAudioSelection = audioResult?.ignoredUserSelection == true,
    )

    LogManager.d(LogTags.SCRCPY_CLIENT, "已保存编解码器检测结果到会话 $expectedSessionId")
}

internal fun ConnectionLifecycle.shouldDetectVideoCodec(options: ScrcpyOptions): Boolean =
    options.getFinalVideoEncoder().isBlank() ||
        options.getFinalVideoDecoder().isBlank() ||
        options.capabilityCache.selectedVideoCodec.isBlank() ||
        (options.config.userVideoEncoder.isNotBlank() && options.capabilityCache.selectedVideoEncoder != options.config.userVideoEncoder) ||
        (options.config.userVideoDecoder.isNotBlank() && options.capabilityCache.selectedVideoDecoder != options.config.userVideoDecoder) ||
        !hasRemoteEncoderCapability(
            capabilities = options.capabilityCache.remoteVideoEncoders,
            encoderName = options.getFinalVideoEncoder(),
            codec = options.capabilityCache.selectedVideoCodec,
        )

internal fun ConnectionLifecycle.shouldDetectAudioCodec(options: ScrcpyOptions): Boolean =
    if (!options.config.enableAudio) {
        false
    } else if (options.capabilityCache.selectedAudioCodec == "raw" && options.config.userAudioEncoder.isBlank() && options.config.userAudioDecoder.isBlank()) {
        false
    } else {
        options.getFinalAudioEncoder().isBlank() ||
            options.getFinalAudioDecoder().isBlank() ||
            options.capabilityCache.selectedAudioCodec.isBlank() ||
            (options.config.userAudioEncoder.isNotBlank() && options.capabilityCache.selectedAudioEncoder != options.config.userAudioEncoder) ||
            (options.config.userAudioDecoder.isNotBlank() && options.capabilityCache.selectedAudioDecoder != options.config.userAudioDecoder) ||
            !hasRemoteEncoderCapability(
                capabilities = options.capabilityCache.remoteAudioEncoders,
                encoderName = options.getFinalAudioEncoder(),
                codec = options.capabilityCache.selectedAudioCodec,
            )
    }

internal fun hasRemoteEncoderCapability(
    capabilities: List<EncoderCapability>,
    encoderName: String,
    codec: String,
): Boolean {
    val normalizedCodec =
        capabilities.firstOrNull { it.name == encoderName }?.mediaType?.let { mediaType ->
            com.screen.remote.android.core.domain.model.CodecCatalog.normalizedName(mediaType, codec)
        } ?: return false
    return capabilities.any { it.name == encoderName && it.codec == normalizedCodec }
}

internal suspend fun ConnectionLifecycle.selectVideoCodecIfNeeded(
    needDetect: Boolean,
    encoderCapabilities: List<EncoderCapability>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestVideoCodec(
            remoteEncoders = encoderCapabilities,
            userEncoder = options.config.userVideoEncoder.ifBlank { null },
            userDecoder = options.config.userVideoDecoder.ifBlank { null },
            allowHardwareDecoders = options.config.enableHardwareDecoding,
        )
    } else {
        null
    }

internal suspend fun ConnectionLifecycle.selectAudioCodecIfNeeded(
    needDetect: Boolean,
    encoderCapabilities: List<EncoderCapability>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestAudioCodec(
            remoteEncoders = encoderCapabilities,
            userEncoder = options.config.userAudioEncoder.ifBlank { null },
            userDecoder = options.config.userAudioDecoder.ifBlank { null },
            allowHardwareDecoders = options.config.enableHardwareDecoding,
        )
    } else {
        null
    }

internal fun ConnectionLifecycle.validateCodecSelection(
    needDetectVideo: Boolean,
    needDetectAudio: Boolean,
    videoResult: CodecSelectionResult?,
    audioResult: CodecSelectionResult?,
): Boolean {
    if ((needDetectVideo && videoResult == null) || (needDetectAudio && audioResult == null)) {
        LogManager.w(LogTags.SCRCPY_CLIENT, "编解码器自动选择失败，将继续使用当前配置或 scrcpy 默认值")
        return false
    }
    return true
}

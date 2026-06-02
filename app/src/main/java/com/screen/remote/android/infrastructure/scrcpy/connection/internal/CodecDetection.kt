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

    if (!options.enableAudio) {
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
    targetSession.saveCodecDetectionResult(
        deviceSerial = targetSession.options.deviceSerial,
        remoteVideoEncoders = videoEncoderCapabilities,
        remoteAudioEncoders = audioEncoderCapabilities,
        selectedVideoCodec = videoResult?.codec ?: targetSession.options.selectedVideoCodec,
        selectedAudioCodec = audioResult?.codec ?: targetSession.options.selectedAudioCodec,
        selectedVideoEncoder = videoResult?.encoder ?: targetSession.options.selectedVideoEncoder,
        selectedAudioEncoder = audioResult?.encoder ?: targetSession.options.selectedAudioEncoder,
        selectedVideoDecoder = videoResult?.decoder ?: targetSession.options.selectedVideoDecoder,
        selectedAudioDecoder = audioResult?.decoder ?: targetSession.options.selectedAudioDecoder,
        // 自动回退是本次设备的解析结果，不得覆盖用户的格式偏好。
        preferredVideoCodec = targetSession.options.preferredVideoCodec,
        preferredAudioCodec = targetSession.options.preferredAudioCodec,
    )

    LogManager.d(LogTags.SCRCPY_CLIENT, "已保存编解码器检测结果到会话 $expectedSessionId")
}

internal fun ConnectionLifecycle.shouldDetectVideoCodec(options: ScrcpyOptions): Boolean =
    options.getFinalVideoEncoder().isBlank() ||
        options.getFinalVideoDecoder().isBlank() ||
        !hasRemoteEncoderCapability(
            capabilities = options.remoteVideoEncoders,
            encoderName = options.getFinalVideoEncoder(),
            codec = options.preferredVideoCodec,
        )

internal fun ConnectionLifecycle.shouldDetectAudioCodec(options: ScrcpyOptions): Boolean =
    if (!options.enableAudio) {
        false
    } else if (
        com.screen.remote.android.core.domain.model.CodecCatalog
            .normalizedName(com.screen.remote.android.core.domain.model.CodecMediaType.AUDIO, options.preferredAudioCodec) == "raw"
    ) {
        false
    } else {
        options.getFinalAudioEncoder().isBlank() ||
            options.getFinalAudioDecoder().isBlank() ||
            !hasRemoteEncoderCapability(
                capabilities = options.remoteAudioEncoders,
                encoderName = options.getFinalAudioEncoder(),
                codec = options.preferredAudioCodec,
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

internal fun ConnectionLifecycle.shouldDetectDecoder(
    selectedDecoder: String,
    userEncoder: String,
    inferCodec: (String) -> String,
): Boolean {
    if (selectedDecoder.isBlank()) {
        return true
    }
    return inferCodec(userEncoder) != inferCodec(selectedDecoder)
}

internal fun ConnectionLifecycle.shouldDetectEncoder(
    selectedEncoder: String,
    userDecoder: String,
    inferCodec: (String) -> String,
): Boolean {
    if (selectedEncoder.isBlank()) {
        return true
    }
    return inferCodec(userDecoder) != inferCodec(selectedEncoder)
}

internal fun ConnectionLifecycle.hasVideoCodecDrift(
    preferredVideoCodec: String,
    selectedEncoder: String,
    selectedDecoder: String,
): Boolean {
    val normalizedPreferred =
        preferredVideoCodec
            .ifBlank { return true }
            .let(CodecSelector::inferVideoCodecFromName)
    val encoderCodec = CodecSelector.inferVideoCodecFromName(selectedEncoder)
    val decoderCodec = CodecSelector.inferVideoCodecFromName(selectedDecoder)

    return encoderCodec != normalizedPreferred || decoderCodec != normalizedPreferred
}

internal suspend fun ConnectionLifecycle.selectVideoCodecIfNeeded(
    needDetect: Boolean,
    encoderCapabilities: List<EncoderCapability>,
    options: ScrcpyOptions,
): CodecSelectionResult? =
    if (needDetect) {
        CodecSelector.selectBestVideoCodec(
            remoteEncoders = encoderCapabilities,
            userEncoder = options.userVideoEncoder.ifBlank { null },
            userDecoder = options.userVideoDecoder.ifBlank { null },
            preferredCodec = options.preferredVideoCodec,
            allowHardwareDecoders = options.enableHardwareDecoding,
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
            userEncoder = options.userAudioEncoder.ifBlank { null },
            userDecoder = options.userAudioDecoder.ifBlank { null },
            preferredCodec = options.preferredAudioCodec,
            allowHardwareDecoders = options.enableHardwareDecoding,
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
